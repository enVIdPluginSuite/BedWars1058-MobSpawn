package dev.etran.mobspawn.compat

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** The mob attributes the UI can edit. */
enum class AttrKey(
    val label: String,
    val modernNames: List<String>,
    val legacyName: String?,
    val step: Double,
    val bigStep: Double,
    val min: Double,
    val max: Double,
    val icon: List<String>
) {
    MAX_HEALTH(
        "Max Health", listOf("MAX_HEALTH", "GENERIC_MAX_HEALTH"), "generic.maxHealth",
        1.0, 10.0, 1.0, 1024.0, listOf("GOLDEN_APPLE")
    ),
    MOVEMENT_SPEED(
        "Movement Speed", listOf("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED"), "generic.movementSpeed",
        0.01, 0.05, 0.0, 5.0, listOf("SUGAR")
    ),
    ATTACK_DAMAGE(
        "Attack Damage", listOf("ATTACK_DAMAGE", "GENERIC_ATTACK_DAMAGE"), "generic.attackDamage",
        1.0, 5.0, 0.0, 2048.0, listOf("IRON_SWORD")
    ),
    FOLLOW_RANGE(
        "Follow Range", listOf("FOLLOW_RANGE", "GENERIC_FOLLOW_RANGE"), "generic.followRange",
        2.0, 8.0, 0.0, 2048.0, listOf("COMPASS")
    ),
    KNOCKBACK_RESISTANCE(
        "Knockback Resistance", listOf("KNOCKBACK_RESISTANCE", "GENERIC_KNOCKBACK_RESISTANCE"),
        "generic.knockbackResistance", 0.1, 0.5, 0.0, 1.0, listOf("IRON_CHESTPLATE")
    ),
    /** 1.20.5+ only; reported as unavailable on older servers. */
    SCALE(
        "Scale", listOf("SCALE", "GENERIC_SCALE"), null,
        0.1, 0.5, 0.0625, 16.0, listOf("SLIME_BALL")
    )
}

/**
 * Reads/writes entity attributes on every supported version:
 *  - 1.9+ : through the org.bukkit.attribute API (reflection, because enum constant names changed
 *           in 1.21.3 and the type is no longer an enum on the newest versions);
 *  - 1.8  : through CraftBukkit/NMS (reflection), because Bukkit had no attribute API yet.
 */
object Attrs {

    fun read(e: LivingEntity, key: AttrKey): Double? = try {
        if (Version.hasAttributeApi) Modern.read(e, key) else Legacy.read(e, key)
    } catch (t: Throwable) {
        null
    }

    /** Returns true when the value was applied. [value] is clamped to the attribute's sane range. */
    fun write(e: LivingEntity, key: AttrKey, value: Double): Boolean {
        val v = Math.round(value.coerceIn(key.min, key.max) * 1000.0) / 1000.0
        val ok = try {
            if (Version.hasAttributeApi) Modern.write(e, key, v) else Legacy.write(e, key, v)
        } catch (t: Throwable) {
            false
        }
        if (ok && key == AttrKey.MAX_HEALTH) {
            try {
                e.health = v // top the mob up so the change is visible straight away
            } catch (t: Throwable) {
            }
        }
        return ok
    }

    // ------------------------------------------------------------------------------------------
    private object Modern {
        private val attributeClass: Class<*> = Class.forName("org.bukkit.attribute.Attribute")
        private val instanceClass: Class<*> = Class.forName("org.bukkit.attribute.AttributeInstance")
        private val getAttribute: Method = (Refl.classOrNull("org.bukkit.attribute.Attributable")
            ?: Class.forName("org.bukkit.entity.LivingEntity")).getMethod("getAttribute", attributeClass)
        private val getValue: Method = instanceClass.getMethod("getValue")
        private val setBase: Method = instanceClass.getMethod("setBaseValue", java.lang.Double.TYPE)
        private val cache = HashMap<AttrKey, Any?>()

        private fun constant(key: AttrKey): Any? = cache.getOrPut(key) {
            key.modernNames.firstNotNullOfOrNull { n ->
                try {
                    attributeClass.getField(n).get(null)
                } catch (t: Throwable) {
                    null
                }
            }
        }

        private fun instance(e: LivingEntity, key: AttrKey): Any? {
            val c = constant(key) ?: return null
            return getAttribute.invoke(e, c)
        }

        fun read(e: LivingEntity, key: AttrKey): Double? {
            val inst = instance(e, key) ?: return null
            return getValue.invoke(inst) as? Double
        }

        fun write(e: LivingEntity, key: AttrKey, v: Double): Boolean {
            val inst = instance(e, key) ?: return false
            setBase.invoke(inst, v)
            return true
        }
    }

    // ------------------------------------------------------------------------------------------
    private object Legacy {
        private val craftPackage: String = Bukkit.getServer().javaClass.`package`.name
        private val nmsPackage: String = "net.minecraft.server." + craftPackage.substringAfterLast('.')

        private val getHandle: Method = Class.forName("$craftPackage.entity.CraftEntity").getMethod("getHandle")
        private val iAttribute: Class<*> = Class.forName("$nmsPackage.IAttribute")
        private val getAttributeInstance: Method =
            Class.forName("$nmsPackage.EntityLiving").getMethod("getAttributeInstance", iAttribute)
        private val instanceClass: Class<*> = Class.forName("$nmsPackage.AttributeInstance")
        private val getValue: Method = instanceClass.getMethod("getValue")
        private val setValue: Method = instanceClass.getMethod("setValue", java.lang.Double.TYPE)
        private val getName: Method = iAttribute.getMethod("getName")

        /** attribute id string ("generic.maxHealth") -> GenericAttributes constant */
        private val constants: Map<String, Any> by lazy {
            val map = HashMap<String, Any>()
            for (f in Class.forName("$nmsPackage.GenericAttributes").fields) {
                if (!Modifier.isStatic(f.modifiers) || !iAttribute.isAssignableFrom(f.type)) continue
                val a = f.get(null) ?: continue
                map[getName.invoke(a) as String] = a
            }
            map
        }

        private fun instance(e: LivingEntity, key: AttrKey): Any? {
            val id = key.legacyName ?: return null
            val attr = constants[id] ?: return null
            return getAttributeInstance.invoke(getHandle.invoke(e), attr)
        }

        fun read(e: LivingEntity, key: AttrKey): Double? {
            val inst = instance(e, key) ?: return null
            return getValue.invoke(inst) as? Double
        }

        fun write(e: LivingEntity, key: AttrKey, v: Double): Boolean {
            val inst = instance(e, key) ?: return false
            setValue.invoke(inst, v)
            return true
        }
    }
}
