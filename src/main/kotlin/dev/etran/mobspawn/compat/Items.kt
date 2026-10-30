package dev.etran.mobspawn.compat

import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta

/**
 * Item/material helpers that work before AND after the 1.13 "flattening".
 * Materials are always looked up by name so that no removed constant is ever linked.
 */
object Items {

    /** First material that exists under any of [names]. */
    fun mat(vararg names: String): Material? {
        for (n in names) {
            val m = try {
                Material.matchMaterial(n)
            } catch (t: Throwable) {
                null
            }
            if (m != null) return m
        }
        return null
    }

    private fun legacyStack(m: Material, amount: Int, data: Int): ItemStack = try {
        ItemStack(m, amount, data.toShort())
    } catch (t: Throwable) {
        ItemStack(m, amount)
    }

    /**
     * Builds a stack from a modern material name, falling back to the pre-1.13 name + data value.
     * Example: stack("LIME_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", 5)
     */
    fun stack(modern: String, legacy: String? = null, data: Int = 0, amount: Int = 1): ItemStack {
        mat(modern)?.let { return ItemStack(it, amount) }
        if (legacy != null) mat(legacy)?.let { return legacyStack(it, amount, data) }
        return ItemStack(mat("STONE")!!, amount)
    }

    fun pane(modern: String, legacyData: Int): ItemStack = stack(modern, "STAINED_GLASS_PANE", legacyData)

    fun filler(): ItemStack = pane("GRAY_STAINED_GLASS_PANE", 7).named(" ")

    fun barrier(): ItemStack = stack("BARRIER", "BARRIER")

    fun clock(): ItemStack = stack("CLOCK", "WATCH")

    fun playerHead(name: String): ItemStack {
        val item = stack("PLAYER_HEAD", "SKULL_ITEM", 3)
        try {
            val meta = item.itemMeta
            if (meta is SkullMeta) {
                meta.owner = name
                item.itemMeta = meta
            }
        } catch (t: Throwable) {
            // Skull owner API differs between versions; a plain head is fine.
        }
        return item
    }

    /** Wool block for a BedWars team colour name (RED, AQUA, ...). */
    fun wool(color: String): ItemStack {
        val c = color.uppercase()
        val legacyData = when (c) {
            "WHITE" -> 0
            "ORANGE" -> 1
            "MAGENTA" -> 2
            "LIGHT_BLUE" -> 3
            "YELLOW" -> 4
            "LIME" -> 5
            "PINK" -> 6
            "GRAY" -> 7
            "LIGHT_GRAY", "SILVER" -> 8
            "CYAN", "AQUA" -> 9
            "PURPLE" -> 10
            "BLUE" -> 11
            "BROWN" -> 12
            "GREEN" -> 13
            "RED" -> 14
            "BLACK" -> 15
            else -> 0
        }
        val modernName = when (c) {
            "AQUA" -> "CYAN"
            "SILVER" -> "LIGHT_GRAY"
            else -> c
        }
        return stack(modernName + "_WOOL", "WOOL", legacyData)
    }

    // ---- mob icons -----------------------------------------------------------------------------

    private val eggAliases = mapOf(
        "MUSHROOM_COW" to listOf("MOOSHROOM"),
        "PIG_ZOMBIE" to listOf("ZOMBIE_PIGMAN", "ZOMBIFIED_PIGLIN"),
        "ZOMBIFIED_PIGLIN" to listOf("ZOMBIE_PIGMAN"),
        "SNOWMAN" to listOf("SNOW_GOLEM"),
        "SNOW_GOLEM" to listOf("SNOWMAN")
    )

    private val fallbackIcons = mapOf(
        "ENDER_DRAGON" to "DRAGON_EGG",
        "WITHER" to "NETHER_STAR",
        "IRON_GOLEM" to "IRON_BLOCK",
        "SNOWMAN" to "SNOW_BLOCK",
        "SNOW_GOLEM" to "SNOW_BLOCK",
        "GIANT" to "ZOMBIE_HEAD",
        "ILLUSIONER" to "ENDER_EYE",
        "WARDEN" to "SCULK_SHRIEKER"
    )

    fun mobIcon(type: EntityType): ItemStack {
        val n = EntityCompat.nameOf(type)
        // Modern spawn eggs: <NAME>_SPAWN_EGG
        mat(n + "_SPAWN_EGG")?.let { return ItemStack(it) }
        eggAliases[n]?.forEach { alias -> mat(alias + "_SPAWN_EGG")?.let { return ItemStack(it) } }
        // Types that have no spawn egg get a themed block/item.
        fallbackIcons[n]?.let { name -> mat(name, "SKULL_ITEM")?.let { return ItemStack(it) } }
        // Legacy spawn eggs: MONSTER_EGG with the entity id as data value.
        val legacy = mat("MONSTER_EGG")
        if (legacy != null) {
            val id = try {
                type.typeId.toInt()
            } catch (t: Throwable) {
                0
            }
            if (id > 0) return legacyStack(legacy, 1, id)
        }
        return ItemStack(mat("EGG", "NAME_TAG")!!)
    }
}

/** Sets name + lore (and hides noisy vanilla tooltips) on an item. */
fun ItemStack.named(name: String, vararg lore: String): ItemStack {
    try {
        val meta = this.itemMeta ?: return this
        meta.displayName = name
        if (lore.isNotEmpty()) meta.lore = lore.toList()
        try {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
        } catch (t: Throwable) {
            // flag set differs between versions
        }
        this.itemMeta = meta
    } catch (t: Throwable) {
        // never let a cosmetic failure break a GUI
    }
    return this
}
