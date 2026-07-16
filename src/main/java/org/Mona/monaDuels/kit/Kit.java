package org.Mona.monaDuels.kit;

import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class Kit {
   private final String name;
   private ItemStack[] inventory;
   private ItemStack[] armor;
   private ItemStack offhand;
   private List<PotionEffect> effects;
   private List<PotionEffect> startBuffs;
   private Material iconMaterial;
   private int iconSlot;
   private String displayName;

   public Kit(String name) {
      this.name = Objects.requireNonNull(name, "name").toLowerCase();
      this.inventory = new ItemStack[36];
      this.armor = new ItemStack[4];
      this.offhand = null;
      this.effects = List.of();
      this.startBuffs = List.of();
      this.iconMaterial = Material.DIAMOND_SWORD;
      this.iconSlot = -1;
      this.displayName = name;
   }

   public String name() {
      return this.name;
   }

   public String getDisplayNameForUI() {
      return this.displayName;
   }

   public ItemStack[] inventory() {
      return this.inventory;
   }

   public void setInventory(ItemStack[] inventory) {
      this.inventory = cloneContents(inventory, 36);
   }

   public ItemStack[] armor() {
      return this.armor;
   }

   public void setArmor(ItemStack[] armor) {
      this.armor = cloneContents(armor, 4);
   }

   public ItemStack offhand() {
      return this.offhand;
   }

   public void setOffhand(ItemStack offhand) {
      this.offhand = offhand == null ? null : offhand.clone();
   }

   public List<PotionEffect> effects() {
      return this.effects;
   }

   public void setEffects(List<PotionEffect> effects) {
      this.effects = effects == null ? List.of() : List.copyOf(effects);
   }

   public List<PotionEffect> startBuffs() {
      return this.startBuffs;
   }

   public void setStartBuffs(List<PotionEffect> startBuffs) {
      this.startBuffs = startBuffs == null ? List.of() : List.copyOf(startBuffs);
   }

   public Material iconMaterial() {
      return this.iconMaterial;
   }

   public void setIconMaterial(Material iconMaterial) {
      this.iconMaterial = iconMaterial != null ? iconMaterial : Material.BARRIER;
   }

   public int iconSlot() {
      return this.iconSlot;
   }

   public void setIconSlot(int iconSlot) {
      this.iconSlot = iconSlot;
   }

   public String displayName() {
      return this.displayName;
   }

   public void setDisplayName(String displayName) {
      this.displayName = displayName;
   }

   private static ItemStack[] cloneContents(ItemStack[] source, int size) {
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
}
