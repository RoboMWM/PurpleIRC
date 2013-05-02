package me.cnaude.plugin.PurpleIRC;

import Utilities.ColorConverter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author Chris Naudé
 */
public class PIRCMain extends JavaPlugin {

    public static String LOG_HEADER;
    static final Logger log = Logger.getLogger("Minecraft");
    private final String sampleFileName = "SampleBot.yml";
    private File pluginFolder;
    private File botsFolder;
    private File configFile;
    public static long startTime;
    public String gameChat, gameAction, gameDeath, gameQuit, gameJoin, gameKick;
    public String ircChat, ircAction, ircPart, ircKick, ircJoin, ircTopic;
    private boolean debugEnabled;
    private boolean stripGameColors;
    private boolean stripIRCColors;
    Long ircConnCheckInterval;
    BotWatcher botWatcher;
    public ColorConverter colorConverter;
    
    public HashMap<String, PurpleBot> ircBots = new HashMap<String, PurpleBot>();
    public HashMap<String, Boolean> botConnected = new HashMap<String, Boolean>();

    @Override
    public void onEnable() {
        LOG_HEADER = "[" + this.getName() + "]";
        pluginFolder = getDataFolder();
        botsFolder = new File(pluginFolder + "/bots");
        configFile = new File(pluginFolder, "config.yml");
        createConfig();
        getConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(new GameListeners(this), this);
        getCommand("irc").setExecutor(new CommandHandlers(this));
        colorConverter = new ColorConverter(stripGameColors, stripIRCColors);
        loadBots();
        createSampleBot();
        botWatcher = new BotWatcher(this);
    }

    @Override
    public void onDisable() {
        if (ircBots.isEmpty()) {
            logInfo("No IRC bots to disconnect.");
        } else {
            logInfo("Disconnecting IRC bots.");
            for (PurpleBot ircBot : ircBots.values()) {
                ircBot.quit();
            }
        }
        botWatcher.cancel();
    }

    private void loadConfig() {
        debugEnabled = getConfig().getBoolean("Debug");
        stripGameColors = getConfig().getBoolean("strip-game-colors", false);
        stripIRCColors = getConfig().getBoolean("strip-irc-colors", false);
        gameAction = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.game-action", ""));
        gameChat = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.game-chat", ""));
        gameDeath = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.game-death", ""));
        gameJoin = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.game-join", ""));
        gameQuit = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.game-quit", ""));

        ircAction = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.irc-action", ""));
        ircChat = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.irc-chat", ""));
        ircKick = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.irc-kick", ""));
        ircJoin = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.irc-join", ""));
        ircPart = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.irc-part", ""));
        ircTopic = ChatColor.translateAlternateColorCodes('&', getConfig().getString("message-format.irc-topic", ""));

        ircConnCheckInterval = getConfig().getLong("conn-check-interval");
        logDebug("Debug enabled");
    }

    private void loadBots() {
        if (botsFolder.exists()) {
            logInfo("Checking for bot files in " + botsFolder);
            for (final File file : botsFolder.listFiles()) {
                if (file.getName().endsWith(".yml")) {
                    logInfo("Loading bot: " + file.getName());
                    PurpleBot pircBot = new PurpleBot(file, this);
                }
            }
        }
    }

    private void createSampleBot() {
        File file = new File(pluginFolder + "/" + sampleFileName);
        if (!file.exists()) {
            try {
                InputStream in = PIRCMain.class.getResourceAsStream("/me/cnaude/plugin/PurpleIRC/Sample/" + sampleFileName);
                byte[] buf = new byte[1024];
                int len;
                OutputStream out = new FileOutputStream(file);
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.close();
            } catch (Exception ex) {
                logError(ex.getMessage());
            }
        }
    }

    private void createConfig() {
        if (!pluginFolder.exists()) {
            try {
                pluginFolder.mkdir();
            } catch (Exception e) {
                logError(e.getMessage());
            }
        }

        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (Exception e) {
                logError(e.getMessage());
            }
        }

        if (!botsFolder.exists()) {
            try {
                botsFolder.mkdir();
            } catch (Exception e) {
                logError(e.getMessage());
            }
        }
    }

    public void logInfo(String _message) {
        log.log(Level.INFO, String.format("%s %s", LOG_HEADER, _message));
    }

    public void logError(String _message) {
        log.log(Level.SEVERE, String.format("%s %s", LOG_HEADER, _message));
    }

    public void logDebug(String _message) {
        if (debugEnabled) {
            log.log(Level.INFO, String.format("%s [DEBUG] %s", LOG_HEADER, _message));
        }
    }

    public String getMCUptime() {
        long jvmUptime = ManagementFactory.getRuntimeMXBean().getUptime();
        String msg = "Server uptime: " + (int) (jvmUptime / 86400000L) + " days"
                + " " + (int) (jvmUptime / 3600000L % 24L) + " hours"
                + " " + (int) (jvmUptime / 60000L % 60L) + " minutes"
                + " " + (int) (jvmUptime / 1000L % 60L) + " seconds.";
        return msg;
    }

    public String getMCPlayers() {
        String msg = "Players currently online("
                + getServer().getOnlinePlayers().length
                + "/" + getServer().getMaxPlayers() + "): ";
        for (Player p : getServer().getOnlinePlayers()) {
            msg = msg + p.getName() + ", ";
        }
        msg = msg.substring(0, msg.length() - 1);
        return msg;
    }

}