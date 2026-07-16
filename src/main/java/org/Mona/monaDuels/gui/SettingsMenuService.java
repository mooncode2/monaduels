package org.Mona.monaDuels.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Duel settings menu (Rev. 2): per-player toggles for the in-duel boss bar and scoreboard.
 * Opened by the «Настройки» item of the duel hotbar.
 */
public final class SettingsMenuService {
   public static final int BOSSBAR_SLOT = 11;
   public static final int SCOREBOARD_SLOT = 15;
   public static final int CLOSE_SLOT = 22;
   private final ConfigManager config;
   private final PlayerDataManager playerData;

   public SettingsMenuService(ConfigManager config, PlayerDataManager playerData) {
      this.config = config;
      this.playerData = playerData;
   }

   public void open(Player player) {
      Inventory inv = Bukkit.createInventory(new SettingsMenuService.SettingsHolder(), 27, ColorUtil.color(this.config.settingsMenuTitle()));
      this.render(player, inv);
      player.openInventory(inv);
   }

   public void handleClick(Player player, int rawSlot, Inventory top) {
      UUID id = player.getUniqueId();
      if (rawSlot == BOSSBAR_SLOT) {
         this.playerData.setBossBarEnabled(id, !this.playerData.isBossBarEnabled(id));
         this.render(player, top);
      } else if (rawSlot == SCOREBOARD_SLOT) {
         this.playerData.setScoreboardEnabled(id, !this.playerData.isScoreboardEnabled(id));
         this.render(player, top);
      } else if (rawSlot == CLOSE_SLOT) {
         player.closeInventory();
      }
   }

   private void render(Player player, Inventory inv) {
      ItemStack filler = filler();

      for (int i = 0; i < inv.getSize(); i++) {
         inv.setItem(i, filler);
      }

      UUID id = player.getUniqueId();
      inv.setItem(
         BOSSBAR_SLOT,
         this.toggleItem("settings.bossbar", "&fБоссбар во время дуэли",
            List.of("&7Показ полосы босса с информацией", "&7о противнике и ките."), this.playerData.isBossBarEnabled(id))
      );
      inv.setItem(
         SCOREBOARD_SLOT,
         this.toggleItem("settings.scoreboard", "&fСкорборд во время дуэли",
            List.of("&7Боковая панель с HP и пингом."), this.playerData.isScoreboardEnabled(id))
      );
      inv.setItem(CLOSE_SLOT, this.button(Material.BARRIER, "settings.close", "&c&lЗакрыть", List.of("&7Закрыть меню")));
      player.updateInventory();
   }

   private ItemStack toggleItem(String path, String defName, List<String> defLore, boolean enabled) {
      ItemStack stack = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         String state = enabled ? " &8· &aВКЛ" : " &8· &cВЫКЛ";
         meta.setDisplayName(ColorUtil.color(this.config.uiName(path, defName) + state));
         List<String> lore = new ArrayList<>(this.config.uiLore(path, defLore).stream().map(ColorUtil::color).toList());
         lore.add("");
         lore.add(ColorUtil.color(enabled ? "&eНажмите, чтобы выключить" : "&eНажмите, чтобы включить"));
         meta.setLore(lore);
         stack.setItemMeta(meta);
      }

      return stack;
   }

   private ItemStack button(Material material, String path, String defName, List<String> defLore) {
      ItemStack stack = new ItemStack(material);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color(this.config.uiName(path, defName)));
         meta.setLore(this.config.uiLore(path, defLore).stream().map(ColorUtil::color).toList());
         stack.setItemMeta(meta);
      }

      return stack;
   }

   private static ItemStack filler() {
      ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
      ItemMeta meta = pane.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(" ");
         pane.setItemMeta(meta);
      }

      return pane;
   }

   public static record SettingsHolder() implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }
}
