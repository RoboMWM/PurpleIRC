package com.cnaude.purpleirc;

import com.dthielke.herochat.Chatter;
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
import com.cnaude.purpleirc.IRCListeners.ModeListener;
import com.cnaude.purpleirc.IRCListeners.MotdListener;
import com.cnaude.purpleirc.IRCListeners.NickChangeListener;
import com.cnaude.purpleirc.IRCListeners.NoticeListener;
import com.cnaude.purpleirc.IRCListeners.PartListener;
import com.cnaude.purpleirc.IRCListeners.PrivateMessageListener;
import com.cnaude.purpleirc.IRCListeners.QuitListener;
import com.cnaude.purpleirc.IRCListeners.ServerResponseListener;
import com.cnaude.purpleirc.IRCListeners.TopicListener;
import com.cnaude.purpleirc.IRCListeners.VersionListener;
import com.cnaude.purpleirc.IRCListeners.WhoisListener;
import com.dthielke.herochat.Herochat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSortedSet;
import com.massivecraft.factions.entity.Faction;
import com.massivecraft.factions.entity.UPlayer;
import com.nyancraft.reportrts.data.HelpRequest;
import com.titankingdoms.dev.titanchat.core.participant.Participant;
import java.io.IOException;
import java.nio.charset.Charset;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.pircbotx.Channel;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.User;
import org.pircbotx.UtilSSLSocketFactory;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.ListenerAdapter;

/**
 *
 * @author Chris Naude
 */
public final class PurpleBot {

    private PircBotX bot;

    public final PurpleIRC plugin;
    private final File file;
    private YamlConfiguration config;
    public boolean autoConnect;
    public boolean ssl;
    public boolean trustAllCerts;
    public boolean sendRawMessageOnConnect;
    public boolean showMOTD;
    public boolean channelCmdNotifyEnabled;
    public boolean relayPrivateChat;
    public boolean partInvalidChannels;
    public int botServerPort;
    public long chatDelay;
    public String botServer;
    public String botNick;
    public String botLogin;
    public String botRealName;
    public String botServerPass;
    public String charSet;
    public String commandPrefix;
    public String quitMessage;
    public String botIdentPassword;
    public String rawMessage;
    public String channelCmdNotifyMode;
    public String partInvalidChannelsMsg;
    private String connectMessage;
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
    public HashMap<String, Boolean> hideJoinWhenVanished = new HashMap<String, Boolean>();
    public HashMap<String, Boolean> hideListWhenVanished = new HashMap<String, Boolean>();
    public HashMap<String, Boolean> hideQuitWhenVanished = new HashMap<String, Boolean>();
    public HashMap<String, String> heroChannel = new HashMap<String, String>();
    public Map<String, Collection<String>> opsList = new HashMap<String, Collection<String>>();
    public Map<String, Collection<String>> worldList = new HashMap<String, Collection<String>>();
    public Map<String, Collection<String>> muteList = new HashMap<String, Collection<String>>();
    public Map<String, Collection<String>> enabledMessages = new HashMap<String, Collection<String>>();
    public Map<String, Map<String, Map<String, String>>> commandMap = new HashMap<String, Map<String, Map<String, String>>>();
    public ArrayList<CommandSender> whoisSenders;
    public List<String> channelCmdNotifyRecipients = new ArrayList<String>();
    private final ArrayList<ListenerAdapter> ircListeners = new ArrayList<ListenerAdapter>();
    public IRCMessageQueueWatcher messageQueue;

    /**
     *
     * @param file
     * @param plugin
     */
    public PurpleBot(File file, PurpleIRC plugin) {
        this.plugin = plugin;
        this.file = file;
        whoisSenders = new ArrayList<CommandSender>();
        config = new YamlConfiguration();
        loadConfig();
        addListeners();
        buildBot();
        messageQueue = new IRCMessageQueueWatcher(this, plugin);
    }

    public void buildBot() {
        Configuration.Builder configBuilder = new Configuration.Builder()
                .setName(botNick)
                .setLogin(botLogin)
                .setAutoNickChange(true)
                .setCapEnabled(true)
                .setMessageDelay(chatDelay)
                .setRealName(botRealName)
                //.setAutoReconnect(autoConnect) // Why doesn't this work?
                .setServer(botServer, botServerPort, botServerPass);
        addAutoJoinChannels(configBuilder);
        for (ListenerAdapter ll : ircListeners) {
            configBuilder.addListener(ll);
        }
        if (!botIdentPassword.isEmpty()) {
            plugin.logInfo("Setting IdentPassword ...");
            configBuilder.setNickservPassword(botIdentPassword);
        }
        if (ssl) {
            UtilSSLSocketFactory socketFactory = new UtilSSLSocketFactory();
            socketFactory.disableDiffieHellman();
            if (trustAllCerts) {
                plugin.logInfo("Enabling SSL and trusting all certificates ...");
                socketFactory.trustAllCertificates();
            } else {
                plugin.logInfo("Enabling SSL ...");
            }
            configBuilder.setSocketFactory(socketFactory);
        }
        if (charSet.isEmpty()) {
            plugin.logInfo("Using default character set: " + Charset.defaultCharset());
        } else {
            if (Charset.isSupported(charSet)) {
                plugin.logInfo("Using character set: " + charSet);
                configBuilder.setEncoding(Charset.forName(charSet));
            } else {
                plugin.logError("Invalid character set: " + charSet);
                plugin.logInfo("Available character sets: " + Joiner.on(", ").join(Charset.availableCharsets().keySet()));
                plugin.logInfo("Using default character set: " + Charset.defaultCharset());
            }
        }
        Configuration configuration = configBuilder.buildConfiguration();
        bot = new PircBotX(configuration);
        if (autoConnect) {
            asyncConnect();
        } else {
            plugin.logInfo("Auto-connect is disabled. To connect: /irc connect " + bot.getNick());
        }
    }

    private void addListeners() {
        ircListeners.add(new ActionListener(plugin, this));
        ircListeners.add(new ConnectListener(plugin, this));
        ircListeners.add(new DisconnectListener(plugin, this));
        ircListeners.add(new JoinListener(plugin, this));
        ircListeners.add(new KickListener(plugin, this));
        ircListeners.add(new MessageListener(plugin, this));
        ircListeners.add(new ModeListener(plugin, this));
        ircListeners.add(new NickChangeListener(plugin, this));
        ircListeners.add(new NoticeListener(plugin, this));
        ircListeners.add(new PartListener(plugin, this));
        ircListeners.add(new PrivateMessageListener(plugin, this));
        ircListeners.add(new QuitListener(plugin, this));
        ircListeners.add(new TopicListener(plugin, this));
        ircListeners.add(new VersionListener(plugin));
        ircListeners.add(new WhoisListener(plugin, this));
        ircListeners.add(new MotdListener(plugin, this));
        ircListeners.add(new ServerResponseListener(plugin, this));
    }

    private void addAutoJoinChannels(Configuration.Builder configBuilder) {
        for (String channelName : botChannels) {
            if (channelAutoJoin.containsKey(channelName)) {
                if (channelAutoJoin.get(channelName)) {
                    if (channelPassword.get(channelName).isEmpty()) {
                        configBuilder.addAutoJoinChannel(channelName);
                    } else {
                        configBuilder.addAutoJoinChannel(channelName, channelPassword.get(channelName));
                    }
                }
            }
        }
    }

    public void reload(CommandSender sender) {
        sender.sendMessage("Reloading bot: " + botNick);
        reload();
    }

    public void reload() {
        asyncQuit(true);
    }

    /**
     *
     * @param sender
     */
    public void reloadConfig(CommandSender sender) {
        config = new YamlConfiguration();
        loadConfig();
        sender.sendMessage("[PurpleIRC] [" + botNick + "] IRC bot configuration reloaded.");
    }

    /**
     *
     * @param channelName
     * @param sender
     * @param user
     */
    public void mute(String channelName, CommandSender sender, String user) {
        if (muteList.get(channelName).contains(user)) {
            sender.sendMessage("User '" + user + "' is already muted.");
        } else {
            sender.sendMessage("User '" + user + "' is now muted.");
            muteList.get(channelName).add(user);
            saveConfig();
        }
    }
    
    /**
     *
     * @param channelName
     * @param sender     
     */
    public void muteList(String channelName, CommandSender sender) {
        if (muteList.isEmpty()) {
            sender.sendMessage("There are no users muted.");
        } else {
            sender.sendMessage("Muted users: " + Joiner.on(", ")
                    .join(muteList.values()));            
            saveConfig();
        }
    }
    
    /**
     *
     * @param channelName
     * @param sender
     * @param user
     */
    public void unMute(String channelName, CommandSender sender, String user) {
        if (muteList.get(channelName).contains(user)) {
            sender.sendMessage("User '" + user + "' is no longer muted.");
            muteList.get(channelName).remove(user);
            saveConfig();
        } else {
            sender.sendMessage("User '" + user + "' is not muted.");
        }
    }

    public void asyncConnect(CommandSender sender) {
        sender.sendMessage(connectMessage);
        asyncConnect();
    }

    /**
     *
     */
    public void asyncConnect() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    plugin.logInfo(connectMessage);
                    bot.startBot();
                } catch (IOException ex) {
                    plugin.logError("Problem connecting to " + botServer + " => "
                            + " as " + botNick + " [Error: " + ex.getMessage() + "]");
                } catch (IrcException ex) {
                    plugin.logError("Problem connecting to " + botServer + " => "
                            + " as " + botNick + " [Error: " + ex.getMessage() + "]");
                }
            }
        });
    }

    public void asyncIRCMessage(final String target, final String message) {
        plugin.logDebug("Entering aysncIRCMessage");
        messageQueue.add(new IRCMessage(target, plugin.colorConverter.
                gameColorsToIrc(message), false));
    }

    public void asyncCTCPMessage(final String target, final String message) {
        plugin.logDebug("Entering asyncCTCPMessage");
        messageQueue.add(new IRCMessage(target, plugin.colorConverter
                .gameColorsToIrc(message), true));
    }

    public void blockingIRCMessage(final String target, final String message) {
        plugin.logDebug("[blockingIRCMessage] About to send IRC message to " + target);
        bot.sendIRC().message(target, plugin.colorConverter
                .gameColorsToIrc(message));
        plugin.logDebug("[blockingIRCMessage] Message sent to " + target);
    }

    public void blockingCTCPMessage(final String target, final String message) {
        plugin.logDebug("[blockingCTCPMessage] About to send IRC message to " + target);
        bot.sendIRC().ctcpResponse(target, plugin.colorConverter
                .gameColorsToIrc(message));
        plugin.logDebug("[blockingCTCPMessage] Message sent to " + target);
    }

    public void asyncCTCPCommand(final String target, final String command) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                bot.sendIRC().ctcpCommand(target, command);
            }
        });
    }

    /**
     *
     * @param sender
     */
    public void saveConfig(CommandSender sender) {
        try {
            config.save(file);
            sender.sendMessage(plugin.LOG_HEADER_F
                    + " Saving bot \"" + botNick + "\" to " + file.getName());
        } catch (IOException ex) {
            plugin.logError(ex.getMessage());
            sender.sendMessage(ex.getMessage());
        }
    }

    /**
     *
     */
    public void saveConfig() {
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.logError(ex.getMessage());
        }
    }

    /**
     *
     * @param sender
     * @param newNick
     */
    public void asyncChangeNick(final CommandSender sender, final String newNick) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                bot.sendIRC().changeNick(newNick);

            }
        });
        sender.sendMessage("Setting nickname to " + newNick);
        config.set("nick", newNick);
        saveConfig();
    }

    public void asyncJoinChannel(final String channelName, final String password) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                bot.sendIRC().joinChannel(channelName, password);
            }
        });
    }

    public void asyncNotice(final String target, final String message) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                bot.sendIRC().notice(target, message);
            }
        });
    }

    public void asyncRawlineNow(final String message) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                bot.sendRaw().rawLineNow(message);
            }
        });
    }

    public void asyncIdentify(final String password) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                bot.sendIRC().identify(password);
            }
        });
    }

    /**
     *
     * @param sender
     * @param newLogin
     */
    public void changeLogin(CommandSender sender, String newLogin) {
        sender.sendMessage(ChatColor.DARK_PURPLE
                + "Login set to " + ChatColor.WHITE
                + newLogin + ChatColor.DARK_PURPLE
                + ". Reload the bot for the change to take effect.");
        config.set("login", newLogin);
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
            config.load(file);
            autoConnect = config.getBoolean("autoconnect", true);
            ssl = config.getBoolean("ssl", false);
            trustAllCerts = config.getBoolean("trust-all-certs", false);
            sendRawMessageOnConnect = config.getBoolean("raw-message-on-connect", false);
            rawMessage = config.getString("raw-message", "");
            relayPrivateChat = config.getBoolean("relay-private-chat", false);
            partInvalidChannels = config.getBoolean("part-invalid-channels", false);
            partInvalidChannelsMsg = config.getString("part-invalid-channels-message", "");
            botNick = config.getString("nick", "");
            botLogin = config.getString("login", "PircBot");
            botRealName = config.getString("realname", "");
            if (botRealName.isEmpty()) {
                botRealName = plugin.getServer()
                        .getPluginManager().getPlugin("PurpleIRC")
                        .getDescription().getWebsite();
            }
            botServer = config.getString("server", "");
            charSet = config.getString("charset", "");
            sanitizeServerName();
            showMOTD = config.getBoolean("show-motd", false);
            botServerPort = config.getInt("port");
            botServerPass = config.getString("password", "");
            botIdentPassword = config.getString("ident-password", "");
            commandPrefix = config.getString("command-prefix", ".");
            chatDelay = config.getLong("message-delay", 1000);
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

            channelCmdNotifyEnabled = config.getBoolean("command-notify.enabled", false);
            plugin.logDebug(" CommandNotifyEnabled => " + channelCmdNotifyEnabled);

            channelCmdNotifyMode = config.getString("command-notify.mode", "msg");
            plugin.logDebug(" channelCmdNotifyMode => " + channelCmdNotifyMode);

            // build command notify recipient list            
            for (String recipient : config.getStringList("command-notify.recipients")) {
                if (!channelCmdNotifyRecipients.contains(recipient)) {
                    channelCmdNotifyRecipients.add(recipient);
                }
                plugin.logDebug(" Command Notify Recipient => " + recipient);
            }
            if (channelCmdNotifyRecipients.isEmpty()) {
                plugin.logInfo(" No command recipients defined.");
            }

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

                hideJoinWhenVanished.put(channelName, config.getBoolean("channels." + enChannelName + ".hide-join-when-vanished", true));
                plugin.logDebug("  HideJoinWhenVanished => " + hideJoinWhenVanished.get(channelName));

                hideListWhenVanished.put(channelName, config.getBoolean("channels." + enChannelName + ".hide-list-when-vanished", true));
                plugin.logDebug("  HideListWhenVanished => " + hideListWhenVanished.get(channelName));

                hideQuitWhenVanished.put(channelName, config.getBoolean("channels." + enChannelName + ".hide-quit-when-vanished", true));
                plugin.logDebug("  HideQuitWhenVanished => " + hideQuitWhenVanished.get(channelName));

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
                    String commandKey = "channels." + enChannelName + ".commands." + command + ".";
                    optionPair.put("modes", config.getString(commandKey + "modes", "*"));
                    optionPair.put("private", config.getString(commandKey + "private", "false"));
                    optionPair.put("ctcp", config.getString(commandKey + "ctcp", "false"));
                    optionPair.put("game_command", config.getString(commandKey + "game_command", "@help"));
                    optionPair.put("private_listen", config.getString(commandKey + "private_listen", "true"));
                    optionPair.put("channel_listen", config.getString(commandKey + "channel_listen", "true"));
                    for (String s : optionPair.keySet()) {
                        config.set(commandKey + s, optionPair.get(s));
                    }
                    map.put(command, optionPair);
                }
                commandMap.put(channelName, map);
                if (map.isEmpty()) {
                    plugin.logInfo("No commands specified!");
                }
                connectMessage = "Connecting to \"" + botServer + ":"
                        + botServerPort + "\" as \"" + botNick
                        + "\" [SSL: " + ssl + "]" + " [TrustAllCerts: "
                        + trustAllCerts + "]";
            }
        } catch (IOException ex) {
            plugin.logError(ex.getMessage());
        } catch (InvalidConfigurationException ex) {
            plugin.logError(ex.getMessage());
        }
    }

    /**
     *
     * @param sender
     * @param delay
     */
    public void setIRCDelay(CommandSender sender, long delay) {
        config.set("message-delay", delay);
        saveConfig();
        sender.sendMessage(ChatColor.DARK_PURPLE
                + "IRC message delay changed to "
                + ChatColor.WHITE + delay + ChatColor.DARK_PURPLE + " ms. "
                + "Reload for the change to take effect.");
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
    /**
     *
     * @param player
     * @param message
     */
    public void gameChat(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (!isPlayerInValidWorld(player, channelName)) {
                continue;
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

                String chatName = "faction-" + playerChatMode.toLowerCase() + "-chat";
                plugin.logDebug("Faction [Player: " + player.getName()
                        + "] [Tag: " + playerFactionName + "] [Mode: "
                        + playerChatMode + "]");
                if (enabledMessages.get(channelName)
                        .contains(chatName)) {
                    asyncIRCMessage(channelName, plugin.tokenizer
                            .chatFactionTokenizer(player, message,
                                    playerFactionName, playerChatMode));
                } else {
                    plugin.logDebug("Player " + player.getName() + " is in chat mode \""
                            + playerChatMode + "\" but \"" + chatName + "\" is disabled.");
                }
            } else {
                plugin.logDebug("No Factions");
            }
            if (enabledMessages.get(channelName).contains("game-chat")) {
                plugin.logDebug("[game-chat] => " + channelName + " => " + message);
                asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer(player, plugin.gameChat, message));
            } else {
                plugin.logDebug("Ignoring message due to game-chat not being listed.");
            }
        }
    }

    // Called from HeroChat listener
    /**
     *
     * @param chatter
     * @param chatColor
     * @param message
     */
    public void heroChat(Chatter chatter, ChatColor chatColor, String message) {
        plugin.logDebug("H1");
        Player player = chatter.getPlayer();
        if (!bot.isConnected()) {
            return;
        }
        plugin.logDebug("H2");
        for (String channelName : botChannels) {
            plugin.logDebug("H3");
            if (!isPlayerInValidWorld(player, channelName)) {
                continue;
            }
            plugin.logDebug("H3.1");
            String hChannel = chatter.getActiveChannel().getName();
            String hNick = chatter.getActiveChannel().getNick();
            String hColor = chatColor.toString();
            plugin.logDebug("HC Channel: " + hChannel);
            plugin.logDebug("H3.2");
            if (enabledMessages.get(channelName).contains("hero-" + hChannel + "-chat")
                    || enabledMessages.get(channelName).contains("hero-chat")) {
                plugin.logDebug("H3.3");
                asyncIRCMessage(channelName, plugin.tokenizer
                        .chatHeroTokenizer(player, message, hColor, hChannel,
                                hNick, getHeroChatChannelTemplate(hChannel)));
                plugin.logDebug("H3.4");
            } else {
                plugin.logDebug("Player " + player.getName() + " is in \""
                        + hChannel + "\" but hero-" + hChannel + "-chat is disabled.");
            }
        }
        plugin.logDebug("H4");
    }

    public void mcMMOAdminChat(Player player, String message) {
        String messageType = "mcmmo-admin-chat";
        for (String channelName : botChannels) {
            if (!isPlayerInValidWorld(player, channelName)) {
                continue;
            }
            if (enabledMessages.get(channelName).contains(messageType)) {
                plugin.logDebug("Sending message because " + messageType + " is enabled.");
                asyncIRCMessage(channelName, plugin.tokenizer
                        .gameChatToIRCTokenizer(player, plugin.mcMMOAdminChat, message));
            } else {
                plugin.logDebug("Player " + player.getName()
                        + " is in mcMMO PartyChat but " + messageType + " is disabled.");
            }
        }
    }

    public void mcMMOPartyChat(Player player, String partyName, String message) {
        String messageType = "mcmmo-party-chat";
        for (String channelName : botChannels) {
            if (!isPlayerInValidWorld(player, channelName)) {
                continue;
            }
            if (enabledMessages.get(channelName).contains(messageType)) {
                plugin.logDebug("Sending message because " + messageType + " is enabled.");
                asyncIRCMessage(channelName, plugin.tokenizer
                        .mcMMOChatToIRCTokenizer(player, plugin.mcMMOPartyChat, message, partyName));
            } else {
                plugin.logDebug("Player " + player.getName()
                        + " is in mcMMO PartyChat but " + messageType + " is disabled.");
            }
        }
    }

    public void mcMMOChat(Player player, String message) {
        String messageType = "mcmmo-chat";
        for (String channelName : botChannels) {
            if (!isPlayerInValidWorld(player, channelName)) {
                continue;
            }
            if (enabledMessages.get(channelName).contains(messageType)) {
                plugin.logDebug("Sending message because " + messageType + " is enabled.");
                asyncIRCMessage(channelName, plugin.tokenizer
                        .gameChatToIRCTokenizer(player, plugin.mcMMOChat, message));
            } else {
                plugin.logDebug("Player " + player.getName()
                        + " is in mcMMO PartyChat but " + messageType + " is disabled.");
            }
        }
    }

    public void townyChat(Player player, 
            com.palmergames.bukkit.TownyChat.channels.Channel townyChannel, String message) {
        if (!bot.isConnected()) {
            return;
        }
        if (plugin.tcHook != null) {
            for (String channelName : botChannels) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    continue;
                }
                plugin.logDebug("townyChat: Checking for towny-"
                        + townyChannel.getName() + "-chat"
                        + " or " + "towny-" + townyChannel.getChannelTag() + "-chat"
                        + " or towny-chat");
                if (enabledMessages.get(channelName).contains("towny-" + townyChannel.getName() + "-chat")
                        || enabledMessages.get(channelName).contains("towny-" + townyChannel.getChannelTag() + "-chat")
                        || enabledMessages.get(channelName).contains("towny-chat")
                        || enabledMessages.get(channelName).contains("towny-channel-chat")) {
                    asyncIRCMessage(channelName, plugin.tokenizer
                            .chatTownyChannelTokenizer(player, townyChannel, message));
                }
            }
        }
    }

    private String getHeroChatChannelTemplate(String hChannel) {
        if (plugin.heroChannelMessages.containsKey(hChannel.toLowerCase())) {
            return plugin.heroChannelMessages.get(hChannel.toLowerCase());
        } else {
            return plugin.heroChat;
        }
    }

    private String getHeroActionChannelTemplate(String hChannel) {
        if (plugin.heroActionChannelMessages.containsKey(hChannel.toLowerCase())) {
            return plugin.heroActionChannelMessages.get(hChannel.toLowerCase());
        } else {
            return plugin.heroAction;
        }
    }

    public void heroAction(Chatter chatter, ChatColor chatColor, String message) {
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
            if (enabledMessages.get(channelName).contains("hero-" + hChannel + "-action")
                    || enabledMessages.get(channelName).contains("hero-action")) {
                asyncIRCMessage(channelName, plugin.tokenizer
                        .chatHeroTokenizer(player, message, hColor, hChannel,
                                hNick, getHeroActionChannelTemplate(hChannel)));
            } else {
                plugin.logDebug("Player " + player.getName() + " is in \""
                        + hChannel + "\" but hero-" + hChannel + "-action is disabled.");
            }
        }
    }

    // Called from TitanChat listener
    /**
     *
     * @param participant
     * @param tChannel
     * @param tColor
     * @param message
     */
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
                asyncIRCMessage(channelName, plugin.tokenizer.titanChatTokenizer(player, tChannel, tColor, message));
            } else {
                plugin.logDebug("Player " + player.getName() + " is in \""
                        + tChannel + "\" but titan-" + tChannel + "-chat is disabled.");
            }
        }
    }

    // Called from /irc send
    /**
     *
     * @param player
     * @param channelName
     * @param message
     */
    public void gameChat(Player player, String channelName, String message) {
        if (!bot.isConnected()) {
            return;
        }
        if (isValidChannel(channelName)) {
            asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer(player, plugin.gameSend, message));
        }
    }

    // Called from CleverEvent
    /**
     *
     * @param cleverBotName
     * @param message
     */
    public void cleverChat(String cleverBotName, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("clever-chat")) {
                asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer(cleverBotName, plugin.cleverSend, message));
            }
        }
    }

    // Called from ReportRTS event
    /**
     *
     * @param pName
     * @param request
     * @param template
     * @param messageType
     */
    public void reportRTSNotify(String pName, HelpRequest request, 
            String template, String messageType) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains(messageType)) {
                asyncIRCMessage(channelName, plugin.tokenizer
                        .reportRTSTokenizer(pName, template, request));
            }
        }
    }
    
    public void reportRTSNotify(CommandSender sender, String message, String template, String messageType) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains(messageType)) {
                asyncIRCMessage(channelName, plugin.tokenizer.reportRTSTokenizer(sender, message, template));
            }
        }
    }

    /**
     *
     * @param channelName
     * @param message
     */
    public void consoleChat(String channelName, String message) {
        if (!bot.isConnected()) {
            return;
        }
        if (isValidChannel(channelName)) {
            asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer("CONSOLE", message, plugin.gameSend));
        }
    }

    /**
     *
     * @param message
     */
    public void consoleChat(String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("console-chat")) {
                asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer(plugin.consoleChat, ChatColor.translateAlternateColorCodes('&', message)));
            }
        }
    }

    /**
     *
     * @param player
     * @param message
     */
    public void gameBroadcast(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("broadcast-message")) {
                asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer(player, plugin.broadcastMessage, ChatColor.translateAlternateColorCodes('&', message)));
            }
        }
    }

    /**
     *
     * @param message
     */
    public void consoleBroadcast(String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("broadcast-console-message")) {
                asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer(plugin.broadcastConsoleMessage, ChatColor.translateAlternateColorCodes('&', message)));
            }
        }
    }

    /**
     *
     * @param player
     * @param message
     */
    public void gameJoin(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("game-join")) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    return;
                }
                if (hideJoinWhenVanished.get(channelName)) {
                    plugin.logDebug("Checking if player " + player.getName() + " is vanished.");
                    if (plugin.vanishHook.isVanished(player)) {
                        plugin.logDebug("Not sending join message to IRC for player " + player.getName() + " due to being vanished.");
                        continue;
                    }
                }
                asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer(player, plugin.gameJoin, message));
            } else {
                plugin.logDebug("not sending join message due to 'game-join' being disabled");
            }
        }
    }

    /**
     *
     * @param player
     * @param message
     */
    public void gameQuit(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("game-quit")) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    return;
                }
                if (hideQuitWhenVanished.get(channelName)) {
                    plugin.logDebug("Checking if player " + player.getName() + " is vanished.");
                    if (plugin.vanishHook.isVanished(player)) {
                        plugin.logDebug("Not sending quit message to IRC for player " + player.getName() + " due to being vanished.");
                        continue;
                    }
                }
                asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer(player, plugin.gameQuit, message));
            }
        }
    }

    /**
     *
     * @param player
     * @param message
     * @param reason
     */
    public void gameKick(Player player, String message, String reason) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("game-kick")) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    return;
                }
                asyncIRCMessage(channelName, plugin.tokenizer
                        .gameKickTokenizer(player, message, reason));
            }
        }
    }

    /**
     *
     * @param player
     * @param message
     */
    public void gameAction(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("game-action")) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    return;
                }
                asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer(player, plugin.gameAction, message));
            }
        }
    }

    /**
     *
     * @param player
     * @param message
     */
    public void gameDeath(Player player, String message) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("game-death")) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    return;
                }
                asyncIRCMessage(channelName, plugin.tokenizer.gameChatToIRCTokenizer(player, plugin.gameDeath, message));
            }
        }
    }

    /**
     *
     * @param channelName
     * @param topic
     * @param sender
     */
    public void changeTopic(String channelName, String topic, CommandSender sender) {
        Channel channel = this.getChannel(channelName);
        String tTopic = tokenizedTopic(topic);
        if (channel != null) {
            channel.send().setTopic(tTopic);
            config.set("channels." + encodeChannel(channelName) + ".topic", topic);
            channelTopic.put(channelName, topic);
            saveConfig();
            sender.sendMessage("IRC topic for " + channelName + " changed to \"" + topic + "\"");
        } else {
            sender.sendMessage("Invalid channel: " + channelName);
        }
    }

    public Channel getChannel(String channelName) {
        Channel channel = null;
        for (Channel c : getChannels()) {
            if (c.getName().equalsIgnoreCase(channelName)) {
                return c;
            }
        }
        return channel;
    }

    /**
     *
     * @param sender
     * @param botServer
     */
    public void setServer(CommandSender sender, String botServer) {
        setServer(sender, botServer, autoConnect);
    }

    /**
     *
     * @param sender
     * @param server
     * @param auto
     */
    public void setServer(CommandSender sender, String server, Boolean auto) {

        if (server.contains(":")) {
            botServerPort = Integer.parseInt(server.split(":")[1]);
            botServer = server.split(":")[0];
        } else {
            botServer = server;
        }
        sanitizeServerName();
        autoConnect = auto;
        config.set("server", botServer);
        config.set("port", botServerPort);
        config.set("autoconnect", autoConnect);

        sender.sendMessage("IRC server changed to \"" + botServer + ":"
                + botServerPort + "\". (AutoConnect: "
                + autoConnect + ")");
    }

    /**
     *
     * @param channelName
     * @param userMask
     * @param sender
     */
    public void addOp(String channelName, String userMask, CommandSender sender) {
        if (opsList.get(channelName).contains(userMask)) {
            sender.sendMessage("User mask " + ChatColor.WHITE + userMask
                    + ChatColor.RESET + " is already in the ops list.");
        } else {
            sender.sendMessage("User mask " + ChatColor.WHITE + userMask
                    + ChatColor.RESET + " has been added to the ops list.");
            opsList.get(channelName).add(userMask);
        }
        config.set("channels." + encodeChannel(channelName) + ".ops", opsList.get(channelName));
        saveConfig();
    }

    /**
     *
     * @param channelName
     * @param userMask
     * @param sender
     */
    public void removeOp(String channelName, String userMask, CommandSender sender) {
        if (opsList.get(channelName).contains(userMask)) {
            sender.sendMessage("User mask " + ChatColor.WHITE + userMask
                    + ChatColor.RESET + " has been removed to the ops list.");
            opsList.get(channelName).remove(userMask);
        } else {
            sender.sendMessage("User mask " + ChatColor.WHITE + userMask
                    + ChatColor.RESET + " is not in the ops list.");
        }
        config.set("channels." + encodeChannel(channelName) + ".ops", opsList.get(channelName));
        saveConfig();
    }

    /**
     *
     * @param channelName
     * @param nick
     */
    public void op(String channelName, String nick) {
        Channel channel;
        channel = getChannel(channelName);
        if (channel != null) {
            for (User user : channel.getUsers()) {
                if (user.getNick().equals(nick)) {
                    channel.send().op(user);
                    return;
                }
            }
        }
    }

    /**
     *
     * @param channelName
     * @param nick
     */
    public void deOp(String channelName, String nick) {
        Channel channel;
        channel = getChannel(channelName);
        if (channel != null) {
            for (User user : channel.getUsers()) {
                if (user.getNick().equals(nick)) {
                    channel.send().deOp(user);
                    return;
                }
            }
        }
    }

    /**
     *
     * @param channelName
     * @param nick
     */
    public void kick(String channelName, String nick) {
        Channel channel;
        channel = getChannel(channelName);
        if (channel != null) {
            for (User user : channel.getUsers()) {
                if (user.getNick().equals(nick)) {
                    channel.send().kick(user);
                    return;
                }
            }
        }
    }

    private String encodeChannel(String s) {
        return s.replace(".", "%2E");
    }

    private String decodeChannel(String s) {
        return s.replace("%2E", ".");
    }

    /**
     *
     * @param channel
     * @param topic
     * @param setBy
     */
    public void fixTopic(Channel channel, String topic, String setBy) {
        String channelName = channel.getName();
        String tTopic = tokenizedTopic(topic);
        if (setBy.equals(botNick)) {
            //config.set("channels." + encodeChannel(channelName) + ".topic", topic);
            //saveConfig();
            return;
        }

        if (channelTopic.containsKey(channelName)) {
            if (channelTopicProtected.containsKey(channelName)) {
                if (channelTopicProtected.get(channelName)) {
                    plugin.logDebug("[" + channel.getName() + "] Topic protected.");
                    String myTopic = tokenizedTopic(channelTopic.get(channelName));
                    plugin.logDebug("rTopic: " + channelTopic.get(channelName));
                    plugin.logDebug("tTopic: " + tTopic);
                    plugin.logDebug("myTopic: " + myTopic);
                    if (!tTopic.equals(myTopic)) {
                        plugin.logDebug("Topic is not correct. Fixing it.");
                        channel.send().setTopic(myTopic);
                    } else {
                        plugin.logDebug("Topic is correct.");
                    }
                }
            }
        }
    }

    private String tokenizedTopic(String topic) {
        return plugin.colorConverter
                .gameColorsToIrc(topic.replace("%MOTD%", plugin.getServer().getMotd()));
    }

    /**
     *
     * @param sender
     */
    public void asyncQuit(CommandSender sender) {
        sender.sendMessage("Disconnecting " + bot.getNick() + " from IRC server " + botServer);
        asyncQuit(false);
    }

    /**
     *
     * @param reload
     */
    public void asyncQuit(final Boolean reload) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                quit();
                if (reload) {
                    buildBot();
                }
            }
        });

    }

    public void quit() {
        if (bot.isConnected()) {
            plugin.logDebug("Q: " + quitMessage);
            if (quitMessage.isEmpty()) {
                bot.sendIRC().quitServer();
            } else {
                bot.sendIRC().quitServer(plugin.colorConverter.gameColorsToIrc(quitMessage));
            }
        }
    }

    /**
     *
     * @param sender
     */
    public void sendTopic(CommandSender sender) {
        for (String channelName : botChannels) {
            if (commandMap.containsKey(channelName)) {
                sender.sendMessage(ChatColor.WHITE + "[" + ChatColor.DARK_PURPLE
                        + botNick + ChatColor.WHITE + "]" + ChatColor.RESET
                        + " IRC topic for " + ChatColor.WHITE + channelName
                        + ChatColor.RESET + ": \""
                        + ChatColor.WHITE + plugin.colorConverter
                        .ircColorsToGame(activeTopic.get(channelName))
                        + ChatColor.RESET + "\"");
            }
        }
    }

    /**
     *
     * @param sender
     * @param nick
     */
    public void sendUserWhois(CommandSender sender, String nick) {
        User user = null;
        for (Channel channel : getChannels()) {
            for (User u : channel.getUsers()) {
                if (u.getNick().equals(nick)) {
                    user = u;
                }
            }
        }

        if (user == null) {
            sender.sendMessage(ChatColor.RED + "Invalid user: " + ChatColor.WHITE + nick);
        } else {
            bot.sendRaw().rawLineNow(String.format("WHOIS %s %s", nick, nick));
            whoisSenders.add(sender);
        }
    }

    /**
     *
     * @param sender
     * @param channelName
     */
    public void sendUserList(CommandSender sender, String channelName) {
        String invalidChannel = ChatColor.RED + "Invalid channel: "
                + ChatColor.WHITE + channelName;
        if (!isValidChannel(channelName)) {
            sender.sendMessage(invalidChannel);
            return;
        }
        Channel channel = getChannel(channelName);
        if (channel != null) {
            sendUserList(sender, channel);
        } else {
            sender.sendMessage(invalidChannel);
        }
    }

    /**
     *
     * @param sender
     * @param channel
     */
    public void sendUserList(CommandSender sender, Channel channel) {
        String channelName = channel.getName();
        if (!isValidChannel(channelName)) {
            sender.sendMessage(ChatColor.RED + "Invalid channel: "
                    + ChatColor.WHITE + channelName);
            return;
        }
        sender.sendMessage(ChatColor.DARK_PURPLE + "-----[  " + ChatColor.WHITE + channelName
                + ChatColor.DARK_PURPLE + " - " + ChatColor.WHITE + bot.getNick() + ChatColor.DARK_PURPLE + " ]-----");
        if (!bot.isConnected()) {
            sender.sendMessage(ChatColor.RED + " Not connected!");
            return;
        }
        List<String> channelUsers = new ArrayList<String>();
        for (User user : channel.getUsers()) {
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

    /**
     *
     * @param sender
     */
    public void sendUserList(CommandSender sender) {
        for (Channel channel : getChannels()) {
            if (isValidChannel(channel.getName())) {
                sendUserList(sender, channel);
            }
        }
    }

    /**
     *
     * @param channel
     */
    public void updateNickList(Channel channel) {
        // Build current list of names in channel
        ArrayList<String> users = new ArrayList<String>();
        for (User user : channel.getUsers()) {
            //plugin.logDebug("N: " + user.getNick());
            users.add(user.getNick());
        }
        // Iterate over previous list and remove from tab list
        String channelName = channel.getName();
        if (channelNicks.containsKey(channelName)) {
            for (String name : channelNicks.get(channelName)) {
                //plugin.logDebug("O: " + name);
                if (!users.contains(name)) {
                    plugin.logDebug("Removing " + name + " from list.");
                    if (plugin.netPackets != null) {
                        plugin.netPackets.remFromTabList(name);
                    }
                }
            }
            channelNicks.remove(channelName);
        }
        channelNicks.put(channelName, users);
    }

    /**
     *
     * @param channel
     */
    public void opFriends(Channel channel) {
        for (User user : channel.getUsers()) {
            opFriends(channel, user);
        }
    }

    /**
     *
     * @param channelName
     */
    public void opFriends(String channelName) {
        Channel channel = getChannel(channelName);
        if (channel != null) {
            for (User user : channel.getUsers()) {
                opFriends(channel, user);
            }
        }
    }

    /**
     *
     * @param channel
     * @param user
     */
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
                    if (!channel.getOps().contains(user)) {
                        plugin.logInfo("Giving operator status to " + sender + " on " + channelName);
                        channel.send().op(user);
                    } else {
                        plugin.logInfo("User " + sender + " is already an operator on " + channelName);
                    }
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
    /**
     *
     * @param nick
     * @param myChannel
     * @param message
     * @param override
     */
    public void broadcastChat(String nick, String myChannel, String message, boolean override) {
        plugin.logDebug("Check if irc-chat is enabled before broadcasting chat from IRC");
        if (enabledMessages.get(myChannel).contains("irc-chat") || override) {
            plugin.logDebug("Yup we can broadcast due to irc-chat enabled");
            plugin.getServer().broadcast(plugin.tokenizer.ircChatToGameTokenizer(nick, myChannel, plugin.ircChat, message), "irc.message.chat");
        } else {
            plugin.logDebug("NOPE we can't broadcast due to irc-chat disabled");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-chat")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(plugin.tokenizer.ircChatToHeroChatTokenizer(nick, myChannel,
                                    plugin.ircHeroChat, message,
                                    Herochat.getChannelManager(),
                                    heroChannel.get(myChannel)));
        }
    }

    // Broadcast chat messages from IRC to specific hero channel
    /**
     *
     * @param nick
     * @param ircChannel
     * @param target
     * @param message
     */
    public void broadcastHeroChat(String nick, String ircChannel, String target, String message) {
        if (message == null) {
            plugin.logDebug("H: NULL MESSAGE");
            asyncIRCMessage(target, "No channel specified!");
            return;
        }
        if (message.contains(" ")) {
            String hChannel;
            String msg;
            hChannel = message.split(" ", 2)[0];
            msg = message.split(" ", 2)[1];
            plugin.logDebug("Check if irc-hero-chat is enabled before broadcasting chat from IRC");
            if (enabledMessages.get(ircChannel).contains("irc-hero-chat")) {
                plugin.logDebug("Checking if " + hChannel + " is a valid hero channel...");
                if (Herochat.getChannelManager().hasChannel(hChannel)) {
                    hChannel = Herochat.getChannelManager().getChannel(hChannel).getName();
                    String template = plugin.ircHeroChat;
                    if (plugin.ircHeroChannelMessages.containsKey(hChannel.toLowerCase())) {
                        template = plugin.ircHeroChannelMessages.get(hChannel.toLowerCase());
                    }
                    plugin.logDebug("T: " + template);
                    String t = plugin.tokenizer.ircChatToHeroChatTokenizer(nick,
                            ircChannel, template, msg,
                            Herochat.getChannelManager(), hChannel);
                    plugin.logDebug("Sending message to" + hChannel + ":" + t);
                    Herochat.getChannelManager().getChannel(hChannel)
                            .sendRawMessage(t);
                    plugin.logDebug("Channel format: " + Herochat.getChannelManager().getChannel(hChannel).getFormat());
                    if (!plugin.ircHChatResponse.isEmpty()) {
                        asyncIRCMessage(target, plugin.tokenizer.targetChatResponseTokenizer(hChannel, msg, plugin.ircHChatResponse));
                    }
                } else {
                    asyncIRCMessage(target, "Hero channel \"" + hChannel + "\" does not exist!");
                }
            } else {
                plugin.logDebug("NOPE we can't broadcast due to irc-hero-chat disabled");
            }
        } else {
            asyncIRCMessage(target, "No message specified.");
        }
    }

    // Send chat messages from IRC to player
    /**
     *
     * @param nick
     * @param myChannel
     * @param target
     * @param message
     */
    public void playerChat(String nick, String myChannel, String target, String message) {
        if (message == null) {
            plugin.logDebug("H: NULL MESSAGE");
            asyncIRCMessage(target, "No player specified!");
            return;
        }
        if (message.contains(" ")) {
            String pName;
            String msg;
            pName = message.split(" ", 2)[0];
            msg = message.split(" ", 2)[1];
            plugin.logDebug("Check if irc-pchat is enabled before broadcasting chat from IRC");
            if (enabledMessages.get(myChannel).contains("irc-pchat")) {
                plugin.logDebug("Yup we can broadcast due to irc-pchat enabled... Checking if " + pName + " is a valid player...");
                Player player = plugin.getServer().getPlayer(pName);
                if (player != null) {
                    if (player.isOnline()) {
                        plugin.logDebug("Yup, " + pName + " is a valid player...");
                        String t = plugin.tokenizer.ircChatToGameTokenizer(nick, myChannel, plugin.ircPChat, msg);
                        if (!plugin.ircPChatResponse.isEmpty()) {
                            asyncIRCMessage(target, plugin.tokenizer.targetChatResponseTokenizer(pName, msg, plugin.ircPChatResponse));
                        }
                        plugin.logDebug("Tokenized message: " + t);
                        player.sendMessage(t);
                    } else {
                        asyncIRCMessage(target, "Player is offline: " + pName);
                    }
                } else {
                    asyncIRCMessage(target, "Player not found (possibly offline): " + pName);
                }
            } else {
                plugin.logDebug("NOPE we can't broadcast due to irc-pchat disabled");
            }
        } else {
            asyncIRCMessage(target, "No message specified.");
        }
    }

// Broadcast action messages from IRC
    /**
     *
     * @param nick
     * @param myChannel
     * @param message
     */
    public void broadcastAction(String nick, String myChannel, String message) {
        if (enabledMessages.get(myChannel).contains("irc-action")) {
            plugin.getServer().broadcast(plugin.tokenizer.ircChatToGameTokenizer(nick, myChannel, plugin.ircAction, message), "irc.message.action");
        } else {
            plugin.logDebug("Ignoring action due to irc-action is false");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-action")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(plugin.tokenizer.ircChatToHeroChatTokenizer(nick,
                                    myChannel, plugin.ircHeroAction, message,
                                    Herochat.getChannelManager(),
                                    heroChannel.get(myChannel)));
        }
    }

    /**
     *
     * @param recipient
     * @param kicker
     * @param reason
     * @param myChannel
     */
    public void broadcastIRCKick(String recipient, String kicker, String reason, String myChannel) {
        if (enabledMessages.get(myChannel).contains("irc-kick")) {
            plugin.getServer().broadcast(plugin.tokenizer.ircKickTokenizer(recipient,
                    kicker, reason, myChannel, plugin.ircKick),
                    "irc.message.kick");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-kick")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(plugin.tokenizer
                            .ircKickToHeroChatTokenizer(recipient, kicker,
                                    reason, myChannel, plugin.ircHeroKick,
                                    Herochat.getChannelManager(),
                                    heroChannel.get(myChannel)));
        }
    }

    /**
     *
     * @param nick
     * @param mode
     * @param myChannel
     */
    public void broadcastIRCMode(String nick, String mode, String myChannel) {
        if (enabledMessages.get(myChannel).contains("irc-mode")) {
            plugin.getServer().broadcast(plugin.tokenizer.ircModeTokenizer(nick, mode,
                    myChannel, plugin.ircMode), "irc.message.mode");
        }
    }

    /**
     *
     * @param nick
     * @param message
     * @param notice
     * @param myChannel
     */
    public void broadcastIRCNotice(String nick, String message, String notice, String myChannel) {
        if (enabledMessages.get(myChannel).contains("irc-notice")) {
            plugin.getServer().broadcast(plugin.tokenizer.ircNoticeTokenizer(nick,
                    message, notice, myChannel, plugin.ircNotice),
                    "irc.message.notice");
        }
    }

    /**
     *
     * @param nick
     * @param myChannel
     */
    public void broadcastIRCJoin(String nick, String myChannel) {
        if (enabledMessages.get(myChannel).contains("irc-join")) {
            plugin.logDebug("[broadcastIRCJoin] Broadcasting join message because irc-join is true.");
            plugin.getServer().broadcast(plugin.tokenizer.chatIRCTokenizer(nick, myChannel, plugin.ircJoin), "irc.message.join");
        } else {
            plugin.logDebug("[broadcastIRCJoin] NOT broadcasting join message because irc-join is false.");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-join")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(plugin.tokenizer.ircChatToHeroChatTokenizer(nick,
                                    myChannel, plugin.ircHeroJoin,
                                    Herochat.getChannelManager(),
                                    heroChannel.get(myChannel)));
        }
    }

    public void broadcastIRCPart(String nick, String myChannel) {
        if (enabledMessages.get(myChannel).contains("irc-part")) {
            plugin.logDebug("[broadcastIRCPart]  Broadcasting join message because irc-part is true.");
            plugin.getServer().broadcast(plugin.tokenizer.chatIRCTokenizer(nick, myChannel, plugin.ircPart), "irc.message.part");
        } else {
            plugin.logDebug("[broadcastIRCPart] NOT broadcasting join message because irc-part is false.");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-part")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(plugin.tokenizer.ircChatToHeroChatTokenizer(nick,
                                    myChannel, plugin.ircHeroPart,
                                    Herochat.getChannelManager(),
                                    heroChannel.get(myChannel)));
        }
    }

    public void broadcastIRCQuit(String nick, String myChannel, String reason) {
        if (enabledMessages.get(myChannel).contains("irc-quit")) {
            plugin.logDebug("[broadcastIRCQuit] Broadcasting quit message because irc-quit is true.");
            plugin.getServer().broadcast(plugin.tokenizer.chatIRCTokenizer(nick,
                    myChannel, plugin.ircQuit)
                    .replace("%NAME%", nick)
                    .replace("%REASON%", reason)
                    .replace("%CHANNEL%", myChannel), "irc.message.quit");
        } else {
            plugin.logDebug("[broadcastIRCQuit] NOT broadcasting quit message because irc-quit is false.");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-quit")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(plugin.tokenizer.ircChatToHeroChatTokenizer(nick,
                                    myChannel, plugin.ircHeroQuit,
                                    Herochat.getChannelManager(),
                                    heroChannel.get(myChannel)));
        }

    }

    // Broadcast topic changes from IRC
    /**
     *
     * @param nick
     * @param myChannel
     * @param message
     */
    public void broadcastIRCTopic(String nick, String myChannel, String message) {
        if (enabledMessages.get(myChannel).contains("irc-topic")) {
            plugin.getServer().broadcast(plugin.tokenizer.chatIRCTokenizer(nick,
                    myChannel, plugin.ircTopic), "irc.message.topic");
        }

        if (enabledMessages.get(myChannel).contains("irc-hero-topic")) {
            Herochat.getChannelManager().getChannel(heroChannel.get(myChannel))
                    .sendRawMessage(plugin.tokenizer.ircChatToHeroChatTokenizer(nick, myChannel,
                                    plugin.ircHeroTopic, message,
                                    Herochat.getChannelManager(),
                                    heroChannel.get(myChannel)));
        }
    }

    // Broadcast disconnect messages from IRC
    /**
     *
     * @param nick
     */
    public void broadcastIRCDisconnect(String nick) {
        plugin.getServer().broadcast("[" + nick + "] Disconnected from IRC server.", "irc.message.disconnect");
    }

    // Broadcast connect messages from IRC
    /**
     *
     * @param nick
     */
    public void broadcastIRCConnect(String nick) {
        plugin.getServer().broadcast("[" + nick + "] Connected to IRC server.", "irc.message.connect");
    }

    // Notify when players use commands
    /**
     *
     * @param player
     * @param cmd
     * @param params
     */
    public void commandNotify(Player player, String cmd, String params) {
        String msg = plugin.tokenizer.gameCommandToIRCTokenizer(player, plugin.gameCommand, cmd, params);
        if (channelCmdNotifyMode.equalsIgnoreCase("msg")) {
            for (String recipient : channelCmdNotifyRecipients) {
                asyncIRCMessage(recipient, msg);
            }
        } else if (channelCmdNotifyMode.equalsIgnoreCase("ctcp")) {
            for (String recipient : channelCmdNotifyRecipients) {
                asyncCTCPMessage(recipient, msg);
            }
        }
    }

    // Notify when player goes AFK
    /**
     *
     * @param player
     * @param afk
     */
    public void essentialsAFK(Player player, boolean afk) {
        if (!bot.isConnected()) {
            return;
        }
        for (String channelName : botChannels) {
            if (enabledMessages.get(channelName).contains("game-afk")) {
                if (!isPlayerInValidWorld(player, channelName)) {
                    return;
                }
                String template;
                if (afk) {
                    template = plugin.playerAFK;
                } else {
                    template = plugin.playerNotAFK;
                }
                plugin.logDebug("Sending AFK message to " + channelName);
                asyncIRCMessage(channelName, plugin.tokenizer.gamePlayerAFKTokenizer(player, template));
            }
        }
    }

    /**
     *
     * @param sender
     * @param nick
     * @param message
     */
    public void msgPlayer(Player sender, String nick, String message) {
        String msg = plugin.tokenizer.gameChatToIRCTokenizer(sender, plugin.gamePChat, message);
        asyncIRCMessage(nick, msg);
    }

    /**
     *
     * @param nick
     * @param message
     */
    public void consoleMsgPlayer(String nick, String message) {
        String msg = plugin.tokenizer.gameChatToIRCTokenizer("console", plugin.consoleChat, message);
        asyncIRCMessage(nick, msg);
    }

    /**
     *
     * @param player
     * @return
     */
    protected String getFactionName(Player player) {
        UPlayer uPlayer = UPlayer.get(player);
        Faction faction = uPlayer.getFaction();
        return faction.getName();
    }

    public boolean isConnected() {
        return bot.isConnected();
    }

    public ImmutableSortedSet<Channel> getChannels() {
        if (bot.getNick().isEmpty()) {
            return ImmutableSortedSet.<Channel>naturalOrder().build();
        }
        return bot.getUserBot().getChannels();
    }

    public long getMessageDelay() {
        return bot.getConfiguration().getMessageDelay();
    }

    public String getMotd() {
        return bot.getServerInfo().getMotd();
    }

    public boolean isValidChannel(String channelName) {
        for (String c : botChannels) {
            if (c.equals(channelName)) {
                return true;
            }
        }
        plugin.logDebug("Channel " + channelName + " is not valid.");
        return false;
    }

    public PircBotX getBot() {
        return bot;
    }
}
