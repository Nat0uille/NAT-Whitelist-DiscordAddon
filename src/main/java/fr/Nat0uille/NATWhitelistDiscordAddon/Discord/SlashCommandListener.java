package fr.Nat0uille.NATWhitelistDiscordAddon.Discord;

import fr.Nat0uille.NATWhitelistDiscordAddon.DatabaseManager;
import fr.Nat0uille.NATWhitelistDiscordAddon.Main;
import fr.Nat0uille.NATWhitelistDiscordAddon.MojangAPIManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        if (event.getName().equals("admin_unlink")) {
            handleAdminUnlinkCommand(event);
        }

        if (event.getName().equals("admin_add_to_whitelist")) {
            handleAdminAddToWhitelistCommand(event);
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

        try {
            // Vérifier si l'utilisateur est déjà lié
            List<Map<String, Object>> existingUser = dbManager.select(
                    "nat_whitelist_discordaddon",
                    "id_discord = ?",
                    discordId
            );

            if (!existingUser.isEmpty() && existingUser.get(0).get("minecraft_name") != null) {
                String existingName = (String) existingUser.get(0).get("minecraft_name");
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
            List<Map<String, Object>> existingPseudo = dbManager.select(
                    "nat_whitelist_discordaddon",
                    "minecraft_name = ?",
                    pseudoMinecraft
            );

            if (!existingPseudo.isEmpty()) {
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

            // Insérer ou mettre à jour dans la base de données
            Map<String, Object> data = new HashMap<>();
            data.put("id_discord", Long.parseLong(discordId));
            data.put("minecraft_name", correctUsername);
            data.put("minecraft_uuid", minecraftUuid.toString());

            // Vérifier si l'entrée existe déjà
            List<Map<String, Object>> existing = dbManager.select(
                    "nat_whitelist_discordaddon",
                    "id_discord = ?",
                    Long.parseLong(discordId)
            );

            boolean success;
            if (existing.isEmpty()) {
                // Insérer nouveau
                success = dbManager.insert("nat_whitelist_discordaddon", data);
            } else {
                // Mettre à jour existant
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("minecraft_name", correctUsername);
                updateData.put("minecraft_uuid", minecraftUuid.toString());
                success = dbManager.update(
                        "nat_whitelist_discordaddon",
                        updateData,
                        "id_discord = ?",
                        Long.parseLong(discordId)
                ) > 0;
            }

            if (!success) {
                throw new Exception("Database operation failed");
            }

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

    private void handleAdminUnlinkCommand(SlashCommandInteractionEvent event) {
        // Vérifier les permissions d'administrateur
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangMessage("error.title"))
                    .setDescription(plugin.getLangMessage("admin.unlink.error.permission"))
                    .setColor(Color.RED);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            return;
        }

        // Récupérer l'utilisateur ciblé
        OptionMapping userOption = event.getOption("user");
        if (userOption == null) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangMessage("error.title"))
                    .setDescription("Veuillez spécifier un utilisateur.")
                    .setColor(Color.RED);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            return;
        }

        User targetUser = userOption.getAsUser();
        String targetUserId = targetUser.getId();

        try {
            // Vérifier si l'utilisateur existe dans la base de données
            List<Map<String, Object>> result = dbManager.select(
                    "nat_whitelist_discordaddon",
                    "id_discord = ?",
                    targetUserId
            );

            if (result.isEmpty()) {
                String desc = plugin.getLangMessage("admin.unlink.error.not-found")
                        .replace("{userName}", targetUser.getName());
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(plugin.getLangMessage("error.title"))
                        .setDescription(desc)
                        .setColor(Color.RED);
                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
                return;
            }

            // Supprimer l'utilisateur de la base de données
            int deletedRows = dbManager.delete(
                    "nat_whitelist_discordaddon",
                    "id_discord = ?",
                    targetUserId
            );

            if (deletedRows > 0) {
                String desc = plugin.getLangMessage("admin.unlink.success.description")
                        .replace("{userName}", targetUser.getName());
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(plugin.getLangMessage("admin.unlink.success.title"))
                        .setDescription(desc)
                        .setColor(Color.GREEN);
                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            } else {
                throw new Exception("Delete operation failed");
            }

        } catch (Exception e) {
            e.printStackTrace();
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangMessage("error.save-failed.title"))
                    .setDescription(plugin.getLangMessage("error.save-failed.description"))
                    .setColor(Color.RED);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
    }

    private void handleAdminAddToWhitelistCommand(SlashCommandInteractionEvent event) {
        // Vérifier les permissions d'administrateur
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangMessage("error.title"))
                    .setDescription(plugin.getLangMessage("admin.add-to-whitelist.error.permission"))
                    .setColor(Color.RED);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            return;
        }

        try {
            // Récupérer tous les utilisateurs de la table nat_whitelist_discordaddon
            List<Map<String, Object>> results = dbManager.select(
                    "nat_whitelist_discordaddon",
                    null
            );

            if (results.isEmpty()) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(plugin.getLangMessage("error.title"))
                        .setDescription(plugin.getLangMessage("admin.add-to-whitelist.error.no-users"))
                        .setColor(Color.RED);
                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
                return;
            }

            // Pour chaque utilisateur, l'ajouter à la whitelist
            for (Map<String, Object> row : results) {
                String minecraftName = (String) row.get("minecraft_name");
                String minecraftUuid = (String) row.get("minecraft_uuid");

                if (minecraftName == null || minecraftUuid == null) {
                    continue;
                }

                // Vérifier si l'utilisateur existe déjà dans nat_whitelist
                List<Map<String, Object>> existingWhitelist = dbManager.select(
                        "nat_whitelist",
                        "uuid = ?",
                        minecraftUuid
                );

                if (existingWhitelist.isEmpty()) {
                    // Insérer dans nat_whitelist
                    Map<String, Object> whitelistData = new HashMap<>();
                    whitelistData.put("player_name", minecraftName);
                    whitelistData.put("uuid", minecraftUuid);
                    dbManager.insert("nat_whitelist", whitelistData);
                } else {
                    // Mettre à jour nat_whitelist
                    Map<String, Object> updateData = new HashMap<>();
                    updateData.put("player_name", minecraftName);
                    dbManager.update(
                            "nat_whitelist",
                            updateData,
                            "uuid = ?",
                            minecraftUuid
                    );
                }
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getLangMessage("admin.add-to-whitelist.success.title"))
                    .setDescription(plugin.getLangMessage("admin.add-to-whitelist.success.description"))
                    .setColor(Color.GREEN);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();

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
