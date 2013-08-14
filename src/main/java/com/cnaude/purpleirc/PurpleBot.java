package com.cnaude.purpleirc;

import com.dthielke.herochat.Chatter;
import com.gmail.nossr50.api.ChatAPI;
import com.gmail.nossr50.api.PartyAPI;
import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.cnaude.purpleirc.IRCListeners.ActionListener;
import com.cnaude.purpleirc.IRCListeners.ConnectListener;
import com.cnaude.purpleirc.IRCListeners.DisconnectListener;
import com.cnaude.purpleirc.IRCListeners.JoinListener;
import com.cnaude.purpleirc.IRCListeners.KickListener;
import com.cnaude.purpleirc.IRCListeners.MessageListener;
import com.cnaude.purpleirc.IRCListeners.MotdListener;
import com.cnaude.purpleirc.IRCListeners.NickChangeListener;
import com.cnaude.purpleirc.IRCListeners.PartListener;
import com.cnaude.purpleirc.IRCListeners.QuitListener;
import com.cnaude.purpleirc.IRCListeners.ServerResponseListener;
import com.cnaude.purpleirc.IRCListeners.TopicListener;
import com.cnaude.purpleirc.IRCListeners.VersionListener;
import com.cnaude.purpleirc.IRCListeners.WhoisListener;
import com.cnaude.purpleirc.Utilities.ChatTokenizer;
import com.dthielke.herochat.Herochat;
import com.massivecraft.factions.entity.Faction;
import com.massivecraft.factions.entity.UPlayer;
import com.nyancraft.reportrts.data.HelpRequest;
import com.titankingdoms.dev.titanchat.core.participant.Participant;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.UtilSSLSocketFactory;

/**
 *
 * @author Chris Naude
 */
public final class PurpleBot {

    public final PircBotX bot;
    public final PurpleIRC plugin;
    private File file;
    private YamlConfiguration config;
    public boolean autoConnect;
    public String botServer;
    public String botNick;
    public String botLogin;
    public String botServerPass;
    public int botServerPort;
    public String commandPrefix;
    public String quitMessage;
    public boolean showMOTD;
    public String botIdentPassword;
    public long chatDelay;
    public boolean ssl;
    public boolean trustAllCerts;
    public boolean sendRawMessageOnConnect;
    public String rawMessage;
    public ArrayList<String> botChannels = new ArrayList<String>();
    public HashMap<String, Collection<String>> channelNicks = new HashMap<String, Collection<String>>();
    public HashMap<String, Collection<String>> tabIgnoreNicks = new HashMap<String, Collection<String>>();
    public HashMap<String, String> channelPassword = new HashMap<String, String>();
    public HashMap<String, String> channelTopic = new HashMap<String, String>();
    public HashMap<String, String> activeTopic = new HashMap<String, String>();
    public HashMap<String, String> channelModes = new HashMap<String, String>();
    public HashMap<String, Boolean> channelTopicProtected = new HashMap<String, Boolean>();
    public HashMap<String, Boolean> channelAutoJoin = new HashMap<String, Boolean>();
    public HashMap<String, Boolean> ignoreIRCChat = new HashMap<String, Boolean>();
    public HashMap<String, String> heroChannel = new HashMap<String, String>();
    public Map<String, Collection<String>> opsList = new HashMap<String, Collection<String>>();
    public Map<String, Collection<String>> worldList = new HashMap<String, Collection<String>>();
    public Map<String, Collection<String>> muteList = new HashMap<String, Collection<String>>();
    public Map<String, Collection<String>> enabledMessages = new HashMap<String, Collection<String>>();
    //          channel     command option     value
    public Map<String, Map<String, Map<String, String>>> commandMap = new HashMap<String, Map<String, Map<String, String>>>();
    public ArrayList<CommandSender> whoisSenders;
    private ChatTokenizer tokenizer;
    public CommandQueueWatcher commandQueue;

    public PurpleBot(File file, PurpleIRC plugin) {
        bot = new PircBotX();
        bot.getListenerManager().addListener(new ActionListener(plugin, this));
        bot.getListenerManager().addListener(new ConnectListener(plugin, this));
        bot.getListenerManager().addListener(new DisconnectListener(plugin, this));
        bot.getListenerManager().addListener(new JoinListener(plugin, this));
        bot.getListenerManager().addListener(new KickListener(plugin, this));
        bot.getListenerManager().addListener(new MessageListener(plugin, this));
        bot.getListenerManager().addListener(new NickChangeListener(plugin, this));
        bot.getListenerManager().addListener(new PartListener(plugin, this));
        bot.getListenerManager().addListener(new QuitListener(plugin, this));
        bot.getListenerManager().addListener(new TopicListener(plugin, this));
        bot.getListenerManager().addListener(new VersionListener(plugin));
        bot.getListenerManager().addListener(new WhoisListener(plugin, this));
        bot.getListenerManager().addListener(new MotdListener(plugin, this));
        bot.getListenerManager().addListener(new ServerResponseListener(plugin, this));
        this.plugin = plugin;
        this.file = file;
        commandQueue = new CommandQueueWatcher(this.plugin);
        whoisSenders = new ArrayList<CommandSender>();
        config = new YamlConfiguration();
        loadConfig();
        asyncConnect(false);
    }

    public void reload(CommandSender sender) {
        config = new YamlConfiguration();
        loadConfig();
        asyncReConnect();
    }

    public void reloadConfig(CommandSender sender) {
        config = new YamlConfiguration();
        loadConfig();
        sender.sendMessage("[PurpleIRC] [" + botNick + "] IRC bot configuration reloaded.");
    }

    public void asyncReConnect() {
        // We want to void blocking the main Bukkit thread        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.logInfo("Disconnecting from " + botServer);
                    bot.disconnect();
                } catch (Exception ex) {
                    plugin.logError("Problem disconnecting from " + botServer + " => "
                            + " [Error: " + ex.getMessage() + "]");
                }
            }
        });
        // We don't want to reconnect too fast. IRC servers don't like that.
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                connect();
            }
        }, 100L);
    }

    public void mute(String channelName, CommandSender sender, String user) {
        if (muteList.get(channelName).contains(user)) {
            sender.sendMessage("User '" + user + "' is already muted.");
        } else {
            sender.sendMessage("User '" + user + "' is now muted.");
            muteList.get(channelName).add(user);
            saveConfig();
        }

    }

    public void unMute(String channelName, CommandSender sender, String user) {
        if (muteList.get(channelName).contains(user)) {
            sender.sendMessage("User '" + user + "' is no longer muted.");
            muteList.get(channelName).remove(user);
            saveConfig();
        } else {
            sender.sendMessage("User '" + user + "' is not muted.");
        }
    }

    public void asyncConnect(boolean fromCommand) {
        // We want to void blocking the main Bukkit thread
        if (autoConnect || fromCommand) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    connect();
                }
            });
        } else {
            this.plugin.logInfo("Autoconnect is disabled. Not connecting to " + botServer + " as " + bot.getName());
        }
    }

    public void asyncConnect(CommandSender sender, boolean fromCommand) {
        // We want to void blocking the main Bukkit thread
        if (autoConnect || fromCommand) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
                @Override
                public void run() {
                    connect();
                }
            });
        } else {
            this.plugin.logInfo("Autoconnect is disabled. Not connecting to " + botServer + " as " + bot.getName());
        }
    }

    private void connect() {
        try {
            plugin.logInfo("Connecting to \"" + botServer + ":" + botServerPort + "\" as \"" + bot.getName()
                    + "\" [SSL: " + ssl + "]" + " [TrustAllCerts: " + trustAllCerts + "]");
            if (ssl) {
                UtilSSLSocketFactory socketFactory = new UtilSSLSocketFactory();
                socketFactory.disableDiffieHellman();
                if (trustAllCerts) {
                    socketFactory.trustAllCertificates();
                }
                bot.connect(botServer, botServerPort, botServerPass, socketFactory);
            } else {
                plugin.logInfo("NO SSL");
                bot.connect(botServer, botServerPort, botServerPass);
            }
        } catch (Exception ex) {
            plugin.logError("Problem connecting to " + botServer + " => "
                    + " as " + bot.getName() + " [Error: " + ex.getMessage() + "]");
        }
    }

    public void saveConfig(CommandSender sender) {
        try {
            config.save(file);
            sender.sendMessage("Saving bot \"" + botNick + "\" to " + file.getName());
        } catch (Exception ex) {
            plugin.logError(ex.getMessage());
            sender.sendMessage(ex.getMessage());
        }
    }

    public void saveConfig() {
        try {
            config.save(file);
        } catch (Exception ex) {
            plugin.logError(ex.getMessage());
        }
    }

    public void changeNick(CommandSender sender, String nick) {
        bot.setName(nick);
        bot.changeNick(nick);
        sender.sendMessage("Setting nickname to " + nick);
        config.set("nick", nick);
        saveConfig();
    }

    public void changeLogin(CommandSender sender, String name) {
        bot.setLogin(name);
        sender.sendMessage("Setting login to " + name);
        config.set("nick", name);
        saveConfig();
    }

    private void sanitizeServerName() {
        botServer = botServer.replace("^.*\\/\\/", "");
        botServer = botServer.replace(":\\d+$", "");
        config.set("server", botServer);
        saveConfig();
    }

    private void loadConfig() {
        try {
            tokenizer = new ChatTokenizer(this.plugin, this);
            config.load(file);
            autoConnect = config.getBoolean("autoconnect", true);
            ssl = config.getBoolean("ssl", false);
            trustAllCerts = config.getBoolean("trust-all-certs", false);
            sendRawMessageOnConnect = config.getBoolean("raw-message-on-connect", false);
            rawMessage = config.getString("raw-message", "");
            botNick = config.getString("nick", "");
            botLogin = config.getString("login", "PircBot");
            bot.setName(botNick);
            bot.setLogin(botLogin);
            plugin.ircBots.put(botNick, this);
            plugin.botConnected.put(botNick, bot.isConnected());
            botServer = config.getString("server", "");
            sanitizeServerName();
            showMOTD = config.getBoolean("show-motd", false);
            botServerPort = config.getInt("port");
            botServerPass = config.getString("password", "");
            botIdentPassword = config.getString("ident-password", "");
            commandPrefix = config.getString("command-prefix", ".");
            chatDelay = config.getLong("message-delay", 1000);
            bot.setMessageDelay(chatDelay);
            plugin.logDebug("Message Delay => " + chatDelay);
            quitMessage = ChatColor.translateAlternateColorCodes('&', config.getString("quit-message", ""));
            plugin.logDebug("Nick => " + botNick);
            plugin.logDebug("Login => " + botLogin);
            plugin.logDebug("Server => " + botServer);
            plugin.logDebug("SSL => " + ssl);
            plugin.logDebug("Trust All Certs => " + trustAllCerts);
            plugin.logDebug("Port => " + botServerPort);
            plugin.logDebug("Command Prefix => " + commandPrefix);
            //plugin.logDebug("Server Password => " + botServerPass);
            plugin.logDebug("Quit Message => " + quitMessage);
            botChannels.clear();
            opsList.clear();
            muteList.clear();
            enabledMessages.clear();
            worldList.clear();
            commandMap.clear();
            for (String enChannelName : config.getConfigurationSection("channels").getKeys(false)) {
                String channelName = decodeChannel(enChannelName);
                plugin.logDebug("Channel  => " + channelName);
                botChannels.add(channelName);

                channelAutoJoin.put(channelName, config.getBoolean("channels." + enChannelName + ".autojoin", true));
                plugin.logDebug("  Autojoin => " + channelAutoJoin.get(channelName));

                channelPassword.put(channelName, config.getString("channels." + enChannelName + ".password", ""));

                channelTopic.put(channelName, config.getString("channels." + enChannelName + ".topic", ""));
                plugin.logDebug("  Topic => " + channelTopic.get(channelName));

                channelModes.put(channelName, config.getString("channels." + enChannelName + ".modes", ""));
                plugin.logDebug("  Channel Modes => " + channelModes.get(channelName));

                channelTopicProtected.put(channelName, config.getBoolean("channels." + enChannelName + ".topic-protect", false));
                plugin.logDebug("  Topic Protected => " + channelTopicProtected.get(channelName).toString());

                heroChannel.put(channelName, config.getString("channels." + enChannelName + ".hero-channel", ""));
                plugin.logDebug("  Topic => " + heroChannel.get(channelName));

                ignoreIRCChat.put(channelName, config.getBoolean("channels." + enChannelName + ".ignore-irc-chat", false));
                plugin.logDebug("  IgnoreIRCChat => " + ignoreIRCChat.get(channelName));

                // build channel op list
                Collection<String> cOps = new ArrayList<String>();
                for (String channelOper : config.getStringList("channels." + enChannelName + ".ops")) {
                    if (!cOps.contains(channelOper)) {
                        cOps.add(channelOper);
                    }
                    plugin.logDebug("  Channel Op => " + channelOper);
                }
                opsList.put(channelName, cOps);
                if (opsList.isEmpty()) {
                    plugin.logInfo("No channel ops defined.");
                }

                // build mute list
                Collection<String> m = new ArrayList<String>();
                for (String mutedUser : config.getStringList("channels." + enChannelName + ".muted")) {
                    if (!m.contains(mutedUser)) {
                        m.add(mutedUser);
                    }
                    plugin.logDebug("  Channel Mute => " + mutedUser);
                }
                muteList.put(channelName, m);
                if (muteList.isEmpty()) {
                    plugin.logInfo("IRC mute list is empty.");
                }

                // build valid chat list
                Collection<String> c = new ArrayList<String>();
                for (String validChat : config.getStringList("channels." + enChannelName + ".enabled-messages")) {
                    if (!c.contains(validChat)) {
                        c.add(validChat);
                    }
                    plugin.logDebug("  Enabled Message => " + validChat);
                }
                enabledMessages.put(channelName, c);
                if (enabledMessages.isEmpty()) {
                    plugin.logInfo("There are no enabled messages!");
                }

                // build valid world list
                Collection<String> w = new ArrayList<String>();
                for (String validWorld : config.getStringList("channels." + enChannelName + ".worlds")) {
                    if (!w.contains(validWorld)) {
                        w.add(validWorld);
                    }
                    plugin.logDebug("  Enabled World => " + validWorld);
                }
                worldList.put(channelName, w);
                if (worldList.isEmpty()) {
                    plugin.logInfo("World list is empty!");
                }
                
                // build valid world list
                Collection<String> t = new ArrayList<String>();
                for (String name : config.getStringList("channels." + enChannelName + ".custom-tab-ignore-list")) {
                    if (!t.contains(name)) {
                        t.add(name);
                    }
                    plugin.logDebug("  Tab Ignore => " + name);
                }
                tabIgnoreNicks.put(channelName, t);
                if (tabIgnoreNicks.isEmpty()) {
                    plugin.logInfo("World list is empty!");
                }                

                // build command map
                Map<String, Map<String, String>> map = new HashMap<String, Map<String, String>>();
                for (String command : config.getConfigurationSection("channels." + enChannelName + ".commands").getKeys(false)) {
                    plugin.logDebug("  Command => " + command);
                    Map<String, String> optionPair = new HashMap<String, String>();
                    for (String commandOption : config.getConfigurationSection("channels." + enChannelName + ".commands." + command).getKeys(false)) {
                        String commandOptionValue = config.getString("channels." + enChannelName + ".commands." + command + "." + commandOption);
                        optionPair.put(commandOption, commandOptionValue);
                        plugin.logDebug("    " + commandOption + " => " + commandOptionValue);
                    }
                    map.put(command, optionPair);
                }
                commandMap.put(channelName, map);
                if (map.isEmpty()) {
                    plugin.logInfo("No commands specified!");
                }
            }
        } catch (Exception ex) {
            plugin.logError(ex.getMessage());
        }
    }

    public void setIRCDelay(CommandSender sender, long delay) {
        bot.setMessageDelay(delay);
        config.set("message-delay", delay);
        saveConfig();
        sender.sendMessage(ChatColor.WHITE + "IRC message delay changed to " + delay + " ms.");
    }

    private boolean isPlayerInValidWorld(Player player, String channelName) {
        if (worldList.containsKey(channelName)) {
            if (worldList.get(channelName).contains("*")) {
                return true;
            }
            if (worldList.get(channelName).contains(player.getWorld().getName())) {
                return true;
            }
        }
        return false;
    }

    // Called from normal game chat listener
    public void gameChat(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (!isPlayerInValidWorld(player, channelName)) {
                continue;
            }
            if (plugin.isMcMMOEnabled()) {
                plugin.logDebug("mcMMO is enabled");
                if (ChatAPI.isUsingAdminChat(player)) {
                    if (enabledMessages.get(channelName).contains("mcmmo-admin-chat")) {
                        asyncSendMessage(channelName, tokenizer.gameChatToIRCTokenizer(player, plugin.mcMMOAdminChat, message));
                    } else {
                        plugin.logDebug("Player " + player.getName() + " is in mcMMO AdminChat but mcmmo-admin-chat is disabled.");
                    }
                } else if (ChatAPI.isUsingPartyChat(player)) {
                    if (enabledMessages.get(channelName).contains("mcmmo-party-chat")) {
                        String partyName = PartyAPI.getPartyName(player);
                        asyncSendMessage(channelName, tokenizer.mcMMOChatToIRCTokenizer(player, plugin.mcMMOPartyChat, message, partyName));
                    } else {
                        plugin.logDebug("Player " + player.getName()
                                + " is in mcMMO PartyChat but \"mcmmo-party-chat\" is disabled.");
                    }
                }
            } else {
                plugin.logDebug("mcMMO is not enabled");
            }

            if (plugin.fcHook != null) {
                String playerChatMode;
                String playerFactionName; 
                try {
                    playerChatMode = plugin.fcHook.getChatMode(player);
                } catch (IllegalAccessError ex) {
                    plugin.logDebug("FC Error: " + ex.getMessage());
                    playerChatMode = "public";
                }
                try {
                    playerFactionName = plugin.fcHook.getFactionName(player);
                } catch (IllegalAccessError ex) {
                    plugin.logDebug("FC Error: " + ex.getMessage());
                    playerFactionName = "unknown";
                }
                
                String chatName = "faction-" + playerChatMode + "-chat";
                plugin.logDebug("Faction [Player: " + player.getName()
                        + "] [Tag: " + playerFactionName + "] [Mode: " + playerChatMode + "]");
                if (enabledMessages.get(channelName).contains(chatName)) {
                    asyncSendMessage(channelName, tokenizer.chatFactionTokenizer(player, message, playerFactionName, playerChatMode));                    
                } else {
                    plugin.logDebug("Player " + player.getName() + " is in chat mode \""
                            + playerChatMode + "\" but \"" + chatName + "\" is disabled.");
                }
            } else {
                plugin.logDebug("No Factions");
            }
            if (enabledMessages.get(channelName).contains("game-chat")) {
                plugin.logDebug("[game-chat] => " + channelName + " => " + message);
                asyncSendMessage(channelName, tokenizer.gameChatToIRCTokenizer(player, plugin.gameChat, message));
            } else {
                plugin.logDebug("Ignoring message due to game-chat not being listed.");
            }
        }
    }

    private void asyncSendMessage(final String channelName, final String message) {
        plugin.logDebug("Entering asyncSendMessage");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                plugin.logDebug("Sending message to " + channelName);
                bot.sendMessage(channelName, message);
            }
        });
    }

    // Called from HeroChat listener
    public void heroChat(Chatter chatter, ChatColor chatColor, String message) {
        Player player = chatter.getPlayer();
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (!isPlayerInValidWorld(player, channelName)) {
                continue;
            }
            String hChannel = chatter.getActiveChannel().getName();
            String hNick = chatter.getActiveChannel().getNick();
            String hColor = chatColor.toString();
            plugin.logDebug("HC Channel: " + hChannel);
            if (enabledMessages.get(channelName).contains("hero-" + hChannel + "-chat")
                    || enabledMessages.get(channelName).contains("hero-chat")) {
                asyncSendMessage(channelName, tokenizer.chatHeroTokenizer(player, message, hColor, hChannel, hNick));
            } else {
                plugin.logDebug("Player " + player.getName() + " is in \""
                        + hChannel + "\" but hero-" + hChannel + "-chat is disabled.");
            }
        }
    }

    // Called from TitanChat listener
    public void titanChat(Participant participant, String tChannel, String tColor, String message) {
        Player player = plugin.getServer().getPlayer(participant.getName());
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (!isPlayerInValidWorld(player, channelName)) {
                continue;
            }
            plugin.logDebug("TC Channel: " + tChannel);
            if (enabledMessages.get(channelName).contains("titan-" + tChannel + "-chat")
                    || enabledMessages.get(channelName).contains("titan-chat")) {
                //asyncSendMessage(channelName, tokenizer.titanChatTokenizer(player, tChannel, tColor, message));
                bot.sendMessage(channelName, tokenizer.titanChatTokenizer(player, tChannel, tColor, message));
            } else {
                plugin.logDebug("Player " + player.getName() + " is in \""
                        + tChannel + "\" but titan-" + tChannel + "-chat is disabled.");
            }
        }
    }

    // Called from /irc send
    public void gameChat(Player player, String channelName, String message) {
        if (!bot.isConnected()) {
            return;
        }
        if (botChannels.contains(channelName)) {
            bot.sendMessage(channelName, tokenizer.gameChatToIRCTokenizer(player, plugin.gameSend, message));
        }
    }

    // Called from CleverEvent
    public void cleverChat(String cleverBotName, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("clever-chat")) {
                bot.sendMessage(channelName, tokenizer.gameChatToIRCTokenizer(cleverBotName, plugin.cleverSend, message));
            }
        }
    }

    // Called from ReportRTS event    
    public void reportRTSNotify(Player player, HelpRequest request) {        
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("rts-notify")) {
                bot.sendMessage(channelName, tokenizer.reportRTSTokenizer(player, plugin.reportRTSSend, request));
            }
        }
    }

    public void consoleChat(String channelName, String message) {
        if (!bot.isConnected()) {
            return;
        }
        if (botChannels.contains(channelName)) {
            bot.sendMessage(channelName, tokenizer.gameChatToIRCTokenizer("CONSOLE", message, plugin.gameSend));
        }
    }

    public void consoleChat(String message) {        
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("console-chat")) {
                bot.sendMessage(channelName, tokenizer.gameChatToIRCTokenizer(plugin.consoleChat, ChatColor.translateAlternateColorCodes('&', message)));
            }
        }
    }

    public void gameBroadcast(Player player, String message) {        
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("broadcast-message")) {
                bot.sendMessage(channelName, tokenizer.gameChatToIRCTokenizer(player, plugin.broadcastMessage, ChatColor.translateAlternateColorCodes('&', message)));
            }
        }
    }

    public void consoleBroadcast(String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("broadcast-console-message")) {
                bot.sendMessage(channelName, tokenizer.gameChatToIRCTokenizer(plugin.broadcastConsoleMessage, ChatColor.translateAlternateColorCodes('&', message)));
            }
        }
    }

    public void gameJoin(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("game-join")) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    return;
                }
                bot.sendMessage(channelName, tokenizer.gameChatToIRCTokenizer(player, plugin.gameJoin, message));
                if (plugin.netPackets != null) {
                    plugin.netPackets.updateTabList(player, this, channelName);
                }
            }
        }
    }

    public void gameQuit(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("game-quit")) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    return;
                }
                bot.sendMessage(channelName, tokenizer.gameChatToIRCTokenizer(player, plugin.gameQuit, message));
            }
        }
    }

    public void gameAction(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("game-action")) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    return;
                }
                bot.sendMessage(channelName, tokenizer.gameChatToIRCTokenizer(player, plugin.gameAction, message));
            }
        }
    }

    public void gameDeath(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("game-death")) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    return;
                }
                bot.sendMessage(channelName, tokenizer.gameChatToIRCTokenizer(player, plugin.gameDeath, message));
            }
        }
    }

    public void changeTopic(String channelName, String topic, CommandSender sender) {
        changeTopic(bot.getChannel(channelName), topic, sender);
    }

    public void changeTopic(Channel channel, String topic, CommandSender sender) {
        String channelName = channel.getName();
        bot.setTopic(channel, topic);
        config.set("channels." + encodeChannel(channelName) + ".topic", topic);
        saveConfig();
        sender.sendMessage("IRC topic for " + channelName + " changed to \"" + topic + "\"");
    }

    public void setServer(CommandSender sender, String botServer) {
        setServer(sender, botServer, autoConnect);
    }

    public void setServer(CommandSender sender, String botServer, Boolean autoConnect) {
        this.botServer = botServer;
        config.set("server", botServer);
        this.autoConnect = autoConnect;
        config.set("autoconnect", autoConnect);
        sanitizeServerName();
        sender.sendMessage("IRC server changed to \"" + botServer + "\". (AutoConnect: " + autoConnect.toString() + ")");
    }

    public void addOp(String channelName, String userMask, CommandSender sender) {
        if (opsList.get(channelName).contains(userMask)) {
            sender.sendMessage("User mask'" + userMask + "' is already in the ops list.");
        } else {
            sender.sendMessage("User mask'" + userMask + "' has been added to the ops list.");
            opsList.get(channelName).add(userMask);
        }
        config.set("channels." + encodeChannel(channelName) + ".ops", opsList.get(channelName));
        saveConfig();
    }

    public void removeOp(String channelName, String userMask, CommandSender sender) {
        if (opsList.get(channelName).contains(userMask)) {
            sender.sendMessage("User mask'" + userMask + "' has been removed to the ops list.");
            opsList.get(channelName).remove(userMask);
        } else {
            sender.sendMessage("User mask'" + userMask + "' is not in the ops list.");
        }
        config.set("channels." + encodeChannel(channelName) + ".ops", opsList.get(channelName));
        saveConfig();
    }

    public void op(String channelName, String nick) {
        bot.op(bot.getChannel(channelName), bot.getUser(nick));
    }

    public void deOp(String channelName, String nick) {
        bot.deOp(bot.getChannel(channelName), bot.getUser(nick));
    }

    public void kick(String channelName, String nick) {
        bot.kick(bot.getChannel(channelName), bot.getUser(nick));
    }

    private String encodeChannel(String s) {
        return s.replace(".", "%2E");
    }

    private String decodeChannel(String s) {
        return s.replace("%2E", ".");
    }

    public void fixTopic(Channel channel, String topic, String setBy) {
        String channelName = channel.getName();
        if (setBy.equals(botNick)) {
            config.set("channels." + encodeChannel(channelName) + ".topic", topic);
            saveConfig();
            return;
        }

        if (channelTopic.containsKey(channelName)) {
            if (channelTopicProtected.containsKey(channelName)) {
                if (channelTopicProtected.get(channelName)) {
                    String myTopic = channelTopic.get(channelName);
                    if (!topic.equals(myTopic)) {
                        bot.setTopic(channel, myTopic);
                    }
                }
            }
        }
    }

    public void quit(CommandSender sender) {
        sender.sendMessage("Disconnecting " + bot.getNick() + " from IRC server " + botServer);
        asyncQuit();
    }

    public void quit() {
        if (quitMessage.isEmpty()) {
            bot.quitServer();
        } else {
            bot.quitServer(plugin.colorConverter.gameColorsToIrc(quitMessage));
        }
    }

    public void asyncQuit() {
        // We want to void blocking the main Bukkit thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                if (quitMessage.isEmpty()) {
                    bot.quitServer();
                } else {
                    bot.quitServer(plugin.colorConverter.gameColorsToIrc(quitMessage));
                }
            }
        });
    }

    public void sendTopic(CommandSender sender) {
        for (String channelName : botChannels) {
            if (commandMap.containsKey(channelName)) {
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.DARK_PURPLE
                        + botNick + ChatColor.WHITE + "]" + ChatColor.RESET
                        + " IRC topic for " + ChatColor.WHITE + channelName
                        + ChatColor.RESET + ": \""
                        + ChatColor.WHITE + activeTopic.get(channelName) + ChatColor.RESET + "\"");
            }
        }
    }

    public void sendUserWhois(CommandSender sender, String nick) {
        User user = bot.getUser(nick);
        if (user.getServer().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Invalid user: " + ChatColor.WHITE + nick);
        } else {
            bot.sendRawLineNow(String.format("WHOIS %s %s", nick, nick));
            whoisSenders.add(sender);
        }
    }

    public void sendUserList(CommandSender sender, String channel) {
        sendUserList(sender, bot.getChannel(channel));
    }

    public void sendUserList(CommandSender sender, Channel channel) {
        String channelName = channel.getName();
        if (!botChannels.contains(channelName)) {
            sender.sendMessage(ChatColor.RED + "Invalid channel: " + channelName);
            return;
        }
        sender.sendMessage(ChatColor.DARK_PURPLE + "-----[  " + ChatColor.WHITE + channelName
                + ChatColor.DARK_PURPLE + " - " + ChatColor.WHITE + bot.getName() + ChatColor.DARK_PURPLE + " ]-----");
        if (!bot.isConnected()) {
            sender.sendMessage(ChatColor.RED + " Not connected!");
            return;
        }
        List<String> channelUsers = new ArrayList<String>();
        for (User user : bot.getUsers(channel)) {
            String nick = user.getNick();
            if (user.isIrcop()) {
                nick = "~" + nick;
            } else if (user.getChannelsSuperOpIn().contains(channel)) {
                nick = "&" + nick;
            } else if (user.getChannelsOpIn().contains(channel)) {
                nick = "@" + nick;
            } else if (user.getChannelsHalfOpIn().contains(channel)) {
                nick = "%" + nick;
            } else if (user.getChannelsVoiceIn().contains(channel)) {
                nick = "+" + nick;
            }
            if (user.isAway()) {
                nick = nick + ChatColor.GRAY + " | Away";
            }
            if (nick.equals(bot.getNick())) {
                nick = ChatColor.DARK_PURPLE + nick;
            }
            channelUsers.add(nick);
        }
        Collections.sort(channelUsers, Collator.getInstance());
        for (String userName : channelUsers) {
            sender.sendMessage("  " + ChatColor.WHITE + userName);
        }
    }

    public void sendUserList(CommandSender sender) {
        for (String channelName : botChannels) {
            sendUserList(sender, bot.getChannel(channelName));
        }
    }
    
    public void updateNickList(Channel channel) {     
        // Build current list of names in channel
        ArrayList<String> users = new ArrayList<String>();
        for (User user : bot.getUsers(channel)) {
            //plugin.logDebug("N: " + user.getNick());
            users.add(user.getNick());            
        }
        // Iterate over previous list and remove from tab list
        if (channelNicks.containsKey(channel.getName())) {
            for (String name: channelNicks.get(channel.getName())) { 
                //plugin.logDebug("O: " + name);
                if (!users.contains(name)) {
                    plugin.logDebug("Removing " + name + " from list.");
                    if (plugin.netPackets != null) {
                        plugin.netPackets.remFromTabList(name);
                    }
                }
            }
            channelNicks.remove(channel.getName());
        }
        channelNicks.put(channel.getName(), users);
    }


    public void opFriends(Channel channel) {
        for (User user : bot.getUsers(channel)) {
            opFriends(channel, user);
        }
    }

    public void opFriends(String channelName) {
        Channel channel = bot.getChannel(channelName);
        for (User user : bot.getUsers(channel)) {
            opFriends(channel, user);
        }
    }

    public void opFriends(Channel channel, User user) {
        if (user.getNick().equals(botNick)) {
            return;
        }
        String channelName = channel.getName();
        for (String opsUser : opsList.get(channelName)) {
            plugin.logDebug("OP => " + user);
            //sender!*login@hostname            
            String mask[] = opsUser.split("[\\!\\@]", 3);
            if (mask.length == 3) {
                String gUser = plugin.regexGlobber.createRegexFromGlob(mask[0]);
                String gLogin = plugin.regexGlobber.createRegexFromGlob(mask[1]);
                String gHost = plugin.regexGlobber.createRegexFromGlob(mask[2]);
                String sender = user.getNick();
                String login = user.getLogin();
                String hostname = user.getHostmask();
                plugin.logDebug("Nick: " + sender + " =~ " + gUser + " = " + sender.matches(gUser));
                plugin.logDebug("Name: " + login + " =~ " + gLogin + " = " + login.matches(gLogin));
                plugin.logDebug("Hostname: " + hostname + " =~ " + gHost + " = " + hostname.matches(gHost));
                if (sender.matches(gUser) && login.matches(gLogin) && hostname.matches(gHost)) {
                    plugin.logInfo("Auto-opping " + sender + " on " + channelName);
                    user.getBot().op(channel, user);
                    // leave after our first match
                    return;
                } else {
                    plugin.logDebug("No match: " + sender + "!" + login + "@" + hostname + " != " + user);
                }
            } else {
                plugin.logInfo("Invalid op mask: " + opsUser);
            }
        }
    }

    // Broadcast chat messages from IRC
    public void broadcastChat(String nick, String myChannel, String message) {
        plugin.logDebug("Check if irc-chat is enabled before broadcasting chat from IRC");
        if (enabledMessages.get(myChannel).contains("irc-chat")) {
            plugin.logDebug("Yup we can broadcast due to irc-chat enabled");
            plugin.getServer().broadcast(tokenizer.ircChatToGameTokenizer(nick, myChannel, plugin.ircChat, message), "irc.message.chat");
        } else {
            plugin.logDebug("NOPE we can't broadcast due to irc-chat disabled");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-chat")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(tokenizer.ircChatToHeroChatTokenizer(nick, myChannel,
                    plugin.ircHeroChat, message, Herochat.getChannelManager(), heroChannel.get(myChannel)));
        }
    }

    // Broadcast action messages from IRC
    public void broadcastAction(String nick, String myChannel, String message) {
        if (enabledMessages.get(myChannel).contains("irc-action")) {
            plugin.getServer().broadcast(tokenizer.ircChatToGameTokenizer(nick, myChannel, plugin.ircAction, message), "irc.message.action");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-action")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(tokenizer.ircChatToHeroChatTokenizer(nick, myChannel,
                    plugin.ircHeroAction, message, Herochat.getChannelManager(), heroChannel.get(myChannel)));
        }
    }
    //ircBot.broadcastIRCKick(recipient.getNick(), kicker.getNick(), event.getReason(), channel.getName());
    // Broadcast kick messages from IRC

    public void broadcastIRCKick(String recipient, String kicker, String reason, String myChannel) {
        if (enabledMessages.get(myChannel).contains("irc-kick")) {
            plugin.getServer().broadcast(tokenizer.ircKickToHeroChatTokenizer(recipient, kicker, reason, myChannel, plugin.ircKick), "irc.message.kick");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-kick")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(tokenizer.chatIRCTokenizer(recipient, kicker,
                    reason, myChannel, plugin.ircHeroKick, Herochat.getChannelManager(), heroChannel.get(myChannel)));
        }
    }

    // Broadcast join messages from IRC
    public void broadcastIRCJoin(String nick, String myChannel) {
        if (enabledMessages.get(myChannel).contains("irc-join")) {
            plugin.getServer().broadcast(tokenizer.chatIRCTokenizer(nick, myChannel, plugin.ircJoin), "irc.message.join");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-join")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(tokenizer.ircChatToHeroChatTokenizer(nick, myChannel,
                    plugin.ircHeroJoin, Herochat.getChannelManager(), heroChannel.get(myChannel)));
        }
    }

    // Broadcast topic changes from IRC
    public void broadcastIRCTopic(String nick, String myChannel, String message) {
        if (enabledMessages.get(myChannel).contains("irc-topic")) {
            plugin.getServer().broadcast(tokenizer.chatIRCTokenizer(nick, myChannel, plugin.ircTopic), "irc.message.topic");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-topic")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(tokenizer.ircChatToHeroChatTokenizer(nick, myChannel,
                    plugin.ircHeroTopic, message, Herochat.getChannelManager(), heroChannel.get(myChannel)));
        }
    }

    // Broadcast disconnect messages from IRC
    public void broadcastIRCDisconnect() {
        plugin.getServer().broadcast("[" + bot.getNick() + "] Disconnected from IRC server.", "irc.message.disconnect");
    }

    // Broadcast connect messages from IRC
    public void broadcastIRCConnect() {
        plugin.getServer().broadcast("[" + bot.getNick() + "] Connected to IRC server.", "irc.message.connect");
    }

    protected String getFactionName(Player player) {
        UPlayer uPlayer = UPlayer.get(player);
        Faction faction = uPlayer.getFaction();
        return faction.getName();
    }        
    
}
