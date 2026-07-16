package org.Mona.monaDuels.cooldown;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class CooldownManager {
   private final Map<String, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();

   public void set(Player player, String type, long durationMillis) {
      this.cooldowns.computeIfAbsent(type, k -> new ConcurrentHashMap<>()).put(player.getUniqueId(), System.currentTimeMillis() + durationMillis);
   }

   public boolean isOnCooldown(Player player, String type) {
      return this.remainingSeconds(player, type) > 0L;
   }

   public long remainingSeconds(Player player, String type) {
      Map<UUID, Long> map = this.cooldowns.get(type);
      if (map == null) {
         return 0L;
      } else {
         Long expires = map.get(player.getUniqueId());
         if (expires == null) {
            return 0L;
         } else {
            long diff = expires - System.currentTimeMillis();
            if (diff <= 0L) {
               map.remove(player.getUniqueId());
               return 0L;
            } else {
               return (diff + 999L) / 1000L;
            }
         }
      }
   }

   public void clear(Player player) {
      for (Map<UUID, Long> map : this.cooldowns.values()) {
         map.remove(player.getUniqueId());
      }
   }
}
