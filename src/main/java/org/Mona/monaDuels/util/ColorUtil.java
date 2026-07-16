package org.Mona.monaDuels.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

public final class ColorUtil {
   private ColorUtil() {
   }

   public static String color(String input) {
      return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
   }

   public static Component component(String input) {
      if (input != null && !input.isBlank()) {
         String normalized = ChatColor.translateAlternateColorCodes('&', input);
         return LegacyComponentSerializer.legacySection().deserialize(normalized);
      } else {
         return Component.empty();
      }
   }

   public static String strip(String input) {
      return input == null ? "" : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', input));
   }
}
