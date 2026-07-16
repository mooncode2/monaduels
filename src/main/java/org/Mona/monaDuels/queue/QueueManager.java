package org.Mona.monaDuels.queue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.Mona.monaDuels.MonaDuels;
import org.Mona.monaDuels.arena.Arena;
import org.Mona.monaDuels.arena.ArenaManager;
import org.Mona.monaDuels.arena.MapPoolManager;
import org.Mona.monaDuels.config.ConfigManager;
import org.Mona.monaDuels.config.MessageService;
import org.Mona.monaDuels.duel.DuelManager;
import org.Mona.monaDuels.gui.KitLayoutEditorService;
import org.Mona.monaDuels.kit.Kit;
import org.Mona.monaDuels.kit.KitManager;
import org.Mona.monaDuels.lobby.LobbyLayoutService;
import org.Mona.monaDuels.player.PlayerDataManager;
import org.Mona.monaDuels.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class QueueManager {
   private final MonaDuels plugin;
   private final ConfigManager config;
   private final MessageService messages;
   private final DuelManager duelManager;
   private final KitManager kitManager;
   private final MapPoolManager mapPoolManager;
   private final ArenaManager arenaManager;
   private KitLayoutEditorService editorService;
   private final Map<String, Deque<UUID>> queuesByKit = new ConcurrentHashMap<>();
   private final Map<UUID, String> queuedKitByPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> lastOpponent = new ConcurrentHashMap<>();
   private final Map<String, Deque<UUID>> rankedQueuesByKit = new ConcurrentHashMap<>();
   private LobbyLayoutService lobbyLayout;
   private PlayerDataManager playerDataManager;

   public QueueManager(
      MonaDuels plugin,
      ConfigManager config,
      MessageService messages,
      DuelManager duelManager,
      KitManager kitManager,
      MapPoolManager mapPoolManager,
      ArenaManager arenaManager
   ) {
      this.plugin = plugin;
      this.config = config;
      this.messages = messages;
      this.duelManager = duelManager;
      this.kitManager = kitManager;
      this.mapPoolManager = mapPoolManager;
      this.arenaManager = arenaManager;
   }

   public void bindLobbyLayout(LobbyLayoutService lobbyLayout) {
      this.lobbyLayout = lobbyLayout;
   }

   public void bindPlayerDataManager(PlayerDataManager playerDataManager) {
      this.playerDataManager = playerDataManager;
   }

   public void bindEditor(KitLayoutEditorService editorService) {
      this.editorService = editorService;
   }

   public boolean isQueued(UUID id) {
      return this.queuedKitByPlayer.containsKey(id);
   }

   public void recordOpponents(UUID a, UUID b) {
      this.lastOpponent.put(a, b);
      this.lastOpponent.put(b, a);
   }

   public void enqueue(Player player, String kitName) {
      UUID id = player.getUniqueId();
      Kit kit = kitName == null ? null : this.kitManager.find(kitName).orElse(null);
      if (kit == null) {
         this.messages.send(player, "kit.not-found", Map.of("kit", kitName == null ? "?" : kitName));
         return;
      }

      if (this.duelManager.isInDuel(id)) {
         this.messages.send(player, "duel.already-in-duel");
         return;
      }

      if (this.editorService != null && this.editorService.isEditing(id)) {
         this.messages.send(player, "kit-editor.busy");
         return;
      }

      String kitKey = kit.name();
      this.dequeue(id);
      Deque<UUID> queue = this.queuesByKit.computeIfAbsent(kitKey, k -> new ArrayDeque<>());
      Player partner = this.pollCompatible(queue, id);
      if (partner != null) {
         this.startMatch(player, partner, kit, false);
      } else {
         queue.addLast(id);
         this.queuedKitByPlayer.put(id, kitKey);
         this.messages.send(player, "queue.joined", Map.of("kit", kit.displayName()));
         this.broadcastQueueActionBar(kit, queue);
      }
   }

   /** Action bar «⚔ Набор … | 👥 В очереди: N» — refreshed for everyone waiting on this kit. */
   private void broadcastQueueActionBar(Kit kit, Deque<UUID> queue) {
      if (!this.config.queueActionBarEnabled()) {
         return;
      }

      String text = this.config
         .queueActionBarFormat()
         .replace("{kit}", ColorUtil.strip(kit.displayName()))
         .replace("{count}", String.valueOf(queue.size()));

      for (UUID queuedId : queue) {
         Player queued = Bukkit.getPlayer(queuedId);
         if (queued != null && queued.isOnline()) {
            queued.sendActionBar(ColorUtil.component(text));
         }
      }
   }

   private Player pollCompatible(Deque<UUID> queue, UUID meId) {
      UUID myLast = this.lastOpponent.get(meId);
      Iterator<UUID> it = queue.iterator();

      while (it.hasNext()) {
         UUID candidateId = it.next();
         if (candidateId.equals(meId)) {
            it.remove();
            this.queuedKitByPlayer.remove(candidateId);
            continue;
         }

         Player candidate = Bukkit.getPlayer(candidateId);
         if (candidate == null || !candidate.isOnline() || this.duelManager.isInDuel(candidateId)) {
            it.remove();
            this.queuedKitByPlayer.remove(candidateId);
            continue;
         }

         if (candidateId.equals(myLast) || meId.equals(this.lastOpponent.get(candidateId))) {
            continue;
         }

         it.remove();
         this.queuedKitByPlayer.remove(candidateId);
         return candidate;
      }

      return null;
   }

   private void startMatch(Player a, Player b, Kit kit, boolean ranked) {
      Optional<Arena> arenaOpt = this.mapPoolManager.findFreeArenaForKit(kit.name(), this.arenaManager);
      if (arenaOpt.isEmpty()) {
         Map<String, String> poolMsg = Map.of("pool", kit.name(), "kit", kit.displayName());
         this.messages.send(a, "arena.none-free-pool", poolMsg);
         this.messages.send(b, "arena.none-free-pool", poolMsg);
         Map<String, Deque<UUID>> targetQueues = ranked ? this.rankedQueuesByKit : this.queuesByKit;
         Deque<UUID> queue = targetQueues.computeIfAbsent(kit.name(), k -> new ArrayDeque<>());
         queue.addFirst(b.getUniqueId());
         this.queuedKitByPlayer.put(b.getUniqueId(), kit.name());
         queue.addFirst(a.getUniqueId());
         this.queuedKitByPlayer.put(a.getUniqueId(), kit.name());
         return;
      }

      this.recordOpponents(a.getUniqueId(), b.getUniqueId());
      this.messages.send(a, "queue.match-found", Map.of("opponent", b.getName()));
      this.messages.send(b, "queue.match-found", Map.of("opponent", a.getName()));
      this.duelManager.startDirectDuel(a, b, kit, arenaOpt.get(), ranked);
   }

   public void dequeue(UUID id) {
      String kitKey = this.queuedKitByPlayer.remove(id);
      if (kitKey != null) {
         Deque<UUID> normalQueue = this.queuesByKit.get(kitKey);
         if (normalQueue != null) {
            normalQueue.remove(id);
            // Удаляем пустые очереди
            if (normalQueue.isEmpty()) {
               this.queuesByKit.remove(kitKey);
            }
         }
         Deque<UUID> rankedQueue = this.rankedQueuesByKit.get(kitKey);
         if (rankedQueue != null) {
            rankedQueue.remove(id);
            // Удаляем пустые очереди
            if (rankedQueue.isEmpty()) {
               this.rankedQueuesByKit.remove(kitKey);
            }
         }
      }
   }

   public void clearFor(UUID id) {
      this.dequeue(id);
      this.lastOpponent.remove(id);
      this.lastOpponent.values().removeIf(other -> other.equals(id));
   }

   private void setQueueHotbar(Player player) {
      ItemStack leaveQueueItem = new ItemStack(Material.RED_DYE);
      ItemMeta meta = leaveQueueItem.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(ColorUtil.color("&cПокинуть подбор"));
         leaveQueueItem.setItemMeta(meta);
      }
      player.getInventory().setItem(4, leaveQueueItem);
   }

   public void leaveQueue(Player player) {
      UUID id = player.getUniqueId();
      String kitKey = this.queuedKitByPlayer.remove(id);
      if (kitKey != null) {
         // Сохраняем режим перед выходом
         String lastMode = this.playerDataManager.getLastGameMode(id);
         boolean ranked = "ranked".equalsIgnoreCase(lastMode);
         this.playerDataManager.setLastGameMode(id, ranked ? "ranked" : "normal");
         
         Deque<UUID> normalQueue = this.queuesByKit.get(kitKey);
         if (normalQueue != null) {
            normalQueue.remove(id);
         }
         Deque<UUID> rankedQueue = this.rankedQueuesByKit.get(kitKey);
         if (rankedQueue != null) {
            rankedQueue.remove(id);
         }
         this.messages.send(player, "queue.left");
      }
      this.restoreDefaultHotbar(player);
   }

   private void restoreDefaultHotbar(Player player) {
      player.getInventory().setItem(4, this.lobbyLayout.getDefaultHotbarItem(4));
   }

   public void enqueue(Player player, String kitName, boolean ranked) {
      UUID id = player.getUniqueId();
      Kit kit = kitName == null ? null : this.kitManager.find(kitName).orElse(null);
      if (kit == null) {
         this.messages.send(player, "kit.not-found", Map.of("kit", kitName == null ? "?" : kitName));
         return;
      }

      if (this.duelManager.isInDuel(id)) {
         this.messages.send(player, "duel.already-in-duel");
         return;
      }

      if (this.editorService != null && this.editorService.isEditing(id)) {
         this.messages.send(player, "kit-editor.busy");
         return;
      }

      String kitKey = kit.name();
      this.dequeue(id);
      
      Map<String, Deque<UUID>> targetQueues = ranked ? this.rankedQueuesByKit : this.queuesByKit;
      Deque<UUID> queue = targetQueues.computeIfAbsent(kitKey, k -> new ArrayDeque<>());
      Player partner = this.pollCompatible(queue, id);
      if (partner != null) {
         this.startMatch(player, partner, kit, ranked);
      } else {
         queue.addLast(id);
         this.queuedKitByPlayer.put(id, kitKey);
         this.messages.send(player, "queue.joined", Map.of("kit", kit.displayName()));
         this.broadcastQueueActionBar(kit, queue);
         this.setQueueHotbar(player);
      }
   }
}
