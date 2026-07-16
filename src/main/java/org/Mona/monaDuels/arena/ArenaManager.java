package org.Mona.monaDuels.arena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class ArenaManager {
   private final ConfigManager configManager;
   private final Map<String, Arena> arenas = new LinkedHashMap<>();

   public ArenaManager(ConfigManager configManager) {
      this.configManager = configManager;
   }

   public void load() {
      this.arenas.clear();
      ConfigurationSection section = this.configManager.arenas().getConfigurationSection("arenas");
      if (section != null) {
         for (String key : section.getKeys(false)) {
            ConfigurationSection arenaSec = section.getConfigurationSection(key);
            if (arenaSec != null) {
               String name = arenaSec.getString("name", key);
               String displayName = arenaSec.getString("display-name", arenaSec.getString("display_name", name));
               String world = arenaSec.getString("world", this.configManager.duelWorld());
               boolean enabled = arenaSec.getBoolean("enabled", true);
               Location spawn1 = LocationUtil.readSpawn(arenaSec, "spawn1", world);
               Location spawn2 = LocationUtil.readSpawn(arenaSec, "spawn2", world);
               Location spectatorSpawn = LocationUtil.readSpawn(arenaSec, "spectator-spawn", world);
               Arena arena = new Arena(name.toLowerCase(Locale.ROOT), world, spawn1, spawn2, enabled);
               arena.setDisplayName(displayName);
               arena.setSpectatorSpawn(spectatorSpawn);
               this.arenas.put(arena.name(), arena);
            }
         }
      }
   }

   public void save() {
      ConfigurationSection root = this.configManager.arenas().getConfigurationSection("arenas");
      if (root == null) {
         root = this.configManager.arenas().createSection("arenas");
      }

      for (String oldKey : new ArrayList<String>(root.getKeys(false))) {
         if (!this.arenas.containsKey(oldKey.toLowerCase(Locale.ROOT))) {
            root.set(oldKey, null);
         }
      }

      for (Arena arena : this.arenas.values()) {
         ConfigurationSection sec = root.getConfigurationSection(arena.name());
         if (sec == null) {
            sec = root.createSection(arena.name());
         }

         sec.set("name", arena.name());
         sec.set("display-name", arena.displayName());
         sec.set("world", arena.world());
         sec.set("enabled", arena.enabled());
         if (arena.spawn1() != null) {
            LocationUtil.writeLocation(sec.createSection("spawn1"), arena.spawn1());
         }

         if (arena.spawn2() != null) {
            LocationUtil.writeLocation(sec.createSection("spawn2"), arena.spawn2());
         }

         if (arena.spectatorSpawn() != null) {
            LocationUtil.writeLocation(sec.createSection("spectator-spawn"), arena.spectatorSpawn());
         }
      }

      this.configManager.saveArenas();
   }

   private String getArenaDisplayNameForUI(Arena arena) {
      return arena.getDisplayNameForUI();
   }

   public Optional<Arena> find(String name) {
      return name == null ? Optional.empty() : Optional.ofNullable(this.arenas.get(name.toLowerCase(Locale.ROOT)));
   }

   public Optional<Arena> allocateFreeArena() {
      for (Arena arena : this.arenas.values()) {
         if (arena.isFree()) {
            String worldName = arena.world();
            if (worldName != null && !worldName.isBlank()) {
               World world = Bukkit.getWorld(worldName);
               if (world != null) {
                  arena.setOccupied(true);
                  return Optional.of(arena);
               }
            }
         }
      }

      return Optional.empty();
   }

   public void release(Arena arena) {
      if (arena != null) {
         arena.setOccupied(false);
      }
   }

   public boolean create(String name, String world) {
      String key = name.toLowerCase(Locale.ROOT);
      if (this.arenas.containsKey(key)) {
         return false;
      } else {
         this.arenas.put(key, new Arena(key, world, null, null, true));
         this.save();
         return true;
      }
   }

   public boolean delete(String name) {
      Arena removed = this.arenas.remove(name.toLowerCase(Locale.ROOT));
      if (removed == null) {
         return false;
      } else {
         this.save();
         return true;
      }
   }

   public Collection<Arena> all() {
      return this.arenas.values();
   }
}
