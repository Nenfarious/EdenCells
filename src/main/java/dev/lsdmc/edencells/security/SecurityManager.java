package dev.lsdmc.edencells.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.utils.Constants;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Handles security validation, rate limiting, and audit logging
 * Thread-safe implementation with proper resource management
 */
public final class SecurityManager {
    
    private final EdenCells plugin;
    private final Pattern regionIdPattern = Pattern.compile(Constants.Validation.REGION_ID_PATTERN);
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Thread-safe rate limiters with auto-cleanup
    private final Map<String, Cache<UUID, Integer>> rateLimiters = new ConcurrentHashMap<>();
    
    // Teleport cooldowns with auto-expiry
    private final Cache<UUID, Long> teleportCooldowns;
    
    public SecurityManager(EdenCells plugin) {
        this.plugin = plugin;
        
        // Initialize rate limiters for different actions using config values
        rateLimiters.put("purchase", createRateLimiter(
            plugin.getConfig().getInt("security.rate-limits.cell-purchase", Constants.RateLimits.PURCHASE_PER_MINUTE)));
        rateLimiters.put("member_add", createRateLimiter(
            plugin.getConfig().getInt("security.rate-limits.member-add", Constants.RateLimits.MEMBER_ADD_PER_MINUTE)));
        rateLimiters.put("member_remove", createRateLimiter(
            plugin.getConfig().getInt("security.rate-limits.member-remove", 5)));
        rateLimiters.put("door_interact", createRateLimiter(
            plugin.getConfig().getInt("security.rate-limits.door-interact", Constants.RateLimits.DOOR_INTERACT_PER_MINUTE)));
        rateLimiters.put("gui_open", createRateLimiter(
            plugin.getConfig().getInt("security.rate-limits.gui-open", Constants.RateLimits.GUI_OPEN_PER_MINUTE)));
        rateLimiters.put("npc_interact", createRateLimiter(
            plugin.getConfig().getInt("security.rate-limits.npc-interact", 10)));
        rateLimiters.put("bulk_add", createRateLimiter(
            plugin.getConfig().getInt("security.rate-limits.bulk-add", Constants.RateLimits.BULK_ADD_PER_MINUTE)));
        
        // Initialize teleport cooldowns with config value
        int cooldownSeconds = plugin.getConfig().getInt("teleportation.cooldown", Constants.RateLimits.NPC_TELEPORT_COOLDOWN_SECONDS);
        this.teleportCooldowns = Caffeine.newBuilder()
            .expireAfterWrite(cooldownSeconds, TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();
    }
    
    /**
     * Creates a rate limiter cache with proper auto-cleanup
     */
    private Cache<UUID, Integer> createRateLimiter(int maxPerMinute) {
        return Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(1000) // Prevent memory leaks
            .build();
    }
    
    /**
     * Validates a region ID for security
     * @param regionId The region ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidRegionId(String regionId) {
        if (regionId == null || regionId.trim().isEmpty()) {
            return false;
        }
        
        // Check length constraints
        if (regionId.length() > Constants.Validation.MAX_REGION_ID_LENGTH) {
            return false;
        }
        
        // Check pattern match
        return regionIdPattern.matcher(regionId.trim()).matches();
    }
    
    /**
     * Validates a username for security
     * @param username The username to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = username.trim();
        
        // Check length constraints
        if (trimmed.length() > Constants.Validation.MAX_USERNAME_LENGTH) {
            return false;
        }
        
        // Basic alphanumeric check with underscores
        return trimmed.matches("^[a-zA-Z0-9_]+$");
    }
    
    /**
     * Validates an economy amount for security
     * @param amount The amount to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidEconomyAmount(double amount) {
        return !Double.isNaN(amount) && 
               !Double.isInfinite(amount) && 
               amount >= 0 && 
               amount <= Constants.Validation.MAX_ECONOMY_AMOUNT;
    }
    
    /**
     * Checks if a player is rate limited for an action
     * @param player The player to check
     * @param action The action being performed
     * @return true if rate limited, false otherwise
     */
    public boolean isRateLimited(Player player, String action) {
        if (player == null || action == null) {
            return true; // Fail safe
        }
        
        // Check for general bypass permission
        if (player.hasPermission(Constants.Permissions.BYPASS) || 
            player.hasPermission(Constants.Permissions.BYPASS_RATE_LIMIT)) {
            return false;
        }
        
        // Check for specific action bypass permissions
        String specificBypass = getBypassPermissionForAction(action);
        if (specificBypass != null && player.hasPermission(specificBypass)) {
            return false;
        }
        
        Cache<UUID, Integer> limiter = rateLimiters.get(action);
        if (limiter == null) {
            return false; // No rate limit configured for this action
        }
        
        UUID playerId = player.getUniqueId();
        int currentCount = limiter.get(playerId, key -> 0);
        int maxAllowed = getMaxForAction(action);
        
        if (currentCount >= maxAllowed) {
            return true;
        }
        
        // Increment counter
        limiter.put(playerId, currentCount + 1);
        return false;
    }
    
    /**
     * Gets the specific bypass permission for an action
     */
    private String getBypassPermissionForAction(String action) {
        return switch (action) {
            case "purchase" -> Constants.Permissions.BYPASS_RATE_LIMIT_PURCHASE;
            case "member_add", "member_remove" -> Constants.Permissions.BYPASS_RATE_LIMIT_MEMBER;
            case "door_interact" -> Constants.Permissions.BYPASS_RATE_LIMIT_DOOR;
            case "gui_open" -> Constants.Permissions.BYPASS_RATE_LIMIT_GUI;
            case "npc_interact" -> Constants.Permissions.BYPASS_RATE_LIMIT_NPC;
            default -> null;
        };
    }
    
    /**
     * Gets the maximum allowed actions per minute for an action type
     */
    private int getMaxForAction(String action) {
        return switch (action) {
            case "purchase" -> plugin.getConfig().getInt("security.rate-limits.cell-purchase", Constants.RateLimits.PURCHASE_PER_MINUTE);
            case "member_add" -> plugin.getConfig().getInt("security.rate-limits.member-add", Constants.RateLimits.MEMBER_ADD_PER_MINUTE);
            case "member_remove" -> plugin.getConfig().getInt("security.rate-limits.member-remove", 5);
            case "door_interact" -> plugin.getConfig().getInt("security.rate-limits.door-interact", Constants.RateLimits.DOOR_INTERACT_PER_MINUTE);
            case "gui_open" -> plugin.getConfig().getInt("security.rate-limits.gui-open", Constants.RateLimits.GUI_OPEN_PER_MINUTE);
            case "npc_interact" -> plugin.getConfig().getInt("security.rate-limits.npc-interact", 10);
            case "bulk_add" -> plugin.getConfig().getInt("security.rate-limits.bulk-add", Constants.RateLimits.BULK_ADD_PER_MINUTE);
            default -> 10; // Default fallback
        };
    }
    
    /**
     * Checks if a player is on teleport cooldown
     * @param player The player to check
     * @return true if on cooldown, false otherwise
     */
    public boolean isOnTeleportCooldown(Player player) {
        if (player == null) return true;
        
        // Check bypass permissions
        if (player.hasPermission(Constants.Permissions.BYPASS) || 
            player.hasPermission(Constants.Permissions.BYPASS_COOLDOWN)) {
            return false;
        }
        
        return teleportCooldowns.getIfPresent(player.getUniqueId()) != null;
    }
    
    /**
     * Sets a teleport cooldown for a player
     * @param player The player to set cooldown for
     */
    public void setTeleportCooldown(Player player) {
        if (player == null) return;
        
        teleportCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    /**
     * Gets the remaining teleport cooldown for a player in seconds
     * @param player The player to check
     * @return Remaining seconds, 0 if no cooldown
     */
    public int getRemainingTeleportCooldown(Player player) {
        if (player == null) return 0;
        
        Long cooldownTime = teleportCooldowns.getIfPresent(player.getUniqueId());
        if (cooldownTime == null) {
            return 0;
        }
        
        long elapsed = System.currentTimeMillis() - cooldownTime;
        int cooldownSeconds = plugin.getConfig().getInt("teleportation.cooldown", Constants.RateLimits.NPC_TELEPORT_COOLDOWN_SECONDS);
        long remaining = (cooldownSeconds * 1000L) - elapsed;
        
        return Math.max(0, (int) (remaining / 1000));
    }
    
    /**
     * Logs an action to the audit log
     * @param player The player performing the action
     * @param action The action being performed
     * @param target The target of the action (can be null)
     * @param details Additional details (can be null)
     */
    public void auditLog(Player player, String action, String target, String details) {
        if (player == null || action == null) {
            return;
        }
        
        // Check if audit logging is enabled
        if (!plugin.getConfig().getBoolean("security.audit.enabled", true)) {
            return;
        }
        
        // Check if this action should be logged
        List<String> logActions = plugin.getConfig().getStringList("security.audit.log-actions");
        if (!logActions.isEmpty() && !logActions.contains(action.toLowerCase())) {
            return;
        }
        
        // Run audit logging async to prevent blocking
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String logFileName = plugin.getConfig().getString("security.audit.log-file", Constants.Storage.AUDIT_LOG_FILE);
                File auditFile = new File(plugin.getDataFolder(), logFileName);
                
                // Ensure parent directory exists
                if (!auditFile.getParentFile().exists()) {
                    auditFile.getParentFile().mkdirs();
                }
                
                String timestamp = LocalDateTime.now().format(dateFormat);
                String playerName = sanitizeInput(player.getName());
                String actionSafe = sanitizeInput(action);
                String targetSafe = target != null ? sanitizeInput(target) : "N/A";
                String detailsSafe = details != null ? sanitizeInput(details) : "N/A";
                
                String logEntry = String.format("[%s] Player: %s | Action: %s | Target: %s | Details: %s%n",
                    timestamp, playerName, actionSafe, targetSafe, detailsSafe);
                
                
                Files.write(auditFile.toPath(), logEntry.getBytes(), 
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write audit log: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("Unexpected error in audit logging: " + e.getMessage());
            }
        });
    }
    
    /**
     * Sanitizes user input to prevent injection attacks
     * @param input The input to sanitize
     * @return Sanitized input
     */
    public String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        
        // Remove potentially dangerous characters
        return input.replaceAll("[\\r\\n\\t]", " ")
                   .replaceAll("[<>\"'&]", "")
                   .trim();
    }
    
    /**
     * Validates that a permissible object has the required permission
     * @param permissible The permissible to check
     * @param permission The permission to check
     * @return true if has permission, false otherwise
     */
    public boolean hasPermission(Permissible permissible, String permission) {
        if (permissible == null || permission == null) {
            return false;
        }
        
        return permissible.hasPermission(permission);
    }
    
    /**
     * Cleans up rate limiters - called periodically
     * This is already handled by Caffeine's auto-expiry, but can be called manually
     */
    public void cleanupRateLimits() {
        // Caffeine handles cleanup automatically, but we can force cleanup
        rateLimiters.values().forEach(cache -> cache.cleanUp());
        teleportCooldowns.cleanUp();
    }
    
    /**
     * Clears rate limits for a specific player
     * @param player The player to clear limits for
     */
    public void clearRateLimits(Player player) {
        if (player == null) return;
        
        UUID playerId = player.getUniqueId();
        
        // Clear from all rate limiters
        for (Cache<UUID, Integer> limiter : rateLimiters.values()) {
            limiter.invalidate(playerId);
        }
        
        // Clear teleport cooldown
        teleportCooldowns.invalidate(playerId);
    }
    
    /**
     * Gets statistics about the current rate limiters
     * @return A string with statistics
     */
    public String getRateLimitStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Rate Limiter Statistics:\n");
        
        for (var entry : rateLimiters.entrySet()) {
            long size = entry.getValue().estimatedSize();
            stats.append(String.format("- %s: %d active entries\n", entry.getKey(), size));
        }
        
        stats.append(String.format("- teleport_cooldowns: %d active entries\n", 
            teleportCooldowns.estimatedSize()));
        
        return stats.toString();
    }
} 