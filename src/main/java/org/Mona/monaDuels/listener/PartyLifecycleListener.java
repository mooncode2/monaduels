package org.Mona.monaDuels.listener;

import org.Mona.monaDuels.party.PartyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PartyLifecycleListener implements Listener {
   private final PartyManager partyManager;

   public PartyLifecycleListener(PartyManager partyManager) {
      this.partyManager = partyManager;
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.partyManager.clearPlayer(event.getPlayer().getUniqueId());
   }
}
