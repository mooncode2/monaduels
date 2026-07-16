package org.Mona.monaDuels.arena;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.kit.KitManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MapPoolManager {
   private final ConfigManager configManager;
   private final Map<String, Set<String>> arenaToKits = new LinkedHashMap<>();

   public MapPoolManager(ConfigManager configManager) {
      this.configManager = configManager;
   }

   public void load() {
      this.arenaToKits.clear();
      File file = new File(this.configManager.dataFolder(), "map-pools.yml");
      if (!file.exists()) {
         this.configManager.plugin().getLogger().warning("map-pools.yml not found; all kits can use all arenas.");
      } else {
         YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
         if (cfg.getKeys(false).isEmpty()) {
            this.configManager.plugin().getLogger().warning("map-pools.yml is empty; all kits can use all arenas.");
         } else {
            for (String arenaNameRaw : cfg.getKeys(false)) {
               String arenaName = normalize(arenaNameRaw);
               Object raw = cfg.get(arenaNameRaw);
               List<String> kits = new ArrayList<>();
               if (raw instanceof List) {
                  for (Object item : (List)raw) {
                     if (item != null && !String.valueOf(item).isBlank()) {
                        kits.add(normalize(String.valueOf(item)));
                     }
                  }

                  if (kits.isEmpty()) {
                     this.configManager.plugin().getLogger().warning("Arena '" + arenaNameRaw + "' has empty kit list in map-pools.yml.");
                  }

                  this.arenaToKits.put(arenaName, new LinkedHashSet<>(kits));
               } else {
                  this.configManager.plugin().getLogger().warning("Invalid map-pools entry for arena '" + arenaNameRaw + "': expected list of kits.");
               }
            }
         }
      }
   }

   public void validate(ArenaManager arenaManager, KitManager kitManager) {
      Set<String> kitNames = new LinkedHashSet<>();
      kitManager.all().forEach(kitx -> kitNames.add(normalize(kitx.name())));

      for (Entry<String, Set<String>> entry : this.arenaToKits.entrySet()) {
         String arenaName = entry.getKey();
         if (arenaManager.find(arenaName).isEmpty()) {
            this.configManager
               .plugin()
               .getLogger()
               .severe("map-pools.yml references unknown arena '" + arenaName + "'. Add it to arenas.yml or remove this entry.");
         }

         for (String kit : entry.getValue()) {
            if (!kitNames.contains(kit)) {
               this.configManager.plugin().getLogger().severe("map-pools.yml arena '" + arenaName + "' references unknown kit '" + kit + "'.");
            }
         }
      }

      for (Arena arena : arenaManager.all()) {
         if (!this.arenaToKits.containsKey(normalize(arena.name()))) {
            this.configManager.plugin().getLogger().severe("Arena '" + arena.name() + "' is missing in map-pools.yml. Add an entry with allowed kits.");
         }
      }
   }

   public Optional<Arena> findFreeArenaForKit(String kitName, ArenaManager arenaManager) {
      String kit = normalize(kitName);
      List<Arena> candidates = new ArrayList<>();

      for (Arena arena : arenaManager.all()) {
         if (arena.isFree()) {
            String worldName = arena.world();
            if (worldName != null && !worldName.isBlank() && Bukkit.getWorld(worldName) != null && this.isKitAllowedOnArena(arena.name(), kit)) {
               candidates.add(arena);
            }
         }
      }

      if (candidates.isEmpty()) {
         return Optional.empty();
      } else {
         Arena chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
         chosen.setOccupied(true);
         return Optional.of(chosen);
      }
   }

   private boolean isKitAllowedOnArena(String arenaName, String kitName) {
      Set<String> allowedKits = this.arenaToKits.get(normalize(arenaName));
      return allowedKits == null ? false : allowedKits.contains(kitName);
   }

   public Collection<String> arenasForKit(String kitName) {
      String kit = normalize(kitName);
      List<String> result = new ArrayList<>();

      for (Entry<String, Set<String>> entry : this.arenaToKits.entrySet()) {
         if (entry.getValue().contains(kit)) {
            result.add(entry.getKey());
         }
      }

      return result;
   }

   private static String normalize(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT);
   }
}
