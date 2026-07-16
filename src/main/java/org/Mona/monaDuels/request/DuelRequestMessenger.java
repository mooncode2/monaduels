package org.Mona.monaDuels.request;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.entity.Player;

public final class DuelRequestMessenger {
   private static final String SENDER_FALLBACK = "&7От &f{challenger} &7({challenger_ping} &7мс)";
   private final ConfigManager config;
   private final MessageService messages;
   private final KitManager kitManager;

   public DuelRequestMessenger(ConfigManager config, MessageService messages, KitManager kitManager) {
      this.config = config;
      this.messages = messages;
      this.kitManager = kitManager;
   }

   public void sendRequest(Player challenger, Player target, String kitName) {
      this.sendRequest(challenger, target, kitName, false);
   }

   public void sendRequest(Player challenger, Player target, String kitName, boolean ranked) {
      String kit = this.kitDisplay(kitName);
      String ping = String.valueOf(challenger.getPing());
      Map<String, String> basePh = new HashMap<>();
      basePh.put("target", target.getName());
      basePh.put("challenger", challenger.getName());
      basePh.put("kit", kit);
      basePh.put("ping", ping);
      basePh.put("challenger_ping", ping);
      basePh.put(
         "match_type",
         ranked
            ? this.messages.format("request.match-type.rated", Map.of(), "&bРейтинговый")
            : this.messages.format("request.match-type.casual", Map.of(), "&7Обычный")
      );
      if (!this.config.requestButtonsEnabled()) {
         this.messages.send(challenger, "request.sent", basePh);
         this.messages.send(target, "request.received", basePh);
         this.messages.send(target, "request.received-hint");
      } else {
         this.messages.send(challenger, "request.sent", basePh);
         this.sendLine(target, "request.interactive.header", basePh, "&6&lЗапрос на Дуэль");
         this.sendSenderLine(target, basePh);
         this.sendLine(target, "request.interactive.kit", basePh, "&7Набор: &f{kit} &8({match_type}&8)");
         Component accept = this.clickableButton(
            "request.interactive.accept-button",
            basePh,
            "&a&l(ПРИНЯТЬ)",
            "/mduel accept",
            this.messages.format("request.interactive.accept-hover", Map.of(), "&aНажмите, чтобы принять"),
            "&aНажмите, чтобы принять"
         );
         Component deny = this.clickableButton(
            "request.interactive.deny-button",
            basePh,
            "&c&l(ОТКЛОНИТЬ)",
            "/mduel deny",
            this.messages.format("request.interactive.deny-hover", Map.of(), "&cНажмите, чтобы отклонить"),
            "&cНажмите, чтобы отклонить"
         );
         Component separator = this.messages.componentOr("request.interactive.buttons-separator", basePh, "&7 или ");
         if (separator == null) {
            separator = ColorUtil.component("&7 или ");
         }

         target.sendMessage(accept.append(separator).append(deny));
      }
   }

   private void sendSenderLine(Player target, Map<String, String> placeholders) {
      String template = this.messages.format("request.interactive.sender", Map.of(), "&7От &f{challenger} &7({challenger_ping} &7мс)");
      if (!template.contains("{challenger_ping}") && !template.contains("{ping}")) {
         template = "&7От &f{challenger} &7({challenger_ping} &7мс)";
      }

      target.sendMessage(ColorUtil.component(this.messages.applyPlaceholders(template, placeholders)));
   }

   private void sendLine(Player player, String path, Map<String, String> placeholders, String fallback) {
      Component line = this.messages.componentOr(path, placeholders, fallback);
      if (line != null) {
         player.sendMessage(line);
      }
   }

   private Component clickableButton(String path, Map<String, String> placeholders, String fallback, String command, String hoverRaw, String hoverFallback) {
      Component base = this.messages.componentOr(path, placeholders, fallback);
      if (base == null) {
         base = ColorUtil.component(fallback);
      }

      String hover = hoverRaw != null && !hoverRaw.isBlank() ? hoverRaw : hoverFallback;
      return base.clickEvent(ClickEvent.runCommand(command)).hoverEvent(HoverEvent.showText(ColorUtil.component(hover)));
   }

   private String kitDisplay(String kitName) {
      return this.kitManager.find(kitName).map(kit -> kit.displayName()).orElse(kitName);
   }
}
