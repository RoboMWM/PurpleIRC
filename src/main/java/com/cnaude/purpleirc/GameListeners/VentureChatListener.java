/*
 * Copyright (C) 2014 cnaude
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.cnaude.purpleirc.GameListeners;

import com.cnaude.purpleirc.Events.VentureChatEvent;
import com.cnaude.purpleirc.PurpleBot;
import com.cnaude.purpleirc.PurpleIRC;
import com.cnaude.purpleirc.TemplateName;
import mineverse.Aust1n46.chat.api.MineverseChatAPI;
import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.channel.ChatChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 *
 * @author Chris Naude
 */
public class VentureChatListener implements Listener {

    final PurpleIRC plugin;

    /**
     *
     * @param plugin the PurpleIRC plugin
     */
    public VentureChatListener(PurpleIRC plugin) {
        this.plugin = plugin;
    }

    /**
     *
     * @param event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onVentureChatEvent(VentureChatEvent event) {
        ventureChat(event.getPlayer(), event.getMessage(), event.getBot());
    }

    /**
     * VentureChat from game to IRC
     *
     * @param player
     * @param message
     * @param bot the calling bot
     */
    public void ventureChat(Player player, String message, PurpleBot bot) {
        MineverseChatPlayer mcp = MineverseChatAPI.getMineverseChatPlayer(player);
        ChatChannel eventChannel = mcp.getCurrentChannel();
        if (mcp.isQuickChat()) { //for single message chat detection
            eventChannel = mcp.getQuickChannel();
        }
        if (!bot.isConnected()) {
            return;
        }
        if (bot.floodChecker.isSpam(player)) {
            bot.sendFloodWarning(player);
            return;
        }
        String vcChannel = eventChannel.getName();
        String vcColor = eventChannel.getColor();
        for (String channelName : bot.botChannels) {
            if (!bot.isPlayerInValidWorld(player, channelName)) {
                continue;
            }
            plugin.logDebug("VC Channel: " + vcChannel);
            String channelTemplateName = "venture-" + vcChannel + "-chat";
            if (bot.isMessageEnabled(channelName, channelTemplateName)
                    || bot.isMessageEnabled(channelName, TemplateName.VENTURE_CHAT)) {
                String template = plugin.getVentureChatTemplate(bot.botNick, vcChannel);
                plugin.logDebug("VC Template: " + template);
                bot.asyncIRCMessage(channelName, plugin.tokenizer
                        .ventureChatTokenizer(player, vcChannel, vcColor, message, template));
            } else {
                plugin.logDebug("Player " + player.getName() + " is in VentureChat channel "
                        + vcChannel + ". Message types " + channelTemplateName + " and "
                        + TemplateName.VENTURE_CHAT + " are disabled. No message sent to IRC.");
            }
        }
    }

}
