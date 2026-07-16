package org.Mona.monaDuels.celebration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.util.ConfigurableItemParser;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class CelebrationService {
   public static final String DEFAULT_ID = "victory_burst";
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final PlayerDataManager playerData;
   private final MessageService messages;
   private final Map<String, CelebrationService.EffectDef> effects = new LinkedHashMap<>();
   private String defaultId = "victory_burst";

   public CelebrationService(MonaDuels plugin, ConfigManager config, PlayerDataManager playerData, MessageService messages) {
      this.plugin = plugin;
      this.config = config;
      this.playerData = playerData;
      this.messages = messages;
      this.reloadEffects();
   }

   public void reloadEffects() {
      this.effects.clear();
      this.defaultId = this.config.defaultKillEffect().toLowerCase(Locale.ROOT);
      ConfigurationSection section = this.config.killEffectsSection();
      if (section != null) {
         for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s != null) {
               String key = id.toLowerCase(Locale.ROOT);
               String type = s.getString("type", key).toLowerCase(Locale.ROOT);
               String display = s.getString("display-name", s.getString("name", id));
               Material icon = ConfigurableItemParser.parseMaterial(s.getString("icon", "FIREWORK_ROCKET"));
               String permission = s.getString("permission");
               String group = s.getString("group");
               this.effects.put(key, new CelebrationService.EffectDef(key, type, display, icon, permission, group, s));
            }
         }
      }

      if (!this.effects.containsKey("victory_burst")) {
         this.effects.put(
            "victory_burst",
            new CelebrationService.EffectDef("victory_burst", "victory_burst", "Празднование победы", Material.SPLASH_POTION, null, null, null)
         );
      }

      if (!this.effects.containsKey(this.defaultId)) {
         this.defaultId = "victory_burst";
      }
   }

   public void playKillEffect(Player killer, Location location) {
      this.playKillEffect(killer, location, null);
   }

   public void playKillEffect(Player killer, Location location, String kitName) {
      if (killer != null && location != null && location.getWorld() != null) {
         // Per-kit choice first (Rev. 2), then the legacy global choice.
         String id = kitName == null ? "" : this.playerData.getKitCelebration(killer.getUniqueId(), kitName);
         if (id == null || id.isBlank()) {
            id = this.playerData.getCelebration(killer.getUniqueId());
         }

         CelebrationService.EffectDef def = id == null || id.isBlank() ? null : this.effects.get(id.toLowerCase(Locale.ROOT));
         if (def == null || !this.canAccess(killer) || !this.canUse(killer, def)) {
            def = this.effects.get(this.defaultId);
         }

         if (def == null) {
            this.playVictoryBurst(killer, location);
         } else {
            switch (def.type()) {
               case "lightning":
                  this.playLightning(def, location);
                  break;
               case "firework":
                  this.playFirework(def, location);
                  break;
               case "particle":
                  this.playParticle(def, location);
                  break;
               case "sound-burst":
                  this.playSoundBurst(def, location);
                  break;
               case "xp-fountain":
                  this.playXpFountain(def, location);
                  break;
               default:
                  this.playVictoryBurst(killer, location);
            }
         }
      }
   }

   /** Global gate (Rev. 2): choosing celebrations is limited to the monaprime group/permission. */
   public boolean canAccess(Player player) {
      if (!this.config.celebrationsEnabled() || player == null) {
         return false;
      }

      String perm = this.config.celebrationsPermission();
      String group = this.config.celebrationsGroup();
      boolean permSet = perm != null && !perm.isBlank();
      boolean groupSet = group != null && !group.isBlank();
      if (!permSet && !groupSet) {
         return true;
      }

      if (permSet && player.hasPermission(perm)) {
         return true;
      }

      return groupSet && player.hasPermission("group." + group.toLowerCase(Locale.ROOT));
   }

   public boolean canUse(Player player, String id) {
      return this.canUse(player, id == null ? null : this.effects.get(id.toLowerCase(Locale.ROOT)));
   }

   private boolean canUse(Player player, CelebrationService.EffectDef def) {
      if (def == null) {
         return false;
      }

      boolean permSet = def.permission() != null && !def.permission().isBlank();
      boolean groupSet = def.group() != null && !def.group().isBlank();
      if (!permSet && !groupSet) {
         return true;
      }

      if (permSet && player.hasPermission(def.permission())) {
         return true;
      }

      return groupSet && player.hasPermission("group." + def.group().toLowerCase(Locale.ROOT));
   }

   public Collection<CelebrationService.EffectDef> allEffects() {
      return this.effects.values();
   }

   public void setCelebration(Player player, String celebrationId) {
      String id = celebrationId == null ? this.defaultId : celebrationId.toLowerCase(Locale.ROOT);
      CelebrationService.EffectDef def = this.effects.get(id);
      if (def == null) {
         id = this.defaultId;
         def = this.effects.get(id);
      }

      if (def != null && !this.canUse(player, def)) {
         this.messages.send(player, "celebration.locked", Map.of("celebration", this.displayName(id)));
      } else {
         this.playerData.setCelebration(player.getUniqueId(), id);
         this.messages.send(player, "celebration.selected", Map.of("celebration", this.displayName(id)));
      }
   }

   public String displayName(String id) {
      CelebrationService.EffectDef def = id == null ? null : this.effects.get(id.toLowerCase(Locale.ROOT));
      return def != null ? def.displayName() : id;
   }

   public String currentCelebration(Player player) {
      String id = this.playerData.getCelebration(player.getUniqueId());
      return id == null || id.isBlank() ? this.defaultId : id;
   }

   public String currentCelebrationForKit(Player player, String kitName) {
      String id = this.playerData.getKitCelebration(player.getUniqueId(), kitName);
      return id == null || id.isBlank() ? this.currentCelebration(player) : id;
   }

   /** Sets the celebration for one kit; returns true if it was accepted. */
   public boolean setCelebrationForKit(Player player, String kitName, String celebrationId) {
      String id = celebrationId == null ? this.defaultId : celebrationId.toLowerCase(Locale.ROOT);
      CelebrationService.EffectDef def = this.effects.get(id);
      if (def == null) {
         id = this.defaultId;
         def = this.effects.get(id);
      }

      if (def == null || !this.canUse(player, def)) {
         this.messages.send(player, "celebration.locked", Map.of("celebration", this.displayName(id)));
         return false;
      }

      this.playerData.setKitCelebration(player.getUniqueId(), kitName, id);
      this.messages.send(player, "celebration.selected", Map.of("celebration", this.displayName(id)));
      return true;
   }

   /** Quiet bulk write used by «Применить ко всем» — the caller sends the summary message. */
   public boolean applyToKits(Player player, Collection<String> kitNames, String celebrationId) {
      String id = celebrationId == null ? this.defaultId : celebrationId.toLowerCase(Locale.ROOT);
      CelebrationService.EffectDef def = this.effects.get(id);
      if (def == null || !this.canUse(player, def)) {
         return false;
      }

      for (String kitName : kitNames) {
         this.playerData.setKitCelebration(player.getUniqueId(), kitName, id);
      }

      return true;
   }

   private void playLightning(CelebrationService.EffectDef def, Location location) {
      World world = location.getWorld();
      int count = def.params() != null ? Math.max(1, def.params().getInt("count", 1)) : 1;

      for (int i = 0; i < count; i++) {
         world.strikeLightningEffect(location);
      }

      this.playConfiguredSound(def, location);
   }

   private void playFirework(CelebrationService.EffectDef def, Location location) {
      World world = location.getWorld();
      ConfigurationSection params = def.params();
      int count = params != null ? Math.max(1, params.getInt("count", 1)) : 1;
      FireworkEffect.Type shape = parseFireworkType(params != null ? params.getString("shape", "BALL_LARGE") : "BALL_LARGE");
      List<Color> colors = parseColors(params != null ? params.getStringList("colors") : null, List.of(Color.RED, Color.YELLOW, Color.WHITE));
      List<Color> fade = parseColors(params != null ? params.getStringList("fade-colors") : null, List.of());
      boolean flicker = params == null || params.getBoolean("flicker", true);
      boolean trail = params == null || params.getBoolean("trail", true);
      FireworkEffect.Builder builder = FireworkEffect.builder().with(shape).flicker(flicker).trail(trail).withColor(colors);
      if (!fade.isEmpty()) {
         builder.withFade(fade);
      }

      FireworkEffect effect = builder.build();
      Location spawnAt = location.clone().add(0.0, 2.0, 0.0);

      for (int i = 0; i < count; i++) {
         Firework firework = world.spawn(spawnAt, Firework.class);
         firework.addScoreboardTag("monaduels_killfx");
         FireworkMeta meta = firework.getFireworkMeta();
         meta.addEffect(effect);
         meta.setPower(0);
         firework.setFireworkMeta(meta);
         this.plugin.getServer().getScheduler().runTask(this.plugin, firework::detonate);
      }

      this.playConfiguredSound(def, location);
   }

   private void playParticle(CelebrationService.EffectDef def, Location location) {
      World world = location.getWorld();
      ConfigurationSection params = def.params();
      Particle particle = parseParticle(params != null ? params.getString("particle") : null, Particle.CRIT);
      int count = params != null ? Math.max(1, params.getInt("count", 40)) : 40;
      double spread = params != null ? params.getDouble("spread", 0.5) : 0.5;
      double speed = params != null ? params.getDouble("speed", 0.05) : 0.05;
      world.spawnParticle(particle, location.clone().add(0.0, 1.0, 0.0), count, spread, spread, spread, speed);
      this.playConfiguredSound(def, location);
   }

   /** «Фонтан опыта»: a burst of small XP orbs scattered around the kill location. */
   private void playXpFountain(CelebrationService.EffectDef def, Location location) {
      World world = location.getWorld();
      ConfigurationSection params = def.params();
      int count = params != null ? Math.max(1, Math.min(30, params.getInt("count", 12))) : 12;
      Location center = location.clone().add(0.0, 0.8, 0.0);
      java.util.Random random = new java.util.Random();

      for (int i = 0; i < count; i++) {
         Location spawnAt = center.clone().add(random.nextDouble() - 0.5, random.nextDouble() * 0.6, random.nextDouble() - 0.5);
         world.spawn(spawnAt, ExperienceOrb.class, orb -> {
            orb.setExperience(1);
            orb.setVelocity(new Vector((random.nextDouble() - 0.5) * 0.3, 0.3 + random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.3));
         });
      }

      this.playConfiguredSound(def, location);
   }

   private void playSoundBurst(CelebrationService.EffectDef def, Location location) {
      ConfigurationSection params = def.params();
      if (params != null && params.getString("particle") != null) {
         Particle particle = parseParticle(params.getString("particle"), Particle.TOTEM_OF_UNDYING);
         int count = Math.max(1, params.getInt("count", 30));
         location.getWorld().spawnParticle(particle, location.clone().add(0.0, 1.0, 0.0), count, 0.4, 0.5, 0.4, 0.1);
      }

      this.playConfiguredSound(def, location);
   }

   private void playConfiguredSound(CelebrationService.EffectDef def, Location location) {
      if (def.params() != null) {
         String soundName = def.params().getString("sound");
         if (soundName != null && !soundName.isBlank()) {
            try {
               Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
               float volume = (float)def.params().getDouble("sound-volume", 1.0);
               float pitch = (float)def.params().getDouble("sound-pitch", 1.0);
               location.getWorld().playSound(location, sound, volume, pitch);
            } catch (IllegalArgumentException var7) {
            }
         }
      }
   }

   private void playVictoryBurst(final Player killer, final Location center) {
      int ticks = 24;
      final BukkitTask[] ref = new BukkitTask[1];
      ref[0] = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, new Runnable() {
         int step = 0;

         @Override
         public void run() {
            if (center.getWorld() == null) {
               ref[0].cancel();
            } else if (this.step >= 24) {
               ref[0].cancel();
               center.getWorld().playSound(center, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.5F, 0.0F);
            } else {
               float pitch = 0.5F + (float)this.step / 24.0F * 1.5F;
               killer.playSound(center, Sound.ENTITY_ITEM_PICKUP, 0.8F, pitch);
               int particleCount = 2 + this.step / 2;
               double radius = 0.3 + (double)this.step * 0.04;
               double angleBase = (double)this.step * 0.45;

               for (int i = 0; i < particleCount; i++) {
                  double angle = angleBase + (Math.PI * 2) * (double)i / (double)Math.max(1, particleCount);
                  double x = center.getX() + Math.cos(angle) * radius;
                  double z = center.getZ() + Math.sin(angle) * radius;
                  double y = center.getY() + 0.5 + (double)(this.step % 3) * 0.15;
                  center.getWorld().spawnParticle(Particle.CRIT, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                  if (this.step > 12) {
                     center.getWorld().spawnParticle(Particle.ENCHANT, x, y + 0.2, z, 1, 0.02, 0.02, 0.02, 0.01);
                  }
               }

               this.step++;
            }
         }
      }, 0L, 2L);
   }

   private static Particle parseParticle(String name, Particle fallback) {
      if (name == null || name.isBlank()) {
         return fallback;
      }

      try {
         return Particle.valueOf(name.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var3) {
         return fallback;
      }
   }

   private static FireworkEffect.Type parseFireworkType(String name) {
      if (name == null) {
         return FireworkEffect.Type.BALL_LARGE;
      }

      try {
         return FireworkEffect.Type.valueOf(name.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var2) {
         return FireworkEffect.Type.BALL_LARGE;
      }
   }

   private static List<Color> parseColors(List<String> names, List<Color> fallback) {
      if (names == null || names.isEmpty()) {
         return fallback;
      }

      List<Color> colors = new ArrayList<>();

      for (String name : names) {
         Color color = parseColor(name);
         if (color != null) {
            colors.add(color);
         }
      }

      return colors.isEmpty() ? fallback : colors;
   }

   private static Color parseColor(String name) {
      if (name == null || name.isBlank()) {
         return null;
      }

      String value = name.trim();
      if (value.startsWith("#") && value.length() == 7) {
         try {
            return Color.fromRGB(Integer.parseInt(value.substring(1), 16));
         } catch (NumberFormatException var3) {
            return null;
         }
      }

      switch (value.toUpperCase(Locale.ROOT)) {
         case "RED":
            return Color.RED;
         case "ORANGE":
         case "GOLD":
            return Color.ORANGE;
         case "YELLOW":
            return Color.YELLOW;
         case "GREEN":
         case "LIME":
            return Color.LIME;
         case "DARK_GREEN":
            return Color.GREEN;
         case "AQUA":
         case "CYAN":
            return Color.AQUA;
         case "BLUE":
            return Color.BLUE;
         case "PURPLE":
         case "MAGENTA":
            return Color.PURPLE;
         case "FUCHSIA":
         case "PINK":
            return Color.FUCHSIA;
         case "BLACK":
            return Color.BLACK;
         case "GRAY":
         case "GREY":
            return Color.GRAY;
         case "SILVER":
            return Color.SILVER;
         case "TEAL":
            return Color.TEAL;
         case "NAVY":
            return Color.NAVY;
         case "MAROON":
            return Color.MAROON;
         case "OLIVE":
            return Color.OLIVE;
         default:
            return Color.WHITE;
      }
   }

   public static record EffectDef(
      String id, String type, String displayName, Material icon, String permission, String group, ConfigurationSection params
   ) {
   }
}
