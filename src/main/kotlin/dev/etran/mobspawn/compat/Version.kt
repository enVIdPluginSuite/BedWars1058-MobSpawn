package dev.etran.mobspawn.compat

import org.bukkit.Bukkit

/**
 * Parses the running server version. Handles both the classic scheme ("1.8.8-R0.1-SNAPSHOT")
 * and the year-based scheme used from 26.1 on ("26.3-R0.1-SNAPSHOT").
 */
object Version {
    val raw: String = try {
        Bukkit.getBukkitVersion()
    } catch (t: Throwable) {
        "1.8.8"
    }

    private val parts: IntArray = run {
        val m = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?""").find(raw)
        intArrayOf(
            m?.groupValues?.get(1)?.toIntOrNull() ?: 1,
            m?.groupValues?.get(2)?.toIntOrNull() ?: 8,
            m?.groupValues?.getOrNull(3)?.toIntOrNull() ?: 0
        )
    }

    val major: Int get() = parts[0]
    val minor: Int get() = parts[1]
    val patch: Int get() = parts[2]

    /** True when the server is at least [maj].[min]. 26.x counts as newer than any 1.x. */
    fun atLeast(maj: Int, min: Int): Boolean = major > maj || (major == maj && minor >= min)

    /** The org.bukkit.attribute API arrived in 1.9. */
    val hasAttributeApi: Boolean by lazy {
        try {
            Class.forName("org.bukkit.attribute.Attribute")
            true
        } catch (t: Throwable) {
            false
        }
    }

    override fun toString(): String = "$major.$minor.$patch ($raw)"
}
