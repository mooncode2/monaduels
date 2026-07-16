package org.Mona.monaDuels.arena;

import java.util.Objects;
import org.bukkit.Location;

public final class Arena {
   private final String name;
   private String displayName;
   private String world;
   private Location spawn1;
   private Location spawn2;
   private Location spectatorSpawn;
   private boolean enabled;
   private boolean occupied;

   public Arena(String name, String world, Location spawn1, Location spawn2, boolean enabled) {
      this.name = Objects.requireNonNull(name, "name");
      this.displayName = name;
      this.world = world;
      this.spawn1 = spawn1;
      this.spawn2 = spawn2;
      this.enabled = enabled;
      this.occupied = false;
   }

   public String name() {
      return this.name;
   }

   public String displayName() {
      return this.displayName != null && !this.displayName.isBlank() ? this.displayName : this.name;
   }

   public String getDisplayNameForUI() {
      return this.displayName;
   }

   public void setDisplayName(String displayName) {
      this.displayName = displayName != null && !displayName.isBlank() ? displayName : this.name;
   }

   public String world() {
      return this.world;
   }

   public void setWorld(String world) {
      this.world = world;
   }

   public Location spawn1() {
      return this.spawn1;
   }

   public void setSpawn1(Location spawn1) {
      this.spawn1 = spawn1;
   }

   public Location spawn2() {
      return this.spawn2;
   }

   public void setSpawn2(Location spawn2) {
      this.spawn2 = spawn2;
   }

   public Location spectatorSpawn() {
      return this.spectatorSpawn;
   }

   public void setSpectatorSpawn(Location spectatorSpawn) {
      this.spectatorSpawn = spectatorSpawn;
   }

   public boolean enabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean occupied() {
      return this.occupied;
   }

   public void setOccupied(boolean occupied) {
      this.occupied = occupied;
   }

   public boolean isReady() {
      return this.enabled && this.spawn1 != null && this.spawn2 != null && this.spawn1.getWorld() != null && this.spawn2.getWorld() != null;
   }

   public boolean isFree() {
      return this.isReady() && !this.occupied;
   }
}
