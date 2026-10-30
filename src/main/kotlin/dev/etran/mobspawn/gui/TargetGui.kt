package dev.etran.mobspawn.gui

import dev.etran.mobspawn.MobSpawnPlugin
import dev.etran.mobspawn.bedwars.TeamInfo
import dev.etran.mobspawn.compat.Items
import dev.etran.mobspawn.compat.named
import dev.etran.mobspawn.mob.TargetMode
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory

/** Choose who an aggressive mob may attack. */
class TargetGui(plugin: MobSpawnPlugin, viewer: Player, val parent: ConfigGui) : Gui(plugin, viewer) {

    override val rows = 3

    private val modeSlots = intArrayOf(10, 11, 12, 13, 14)
    private val modes = TargetMode.values()

    override fun title(): String = "§8Who does it attack?"

    override fun render(inv: Inventory) {
        val s = parent.settings
        for (i in modes.indices) {
            val mode = modes[i]
            val selected = s.targetMode == mode
            val icon = when (mode) {
                TargetMode.ALL -> Items.stack("DIAMOND_SWORD")
                TargetMode.ENEMIES -> Items.stack("IRON_SWORD")
                TargetMode.PLAYER -> Items.playerHead(s.targetPlayerName ?: viewer.name)
                TargetMode.TEAM -> Items.wool("RED")
                TargetMode.NONE -> Items.barrier()
            }
            val lore = ArrayList<String>()
            lore.add("§7" + mode.description)
            if (mode == TargetMode.PLAYER && s.targetPlayerName != null) lore.add("§7Picked: §f" + s.targetPlayerName)
            if (mode == TargetMode.TEAM && s.targetTeam != null) lore.add("§7Picked: §f" + s.targetTeam)
            if (mode == TargetMode.PLAYER || mode == TargetMode.TEAM) lore.add("§eClick §7to pick")
            if (selected) lore.add("§a► Currently selected")
            inv.setItem(modeSlots[i], icon.named((if (selected) "§a" else "§e") + mode.label, *lore.toTypedArray()))
        }
        inv.setItem(22, Items.stack("ARROW").named("§aBack"))
        fillEmpty(inv)
    }

    override fun onClick(slot: Int, click: ClickType) {
        if (!plugin.canUse(viewer)) {
            viewer.closeInventory()
            return
        }
        if (slot == 22) {
            parent.openLater()
            return
        }
        val idx = modeSlots.indexOf(slot)
        if (idx < 0) return
        when (val mode = modes[idx]) {
            TargetMode.PLAYER -> PlayerPickerGui(plugin, viewer, this).openLater()
            TargetMode.TEAM -> {
                if (plugin.bedWars.teamsOf(viewer).isEmpty()) {
                    plugin.msg(viewer, "You must be inside a BedWars arena to pick one of its teams.")
                } else {
                    TeamPickerGui(plugin, viewer, this).openLater()
                }
            }
            else -> {
                parent.settings.targetMode = mode
                parent.onSettingsChanged(false)
                refresh()
            }
        }
    }

    fun apply(change: (dev.etran.mobspawn.mob.MobSettings) -> Unit) {
        change(parent.settings)
        parent.onSettingsChanged(false)
    }
}

/** Online-player picker (heads), paged. */
class PlayerPickerGui(plugin: MobSpawnPlugin, viewer: Player, private val parent: TargetGui) : Gui(plugin, viewer) {

    override val rows = 6

    private var page = 0
    private val slotPlayers = arrayOfNulls<Player>(45)

    private fun players(): List<Player> = Bukkit.getOnlinePlayers().toList().sortedBy { it.name.lowercase() }

    override fun title(): String = "§8Pick a player"

    override fun render(inv: Inventory) {
        val all = players()
        val pages = maxOf(1, (all.size + 44) / 45)
        if (page >= pages) page = pages - 1
        for (i in 0 until 45) slotPlayers[i] = null
        for (i in 0 until 45) {
            val p = all.getOrNull(page * 45 + i) ?: break
            slotPlayers[i] = p
            inv.setItem(i, Items.playerHead(p.name).named("§e" + p.name, "§eClick §7to target this player"))
        }
        inv.setItem(45, Items.stack("ARROW").named("§aPrevious page"))
        inv.setItem(49, Items.stack("ARROW").named("§aBack"))
        inv.setItem(53, Items.stack("ARROW").named("§aNext page"))
        fillEmpty(inv)
    }

    override fun onClick(slot: Int, click: ClickType) {
        if (!plugin.canUse(viewer)) {
            viewer.closeInventory()
            return
        }
        when {
            slot < 45 -> {
                val p = slotPlayers[slot] ?: return
                parent.apply {
                    it.targetMode = TargetMode.PLAYER
                    it.targetPlayerId = p.uniqueId
                    it.targetPlayerName = p.name
                }
                parent.openLater()
            }
            slot == 45 -> if (page > 0) { page--; refresh() }
            slot == 53 -> { page++; refresh() }
            slot == 49 -> parent.openLater()
        }
    }
}

/** Picker for the BedWars teams of the arena the viewer is standing in. */
class TeamPickerGui(plugin: MobSpawnPlugin, viewer: Player, private val parent: TargetGui) : Gui(plugin, viewer) {

    override val rows = 3

    private var teams: List<TeamInfo> = emptyList()

    override fun title(): String = "§8Pick a team"

    override fun render(inv: Inventory) {
        teams = plugin.bedWars.teamsOf(viewer)
        for (i in teams.indices) {
            if (i >= 9) break
            val t = teams[i]
            inv.setItem(9 + i, Items.wool(t.color).named("§e" + t.name, "§eClick §7to target this team"))
        }
        inv.setItem(22, Items.stack("ARROW").named("§aBack"))
        fillEmpty(inv)
    }

    override fun onClick(slot: Int, click: ClickType) {
        if (!plugin.canUse(viewer)) {
            viewer.closeInventory()
            return
        }
        if (slot == 22) {
            parent.openLater()
            return
        }
        val t = teams.getOrNull(slot - 9) ?: return
        val arena = plugin.bedWars.arenaName(viewer)
        parent.apply {
            it.targetMode = TargetMode.TEAM
            it.targetTeam = t.name
            it.targetArena = arena
        }
        parent.openLater()
    }
}
