package dev.lsdmc.models;

import dev.lsdmc.EdenCells;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages cell groups and their operations.
 * Handles loading, saving, and querying of cell groups.
 */
public class CellGroupManager {
    private final EdenCells plugin;
    private final Map<String, CellGroup> groups = new HashMap<>();
    private final File groupsFile;

    public CellGroupManager(EdenCells plugin) {
        this.plugin = plugin;
        this.groupsFile = new File(plugin.getDataFolder(), "groups.yml");
        loadGroups();
    }

    /**
     * Load groups from configuration
     */
    public void loadGroups() {
        groups.clear();
        
        if (!groupsFile.exists()) {
            try {
                groupsFile.createNewFile();
                plugin.getLogger().info("Created new groups.yml file");
                return;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create groups.yml file", e);
                return;
            }
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(groupsFile);
        ConfigurationSection groupsSection = config.getConfigurationSection("groups");
        
        if (groupsSection != null) {
            for (String groupId : groupsSection.getKeys(false)) {
                ConfigurationSection groupSection = groupsSection.getConfigurationSection(groupId);
                if (groupSection != null) {
                    String displayName = groupSection.getString("displayName", groupId);
                    List<String> regions = groupSection.getStringList("regions");
                    Map<String, Object> metadata = new HashMap<>();
                    
                    ConfigurationSection metadataSection = groupSection.getConfigurationSection("metadata");
                    if (metadataSection != null) {
                        for (String key : metadataSection.getKeys(false)) {
                            metadata.put(key, metadataSection.get(key));
                        }
                    }

                    CellGroup group = new CellGroup(groupId, displayName, new HashSet<>(regions), metadata);
                    groups.put(groupId, group);
                }
            }
        }

        plugin.getLogger().info("Loaded " + groups.size() + " cell groups");
    }

    /**
     * Save groups to configuration
     */
    public void saveGroups() {
        YamlConfiguration config = new YamlConfiguration();
        
        for (CellGroup group : groups.values()) {
            String path = "groups." + group.getId();
            config.set(path + ".displayName", group.getDisplayName());
            config.set(path + ".regions", new ArrayList<>(group.getRegionIds()));
            
            Map<String, Object> metadata = group.getAllMetadata();
            if (!metadata.isEmpty()) {
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    config.set(path + ".metadata." + entry.getKey(), entry.getValue());
                }
            }
        }

        try {
            config.save(groupsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save groups.yml file", e);
        }
    }

    /**
     * Create a new cell group
     */
    public boolean createGroup(String id, String displayName) {
        if (groups.containsKey(id)) {
            return false;
        }
        groups.put(id, new CellGroup(id, displayName, new HashSet<>(), new HashMap<>()));
        saveGroups();
        return true;
    }

    /**
     * Delete a cell group
     */
    public boolean deleteGroup(String id) {
        if (groups.remove(id) != null) {
            saveGroups();
            return true;
        }
        return false;
    }

    /**
     * Get a cell group by ID
     */
    public CellGroup getGroup(String id) {
        return groups.get(id);
    }

    /**
     * Get all cell groups
     */
    public Collection<CellGroup> getAllGroups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    /**
     * Add a region to a group
     */
    public boolean addRegionToGroup(String groupId, String regionId) {
        CellGroup group = groups.get(groupId);
        if (group != null && group.addRegion(regionId)) {
            saveGroups();
            return true;
        }
        return false;
    }

    /**
     * Remove a region from a group
     */
    public boolean removeRegionFromGroup(String groupId, String regionId) {
        CellGroup group = groups.get(groupId);
        if (group != null && group.removeRegion(regionId)) {
            saveGroups();
            return true;
        }
        return false;
    }

    /**
     * Get all groups containing a region
     */
    public List<CellGroup> getGroupsForRegion(String regionId) {
        return groups.values().stream()
                .filter(group -> group.containsRegion(regionId))
                .collect(Collectors.toList());
    }

    /**
     * Check if a player is within any region of a group
     */
    public boolean isPlayerInGroup(Player player, String groupId) {
        CellGroup group = groups.get(groupId);
        if (group == null || player == null) return false;

        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) return false;

        List<Region> regions = new ArrayList<Region>();
        for (Region region : arm.getRegionManager()) {
            regions.add(region);
        }
        return group.containsPlayer(player, regions);
    }

    /**
     * Get all groups a player is currently in
     */
    public List<CellGroup> getGroupsForPlayer(Player player) {
        if (player == null) return Collections.emptyList();

        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) return Collections.emptyList();

        List<Region> regions = new ArrayList<Region>();
        for (Region region : arm.getRegionManager()) {
            regions.add(region);
        }
        return groups.values().stream()
                .filter(group -> group.containsPlayer(player, regions))
                .collect(Collectors.toList());
    }

    /**
     * Get all regions in a group
     */
    public List<Region> getRegionsInGroup(String groupId) {
        CellGroup group = groups.get(groupId);
        if (group == null) return Collections.emptyList();

        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) return Collections.emptyList();

        List<Region> regions = new ArrayList<Region>();
        for (Region region : arm.getRegionManager()) {
            if (group.containsRegion(region.getRegion().getId())) {
                regions.add(region);
            }
        }
        return regions;
    }
} 