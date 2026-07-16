package org.Mona.monaDuels.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class MessageService {
   private static final String DUEL_RESULT_FALLBACK = "{prefix}&f{winner} &aпобедил &f{loser} &aв дуэли на ките &f{kit}&a!";
   private final ConfigManager configManager;

   public MessageService(ConfigManager configManager) {
      this.configManager = configManager;
   }

   public void send(CommandSender sender, String path) {
      this.send(sender, path, Map.of());
   }

   public void send(CommandSender sender, String path, Map<String, String> placeholders) {
      this.sendComponent(sender, this.component(path, placeholders));
   }

   public void sendComponent(CommandSender sender, Component component) {
      if (component != null && !Component.empty().equals(component)) {
         sender.sendMessage(component);
      }
   }

   public Component component(String path, Map<String, String> placeholders) {
      return this.resolveComponent(path, placeholders);
   }

   public Component componentOr(String path, Map<String, String> placeholders, String fallbackRaw) {
      String raw = this.resolve(path, placeholders);
      if (raw.isEmpty() && fallbackRaw != null && !fallbackRaw.isEmpty()) {
         raw = this.applyPlaceholders(fallbackRaw, placeholders);
      }

      return raw.isEmpty() ? null : ColorUtil.component(raw);
   }

   public void send(Player player, String path, String key, String value) {
      Map<String, String> map = new HashMap<>();
      map.put(key, value);
      this.send(player, path, map);
   }

   public void broadcast(String path, Map<String, String> placeholders) {
      Component component = this.resolveComponent(path, placeholders);
      if (component != null) {
         Bukkit.getServer().broadcast(component);
      }
   }

   public void broadcastDuelResult(Map<String, String> placeholders) {
      Component component = this.resolveComponent("duel.result", placeholders);
      if (component == null) {
         component = this.resolveComponentFromRaw("{prefix}&f{winner} &aпобедил &f{loser} &aв дуэли на ките &f{kit}&a!", placeholders);
      }

      if (component != null) {
         Bukkit.getServer().broadcast(component);
      }
   }

   public String resolve(String path, Map<String, String> placeholders) {
      FileConfiguration messages = this.configManager.messages();
      String raw = messages.getString(path, "");
      if (raw.isEmpty() && "duel.result".equals(path)) {
         raw = "{prefix}&f{winner} &aпобедил &f{loser} &aв дуэли на ките &f{kit}&a!";
      }

      return raw.isEmpty() ? "" : this.applyPlaceholders(raw, placeholders);
   }

   public String format(String path, Map<String, String> placeholders, String fallbackTemplate) {
      String template = this.configManager.messages().getString(path, fallbackTemplate);
      if (template == null || template.isBlank()) {
         template = fallbackTemplate;
      }

      return this.applyPlaceholders(template, placeholders);
   }

   public String applyPlaceholders(String raw, Map<String, String> placeholders) {
      String prefix = this.configManager.messages().getString("prefix", "");
      Map<String, String> all = new HashMap<>(placeholders);
      all.putIfAbsent("prefix", prefix);
      String result = raw;

      for (Entry<String, String> entry : all.entrySet()) {
         result = result.replace("{" + entry.getKey() + "}", (CharSequence)(entry.getValue() == null ? "" : entry.getValue()));
      }

      return result;
   }

   private Component resolveComponent(String path, Map<String, String> placeholders) {
      String raw = this.resolve(path, placeholders);
      return raw.isEmpty() ? null : ColorUtil.component(raw);
   }

   private Component resolveComponentFromRaw(String raw, Map<String, String> placeholders) {
      String resolved = this.applyPlaceholders(raw, placeholders);
      return resolved.isEmpty() ? null : ColorUtil.component(resolved);
   }
}
