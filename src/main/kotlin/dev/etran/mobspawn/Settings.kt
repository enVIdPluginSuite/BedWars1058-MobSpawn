package dev.etran.mobspawn

import dev.etran.mobspawn.mob.MobSettings
import dev.etran.mobspawn.mob.TargetMode
import org.bukkit.configuration.file.FileConfiguration

/** Typed, always-fresh view of config.yml (reads straight from the live config, so /mobspawn reload works). */
class Settings(private val plugin: MobSpawnPlugin) {
    private val c: FileConfiguration get() = plugin.config

    val defaultMapDamage: Boolean get() = c.getBoolean("defaults.map-damage", false)
    val defaultTimer: Int get() = c.getInt("defaults.timer-seconds", 0).coerceAtLeast(0)
    val defaultTargetMode: TargetMode
        get() = TargetMode.parse(c.getString("defaults.target-mode", "ALL")).let {
            if (it == TargetMode.PLAYER || it == TargetMode.TEAM) TargetMode.ALL else it
        }

    val allowCreative: Boolean get() = c.getBoolean("access.allow-creative", true)
    val creativeAttributes: Boolean get() = c.getBoolean("access.creative-can-edit-attributes", false)

    val maxAmount: Int get() = c.getInt("limits.max-spawn-amount", 20).coerceAtLeast(1)
    val maxTimer: Int get() = c.getInt("limits.max-timer-seconds", 3600).coerceAtLeast(1)
    val blockedMobs: Set<String>
        get() = c.getStringList("limits.blocked-mobs").map { it.trim().uppercase() }.toSet()

    val modeTimeoutSeconds: Int get() = c.getInt("behaviour.mode-timeout-seconds", 30).coerceAtLeast(5)
    val showTimerName: Boolean get() = c.getBoolean("behaviour.show-timer-name", true)
    val targetScanTicks: Long get() = c.getInt("behaviour.target-scan-interval-ticks", 10).coerceAtLeast(2).toLong()
    val defaultTargetRange: Double get() = c.getDouble("behaviour.default-target-range", 24.0)
    val overrideCancelledMapDamage: Boolean get() = c.getBoolean("behaviour.override-cancelled-map-damage", true)

    fun newDefaultSettings() = MobSettings(
        mapDamage = defaultMapDamage,
        timerSeconds = defaultTimer,
        targetMode = defaultTargetMode
    )
}
