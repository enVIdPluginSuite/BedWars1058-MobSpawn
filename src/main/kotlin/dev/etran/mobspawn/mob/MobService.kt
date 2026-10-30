package dev.etran.mobspawn.mob

import dev.etran.mobspawn.MobSpawnPlugin
import dev.etran.mobspawn.compat.AttrKey
import dev.etran.mobspawn.compat.Attrs
import dev.etran.mobspawn.compat.EntityCompat
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/** Central logic: spawning, registry, despawn timers, target selection and the click modes. */
class MobService(private val plugin: MobSpawnPlugin) {

    enum class Mode { EDIT, DELETE }

    private class ModeEntry(val mode: Mode, val expiresAt: Long)

    private val mobs = LinkedHashMap<UUID, ManagedMob>()
    private val modes = HashMap<UUID, ModeEntry>()
    private val drafts = HashMap<UUID, Draft>()
    private val lastClick = HashMap<UUID, Long>()

    /** True while this plugin is inside World#spawnEntity, so other plugins' spawn cancels can be undone. */
    @Volatile
    var spawning: Boolean = false
        private set

    private var secondTask: BukkitTask? = null
    private var targetTask: BukkitTask? = null

    // ---- lifecycle ----------------------------------------------------------------------------

    fun start() {
        stop()
        secondTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tickSecond() }, 20L, 20L)
        val every = plugin.cfg.targetScanTicks
        targetTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tickTargets() }, every, every)
    }

    fun stop() {
        secondTask?.cancel()
        targetTask?.cancel()
        secondTask = null
        targetTask = null
    }

    // ---- registry -----------------------------------------------------------------------------

    fun get(entity: Entity?): ManagedMob? = if (entity == null) null else mobs[entity.uniqueId]

    fun count(): Int = mobs.size

    /**
     * Wraps any living entity so it can be edited. The wrapper is NOT registered (and therefore does not
     * change the mob's behaviour) until something is actually modified; see [register].
     */
    fun getOrAdopt(entity: LivingEntity, who: Player): ManagedMob {
        get(entity)?.let { return it }
        return ManagedMob(
            entity,
            MobSettings(mapDamage = null, timerSeconds = 0, targetMode = null),
            who.uniqueId, who.name, plugin.bedWars.teamName(who), plugin.bedWars.arenaName(who)
        )
    }

    fun register(mob: ManagedMob) {
        if (mob.registered) return
        mob.registered = true
        mobs[mob.uuid] = mob
    }

    fun forget(id: UUID) {
        mobs.remove(id)
    }

    fun draft(p: Player): Draft = drafts.getOrPut(p.uniqueId) { Draft(plugin.cfg.newDefaultSettings(), 1) }

    fun forgetPlayer(id: UUID) {
        modes.remove(id)
        drafts.remove(id)
        lastClick.remove(id)
    }

    // ---- spawning / removing ------------------------------------------------------------------

    fun spawn(owner: Player, base: Location, type: EntityType, template: MobSettings, amount: Int): List<ManagedMob> {
        val out = ArrayList<ManagedMob>()
        val team = plugin.bedWars.teamName(owner)
        val arena = plugin.bedWars.arenaName(owner)
        for (i in 0 until amount.coerceIn(1, plugin.cfg.maxAmount)) {
            val loc = base.clone()
            if (amount > 1) loc.add((Math.random() - 0.5) * 2.5, 0.0, (Math.random() - 0.5) * 2.5)
            val world = loc.world ?: return out
            spawning = true
            val entity: Entity? = try {
                world.spawnEntity(loc, type)
            } catch (t: Throwable) {
                null
            } finally {
                spawning = false
            }
            if (entity !is LivingEntity) {
                entity?.remove()
                continue
            }
            try {
                entity.removeWhenFarAway = false
            } catch (t: Throwable) {
            }
            val mob = ManagedMob(entity, template.copy(), owner.uniqueId, owner.name, team, arena)
            register(mob)
            applySettings(mob, resetTimer = true)
            out.add(mob)
        }
        return out
    }

    fun remove(entity: LivingEntity) {
        mobs.remove(entity.uniqueId)
        entity.remove()
    }

    /** Removes managed mobs; with [center] only those within [radius] blocks of it. Returns how many. */
    fun clear(center: Location?, radius: Double): Int {
        var n = 0
        for (mob in ArrayList(mobs.values)) {
            val e = mob.entity
            if (center != null) {
                if (e.world != center.world || e.location.distanceSquared(center) > radius * radius) continue
            }
            try {
                e.remove()
            } catch (t: Throwable) {
            }
            mobs.remove(mob.uuid)
            n++
        }
        return n
    }

    // ---- settings & timers --------------------------------------------------------------------

    fun applySettings(mob: ManagedMob, resetTimer: Boolean) {
        if (resetTimer) mob.remaining = mob.settings.timerSeconds
        updateName(mob)
    }

    private fun fmt(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "$m:" + (if (s < 10) "0$s" else "$s")
    }

    private fun updateName(mob: ManagedMob) {
        val e = mob.entity
        try {
            if (mob.settings.timerSeconds > 0 && plugin.cfg.showTimerName) {
                if (!mob.nameSaved) {
                    mob.originalName = e.customName
                    mob.originalNameVisible = e.isCustomNameVisible
                    mob.nameSaved = true
                }
                e.customName = "§e" + EntityCompat.pretty(e.type) + " §7[§f" + fmt(mob.remaining) + "§7]"
                e.isCustomNameVisible = true
            } else if (mob.nameSaved) {
                e.customName = mob.originalName
                e.isCustomNameVisible = mob.originalNameVisible
                mob.nameSaved = false
            }
        } catch (t: Throwable) {
            // names are cosmetic
        }
    }

    private fun resolve(mob: ManagedMob): LivingEntity? {
        val e = mob.entity
        if (e.isValid) return e
        if (e.isDead) return null
        // Not dead but not valid: its chunk is unloaded. Try to re-link every few seconds.
        if (mob.staleSeconds % 5 == 0) {
            val found = EntityCompat.findEntity(mob.uuid)
            if (found != null && found.isValid) {
                mob.entity = found
                return found
            }
        }
        return null
    }

    private fun tickSecond() {
        val now = System.currentTimeMillis()
        modes.entries.removeIf { it.value.expiresAt < now }

        for (mob in ArrayList(mobs.values)) {
            val e = resolve(mob)
            if (e == null) {
                if (mob.entity.isDead || ++mob.staleSeconds > 600) mobs.remove(mob.uuid)
                continue
            }
            mob.staleSeconds = 0
            if (mob.settings.timerSeconds > 0) {
                mob.remaining--
                if (mob.remaining <= 0) {
                    try {
                        e.remove()
                    } catch (t: Throwable) {
                    }
                    mobs.remove(mob.uuid)
                } else {
                    updateName(mob)
                }
            }
        }
    }

    // ---- targeting ----------------------------------------------------------------------------

    /** Is [p] a legal victim for [mob] under its targeting mode? (Ignores spectators and creative players.) */
    fun isPlayerAllowed(mob: ManagedMob, p: Player): Boolean {
        val s = mob.settings
        val mode = s.targetMode ?: return true
        if (!p.isOnline || p.isDead) return false
        val gm = p.gameMode
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false
        if (plugin.bedWars.isSpectating(p)) return false
        return when (mode) {
            TargetMode.ALL -> true
            TargetMode.NONE -> false
            TargetMode.PLAYER -> p.uniqueId == s.targetPlayerId
            TargetMode.ENEMIES -> {
                if (p.uniqueId == mob.ownerId) false
                else {
                    val team = mob.ownerTeam
                    !(team != null &&
                        plugin.bedWars.teamName(p) == team &&
                        plugin.bedWars.arenaName(p) == mob.ownerArena)
                }
            }
            TargetMode.TEAM -> {
                val team = s.targetTeam
                team != null &&
                    plugin.bedWars.teamName(p) == team &&
                    plugin.bedWars.arenaName(p) == s.targetArena
            }
        }
    }

    /** Used by the EntityTargetEvent listener. */
    fun isTargetAllowed(mob: ManagedMob, target: Entity): Boolean {
        val mode = mob.settings.targetMode ?: return true
        if (target is Player) return isPlayerAllowed(mob, target)
        return mode == TargetMode.ALL
    }

    private fun tickTargets() {
        for (mob in ArrayList(mobs.values)) {
            val e = mob.entity
            val mode = mob.settings.targetMode ?: continue
            if (!e.isValid || !EntityCompat.isAggressive(e)) continue

            val current = EntityCompat.getTarget(e)
            if (mode == TargetMode.NONE) {
                if (current != null) EntityCompat.setTarget(e, null)
                continue
            }
            if (current != null) {
                if (current is Player) {
                    if (isPlayerAllowed(mob, current)) continue
                } else if (mode == TargetMode.ALL) continue
            }

            val follow = Attrs.read(e, AttrKey.FOLLOW_RANGE)
            val range = if (follow != null && follow > 0.0) follow else plugin.cfg.defaultTargetRange
            val r2 = range * range
            var best: Player? = null
            var bestDist = Double.MAX_VALUE
            for (p in e.world.players) {
                val d = p.location.distanceSquared(e.location)
                if (d > r2 || d >= bestDist) continue
                if (!isPlayerAllowed(mob, p)) continue
                best = p
                bestDist = d
            }
            if (best != null || current != null) EntityCompat.setTarget(e, best)
        }
    }

    // ---- click modes (edit / delete) ----------------------------------------------------------

    fun setMode(p: Player, mode: Mode) {
        modes[p.uniqueId] = ModeEntry(mode, System.currentTimeMillis() + plugin.cfg.modeTimeoutSeconds * 1000L)
    }

    fun getMode(p: Player): Mode? {
        val m = modes[p.uniqueId] ?: return null
        if (m.expiresAt < System.currentTimeMillis()) {
            modes.remove(p.uniqueId)
            return null
        }
        return m.mode
    }

    fun clearMode(p: Player) {
        modes.remove(p.uniqueId)
    }

    /** Returns true if this click is a duplicate of one handled a moment ago (1.9+ fires interact events per hand). */
    fun isDuplicateClick(p: Player): Boolean {
        val now = System.currentTimeMillis()
        val last = lastClick[p.uniqueId]
        lastClick[p.uniqueId] = now
        return last != null && now - last < 150
    }
}
