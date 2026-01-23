package fr.Nat0uille.NATWhitelistDiscordAddon.Discord;

import fr.Nat0uille.NATWhitelistDiscordAddon.DatabaseManager;
import fr.Nat0uille.NATWhitelistDiscordAddon.Main;
import fr.Nat0uille.NATWhitelistDiscordAddon.MojangAPIManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.Color;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

public class SlashCommandListener extends ListenerAdapter {

    private final DatabaseManager dbManager;
    private final Main plugin;

    public SlashCommandListener(DatabaseManager dbManager, Main plugin) {
        this.dbManager = dbManager;
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("test")) {
            // Utilise le message de langue pour la commande test
            event.reply(plugin.getLangMessage("command.test.success")).setEphemeral(true).queue();
        }

        if (event.getName().equals("link")) {
            handleLinkCommand(event);
        }
    }

    private void handleLinkCommand(SlashCommandInteractionEvent event) {
        
        if (event.getGuild() == null || event.getMember() == null) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangMessage("error.title"))
                    .setDescription(plugin.getLangMessage("error.guild-only"))
                    .setColor(Color.RED);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            return;
        }
        
        boolean roleRequired = plugin.getConfig().getBoolean("discord.role-required", false);
        if (roleRequired) {
            String roleIdStr = plugin.getConfig().getString("discord.role-id", "");
            if (!roleIdStr.isEmpty()) {
                Role role = event.getGuild().getRoleById(roleIdStr);
                if (role != null && !event.getMember().getRoles().contains(role)) {
                    String desc = plugin.getLangMessage("error.role.missing").replace("{role}", role.getAsMention());
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle(plugin.getLangMessage("error.title"))
                            .setDescription(desc)
                            .setColor(Color.RED);
                    event.replyEmbeds(embed.build()).setEphemeral(true).queue();
                    return;
                }
            }
        }

        String discordId = event.getUser().getId();

        try (Connection conn = dbManager.getConnection()) {
            // Vérifier si l'utilisateur est déjà lié
            PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT minecraft_name FROM nat_whitelist_discordaddon WHERE id_discord = ?"
            );
            checkStmt.setString(1, discordId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getString("minecraft_name") != null) {
                String existingName = rs.getString("minecraft_name");
                String desc = plugin.getLangMessage("link.already.linked.description").replace("{minecraft}", existingName);
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(plugin.getLangMessage("link.already.linked.title"))
                        .setDescription(desc)
                        .setColor(Color.ORANGE);
                event.replyEmbeds(embed.build()).setEphemeral(false).queue();
                return;
            }

            // Récupérer le pseudo Minecraft
            OptionMapping pseudoOption = event.getOption("pseudo_minecraft");
            if (pseudoOption == null) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(plugin.getLangMessage("error.title"))
                        .setDescription(plugin.getLangMessage("error.provide-minecraft"))
                        .setColor(Color.RED);
                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
                return;
            }

            String pseudoMinecraft = pseudoOption.getAsString();

            // Vérifier si le pseudo est déjà utilisé
            PreparedStatement checkPseudoStmt = conn.prepareStatement(
                    "SELECT minecraft_name FROM nat_whitelist_discordaddon WHERE minecraft_name = ?"
            );
            checkPseudoStmt.setString(1, pseudoMinecraft);
            ResultSet pseudoRs = checkPseudoStmt.executeQuery();

            if (pseudoRs.next()) {
                String desc = plugin.getLangMessage("error.minecraft-already-used").replace("{minecraft}", pseudoMinecraft);
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(plugin.getLangMessage("error.title"))
                        .setDescription(desc)
                        .setColor(Color.ORANGE);
                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
                return;
            }

            // Récupérer l'UUID depuis l'API Mojang
            UUID minecraftUuid = MojangAPIManager.getUUIDFromUsername(pseudoMinecraft);
            if (minecraftUuid == null) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(plugin.getLangMessage("error.title"))
                        .setDescription(plugin.getLangMessage("error.minecraft-not-found"))
                        .setColor(Color.RED);
                event.replyEmbeds(embed.build()).setEphemeral(false).queue();
                return;
            }

            // Récupérer le pseudo correct (avec la casse)
            String correctUsername = MojangAPIManager.getCorrectUsernameFromMojang(pseudoMinecraft);
            if (correctUsername == null) {
                correctUsername = pseudoMinecraft;
            }

            // Insérer dans la base de données
            PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO nat_whitelist_discordaddon (id_discord, minecraft_name, minecraft_uuid) " +
                            "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE minecraft_name=?, minecraft_uuid=?"
            );
            insertStmt.setString(1, discordId);
            insertStmt.setString(2, correctUsername);
            insertStmt.setString(3, minecraftUuid.toString());
            insertStmt.setString(4, correctUsername);
            insertStmt.setString(5, minecraftUuid.toString());
            insertStmt.executeUpdate();

            // Réponse avec embed de succès
            String avatarUrl = "https://crafatar.com/avatars/" + minecraftUuid.toString().replace("-", "");
            String desc = plugin.getLangMessage("link.success.description")
                    .replace("{discordName}", event.getUser().getName())
                    .replace("{discordId}", discordId)
                    .replace("{minecraft}", correctUsername)
                    .replace("{uuid}", minecraftUuid.toString());
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangMessage("link.success.title"))
                    .setDescription(desc)
                    .setColor(Color.GREEN)
                    .setThumbnail(avatarUrl);
            event.replyEmbeds(embed.build()).setEphemeral(false).queue();

        } catch (Exception e) {
            e.printStackTrace();
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangMessage("error.save-failed.title"))
                    .setDescription(plugin.getLangMessage("error.save-failed.description"))
                    .setColor(Color.RED);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
    }
}
