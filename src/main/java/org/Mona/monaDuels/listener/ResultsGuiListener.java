package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class ResultsGuiListener implements Listener {
   private final ConfigManager config;

   public ResultsGuiListener(ConfigManager config) {
      this.config = config;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         String var5 = ColorUtil.color(this.config.resultsGuiTitle());
         if (event.getView().getTitle() != null && event.getView().getTitle().equals(var5)) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.getType() == Material.BARRIER) {
               player.closeInventory();
            }
         }
      }
   }
}
