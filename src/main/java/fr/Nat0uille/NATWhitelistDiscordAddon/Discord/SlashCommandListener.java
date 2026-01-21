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
            event.reply("✅ La commande test fonctionne !").setEphemeral(true).queue();
        }

        if (event.getName().equals("link")) {
            handleLinkCommand(event);
        }
    }

    private void handleLinkCommand(SlashCommandInteractionEvent event) {
        
        if (event.getGuild() == null || event.getMember() == null) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("⚠️ Erreur")
                    .setDescription("Cette commande doit être exécutée dans un serveur Discord.")
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
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("⚠️ Erreur")
                            .setDescription("Tu dois avoir le rôle " + role.getAsMention() + " pour utiliser cette commande.")
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
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("⚠️ Déjà lié")
                        .setDescription("Tu as déjà lié ton pseudo Minecraft `" + existingName +
                                "` à ton compte Discord.\nContacte un admin pour modifier.")
                        .setColor(Color.ORANGE);
                event.replyEmbeds(embed.build()).setEphemeral(false).queue();
                return;
            }

            // Récupérer le pseudo Minecraft
            OptionMapping pseudoOption = event.getOption("pseudo_minecraft");
            if (pseudoOption == null) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("⚠️ Erreur")
                        .setDescription("Veuillez fournir un pseudo Minecraft.")
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
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("⚠️ Erreur")
                        .setDescription("Le pseudo Minecraft `" + pseudoMinecraft +
                                "` est déjà lié à un compte Discord.")
                        .setColor(Color.ORANGE);
                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
                return;
            }

            // Récupérer l'UUID depuis l'API Mojang
            UUID minecraftUuid = MojangAPIManager.getUUIDFromUsername(pseudoMinecraft);
            if (minecraftUuid == null) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("⚠️ Erreur")
                        .setDescription("Erreur lors de la récupération du pseudo Minecraft : Ton pseudo n'existe pas")
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
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Succès")
                    .setDescription("Enregistrement effectué !\n" +
                            "**Pseudo Discord**: " + event.getUser().getName() + "\n" +
                            "**ID Discord**: `" + discordId + "`\n" +
                            "**Pseudo Minecraft**: `" + correctUsername + "`\n" +
                            "**UUID Minecraft**: `" + minecraftUuid + "`")
                    .setColor(Color.GREEN)
                    .setThumbnail(avatarUrl);
            event.replyEmbeds(embed.build()).setEphemeral(false).queue();

        } catch (Exception e) {
            e.printStackTrace();
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("❌ Erreur")
                    .setDescription("Une erreur est survenue lors de l'enregistrement.")
                    .setColor(Color.RED);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
    }
}
