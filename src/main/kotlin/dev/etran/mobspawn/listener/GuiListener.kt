package dev.etran.mobspawn.listener

import dev.etran.mobspawn.gui.Gui
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

/**
 * Routes clicks to menus. Everything in a menu is cancelled so items can never be taken out.
 * Only InventoryEvent#getInventory is used (never InventoryView), which keeps this binary
 * compatible with every server version.
 */
class GuiListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onClick(e: InventoryClickEvent) {
        val gui = e.inventory.holder as? Gui ?: return
        e.isCancelled = true
        if (e.whoClicked !is Player) return
        val slot = e.rawSlot
        if (slot < 0 || slot >= e.inventory.size) return
        gui.onClick(slot, e.click)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onDrag(e: InventoryDragEvent) {
        if (e.inventory.holder is Gui) e.isCancelled = true
    }
}
