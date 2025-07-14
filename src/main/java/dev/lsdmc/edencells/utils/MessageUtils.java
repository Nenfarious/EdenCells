package dev.lsdmc.edencells.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Utility class for sending formatted messages
 * Uses the plugin's color scheme and Adventure components
 */
public final class MessageUtils {
    
    // Color scheme from Constants
    private static final TextColor PRIMARY_COLOR = TextColor.fromHexString("#9D4EDD"); // Vibrant purple
    private static final TextColor SECONDARY_COLOR = TextColor.fromHexString("#06FFA5"); // Bright cyan
    private static final TextColor ACCENT_COLOR = TextColor.fromHexString("#FFB3C6"); // Soft pink
    private static final TextColor ERROR_COLOR = TextColor.fromHexString("#FF6B6B"); // Coral red
    private static final TextColor SUCCESS_COLOR = TextColor.fromHexString("#51CF66"); // Fresh green
    private static final TextColor NEUTRAL_COLOR = TextColor.fromHexString("#ADB5BD"); // Light gray
    
    private MessageUtils() {} // Utility class
    
    /**
     * Format a title with primary color and bold
     */
    public static Component formatTitle(String text) {
        return Component.text(text)
            .color(PRIMARY_COLOR)
            .decoration(TextDecoration.BOLD, true);
    }
    
    /**
     * Success message in green
     */
    public static Component success(String text) {
        return Component.text(text).color(SUCCESS_COLOR);
    }
    
    /**
     * Error message in red
     */
    public static Component error(String text) {
        return Component.text(text).color(ERROR_COLOR);
    }
    
    /**
     * Warning message in yellow/orange
     */
    public static Component warning(String text) {
        return Component.text(text).color(NamedTextColor.YELLOW);
    }
    
    /**
     * Info message in secondary color
     */
    public static Component info(String text) {
        return Component.text(text).color(SECONDARY_COLOR);
    }
    
    /**
     * Heading with gold color and bold
     */
    public static Component heading(String text) {
        return Component.text(text)
            .color(NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true);
    }
    
    /**
     * Legacy color code support
     */
    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    /**
     * Format currency amount
     */
    public static String formatPrice(double amount, String currency) {
        return String.format("$%.2f %s", amount, currency);
    }
    
    /**
     * Decorative divider
     */
    public static Component divider() {
        return Component.text("──────────────────────────────────")
            .color(PRIMARY_COLOR);
    }
    
    /**
     * No permission message
     */
    public static Component noPermission(String permission) {
        return error("You don't have permission to do this!" + 
                    (permission.isEmpty() ? "" : " (" + permission + ")"));
    }
    
    /**
     * Action message with italic styling
     */
    public static Component actionMessage(String text, NamedTextColor color) {
        return Component.text(text)
            .color(color)
            .decoration(TextDecoration.ITALIC, true);
    }
    
    /**
     * Status indicator with checkmark/X
     */
    public static Component status(String label, boolean isPositive) {
        NamedTextColor color = isPositive ? NamedTextColor.GREEN : NamedTextColor.RED;
        String symbol = isPositive ? "✓" : "✗";
        return Component.text(symbol + " " + label).color(color);
    }
    
    /**
     * Progress indicator
     */
    public static Component progress(String label, int current, int max) {
        NamedTextColor color = (current >= max) ? NamedTextColor.RED : NamedTextColor.GREEN;
        return Component.text(label + ": " + current + "/" + max).color(color);
    }
    
    /**
     * Time remaining display
     */
    public static Component timeRemaining(String timeString) {
        return Component.text("Expires: " + timeString).color(NamedTextColor.YELLOW);
    }
    
    /**
     * Cost display
     */
    public static Component cost(double amount, String currency) {
        return Component.text("Cost: $" + String.format("%.2f", amount) + " " + currency)
            .color(NamedTextColor.GOLD);
    }
    
    /**
     * Balance display with affordability indicator
     */
    public static Component balance(double amount, String currency, boolean sufficient) {
        NamedTextColor color = sufficient ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text("Balance: $" + String.format("%.2f", amount) + " " + currency)
            .color(color);
    }
    
    // Convenience methods for sending messages
    
    /**
     * Send a MiniMessage formatted message
     */
    public static void send(CommandSender sender, String message) {
        sender.sendMessage(fromMiniMessage(message));
    }
    
    /**
     * Send error message to player
     */
    public static void sendError(Player player, String message) {
        player.sendMessage(error(message));
    }
    
    /**
     * Send error message to any command sender
     */
    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(error(message));
    }
    
    /**
     * Send formatted error message to player
     */
    public static void sendError(Player player, String message, Object... args) {
        player.sendMessage(error(String.format(message, args)));
    }
    
    /**
     * Send formatted error message to any command sender
     */
    public static void sendError(CommandSender sender, String message, Object... args) {
        sender.sendMessage(error(String.format(message, args)));
    }
    
    /**
     * Send success message to player
     */
    public static void sendSuccess(Player player, String message) {
        player.sendMessage(success(message));
    }
    
    /**
     * Send success message to any command sender
     */
    public static void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(success(message));
    }
    
    /**
     * Send formatted success message to player
     */
    public static void sendSuccess(Player player, String message, Object... args) {
        player.sendMessage(success(String.format(message, args)));
    }
    
    /**
     * Send formatted success message to any command sender
     */
    public static void sendSuccess(CommandSender sender, String message, Object... args) {
        sender.sendMessage(success(String.format(message, args)));
    }
    
    /**
     * Send info message to player
     */
    public static void sendInfo(Player player, String message) {
        player.sendMessage(info(message));
    }
    
    /**
     * Send formatted info message to player
     */
    public static void sendInfo(Player player, String message, Object... args) {
        player.sendMessage(info(String.format(message, args)));
    }
    
    /**
     * Send info message to any command sender
     */
    public static void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(info(message));
    }
    
    /**
     * Send formatted info message to any command sender
     */
    public static void sendInfo(CommandSender sender, String message, Object... args) {
        sender.sendMessage(info(String.format(message, args)));
    }
    
    /**
     * Send no permission message to player
     */
    public static void sendNoPermission(Player player) {
        player.sendMessage(noPermission(""));
    }
    
    /**
     * Send no permission message to any command sender
     */
    public static void sendNoPermission(CommandSender sender) {
        sender.sendMessage(noPermission(""));
    }
    
    /**
     * Parse MiniMessage format
     */
    public static Component fromMiniMessage(String message) {
        return MiniMessage.miniMessage().deserialize(message);
    }
    
    /**
     * Create a clickable button component
     */
    public static Component button(String text, NamedTextColor color) {
        return Component.text("[ " + text + " ]")
            .color(color)
            .decoration(TextDecoration.BOLD, true);
    }
    
    /**
     * Create highlighted text
     */
    public static Component highlight(String text) {
        return Component.text(text)
            .color(ACCENT_COLOR)
            .decoration(TextDecoration.BOLD, true);
    }
    
    /**
     * Create a prefix for all plugin messages
     */
    public static Component prefix() {
        return Component.text("[")
            .color(NEUTRAL_COLOR)
            .append(Component.text("EdenCells").color(PRIMARY_COLOR))
            .append(Component.text("] ").color(NEUTRAL_COLOR));
    }
    
    /**
     * Send a message with the plugin prefix
     */
    public static void sendPrefixed(CommandSender sender, Component message) {
        sender.sendMessage(prefix().append(message));
    }
}