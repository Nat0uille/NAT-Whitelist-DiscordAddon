package fr.Nat0uille.NATWhitelistDiscordAddon;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class Main extends JavaPlugin {

    private FileConfiguration langConfig;

    private DatabaseManager dbManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveLanguage();
        loadLang();

        dbManager = new DatabaseManager();

        String type = getConfig().getString("database.type");
        String host = getConfig().getString("database.host");
        int port = getConfig().getInt("database.port");
        String dbName = getConfig().getString("database.database");
        String username = getConfig().getString("database.username");
        String password = getConfig().getString("database.password");

        boolean connected;
        if ("MySQL".equalsIgnoreCase(type)) {
            connected = dbManager.connectMySQL(host, port, dbName, username, password);
        } else if ("MariaDB".equalsIgnoreCase(type)) {
            connected = dbManager.connectMariaDB(host, port, dbName, username, password);
        }
        else {
            getLogger().severe("Invalid database type!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!connected) {
            getLogger().severe("Unable to connect to the database!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public void onDisable() {
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
            String lang = getConfig().getString("languages", "en-us");
            String notFound = notFoundMessages.getOrDefault(lang, notFoundMessages.get("en-us"));
            return notFound.replace("{key}", key);
        }
        return message;
    }

    private final Map<String, String> notFoundMessages = Map.of(
            "en-us", "Message not found, please check {key} in your language file! (en-us.yml)",
            "fr-fr", "Message introuvable, vérifiez {key} dans votre fichier de langue ! (fr-fr.yml)"
    );

    private boolean saveLanguage() {
        try {
            langConfig.save(new File(getDataFolder(), "languages/" + getConfig().getString("lang") + ".yml"));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
