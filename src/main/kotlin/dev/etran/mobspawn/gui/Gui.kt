package dev.etran.mobspawn.gui

import dev.etran.mobspawn.MobSpawnPlugin
import dev.etran.mobspawn.compat.Items
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/**
 * Base class for every menu. Menus are identified by being the inventory *holder*, so no
 * InventoryView API (which changed from a class to an interface in 1.21) is ever touched.
 */
abstract class Gui(protected val plugin: MobSpawnPlugin, protected val viewer: Player) : InventoryHolder {

    private var inv: Inventory? = null

    protected abstract val rows: Int
    protected abstract fun title(): String
    protected abstract fun render(inv: Inventory)

    /** [slot] is always inside this menu's own inventory. */
    abstract fun onClick(slot: Int, click: ClickType)

    override fun getInventory(): Inventory = inv ?: throw IllegalStateException("GUI has not been opened yet")

    fun open() {
        val i = Bukkit.createInventory(this, rows * 9, safeTitle(title()))
        inv = i
        render(i)
        viewer.openInventory(i)
    }

    /** Opens on the next tick; required when switching menus from inside a click handler. */
    fun openLater() {
        Bukkit.getScheduler().runTask(plugin, Runnable { open() })
    }

    fun refresh() {
        val i = inv ?: return
        i.clear()
        render(i)
    }

    protected fun fillEmpty(i: Inventory) {
        val f = Items.filler()
        for (s in 0 until i.size) if (i.getItem(s) == null) i.setItem(s, f.clone())
    }

    /** Pre-1.14 inventory titles are limited to 32 characters. */
    private fun safeTitle(t: String): String {
        if (t.length <= 32) return t
        return t.substring(0, 32).trimEnd('§')
    }
}
