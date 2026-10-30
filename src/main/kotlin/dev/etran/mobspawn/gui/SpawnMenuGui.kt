package dev.etran.mobspawn.gui

import dev.etran.mobspawn.MobSpawnPlugin
import dev.etran.mobspawn.compat.EntityCompat
import dev.etran.mobspawn.compat.Items
import dev.etran.mobspawn.compat.named
import dev.etran.mobspawn.mob.MobService
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory

/** Paged list of every spawnable mob on this server version. */
class SpawnMenuGui(
    plugin: MobSpawnPlugin,
    viewer: Player,
    private var page: Int = 0,
    private var filter: Filter = Filter.ALL
) : Gui(plugin, viewer) {

    enum class Filter(val label: String) {
        ALL("All mobs"), HOSTILE("Hostile only"), PASSIVE("Passive only")
    }

    override val rows = 6

    private val slotTypes = arrayOfNulls<EntityType>(45)

    private fun list(): List<EntityType> {
        val blocked = plugin.cfg.blockedMobs
        return EntityCompat.types.filter { t ->
            if (EntityCompat.nameOf(t) in blocked) return@filter false
            when (filter) {
                Filter.ALL -> true
                Filter.HOSTILE -> EntityCompat.isAggressiveType(t)
                Filter.PASSIVE -> !EntityCompat.isAggressiveType(t)
            }
        }
    }

    private fun pageCount(): Int = maxOf(1, (list().size + 44) / 45)

    override fun title(): String = "§8Mob Spawner §7(${page + 1}/${pageCount()})"

    override fun render(inv: Inventory) {
        val all = list()
        if (page >= pageCount()) page = pageCount() - 1
        val draft = plugin.service.draft(viewer)
        val s = draft.settings

        for (i in 0 until 45) slotTypes[i] = null
        val from = page * 45
        for (i in 0 until 45) {
            val t = all.getOrNull(from + i) ?: break
            slotTypes[i] = t
            val hostile = EntityCompat.isAggressiveType(t)
            inv.setItem(
                i, Items.mobIcon(t).named(
                    "§e" + EntityCompat.pretty(t),
                    "§7Behaviour: " + (if (hostile) "§cHostile" else "§aPassive"),
                    "",
                    "§eLeft-click §7configure & spawn",
                    "§eRight-click §7quick spawn x1",
                    "§eShift-click §7quick spawn x${draft.amount}"
                )
            )
        }

        inv.setItem(45, Items.stack("ARROW").named("§aPrevious page"))
        inv.setItem(46, Items.stack("HOPPER").named("§bFilter: §f${filter.label}", "§7Click to change"))
        inv.setItem(
            47, Items.stack("ANVIL").named(
                "§eEdit mode",
                "§7Closes this menu, then hit or right-click",
                "§7a mob to edit its attributes, timer,",
                "§7targeting and map damage."
            )
        )
        inv.setItem(
            48, Items.stack("TNT").named(
                "§cDelete mode",
                "§7Closes this menu, then hit or right-click",
                "§7a mob to remove it."
            )
        )
        inv.setItem(
            49, Items.stack("PAPER").named(
                "§bCurrent spawn settings",
                "§7Map damage: " + describeMapDamage(s.mapDamage),
                "§7Timer: " + (if (s.timerSeconds <= 0) "§fnone" else "§f${s.timerSeconds}s"),
                "§7Targeting: §f" + (s.targetMode?.label ?: "-"),
                "§7Amount: §f${draft.amount}",
                "",
                "§7Change these by left-clicking a mob."
            )
        )
        inv.setItem(52, Items.barrier().named("§cClose"))
        inv.setItem(53, Items.stack("ARROW").named("§aNext page"))
        fillEmpty(inv)
    }

    override fun onClick(slot: Int, click: ClickType) {
        if (!plugin.canUse(viewer)) {
            viewer.closeInventory()
            return
        }
        if (slot < 45) {
            val type = slotTypes[slot] ?: return
            when (click) {
                ClickType.RIGHT -> quickSpawn(type, 1)
                ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> quickSpawn(type, plugin.service.draft(viewer).amount)
                else -> ConfigGui(plugin, viewer, type, null).openLater()
            }
            return
        }
        when (slot) {
            45 -> if (page > 0) {
                page--
                reopenTitle()
            }
            53 -> if (page < pageCount() - 1) {
                page++
                reopenTitle()
            }
            46 -> {
                filter = Filter.values()[(filter.ordinal + 1) % Filter.values().size]
                page = 0
                reopenTitle()
            }
            47 -> {
                viewer.closeInventory()
                plugin.service.setMode(viewer, MobService.Mode.EDIT)
                plugin.msg(viewer, "Edit mode on: hit or right-click a mob within ${plugin.cfg.modeTimeoutSeconds}s. /mobspawn cancel to stop.")
            }
            48 -> {
                viewer.closeInventory()
                plugin.service.setMode(viewer, MobService.Mode.DELETE)
                plugin.msg(viewer, "Delete mode on: hit or right-click a mob within ${plugin.cfg.modeTimeoutSeconds}s. /mobspawn cancel to stop.")
            }
            52 -> viewer.closeInventory()
        }
    }

    /** The page number is part of the title, so page changes reopen the menu to refresh it. */
    private fun reopenTitle() {
        SpawnMenuGui(plugin, viewer, page, filter).openLater()
    }

    private fun quickSpawn(type: EntityType, amount: Int) {
        val draft = plugin.service.draft(viewer)
        val spawned = plugin.service.spawn(viewer, viewer.location, type, draft.settings, amount)
        plugin.msg(viewer, "Spawned ${spawned.size}x ${EntityCompat.pretty(type)}.")
    }

    companion object {
        fun describeMapDamage(v: Boolean?): String = when (v) {
            true -> "§aON"
            false -> "§cOFF"
            null -> "§7default"
        }
    }
}
