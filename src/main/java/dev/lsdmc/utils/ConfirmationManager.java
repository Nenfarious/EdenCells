package dev.lsdmc.utils;

import dev.lsdmc.EdenCells;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Enhanced confirmation manager for handling various confirmation dialogs
 * and operations in the plugin.
 */
public class ConfirmationManager implements Listener {
    private final EdenCells plugin;
    private final Map<UUID, PendingConfirmation> pendingConfirmations;
    private static final long DEFAULT_TIMEOUT = 30; // 30 seconds default timeout

    // Confirmation types for better organization
    public enum ConfirmationType {
        RESET_PLAYER("Reset all cells for player", "This will reset ALL cells owned by %s. This cannot be undone!"),
        RESET_REGION("Reset specific region", "This will reset region %s. This cannot be undone!"),
        DELETE_GROUP("Delete cell group", "This will delete group %s and all its cells. This cannot be undone!"),
        BULK_OPERATION("Bulk operation", "This will affect multiple cells. Are you sure?"),
        REMOVE_MEMBER("Remove member", "This will remove %s from the cell. Cost: $%.2f");

        private final String description;
        private final String confirmationMessage;

        ConfirmationType(String description, String confirmationMessage) {
            this.description = description;
            this.confirmationMessage = confirmationMessage;
        }

        public String getDescription() {
            return description;
        }

        public String getConfirmationMessage(Object... args) {
            return String.format(confirmationMessage, args);
        }
    }

    public ConfirmationManager(EdenCells plugin) {
        this.plugin = plugin;
        this.pendingConfirmations = new ConcurrentHashMap<>();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.debug("ConfirmationManager initialized");
    }

    /**
     * Request a confirmation from a player with a custom message and callback
     */
    public void requestConfirmation(Player player, String message, Consumer<Boolean> callback, long timeoutSeconds) {
        UUID playerId = player.getUniqueId();

        cancelExistingConfirmation(playerId);

        // Send confirmation message with formatting
        player.sendMessage(Component.text("──────────────────────────────────").color(NamedTextColor.DARK_PURPLE));
        player.sendMessage(Component.text("Confirmation Required").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("──────────────────────────────────").color(NamedTextColor.DARK_PURPLE));
        player.sendMessage(Component.text(message).color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("──────────────────────────────────").color(NamedTextColor.DARK_PURPLE));
        player.sendMessage(Component.text("Type 'yes' to confirm or 'no' to cancel.").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Expires in " + timeoutSeconds + " seconds.").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("──────────────────────────────────").color(NamedTextColor.DARK_PURPLE));

        // Schedule timeout task
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingConfirmation expired = pendingConfirmations.remove(playerId);
            if (expired != null) {
                expired.callback().accept(false);
                player.sendMessage(Component.text("Confirmation timed out.", NamedTextColor.RED));
            }
        }, timeoutSeconds * 20L);

        pendingConfirmations.put(playerId, new PendingConfirmation(callback, task));
    }

    /**
     * Request a confirmation for a specific operation type
     */
    public boolean requestConfirmation(Player player, ConfirmationType type, String context) {
        String message = switch (type) {
            case RESET_PLAYER -> {
                OfflinePlayer target = Bukkit.getOfflinePlayer(UUID.fromString(context));
                String name = target.getName() != null ? target.getName() : "Unknown";
                yield type.getConfirmationMessage(name);
            }
            case RESET_REGION -> type.getConfirmationMessage(context);
            case DELETE_GROUP -> type.getConfirmationMessage(context);
            case BULK_OPERATION -> type.getConfirmationMessage();
            case REMOVE_MEMBER -> {
                String[] parts = context.split(":");
                if (parts.length == 2) {
                    String memberName = parts[0];
                    double cost = Double.parseDouble(parts[1]);
                    yield type.getConfirmationMessage(memberName, cost);
                }
                yield type.getConfirmationMessage(context, 0.0);
            }
        };

        requestConfirmation(player, message, confirmed -> {
            if (confirmed) {
                switch (type) {
                    case RESET_PLAYER -> {
                        UUID targetUuid = UUID.fromString(context);
                        resetPlayerRegions(player, targetUuid);
                    }
                    case RESET_REGION -> resetRegion(player, context);
                    case DELETE_GROUP -> deleteGroup(player, context);
                    case BULK_OPERATION -> executeBulkOperation(player, context);
                    case REMOVE_MEMBER -> {
                        String[] parts = context.split(":");
                        if (parts.length == 2) {
                            String memberUuid = parts[0];
                            String regionId = parts[1];
                            removeMember(player, regionId, UUID.fromString(memberUuid));
                        }
                    }
                }
            }
        }, DEFAULT_TIMEOUT);

        return false; // Always return false for async confirmation
    }

    /**
     * Handle a player's confirmation response
     */
    public boolean confirm(Player player, boolean confirmed) {
        UUID playerId = player.getUniqueId();
        PendingConfirmation pending = pendingConfirmations.remove(playerId);
        if (pending != null) {
            pending.task().cancel();
            pending.callback().accept(confirmed);
            player.sendMessage(confirmed ?
                    Component.text("Confirmed!", NamedTextColor.GREEN) :
                    Component.text("Cancelled.", NamedTextColor.RED));
            return true;
        }
        return false;
    }

    private void cancelExistingConfirmation(UUID playerId) {
        PendingConfirmation existing = pendingConfirmations.remove(playerId);
        if (existing != null) {
            existing.task().cancel();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelExistingConfirmation(event.getPlayer().getUniqueId());
    }

    /**
     * Reset all regions owned by a specific player
     */
    public void resetPlayerRegions(Player admin, UUID targetPlayerUuid) {
        try {
            AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
            if (arm == null) {
                admin.sendMessage(Component.text("AdvancedRegionMarket not available!", NamedTextColor.RED));
                return;
            }

            // Get all regions owned by the target player
            List<Region> playerRegions = new ArrayList<>();
            for (Region region : arm.getRegionManager()) {
                if (region.isSold() && region.getOwner() != null && targetPlayerUuid.equals(region.getOwner())) {
                    playerRegions.add(region);
                }
            }

            if (playerRegions.isEmpty()) {
                admin.sendMessage(Component.text("Player has no regions to reset.", NamedTextColor.YELLOW));
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerUuid);
            String targetName = target.getName() != null ? target.getName() : "Unknown";

            admin.sendMessage(Component.text("Resetting " + playerRegions.size() + " regions for " + targetName + "...")
                    .color(NamedTextColor.YELLOW));

            int successCount = 0;
            int failureCount = 0;

            for (Region region : playerRegions) {
                try {
                    region.resetRegion(Region.ActionReason.MANUALLY_BY_ADMIN, true);
                    successCount++;
                    admin.sendMessage(Component.text("  ✓ Reset region: " + region.getRegion().getId())
                            .color(NamedTextColor.GREEN));
                } catch (Exception e) {
                    failureCount++;
                    admin.sendMessage(Component.text("  ✗ Failed to reset region: " + region.getRegion().getId()
                            + " (" + e.getMessage() + ")").color(NamedTextColor.RED));
                    plugin.getLogger().log(Level.WARNING, "Failed to reset region " + region.getRegion().getId(), e);
                }
            }

            // Summary message
            if (successCount > 0) {
                admin.sendMessage(Component.text("Successfully reset " + successCount + " regions.")
                        .color(NamedTextColor.GREEN));
            }
            if (failureCount > 0) {
                admin.sendMessage(Component.text("Failed to reset " + failureCount + " regions.")
                        .color(NamedTextColor.RED));
            }

            admin.sendMessage(Component.text("Reset operation completed for " + targetName + ".")
                    .color(NamedTextColor.GOLD));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error resetting player regions", e);
            admin.sendMessage(Component.text("An error occurred while resetting regions.")
                    .color(NamedTextColor.RED));
        }
    }

    /**
     * Reset a specific region
     */
    private void resetRegion(Player admin, String regionId) {
        try {
            AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
            if (arm == null) {
                admin.sendMessage(Component.text("AdvancedRegionMarket not available!", NamedTextColor.RED));
                return;
            }

            Region region = plugin.findRegionById(regionId);
            if (region == null) {
                admin.sendMessage(Component.text("Region not found: " + regionId, NamedTextColor.RED));
                return;
            }

            region.resetRegion(Region.ActionReason.MANUALLY_BY_ADMIN, true);
            admin.sendMessage(Component.text("Successfully reset region: " + regionId, NamedTextColor.GREEN));

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to reset region " + regionId, e);
            admin.sendMessage(Component.text("Failed to reset region: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * Delete a cell group
     */
    private void deleteGroup(Player admin, String groupName) {
        try {
            if (!plugin.getCellGroups().containsKey(groupName)) {
                admin.sendMessage(Component.text("Group not found: " + groupName, NamedTextColor.RED));
                return;
            }

            List<String> regions = plugin.getCellGroups().get(groupName);
            if (regions.isEmpty()) {
                admin.sendMessage(Component.text("No regions in group to delete.", NamedTextColor.YELLOW));
                return;
            }

            int successCount = 0;
            int failureCount = 0;

            for (String regionId : regions) {
                try {
                    Region region = plugin.findRegionById(regionId);
                    if (region != null) {
                        region.delete();
                        successCount++;
                    }
                } catch (Exception e) {
                    failureCount++;
                    plugin.getLogger().log(Level.WARNING, "Failed to delete region " + regionId, e);
                }
            }

            plugin.getCellGroups().remove(groupName);
            plugin.saveCellGroups();

            if (successCount > 0) {
                admin.sendMessage(Component.text("Successfully deleted " + successCount + " regions from group.")
                        .color(NamedTextColor.GREEN));
            }
            if (failureCount > 0) {
                admin.sendMessage(Component.text("Failed to delete " + failureCount + " regions.")
                        .color(NamedTextColor.RED));
            }

            admin.sendMessage(Component.text("Group '" + groupName + "' has been deleted.")
                    .color(NamedTextColor.GOLD));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error deleting group", e);
            admin.sendMessage(Component.text("An error occurred while deleting the group.")
                    .color(NamedTextColor.RED));
        }
    }

    /**
     * Execute a bulk operation
     */
    private void executeBulkOperation(Player admin, String operationData) {
        // Implementation would depend on the specific bulk operation
        admin.sendMessage(Component.text("Bulk operation not yet implemented.", NamedTextColor.YELLOW));
    }

    /**
     * Remove a member from a region
     */
    private void removeMember(Player admin, String regionId, UUID memberUuid) {
        try {
            Region region = plugin.findRegionById(regionId);
            if (region == null) {
                admin.sendMessage(Component.text("Region not found: " + regionId, NamedTextColor.RED));
                return;
            }

            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            String memberName = member.getName() != null ? member.getName() : memberUuid.toString();

            if (!region.getRegion().hasMember(memberUuid)) {
                admin.sendMessage(Component.text(memberName + " is not a member of this region.", NamedTextColor.RED));
                return;
            }

            double cost = plugin.getMemberRemoveCost();
            if (cost > 0) {
                if (!plugin.getEconomy().has(admin, cost)) {
                    admin.sendMessage(Component.text("Insufficient funds. Cost: $" + plugin.getEconomy().format(cost))
                            .color(NamedTextColor.RED));
                    return;
                }
                if (!plugin.getEconomy().withdrawPlayer(admin, cost).transactionSuccess()) {
                    admin.sendMessage(Component.text("Payment failed.", NamedTextColor.RED));
                    return;
                }
                admin.sendMessage(Component.text("$" + plugin.getEconomy().format(cost) + " withdrawn.")
                        .color(NamedTextColor.YELLOW));
            }

            region.getRegion().removeMember(memberUuid);
            region.queueSave();

            admin.sendMessage(Component.text("Removed " + memberName + " from region " + regionId)
                    .color(NamedTextColor.GREEN));

            if (member.isOnline()) {
                Player memberPlayer = member.getPlayer();
                if (memberPlayer != null) {
                    memberPlayer.sendMessage(Component.text("You have been removed from region " + regionId)
                            .color(NamedTextColor.RED));
                }
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to remove member", e);
            admin.sendMessage(Component.text("Failed to remove member: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    /**
     * Record class for pending confirmations
     */
    private record PendingConfirmation(Consumer<Boolean> callback, BukkitTask task) {}
}