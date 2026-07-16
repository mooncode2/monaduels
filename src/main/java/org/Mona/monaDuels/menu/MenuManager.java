package org.Mona.monaDuels.menu;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.util.ColorUtil;
import org.Mona.monaDuels.util.ConfigurableItemParser;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuManager implements Listener {
   private final JavaPlugin plugin;
   private final Map<String, MenuManager.ConfigMenu> menus = new HashMap<>();
   private final Map<String, String> openCommandToMenu = new HashMap<>();
   private final Map<String, Map<Integer, BiConsumer<Player, String>>> slotHandlers = new HashMap<>();
   private final Map<String, Map<String, BiConsumer<Player, String>>> keyHandlers = new HashMap<>();
   private final Map<String, Map<String, BiConsumer<Player, String>>> rightKeyHandlers = new HashMap<>();
   private final Map<String, Map<String, String>> slotToItemKey = new HashMap<>();
   private BiConsumer<Player, String> kitActionHandler;

   public MenuManager(JavaPlugin plugin) {
      this.plugin = plugin;
   }

   public void initialize() {
      File menusDir = new File(this.plugin.getDataFolder(), "menus");
      if (!menusDir.exists()) {
         this.plugin.saveResource("menus/kits-menu.yml", false);
      }

      this.loadMenus();
      Bukkit.getPluginManager().registerEvents(this, this.plugin);
   }

   public void reloadMenus() {
      this.menus.clear();
      this.openCommandToMenu.clear();
      this.slotHandlers.clear();
      this.keyHandlers.clear();
      this.rightKeyHandlers.clear();
      this.slotToItemKey.clear();
      this.loadMenus();
   }

   public void registerSlotHandler(String menuId, int slot, BiConsumer<Player, String> handler) {
      this.slotHandlers.computeIfAbsent(menuId.toLowerCase(Locale.ROOT), k -> new HashMap<>()).put(slot, handler);
   }

   public void registerKeyHandler(String menuId, String itemKey, BiConsumer<Player, String> handler) {
      this.keyHandlers.computeIfAbsent(menuId.toLowerCase(Locale.ROOT), k -> new HashMap<>()).put(itemKey.toLowerCase(Locale.ROOT), handler);
   }

   public void registerRightKeyHandler(String menuId, String itemKey, BiConsumer<Player, String> handler) {
      this.rightKeyHandlers.computeIfAbsent(menuId.toLowerCase(Locale.ROOT), k -> new HashMap<>()).put(itemKey.toLowerCase(Locale.ROOT), handler);
   }

   public void setKitActionHandler(BiConsumer<Player, String> kitActionHandler) {
      this.kitActionHandler = kitActionHandler;
   }

   public void syncKitDisplayItems(String menuId, Collection<Kit> kits) {
      MenuManager.ConfigMenu menu = this.menus.get(menuId.toLowerCase(Locale.ROOT));
      if (menu != null) {
         for (Kit kit : kits) {
            String kitKey = kit.name();
            MenuManager.ConfigMenuItem existing = menu.itemsByKey.get(kitKey);
            int slot = kit.iconSlot() >= 0 ? kit.iconSlot() : (existing != null ? existing.slot : -1);
            if (slot >= 0) {
               String material = existing != null ? existing.itemSection.getString("material", kit.iconMaterial().name()) : kit.iconMaterial().name();
               String name = kit.displayName();
               List<String> lore = existing != null ? existing.itemSection.getStringList("lore") : List.of("&7Нажмите для выбора");
               List<String> actions = List.of("[kit] " + kitKey);
               YamlConfiguration itemCfg = new YamlConfiguration();
               itemCfg.set("material", material);
               itemCfg.set("display-name", name);
               itemCfg.set("lore", lore);
               MenuManager.ConfigMenuItem item = new MenuManager.ConfigMenuItem(kitKey, slot, itemCfg, actions);
               menu.itemsByKey.put(kitKey, item);
               menu.itemsBySlot.put(slot, item);
            }
         }
      }
   }

   public void clearHandlers(String menuId) {
      String id = menuId.toLowerCase(Locale.ROOT);
      this.slotHandlers.remove(id);
      this.keyHandlers.remove(id);
      this.rightKeyHandlers.remove(id);
   }

   public boolean openMenu(Player player, String menuId) {
      return this.openMenu(player, menuId, Map.of());
   }

   public boolean openMenu(Player player, String menuId, Map<String, String> extraPlaceholders) {
      MenuManager.ConfigMenu menu = this.menus.get(menuId.toLowerCase(Locale.ROOT));
      if (menu == null) {
         player.sendMessage(ColorUtil.color("&cMenu '" + menuId + "' not found."));
         return false;
      } else {
         Inventory inv = Bukkit.createInventory(
            new MenuManager.ConfigMenuHolder(menu.id), menu.size, ColorUtil.component(this.replacePlaceholders(menu.title, player, extraPlaceholders))
         );
         Map<String, String> slotKeys = new HashMap<>();

         for (Entry<String, MenuManager.ConfigMenuItem> entry : menu.itemsByKey.entrySet()) {
            MenuManager.ConfigMenuItem item = entry.getValue();
            ItemStack stack = this.buildItem(item, player, extraPlaceholders);
            if (item.slot >= 0 && item.slot < inv.getSize()) {
               inv.setItem(item.slot, stack);
               slotKeys.put(String.valueOf(item.slot), entry.getKey());
            }
         }

         this.slotToItemKey.put(menu.id, slotKeys);
         player.openInventory(inv);
         return true;
      }
   }

   public boolean hasMenu(String menuId) {
      return this.menus.containsKey(menuId.toLowerCase(Locale.ROOT));
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onMenuClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (event.getInventory().getHolder() instanceof MenuManager.ConfigMenuHolder holder) {
            event.setCancelled(true);
            MenuManager.ConfigMenu menu = this.menus.get(holder.menuId);
            if (menu != null) {
               int slot = event.getRawSlot();
               if (slot >= 0 && slot < event.getInventory().getSize()) {
                  String itemKey = this.slotToItemKey.getOrDefault(menu.id, Map.of()).get(String.valueOf(slot));
                  MenuManager.ConfigMenuItem item = menu.itemsBySlot.get(slot);
                  if (event.getClick().isRightClick() && itemKey != null) {
                     Map<String, BiConsumer<Player, String>> rightHandlerMap = this.rightKeyHandlers.get(menu.id);
                     if (rightHandlerMap != null) {
                        BiConsumer<Player, String> rightHandler = rightHandlerMap.get(itemKey.toLowerCase(Locale.ROOT));
                        if (rightHandler != null) {
                           rightHandler.accept(player, itemKey);
                           return;
                        }
                     }
                  }

                  Map<Integer, BiConsumer<Player, String>> slotHandlerMap = this.slotHandlers.get(menu.id);
                  if (slotHandlerMap != null) {
                     BiConsumer<Player, String> slotHandler = slotHandlerMap.get(slot);
                     if (slotHandler != null) {
                        slotHandler.accept(player, itemKey != null ? itemKey : "");
                        return;
                     }
                  }

                  if (itemKey != null) {
                     Map<String, BiConsumer<Player, String>> keyHandlerMap = this.keyHandlers.get(menu.id);
                     if (keyHandlerMap != null) {
                        BiConsumer<Player, String> keyHandler = keyHandlerMap.get(itemKey.toLowerCase(Locale.ROOT));
                        if (keyHandler != null) {
                           keyHandler.accept(player, itemKey);
                           return;
                        }
                     }
                  }

                  if (item != null) {
                     for (String action : item.actions) {
                        this.runAction(player, action);
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (event.getInventory().getHolder() instanceof MenuManager.ConfigMenuHolder var2) {
         ;
      }
   }

   private void runAction(Player player, String rawAction) {
      String action = this.replacePlaceholders(rawAction, player, Map.of());
      String lower = action.toLowerCase(Locale.ROOT);
      if (lower.startsWith("[player] ")) {
         player.performCommand(action.substring(9).trim());
      } else if (lower.startsWith("[console] ")) {
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.substring(10).trim());
      } else if (lower.startsWith("[menu] ")) {
         this.openMenu(player, action.substring(7).trim());
      } else if (lower.equals("[close]")) {
         player.closeInventory();
      } else if (lower.startsWith("[message] ")) {
         player.sendMessage(ColorUtil.color(action.substring(10)));
      } else {
         if (lower.startsWith("[kit] ") && this.kitActionHandler != null) {
            this.kitActionHandler.accept(player, action.substring(6).trim());
         }
      }
   }

   private void loadMenus() {
      File menusDir = new File(this.plugin.getDataFolder(), "menus");
      if (!menusDir.exists() && !menusDir.mkdirs()) {
         this.plugin.getLogger().warning("Could not create menus directory.");
      } else {
         File[] files = menusDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
         if (files != null) {
            for (File file : files) {
               YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
               String id = cfg.getString("id", file.getName().replace(".yml", "")).toLowerCase(Locale.ROOT);
               String title = cfg.getString("title", "&8Menu");
               int size = this.normalizeSize(cfg.getInt("size", 27));
               String openCommand = this.normalizeOpenCommand(cfg.getString("open-command", ""));
               ConfigurationSection itemsSection = cfg.getConfigurationSection("items");
               if (itemsSection != null) {
                  MenuManager.ConfigMenu menu = new MenuManager.ConfigMenu(id, title, size);

                  for (String key : itemsSection.getKeys(false)) {
                     if (itemsSection.get(key) instanceof List<?> slots) {
                        this.loadSlotListItem(menu, key, slots);
                     } else {
                        ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);
                        if (itemSec != null) {
                           if (itemSec.get("slots") instanceof List<?> slotList && itemSec.get("material") instanceof String material && !material.isBlank()) {
                              this.loadMultiSlotItem(menu, material, slotList);
                              continue;
                           }

                           Object slotObj = itemSec.get("slot");
                           List<Integer> slotList = new ArrayList<>();
                           if (slotObj instanceof Number n) {
                              slotList.add(n.intValue());
                           } else if (slotObj instanceof List) {
                              for (Object o : (List)slotObj) {
                                 if (o instanceof Number num) {
                                    slotList.add(num.intValue());
                                 } else {
                                    try {
                                       slotList.add(Integer.parseInt(String.valueOf(o).trim()));
                                    } catch (NumberFormatException var30) {
                                    }
                                 }
                              }
                           } else if (slotObj instanceof String s) {
                              for (String part : s.split(",")) {
                                 try {
                                    slotList.add(Integer.parseInt(part.trim()));
                                 } catch (NumberFormatException var29) {
                                 }
                              }
                           }

                           if (slotList.isEmpty()) {
                              slotList.add(-1);
                           }

                           List<String> actions = itemSec.getStringList("actions");
                           String singleAction = itemSec.getString("action");
                           if (actions.isEmpty() && singleAction != null && !singleAction.isBlank()) {
                              actions = List.of(singleAction);
                           }

                           for (int slot : slotList) {
                              String itemKey = slotList.size() == 1 ? key : key + "_" + slot;
                              MenuManager.ConfigMenuItem item = new MenuManager.ConfigMenuItem(itemKey, slot, itemSec, actions);
                              menu.itemsByKey.put(itemKey, item);
                              if (slot >= 0) {
                                 menu.itemsBySlot.put(slot, item);
                              }
                           }
                        }
                     }
                  }

                  this.menus.put(menu.id, menu);
                  if (!openCommand.isBlank()) {
                     this.openCommandToMenu.put(openCommand, menu.id);
                  }
               }
            }
         }
      }
   }

   private void loadSlotListItem(MenuManager.ConfigMenu menu, String materialName, List<?> slots) {
      for (Object rawSlot : slots) {
         int slot = this.parseSlot(rawSlot);
         if (slot >= 0) {
            YamlConfiguration itemSec = new YamlConfiguration();
            itemSec.set("material", materialName);
            String itemKey = materialName.toLowerCase(Locale.ROOT) + "_" + slot;
            MenuManager.ConfigMenuItem item = new MenuManager.ConfigMenuItem(itemKey, slot, itemSec, List.of());
            menu.itemsByKey.put(itemKey, item);
            menu.itemsBySlot.put(slot, item);
         }
      }
   }

   private void loadMultiSlotItem(MenuManager.ConfigMenu menu, String materialName, List<?> slots) {
      for (Object rawSlot : slots) {
         int slot = this.parseSlot(rawSlot);
         if (slot >= 0) {
            YamlConfiguration itemSec = new YamlConfiguration();
            itemSec.set("material", materialName);
            itemSec.set("slots", List.of(slots));
            String itemKey = materialName.toLowerCase(Locale.ROOT) + "_multi_" + slot;
            MenuManager.ConfigMenuItem item = new MenuManager.ConfigMenuItem(itemKey, slot, itemSec, List.of());
            menu.itemsByKey.put(itemKey, item);
            menu.itemsBySlot.put(slot, item);
         }
      }
   }

   private int parseSlot(Object rawSlot) {
      if (rawSlot instanceof Number number) {
         return number.intValue();
      } else if (rawSlot == null) {
         return -1;
      } else {
         try {
            return Integer.parseInt(String.valueOf(rawSlot).trim());
         } catch (NumberFormatException var3) {
            return -1;
         }
      }
   }

   @EventHandler
   public void onOpenCommand(PlayerCommandPreprocessEvent event) {
      String raw = event.getMessage();
      if (raw != null && !raw.isBlank() && raw.startsWith("/")) {
         String commandLabel = raw.substring(1).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
         String menuId = this.openCommandToMenu.get(commandLabel);
         if (menuId != null) {
            event.setCancelled(true);
            this.openMenu(event.getPlayer(), menuId);
         }
      }
   }

   private ItemStack buildItem(MenuManager.ConfigMenuItem item, Player player, Map<String, String> placeholders) {
      Map<String, String> ph = new HashMap<>(placeholders);
      ph.put("player", player.getName());
      return ConfigurableItemParser.fromSection(item.itemSection, player, ph);
   }

   private int normalizeSize(int requested) {
      int size = Math.max(9, Math.min(54, requested));
      int remainder = size % 9;
      return remainder == 0 ? size : size + (9 - remainder);
   }

   private String normalizeOpenCommand(String openCommand) {
      String value = openCommand == null ? "" : openCommand.trim().toLowerCase(Locale.ROOT);
      if (value.startsWith("/")) {
         value = value.substring(1);
      }

      return value;
   }

   private String replacePlaceholders(String input, Player player, Map<String, String> extra) {
      String result = input.replace("%player%", player.getName());

      for (Entry<String, String> entry : extra.entrySet()) {
         result = result.replace("%" + entry.getKey() + "%", entry.getValue());
      }

      return result;
   }

   private static record ConfigMenu(
      String id, String title, int size, Map<String, MenuManager.ConfigMenuItem> itemsByKey, Map<Integer, MenuManager.ConfigMenuItem> itemsBySlot
   ) {
      private ConfigMenu(String id, String title, int size) {
         this(id, title, size, new HashMap<>(), new HashMap<>());
      }
   }

   public static record ConfigMenuHolder(String menuId) implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }

   private static record ConfigMenuItem(String key, int slot, ConfigurationSection itemSection, List<String> actions) {
   }
}
