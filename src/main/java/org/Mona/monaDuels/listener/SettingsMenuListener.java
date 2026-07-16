package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.gui.SettingsMenuService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class SettingsMenuListener implements Listener {
   private final SettingsMenuService settingsMenu;

   public SettingsMenuListener(SettingsMenuService settingsMenu) {
      this.settingsMenu = settingsMenu;
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onClick(InventoryClickEvent event) {
      if (event.getInventory().getHolder() instanceof SettingsMenuService.SettingsHolder) {
         event.setCancelled(true);
         if (event.getWhoClicked() instanceof Player player) {
            this.settingsMenu.handleClick(player, event.getRawSlot(), event.getView().getTopInventory());
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDrag(InventoryDragEvent event) {
      if (event.getInventory().getHolder() instanceof SettingsMenuService.SettingsHolder) {
         event.setCancelled(true);
      }
   }
}
