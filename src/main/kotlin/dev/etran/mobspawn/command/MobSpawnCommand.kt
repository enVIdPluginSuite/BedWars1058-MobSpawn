package dev.etran.mobspawn.command

import dev.etran.mobspawn.MobSpawnPlugin
import dev.etran.mobspawn.compat.EntityCompat
import dev.etran.mobspawn.gui.SpawnMenuGui
import dev.etran.mobspawn.mob.MobService
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class MobSpawnCommand(private val plugin: MobSpawnPlugin) : CommandExecutor, TabCompleter {

    private val subcommands = listOf("menu", "spawn", "edit", "delete", "cancel", "clear", "reload", "help")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val sub = args.firstOrNull()?.lowercase() ?: "menu"

        if (sub == "reload") {
            if (!plugin.isAdmin(sender)) return deny(sender)
            plugin.reload()
            plugin.msg(sender, "Configuration reloaded.")
            return true
        }

        if (sender !is Player) {
            plugin.msg(sender, "This command can only be used in game.")
            return true
        }
        if (!plugin.canUse(sender)) return deny(sender)

        when (sub) {
            "menu", "gui", "open" -> SpawnMenuGui(plugin, sender).open()
            "spawn" -> spawn(sender, args)
            "edit" -> toggleMode(sender, MobService.Mode.EDIT)
            "delete", "remove" -> toggleMode(sender, MobService.Mode.DELETE)
            "cancel" -> {
                plugin.service.clearMode(sender)
                plugin.msg(sender, "Click mode cancelled.")
            }
            "clear" -> clear(sender, args)
            else -> help(sender)
        }
        return true
    }

    private fun deny(sender: CommandSender): Boolean {
        plugin.msg(sender, "§cYou need to be in creative mode or have the right permission to do that.")
        return true
    }

    private fun toggleMode(p: Player, mode: MobService.Mode) {
        if (plugin.service.getMode(p) == mode) {
            plugin.service.clearMode(p)
            plugin.msg(p, "Click mode cancelled.")
            return
        }
        plugin.service.setMode(p, mode)
        val what = if (mode == MobService.Mode.EDIT) "open its settings" else "delete it"
        plugin.msg(p, "Hit or right-click a mob to $what (active for ${plugin.cfg.modeTimeoutSeconds}s). " +
            "Run the command again or /mobspawn cancel to stop.")
    }

    private fun spawn(p: Player, args: Array<out String>) {
        if (args.size < 2) {
            plugin.msg(p, "Usage: /mobspawn spawn <mob> [amount]")
            return
        }
        val type = EntityCompat.byName(args[1])
        if (type == null) {
            plugin.msg(p, "Unknown mob '${args[1]}'. Tab-complete to see what this server version supports.")
            return
        }
        if (EntityCompat.nameOf(type) in plugin.cfg.blockedMobs) {
            plugin.msg(p, "${EntityCompat.pretty(type)} is blocked in the config.")
            return
        }
        val amount = (args.getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(1, plugin.cfg.maxAmount)
        val draft = plugin.service.draft(p)
        val spawned = plugin.service.spawn(p, p.location, type, draft.settings, amount)
        plugin.msg(p, "Spawned ${spawned.size}x ${EntityCompat.pretty(type)} using your menu settings.")
    }

    private fun clear(p: Player, args: Array<out String>) {
        if (!plugin.isAdmin(p)) {
            deny(p)
            return
        }
        val arg = args.getOrNull(1)
        val n = if (arg == null || arg.equals("all", true)) {
            plugin.service.clear(null, 0.0)
        } else {
            val radius = arg.toDoubleOrNull()
            if (radius == null || radius <= 0) {
                plugin.msg(p, "Usage: /mobspawn clear [all|radius]")
                return
            }
            plugin.service.clear(p.location, radius)
        }
        plugin.msg(p, "Removed $n mob(s) spawned or edited through this plugin.")
    }

    private fun help(p: Player) {
        plugin.msg(p, "Commands:")
        p.sendMessage("§b/mobspawn §7- open the spawn menu")
        p.sendMessage("§b/mobspawn spawn <mob> [amount] §7- quick spawn with your menu settings")
        p.sendMessage("§b/mobspawn edit §7- then hit / right-click a mob to edit it")
        p.sendMessage("§b/mobspawn delete §7- then hit / right-click a mob to remove it")
        p.sendMessage("§b/mobspawn cancel §7- leave edit/delete mode")
        p.sendMessage("§b/mobspawn clear [all|radius] §7- remove managed mobs (admin)")
        p.sendMessage("§b/mobspawn reload §7- reload config (admin)")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            return subcommands.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2 && args[0].equals("spawn", ignoreCase = true)) {
            return EntityCompat.types.map { EntityCompat.nameOf(it).lowercase() }
                .filter { it.startsWith(args[1], ignoreCase = true) }
        }
        if (args.size == 3 && args[0].equals("spawn", ignoreCase = true)) {
            return listOf("1", "3", "5", "10").filter { it.startsWith(args[2]) }
        }
        if (args.size == 2 && args[0].equals("clear", ignoreCase = true)) {
            return listOf("all", "10", "25", "50").filter { it.startsWith(args[1], ignoreCase = true) }
        }
        return emptyList()
    }
}
