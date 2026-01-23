package fr.Nat0uille.NATWhitelistDiscordAddon;

import fr.Nat0uille.NATWhitelistDiscordAddon.Discord.SlashCommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.OptionType;


public class DiscordBot {
    private JDA jda;
    private final DatabaseManager dbManager;
    private final Main plugin;

    public DiscordBot(DatabaseManager dbManager, Main plugin) {
        this.dbManager = dbManager;
        this.plugin = plugin;
    }

    public boolean connect(String token) {
        try {
            jda = JDABuilder.createDefault(token)
                    .addEventListeners(new SlashCommandListener(dbManager, plugin))
                    .build();
            jda.awaitReady();

            jda.getPresence().setActivity(Activity.playing(plugin.getConfig().getString("status")));

            jda.updateCommands().addCommands(
                    Commands.slash("link", plugin.getLangMessage("command.link.description"))
                            .addOption(OptionType.STRING, "minecraft_username", plugin.getLangMessage("command.link.option.pseudo"), true),
                    Commands.slash("admin_unlink", plugin.getLangMessage("command.admin-unlink.description"))
                            .addOption(OptionType.USER, "user", plugin.getLangMessage("command.admin-unlink.option.user"), true),
                    Commands.slash("admin_add_to_whitelist", plugin.getLangMessage("command.admin-add-to-whitelist.description"))
            ).queue();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        if (jda != null) {
            try {
                jda.shutdown();
            } catch (Exception ignored) {
            }
            jda = null;
        }
    }

}
