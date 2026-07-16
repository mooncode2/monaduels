package org.Mona.monaDuels.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.duel.DuelSession;
import org.Mona.monaDuels.duel.MatchResult;
import org.Mona.monaDuels.stats.StatsManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class DuelResultService {
   private final ConfigManager config;
   private final MessageService messages;
   private final StatsManager statsManager;
   private final Map<String, MatchResult> resultsByShortId = new LinkedHashMap<>();
   private static final int MAX_CACHED = 64;

   public DuelResultService(ConfigManager config, MessageService messages, StatsManager statsManager) {
      this.config = config;
      this.messages = messages;
      this.statsManager = statsManager;
   }

   public void handleEnd(DuelSession session, Player winner, Player loser, MatchResult result) {
      this.cacheResult(result);
      if (this.config.endTitleEnabled()) {
         this.showEndTitle(winner, true, loser.getName());
         this.showEndTitle(loser, false, winner.getName());
      }
   }

   public void publishResultsChat(MatchResult result) {
      if (result != null) {
         this.broadcastResultsMessage(result);
      }
   }

   public Optional<MatchResult> findResult(String shortId) {
      return shortId == null ? Optional.empty() : Optional.ofNullable(this.resultsByShortId.get(shortId.toLowerCase()));
   }

   public void openResultsGui(Player player, MatchResult result) {
      if (this.config.resultsGuiEnabled()) {
         if (player != null && player.isOnline()) {
            String title = ColorUtil.color(this.config.resultsGuiTitle());
            Inventory inv = Bukkit.createInventory(null, 54, title);
            ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", null);

            for (int i = 0; i < inv.getSize(); i++) {
               inv.setItem(i, filler);
            }

            inv.setItem(
               20,
               playerHead(
                  result.winnerName(),
                  "&a&lПобедитель",
                  List.of("&7Ник: &f" + result.winnerName(), "&aELO: &f" + result.winnerElo() + " &7(" + formatEloChange(result.winnerEloChange()) + ")")
               )
            );
            inv.setItem(
               24,
               playerHead(
                  result.loserName(),
                  "&c&lПроигравший",
                  List.of("&7Ник: &f" + result.loserName(), "&cELO: &f" + result.loserElo() + " &7(" + formatEloChange(result.loserEloChange()) + ")")
               )
            );
            inv.setItem(
               22,
               item(
                  Material.DIAMOND_SWORD,
                  "&e&lДуэль",
                  List.of(
                     "&7Кит: &f" + ColorUtil.color(result.kitDisplay()),
                     "&7Арена: &f" + ColorUtil.color(result.arenaDisplay()),
                     "&7Длительность: &f" + MatchResult.formatDuration(result.durationMs())
                  )
               )
            );
            inv.setItem(
               40,
               item(
                  Material.CLOCK,
                  "&f&lВремя боя",
                  List.of(
                     "&7Длительность: &f" + MatchResult.formatDuration(result.durationMs()),
                     "&7Всего в дуэлях: &f" + StatsManager.formatTotalTime(this.statsManager.getTotalTimeMs(player.getUniqueId())),
                     "&7Побед: &a" + this.statsManager.getWins(player.getUniqueId()),
                     "&7Поражений: &c" + this.statsManager.getLosses(player.getUniqueId())
                  )
               )
            );
            inv.setItem(49, item(Material.BARRIER, "&cЗакрыть", List.of("&7Нажмите, чтобы закрыть")));
            player.openInventory(inv);
         }
      }
   }

   private void showEndTitle(Player player, boolean won, String opponentName) {
      String titleRaw = won ? this.config.endTitleWin() : this.config.endTitleLose();
      String subtitleRaw = won ? this.config.endTitleWinSubtitle() : this.config.endTitleLoseSubtitle();
      Map<String, String> ph = Map.of("opponent", opponentName);
      Times times = Times.times(
         Duration.ofMillis((long)this.config.endTitleFadeInTicks() * 50L),
         Duration.ofMillis((long)this.config.endTitleStayTicks() * 50L),
         Duration.ofMillis((long)this.config.endTitleFadeOutTicks() * 50L)
      );
      player.showTitle(Title.title(ColorUtil.component(applyRaw(titleRaw, ph)), ColorUtil.component(applyRaw(subtitleRaw, ph)), times));
   }

   private void broadcastResultsMessage(MatchResult result) {
      if (this.config.resultsChatEnabled()) {
         Map<String, String> ph = Map.of(
            "winner",
            result.winnerName(),
            "loser",
            result.loserName(),
            "kit",
            result.kitDisplay(),
            "kit_id",
            result.kitName(),
            "arena",
            result.arenaDisplay(),
            "arena_id",
            result.arenaName()
         );
         Component headerBase = this.messages.componentOr("results.chat.header", ph, "&e&lРезультаты матча &7(Нажми что бы увидеть)");
         if (headerBase != null) {
            Component header = headerBase.clickEvent(ClickEvent.runCommand("/mduel results " + result.shortId()))
               .hoverEvent(HoverEvent.showText(ColorUtil.component("&aОткрыть результаты матча")));
            Component summary = this.messages.componentOr("results.chat.summary", ph, "&7Победил(а): &a{winner} &7| Проиграл(а) &c{loser}");

            for (Player online : Bukkit.getOnlinePlayers()) {
               online.sendMessage(header);
               if (summary != null) {
                  online.sendMessage(summary);
               }
            }
         }
      }
   }

   private void cacheResult(MatchResult result) {
      this.resultsByShortId.put(result.shortId(), result);

      while (this.resultsByShortId.size() > 64) {
         String first = this.resultsByShortId.keySet().iterator().next();
         this.resultsByShortId.remove(first);
      }
   }

   private static String formatEloChange(int change) {
      return change >= 0 ? "+" + change : String.valueOf(change);
   }

   private static String applyRaw(String raw, Map<String, String> placeholders) {
      String result = raw;

      for (Entry<String, String> entry : placeholders.entrySet()) {
         result = result.replace("{" + entry.getKey() + "}", entry.getValue());
      }

      return result;
   }

   private static ItemStack item(Material material, String name, List<String> lore) {
      ItemStack stack = new ItemStack(material);
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color(name));
         if (lore != null) {
            meta.setLore(lore.stream().map(ColorUtil::color).toList());
         }

         stack.setItemMeta(meta);
      }

      return stack;
   }

   private static ItemStack playerHead(String playerName, String name, List<String> lore) {
      ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta)stack.getItemMeta();
      if (meta != null) {
         meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
         meta.setDisplayName(ColorUtil.color(name));
         meta.setLore(lore.stream().map(ColorUtil::color).toList());
         stack.setItemMeta(meta);
      }

      return stack;
   }
}
