package org.Mona.monaDuels.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

/**
 * Gate + persistence-backed application of cosmetic armor trims per player/kit.
 * Only players passing the {@code monaprime} gate can select or receive trims.
 */
public final class TrimService {
   private static final Map<String, Material> MATERIAL_ICONS = Map.ofEntries(
      Map.entry("quartz", Material.QUARTZ),
      Map.entry("iron", Material.IRON_INGOT),
      Map.entry("netherite", Material.NETHERITE_INGOT),
      Map.entry("redstone", Material.REDSTONE),
      Map.entry("copper", Material.COPPER_INGOT),
      Map.entry("gold", Material.GOLD_INGOT),
      Map.entry("emerald", Material.EMERALD),
      Map.entry("diamond", Material.DIAMOND),
      Map.entry("lapis", Material.LAPIS_LAZULI),
      Map.entry("amethyst", Material.AMETHYST_SHARD),
      Map.entry("resin", Material.RESIN_BRICK)
   );
   private final ConfigManager config;
   private final PlayerDataManager playerData;

   public TrimService(ConfigManager config, PlayerDataManager playerData) {
      this.config = config;
      this.playerData = playerData;
   }

   public boolean isEnabled() {
      return this.config.trimsEnabled();
   }

   /** True if the player may open the trims menu and receive trims (permission OR group node). */
   public boolean canUse(Player player) {
      if (!this.config.trimsEnabled() || player == null) {
         return false;
      }

      String perm = this.config.trimsPermission();
      String group = this.config.trimsGroup();
      boolean hasPerm = perm != null && !perm.isBlank();
      boolean hasGroup = group != null && !group.isBlank();
      if (!hasPerm && !hasGroup) {
         return true;
      }

      if (hasPerm && player.hasPermission(perm)) {
         return true;
      }

      return hasGroup && player.hasPermission("group." + group.toLowerCase(Locale.ROOT));
   }

   public ArmorTrim resolveTrim(UUID playerId, String kitName) {
      if (!this.playerData.hasKitTrim(playerId, kitName)) {
         return null;
      }

      TrimPattern pattern = this.resolvePattern(this.playerData.getKitTrimPattern(playerId, kitName));
      TrimMaterial material = this.resolveMaterial(this.playerData.getKitTrimMaterial(playerId, kitName));
      return pattern != null && material != null ? new ArmorTrim(material, pattern) : null;
   }

   /** Applies the player's stored trim (if any and allowed) to their live armor. */
   public void applyToLiveArmor(Player player, Kit kit) {
      if (kit == null || !this.canUse(player)) {
         return;
      }

      ArmorTrim trim = this.resolveTrim(player.getUniqueId(), kit.name());
      if (trim != null) {
         PlayerInventory inv = player.getInventory();
         ItemStack[] armor = inv.getArmorContents();
         if (applyTrim(armor, trim)) {
            inv.setArmorContents(armor);
         }
      }
   }

   /** Applies a trim to an armor array in place; returns true if anything changed. */
   public static boolean applyTrim(ItemStack[] armor, ArmorTrim trim) {
      if (trim == null || armor == null) {
         return false;
      }

      boolean changed = false;

      for (ItemStack piece : armor) {
         if (piece != null && piece.getItemMeta() instanceof ArmorMeta meta) {
            meta.setTrim(trim);
            piece.setItemMeta(meta);
            changed = true;
         }
      }

      return changed;
   }

   public TrimPattern resolvePattern(String key) {
      NamespacedKey nk = parseKey(key);
      return nk == null ? null : Registry.TRIM_PATTERN.get(nk);
   }

   public TrimMaterial resolveMaterial(String key) {
      NamespacedKey nk = parseKey(key);
      return nk == null ? null : Registry.TRIM_MATERIAL.get(nk);
   }

   public List<TrimPattern> availablePatterns() {
      List<String> allow = this.config.trimsAllowedPatterns();
      List<TrimPattern> out = new ArrayList<>();

      for (TrimPattern pattern : Registry.TRIM_PATTERN) {
         if (allow.isEmpty() || allow.stream().anyMatch(a -> matchesKey(a, pattern.getKey()))) {
            out.add(pattern);
         }
      }

      return out;
   }

   public List<TrimMaterial> availableMaterials() {
      List<String> allow = this.config.trimsAllowedMaterials();
      List<TrimMaterial> out = new ArrayList<>();

      for (TrimMaterial material : Registry.TRIM_MATERIAL) {
         if (allow.isEmpty() || allow.stream().anyMatch(a -> matchesKey(a, material.getKey()))) {
            out.add(material);
         }
      }

      return out;
   }

   public Material patternIcon(TrimPattern pattern) {
      Material icon = Material.matchMaterial(pattern.getKey().getKey().toUpperCase(Locale.ROOT) + "_ARMOR_TRIM_SMITHING_TEMPLATE");
      return icon != null ? icon : Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
   }

   public Material materialIcon(TrimMaterial material) {
      return MATERIAL_ICONS.getOrDefault(material.getKey().getKey(), Material.IRON_INGOT);
   }

   public static String prettyName(NamespacedKey key) {
      String raw = key.getKey().replace('_', ' ');
      String[] words = raw.split(" ");
      StringBuilder sb = new StringBuilder();

      for (String word : words) {
         if (!word.isBlank()) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
         }
      }

      return sb.toString().trim();
   }

   private static boolean matchesKey(String allowEntry, NamespacedKey key) {
      if (allowEntry == null) {
         return false;
      }

      String a = allowEntry.trim().toLowerCase(Locale.ROOT);
      return a.equals(key.getKey()) || a.equals(key.toString()) || a.equals(key.getNamespace() + ":" + key.getKey());
   }

   private static NamespacedKey parseKey(String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }

      String key = raw.trim().toLowerCase(Locale.ROOT);
      NamespacedKey nk = NamespacedKey.fromString(key);
      return nk != null ? nk : NamespacedKey.minecraft(key);
   }
}
