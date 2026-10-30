package dev.etran.mobspawn.gui

import dev.etran.mobspawn.MobSpawnPlugin
import dev.etran.mobspawn.compat.AttrKey
import dev.etran.mobspawn.compat.Attrs
import dev.etran.mobspawn.compat.EntityCompat
import dev.etran.mobspawn.compat.Items
import dev.etran.mobspawn.compat.named
import dev.etran.mobspawn.mob.ManagedMob
import dev.etran.mobspawn.mob.MobSettings
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory

/**
 * One menu for two jobs:
 *  - [mob] == null : configuring a mob that is about to be spawned of [type] (a "draft");
 *  - [mob] != null : editing a mob that already exists in the world.
 */
class ConfigGui(
    plugin: MobSpawnPlugin,
    viewer: Player,
    private val type: EntityType?,
    private val mob: ManagedMob?
) : Gui(plugin, viewer) {

    override val rows = 6

    val settings: MobSettings get() = mob?.settings ?: plugin.service.draft(viewer).settings

    private val entityType: EntityType get() = mob?.entity?.type ?: type!!

    private val aggressive: Boolean
        get() = if (mob != null) EntityCompat.isAggressive(mob.entity) else EntityCompat.isAggressiveType(type!!)

    private val attrKeys = AttrKey.values()
    private val attrSlots = intArrayOf(28, 29, 30, 31, 32, 33)

    override fun title(): String =
        (if (mob != null) "§8Edit: " else "§8Spawn: ") + EntityCompat.pretty(entityType)

    /** Call after any change to [settings] so a live mob picks it up. */
    fun onSettingsChanged(resetTimer: Boolean) {
        val m = mob ?: return
        plugin.service.register(m)
        plugin.service.applySettings(m, resetTimer)
    }

    override fun render(inv: Inventory) {
        val s = settings

        // header
        val headerLore = ArrayList<String>()
        if (mob != null) {
            val e = mob.entity
            val hp = try { e.health } catch (t: Throwable) { 0.0 }
            val max = Attrs.read(e, AttrKey.MAX_HEALTH)
            headerLore.add("§7Health: §f" + String.format("%.1f", hp) + (if (max != null) "§7/§f" + String.format("%.1f", max) else ""))
            headerLore.add("§7Id: §8" + mob.uuid.toString().substring(0, 8))
            if (!mob.registered) headerLore.add("§7Not spawned by this plugin: untouched until you change something.")
        } else {
            headerLore.add("§7Pick the settings, then press §aSpawn§7.")
        }
        inv.setItem(4, Items.mobIcon(entityType).named("§e" + EntityCompat.pretty(entityType), *headerLore.toTypedArray()))

        // map damage
        val md = s.mapDamage
        val mdPane = when (md) {
            true -> Items.pane("LIME_STAINED_GLASS_PANE", 5)
            false -> Items.pane("RED_STAINED_GLASS_PANE", 14)
            null -> Items.pane("GRAY_STAINED_GLASS_PANE", 7)
        }
        inv.setItem(
            10, mdPane.named(
                "§eMap damage: " + SpawnMenuGui.describeMapDamage(md),
                "§7ON: explosions and block changes caused by",
                "§7this mob destroy the map.",
                "§7OFF: this mob never breaks any blocks.",
                "",
                "§eClick §7to toggle"
            )
        )

        // targeting
        if (aggressive) {
            inv.setItem(
                12, Items.stack("DIAMOND_SWORD", "DIAMOND_SWORD").named(
                    "§eTargeting: §f" + (s.targetMode?.label ?: "untouched"),
                    *targetLore(s).toTypedArray()
                )
            )
        } else {
            inv.setItem(12, Items.barrier().named("§7Targeting", "§7This is a passive mob; it never attacks."))
        }

        // slot 14: amount (draft) / heal (live)
        if (mob == null) {
            val amount = plugin.service.draft(viewer).amount
            inv.setItem(
                14, Items.stack("CHEST").named(
                    "§eAmount: §f$amount",
                    "§eLeft/Right-click §7+1 / -1",
                    "§eShift-click §7+5 / -5",
                    "§7Max: ${plugin.cfg.maxAmount}"
                )
            )
        } else {
            inv.setItem(14, Items.stack("GOLDEN_APPLE").named("§aHeal to full health"))
            inv.setItem(16, Items.stack("TNT").named("§cDelete this mob", "§7Removes it from the world."))
        }

        renderTimer(inv, s)
        renderAttributes(inv)

        inv.setItem(45, Items.stack("ARROW").named(if (mob == null) "§aBack to mob list" else "§aClose"))
        if (mob == null) {
            inv.setItem(
                49, Items.stack("EMERALD_BLOCK", "EMERALD_BLOCK").named(
                    "§a§lSPAWN",
                    "§7Spawns §f${plugin.service.draft(viewer).amount}x ${EntityCompat.pretty(entityType)}",
                    "§7at your position."
                )
            )
            inv.setItem(
                50, Items.stack("EMERALD").named(
                    "§aSpawn & edit attributes",
                    "§7Spawns one mob and opens its live settings",
                    "§7so you can tune health, speed, damage..."
                )
            )
        } else {
            inv.setItem(49, Items.barrier().named("§cClose"))
        }
        fillEmpty(inv)
    }

    private fun targetLore(s: MobSettings): List<String> {
        val out = ArrayList<String>()
        s.targetMode?.let { out.add("§7" + it.description) }
        when (s.targetMode) {
            dev.etran.mobspawn.mob.TargetMode.PLAYER -> out.add("§7Player: §f" + (s.targetPlayerName ?: "none picked"))
            dev.etran.mobspawn.mob.TargetMode.TEAM -> out.add("§7Team: §f" + (s.targetTeam ?: "none picked"))
            else -> {
            }
        }
        out.add("")
        out.add("§eClick §7to choose who it attacks")
        return out
    }

    private fun renderTimer(inv: Inventory, s: MobSettings) {
        val lore = ArrayList<String>()
        lore.add("§7Despawns the mob after this long.")
        if (mob != null && s.timerSeconds > 0) lore.add("§7Time left: §f${mob.remaining}s")
        lore.add("§7Default: no timer.")
        inv.setItem(
            22, Items.clock().named(
                "§eDespawn timer: §f" + (if (s.timerSeconds <= 0) "none" else formatTime(s.timerSeconds)),
                *lore.toTypedArray()
            )
        )
        inv.setItem(18, Items.barrier().named("§cNo timer", "§7The mob stays until killed or deleted."))
        val minus = intArrayOf(19, 20, 21)
        val plus = intArrayOf(23, 24, 25)
        val amounts = intArrayOf(60, 10, 1)
        for (i in 0..2) {
            val label = if (amounts[i] == 60) "1m" else "${amounts[i]}s"
            inv.setItem(minus[i], Items.pane("RED_STAINED_GLASS_PANE", 14).named("§c-$label"))
            inv.setItem(plus[2 - i], Items.pane("LIME_STAINED_GLASS_PANE", 5).named("§a+" + (if (amounts[i] == 60) "1m" else "${amounts[i]}s")))
        }
    }

    private fun renderAttributes(inv: Inventory) {
        if (mob == null) {
            inv.setItem(
                31, Items.stack("BOOK", "BOOK").named(
                    "§bAttributes",
                    "§7Health, speed, damage, follow range,",
                    "§7knockback resistance and scale are edited",
                    "§7on the live mob: use §aSpawn & edit attributes§7,",
                    "§7or §e/mobspawn edit§7 and click the mob."
                )
            )
            return
        }
        val allowed = plugin.canEditAttributes(viewer)
        for (i in attrKeys.indices) {
            val key = attrKeys[i]
            val value = Attrs.read(mob.entity, key)
            val icon = Items.stack(key.icon[0], key.icon[0])
            if (value == null) {
                inv.setItem(attrSlots[i], Items.pane("GRAY_STAINED_GLASS_PANE", 7).named("§7${key.label}", "§8Not available for this mob/version."))
            } else if (!allowed) {
                inv.setItem(attrSlots[i], icon.named("§e${key.label}: §f${fmt(value)}", "§cEditing needs the mobspawn.admin permission."))
            } else {
                inv.setItem(
                    attrSlots[i], icon.named(
                        "§e${key.label}: §f${fmt(value)}",
                        "§eLeft-click §7+${fmt(key.step)}   §eRight-click §7-${fmt(key.step)}",
                        "§eShift-left §7+${fmt(key.bigStep)}   §eShift-right §7-${fmt(key.bigStep)}"
                    )
                )
            }
        }
    }

    private fun fmt(d: Double): String {
        val r = Math.round(d * 1000.0) / 1000.0
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
    }

    private fun formatTime(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    // ---- clicks -------------------------------------------------------------------------------

    override fun onClick(slot: Int, click: ClickType) {
        if (!plugin.canUse(viewer)) {
            viewer.closeInventory()
            return
        }
        if (mob != null && !mob.entity.isValid) {
            viewer.closeInventory()
            plugin.msg(viewer, "That mob no longer exists.")
            return
        }
        val s = settings
        when (slot) {
            10 -> {
                s.mapDamage = !(s.mapDamage ?: false)
                onSettingsChanged(false)
                refresh()
            }
            12 -> if (aggressive) TargetGui(plugin, viewer, this).openLater()
            14 -> if (mob == null) changeAmount(click) else heal()
            16 -> if (mob != null) delete()
            18 -> setTimer(0)
            19 -> addTimer(-60)
            20 -> addTimer(-10)
            21 -> addTimer(-1)
            23 -> addTimer(1)
            24 -> addTimer(10)
            25 -> addTimer(60)
            45 -> if (mob == null) SpawnMenuGui(plugin, viewer).openLater() else viewer.closeInventory()
            49 -> if (mob == null) spawn(false) else viewer.closeInventory()
            50 -> if (mob == null) spawn(true)
            else -> {
                val idx = attrSlots.indexOf(slot)
                if (idx >= 0) editAttribute(attrKeys[idx], click)
            }
        }
    }

    private fun setTimer(sec: Int) {
        settings.timerSeconds = sec.coerceIn(0, plugin.cfg.maxTimer)
        onSettingsChanged(true)
        refresh()
    }

    private fun addTimer(delta: Int) = setTimer(settings.timerSeconds + delta)

    private fun changeAmount(click: ClickType) {
        val d = plugin.service.draft(viewer)
        val delta = when (click) {
            ClickType.LEFT -> 1
            ClickType.RIGHT -> -1
            ClickType.SHIFT_LEFT -> 5
            ClickType.SHIFT_RIGHT -> -5
            else -> return
        }
        d.amount = (d.amount + delta).coerceIn(1, plugin.cfg.maxAmount)
        refresh()
    }

    private fun heal() {
        val m = mob ?: return
        try {
            m.entity.health = Attrs.read(m.entity, AttrKey.MAX_HEALTH) ?: 20.0
        } catch (t: Throwable) {
        }
        refresh()
    }

    private fun delete() {
        val m = mob ?: return
        plugin.service.remove(m.entity)
        viewer.closeInventory()
        plugin.msg(viewer, "Deleted ${EntityCompat.pretty(m.entity.type)}.")
    }

    private fun editAttribute(key: AttrKey, click: ClickType) {
        val m = mob ?: return
        if (!plugin.canEditAttributes(viewer)) {
            plugin.msg(viewer, "Editing attributes needs the mobspawn.admin permission.")
            return
        }
        val cur = Attrs.read(m.entity, key) ?: return
        val delta = when (click) {
            ClickType.LEFT -> key.step
            ClickType.RIGHT -> -key.step
            ClickType.SHIFT_LEFT -> key.bigStep
            ClickType.SHIFT_RIGHT -> -key.bigStep
            else -> return
        }
        Attrs.write(m.entity, key, cur + delta)
        refresh()
    }

    private fun spawn(thenEdit: Boolean) {
        val d = plugin.service.draft(viewer)
        val amount = if (thenEdit) 1 else d.amount
        val spawned = plugin.service.spawn(viewer, viewer.location, entityType, d.settings, amount)
        if (spawned.isEmpty()) {
            plugin.msg(viewer, "Could not spawn ${EntityCompat.pretty(entityType)} here.")
            return
        }
        plugin.msg(viewer, "Spawned ${spawned.size}x ${EntityCompat.pretty(entityType)}.")
        if (thenEdit) ConfigGui(plugin, viewer, null, spawned[0]).openLater() else viewer.closeInventory()
    }
}
