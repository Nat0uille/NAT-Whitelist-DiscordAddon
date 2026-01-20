package fr.Nat0uille.NATWhitelistDiscordAddon;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

/**
 * Simple wrapper around JDA to manage bot lifecycle (connect / disconnect).
 */
public class DiscordBot {
    private JDA jda;

    /**
     * Connects the bot using the provided token. Blocks until ready.
     * @param token Discord bot token
     * @return true if connected successfully, false otherwise
     */
    public boolean connect(String token) {
        try {
            jda = JDABuilder.createDefault(token).build();
            jda.awaitReady();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gracefully shuts down the bot if running.
     */
    public void disconnect() {
        if (jda != null) {
            try {
                jda.shutdown();
            } catch (Exception ignored) {
            }
            jda = null;
        }
    }

    public JDA getJda() {
        return jda;
    }
}
