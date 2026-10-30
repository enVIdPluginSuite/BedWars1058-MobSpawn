package dev.etran.mobspawn.bedwars

import dev.etran.mobspawn.compat.Refl
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

data class TeamInfo(val name: String, val color: String)

/**
 * Reflection-based bridge to the BedWars1058 API. Reflection keeps the jar independent of the
 * BedWars1058 release it is installed next to. If anything is missing, every method simply
 * returns null/false and the plugin carries on without team features.
 */
class BedWarsHook(private val plugin: JavaPlugin) {

    private var arenaUtil: Any? = null

    val available: Boolean get() = arenaUtil != null

    init {
        try {
            val apiClass = Class.forName("com.andrei1058.bedwars.api.BedWars")
            @Suppress("UNCHECKED_CAST")
            val reg = Bukkit.getServicesManager().getRegistration(apiClass as Class<Any>)
            val api = reg?.provider
            arenaUtil = Refl.call(api, "getArenaUtil")
        } catch (t: Throwable) {
            plugin.logger.warning("BedWars1058 API not reachable (${t.javaClass.simpleName}); team features disabled.")
        }
    }

    fun arenaOf(p: Player): Any? = Refl.call(arenaUtil, "getArenaByPlayer", p)

    fun arenaName(p: Player): String? = Refl.call(arenaOf(p), "getArenaName") as? String

    fun isSpectating(p: Player): Boolean = Refl.call(arenaUtil, "isSpectating", p) as? Boolean ?: false

    fun teamName(p: Player): String? {
        val team = Refl.call(arenaOf(p), "getTeam", p) ?: return null
        return Refl.call(team, "getName") as? String
    }

    /** Teams of the arena [p] is currently in (empty when they are not in an arena). */
    fun teamsOf(p: Player): List<TeamInfo> {
        val arena = arenaOf(p) ?: return emptyList()
        val teams = Refl.call(arena, "getTeams") as? Iterable<*> ?: return emptyList()
        val out = ArrayList<TeamInfo>()
        for (t in teams) {
            val name = Refl.call(t, "getName") as? String ?: continue
            val color = Refl.call(t, "getColor")
            val colorName = (color as? Enum<*>)?.name ?: name
            out.add(TeamInfo(name, colorName))
        }
        return out
    }
}
