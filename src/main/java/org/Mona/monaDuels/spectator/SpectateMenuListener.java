package org.Mona.monaDuels.spectator;

import java.util.List;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SpectateMenuListener implements Listener {
   private final ConfigManager config;
   private final SpectatorManager spectatorManager;

   public SpectateMenuListener(ConfigManager config, SpectatorManager spectatorManager) {
      this.config = config;
      this.spectatorManager = spectatorManager;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         String var11 = ColorUtil.color(this.config.spectateMenuTitle());
         if (event.getView().getTitle() != null && event.getView().getTitle().equals(var11)) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.hasItemMeta()) {
               ItemMeta meta = item.getItemMeta();
               if (meta != null && meta.hasLore()) {
                  List<String> lore = meta.getLore();
                  if (lore != null) {
                     for (String line : lore) {
                        String plain = ColorUtil.strip(line);
                        if (plain.startsWith("id:")) {
                           String shortId = plain.substring(3).trim();
                           player.closeInventory();
                           this.spectatorManager.joinSpectateByShortId(player, shortId);
                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }
}
