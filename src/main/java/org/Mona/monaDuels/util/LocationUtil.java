package org.Mona.monaDuels.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class LocationUtil {
   private LocationUtil() {
   }

   public static void writeLocation(ConfigurationSection section, Location location) {
      if (section != null && location != null) {
         section.set("world", location.getWorld() != null ? location.getWorld().getName() : "world");
         section.set("x", location.getX());
         section.set("y", location.getY());
         section.set("z", location.getZ());
         section.set("yaw", location.getYaw());
         section.set("pitch", location.getPitch());
      }
   }

   public static Location readLocation(ConfigurationSection section) {
      if (section == null) {
         return null;
      } else {
         String worldName = section.getString("world");
         if (worldName != null && !worldName.isBlank()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
               return null;
            } else {
               double x = section.getDouble("x");
               double y = section.getDouble("y");
               double z = section.getDouble("z");
               float yaw = (float)section.getDouble("yaw");
               float pitch = (float)section.getDouble("pitch");
               return new Location(world, x, y, z, yaw, pitch);
            }
         } else {
            return null;
         }
      }
   }

   public static Location readSpawn(ConfigurationSection arenaSection, String spawnKey, String fallbackWorld) {
      ConfigurationSection spawn = arenaSection.getConfigurationSection(spawnKey);
      if (spawn == null) {
         return null;
      } else {
         if (!spawn.contains("world")) {
            spawn.set("world", fallbackWorld);
         }

         return readLocation(spawn);
      }
   }
}
