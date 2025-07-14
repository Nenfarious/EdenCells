package dev.lsdmc.edencells.commands;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.managers.SyncManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import dev.lsdmc.edencells.utils.PermissionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles security-related commands
 * Manages rate limits, audit logs, and security settings
 */
public final class SecurityCommands implements CommandExecutor, TabCompleter {
    
    private final EdenCells plugin;
    
    public SecurityCommands(EdenCells plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
            MessageUtils.sendNoPermission(sender);
            return true;
        }
        
        if (args.length == 0) {
            sendSecurityHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "ratelimits":
                return handleRateLimits(sender, args);
            case "audit":
                return handleAudit(sender, args);
            case "clearrate":
                return handleClearRate(sender, args);
            case "validation":
                return handleValidation(sender, args);
            case "sync":
                return handleSync(sender, args);
            case "help":
                sendSecurityHelp(sender);
                return true;
            default:
                MessageUtils.sendError(sender, "Unknown subcommand: " + subCommand);
                sendSecurityHelp(sender);
                return true;
        }
    }
    
    private boolean handleRateLimits(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // List all rate limits
            MessageUtils.sendInfo(sender, "=== Rate Limits (per minute) ===");
            MessageUtils.sendInfo(sender, "• Cell Purchase: %d", 
                plugin.getConfig().getInt("security.rate-limits.cell-purchase", 3));
            MessageUtils.sendInfo(sender, "• Member Add: %d", 
                plugin.getConfig().getInt("security.rate-limits.member-add", 5));
            MessageUtils.sendInfo(sender, "• Member Remove: %d", 
                plugin.getConfig().getInt("security.rate-limits.member-remove", 5));
            MessageUtils.sendInfo(sender, "• Door Interact: %d", 
                plugin.getConfig().getInt("security.rate-limits.door-interact", 20));
            MessageUtils.sendInfo(sender, "• GUI Open: %d", 
                plugin.getConfig().getInt("security.rate-limits.gui-open", 10));
            MessageUtils.sendInfo(sender, "• NPC Interact: %d", 
                plugin.getConfig().getInt("security.rate-limits.npc-interact", 10));
            return true;
        }
        
        if (args.length == 3 && args[1].equalsIgnoreCase("set")) {
            String action = args[2];
            if (args.length < 4) {
                MessageUtils.sendError(sender, "Usage: /esecurity ratelimits set <action> <limit>");
                return true;
            }
            
            try {
                int limit = Integer.parseInt(args[3]);
                plugin.getConfig().set("security.rate-limits." + action, limit);
                plugin.saveConfig();
                MessageUtils.sendSuccess(sender, "Set rate limit for '%s' to %d per minute", action, limit);
            } catch (NumberFormatException e) {
                MessageUtils.sendError(sender, "Invalid number: " + args[3]);
            }
            return true;
        }
        
        MessageUtils.sendError(sender, "Usage: /esecurity ratelimits [set <action> <limit>]");
        return true;
    }
    
    private boolean handleAudit(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Show audit status
            boolean enabled = plugin.getConfig().getBoolean("security.audit.enabled", true);
            String logFile = plugin.getConfig().getString("security.audit.log-file", "logs/audit.log");
            List<String> actions = plugin.getConfig().getStringList("security.audit.log-actions");
            
            MessageUtils.sendInfo(sender, "=== Audit Settings ===");
            MessageUtils.sendInfo(sender, "• Enabled: %s", enabled ? "Yes" : "No");
            MessageUtils.sendInfo(sender, "• Log File: %s", logFile);
            MessageUtils.sendInfo(sender, "• Logged Actions: %s", 
                actions.isEmpty() ? "All" : String.join(", ", actions));
            return true;
        }
        
        if (args.length >= 2) {
            String option = args[1].toLowerCase();
            switch (option) {
                case "enable":
                    plugin.getConfig().set("security.audit.enabled", true);
                    plugin.saveConfig();
                    MessageUtils.sendSuccess(sender, "Audit logging enabled");
                    return true;
                case "disable":
                    plugin.getConfig().set("security.audit.enabled", false);
                    plugin.saveConfig();
                    MessageUtils.sendSuccess(sender, "Audit logging disabled");
                    return true;
                case "view":
                    // Would implement viewing recent audit logs
                    MessageUtils.sendInfo(sender, "Recent audit entries would be shown here");
                    return true;
            }
        }
        
        MessageUtils.sendError(sender, "Usage: /esecurity audit [enable|disable|view]");
        return true;
    }
    
    private boolean handleClearRate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendError(sender, "Usage: /esecurity clearrate <player>");
            return true;
        }
        
        String playerName = args[1];
        Player target = plugin.getServer().getPlayer(playerName);
        
        if (target == null) {
            MessageUtils.sendError(sender, "Player '%s' not found!", playerName);
            return true;
        }
        
        plugin.getSecurityManager().clearRateLimits(target);
        MessageUtils.sendSuccess(sender, "Cleared rate limits for %s", playerName);
        
        return true;
    }
    
    private boolean handleValidation(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Show validation settings
            MessageUtils.sendInfo(sender, "=== Validation Settings ===");
            MessageUtils.sendInfo(sender, "• Max Region ID Length: %d", 
                plugin.getConfig().getInt("security.validation.max-region-id-length", 32));
            MessageUtils.sendInfo(sender, "• Region ID Pattern: %s", 
                plugin.getConfig().getString("security.validation.region-id-pattern", "^[a-zA-Z0-9_-]+$"));
            MessageUtils.sendInfo(sender, "• Max Transaction: %s", 
                plugin.formatCurrency(plugin.getConfig().getDouble("security.validation.max-transaction", 1000000.0)));
            return true;
        }
        
        MessageUtils.sendError(sender, "Use /econfig to modify validation settings");
        return true;
    }
    
    private boolean handleSync(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Show sync status and options
            SyncManager syncManager = plugin.getSyncManager();
            Map<String, Object> lastStats = syncManager.getLastSyncStats();
            long lastSyncTime = syncManager.getLastSyncTime();
            
            MessageUtils.send(sender, "<color:#9D4EDD>=== ARM Data Synchronization ===</color>");
            MessageUtils.send(sender, "");
            
            if (lastSyncTime > 0) {
                long timeSince = System.currentTimeMillis() - lastSyncTime;
                long minutes = timeSince / (1000 * 60);
                MessageUtils.send(sender, "<color:#FFB3C6>Last Sync:</color> <color:#06FFA5>" + minutes + " minutes ago</color>");
                
                if (!lastStats.isEmpty()) {
                    MessageUtils.send(sender, "<color:#FFB3C6>Last Results:</color>");
                    MessageUtils.send(sender, "  <color:#51CF66>Valid regions:</color> <color:#FFB3C6>" + lastStats.get("validRegions") + "</color>");
                    MessageUtils.send(sender, "  <color:#FF6B6B>Invalid regions:</color> <color:#FFB3C6>" + lastStats.get("invalidRegions") + "</color>");
                    MessageUtils.send(sender, "  <color:#FF6B6B>Orphaned doors:</color> <color:#FFB3C6>" + lastStats.get("orphanedDoors") + "</color>");
                    MessageUtils.send(sender, "  <color:#51CF66>Door ownerships synced:</color> <color:#FFB3C6>" + lastStats.getOrDefault("syncedDoorOwnerships", 0) + "</color>");
                    MessageUtils.send(sender, "  <color:#FFB3C6>Ownership issues:</color> <color:#FFB3C6>" + lastStats.get("fixedOwnership") + "</color>");
                    MessageUtils.send(sender, "  <color:#FFB3C6>Missing groups:</color> <color:#FFB3C6>" + lastStats.getOrDefault("missingGroups", 0) + "</color>");
                }
            } else {
                MessageUtils.send(sender, "<color:#ADB5BD>No synchronization has been performed yet</color>");
            }
            
            MessageUtils.send(sender, "");
            MessageUtils.send(sender, "<color:#51CF66>Available Commands:</color>");
            MessageUtils.send(sender, "<color:#FFB3C6>/esecurity sync full</color> <color:#06FFA5>- Full synchronization (fixes issues)</color>");
            MessageUtils.send(sender, "<color:#FFB3C6>/esecurity sync check</color> <color:#06FFA5>- Quick check (read-only)</color>");
            MessageUtils.send(sender, "<color:#FFB3C6>/esecurity sync status</color> <color:#06FFA5>- Show this status</color>");
            
            return true;
        }
        
        String syncType = args[1].toLowerCase();
        SyncManager syncManager = plugin.getSyncManager();
        
        switch (syncType) {
            case "full":
                MessageUtils.send(sender, "<color:#9D4EDD>Starting full ARM data synchronization...</color>");
                MessageUtils.send(sender, "<color:#ADB5BD>This may take a moment and will fix any issues found.</color>");
                
                // Perform full sync asynchronously
                syncManager.performFullSync(sender).thenAccept(result -> {
                    // Additional logging for console
                    if (result.hasErrors()) {
                        plugin.getLogger().warning("Sync completed with " + result.getErrors().size() + " issues:");
                        for (String error : result.getErrors()) {
                            plugin.getLogger().warning("  - " + error);
                        }
                    }
                }).exceptionally(throwable -> {
                    MessageUtils.sendError(sender, "Sync failed: " + throwable.getMessage());
                    plugin.getLogger().severe("Sync failed: " + throwable.getMessage());
                    throwable.printStackTrace();
                    return null;
                });
                
                return true;
                
            case "check":
                MessageUtils.send(sender, "<color:#9D4EDD>Performing quick sync check...</color>");
                MessageUtils.send(sender, "<color:#ADB5BD>This is read-only and won't fix any issues.</color>");
                
                // Perform quick check asynchronously
                syncManager.performQuickCheck().thenAccept(result -> {
                    MessageUtils.send(sender, "<color:#9D4EDD>=== Quick Sync Check Results ===</color>");
                    MessageUtils.send(sender, "");
                    MessageUtils.send(sender, "<color:#51CF66>✓ Valid regions:</color> <color:#FFB3C6>" + result.getValidRegions() + "</color>");
                    
                    if (result.getInvalidRegions() > 0) {
                        MessageUtils.send(sender, "<color:#FF6B6B>✗ Invalid regions found:</color> <color:#FFB3C6>" + result.getInvalidRegions() + "</color>");
                        MessageUtils.send(sender, "<color:#ADB5BD>Run '/esecurity sync full' to fix these issues</color>");
                    } else {
                        MessageUtils.send(sender, "<color:#51CF66>✓ No issues detected!</color>");
                    }
                    
                    MessageUtils.send(sender, "");
                    MessageUtils.send(sender, "<color:#ADB5BD>Check completed in " + result.getSyncTime() + "ms</color>");
                }).exceptionally(throwable -> {
                    MessageUtils.sendError(sender, "Quick check failed: " + throwable.getMessage());
                    return null;
                });
                
                return true;
                
            case "status":
                // Same as no arguments
                return handleSync(sender, new String[]{"sync"});
                
            default:
                MessageUtils.sendError(sender, "Unknown sync type: " + syncType);
                MessageUtils.send(sender, "<color:#ADB5BD>Use: full, check, or status</color>");
                return true;
        }
    }
    
    private void sendSecurityHelp(CommandSender sender) {
        MessageUtils.send(sender, "<color:#9D4EDD>=== Security Commands ===</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/esecurity ratelimits</color> <color:#06FFA5>- View rate limits</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/esecurity ratelimits set <action> <limit></color> <color:#06FFA5>- Set rate limit</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/esecurity audit [enable|disable|view]</color> <color:#06FFA5>- Manage audit logging</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/esecurity clearrate <player></color> <color:#06FFA5>- Clear player's rate limits</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/esecurity validation</color> <color:#06FFA5>- View validation settings</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/esecurity sync</color> <color:#06FFA5>- Sync data</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/esecurity help</color> <color:#06FFA5>- Show this help</color>");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            return Arrays.asList("ratelimits", "audit", "clearrate", "validation", "sync", "help")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("ratelimits")) {
                return Collections.singletonList("set");
            } else if (subCommand.equals("audit")) {
                return Arrays.asList("enable", "disable", "view")
                    .stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("clearrate")) {
                // Return online player names
                return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            } else if (subCommand.equals("sync")) {
                return Arrays.asList("full", "check", "status")
                    .stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("ratelimits") && args[1].equalsIgnoreCase("set")) {
            return Arrays.asList("cell-purchase", "member-add", "member-remove", 
                "door-interact", "gui-open", "npc-interact")
                .stream()
                .filter(s -> s.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
} 