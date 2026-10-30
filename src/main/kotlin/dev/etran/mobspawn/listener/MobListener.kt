package dev.etran.mobspawn.listener

import dev.etran.mobspawn.MobSpawnPlugin
import dev.etran.mobspawn.compat.EntityCompat
import dev.etran.mobspawn.gui.ConfigGui
import dev.etran.mobspawn.mob.ManagedMob
import dev.etran.mobspawn.mob.MobService
import org.bukkit.block.Block
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerQuitEvent

class MobListener(private val plugin: MobSpawnPlugin) : Listener {

    private val service: MobService get() = plugin.service

    // ---- spawning -----------------------------------------------------------------------------

    /** BedWars (or another plugin) may cancel mob spawns in arena worlds; ours must still work. */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onSpawn(e: CreatureSpawnEvent) {
        if (service.spawning && e.isCancelled) e.isCancelled = false
    }

    // ---- edit / delete modes ------------------------------------------------------------------

    private fun attacker(damager: Entity): Player? {
        if (damager is Player) return damager
        if (damager is Projectile) return damager.shooter as? Player
        return null
    }

    private fun usable(victim: Entity): LivingEntity? {
        if (victim !is LivingEntity || victim is Player || victim is ArmorStand) return null
        return victim
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onHit(e: EntityDamageByEntityEvent) {
        val player = attacker(e.damager) ?: return
        val victim = usable(e.entity) ?: return
        val mode = service.getMode(player) ?: return
        if (!plugin.canUse(player)) {
            service.clearMode(player)
            return
        }
        e.isCancelled = true
        if (!service.isDuplicateClick(player)) handleClick(player, victim, mode)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteract(e: PlayerInteractEntityEvent) {
        val player = e.player
        val victim = usable(e.rightClicked) ?: return
        val mode = service.getMode(player) ?: return
        if (!plugin.canUse(player)) {
            service.clearMode(player)
            return
        }
        e.isCancelled = true
        if (!service.isDuplicateClick(player)) handleClick(player, victim, mode)
    }

    private fun handleClick(player: Player, victim: LivingEntity, mode: MobService.Mode) {
        val name = EntityCompat.pretty(victim.type)
        when (mode) {
            MobService.Mode.DELETE -> {
                service.remove(victim)
                plugin.msg(player, "Deleted $name.")
            }
            MobService.Mode.EDIT -> {
                service.clearMode(player)
                ConfigGui(plugin, player, null, service.getOrAdopt(victim, player)).openLater()
            }
        }
    }

    // ---- bookkeeping --------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(e: EntityDeathEvent) {
        service.forget(e.entity.uniqueId)
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        service.forgetPlayer(e.player.uniqueId)
    }

    // ---- targeting ----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onTarget(e: EntityTargetEvent) {
        val mob = service.get(e.entity) ?: return
        val target = e.target ?: return
        if (!service.isTargetAllowed(mob, target)) e.isCancelled = true
    }

    // ---- map damage ---------------------------------------------------------------------------

    /** The mob responsible for an explosion: the mob itself, or the shooter of its fireball / wither skull. */
    private fun ownerOf(source: Entity?): ManagedMob? {
        if (source == null) return null
        if (source is LivingEntity) return service.get(source)
        if (source is Projectile) {
            val shooter = source.shooter
            if (shooter is LivingEntity) return service.get(shooter)
        }
        return null
    }

    private var pendingEvent: EntityExplodeEvent? = null
    private var pendingBlocks: List<Block>? = null

    /** Runs before BedWars1058 trims the block list, so a mob with map damage ON can get the full list back. */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onExplodeEarly(e: EntityExplodeEvent) {
        val mob = ownerOf(e.entity) ?: return
        if (mob.settings.mapDamage == true) {
            pendingEvent = e
            pendingBlocks = ArrayList(e.blockList())
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onExplodeLate(e: EntityExplodeEvent) {
        val mob = ownerOf(e.entity) ?: return
        when (mob.settings.mapDamage) {
            true -> {
                if (pendingEvent === e) {
                    val blocks = pendingBlocks
                    if (blocks != null) {
                        e.blockList().clear()
                        e.blockList().addAll(blocks)
                    }
                    if (e.isCancelled && plugin.cfg.overrideCancelledMapDamage) e.isCancelled = false
                }
                pendingEvent = null
                pendingBlocks = null
            }
            false -> e.blockList().clear()
            null -> {
            }
        }
    }

    /** Endermen, wither, ravagers, door-breaking zombies, ... */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onChangeBlock(e: EntityChangeBlockEvent) {
        val entity = e.entity as? LivingEntity ?: return
        val mob = service.get(entity) ?: return
        when (mob.settings.mapDamage) {
            false -> e.isCancelled = true
            true -> if (e.isCancelled && plugin.cfg.overrideCancelledMapDamage) e.isCancelled = false
            null -> {
            }
        }
    }
}
