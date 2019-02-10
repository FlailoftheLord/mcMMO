package com.gmail.nossr50.runnables.party;

import com.gmail.nossr50.core.config.skills.Config;
import com.gmail.nossr50.core.datatypes.party.Party;
import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.party.PartyManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

public class PartyAutoKickTask extends BukkitRunnable {
    private final static long KICK_TIME = 24L * 60L * 60L * 1000L * Config.getInstance().getAutoPartyKickTime();

    @Override
    public void run() {
        HashMap<OfflinePlayer, Party> toRemove = new HashMap<OfflinePlayer, Party>();
        List<UUID> processedPlayers = new ArrayList<UUID>();

        long currentTime = System.currentTimeMillis();

        for (Party party : PartyManager.getParties()) {
            for (UUID memberUniqueId : party.getMembers().keySet()) {
                OfflinePlayer member = mcMMO.p.getServer().getOfflinePlayer(memberUniqueId);
                boolean isProcessed = processedPlayers.contains(memberUniqueId);

                if ((!member.isOnline() && (currentTime - member.getLastPlayed() > KICK_TIME)) || isProcessed) {
                    toRemove.put(member, party);
                }

                if (!isProcessed) {
                    processedPlayers.add(memberUniqueId);
                }
            }
        }

        for (Entry<OfflinePlayer, Party> entry : toRemove.entrySet()) {
            PartyManager.removeFromParty(entry.getKey(), entry.getValue());
        }
    }
}