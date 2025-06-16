package dev.lsdmc.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;

/**
 * Utility class for consistent message formatting across the plugin.
 */
public class MessageUtils {
    private static final TextColor DEFAULT_COLOR = TextColor.color(10040012);
    
    /**
     * Formats a title for GUI menus
     */
    public static Component formatTitle(String text) {
        return Component.text(text).color(DEFAULT_COLOR);
    }

    /**
     * Formats a success message
     */
    public static Component success(String text) {
        return Component.text(text).color(NamedTextColor.GREEN);
    }

    /**
     * Formats an error message
     */
    public static Component error(String text) {
        return Component.text(text).color(NamedTextColor.RED);
    }

    /**
     * Formats a warning message
     */
    public static Component warning(String text) {
        return Component.text(text).color(NamedTextColor.YELLOW);
    }

    /**
     * Formats an info message
     */
    public static Component info(String text) {
        return Component.text(text).color(NamedTextColor.WHITE);
    }

    /**
     * Formats a heading
     */
    public static Component heading(String text) {
        return Component.text(text).color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    /**
     * Formats text with legacy color codes
     */
    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Formats a price with currency
     */
    public static String formatPrice(double amount, String currency) {
        return String.format("$%.2f %s", amount, currency);
    }

    /**
     * Creates a divider line
     */
    public static Component divider() {
        return Component.text("──────────────────────────────────").color(NamedTextColor.DARK_PURPLE);
    }

    /**
     * Formats a permission denied message
     */
    public static Component noPermission(String permission) {
        return error("You don't have permission to do this. (" + permission + ")");
    }

    /**
     * Creates a clickable action message
     */
    public static Component actionMessage(String text, NamedTextColor color) {
        return Component.text(text).color(color).decoration(TextDecoration.ITALIC, true);
    }

    /**
     * Creates a status indicator
     */
    public static Component status(String label, boolean isPositive) {
        NamedTextColor color = isPositive ? NamedTextColor.GREEN : NamedTextColor.RED;
        String symbol = isPositive ? "✓" : "✗";
        return Component.text(label + ": " + symbol).color(color);
    }

    /**
     * Creates a progress indicator
     */
    public static Component progress(String label, int current, int max) {
        NamedTextColor color = current >= max ? NamedTextColor.RED : NamedTextColor.GREEN;
        return Component.text(label + ": " + current + "/" + max).color(color);
    }

    /**
     * Creates a time remaining indicator
     */
    public static Component timeRemaining(String timeString) {
        return Component.text("Expires: " + timeString).color(NamedTextColor.YELLOW);
    }

    /**
     * Creates a cost indicator
     */
    public static Component cost(double amount, String currency) {
        return Component.text("Cost: $" + String.format("%.2f", amount) + " " + currency)
            .color(NamedTextColor.GOLD);
    }

    /**
     * Creates a balance indicator
     */
    public static Component balance(double amount, String currency, boolean sufficient) {
        NamedTextColor color = sufficient ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text("Balance: $" + String.format("%.2f", amount) + " " + currency)
            .color(color);
    }
} 