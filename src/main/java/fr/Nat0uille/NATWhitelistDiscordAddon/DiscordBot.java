package fr.Nat0uille.NATWhitelistDiscordAddon;

import fr.Nat0uille.NATWhitelistDiscordAddon.Discord.SlashCommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
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

            jda.updateCommands().addCommands(
                    Commands.slash("test", "Commande de test"),
                    Commands.slash("link", "Relie ton compte")
                            .addOption(OptionType.STRING, "pseudo_minecraft", "Ton pseudo Minecraft", true)
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
