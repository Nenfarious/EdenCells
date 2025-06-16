package dev.lsdmc.utils;

import org.bukkit.entity.Player;

/**
 * Utility class for consistent permission handling across the plugin.
 */
public class PermissionManager {
    // Admin permissions
    public static final String ADMIN = "edencells.admin";
    public static final String ADMIN_RESET = "edencells.admin.reset";
    public static final String ADMIN_DOORS = "edencells.admin.doors";
    public static final String ADMIN_GROUPS = "edencells.admin.groups";
    public static final String ADMIN_UNLIMITED = "edencells.admin.unlimited";

    // Door permissions
    public static final String DOOR_OPEN = "edencells.door.open";
    public static final String DOOR_CLOSE = "edencells.door.close";
    public static final String DOOR_LOCATE = "edencells.door.locate";
    public static final String DOOR_INFO = "edencells.door.info";

    // Cell permissions
    public static final String CELL_INFO = "edencells.cell.info";
    public static final String CELL_PURCHASE = "edencells.cell.purchase";
    public static final String CELL_MEMBERS = "edencells.cell.members";
    public static final String CELL_RESET = "edencells.cell.reset";

    // Group permissions
    public static final String GROUP_CREATE = "edencells.group.create";
    public static final String GROUP_DELETE = "edencells.group.delete";
    public static final String GROUP_LIST = "edencells.group.list";

    // Bypass permissions
    public static final String BYPASS_LIMITS = "edencells.bypass.limits";

    /**
     * Checks if a player has a permission and sends a message if they don't
     */
    public static boolean hasPermission(Player player, String permission) {
        if (!player.hasPermission(permission)) {
            player.sendMessage(MessageUtils.noPermission(permission));
            return false;
        }
        return true;
    }

    /**
     * Checks if a player has admin permissions
     */
    public static boolean isAdmin(Player player) {
        return player.hasPermission(ADMIN);
    }

    /**
     * Checks if a player has door admin permissions
     */
    public static boolean isDoorAdmin(Player player) {
        return player.hasPermission(ADMIN_DOORS);
    }

    /**
     * Checks if a player has group admin permissions
     */
    public static boolean isGroupAdmin(Player player) {
        return player.hasPermission(ADMIN_GROUPS);
    }

    /**
     * Checks if a player has reset permissions
     */
    public static boolean canReset(Player player) {
        return player.hasPermission(ADMIN_RESET);
    }

    /**
     * Checks if a player can bypass limits
     */
    public static boolean canBypassLimits(Player player) {
        return player.hasPermission(BYPASS_LIMITS) || player.hasPermission(ADMIN_UNLIMITED);
    }
} 