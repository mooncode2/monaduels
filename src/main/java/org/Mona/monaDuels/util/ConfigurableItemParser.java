package org.Mona.monaDuels.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionType;

public final class ConfigurableItemParser {
   private static final Map<String, String> MATERIAL_ALIASES = Map.of(
      "BOOK_AND_QUILL",
      "WRITABLE_BOOK",
      "SKULL_ITEM",
      "PLAYER_HEAD",
      "SKULL",
      "PLAYER_HEAD",
      "WARDEN_COMPASS",
      "RECOVERY_COMPASS",
      "COMPASS",
      "RECOVERY_COMPASS"
   );
   private static final Map<String, String> POTION_ALIASES = Map.of("INSTANT_DAMAGE", "strong_harming", "HARMING", "harming", "DAMAGE", "harming");

   private ConfigurableItemParser() {
   }

   public static Material parseMaterial(String raw) {
      if (raw != null && !raw.isBlank()) {
         String normalized = normalizeKey(raw);
         Material registry = (Material)Registry.MATERIAL.get(NamespacedKey.minecraft(normalized));
         if (registry != null) {
            return registry;
         } else {
            String upper = normalized.toUpperCase(Locale.ROOT);
            upper = MATERIAL_ALIASES.getOrDefault(upper, upper);
            Material matched = Material.getMaterial(upper);
            if (matched != null) {
               return matched;
            } else {
               matched = Material.matchMaterial(upper);
               return matched != null ? matched : Material.BARRIER;
            }
         }
      } else {
         return Material.BARRIER;
      }
   }

   private static String normalizeKey(String raw) {
      String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
      if (!normalized.contains(":")) {
         return normalized;
      } else {
         int colonIndex = normalized.indexOf(58);
         if (colonIndex + 1 >= normalized.length()) {
            return "barrier";
         } else {
            String key = normalized.substring(colonIndex + 1);
            return key.isBlank() ? "barrier" : key;
         }
      }
   }

   public static ItemStack fromSection(ConfigurationSection section, Player player, Map<String, String> placeholders) {
      if (section == null) {
         return new ItemStack(Material.BARRIER);
      } else {
         Material material = parseMaterial(section.getString("material", "BARRIER"));
         int amount = Math.max(1, section.getInt("amount", 1));
         ItemStack stack = new ItemStack(material, amount);
         ItemMeta meta = stack.getItemMeta();
         if (meta == null) {
            return stack;
         } else {
            String name = section.getString("display-name", section.getString("displayname", section.getString("name", "")));
            if (!name.isBlank()) {
               meta.displayName(ColorUtil.component(applyPlaceholders(name, player, placeholders)));
            }

            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
               List<Component> loreComponents = new ArrayList<>();

               for (String line : lore) {
                  loreComponents.add(ColorUtil.component(applyPlaceholders(line, player, placeholders)));
               }

               meta.lore(loreComponents);
            }

            applyEnchantments(meta, section);
            boolean enchantedFlag = section.getBoolean("enchanted", false);
            if (section.getBoolean("enchantment-glint", false) || section.getBoolean("glow", false) || enchantedFlag) {
               meta.setEnchantmentGlintOverride(true);
               if (enchantedFlag) {
                  Enchantment dummy = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
                  if (dummy != null) {
                     meta.addEnchant(dummy, 1, true);
                  }
               }
            }

            if (meta instanceof SkullMeta skullMeta) {
               if (!section.getBoolean("player-head", false) && !section.getBoolean("own-head", false)) {
                  String owner = section.getString("skull-owner", section.getString("head-owner"));
                  if (owner != null && !owner.isBlank()) {
                     skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(applyPlaceholders(owner, player, placeholders)));
                  }
               } else if (player != null) {
                  skullMeta.setOwningPlayer(player);
               }
            }

            if (meta instanceof PotionMeta potionMeta) {
               String potionKey = section.getString("potion-type", section.getString("potion"));
               PotionType type = parsePotionType(potionKey);
               if (type != null) {
                  potionMeta.setBasePotionType(type);
               }
            }

            if (section.getBoolean("hide-flags", true)) {
               meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
            }

            stack.setItemMeta(meta);
            return stack;
         }
      }
   }

   public static Map<Enchantment, Integer> readEnchantments(ConfigurationSection section) {
      Map<Enchantment, Integer> result = new LinkedHashMap<>();
      if (section == null) {
         return result;
      } else {
         ConfigurationSection enchSec = section.getConfigurationSection("enchantments");
         if (enchSec != null) {
            for (String key : enchSec.getKeys(false)) {
               Enchantment enchantment = parseEnchantment(key);
               if (enchantment != null) {
                  result.put(enchantment, Math.max(1, enchSec.getInt(key, 1)));
               }
            }

            return result;
         } else {
            for (String entry : section.getStringList("enchantments")) {
               if (entry != null && !entry.isBlank()) {
                  String[] parts = entry.split(":");
                  Enchantment enchantment = parseEnchantment(parts[0].trim());
                  if (enchantment != null) {
                     int level = parts.length > 1 ? parseInt(parts[1], 1) : 1;
                     result.put(enchantment, Math.max(1, level));
                  }
               }
            }

            return result;
         }
      }
   }

   private static void applyEnchantments(ItemMeta meta, ConfigurationSection section) {
      for (Entry<Enchantment, Integer> entry : readEnchantments(section).entrySet()) {
         meta.addEnchant(entry.getKey(), entry.getValue(), true);
      }
   }

   public static Enchantment parseEnchantment(String raw) {
      if (raw != null && !raw.isBlank()) {
         String key = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
         if (key.contains(":")) {
            key = key.substring(key.indexOf(58) + 1);
         }

         Enchantment enchantment = (Enchantment)Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
         return enchantment != null ? enchantment : Enchantment.getByKey(NamespacedKey.minecraft(key));
      } else {
         return null;
      }
   }

   public static PotionType parsePotionType(String raw) {
      if (raw != null && !raw.isBlank()) {
         String key = raw.toLowerCase(Locale.ROOT).replace(' ', '_');
         if (key.contains(":")) {
            key = key.substring(key.indexOf(58) + 1);
         }

         key = POTION_ALIASES.getOrDefault(key.toUpperCase(Locale.ROOT), key);
         PotionType registry = (PotionType)Registry.POTION.get(NamespacedKey.minecraft(key));
         if (registry != null) {
            return registry;
         } else {
            registry = (PotionType)Registry.POTION.get(NamespacedKey.minecraft("strong_" + key));
            return registry != null ? registry : (PotionType)Registry.POTION.get(NamespacedKey.minecraft(key.replace("strong_", "")));
         }
      } else {
         return null;
      }
   }

   private static int parseInt(String raw, int fallback) {
      try {
         return Integer.parseInt(raw.trim());
      } catch (NumberFormatException var3) {
         return fallback;
      }
   }

   private static String applyPlaceholders(String raw, Player player, Map<String, String> placeholders) {
      String result = raw;
      if (player != null) {
         result = raw.replace("%player%", player.getName());
      }

      if (placeholders != null) {
         for (Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
         }
      }

      return result;
   }
}
