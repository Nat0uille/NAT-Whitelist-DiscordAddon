package fr.Nat0uille.NATWhitelistDiscordAddon;

import fr.Nat0uille.NATWhitelistDiscordAddon.Discord.SlashCommandListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.build.Commands;


public class DiscordBot {
    private JDA jda;


    public boolean connect(String token) {
        try {
            jda = JDABuilder.createDefault(token)
                    .addEventListeners(new SlashCommandListener())
                    .build();
            jda.awaitReady();

            // Enregistrer la commande slash /test
            jda.updateCommands().addCommands(
                    Commands.slash("test", "Commande de test")
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
