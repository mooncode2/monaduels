package org.Mona.monaDuels.display;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.duel.DuelSession;
import org.Mona.monaDuels.duel.DuelState;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class DuelBossBarService {
   private final ConfigManager config;
   private final KitManager kitManager;
   private final PlayerDataManager playerData;
   private final Map<UUID, BossBar> bossBars = new HashMap<>();

   public DuelBossBarService(ConfigManager config, KitManager kitManager, PlayerDataManager playerData) {
      this.config = config;
      this.kitManager = kitManager;
      this.playerData = playerData;
   }

   public void update(Player viewer, Player opponent, DuelSession session, boolean spectating) {
      if (!this.config.bossBarEnabled() || this.playerData != null && !this.playerData.isBossBarEnabled(viewer.getUniqueId())) {
         this.hide(viewer);
      } else {
         BossBar bar = this.bossBars.computeIfAbsent(viewer.getUniqueId(), id -> this.createBar());
         viewer.showBossBar(bar);
         bar.name(ColorUtil.component(this.buildText(viewer, opponent, session, spectating)));
         bar.progress(1.0F);
      }
   }

   public void hide(Player player) {
      BossBar bar = this.bossBars.remove(player.getUniqueId());
      if (bar != null) {
         player.hideBossBar(bar);
      }
   }

   public void clearAll() {
      for (Entry<UUID, BossBar> entry : this.bossBars.entrySet()) {
         Player player = Bukkit.getPlayer(entry.getKey());
         if (player != null) {
            player.hideBossBar(entry.getValue());
         }
      }

      this.bossBars.clear();
   }

   private BossBar createBar() {
      return BossBar.bossBar(ColorUtil.component(" "), 1.0F, parseColor(this.config.bossBarColor()), Overlay.PROGRESS);
   }

   private String buildText(Player viewer, Player opponent, DuelSession session, boolean spectating) {
      String kitDisplay = this.kitManager.find(session.kitName()).map(k -> ColorUtil.color(k.displayName())).orElse(session.kitName());
      String status;
      if (spectating) {
         status = this.config.bossBarStatusSpectating();
      } else if (session.state() == DuelState.ACTIVE) {
         status = this.config.bossBarStatusInGame();
      } else {
         status = this.config.bossBarStatusPreparing();
      }

      Map<String, String> ph = new HashMap<>();
      ph.put("opponent", opponent.getName());
      ph.put("player", viewer.getName());
      ph.put("kit", kitDisplay);
      ph.put("status", status);
      return apply(this.config.bossBarFormat(), ph);
   }

   private static String apply(String raw, Map<String, String> placeholders) {
      String result = raw;

      for (Entry<String, String> entry : placeholders.entrySet()) {
         result = result.replace("{" + entry.getKey() + "}", entry.getValue());
      }

      return result;
   }

   private static Color parseColor(String name) {
      try {
         return Color.valueOf(name.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException var2) {
         return Color.RED;
      }
   }
}
