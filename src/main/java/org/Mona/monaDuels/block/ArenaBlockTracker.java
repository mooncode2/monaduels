package org.Mona.monaDuels.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

public final class ArenaBlockTracker {
   private final Set<Location> placedBlocks = new HashSet<>();
   private final List<ArenaBlockTracker.BrokenBlockRecord> brokenBlocks = new ArrayList<>();

   public void trackPlaced(Block block) {
      this.placedBlocks.add(normalize(block.getLocation()));
   }

   public void trackBroken(Block block) {
      this.brokenBlocks.add(new ArenaBlockTracker.BrokenBlockRecord(normalize(block.getLocation()), block.getBlockData().clone()));
   }

   public void cleanupPlaced() {
      for (Location location : this.placedBlocks) {
         World world = location.getWorld();
         if (world != null) {
            Block block = world.getBlockAt(location);
            block.setType(Material.AIR, false);
         }
      }

      this.placedBlocks.clear();
   }

   public void restoreBroken() {
      for (ArenaBlockTracker.BrokenBlockRecord record : this.brokenBlocks) {
         World world = record.location().getWorld();
         if (world != null) {
            Block block = world.getBlockAt(record.location());
            block.setBlockData(record.data(), false);
         }
      }

      this.brokenBlocks.clear();
   }

   public void reset() {
      this.cleanupPlaced();
      this.restoreBroken();
   }

   public boolean isTrackedPlaced(Location location) {
      return this.placedBlocks.contains(normalize(location));
   }

   private static Location normalize(Location location) {
      return new Location(location.getWorld(), (double)location.getBlockX(), (double)location.getBlockY(), (double)location.getBlockZ());
   }

   public static record BrokenBlockRecord(Location location, BlockData data) {
   }
}
