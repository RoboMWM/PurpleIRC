/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.cnaude.purpleirc.IRCListeners;

import com.cnaude.purpleirc.IRCCommand;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.cnaude.purpleirc.PurpleBot;
import com.cnaude.purpleirc.IRCCommandSender;
import com.cnaude.purpleirc.PurpleIRC;
import com.google.common.base.Joiner;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;

/**
 *
 * @author cnaude
 */
public class MessageListener extends ListenerAdapter {

    PurpleIRC plugin;
    PurpleBot ircBot;

    public MessageListener(PurpleIRC plugin, PurpleBot ircBot) {
        this.plugin = plugin;
        this.ircBot = ircBot;
    }

    @Override
    public void onMessage(MessageEvent event) {
        
        String message = event.getMessage();
        Channel channel = event.getChannel();
        User user = event.getUser();
        PircBotX bot = event.getBot();
        
        plugin.logDebug("Message caught <"+user.getNick()+">: " + message);

        if (!ircBot.botChannels.contains(channel.getName())) {
            plugin.logDebug("Invalid IRC channel ("+channel.getName()+"). Ignoring message from "+user.getNick()+": " + message);
            return;
        }
        String myChannel = channel.getName();
        if (ircBot.muteList.get(myChannel).contains(user.getNick())) {
            plugin.logDebug("User is muted. Ignoring message from "+user.getNick()+": " + message);
            return;
        }
        if (message.startsWith(ircBot.commandPrefix) && (!message.startsWith(ircBot.commandPrefix + ircBot.commandPrefix))) {
            String command = message.split(" ")[0].substring(1);
            String commandArgs = null;
            if (message.contains(" ")) {
                commandArgs = message.split(" ",2)[1];
            }
            plugin.logDebug("IRC command detected: " + command);
            if (ircBot.commandMap.get(myChannel).containsKey(command)) {
                String gameCommand = (String) ircBot.commandMap.get(myChannel).get(command).get("game_command");
                String modes = (String) ircBot.commandMap.get(myChannel).get(command).get("modes");                
                boolean privateCommand = Boolean.parseBoolean(ircBot.commandMap.get(myChannel).get(command).get("private"));
                plugin.logDebug(gameCommand + ":" + modes + ":" + privateCommand);
                String target = channel.getName();
                if (privateCommand) {
                    target = user.getNick();
                }
                boolean modeOkay = false;
                if (modes.equals("*")) {
                    modeOkay = true;
                } else if (modes.contains("o")) {
                    modeOkay = user.getChannelsOpIn().contains(channel);
                } else if (modes.contains("v")) {
                    modeOkay = user.getChannelsVoiceIn().contains(channel);
                }

                if (modeOkay) {
                    if (gameCommand.equals("@list")) {
                        bot.sendMessage(target, plugin.getMCPlayers());                        
                    } else if (gameCommand.equals("@uptime")) {
                        bot.sendMessage(target, plugin.getMCUptime());
                    } else if (gameCommand.equals("@help")) {
                        bot.sendMessage(target, getCommands(ircBot.commandMap,myChannel));
                    } else if (gameCommand.equals("@chat")) {
                        ircBot.broadcastChat(user.getNick(), myChannel, commandArgs);
                    } else {
                        if (commandArgs == null) {
                            commandArgs = "";
                        }
                        if (gameCommand.contains("%ARGS%")) {
                            gameCommand = gameCommand.replace("%ARGS%", commandArgs);
                        }   
                        if (gameCommand.contains("%NAME%")) {
                            gameCommand = gameCommand.replace("%NAME%", user.getNick());
                        } 
                        plugin.logDebug("GM: \"" + gameCommand.trim() + "\"");
                        ircBot.commandQueue.add(new IRCCommand(new IRCCommandSender(event.getBot(), target, plugin), gameCommand.trim()));
                        //plugin.getServer().dispatchCommand(new IRCCommandSender(event.getBot(), target, plugin), gameCommand);
                    }
                } else {
                    plugin.logDebug("User '" + user.getNick() + "' mode not okay.");
                }
            } else {
                bot.sendMessage(channel, "I'm sorry " + user.getNick() + " I can't do that. Type \""
                        + ircBot.commandPrefix + "help\" for a list of commands I might respond to.");
            }
        } else {
            if (ircBot.ignoreIRCChat.get(myChannel)) {
                plugin.logDebug("Message NOT dispatched for broadcast due to \"ignore-irc-chat\" being true ...");
            } else {
                plugin.logDebug("Message dispatched for broadcast...");
                ircBot.broadcastChat(user.getNick(), myChannel, message);
            }
            
        }
    }
    
    private String getCommands(Map<String, Map<String, Map<String, String>>> commandMap, String myChannel) {        
        if (commandMap.containsKey(myChannel)) {
            List<String> sortedCommands = new ArrayList<String>();
            for (String command : commandMap.get(myChannel).keySet()) {
                sortedCommands.add(command);
            }
            Collections.sort(sortedCommands, Collator.getInstance());
            
            return "Valid commands: " + Joiner.on(", ").join(sortedCommands);
        }
        return "";
    }
}
