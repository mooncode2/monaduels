package org.Mona.monaDuels.service;

import org.Mona.monaDuels.config.ConfigManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class DuelSoundService {
   private final ConfigManager config;

   public DuelSoundService(ConfigManager config) {
      this.config = config;
   }

   public void playCountdownTick(Player player) {
      this.play(player, this.config.soundCountdownTick(), this.config.soundCountdownTickVolume(), this.config.soundCountdownTickPitch());
   }

   public void playDuelStart(Player player) {
      this.play(player, this.config.soundDuelStart(), this.config.soundDuelStartVolume(), this.config.soundDuelStartPitch());
   }

   public void playVictory(Player player) {
      this.play(player, this.config.soundVictory(), this.config.soundVictoryVolume(), this.config.soundVictoryPitch());
   }

   public void playDefeat(Player player) {
      this.play(player, this.config.soundDefeat(), this.config.soundDefeatVolume(), this.config.soundDefeatPitch());
   }

   private void play(Player player, String soundName, float volume, float pitch) {
      if (this.config.soundsEnabled() && soundName != null && !soundName.isBlank()) {
         try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
         } catch (IllegalArgumentException var6) {
         }
      }
   }
}
