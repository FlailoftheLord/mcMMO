package com.gmail.nossr50.core.chat;

import com.gmail.nossr50.core.config.Config;
import com.gmail.nossr50.core.datatypes.party.Party;
import com.gmail.nossr50.core.runnables.party.PartyChatTask;
import com.gmail.nossr50.events.chat.McMMOPartyChatEvent;
import org.bukkit.plugin.Plugin;

public class PartyChatManager extends ChatManager {
    private Party party;

    protected PartyChatManager(Plugin plugin) {
        super(plugin, Config.getInstance().getPartyDisplayNames(), Config.getInstance().getPartyChatPrefix());
    }

    public void setParty(Party party) {
        this.party = party;
    }

    @Override
    public void handleChat(String senderName, String displayName, String message, boolean isAsync) {
        handleChat(new McMMOPartyChatEvent(plugin, senderName, displayName, party.getName(), message, isAsync));
    }

    @Override
    protected void sendMessage() {
        new PartyChatTask(plugin, party, senderName, displayName, message).runTask(plugin);
    }
}