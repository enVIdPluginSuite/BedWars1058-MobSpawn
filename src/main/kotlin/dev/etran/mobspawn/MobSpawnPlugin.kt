package dev.etran.mobspawn

import dev.etran.mobspawn.bedwars.BedWarsHook
import dev.etran.mobspawn.command.MobSpawnCommand
import dev.etran.mobspawn.compat.Version
import dev.etran.mobspawn.listener.GuiListener
import dev.etran.mobspawn.listener.MobListener
import dev.etran.mobspawn.mob.MobService
import org.bukkit.GameMode
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class MobSpawnPlugin : JavaPlugin() {

    lateinit var cfg: Settings
        private set
    lateinit var bedWars: BedWarsHook
        private set
    lateinit var service: MobService
        private set

    override fun onEnable() {
        saveDefaultConfig()
        cfg = Settings(this)
        bedWars = BedWarsHook(this)
        service = MobService(this)

        server.pluginManager.registerEvents(GuiListener(), this)
        server.pluginManager.registerEvents(MobListener(this), this)

        val command = MobSpawnCommand(this)
        getCommand("mobspawn")?.let {
            it.executor = command
            it.tabCompleter = command
        }

        service.start()
        logger.info("Enabled on server version ${Version.raw}; attribute API: ${Version.hasAttributeApi}; " +
            "BedWars1058 hook: ${bedWars.available}")
    }

    override fun onDisable() {
        service.stop()
    }

    fun reload() {
        reloadConfig()
        service.start() // re-reads the target scan interval
    }

    // ---- permissions --------------------------------------------------------------------------

    /** Spawn / edit / delete: permission, or creative mode when enabled in the config. */
    fun canUse(p: Player): Boolean =
        p.hasPermission("mobspawn.use") || (cfg.allowCreative && p.gameMode == GameMode.CREATIVE)

    /** Attribute editing: admin permission (optionally also creative). */
    fun canEditAttributes(p: Player): Boolean =
        p.hasPermission("mobspawn.admin") || (cfg.creativeAttributes && p.gameMode == GameMode.CREATIVE)

    fun isAdmin(sender: CommandSender): Boolean = sender.hasPermission("mobspawn.admin")

    fun msg(to: CommandSender, text: String) {
        to.sendMessage("§8[§bMobSpawn§8] §7$text")
    }
}
