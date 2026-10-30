package dev.etran.mobspawn.compat

import org.bukkit.entity.Creature
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.UUID

/** Entity helpers that survive the API changes between 1.8 and 26.x. */
object EntityCompat {

    /** Name of an EntityType whether it is an enum (all versions so far) or a registry-style class. */
    fun nameOf(type: Any): String = (type as? Enum<*>)?.name ?: type.toString().uppercase()

    fun pretty(name: String): String =
        name.lowercase().split('_').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    fun pretty(type: EntityType): String = pretty(nameOf(type))

    private val aggressiveNames = setOf(
        "IRON_GOLEM", "SLIME", "MAGMA_CUBE", "GHAST", "ENDER_DRAGON", "WOLF", "PHANTOM", "SHULKER",
        "HOGLIN", "ZOGLIN", "ENDERMITE", "SILVERFISH", "VEX", "ENDERMAN", "PIG_ZOMBIE", "ZOMBIFIED_PIGLIN",
        "BEE", "GUARDIAN", "ELDER_GUARDIAN", "WITHER", "RAVAGER", "WARDEN", "BREEZE", "CREAKING"
    )

    fun isAggressiveType(type: EntityType): Boolean {
        if (nameOf(type) in aggressiveNames) return true
        val cls = try {
            type.entityClass
        } catch (t: Throwable) {
            null
        }
        return cls != null && Monster::class.java.isAssignableFrom(cls)
    }

    fun isAggressive(e: LivingEntity): Boolean = try {
        e is Monster || nameOf(e.type) in aggressiveNames
    } catch (t: Throwable) {
        nameOf(e.type) in aggressiveNames
    }

    // ---- entity types -------------------------------------------------------------------------

    private fun allTypes(): List<EntityType> {
        val enums = EntityType::class.java.enumConstants
        if (enums != null) return enums.toList()
        // Registry-style class: collect its public static constants.
        return EntityType::class.java.fields
            .filter { Modifier.isStatic(it.modifiers) && it.type == EntityType::class.java }
            .mapNotNull {
                try {
                    it.get(null) as? EntityType
                } catch (t: Throwable) {
                    null
                }
            }
    }

    private fun spawnable(t: EntityType): Boolean {
        val n = nameOf(t)
        if (n == "PLAYER" || n == "UNKNOWN" || n == "ARMOR_STAND") return false
        val cls = try {
            t.entityClass
        } catch (e: Throwable) {
            null
        } ?: return false
        if (!LivingEntity::class.java.isAssignableFrom(cls)) return false
        return try {
            t.isSpawnable
        } catch (e: Throwable) {
            true
        }
    }

    /** Every living, spawnable type of the running server, sorted by display name. */
    val types: List<EntityType> by lazy {
        allTypes().filter { spawnable(it) }.sortedBy { pretty(it) }
    }

    private val aliases = mapOf(
        "SNOW_GOLEM" to "SNOWMAN", "SNOWMAN" to "SNOW_GOLEM",
        "MOOSHROOM" to "MUSHROOM_COW", "MUSHROOM_COW" to "MOOSHROOM",
        "ZOMBIFIED_PIGLIN" to "PIG_ZOMBIE", "PIG_ZOMBIE" to "ZOMBIFIED_PIGLIN",
        "ZOMBIE_PIGMAN" to "PIG_ZOMBIE"
    )

    fun byName(input: String): EntityType? {
        val key = input.trim().uppercase().replace(' ', '_').replace('-', '_')
        val wanted = listOfNotNull(key, aliases[key])
        return types.firstOrNull { nameOf(it) in wanted }
    }

    // ---- targeting ----------------------------------------------------------------------------

    private val mobClass: Class<*>? by lazy { Refl.classOrNull("org.bukkit.entity.Mob") }
    private val mobSetTarget: Method? by lazy {
        mobClass?.methods?.firstOrNull { it.name == "setTarget" && it.parameterTypes.size == 1 }
    }
    private val mobGetTarget: Method? by lazy {
        mobClass?.methods?.firstOrNull { it.name == "getTarget" && it.parameterTypes.isEmpty() }
    }

    fun getTarget(e: LivingEntity): LivingEntity? {
        try {
            if (e is Creature) return e.target
        } catch (t: Throwable) {
        }
        return try {
            val c = mobClass
            if (c != null && c.isInstance(e)) mobGetTarget?.invoke(e) as? LivingEntity else null
        } catch (t: Throwable) {
            null
        }
    }

    fun setTarget(e: LivingEntity, target: LivingEntity?): Boolean {
        try {
            if (e is Creature) {
                e.target = target
                return true
            }
        } catch (t: Throwable) {
        }
        return try {
            val c = mobClass
            if (c != null && c.isInstance(e) && mobSetTarget != null) {
                mobSetTarget!!.invoke(e, target)
                true
            } else false
        } catch (t: Throwable) {
            false
        }
    }

    // ---- lookup -------------------------------------------------------------------------------

    private val getEntityMethod: Method? by lazy {
        try {
            org.bukkit.Bukkit::class.java.getMethod("getEntity", UUID::class.java)
        } catch (t: Throwable) {
            null
        }
    }

    /** Finds a loaded entity by UUID (Bukkit#getEntity exists from 1.12; older versions scan worlds). */
    fun findEntity(id: UUID): LivingEntity? {
        try {
            val m = getEntityMethod
            if (m != null) return m.invoke(null, id) as? LivingEntity
        } catch (t: Throwable) {
        }
        for (w in org.bukkit.Bukkit.getWorlds()) {
            for (le in w.livingEntities) if (le.uniqueId == id) return le
        }
        return null
    }
}
