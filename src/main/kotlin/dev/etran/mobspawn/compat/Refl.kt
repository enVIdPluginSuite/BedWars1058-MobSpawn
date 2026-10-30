package dev.etran.mobspawn.compat

import java.lang.reflect.Method

/** Tiny reflection helpers. Every call swallows errors and returns null so features degrade gracefully. */
object Refl {

    private fun wrap(c: Class<*>): Class<*> = when (c) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        else -> c
    }

    private fun matches(types: Array<Class<*>>, args: Array<out Any?>): Boolean {
        for (i in types.indices) {
            val a = args[i] ?: continue
            if (!wrap(types[i]).isInstance(a)) return false
        }
        return true
    }

    fun findMethod(target: Class<*>, name: String, vararg args: Any?): Method? =
        target.methods.firstOrNull {
            it.name == name && it.parameterTypes.size == args.size && matches(it.parameterTypes, args)
        }

    /** Calls a public method by name on [target]; returns null when it is missing or throws. */
    fun call(target: Any?, name: String, vararg args: Any?): Any? {
        if (target == null) return null
        return try {
            val m = findMethod(target.javaClass, name, *args) ?: return null
            try {
                m.isAccessible = true
            } catch (ignored: Throwable) {
            }
            m.invoke(target, *args)
        } catch (t: Throwable) {
            null
        }
    }

    fun classOrNull(name: String): Class<*>? = try {
        Class.forName(name)
    } catch (t: Throwable) {
        null
    }
}
