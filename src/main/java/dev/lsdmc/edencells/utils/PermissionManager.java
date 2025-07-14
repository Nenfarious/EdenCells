package dev.lsdmc.edencells.utils;

import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

/**
 * Utility class for permission management
 * Provides clean permission checking methods
 */
public final class PermissionManager {
    
    private PermissionManager() {} // Utility class
    
    /**
     * Check if a player has a specific permission
     * @param player The player to check
     * @param permission The permission node
     * @return true if the player has the permission
     */
    public static boolean hasPermission(Player player, String permission) {
        if (player == null || permission == null) {
            return false;
        }
        
        return player.hasPermission(permission);
    }
    
    /**
     * Check if a permissible has a specific permission
     * @param permissible The permissible to check
     * @param permission The permission node
     * @return true if the permissible has the permission
     */
    public static boolean hasPermission(Permissible permissible, String permission) {
        if (permissible == null || permission == null) {
            return false;
        }
        
        return permissible.hasPermission(permission);
    }
    
    /**
     * Check if a player has admin permissions
     * @param player The player to check
     * @return true if the player is an admin
     */
    public static boolean isAdmin(Player player) {
        return hasPermission(player, Constants.Permissions.ADMIN);
    }
    
    /**
     * Check if a player can bypass restrictions
     * @param player The player to check
     * @return true if the player can bypass
     */
    public static boolean canBypass(Player player) {
        return hasPermission(player, Constants.Permissions.BYPASS);
    }
    
    /**
     * Check if a player has any of the given permissions
     * @param player The player to check
     * @param permissions The permission nodes to check
     * @return true if the player has at least one permission
     */
    public static boolean hasAnyPermission(Player player, String... permissions) {
        if (player == null || permissions == null) {
            return false;
        }
        
        for (String permission : permissions) {
            if (hasPermission(player, permission)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a player has all of the given permissions
     * @param player The player to check
     * @param permissions The permission nodes to check
     * @return true if the player has all permissions
     */
    public static boolean hasAllPermissions(Player player, String... permissions) {
        if (player == null || permissions == null) {
            return false;
        }
        
        for (String permission : permissions) {
            if (!hasPermission(player, permission)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Send no permission message if player lacks permission
     * @param player The player to check
     * @param permission The permission to check
     * @return true if player has permission, false if they don't (and message sent)
     */
    public static boolean checkPermission(Player player, String permission) {
        if (hasPermission(player, permission)) {
            return true;
        }
        
        MessageUtils.sendNoPermission(player);
        return false;
    }
    
    /**
     * Send custom no permission message if player lacks permission
     * @param player The player to check
     * @param permission The permission to check
     * @param message Custom message to send
     * @return true if player has permission, false if they don't (and message sent)
     */
    public static boolean checkPermission(Player player, String permission, String message) {
        if (hasPermission(player, permission)) {
            return true;
        }
        
        MessageUtils.sendError(player, message);
        return false;
    }
}


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT (1).jar!\dev\lsdm\\utils\PermissionManager.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */