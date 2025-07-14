package dev.lsdmc.edencells.models;

import dev.lsdmc.edencells.EdenCells;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Manages cell groups with limits and configuration
 * Uses separate cell-groups.yml file for better organization
 */
public final class CellGroupManager {
    
    private final EdenCells plugin;
    private final ConcurrentMap<String, CellGroup> groups = new ConcurrentHashMap<>();
    private int globalCellLimit = -1; // No limit by default
    
    private File groupsFile;
    private FileConfiguration groupsConfig;
    
    public CellGroupManager(EdenCells plugin) {
        this.plugin = plugin;
        initializeGroupsFile();
        loadGroups();
    }
    
    /**
     * Initialize the cell-groups.yml file
     */
    private void initializeGroupsFile() {
        groupsFile = new File(plugin.getDataFolder(), "cell-groups.yml");
        
        // Create the file if it doesn't exist
        if (!groupsFile.exists()) {
            plugin.saveResource("cell-groups.yml", false);
        }
        
        groupsConfig = YamlConfiguration.loadConfiguration(groupsFile);
        
        // Migrate from config.yml if needed
        migrateFromMainConfig();
    }
    
    /**
     * Migrate cell groups from main config.yml to cell-groups.yml if they exist there
     */
    private void migrateFromMainConfig() {
        ConfigurationSection mainConfigGroups = plugin.getConfig().getConfigurationSection("cell-groups");
        
        if (mainConfigGroups != null && !mainConfigGroups.getKeys(false).isEmpty()) {
            plugin.getLogger().info("Migrating cell groups from config.yml to cell-groups.yml...");
            
            // Copy the entire cell-groups section
            groupsConfig.set("groups", mainConfigGroups);
            
            // Copy global limits
            int globalLimit = plugin.getConfig().getInt("cell-limits.global", -1);
            groupsConfig.set("limits.global", globalLimit);
            
            // Set version
            groupsConfig.set("version", 1);
            
            // Save the new file
            saveGroups();
            
            // Remove from main config
            plugin.getConfig().set("cell-groups", null);
            plugin.getConfig().set("cell-limits", null);
            plugin.saveConfig();
            
            plugin.getLogger().info("Migration completed! Cell groups moved to cell-groups.yml");
        }
    }
    
    /**
     * Load groups from cell-groups.yml
     */  
    public void loadGroups() {
        groups.clear();
        
        try {
            // Reload configuration
            groupsConfig = YamlConfiguration.loadConfiguration(groupsFile);
            
            // Load global cell limit
            globalCellLimit = groupsConfig.getInt("limits.global", -1);
            
            ConfigurationSection groupsSection = groupsConfig.getConfigurationSection("groups");
            
            if (groupsSection != null) {
                for (String groupName : groupsSection.getKeys(false)) {
                    try {
                        ConfigurationSection groupSection = groupsSection.getConfigurationSection(groupName);
                        if (groupSection == null) continue;
                        
                        // Get display name
                        String displayName = groupSection.getString("display-name", groupName);
                        
                        // Get regions as Set
                        List<String> regionsList = groupSection.getStringList("regions");
                        Set<String> regions = new LinkedHashSet<>(regionsList);
                        
                        // Get options
                        ConcurrentMap<String, Object> options = new ConcurrentHashMap<>();
                        
                        // Cell limit
                        if (groupSection.contains("cell-limit")) {
                            options.put("cellLimit", groupSection.getInt("cell-limit", -1));
                        }
                        
                        // Teleport cost
                        if (groupSection.contains("teleport-cost")) {
                            options.put("teleportCost", groupSection.getDouble("teleport-cost", -1));
                        }
                        
                        // Is donor group
                        if (groupSection.contains("is-donor")) {
                            options.put("isDonor", groupSection.getBoolean("is-donor", false));
                        }
                        
                        // Required permission  
                        if (groupSection.contains("permission")) {
                            options.put("permission", groupSection.getString("permission"));
                        }
                        
                        // Create group using package-private constructor
                        CellGroup group = new CellGroup(groupName, displayName, regions, options);
                        groups.put(groupName, group);
                        
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load cell group '" + groupName + "': " + e.getMessage());
                    }
                }
            }
            
            plugin.getLogger().info("Loaded " + groups.size() + " cell groups from cell-groups.yml");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load cell groups: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Save groups to cell-groups.yml
     */
    public synchronized void saveGroups() {
        try {
            // Save global limit
            groupsConfig.set("limits.global", globalCellLimit);
            
            // Clear existing groups section
            groupsConfig.set("groups", null);
            
            // Save all groups
            for (Map.Entry<String, CellGroup> entry : groups.entrySet()) {
                String groupName = entry.getKey();
                CellGroup group = entry.getValue();
                
                String path = "groups." + groupName;
                groupsConfig.set(path + ".display-name", group.getDisplayName());
                
                // Convert Set back to List for YAML
                groupsConfig.set(path + ".regions", group.getRegions().stream().collect(Collectors.toList()));
                
                // Save options only if they differ from defaults
                if (group.getCellLimit() != -1) {
                    groupsConfig.set(path + ".cell-limit", group.getCellLimit());
                }
                if (group.getTeleportCost() != -1) {
                    groupsConfig.set(path + ".teleport-cost", group.getTeleportCost());
                }
                if (group.isDonorGroup()) {
                    groupsConfig.set(path + ".is-donor", true);
                }
                if (group.getRequiredPermission() != null) {
                    groupsConfig.set(path + ".permission", group.getRequiredPermission());
                }
            }
            
            // Set file version
            groupsConfig.set("version", 1);
            
            // Save to file
            groupsConfig.save(groupsFile);
            
            plugin.debug("Saved " + groups.size() + " cell groups to cell-groups.yml");
            
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save cell groups: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Reload groups from file
     */
    public void reloadGroups() {
        loadGroups();
    }
    
    /**
     * Get a group by name
     * @param name The group name
     * @return The group or null
     */
    public CellGroup getGroup(String name) {
        return name != null ? groups.get(name.trim()) : null;
    }
    
    /**
     * Get a group by region ID
     * @param regionId The region ID
     * @return The group containing this region or null
     */
    public CellGroup getGroupByRegion(String regionId) {
        if (regionId == null) return null;
        
        String trimmed = regionId.trim();
        for (CellGroup group : groups.values()) {
            if (group.containsRegion(trimmed)) {
                return group;
            }
        }
        return null;
    }
    
    /**
     * Get all groups (defensive copy)
     * @return Map of all groups
     */
    public Map<String, CellGroup> getAllGroups() {
        return new HashMap<>(groups);
    }
    
    /**
     * Create a new group
     * @param name The group name
     * @return The created group or null if already exists
     * @throws IllegalArgumentException if name is invalid
     */
    public CellGroup createGroup(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Group name cannot be null or empty");
        }
        
        String trimmed = name.trim();
        if (groups.containsKey(trimmed)) {
            return null;
        }
        
        try {
            CellGroup group = new CellGroup(trimmed);
            groups.put(trimmed, group);
            return group;
        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors
        }
    }
    
    /**
     * Delete a group
     * @param name The group name
     * @return true if deleted
     */
    public boolean deleteGroup(String name) {
        if (name == null) return false;
        
        CellGroup removed = groups.remove(name.trim());
        return removed != null;
    }
    
    /**
     * Check if a player can acquire a cell in a group
     * @param player The player
     * @param group The group
     * @return true if allowed
     */
    public boolean canPlayerAcquireInGroup(Player player, CellGroup group) {
        if (player == null || group == null) {
            return false;
        }
        
        // Check permission
        String permission = group.getRequiredPermission();
        if (permission != null && !player.hasPermission(permission)) {
            return false;
        }
        
        // Check group limit
        int groupLimit = group.getCellLimit();
        if (groupLimit > 0) {
            int currentCount = getPlayerCellCountInGroup(player.getUniqueId(), group);
            if (currentCount >= groupLimit) {
                return false;
            }
        }
        
        // Check global limit
        if (globalCellLimit > 0) {
            int totalCount = getPlayerTotalCellCount(player.getUniqueId());
            if (totalCount >= globalCellLimit) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get player's cell count in a specific group
     * @param playerUuid The player UUID
     * @param group The group
     * @return Cell count
     */
    public int getPlayerCellCountInGroup(UUID playerUuid, CellGroup group) {
        if (playerUuid == null || group == null) {
            return 0;
        }
        
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) return 0;
        
        return (int) arm.getRegionManager().getRegionsByOwner(playerUuid)
            .stream()
            .filter(region -> group.containsRegion(region.getRegion().getId()))
            .count();
    }
    
    /**
     * Get player's total cell count across all groups
     * @param playerUuid The player UUID
     * @return Total cell count
     */
    public int getPlayerTotalCellCount(UUID playerUuid) {
        if (playerUuid == null) return 0;
        
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) return 0;
        
        return arm.getRegionManager().getRegionsByOwner(playerUuid).size();
    }
    
    /**
     * Get accessible groups for a player
     * @param player The player
     * @return List of accessible groups
     */
    public List<CellGroup> getAccessibleGroups(Player player) {
        if (player == null) {
            return List.of();
        }
        
        return groups.values().stream()
            .filter(group -> {
                String permission = group.getRequiredPermission();
                return permission == null || player.hasPermission(permission);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Get teleport cost for a region
     * @param regionId The region ID
     * @return Teleport cost or -1 for default
     */
    public double getTeleportCostForRegion(String regionId) {
        CellGroup group = getGroupByRegion(regionId);
        if (group != null) {
            return group.getTeleportCost();
        }
        return -1;
    }
    
    /**
     * Set the global cell limit
     * @param limit The limit (-1 = no limit)
     * @throws IllegalArgumentException if limit is invalid
     */
    public void setGlobalCellLimit(int limit) {
        if (limit < -1) {
            throw new IllegalArgumentException("Global cell limit cannot be less than -1");
        }
        this.globalCellLimit = limit;
    }
    
    /**
     * Get the global cell limit
     * @return The limit (-1 = no limit)
     */
    public int getGlobalCellLimit() {
        return globalCellLimit;
    }
    
    /**
     * Get limit info for a player
     * @param player The player
     * @return Map of limit information
     */
    public Map<String, String> getPlayerLimitInfo(Player player) {
        if (player == null) {
            return Map.of();
        }
        
        Map<String, String> info = new HashMap<>();
        
        // Global limit
        int totalCells = getPlayerTotalCellCount(player.getUniqueId());
        if (globalCellLimit > 0) {
            info.put("global", totalCells + "/" + globalCellLimit);
        } else {
            info.put("global", totalCells + "/∞");
        }
        
        // Group limits
        for (CellGroup group : groups.values()) {
            int groupLimit = group.getCellLimit();
            if (groupLimit > 0) {
                int groupCells = getPlayerCellCountInGroup(player.getUniqueId(), group);
                info.put(group.getName(), groupCells + "/" + groupLimit);
            }
        }
        
        return info;
    }
    
    /**
     * Get the cell-groups.yml file
     * @return The file
     */
    public File getGroupsFile() {
        return groupsFile;
    }
    
    /**
     * Get the groups configuration
     * @return The configuration
     */
    public FileConfiguration getGroupsConfig() {
        return groupsConfig;
    }
    
    /**
     * Check if a group name is valid
     * @param name The name to check
     * @return true if valid
     */
    public boolean isValidGroupName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Try to create a temporary group to validate the name
            new CellGroup(name.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT (1).jar!\dev\lsdmc\models\CellGroupManager.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */