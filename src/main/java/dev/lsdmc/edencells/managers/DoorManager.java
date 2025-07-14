package dev.lsdmc.edencells.managers;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.security.SecurityManager;
import dev.lsdmc.edencells.utils.Constants;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages door linking to cells
 */
public final class DoorManager {
    
    private final EdenCells plugin;
    private final CellManager cellManager;
    private final SecurityManager security;
    
    // Map of door location -> region ID
    private final Map<String, String> doorLinks = new ConcurrentHashMap<>();
    private final Set<Material> validDoorMaterials = new HashSet<>();
    private File doorsFile;
    
    // Door interaction cooldowns - player UUID -> last interaction time
    private final Map<UUID, Long> doorCooldowns = new ConcurrentHashMap<>();
    private static final long DOOR_COOLDOWN_MS = 500; // 500ms cooldown
    
    // Sound settings
    private boolean playSounds;
    private Sound openSound;
    private Sound closeSound;
    private float soundVolume;
    private float soundPitch;
    
    public DoorManager(EdenCells plugin, CellManager cellManager, SecurityManager security) {
        this.plugin = plugin;
        this.cellManager = cellManager;
        this.security = security;
        this.doorsFile = new File(plugin.getDataFolder(), "doors.yml");
        
        loadConfig();
        loadDoors();
    }
    
    /**
     * Load configuration settings
     */
    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        
        // Load sound settings
        playSounds = config.getBoolean("doors.sounds.enabled", true);
        String openSoundName = config.getString("doors.sounds.open-sound", "BLOCK_IRON_DOOR_OPEN");
        String closeSoundName = config.getString("doors.sounds.close-sound", "BLOCK_IRON_DOOR_CLOSE");
        soundVolume = (float) config.getDouble("doors.sounds.volume", 1.0);
        soundPitch = (float) config.getDouble("doors.sounds.pitch", 1.0);
        
        // Parse sounds
        try {
            openSound = Sound.valueOf(openSoundName.toUpperCase().replace(".", "_"));
        } catch (IllegalArgumentException e) {
            openSound = Sound.BLOCK_IRON_DOOR_OPEN;
            plugin.getLogger().warning("Invalid open sound: " + openSoundName + ", using default");
        }
        
        try {
            closeSound = Sound.valueOf(closeSoundName.toUpperCase().replace(".", "_"));
        } catch (IllegalArgumentException e) {
            closeSound = Sound.BLOCK_IRON_DOOR_CLOSE;
            plugin.getLogger().warning("Invalid close sound: " + closeSoundName + ", using default");
        }
        
        // Load valid door materials
        validDoorMaterials.clear();
        List<String> materials = config.getStringList("doors.valid-materials");
        if (materials.isEmpty()) {
            // Default materials
            validDoorMaterials.add(Material.IRON_DOOR);
            validDoorMaterials.add(Material.OAK_DOOR);
            validDoorMaterials.add(Material.SPRUCE_DOOR);
            validDoorMaterials.add(Material.BIRCH_DOOR);
            validDoorMaterials.add(Material.JUNGLE_DOOR);
            validDoorMaterials.add(Material.ACACIA_DOOR);
            validDoorMaterials.add(Material.DARK_OAK_DOOR);
            validDoorMaterials.add(Material.CRIMSON_DOOR);
            validDoorMaterials.add(Material.WARPED_DOOR);
            validDoorMaterials.add(Material.IRON_TRAPDOOR);
        } else {
            for (String mat : materials) {
                try {
                    validDoorMaterials.add(Material.valueOf(mat.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid door material: " + mat);
                }
            }
        }
    }
    
    /**
     * Link a door to a region
     * @param location The door location (bottom block)
     * @param regionId The region ID
     */
    public void linkDoor(Location location, String regionId) {
        String key = locationToKey(location);
        doorLinks.put(key, regionId);
        saveDoors();
        plugin.debug("Linked door at " + key + " to region " + regionId);
    }
    
    /**
     * Unlink a door
     * @param location The door location
     */
    public void unlinkDoor(Location location) {
        String key = locationToKey(location);
        String regionId = doorLinks.remove(key);
        if (regionId != null) {
            saveDoors();
            plugin.debug("Unlinked door at " + key + " from region " + regionId);
        }
    }
    
    /**
     * Get the region ID linked to a door
     * @param location The door location
     * @return Region ID or null
     */
    public String getLinkedRegion(Location location) {
        // Check bottom block location
        Block bottom = getBottomDoorBlock(location.getBlock());
        if (bottom != null) {
            String key = locationToKey(bottom.getLocation());
            return doorLinks.get(key);
        }
        return null;
    }
    
    /**
     * Check if a door is linked
     * @param location The door location
     * @return true if linked
     */
    public boolean isDoorLinked(Location location) {
        return getLinkedRegion(location) != null;
    }
    
    /**
     * Check if a player can access a linked door
     * @param player The player
     * @param location The door location
     * @return true if the player can access the door
     */
    public boolean canAccessDoor(Player player, Location location) {
        String regionId = getLinkedRegion(location);
        if (regionId == null) {
            return true; // Unlinked doors are accessible to everyone
        }
        
        // Find the region
        Region region = plugin.findRegionById(regionId);
        if (region == null) {
            // Region no longer exists - should be cleaned up
            plugin.debug("Door linked to non-existent region: " + regionId);
            return false;
        }
        
        // Check if player is owner or member of the region
        return cellManager.isOwner(player, region) || cellManager.hasAccess(player, region);
    }
    
    /**
     * Check if a player is on door cooldown
     * @param player The player
     * @return true if on cooldown
     */
    public boolean isOnDoorCooldown(Player player) {
        Long lastInteraction = doorCooldowns.get(player.getUniqueId());
        if (lastInteraction == null) {
            return false;
        }
        
        long timeSince = System.currentTimeMillis() - lastInteraction;
        if (timeSince < DOOR_COOLDOWN_MS) {
            return true;
        }
        
        // Clean up old entry
        doorCooldowns.remove(player.getUniqueId());
        return false;
    }
    
    /**
     * Set door cooldown for a player
     * @param player The player
     */
    private void setDoorCooldown(Player player) {
        doorCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }
    
    /**
     * Toggle a door open/closed
     * @param block The door block
     */
    public void toggleDoor(Block block) {
        if (!isValidDoor(block.getType())) {
            return;
        }
        
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof Openable openable)) {
            return;
        }
        
        // Toggle the door state
        boolean wasOpen = openable.isOpen();
        openable.setOpen(!wasOpen);
        block.setBlockData(openable);
        
        // Handle double doors
        Block otherHalf = findDoubleDoor(block);
        if (otherHalf != null) {
            BlockData otherData = otherHalf.getBlockData();
            if (otherData instanceof Openable otherOpenable) {
                otherOpenable.setOpen(!wasOpen);
                otherHalf.setBlockData(otherOpenable);
            }
        }
        
        // Play sound
        if (playSounds) {
            Sound sound = wasOpen ? closeSound : openSound;
            block.getWorld().playSound(block.getLocation(), sound, soundVolume, soundPitch);
        }
        
        plugin.debug("Toggled door at " + locationToKey(block.getLocation()) + " to " + (!wasOpen ? "open" : "closed"));
    }
    
    /**
     * Toggle a door for a player (with cooldown)
     * @param block The door block
     * @param player The player toggling the door
     * @return true if door was toggled, false if on cooldown
     */
    public boolean toggleDoorForPlayer(Block block, Player player) {
        if (isOnDoorCooldown(player)) {
            return false;
        }
        
        toggleDoor(block);
        setDoorCooldown(player);
        return true;
    }
    
    /**
     * Find the other half of a double door
     * @param door The door block
     * @return The other door block or null
     */
    private Block findDoubleDoor(Block door) {
        BlockData data = door.getBlockData();
        if (!(data instanceof Door doorData)) {
            return null;
        }
        
        BlockFace facing = doorData.getFacing();
        BlockFace hinge = doorData.getHinge() == Door.Hinge.LEFT ? facing.getOppositeFace() : facing;
        
        // Check adjacent blocks
        Block[] adjacent = {
            door.getRelative(hinge.getOppositeFace()),
            door.getRelative(hinge)
        };
        
        for (Block check : adjacent) {
            if (check.getType() == door.getType()) {
                BlockData checkData = check.getBlockData();
                if (checkData instanceof Door checkDoor) {
                    // Same facing and half (top/bottom)
                    if (checkDoor.getFacing() == facing && 
                        checkDoor.getHalf() == doorData.getHalf()) {
                        return check;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Get the bottom block of a door
     * @param block The door block
     * @return The bottom block or null
     */
    public Block getBottomDoorBlock(Block block) {
        if (!isValidDoor(block.getType())) {
            return null;
        }
        
        BlockData data = block.getBlockData();
        if (data instanceof Door doorData) {
            if (doorData.getHalf() == Bisected.Half.TOP) {
                return block.getRelative(BlockFace.DOWN);
            }
        }
        
        return block;
    }
    
    /**
     * Check if a material is a valid door
     * @param material The material
     * @return true if valid door
     */
    public boolean isValidDoor(Material material) {
        return validDoorMaterials.contains(material);
    }
    
    /**
     * Check if a material is a valid door (alias for backward compatibility)
     * @param material The material
     * @return true if valid door
     */
    public boolean isValidDoorMaterial(Material material) {
        return isValidDoor(material);
    }
    
    /**
     * Get the linked cell (backward compatibility)
     * @param location The door location
     * @return Region ID and world or null
     */
    public String getLinkedCell(Location location) {
        String regionId = getLinkedRegion(location);
        if (regionId != null) {
            return regionId + ":" + location.getWorld().getName();
        }
        return null;
    }
    
    /**
     * Load door links from file
     */
    public void loadDoors() {
        if (!doorsFile.exists()) {
            plugin.getLogger().info("No doors.yml file found, starting fresh");
            return;
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(doorsFile);
        doorLinks.clear();
        
        for (String key : config.getKeys(false)) {
            String regionId = config.getString(key);
            if (regionId != null) {
                doorLinks.put(key, regionId);
            }
        }
        
        plugin.getLogger().info("Loaded " + doorLinks.size() + " door links");
    }
    
    /**
     * Save door links to file
     */
    public void saveDoors() {
        FileConfiguration config = new YamlConfiguration();
        
        for (Map.Entry<String, String> entry : doorLinks.entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }
        
        try {
            config.save(doorsFile);
            plugin.debug("Saved " + doorLinks.size() + " door links");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save door links: " + e.getMessage());
        }
    }
    
    /**
     * Convert location to string key
     * @param location The location
     * @return String key
     */
    private String locationToKey(Location location) {
        return String.format("%s:%d:%d:%d",
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }
    
    /**
     * Get all door links for debugging
     * @return Map of door locations to region IDs
     */
    public Map<String, String> getAllDoorLinks() {
        return new HashMap<>(doorLinks);
    }
    
    /**
     * Get the number of linked doors
     * @return Door count
     */
    public int getDoorCount() {
        return doorLinks.size();
    }
    
    /**
     * Clean up invalid door links
     * @return Number of removed links
     */
    public int cleanupInvalidLinks() {
        int removed = 0;
        Iterator<Map.Entry<String, String>> iter = doorLinks.entrySet().iterator();
        
        while (iter.hasNext()) {
            Map.Entry<String, String> entry = iter.next();
            String regionId = entry.getValue();
            
            // Check if region still exists
            Region region = plugin.findRegionById(regionId);
            if (region == null) {
                iter.remove();
                removed++;
                plugin.getLogger().info("Removed invalid door link to non-existent region: " + regionId);
            }
        }
        
        if (removed > 0) {
            saveDoors();
        }
        
        return removed;
    }
    
    /**
     * Sync door ownership for a specific region when ownership changes
     * @param regionId The region ID that changed ownership
     */
    public void syncDoorOwnershipForRegion(String regionId) {
        plugin.debug("Syncing door ownership for region: " + regionId);
        
        // Find all doors linked to this region
        List<Location> linkedDoors = findDoorsLinkedToRegion(regionId);
        
        if (linkedDoors.isEmpty()) {
            plugin.debug("No doors linked to region: " + regionId);
            return;
        }
        
        // Verify the region still exists
        Region region = plugin.findRegionById(regionId);
        if (region == null) {
            plugin.debug("Region no longer exists, unlinking doors: " + regionId);
            // Region no longer exists, unlink all doors
            for (Location doorLocation : linkedDoors) {
                unlinkDoor(doorLocation);
            }
            return;
        }
        
        plugin.debug("Found " + linkedDoors.size() + " doors linked to region " + regionId);
        
        // If region is not sold, doors should remain linked but no one has access
        if (!cellManager.isSold(region)) {
            plugin.debug("Region " + regionId + " is not sold, doors remain linked but no access");
            return;
        }
        
        // Doors are properly synced - access is checked dynamically in canAccessDoor
        plugin.debug("Door ownership synced for region: " + regionId);
    }
    
    /**
     * Find all doors linked to a specific region
     * @param regionId The region ID
     * @return List of door locations
     */
    public List<Location> findDoorsLinkedToRegion(String regionId) {
        List<Location> linkedDoors = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : doorLinks.entrySet()) {
            if (regionId.equals(entry.getValue())) {
                Location doorLocation = parseLocationFromKey(entry.getKey());
                if (doorLocation != null) {
                    linkedDoors.add(doorLocation);
                }
            }
        }
        
        return linkedDoors;
    }
    
    /**
     * Sync all door ownerships - used by sync command
     * @return Number of doors synced
     */
    public int syncAllDoorOwnerships() {
        plugin.debug("Starting full door ownership sync");
        
        int syncedDoors = 0;
        Map<String, List<Location>> regionDoors = new HashMap<>();
        
        // Group doors by region
        for (Map.Entry<String, String> entry : doorLinks.entrySet()) {
            String regionId = entry.getValue();
            Location doorLocation = parseLocationFromKey(entry.getKey());
            
            if (doorLocation != null) {
                regionDoors.computeIfAbsent(regionId, k -> new ArrayList<>()).add(doorLocation);
            }
        }
        
        // Sync each region's doors
        for (String regionId : regionDoors.keySet()) {
            syncDoorOwnershipForRegion(regionId);
            syncedDoors += regionDoors.get(regionId).size();
        }
        
        plugin.debug("Synced " + syncedDoors + " doors across " + regionDoors.size() + " regions");
        return syncedDoors;
    }
    
    /**
     * Parse location from door key
     * @param doorKey The door key (world:x:y:z format)
     * @return The location or null if invalid
     */
    private Location parseLocationFromKey(String doorKey) {
        try {
            String[] parts = doorKey.split(":");
            if (parts.length != 4) {
                return null;
            }
            
            String worldName = parts[0];
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            
            org.bukkit.World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                return null;
            }
            
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
} 