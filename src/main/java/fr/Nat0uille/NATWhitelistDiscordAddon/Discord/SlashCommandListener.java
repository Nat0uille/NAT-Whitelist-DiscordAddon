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
}
