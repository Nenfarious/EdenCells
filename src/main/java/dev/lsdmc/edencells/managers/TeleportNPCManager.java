package dev.lsdmc.edencells.managers;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.models.CellGroup;
import dev.lsdmc.edencells.security.SecurityManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import net.alex9849.arm.regions.Region;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages Citizens teleport NPCs
 */
public final class TeleportNPCManager {
    
    private final EdenCells plugin;
    private final CellManager cellManager;
    private final Economy economy;
    private final SecurityManager security;
    
    // Map of NPC ID to configuration
    private final Map<Integer, NPCConfig> npcConfigs = new HashMap<>();
    
    // File storage
    private File npcsFile;
    private FileConfiguration npcsConfig;
    
    public record NPCConfig(
        String name,
        String cellGroupName,  // Changed from worldGuardRegion
        String worldName,
        boolean requirePayment,
        double cost
    ) {}
    
    public TeleportNPCManager(EdenCells plugin, CellManager cellManager, Economy economy, SecurityManager security) {
        this.plugin = plugin;
        this.cellManager = cellManager;
        this.economy = economy;
        this.security = security;
        
        setupNPCStorage();
        loadNPCs();
    }
    
    /**
     * Setup NPC storage file
     */
    private void setupNPCStorage() {
        npcsFile = new File(plugin.getDataFolder(), "teleport-npcs.yml");
        if (!npcsFile.exists()) {
            try {
                npcsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create teleport-npcs.yml: " + e.getMessage());
            }
        }
        npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
    }
    
    /**
     * Load NPC configurations from file
     */
    public void loadNPCs() {
        npcConfigs.clear();
        
        if (!npcsConfig.contains("npcs")) {
            return;
        }
        
        for (String npcIdStr : npcsConfig.getConfigurationSection("npcs").getKeys(false)) {
            try {
                int npcId = Integer.parseInt(npcIdStr);
                String path = "npcs." + npcIdStr;
                
                String name = npcsConfig.getString(path + ".name", "Unknown");
                String cellGroupName = npcsConfig.getString(path + ".cell-group", "");
                String worldName = npcsConfig.getString(path + ".world", "world");
                boolean requirePayment = npcsConfig.getBoolean(path + ".require-payment", true);
                double cost = npcsConfig.getDouble(path + ".cost", 50.0);
                
                if (!cellGroupName.isEmpty()) {
                    NPCConfig config = new NPCConfig(name, cellGroupName, worldName, requirePayment, cost);
                    npcConfigs.put(npcId, config);
                    
                    // Ensure the NPC has our trait if it exists
                    if (CitizensAPI.hasImplementation()) {
                        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
                        if (npc != null && !npc.hasTrait(dev.lsdmc.edencells.npc.TeleportNPC.class)) {
                            npc.addTrait(dev.lsdmc.edencells.npc.TeleportNPC.class);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid NPC ID in config: " + npcIdStr);
            }
        }
        
        plugin.getLogger().info("Loaded " + npcConfigs.size() + " teleport NPCs");
    }
    
    /**
     * Save NPC configurations to file
     */
    public void saveNPCs() {
        // Clear existing data
        npcsConfig.set("npcs", null);
        
        // Save all NPC configs
        for (Map.Entry<Integer, NPCConfig> entry : npcConfigs.entrySet()) {
            int npcId = entry.getKey();
            NPCConfig config = entry.getValue();
            String path = "npcs." + npcId;
            
            npcsConfig.set(path + ".name", config.name());
            npcsConfig.set(path + ".cell-group", config.cellGroupName());
            npcsConfig.set(path + ".world", config.worldName());
            npcsConfig.set(path + ".require-payment", config.requirePayment());
            npcsConfig.set(path + ".cost", config.cost());
        }
        
        try {
            npcsConfig.save(npcsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teleport-npcs.yml: " + e.getMessage());
        }
    }

    /**
     * Create a teleport NPC for a cell group
     * @param name The NPC name
     * @param location The spawn location
     * @param cellGroupName The cell group name
     * @return The created NPC or null
     */
    public NPC createTeleportNPC(String name, Location location, String cellGroupName) {
        if (!CitizensAPI.hasImplementation()) {
            return null;
        }
        
        // Verify cell group exists
        CellGroup cellGroup = plugin.getCellGroupManager().getGroup(cellGroupName);
        if (cellGroup == null) {
            plugin.getLogger().warning("Cannot create NPC for non-existent cell group: " + cellGroupName);
            return null;
        }
        
        // Create NPC
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.spawn(location);
        
        // Add our trait
        npc.addTrait(dev.lsdmc.edencells.npc.TeleportNPC.class);
        
        // Add configuration for the NPC
        boolean requirePayment = plugin.getConfig().getBoolean(Constants.Config.TELEPORT_REQUIRE_PAYMENT, true);
        double cost = plugin.getConfig().getDouble(Constants.Config.TELEPORT_BASE_COST, 50.0);
        
        // Check if group has custom teleport cost
        if (cellGroup.getTeleportCost() >= 0) {
            cost = cellGroup.getTeleportCost();
        }
        
        NPCConfig config = new NPCConfig(
            name,
            cellGroupName,
            location.getWorld().getName(),
            requirePayment,
            cost
        );
        
        npcConfigs.put(npc.getId(), config);
        saveNPCs(); // Save immediately
        
        return npc;
    }
    
    /**
     * Set an existing NPC as a teleport NPC
     * @param npcId The existing NPC ID
     * @param cellGroupName The cell group name
     * @return true if successful
     */
    public boolean setExistingNPCAsTeleport(int npcId, String cellGroupName) {
        if (!CitizensAPI.hasImplementation()) {
            return false;
        }
        
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null) {
            return false;
        }
        
        // Verify cell group exists
        CellGroup cellGroup = plugin.getCellGroupManager().getGroup(cellGroupName);
        if (cellGroup == null) {
            return false;
        }
        
        // Add our trait if not already present
        if (!npc.hasTrait(dev.lsdmc.edencells.npc.TeleportNPC.class)) {
            npc.addTrait(dev.lsdmc.edencells.npc.TeleportNPC.class);
        }
        
        // Create configuration
        boolean requirePayment = plugin.getConfig().getBoolean(Constants.Config.TELEPORT_REQUIRE_PAYMENT, true);
        double cost = plugin.getConfig().getDouble(Constants.Config.TELEPORT_BASE_COST, 50.0);
        
        // Check if group has custom teleport cost
        if (cellGroup.getTeleportCost() >= 0) {
            cost = cellGroup.getTeleportCost();
        }
        
        NPCConfig config = new NPCConfig(
            npc.getName(),
            cellGroupName,
            npc.getStoredLocation().getWorld().getName(),
            requirePayment,
            cost
        );
        
        npcConfigs.put(npcId, config);
        saveNPCs(); // Save immediately
        
        return true;
    }
    
    /**
     * Remove a teleport NPC
     * @param npcId The NPC ID
     * @return true if removed
     */
    public boolean removeTeleportNPC(int npcId) {
        if (!CitizensAPI.hasImplementation()) {
            return false;
        }
        
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc != null) {
            npc.destroy();
            npcConfigs.remove(npcId);
            saveNPCs(); // Save immediately
            return true;
        }
        
        return false;
    }
    
    /**
     * Remove teleport functionality from an NPC without destroying it
     * @param npcId The NPC ID
     * @return true if removed
     */
    public boolean removeTeleportFromNPC(int npcId) {
        if (!CitizensAPI.hasImplementation()) {
            return false;
        }
        
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc != null && npc.hasTrait(dev.lsdmc.edencells.npc.TeleportNPC.class)) {
            npc.removeTrait(dev.lsdmc.edencells.npc.TeleportNPC.class);
            npcConfigs.remove(npcId);
            saveNPCs(); // Save immediately
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle teleport request from NPC
     * @param player The player
     * @param npcId The NPC ID
     */
    public void handleTeleport(Player player, int npcId) {
        handleTeleport(player, npcId, false); // Default to right-click behavior
    }
    
    /**
     * Handle teleport request from NPC with click type
     * @param player The player
     * @param npcId The NPC ID
     * @param isLeftClick Whether this was a left-click (for donor cells)
     */
    public void handleTeleport(Player player, int npcId, boolean isLeftClick) {
        NPCConfig config = npcConfigs.get(npcId);
        if (config == null) {
            MessageUtils.sendError(player, "NPC not configured!");
            return;
        }
        
        // Check cooldown
        if (security.isOnTeleportCooldown(player)) {
            int remaining = security.getRemainingTeleportCooldown(player);
            MessageUtils.sendError(player, "Please wait %d seconds before teleporting again!", remaining);
            return;
        }
        
        Region targetCell = null;
        double teleportCost = 0;
        
        if (isLeftClick) {
            // Left-click: Try to teleport to donor cell
            targetCell = findPlayerDonorCell(player);
            if (targetCell == null) {
                MessageUtils.sendError(player, "You don't own any donor cells!");
                return;
            }
            // Donor cells use their group's teleport cost (usually 0)
            CellGroup donorGroup = plugin.getCellGroupManager().getGroupByRegion(targetCell.getRegion().getId());
            teleportCost = getTeleportCostForGroup(donorGroup);
        } else {
            // Right-click: Try to teleport to cell in this NPC's group
            CellGroup cellGroup = plugin.getCellGroupManager().getGroup(config.cellGroupName());
            if (cellGroup == null) {
                MessageUtils.sendError(player, "Cell group '%s' not found!", config.cellGroupName());
                return;
            }
            
            targetCell = findPlayerCellInGroup(player, cellGroup);
            if (targetCell == null) {
                MessageUtils.sendError(player, "You don't own any cells in the %s group!", cellGroup.getDisplayName());
                MessageUtils.sendInfo(player, "Look for cell signs in this area to purchase one!");
                return;
            }
            
            teleportCost = getTeleportCostForGroup(cellGroup);
        }
        
        // Check if player should be charged (check configurable free teleportation groups)
        boolean shouldCharge = shouldChargeForTeleportation(player, teleportCost);
        if (shouldCharge && teleportCost > 0 && economy != null) {
            if (!economy.has(player, teleportCost)) {
                MessageUtils.sendError(player, "Insufficient funds! Teleportation costs %s", 
                    plugin.formatCurrency(teleportCost));
                return;
            }
            
            if (!economy.withdrawPlayer(player, teleportCost).transactionSuccess()) {
                MessageUtils.sendError(player, "Payment failed! Please try again.");
                return;
            }
            
            MessageUtils.sendInfo(player, "Charged %s for teleportation", plugin.formatCurrency(teleportCost));
        }
        
        // Teleport the player
        teleportToCell(player, targetCell);
        security.setTeleportCooldown(player);
        
        plugin.debug("Player " + player.getName() + " teleported to cell " + targetCell.getRegion().getId() + 
            " via NPC " + npcId + " (left-click: " + isLeftClick + ")");
    }
    
    /**
     * Find the player's cell in a specific group (should only be one)
     */
    private Region findPlayerCellInGroup(Player player, CellGroup cellGroup) {
        List<Region> playerCells = cellManager.getPlayerCells(player);
        
        for (Region cell : playerCells) {
            if (cellGroup.containsRegion(cell.getRegion().getId())) {
                return cell;
            }
        }
        
        return null;
    }
    
    /**
     * Find the player's donor cell (should only be one)
     */
    private Region findPlayerDonorCell(Player player) {
        List<Region> playerCells = cellManager.getPlayerCells(player);
        
        for (Region cell : playerCells) {
            CellGroup group = plugin.getCellGroupManager().getGroupByRegion(cell.getRegion().getId());
            if (group != null && group.isDonorGroup()) {
                return cell;
            }
        }
        
        return null;
    }
    
    /**
     * Get the teleport cost for a cell group
     */
    private double getTeleportCostForGroup(CellGroup cellGroup) {
        if (cellGroup == null) {
            return plugin.getConfig().getDouble("teleportation.base-cost", 50.0);
        }
        
        double groupCost = cellGroup.getTeleportCost();
        return groupCost >= 0 ? groupCost : plugin.getConfig().getDouble("teleportation.base-cost", 50.0);
    }
    
    /**
     * Check if player should be charged for teleportation
     * @param player The player
     * @param teleportCost The cost that would be charged
     * @return true if player should be charged, false if they get free teleportation
     */
    private boolean shouldChargeForTeleportation(Player player, double teleportCost) {
        // Free if cost is 0
        if (teleportCost <= 0) {
            return false;
        }
        
        // Check if player has bypass permission
        if (player.hasPermission(Constants.Permissions.BYPASS_PAYMENT)) {
            return false;
        }
        
        // Check configurable free teleportation groups
        List<String> freeGroups = plugin.getConfig().getStringList("teleportation.free-groups");
        for (String group : freeGroups) {
            // Support both "group.groupname" and "edencells.group.groupname" formats
            if (player.hasPermission("group." + group) || 
                player.hasPermission("edencells.group." + group) ||
                player.hasPermission(group)) {
                return false;
            }
        }
        
        // Check if teleportation payment is required globally
        return plugin.getConfig().getBoolean("teleportation.require-payment", true);
    }
    
    /**
     * Teleport player to a cell
     * @param player The player
     * @param cell The cell
     */
    private void teleportToCell(Player player, Region cell) {
        try {
            // Use ARM's built-in teleport functionality which handles safe locations automatically
            cell.teleport(player, false);
            
            MessageUtils.send(player, "<color:#51CF66>Teleported to cell <color:#FFB3C6>" + cell.getRegion().getId() + "</color>!</color>");
            
            // Play teleport sound
            player.getWorld().playSound(player.getLocation(), Constants.Sounds.TELEPORT, 1.0f, 1.0f);
            
            plugin.debug("Successfully teleported " + player.getName() + " to cell " + cell.getRegion().getId());
            
        } catch (Exception e) {
            MessageUtils.sendError(player, "Failed to teleport to cell!");
            plugin.getLogger().warning("Error teleporting " + player.getName() + " to cell " + 
                cell.getRegion().getId() + ": " + e.getMessage());
                
            // Refund if payment was taken
            double teleportCost = plugin.getConfig().getDouble(Constants.Config.TELEPORT_BASE_COST, 50.0);
            boolean requirePayment = plugin.getConfig().getBoolean(Constants.Config.TELEPORT_REQUIRE_PAYMENT, true);
            
            if (requirePayment && economy != null) {
                economy.depositPlayer(player, teleportCost);
                MessageUtils.sendInfo(player, "Teleportation fee has been refunded due to error.");
            }
        }
    }
    
    /**
     * Get NPC configuration
     * @param npcId The NPC ID
     * @return The config or null
     */
    public NPCConfig getNPCConfig(int npcId) {
        return npcConfigs.get(npcId);
    }
    
    /**
     * Set an existing NPC as a teleport NPC (simplified version for command)
     * @param npcId The existing NPC ID
     * @return true if successful
     */
    public boolean setTeleportNPC(int npcId) {
        if (!CitizensAPI.hasImplementation()) {
            return false;
        }
        
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null) {
            return false;
        }
        
        // Use a default cell group - could be enhanced to prompt for group selection
        List<CellGroup> availableGroups = new ArrayList<>(plugin.getCellGroupManager().getAllGroups().values());
        if (availableGroups.isEmpty()) {
            return false;
        }
        
        // Use the first available group as default
        String cellGroupName = availableGroups.get(0).getName();
        return setExistingNPCAsTeleport(npcId, cellGroupName);
    }
    
    /**
     * Remove teleport functionality from an NPC (alias for removeTeleportFromNPC)
     * @param npcId The NPC ID
     * @return true if successful
     */
    public boolean unsetTeleportNPC(int npcId) {
        return removeTeleportFromNPC(npcId);
    }
    
    /**
     * Reload teleport NPCs from configuration
     */
    public void reloadTeleportNPCs() {
        loadNPCs();
    }
} 