package dev.etran.mobspawn.mob

import org.bukkit.entity.LivingEntity
import java.util.UUID

/** Who an aggressive mob is allowed to attack. */
enum class TargetMode(val label: String, val description: String) {
    ALL("All players", "Attacks any player (default)."),
    ENEMIES("Everyone but my team", "Ignores the spawner and their BedWars team."),
    PLAYER("One specific player", "Only attacks the player you pick."),
    TEAM("One BedWars team", "Only attacks members of the team you pick."),
    NONE("Nobody", "Stays idle and never targets anyone.");

    companion object {
        fun parse(s: String?): TargetMode = values().firstOrNull { it.name.equals(s, ignoreCase = true) } ?: ALL
    }
}

/**
 * Per-mob settings. `null` for [mapDamage] / [targetMode] means "untouched": the plugin leaves
 * that behaviour alone (used for mobs that were not spawned by us, e.g. BedWars golems/dragons).
 */
class MobSettings(
    var mapDamage: Boolean? = false,
    var timerSeconds: Int = 0,
    var targetMode: TargetMode? = TargetMode.ALL,
    var targetPlayerId: UUID? = null,
    var targetPlayerName: String? = null,
    var targetTeam: String? = null,
    var targetArena: String? = null
) {
    fun copy() = MobSettings(
        mapDamage, timerSeconds, targetMode, targetPlayerId, targetPlayerName, targetTeam, targetArena
    )
}

/** What the spawn menu remembers for a player between spawns. */
class Draft(var settings: MobSettings, var amount: Int = 1)

/** A mob the plugin keeps track of. */
class ManagedMob(
    var entity: LivingEntity,
    val settings: MobSettings,
    val ownerId: UUID?,
    val ownerName: String?,
    val ownerTeam: String?,
    val ownerArena: String?
) {
    val uuid: UUID = entity.uniqueId

    /** Seconds left on the despawn timer (only meaningful while settings.timerSeconds > 0). */
    var remaining: Int = 0

    /** Seconds the entity has been unloaded/unreachable. */
    var staleSeconds: Int = 0

    var registered: Boolean = false

    // Original name, restored when the timer is removed.
    var nameSaved: Boolean = false
    var originalName: String? = null
    var originalNameVisible: Boolean = false
}
