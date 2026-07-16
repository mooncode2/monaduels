package org.Mona.monaDuels.duel;

import java.util.UUID;

public final class MatchResult {
   private final UUID sessionId;
   private final String winnerName;
   private final String loserName;
   private final String kitName;
   private final String kitDisplay;
   private final String arenaName;
   private final String arenaDisplay;
   private final long durationMs;
   private final int winnerEloChange;
   private final int loserEloChange;
   private final int winnerElo;
   private final int loserElo;

   public MatchResult(
      UUID sessionId,
      String winnerName,
      String loserName,
      String kitName,
      String kitDisplay,
      String arenaName,
      long durationMs,
      int winnerEloChange,
      int loserEloChange,
      int winnerElo,
      int loserElo
   ) {
      this(sessionId, winnerName, loserName, kitName, kitDisplay, arenaName, arenaName, durationMs, winnerEloChange, loserEloChange, winnerElo, loserElo);
   }

   public MatchResult(
      UUID sessionId,
      String winnerName,
      String loserName,
      String kitName,
      String kitDisplay,
      String arenaName,
      String arenaDisplay,
      long durationMs,
      int winnerEloChange,
      int loserEloChange,
      int winnerElo,
      int loserElo
   ) {
      this.sessionId = sessionId;
      this.winnerName = winnerName;
      this.loserName = loserName;
      this.kitName = kitName;
      this.kitDisplay = kitDisplay;
      this.arenaName = arenaName;
      this.arenaDisplay = arenaDisplay;
      this.durationMs = durationMs;
      this.winnerEloChange = winnerEloChange;
      this.loserEloChange = loserEloChange;
      this.winnerElo = winnerElo;
      this.loserElo = loserElo;
   }

   public UUID sessionId() {
      return this.sessionId;
   }

   public String shortId() {
      return this.sessionId.toString().substring(0, 8);
   }

   public String winnerName() {
      return this.winnerName;
   }

   public String loserName() {
      return this.loserName;
   }

   public String kitName() {
      return this.kitName;
   }

   public String kitDisplay() {
      return this.kitDisplay;
   }

   public String arenaName() {
      return this.arenaName;
   }

   public String arenaDisplay() {
      return this.arenaDisplay != null && !this.arenaDisplay.isBlank() ? this.arenaDisplay : this.arenaName;
   }

   public long durationMs() {
      return this.durationMs;
   }

   public int winnerEloChange() {
      return this.winnerEloChange;
   }

   public int loserEloChange() {
      return this.loserEloChange;
   }

   public int winnerElo() {
      return this.winnerElo;
   }

   public int loserElo() {
      return this.loserElo;
   }

   public static String formatDuration(long ms) {
      long totalSec = Math.max(0L, ms / 1000L);
      long min = totalSec / 60L;
      long sec = totalSec % 60L;
      return String.format("%02d:%02d", min, sec);
   }
}
