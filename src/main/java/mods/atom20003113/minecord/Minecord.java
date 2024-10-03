package mods.atom20003113.minecord;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.ServerChatEvent;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class Minecord {
    @SubscribeEvent
    public static final String MODID = "minecord";
    public static void onChat(ServerChatEvent event) {
        execute(event, event.getPlayer().level(), event.getPlayer().getX(), event.getPlayer().getY(), event.getPlayer().getZ(), event.getPlayer(), event.getRawText());
    }

    public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, String text) {
        execute(null, world, x, y, z, entity, text);
    }

    private static void execute(@Nullable Event event, LevelAccessor world, double x, double y, double z, Entity entity, String text) {
        if (entity == null || text == null) return;

        // Cancel the event if it's cancellable
        if (event != null && event.isCancelable()) {
            event.setCanceled(true);
        }

        String font = "";
        String messageText = text;

        // Check for #font=<minecraft font>
        if (text.startsWith("#font=")) {
            int fontEndIndex = text.indexOf(' ', 6);
            if (fontEndIndex != -1) {
                font = text.substring(6, fontEndIndex).trim();
                messageText = text.substring(fontEndIndex + 1).trim();
            } else {
                font = text.substring(6).trim();
                messageText = "";
            }
        }

        // Handle direct messages
        if (messageText.startsWith("dm!")) {
            int spaceIndex = messageText.indexOf(' ', 3);
            if (spaceIndex != -1) {
                String targetPlayerName = messageText.substring(3, spaceIndex);
                String dmMessage = messageText.substring(spaceIndex + 1);
                sendPrivateMessage(world, x, y, z, entity, targetPlayerName, dmMessage);
                return;
            }
        }

        // Split the message to check for pings and format
        String[] words = messageText.split(" ");
        StringBuilder processedMessage = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];

            // Check for titles
            if (word.startsWith("##")) {
                processedMessage.append("{\"text\":\"").append(word.substring(2)).append("\",\"bold\":true,\"color\":\"gold\"}"); // Title formatting
            }
            // Check for links
            else if (word.startsWith("http://") || word.startsWith("https://")) {
                processedMessage.append("{\"text\":\"").append(word).append("\",\"underlined\":true,\"color\":\"blue\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"").append(word).append("\"}}");
            }
            // Check for masked links
            else if (word.startsWith("[") && word.contains("](") && word.endsWith(")")) {
                int linkStart = word.indexOf("[") + 1;
                int linkEnd = word.indexOf("](");
                String displayText = word.substring(linkStart, linkEnd).trim();
                String url = word.substring(linkEnd + 2, word.length() - 1).trim();
                processedMessage.append("{\"text\":\"").append(displayText).append("\",\"underlined\":true,\"color\":\"blue\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"").append(url).append("\"}}");
            }
            // Check for paragraphs
            else if (word.equals("~")) {
                processedMessage.append("{\"text\":\"\\n\"}"); // New line for paragraphs
            }
            // Check for pings
            else if (word.startsWith("@") && word.length() > 1) {
                String pingedUser = word.substring(1); // Remove '@' to get the username
                if (!isPlayerOnline(world, pingedUser)) {
                    // Send message if player is offline
                    sendPlayerNotFoundMessage(world, entity, pingedUser);
                    return; // Stop further processing
                }
                processedMessage.append("{\"text\":\"@").append(pingedUser)
                        .append("\",\"bold\":true,\"color\":\"aqua\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://namemc.com/profile/")
                        .append(pingedUser)
                        .append("\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"Go to ")
                        .append(pingedUser)
                        .append("'s profile\"}}");
            } else {
                // Regular text formatting (including markdown)
                processedMessage.append(formatMarkdown(word));
            }

            // If it's not the last word, append a space after it
            if (i < words.length - 1) {
                processedMessage.append(",{\"text\":\" \"},");
            }
        }

        // Build the complete JSON message
        String jsonMessage = "[\"\","
                + "{\"text\":\"" + entity.getDisplayName().getString()
                + "\",\"bold\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://namemc.com/profile/"
                + entity.getDisplayName().getString()
                + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":\"Go to " + entity.getDisplayName().getString() + "'s profile\"}},"
                + "{\"text\":\": \",\"bold\":true},"  // Always render ": " before the message
                + processedMessage.toString() // Insert the processed message
                + (font.isEmpty() ? "" : ",{\"font\":\"" + font + "\"}") // Apply font if specified
                + "]";

        // Execute the command to send the message
        if (world instanceof ServerLevel _level) {
            _level.getServer().getCommands().performPrefixedCommand(
                    new CommandSourceStack(CommandSource.NULL, new Vec3(x, y, z), Vec2.ZERO, _level, 4, "", Component.literal(""), _level.getServer(), null).withSuppressedOutput(),
                    "/tellraw @a " + jsonMessage
            );
        }
    }

    private static void sendPrivateMessage(LevelAccessor world, double x, double y, double z, Entity sender, String targetPlayerName, String message) {
        // Locate the target player entity
        Entity targetPlayer = ((ServerLevel) world).getServer().getPlayerList().getPlayerByName(targetPlayerName);
        if (targetPlayer != null) {
            // Build the direct message
            String directMessage = "{\"text\":\"dm " + sender.getDisplayName().getString() + ": " + message + "\"}";

            // Send the message only to the target player and the sender
            if (world instanceof ServerLevel _level) {
                _level.getServer().getCommands().performPrefixedCommand(
                        new CommandSourceStack(CommandSource.NULL, new Vec3(x, y, z), Vec2.ZERO, _level, 4, "", Component.literal(""), _level.getServer(), null).withSuppressedOutput(),
                        "/tellraw " + targetPlayerName + " " + directMessage
                );
                // Also send the message to the sender for confirmation
                _level.getServer().getCommands().performPrefixedCommand(
                        new CommandSourceStack(CommandSource.NULL, new Vec3(x, y, z), Vec2.ZERO, _level, 4, "", Component.literal(""), _level.getServer(), null).withSuppressedOutput(),
                        "/tellraw " + sender.getDisplayName().getString() + " " + directMessage
                );
            }
        } else {
            // Send player not found message
            sendPlayerNotFoundMessage(world, sender, targetPlayerName);
        }
    }

    private static void sendPlayerNotFoundMessage(LevelAccessor world, Entity sender, String playerName) {
        String message = "{\"text\":\"Player " + playerName + " not found!\"}";
        if (world instanceof ServerLevel _level) {
            _level.getServer().getCommands().performPrefixedCommand(
                    new CommandSourceStack(CommandSource.NULL, new Vec3(sender.getX(), sender.getY(), sender.getZ()), Vec2.ZERO, _level, 4, "", Component.literal(""), _level.getServer(), null).withSuppressedOutput(),
                    "/tellraw " + sender.getDisplayName().getString() + " " + message
            );
        }
    }

    private static String formatMarkdown(String text) {
        // Example of handling markdown (this method needs to be implemented based on your requirements)
        // Add your markdown formatting logic here (e.g. bold, italic, strikethrough)
        StringBuilder formattedText = new StringBuilder();
        formattedText.append("{\"text\":\"").append(text).append("\"}"); // Placeholder for actual formatting
        return formattedText.toString();
    }

    private static boolean isPlayerOnline(LevelAccessor world, String playerName) {
        Entity player = ((ServerLevel) world).getServer().getPlayerList().getPlayerByName(playerName);
        return player != null;
    }
}
