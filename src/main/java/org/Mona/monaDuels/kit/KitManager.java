package org.Mona.monaDuels.kit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.service.TrimService;
import org.Mona.monaDuels.util.ConfigurableItemParser;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class KitManager {
   private final MonaDuels plugin;
   private final Map<String, Kit> kits = new LinkedHashMap<>();
   private TrimService trimService;

   public KitManager(MonaDuels plugin) {
      this.plugin = plugin;
   }

   public void bindTrims(TrimService trimService) {
      this.trimService = trimService;
   }

   public void load() {
      this.kits.clear();
      File dir = this.kitsDirectory();
      if (dir.exists() || dir.mkdirs()) {
         File[] files = dir.listFiles((d, namex) -> namex.endsWith(".yml"));
         if (files != null) {
            for (File file : files) {
               YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
               String name = cfg.getString("name", file.getName().replace(".yml", "")).toLowerCase(Locale.ROOT);
               Kit kit = new Kit(name);
               kit.setInventory(readItemArray(cfg, "inventory", 36));
               kit.setArmor(readArmor(cfg));
               kit.setOffhand(cfg.getItemStack("offhand"));
               kit.setEffects(readEffects(cfg));
               kit.setStartBuffs(readEffects(cfg, "start-buffs"));
               kit.setDisplayName(cfg.getString("display-name", name));
               if (cfg.contains("icon.material")) {
                  String materialName = cfg.getString("icon.material", "BARRIER");
                  kit.setIconMaterial(ConfigurableItemParser.parseMaterial(materialName));
                  kit.setIconSlot(cfg.getInt("icon.slot", -1));
               }

               this.kits.put(name, kit);
            }
         }
      }
   }

   public boolean createFromPlayer(String name, Player player) {
      String key = name.toLowerCase(Locale.ROOT);
      Kit kit = new Kit(key);
      PlayerInventory inv = player.getInventory();
      kit.setInventory(inv.getStorageContents());
      kit.setArmor(fromEquippedArmor(inv));
      kit.setOffhand(inv.getItemInOffHand());
      kit.setEffects(new ArrayList<>(player.getActivePotionEffects()));
      kit.setDisplayName("&f" + key);
      kit.setIconMaterial(guessIcon(inv));
      this.kits.put(key, kit);
      return this.save(kit);
   }

   public boolean remove(String name) {
      String key = name.toLowerCase(Locale.ROOT);
      Kit removed = this.kits.remove(key);
      if (removed == null) {
         return false;
      } else {
         File file = new File(this.kitsDirectory(), key + ".yml");
         if (file.exists() && !file.delete()) {
            this.plugin.getLogger().warning("Could not delete kit file: " + file.getName());
         }

         return true;
      }
   }

   public void apply(Player player, Kit kit) {
      PlayerInventory inv = player.getInventory();
      inv.clear();
      applyArmor(inv, kit.armor());
      inv.setStorageContents(cloneArray(kit.inventory(), inv.getStorageContents().length));
      inv.setItemInOffHand(kit.offhand() == null ? new ItemStack(Material.AIR) : kit.offhand().clone());
      player.getActivePotionEffects().forEach(effectx -> player.removePotionEffect(effectx.getType()));

      for (PotionEffect effect : kit.effects()) {
         player.addPotionEffect(effect);
      }

      if (this.trimService != null) {
         this.trimService.applyToLiveArmor(player, kit);
      }

      player.updateInventory();
   }

   public void applyForPlayer(Player player, Kit kit, PlayerDataManager playerData) {
      if (playerData == null || !playerData.hasKitLayout(player.getUniqueId(), kit.name())) {
         this.apply(player, kit);
         return;
      }

      PlayerInventory inv = player.getInventory();
      inv.clear();
      ItemStack[] storage = playerData.getKitLayoutInventory(player.getUniqueId(), kit.name());
      ItemStack[] armor = playerData.getKitLayoutArmor(player.getUniqueId(), kit.name());
      ItemStack offhand = playerData.getKitLayoutOffhand(player.getUniqueId(), kit.name());
      applyArmor(inv, armor != null ? armor : kit.armor());
      inv.setStorageContents(cloneArray(storage != null ? storage : kit.inventory(), inv.getStorageContents().length));
      inv.setItemInOffHand(offhand == null ? new ItemStack(Material.AIR) : offhand.clone());
      player.getActivePotionEffects().forEach(effectx -> player.removePotionEffect(effectx.getType()));

      for (PotionEffect effect : kit.effects()) {
         player.addPotionEffect(effect);
      }

      if (this.trimService != null) {
         this.trimService.applyToLiveArmor(player, kit);
      }

      player.updateInventory();
   }

   public boolean addStartBuff(String kitName, PotionEffect effect) {
      if (kitName != null && effect != null) {
         Kit kit = this.kits.get(kitName.toLowerCase(Locale.ROOT));
         if (kit == null) {
            return false;
         } else {
            List<PotionEffect> updated = new ArrayList<>(kit.startBuffs());
            updated.removeIf(e -> e.getType().equals(effect.getType()));
            updated.add(effect);
            kit.setStartBuffs(updated);
            return this.save(kit);
         }
      } else {
         return false;
      }
   }

   public Optional<Kit> find(String name) {
      return name == null ? Optional.empty() : Optional.ofNullable(this.kits.get(name.toLowerCase(Locale.ROOT)));
   }

   public Collection<Kit> all() {
      return this.kits.values();
   }

   private boolean save(Kit kit) {
      File file = new File(this.kitsDirectory(), kit.name() + ".yml");
      YamlConfiguration cfg = new YamlConfiguration();
      cfg.set("name", kit.name());
      cfg.set("display-name", kit.displayName());
      cfg.set("inventory", cloneArray(kit.inventory(), 36));
      writeArmor(cfg, kit.armor());
      cfg.set("offhand", kit.offhand());
      cfg.set("effects", writeEffects(kit.effects()));
      cfg.set("start-buffs", writeEffects(kit.startBuffs()));
      cfg.set("icon.material", kit.iconMaterial().name());
      cfg.set("icon.slot", kit.iconSlot());

      try {
         cfg.save(file);
         return true;
      } catch (IOException var5) {
         this.plugin.getLogger().log(Level.SEVERE, "Failed to save kit " + kit.name(), (Throwable)var5);
         return false;
      }
   }

   private File kitsDirectory() {
      return new File(this.plugin.getDataFolder(), "kits");
   }

   private static ItemStack[] fromEquippedArmor(PlayerInventory inv) {
      return new ItemStack[]{cloneItem(inv.getBoots()), cloneItem(inv.getLeggings()), cloneItem(inv.getChestplate()), cloneItem(inv.getHelmet())};
   }

   private static void applyArmor(PlayerInventory inv, ItemStack[] armor) {
      if (armor != null && armor.length >= 4) {
         inv.setBoots(cloneItem(armor[0]));
         inv.setLeggings(cloneItem(armor[1]));
         inv.setChestplate(cloneItem(armor[2]));
         inv.setHelmet(cloneItem(armor[3]));
      } else {
         inv.setBoots(null);
         inv.setLeggings(null);
         inv.setChestplate(null);
         inv.setHelmet(null);
      }
   }

   private static void writeArmor(YamlConfiguration cfg, ItemStack[] armor) {
      ConfigurationSection section = cfg.createSection("armor");
      if (armor != null && armor.length >= 4) {
         section.set("boots", armor[0]);
         section.set("leggings", armor[1]);
         section.set("chestplate", armor[2]);
         section.set("helmet", armor[3]);
      }
   }

   private static ItemStack[] readArmor(YamlConfiguration cfg) {
      ConfigurationSection section = cfg.getConfigurationSection("armor");
      return section != null
         ? new ItemStack[]{section.getItemStack("boots"), section.getItemStack("leggings"), section.getItemStack("chestplate"), section.getItemStack("helmet")}
         : readItemArray(cfg, "armor", 4);
   }

   private static Material guessIcon(PlayerInventory inv) {
      ItemStack chest = inv.getChestplate();
      if (chest != null && chest.getType() != Material.AIR) {
         return chest.getType();
      } else {
         ItemStack main = inv.getItemInMainHand();
         return main != null && main.getType() != Material.AIR ? main.getType() : Material.DIAMOND_SWORD;
      }
   }

   private static ItemStack cloneItem(ItemStack item) {
      return item == null ? null : item.clone();
   }

   private static ItemStack[] cloneArray(ItemStack[] source, int size) {
      ItemStack[] result = new ItemStack[size];
      if (source == null) {
         return result;
      } else {
         for (int i = 0; i < Math.min(source.length, size); i++) {
            result[i] = source[i] == null ? null : source[i].clone();
         }

         return result;
      }
   }

   private static List<Map<String, Object>> writeEffects(List<PotionEffect> effects) {
      List<Map<String, Object>> list = new ArrayList<>();

      for (PotionEffect effect : effects) {
         Map<String, Object> map = new LinkedHashMap<>();
         map.put("type", effect.getType().getKey().toString());
         map.put("duration", effect.getDuration());
         map.put("amplifier", effect.getAmplifier());
         map.put("ambient", effect.isAmbient());
         map.put("particles", effect.hasParticles());
         map.put("icon", effect.hasIcon());
         list.add(map);
      }

      return list;
   }

   private static ItemStack[] readItemArray(YamlConfiguration cfg, String path, int size) {
      Object raw = cfg.get(path);
      if (raw instanceof ItemStack[]) {
         return (ItemStack[])raw;
      } else if (raw instanceof List<?> list) {
         ItemStack[] result = new ItemStack[Math.max(size, list.size())];

         for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof ItemStack stack) {
               result[i] = stack;
            }
         }

         return result;
      } else {
         return new ItemStack[size];
      }
   }

   private static List<PotionEffect> readEffects(YamlConfiguration cfg) {
      return readEffects(cfg, "effects");
   }

   private static List<PotionEffect> readEffects(YamlConfiguration cfg, String path) {
      List<PotionEffect> result = new ArrayList<>();

      for (Map<?, ?> map : cfg.getMapList(path)) {
         Object typeObj = map.get("type");
         if (typeObj != null) {
            PotionEffectType type = PotionEffectType.getByName(String.valueOf(typeObj));
            if (type == null) {
               type = PotionEffectType.getByKey(NamespacedKey.minecraft(String.valueOf(typeObj).toLowerCase(Locale.ROOT).replace("minecraft:", "")));
            }

            if (type != null) {
               int duration = map.get("duration") instanceof Number n ? n.intValue() : 200;
               int amplifier = map.get("amplifier") instanceof Number nx ? nx.intValue() : 0;
               boolean ambient = Boolean.TRUE.equals(map.get("ambient"));
               boolean particles = !Boolean.FALSE.equals(map.get("particles"));
               boolean icon = !Boolean.FALSE.equals(map.get("icon"));
               result.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
            }
         }
      }

      return result;
   }
}
