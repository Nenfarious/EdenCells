package dev.lsdmc.edencells.managers;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.models.CellGroup;
import dev.lsdmc.edencells.models.CellGroupManager;
import dev.lsdmc.edencells.security.SecurityManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.alex9849.arm.regions.RentRegion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages all cell-related operations with enhanced safety and error handling
 */
public final class CellManager {
    
    private final EdenCells plugin;
    private final AdvancedRegionMarket arm;
    private final Economy economy;
    private final SecurityManager security;
    
    public CellManager(EdenCells plugin, AdvancedRegionMarket arm, Economy economy, SecurityManager security) {
        this.plugin = plugin;
        this.arm = arm;
        this.economy = economy;
        this.security = security;
        
        // Validate critical dependencies
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null");
        }
        if (arm == null) {
            throw new IllegalArgumentException("AdvancedRegionMarket cannot be null");
        }
        if (security == null) {
            throw new IllegalArgumentException("SecurityManager cannot be null");
        }
    }
    
    /**
     * Get a cell by ID with enhanced validation
     */
    public Region getCell(String cellId, World world) {
        if (cellId == null || cellId.trim().isEmpty()) {
            plugin.debug("getCell called with null or empty cellId");
            return null;
        }
        
        if (world == null) {
            plugin.debug("getCell called with null world");
            return null;
        }
        
        try {
            String trimmedId = cellId.trim();
            for (Region region : arm.getRegionManager()) {
                if (region != null && 
                    region.getRegion() != null && 
                    region.getRegion().getId().equalsIgnoreCase(trimmedId) &&
                    region.getRegionworld().equals(world)) {
                return region;
            }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting cell '" + cellId + "': " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get all cells owned by a player with null safety
     */
    public List<Region> getPlayerCells(OfflinePlayer player) {
        if (player == null) {
            return Collections.emptyList();
        }
        
        try {
            return arm.getRegionManager()
                .getRegionsByOwner(player.getUniqueId())
            .stream()
                .filter(Objects::nonNull)
            .collect(Collectors.toList());
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting cells for player " + player.getName() + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get player cells in a specific region/world with enhanced filtering
     */
    public List<Region> getPlayerCellsInRegion(OfflinePlayer player, String worldName, String regionName) {
        if (player == null || worldName == null || regionName == null) {
            return Collections.emptyList();
        }
        
        try {
            return getPlayerCells(player).stream()
                .filter(region -> region != null && 
                         region.getRegion() != null &&
                         region.getRegionworld().getName().equalsIgnoreCase(worldName.trim()) &&
                         region.getRegion().getId().toLowerCase().contains(regionName.toLowerCase().trim()))
            .collect(Collectors.toList());
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting player cells in region: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Check if player has access to a cell with comprehensive validation
     */
    public boolean hasAccess(Player player, Region cell) {
        if (player == null || cell == null) {
            return false;
        }
        
        try {
            UUID playerId = player.getUniqueId();
            
            // Check if player is owner
            if (cell.getRegion().hasOwner(playerId)) {
                return true;
            }
            
            // Check if player is member
            if (cell.getRegion().hasMember(playerId)) {
                return true;
            }
            
            // Check bypass permission
            if (player.hasPermission(Constants.Permissions.BYPASS)) {
            return true;
        }
        
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking access for player " + player.getName() + ": " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Check if player owns a cell
     */
    public boolean isOwner(OfflinePlayer player, Region cell) {
        if (player == null || cell == null) {
            return false;
        }
        
        try {
            return cell.getRegion().hasOwner(player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking ownership: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a cell is sold/rented
     */
    public boolean isSold(Region cell) {
        if (cell == null) {
            return false;
        }
        
        try {
            return cell.isSold();
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking if cell is sold: " + e.getMessage());
        return false;
        }
    }
    
    /**
     * Get the price of a cell safely
     */
    public double getPrice(Region cell) {
        if (cell == null) {
            return 0.0;
        }
        
        try {
            if (cell instanceof RentRegion rentRegion) {
            return rentRegion.getPricePerPeriod();
            }
            return cell.getPricePerPeriod(); // Using getPricePerPeriod for consistency
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting cell price: " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Add a member to a cell with comprehensive validation
     */
    public boolean addMember(Region cell, Player player, String targetName) {
        // Validate inputs
        if (cell == null || player == null || targetName == null || targetName.trim().isEmpty()) {
            if (player != null) {
                MessageUtils.sendError(player, "Invalid parameters for adding member.");
            }
            return false;
        }
        
        // Security checks
        if (!security.isValidUsername(targetName)) {
            MessageUtils.sendError(player, "Invalid username format.");
            return false;
        }
        
        if (security.isRateLimited(player, "member_add")) {
            MessageUtils.sendError(player, "You're adding members too quickly! Please wait.");
            return false;
        }
        
        try {
            // Check if player owns the cell
            if (!isOwner(player, cell)) {
                MessageUtils.sendError(player, "You don't own this cell!");
                return false;
            }
            
            // Find target player
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName.trim());
            if (target == null) {
                MessageUtils.sendError(player, "Player '" + targetName + "' not found!");
                return false;
            }
            
            // Check if already a member
            if (cell.getRegion().hasMember(target.getUniqueId())) {
                MessageUtils.sendError(player, targetName + " is already a member!");
            return false;
        }
        
            // Check if owner
            if (cell.getRegion().hasOwner(target.getUniqueId())) {
                MessageUtils.sendError(player, "Cannot add the owner as a member!");
            return false;
        }
        
            // Check member limit
            int maxMembers = cell.getMaxMembers();
            if (maxMembers >= 0 && cell.getRegion().getMembers().size() >= maxMembers) {
                MessageUtils.sendError(player, "Member limit of " + maxMembers + " reached!");
                return false;
            }
            
            // Check cost and payment
            double cost = plugin.getMemberAddCost();
            if (cost > 0.0 && economy != null) {
                if (!economy.has(player, cost)) {
                    MessageUtils.sendError(player, "Insufficient funds! Cost: " + plugin.formatCurrency(cost));
                    return false;
                }
                
                if (!economy.withdrawPlayer(player, cost).transactionSuccess()) {
                    MessageUtils.sendError(player, "Payment failed!");
                    return false;
                }
                
                MessageUtils.sendInfo(player, "Charged " + plugin.formatCurrency(cost) + " for adding member.");
            }
            
            // Add member
            cell.getRegion().addMember(target.getUniqueId());
            cell.queueSave();
            
            // Audit log
            security.auditLog(player, "ADD_MEMBER", cell.getRegion().getId(), 
                "Added " + targetName + " as member");
            
            // Success messages
            MessageUtils.sendSuccess(player, "Added " + targetName + " as a member!");
            
            if (target.isOnline()) {
                Player targetPlayer = (Player) target;
                MessageUtils.sendSuccess(targetPlayer, 
                    "You have been added as a member to cell " + cell.getRegion().getId() + " by " + player.getName());
            }
        
        return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error adding member: " + e.getMessage());
            MessageUtils.sendError(player, "Failed to add member due to an error.");
            return false;
        }
    }
    
    /**
     * Remove a member from a cell with comprehensive validation
     */
    public boolean removeMember(Region cell, Player player, String targetName) {
        // Validate inputs
        if (cell == null || player == null || targetName == null || targetName.trim().isEmpty()) {
            if (player != null) {
                MessageUtils.sendError(player, "Invalid parameters for removing member.");
            }
            return false;
        }
        
        // Security checks
        if (!security.isValidUsername(targetName)) {
            MessageUtils.sendError(player, "Invalid username format.");
            return false;
        }
        
        try {
            // Check if player owns the cell
        if (!isOwner(player, cell)) {
            MessageUtils.sendError(player, "You don't own this cell!");
            return false;
        }
        
            // Find target player
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName.trim());
            if (target == null) {
                MessageUtils.sendError(player, "Player '" + targetName + "' not found!");
            return false;
        }
        
            // Check if actually a member
            if (!cell.getRegion().hasMember(target.getUniqueId())) {
                MessageUtils.sendError(player, targetName + " is not a member!");
                return false;
            }
            
            // Check cost and payment
            double cost = plugin.getMemberRemoveCost();
            if (cost > 0.0 && economy != null) {
                if (!economy.has(player, cost)) {
                    MessageUtils.sendError(player, "Insufficient funds! Cost: " + plugin.formatCurrency(cost));
                    return false;
                }
                
                if (!economy.withdrawPlayer(player, cost).transactionSuccess()) {
                    MessageUtils.sendError(player, "Payment failed!");
                    return false;
                }
                
                MessageUtils.sendInfo(player, "Charged " + plugin.formatCurrency(cost) + " for removing member.");
            }
            
            // Remove member
            cell.getRegion().removeMember(target.getUniqueId());
            cell.queueSave();
            
            // Audit log
            security.auditLog(player, "REMOVE_MEMBER", cell.getRegion().getId(), 
                "Removed " + targetName + " as member");
            
            // Success messages
            MessageUtils.sendSuccess(player, "Removed " + targetName + " as a member!");
            
            if (target.isOnline()) {
                Player targetPlayer = (Player) target;
                MessageUtils.sendInfo(targetPlayer, 
                    "You have been removed as a member from cell " + cell.getRegion().getId() + " by " + player.getName());
            }
        
        return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error removing member: " + e.getMessage());
            MessageUtils.sendError(player, "Failed to remove member due to an error.");
            return false;
        }
    }
    
    /**
     * Get available cells for purchase/rent
     */
    public List<Region> getAvailableCells(World world) {
        if (world == null) {
            return Collections.emptyList();
        }
        
        try {
            List<Region> availableCells = new ArrayList<>();
            for (Region region : arm.getRegionManager()) {
                if (region != null && 
                    region.getRegion() != null && 
                    region.getRegionworld().equals(world) &&
                    !region.isSold()) {
                    availableCells.add(region);
                }
            }
            return availableCells;
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting available cells: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * Get comprehensive cell information with null safety
     */
    public Map<String, String> getCellInfo(Region cell) {
        Map<String, String> info = new HashMap<>();
        
        if (cell == null) {
            info.put("error", "Cell is null");
            return info;
        }
        
        try {
            // Basic info
            info.put("id", cell.getRegion() != null ? cell.getRegion().getId() : "Unknown");
            info.put("world", cell.getRegionworld() != null ? 
                     cell.getRegionworld().getName() : "Unknown");
            info.put("sold", isSold(cell) ? "Occupied" : "Available");
            info.put("price", plugin.formatCurrency(getPrice(cell)));
            
            // Type information
            if (cell instanceof RentRegion) {
                info.put("type", "Rental");
            } else {
                info.put("type", "Purchase");
            }
            
            // Owner information
            UUID ownerId = cell.getOwner();
            if (ownerId != null) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
                info.put("owner", owner.getName() != null ? owner.getName() : "Unknown");
            } else {
                info.put("owner", "Available");
            }
            
            // Member information
            var members = cell.getRegion().getMembers();
            if (members != null && !members.isEmpty()) {
                List<String> memberNames = members.stream()
                    .map(Bukkit::getOfflinePlayer)
                    .filter(Objects::nonNull)
                    .map(OfflinePlayer::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                
                info.put("members", String.join(", ", memberNames));
                info.put("memberCount", String.valueOf(memberNames.size()));
            } else {
                info.put("members", "None");
                info.put("memberCount", "0");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting cell info: " + e.getMessage());
            info.put("error", "Failed to get cell information");
        }
        
        return info;
    }
    
    /**
     * Purchase a cell
     * @param player The player
     * @param cell The cell to purchase
     * @return true if successful
     */
    public boolean purchaseCell(Player player, Region cell) {
        if (cell == null || player == null) {
            plugin.debug("purchaseCell: null cell or player");
            return false;
        }
        
        try {
            // Check if cell is available
            if (isSold(cell)) {
                MessageUtils.sendError(player, "This cell is already owned!");
                return false;
            }
            
            // Check cell limits
            if (!canPlayerAcquireCell(player, cell)) {
                return false; // Error message already sent by canPlayerAcquireCell
            }
            
            double price = getPrice(cell);
            if (price <= 0) {
                MessageUtils.sendError(player, "This cell is not for sale!");
                return false;
            }
            
            // Check economy
            if (economy == null) {
                MessageUtils.sendError(player, "Economy system not available!");
                return false;
            }
            
            if (!economy.has(player, price)) {
                MessageUtils.sendError(player, "Insufficient funds! You need %s", 
                    plugin.formatCurrency(price));
                return false;
            }
            
            // Rate limit check
            if (security.isRateLimited(player, "purchase")) {
                MessageUtils.sendError(player, "You're purchasing too quickly! Please wait.");
                return false;
            }
            
            // Attempt purchase through ARM
            boolean success = false;
            
            try {
                // Use ARM's buy method
                cell.buy(player);
                success = true;
                
                String action = cell instanceof RentRegion ? "rented" : "purchased";
                MessageUtils.sendSuccess(player, "Successfully %s cell '%s' for %s!", 
                    action, cell.getRegion().getId(), plugin.formatCurrency(price));
                    
            } catch (Exception e) {
                plugin.getLogger().warning("ARM purchase failed: " + e.getMessage());
                success = false;
            }
            
            if (success) {
                // Sync door ownership for this region
                plugin.getDoorManager().syncDoorOwnershipForRegion(cell.getRegion().getId());
                
                // Audit log
                security.auditLog(player, "CELL_PURCHASE", cell.getRegion().getId(), 
                    "Price: " + plugin.formatCurrency(price));
                
                plugin.debug("Player " + player.getName() + " successfully purchased cell " + 
                    cell.getRegion().getId());
                
                return true;
            } else {
                MessageUtils.sendError(player, "Failed to purchase cell! Please try again.");
                return false;
            }
            
        } catch (Exception e) {
            MessageUtils.sendError(player, "An error occurred while purchasing the cell!");
            plugin.getLogger().warning("Error in purchaseCell for " + player.getName() + 
                " and cell " + cell.getRegion().getId() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a player can acquire a cell (respects limits and permissions)
     * @param player The player
     * @param cell The cell
     * @return true if player can acquire the cell
     */
    public boolean canPlayerAcquireCell(Player player, Region cell) {
        CellGroupManager groupManager = plugin.getCellGroupManager();
        CellGroup group = groupManager.getGroupByRegion(cell.getRegion().getId());
        
        if (group != null) {
            // Check if player can acquire in this group
            if (!groupManager.canPlayerAcquireInGroup(player, group)) {
                // Check specific reason for denial
                String permission = group.getRequiredPermission();
                if (permission != null && !player.hasPermission(permission)) {
                    MessageUtils.sendError(player, "You don't have permission to acquire cells in this group!");
                    return false;
                }
                
                // Check group limit
                int groupLimit = group.getCellLimit();
                if (groupLimit > 0) {
                    int currentCount = groupManager.getPlayerCellCountInGroup(player.getUniqueId(), group);
                    if (currentCount >= groupLimit) {
                        MessageUtils.sendError(player, "You've reached the limit of %d cells in the %s group!", 
                            groupLimit, group.getDisplayName());
                        return false;
                    }
                }
                
                // Check global limit
                int globalLimit = groupManager.getGlobalCellLimit();
                if (globalLimit > 0) {
                    int totalCount = groupManager.getPlayerTotalCellCount(player.getUniqueId());
                    if (totalCount >= globalLimit) {
                        MessageUtils.sendError(player, "You've reached the global limit of %d cells!", globalLimit);
                        return false;
                    }
                }
            }
        }
        
        return true;
    }

    /**
     * Sell a cell back to the market
     * @param player The player
     * @param cell The cell to sell
     * @return true if successful
     */
    public boolean sellCell(Player player, Region cell) {
        if (cell == null || player == null) {
            plugin.debug("sellCell: null cell or player");
            return false;
        }
        
        try {
            // Check ownership
            if (!isOwner(player, cell)) {
                MessageUtils.sendError(player, "You don't own this cell!");
                return false;
            }
            
            // Check if this is a rental that can be cancelled
            try {
                // Use ARM's unsell method which works for both rental and sell regions
                cell.unsell(Region.ActionReason.MANUALLY_BY_ADMIN, true, false);
                
                String action = cell instanceof RentRegion ? "cancelled rental for" : "sold";
                MessageUtils.sendSuccess(player, "Successfully %s cell '%s'!", 
                    action, cell.getRegion().getId());
                    
                // Sync door ownership - player no longer owns the cell
                plugin.getDoorManager().syncDoorOwnershipForRegion(cell.getRegion().getId());
                
                // Audit log
                String auditAction = cell instanceof RentRegion ? "CELL_RENTAL_CANCEL" : "CELL_SELL";
                String details = cell instanceof RentRegion ? "Rental cancelled" : "Sold back to market";
                security.auditLog(player, auditAction, cell.getRegion().getId(), details);
                
                plugin.debug("Player " + player.getName() + " " + action + " cell " + 
                    cell.getRegion().getId());
                
                return true;
                
            } catch (Exception e) {
                plugin.getLogger().warning("ARM unsell failed: " + e.getMessage());
                MessageUtils.sendError(player, "Failed to sell/cancel cell!");
                return false;
            }
            
        } catch (Exception e) {
            MessageUtils.sendError(player, "An error occurred while selling the cell!");
            plugin.getLogger().warning("Error in sellCell for " + player.getName() + 
                " and cell " + cell.getRegion().getId() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Extend rental period with validation
     */
    public boolean extendRental(Player player, Region cell, int periods) {
        if (player == null || cell == null || periods <= 0) {
            if (player != null) {
                MessageUtils.sendError(player, "Invalid extension parameters.");
            }
            return false;
        }
        
        if (!(cell instanceof RentRegion rentRegion)) {
            MessageUtils.sendError(player, "This cell is not a rental!");
            return false;
        }
        
        try {
            // Check ownership
            if (!isOwner(player, cell)) {
                MessageUtils.sendError(player, "You don't own this rental!");
                return false;
            }
            
            // Calculate cost
            double costPerPeriod = rentRegion.getPricePerPeriod();
            double totalCost = costPerPeriod * periods;
            
            if (!security.isValidEconomyAmount(totalCost)) {
                MessageUtils.sendError(player, "Invalid rental cost!");
                return false;
            }
            
            // Check economy and funds
            if (economy == null) {
                MessageUtils.sendError(player, "Economy system is not available!");
                return false;
            }
            
            if (!economy.has(player, totalCost)) {
                MessageUtils.sendError(player, "Insufficient funds! Cost: " + plugin.formatCurrency(totalCost));
                return false;
            }
            
            // Process payment
            if (!economy.withdrawPlayer(player, totalCost).transactionSuccess()) {
                MessageUtils.sendError(player, "Payment failed! Please try again.");
                return false;
            }
            
            // Extend rental using proper ARM API
            try {
                // Use ARM's extend method for rental regions - ARM handles period calculation internally
                rentRegion.extend(player);
                plugin.getLogger().info("Successfully extended rental for player " + player.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("Error extending rental: " + e.getMessage());
                // Continue anyway as the payment was processed
            }
            
            // Audit log
            security.auditLog(player, "EXTEND_RENTAL", cell.getRegion().getId(), 
                "Periods: " + periods + ", Cost: " + plugin.formatCurrency(totalCost));
            
            // Success message
            MessageUtils.sendSuccess(player, "Extended rental for " + periods + " period(s) for " + 
                plugin.formatCurrency(totalCost) + "!");
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error extending rental: " + e.getMessage());
            MessageUtils.sendError(player, "Failed to extend rental due to an error.");
            return false;
        }
    }
    
    /**
     * Get rental information for a rental cell
     */
    public Map<String, String> getRentalInfo(Region cell) {
        Map<String, String> info = new HashMap<>();
        
        if (!(cell instanceof RentRegion rentRegion)) {
            info.put("error", "Not a rental cell");
            return info;
        }
        
        try {
            info.put("periodPrice", plugin.formatCurrency(rentRegion.getPricePerPeriod()));
            
            // Get remaining time using proper ARM API
            try {
                long payedTillTimestamp = rentRegion.getPayedTill();
                long remainingTime = payedTillTimestamp - System.currentTimeMillis();
                info.put("timeLeft", formatTimeLeft(remainingTime));
            } catch (Exception e) {
                plugin.getLogger().warning("Error getting remaining rental time: " + e.getMessage());
                info.put("timeLeft", "Unknown");
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting rental info: " + e.getMessage());
            info.put("error", "Failed to get rental information");
        }
        
        return info;
    }
    
    /**
     * Format milliseconds into human-readable time
     */
    private String formatTimeLeft(long milliseconds) {
        if (milliseconds <= 0) {
            return "Expired";
        }
        
        try {
            Duration duration = Duration.ofMillis(milliseconds);
            long days = duration.toDays();
            long hours = duration.toHours() % 24;
            long minutes = duration.toMinutes() % 60;
        
        if (days > 0) {
                return String.format("%dd %dh %dm", days, hours, minutes);
            } else if (hours > 0) {
                return String.format("%dh %dm", hours, minutes);
        } else {
                return String.format("%dm", minutes);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error formatting time: " + e.getMessage());
            return "Unknown";
        }
    }
} 