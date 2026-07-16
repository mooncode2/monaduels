package org.Mona.monaDuels.queue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.Mona.monaDuels.util.ConfigurableItemParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class PlayAgainService {
   private final ConfigManager config;
   private final KitManager kitManager;
   private final NamespacedKey kitKey;
   /** Players who clicked «Играть снова» during the post-fight stage — queued after teleport. */
   private final Set<UUID> pendingRequeue = ConcurrentHashMap.newKeySet();

   public PlayAgainService(MonaDuels plugin, ConfigManager config, KitManager kitManager) {
      this.config = config;
      this.kitManager = kitManager;
      this.kitKey = new NamespacedKey(plugin, "play_again_kit");
   }

   public void givePlayAgainItem(Player player, String kitName) {
      // Rev. 2: given right at match end, while the players are still on the arena — no world or
      // in-duel guard. It lands in the held slot for an instant click.
      if (!this.config.playAgainEnabled() || kitName == null || kitName.isBlank() || !player.isOnline()) {
         return;
      }

      Kit kit = this.kitManager.find(kitName).orElse(null);
      String kitDisplay = kit != null ? kit.displayName() : kitName;
      Material itemMaterial;
      if (player.getWorld().getName().equalsIgnoreCase("lobby") || 
          player.getWorld().getName().equalsIgnoreCase(this.config.lobbyWorldName()) ||
          player.getWorld().getName().equalsIgnoreCase(this.config.lobbyHotbarWorld())) {
         itemMaterial = Material.LIGHT_GRAY_SHULKER_BOX;
      } else {
         itemMaterial = ConfigurableItemParser.parseMaterial(this.config.playAgainMaterial());
      }
      ItemStack item = new ItemStack(itemMaterial);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color(this.config.playAgainName()));
         List<String> lore = this.config
            .playAgainLore()
            .stream()
            .map(line -> ColorUtil.color(line.replace("{kit}", ColorUtil.strip(kitDisplay)).replace("{kit_id}", kitName)))
            .toList();
         meta.setLore(lore);
         meta.getPersistentDataContainer().set(this.kitKey, PersistentDataType.STRING, kitName.toLowerCase());
         item.setItemMeta(meta);
      }
      
      // Защита предмета от перемещения/броска (только если это шалкер в лобби)
      if (itemMaterial == Material.LIGHT_GRAY_SHULKER_BOX) {
         item = this.protectItem(item);
      }

      // Task 5: place in the player's currently held (active) hotbar slot so it can be clicked
      // instantly without moving the cursor.
      player.getInventory().setItem(player.getInventory().getHeldItemSlot(), item);
   }

   public boolean isPlayAgain(ItemStack item) {
      if (item == null) {
         return false;
      }

      ItemMeta meta = item.getItemMeta();
      return meta != null && meta.getPersistentDataContainer().has(this.kitKey, PersistentDataType.STRING);
   }

   public String kitOf(ItemStack item) {
      if (item == null) {
         return null;
      }

      ItemMeta meta = item.getItemMeta();
      return meta == null ? null : meta.getPersistentDataContainer().get(this.kitKey, PersistentDataType.STRING);
   }

   private ItemStack protectItem(ItemStack item) {
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setUnbreakable(true);
         meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
         meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
      }
      item.setItemMeta(meta);
      return item;
   }

   public void markPending(UUID playerId) {
      this.pendingRequeue.add(playerId);
   }

   public boolean consumePending(UUID playerId) {
      return this.pendingRequeue.remove(playerId);
   }

   public void clearFor(UUID playerId) {
      this.pendingRequeue.remove(playerId);
   }
}
