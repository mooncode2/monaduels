package org.Mona.monaDuels.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class PlayerSnapshot {
   private final ItemStack[] contents;
   private final ItemStack[] armor;
   private final ItemStack offhand;
   private final Collection<PotionEffect> effects;
   private final Location location;
   private final GameMode gameMode;
   private final double health;
   private final int foodLevel;
   private final float saturation;
   private final int level;
   private final float exp;

   private PlayerSnapshot(Player player) {
      this.contents = clone(player.getInventory().getStorageContents());
      this.armor = clone(player.getInventory().getArmorContents());
      this.offhand = player.getInventory().getItemInOffHand().clone();
      this.effects = new ArrayList<>(player.getActivePotionEffects());
      this.location = player.getLocation().clone();
      this.gameMode = player.getGameMode();
      this.health = player.getHealth();
      this.foodLevel = player.getFoodLevel();
      this.saturation = player.getSaturation();
      this.level = player.getLevel();
      this.exp = player.getExp();
   }

   public static PlayerSnapshot capture(Player player) {
      Objects.requireNonNull(player, "player cannot be null");
      return new PlayerSnapshot(player);
   }

   public boolean includesInventory() {
      return true;
   }

   public Location location() {
      return this.location.clone();
   }

   public World world() {
      return this.location.getWorld();
   }

   public void restoreLocation(Player player) {
      player.teleport(this.location);
   }

   public void restoreInventoryOnly(Player player) {
      player.getInventory().clear();
      player.getInventory().setStorageContents(clone(this.contents));
      player.getInventory().setArmorContents(clone(this.armor));
      player.getInventory().setItemInOffHand(this.offhand == null ? null : this.offhand.clone());
      player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

      for (PotionEffect effect : this.effects) {
         player.addPotionEffect(effect);
      }

      player.updateInventory();
   }

   public void restore(Player player) {
      player.getInventory().clear();
      player.getInventory().setStorageContents(clone(this.contents));
      player.getInventory().setArmorContents(clone(this.armor));
      player.getInventory().setItemInOffHand(this.offhand == null ? null : this.offhand.clone());
      player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

      for (PotionEffect effect : this.effects) {
         player.addPotionEffect(effect);
      }

      player.updateInventory();
      player.teleport(this.location);
      player.setGameMode(this.gameMode);
      player.setHealth(Math.min(this.health, player.getMaxHealth()));
      player.setFoodLevel(this.foodLevel);
      player.setSaturation(this.saturation);
      player.setLevel(this.level);
      player.setExp(this.exp);
   }

   private static ItemStack[] clone(ItemStack[] source) {
      if (source == null) {
         return new ItemStack[0];
      } else {
         ItemStack[] copy = new ItemStack[source.length];

         for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
         }

         return copy;
      }
   }
}
