package org.Mona.monaDuels.display;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.duel.DuelSession;
import org.Mona.monaDuels.duel.DuelState;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.service.DuelSoundService;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public final class DuelDisplayManager {
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final MessageService messages;
   private final DuelBossBarService bossBarService;
   private final DuelSoundService soundService;
   private final PlayerDataManager playerData;
   private final Map<UUID, DuelDisplayManager.SessionDisplayState> statesBySession = new ConcurrentHashMap<>();

   public DuelDisplayManager(
      MonaDuels plugin,
      ConfigManager config,
      MessageService messages,
      DuelBossBarService bossBarService,
      DuelSoundService soundService,
      PlayerDataManager playerData
   ) {
      this.plugin = plugin;
      this.config = config;
      this.messages = messages;
      this.bossBarService = bossBarService;
      this.soundService = soundService;
      this.playerData = playerData;
   }

   private boolean sidebarEnabledFor(UUID playerId) {
      return this.config.scoreboardEnabled() && (this.playerData == null || this.playerData.isScoreboardEnabled(playerId));
   }

   public void beginSession(DuelSession session, Player p1, Player p2) {
      DuelDisplayManager.SessionDisplayState state = new DuelDisplayManager.SessionDisplayState();
      state.originalScoreboardP1 = p1.getScoreboard();
      state.originalScoreboardP2 = p2.getScoreboard();
      this.statesBySession.put(session.sessionId(), state);
      this.updateDuelTabVisibility(p1, p2, state.hiddenFromP1);
      this.updateDuelTabVisibility(p2, p1, state.hiddenFromP2);
      this.startUpdateTask(session, p1, p2, state);
   }

   public void showMatchFound(Player p1, Player p2, String kitDisplay) {
      if (this.config.matchFoundEnabled()) {
         this.showMatchFoundToPlayer(p1, p2, kitDisplay);
         this.showMatchFoundToPlayer(p2, p1, kitDisplay);
      }
   }

   private void showMatchFoundToPlayer(Player player, Player opponent, String kitDisplay) {
      Map<String, String> placeholders = Map.of("opponent", opponent.getName(), "kit", kitDisplay == null ? "" : kitDisplay);
      String titleRaw = this.applyRawPlaceholders(this.config.matchFoundTitle(), placeholders);
      String subtitleRaw = this.applyRawPlaceholders(this.config.matchFoundSubtitle(), placeholders);
      String actionBarRaw = this.applyRawPlaceholders(this.config.matchFoundActionBar(), placeholders);
      Times times = Times.times(
         Duration.ofMillis((long)this.config.matchFoundFadeInTicks() * 50L),
         Duration.ofMillis((long)this.config.matchFoundStaySeconds() * 1000L),
         Duration.ofMillis((long)this.config.matchFoundFadeOutTicks() * 50L)
      );
      player.showTitle(Title.title(ColorUtil.component(titleRaw), ColorUtil.component(subtitleRaw), times));
      Component actionBar = ColorUtil.component(actionBarRaw);
      int durationTicks = this.config.matchFoundStaySeconds() * 20;
      BukkitTask barTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         if (player.isOnline()) {
            player.sendActionBar(actionBar);
         }
      }, 0L, 10L);
      Bukkit.getScheduler().runTaskLater(this.plugin, barTask::cancel, (long)durationTicks);
   }

   public void showCountdownTick(Player p1, Player p2, int seconds) {
      Map<String, String> ph = Map.of("seconds", String.valueOf(seconds));
      if (this.config.countdownChatEnabled()) {
         this.messages.send(p1, "countdown.tick", ph);
         this.messages.send(p2, "countdown.tick", ph);
      }

      if (this.config.countdownTitleEnabled()) {
         this.showTitle(p1, this.config.countdownTitleTick(), this.config.countdownSubtitleTick(), ph);
         this.showTitle(p2, this.config.countdownTitleTick(), this.config.countdownSubtitleTick(), ph);
      }

      this.soundService.playCountdownTick(p1);
      this.soundService.playCountdownTick(p2);
   }

   public void showCountdownStart(Player p1, Player p2) {
      if (this.config.countdownChatEnabled()) {
         this.messages.send(p1, "countdown.start");
         this.messages.send(p2, "countdown.start");
      }

      if (this.config.countdownTitleEnabled()) {
         this.showTitle(p1, this.config.countdownTitleStart(), this.config.countdownSubtitleStart(), Map.of());
         this.showTitle(p2, this.config.countdownTitleStart(), this.config.countdownSubtitleStart(), Map.of());
      }

      this.soundService.playDuelStart(p1);
      this.soundService.playDuelStart(p2);
   }

   public void clearScoreboards(DuelSession session) {
      DuelDisplayManager.SessionDisplayState state = this.statesBySession.get(session.sessionId());
      Player p1 = Bukkit.getPlayer(session.playerOneId());
      Player p2 = Bukkit.getPlayer(session.playerTwoId());
      if (p1 != null) {
         this.bossBarService.hide(p1);
         restorePlayer(p1, state == null ? null : state.originalScoreboardP1);
         this.restoreTabVisibility(p1, state == null ? Set.of() : state.hiddenFromP1);
      }

      if (p2 != null) {
         this.bossBarService.hide(p2);
         restorePlayer(p2, state == null ? null : state.originalScoreboardP2);
         this.restoreTabVisibility(p2, state == null ? Set.of() : state.hiddenFromP2);
      }

      for (UUID spectatorId : session.spectators()) {
         Player spectator = Bukkit.getPlayer(spectatorId);
         if (spectator != null) {
            this.bossBarService.hide(spectator);
            Scoreboard original = state == null ? null : state.originalScoreboards.get(spectatorId);
            restorePlayer(spectator, original);
         }
      }
   }

   public void endSession(DuelSession session, Player p1, Player p2) {
      DuelDisplayManager.SessionDisplayState state = this.statesBySession.remove(session.sessionId());
      if (state != null && state.updateTask != null) {
         state.updateTask.cancel();
      }

      if (p1 != null) {
         this.bossBarService.hide(p1);
         restorePlayer(p1, state == null ? null : state.originalScoreboardP1);
         this.restoreTabVisibility(p1, state == null ? Set.of() : state.hiddenFromP1);
         p1.hideTitle();
      }

      if (p2 != null) {
         this.bossBarService.hide(p2);
         restorePlayer(p2, state == null ? null : state.originalScoreboardP2);
         this.restoreTabVisibility(p2, state == null ? Set.of() : state.hiddenFromP2);
         p2.hideTitle();
      }

      for (UUID spectatorId : session.spectators()) {
         Player spectator = Bukkit.getPlayer(spectatorId);
         if (spectator != null) {
            this.bossBarService.hide(spectator);
            Scoreboard original = state == null ? null : state.originalScoreboards.remove(spectatorId);
            restorePlayer(spectator, original);
         }
      }
   }

   private void startUpdateTask(DuelSession session, Player p1, Player p2, DuelDisplayManager.SessionDisplayState state) {
      int interval = this.config.displayUpdateTicks();
      state.updateTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         if (p1.isOnline() && p2.isOnline()) {
            DuelState duelState = session.state();
            if (duelState != DuelState.ENDING) {
               if (duelState == DuelState.ACTIVE || duelState == DuelState.COUNTDOWN || duelState == DuelState.POST_FIGHT) {
                  boolean fightingPhase = duelState == DuelState.ACTIVE || duelState == DuelState.COUNTDOWN;
                  this.updateDuelTabVisibility(p1, p2, state.hiddenFromP1);
                  this.updateDuelTabVisibility(p2, p1, state.hiddenFromP2);
                  this.updateFighterBoard(p1, p2, state.originalScoreboardP1, fightingPhase);
                  this.updateFighterBoard(p2, p1, state.originalScoreboardP2, fightingPhase);

                  if (this.config.bossBarEnabled()) {
                     this.bossBarService.update(p1, p2, session, false);
                     this.bossBarService.update(p2, p1, session, false);
                  }

                  for (UUID spectatorId : session.spectators()) {
                     Player spectator = Bukkit.getPlayer(spectatorId);
                     if (spectator != null && spectator.isOnline()) {
                        state.originalScoreboards.putIfAbsent(spectatorId, spectator.getScoreboard());
                        if (fightingPhase && this.sidebarEnabledFor(spectatorId)) {
                           this.applyDualSidebarToPlayer(spectator, p1, p2, state.originalScoreboards.get(spectatorId));
                        }

                        if (this.config.bossBarEnabled()) {
                           this.bossBarService.update(spectator, p1, session, true);
                        }
                     }
                  }
               }
            }
         }
      }, (long)interval, (long)interval);
   }

   /** Per-player sidebar (Rev. 2 settings): the sidebar honours the viewer's own toggle. */
   private void updateFighterBoard(Player viewer, Player opponent, Scoreboard original, boolean fightingPhase) {
      if (fightingPhase && this.sidebarEnabledFor(viewer.getUniqueId())) {
         this.applyDualSidebarToPlayer(viewer, viewer, opponent, original);
      } else if (this.config.nametagHpEnabled()) {
         this.updateViewerNametag(viewer, opponent, original);
      }
   }

   private void updateViewerNametag(Player viewer, Player opponent, Scoreboard original) {
      Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
      this.applyDuelTabTeams(board, viewer, viewer, opponent, original);
      viewer.setScoreboard(board);
   }

   private void applyDualSidebarToPlayer(Player viewer, Player fighterOne, Player fighterTwo, Scoreboard original) {
      Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
      this.applyDualSidebar(board, fighterOne, fighterTwo);
      Player self = viewer.getUniqueId().equals(fighterTwo.getUniqueId()) ? fighterTwo : fighterOne;
      Player opponent = self.getUniqueId().equals(fighterOne.getUniqueId()) ? fighterTwo : fighterOne;
      this.applyDuelTabTeams(board, viewer, self, opponent, original);
      viewer.setScoreboard(board);
   }

   private void applyDuelTabTeams(Scoreboard board, Player viewer, Player self, Player opponent, Scoreboard original) {
      this.applyPlayerListTeam(board, original, "mdself", self, NamedTextColor.GREEN);
      this.applyPlayerListTeam(board, original, "mdopp", opponent, NamedTextColor.RED);
   }

   private void applyPlayerListTeam(Scoreboard board, Scoreboard original, String teamName, Player player, NamedTextColor color) {
      Team team = board.getTeam(teamName);
      if (team == null) {
         team = board.registerNewTeam(teamName);
         team.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.ALWAYS);
         team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
      }

      Team originalTeam = original == null ? null : original.getEntryTeam(player.getName());
      if (originalTeam != null) {
         team.prefix(originalTeam.prefix());
         team.suffix(originalTeam.suffix());
      }

      team.color(color);
      team.addEntry(player.getName());
   }

   private void applyDualSidebar(Scoreboard board, Player fighterOne, Player fighterTwo) {
      String title = this.applyDualPlaceholders(this.config.scoreboardTitle(), fighterOne, fighterTwo);
      Objective objective = board.registerNewObjective("monaduels", Criteria.DUMMY, ColorUtil.component(title));
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
      List<String> lines = this.config.scoreboardLines();
      int score = lines.size();

      for (String rawLine : lines) {
         String line = this.applyDualPlaceholders(rawLine, fighterOne, fighterTwo);
         String teamName = "line" + score;
         Team team = board.registerNewTeam(teamName);
         String entry = uniqueEntry(score);
         team.addEntry(entry);
         team.prefix(ColorUtil.component(line));
         objective.getScore(entry).setScore(score);
         score--;
      }
   }

   private void applyOpponentNametagTeam(Scoreboard board, Player opponent) {
      String teamName = teamNameFor(opponent.getUniqueId());
      Team team = board.getTeam(teamName);
      if (team == null) {
         team = board.registerNewTeam(teamName);
         team.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.ALWAYS);
         team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
      }

      String entry = opponent.getName();
      if (!team.hasEntry(entry)) {
         for (String existing : List.copyOf(team.getEntries())) {
            if (!existing.equals(entry)) {
               team.removeEntry(existing);
            }
         }

         team.addEntry(entry);
      }

      team.suffix(ColorUtil.component(this.formatHpSuffix(opponent)));
      team.prefix(Component.empty());
   }

   private String formatHpSuffix(Player player) {
      double hp = player.getHealth();
      Map<String, String> ph = new HashMap<>();
      ph.put("hp", this.formatHp(hp));
      ph.put("hp_int", String.valueOf((int)Math.ceil(hp)));
      ph.put("hearts", this.config.nametagHeartsSymbol());
      return this.applyRawPlaceholders(this.config.nametagFormat(), ph);
   }

   private String applyDualPlaceholders(String raw, Player fighterOne, Player fighterTwo) {
      Map<String, String> ph = new HashMap<>();
      ph.put("player1", fighterOne.getName());
      ph.put("player2", fighterTwo.getName());
      ph.put("player1_hp", this.formatHp(fighterOne.getHealth()));
      ph.put("player2_hp", this.formatHp(fighterTwo.getHealth()));
      ph.put("player1_ping", String.valueOf(fighterOne.getPing()));
      ph.put("player2_ping", String.valueOf(fighterTwo.getPing()));
      ph.put("opponent", fighterTwo.getName());
      ph.put("player", fighterOne.getName());
      ph.put("opponent_hp", this.formatHp(fighterTwo.getHealth()));
      ph.put("your_ping", String.valueOf(fighterOne.getPing()));
      ph.put("opponent_ping", String.valueOf(fighterTwo.getPing()));
      ph.put("hearts", this.config.nametagHeartsSymbol());
      return this.applyRawPlaceholders(raw, ph);
   }

   private String applyRawPlaceholders(String raw, Map<String, String> placeholders) {
      String result = raw == null ? "" : raw;

      for (Entry<String, String> entry : placeholders.entrySet()) {
         result = result.replace("{" + entry.getKey() + "}", entry.getValue());
      }

      return result;
   }

   private String formatHp(double hp) {
      int decimals = this.config.nametagHpDecimals();
      return decimals <= 0 ? String.valueOf((int)Math.ceil(hp)) : String.format(Locale.US, "%." + decimals + "f", hp);
   }

   private static String teamNameFor(UUID playerId) {
      String compact = playerId.toString().replace("-", "");
      return ("md" + compact).substring(0, 16);
   }

   private static String uniqueEntry(int index) {
      ChatColor color = ChatColor.values()[index % ChatColor.values().length];
      return color.toString() + ChatColor.RESET;
   }

   private void updateDuelTabVisibility(Player viewer, Player opponent, Set<UUID> hiddenPlayers) {
      for (Player other : Bukkit.getOnlinePlayers()) {
         if (!other.getUniqueId().equals(viewer.getUniqueId()) && !other.getUniqueId().equals(opponent.getUniqueId()) && viewer.canSee(other)) {
            viewer.hidePlayer(this.plugin, other);
            hiddenPlayers.add(other.getUniqueId());
         }
      }
   }

   private void restoreTabVisibility(Player viewer, Set<UUID> hiddenPlayers) {
      for (UUID hiddenId : new HashSet<>(hiddenPlayers)) {
         Player hidden = Bukkit.getPlayer(hiddenId);
         if (hidden != null) {
            viewer.showPlayer(this.plugin, hidden);
         }
      }

      hiddenPlayers.clear();
   }

   private void showTitle(Player player, String titleRaw, String subtitleRaw, Map<String, String> placeholders) {
      String title = this.applyRawPlaceholders(titleRaw, placeholders);
      String subtitle = this.applyRawPlaceholders(subtitleRaw, placeholders);
      Times times = Times.times(
         Duration.ofMillis((long)this.config.countdownTitleFadeInTicks() * 50L),
         Duration.ofMillis((long)this.config.countdownTitleStayTicks() * 50L),
         Duration.ofMillis((long)this.config.countdownTitleFadeOutTicks() * 50L)
      );
      player.showTitle(Title.title(ColorUtil.component(title), ColorUtil.component(subtitle), times));
   }

   public void restoreSpectatorScoreboard(Player spectator) {
      for (DuelDisplayManager.SessionDisplayState state : this.statesBySession.values()) {
         Scoreboard original = state.originalScoreboards.remove(spectator.getUniqueId());
         if (original != null) {
            restorePlayer(spectator, original);
            return;
         }
      }

      restorePlayer(spectator, null);
   }

   private static void restorePlayer(Player player, Scoreboard scoreboard) {
      player.customName(null);
      player.setCustomNameVisible(false);
      if (scoreboard != null) {
         player.setScoreboard(scoreboard);
      } else {
         player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
      }
   }

   private static final class SessionDisplayState {
      private Scoreboard originalScoreboardP1;
      private Scoreboard originalScoreboardP2;
      private final Map<UUID, Scoreboard> originalScoreboards = new ConcurrentHashMap<>();
      private final Set<UUID> hiddenFromP1 = ConcurrentHashMap.newKeySet();
      private final Set<UUID> hiddenFromP2 = ConcurrentHashMap.newKeySet();
      private BukkitTask updateTask;
   }
}
