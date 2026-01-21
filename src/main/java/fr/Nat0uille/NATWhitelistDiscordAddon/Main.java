package fr.Nat0uille.NATWhitelistDiscordAddon;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class Main extends JavaPlugin {

    private FileConfiguration langConfig;

    private DatabaseManager dbManager;

    // Discord bot wrapper
    private DiscordBot discordBot;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveAllLangResources();
        loadLang();

        String token = getConfig().getString("discord-bot-token", "");
        if (token == null || token.isBlank()) {
            getLogger().severe("Discord bot token not found!");
            getLogger().severe("Please set the discord-bot-token in the config.yml file!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dbManager = new DatabaseManager();

        String type = CoreConfigUtil.getCoreConfigString("database.type");
        String host = CoreConfigUtil.getCoreConfigString("database.host");
        int port = CoreConfigUtil.getCoreConfigInt("database.port");
        String dbName = CoreConfigUtil.getCoreConfigString("database.database");
        String username = CoreConfigUtil.getCoreConfigString("database.username");
        String password = CoreConfigUtil.getCoreConfigString("database.password");

        boolean connected;
        if ("MySQL".equalsIgnoreCase(type)) {
            connected = dbManager.connectMySQL(host, port, dbName, username, password);
        } else if ("MariaDB".equalsIgnoreCase(type)) {
            connected = dbManager.connectMariaDB(host, port, dbName, username, password);
        }
        else {
            getLogger().severe("Invalid database type!");
            getLogger().severe(type);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!connected) {
            getLogger().severe("Unable to connect to the database!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dbManager.execute("CREATE TABLE IF NOT EXISTS nat_whitelist_discordaddon (id_discord BIGINT PRIMARY KEY,minecraft_name VARCHAR(255) NOT NULL,minecraft_uuid VARCHAR(255) NOT NULL) CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci; ");

        // Initialize and connect the Discord bot
        discordBot = new DiscordBot(dbManager, this);
        boolean botConnected = discordBot.connect(token);
        if (!botConnected) {
            getLogger().severe("Unable to connect to the Discord bot!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.disconnect();
        }

        if (dbManager != null) {
            dbManager.disconnect();
        }
        getServer().getServicesManager().unregister(this);
    }

    public void loadLang() {
        String lang = getConfig().getString("lang");
        File langFile = new File(getDataFolder(), "languages/" + lang + ".yml");
        if (!langFile.exists()) {
            saveResource("languages/" + lang + ".yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }



    public String getLangMessage(String key) {
        String message = langConfig.getString(key);
        if (message == null) {
            String lang = getConfig().getString("lang", "en-us");
            String notFound = notFoundMessages.getOrDefault(lang, notFoundMessages.get("en-us"));
            return notFound.replace("{key}", key);
        }
        return message;
    }

    private void saveAllLangResources() {
        String[] langs = {"en-us.yml", "fr-fr.yml"};
            File langDir = new File(getDataFolder(), "languages");
        if (!langDir.exists()) langDir.mkdirs();
        for (String langFile : langs) {
            File outFile = new File(langDir, langFile);
            if (!outFile.exists()) {
                saveResource("languages/" + langFile, false);
            }
        }
    }

    private final Map<String, String> notFoundMessages = Map.of(
            "en-us", "Message not found, please check {key} in your language file! (en-us.yml)",
            "fr-fr", "Message introuvable, vérifiez {key} dans votre fichier de langue ! (fr-fr.yml)"
    );

    public class CoreConfigUtil {

        public static String getCoreConfigString(String path) {
            Plugin corePlugin = Bukkit.getPluginManager().getPlugin("NAT-Whitelist");
            if (corePlugin != null) {
                return corePlugin.getConfig().getString(path);
            }
            return null;
        }

        public static int getCoreConfigInt(String path) {
            Plugin corePlugin = Bukkit.getPluginManager().getPlugin("NAT-Whitelist");
            if (corePlugin != null) {
                return corePlugin.getConfig().getInt(path);
            }
            return 0;
        }

        public static boolean getCoreConfigBoolean(String path) {
            Plugin corePlugin = Bukkit.getPluginManager().getPlugin("NAT-Whitelist");
            if (corePlugin != null) {
                return corePlugin.getConfig().getBoolean(path);
            }
            return false;
        }
    }
}
