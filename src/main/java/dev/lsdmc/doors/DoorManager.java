package dev.lsdmc.doors;

import dev.lsdmc.EdenCells;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Simplified Door Manager that only handles door-region linking and basic permissions.
 * Doors can only be opened by region owners and members.
 */
public class DoorManager implements Listener {
    private final EdenCells plugin;
    private final Map<Location, String> doorToRegion = new HashMap<>();
    private final Set<Material> validDoorMaterials = new HashSet<>();
    private final File doorFile;
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_TIME = 500; // 0.5 seconds
    
    private boolean playSounds;
    private String openSound;
    private String closeSound;
    private double soundVolume;

    public DoorManager(EdenCells plugin) {
        this.plugin = plugin;
        this.doorFile = new File(plugin.getDataFolder(), "doors.yml");
        loadConfig();
        loadDoors();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        playSounds = config.getBoolean("door-settings.play-sounds", true);
        openSound = config.getString("door-settings.open-sound", "BLOCK_IRON_DOOR_OPEN");
        closeSound = config.getString("door-settings.close-sound", "BLOCK_IRON_DOOR_CLOSE");
        soundVolume = config.getDouble("door-settings.sound-volume", 1.0);

        List<String> materials = config.getStringList("door-settings.materials");
        validDoorMaterials.clear();
        for (String material : materials) {
            try {
                validDoorMaterials.add(Material.valueOf(material.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid door material: " + material);
            }
        }

        if (validDoorMaterials.isEmpty()) {
            validDoorMaterials.add(Material.IRON_DOOR);
        }
    }

    private void loadDoors() {
        if (!doorFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(doorFile);
        for (String key : config.getKeys(false)) {
            String locationStr = config.getString(key + ".location");
            String regionId = config.getString(key + ".region");
            
            if (locationStr != null && regionId != null) {
                Location location = parseLocation(locationStr);
                if (location != null) {
                    doorToRegion.put(location, regionId);
                }
            }
        }
        plugin.log("Loaded " + doorToRegion.size() + " door links");
    }

    public void saveDoorData() {
        YamlConfiguration config = new YamlConfiguration();
        int index = 0;
        for (Map.Entry<Location, String> entry : doorToRegion.entrySet()) {
            String key = "door_" + index++;
            config.set(key + ".location", locationToString(entry.getKey()));
            config.set(key + ".region", entry.getValue());
        }

        try {
            config.save(doorFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save door data", e);
        }
    }

    public void linkDoor(Location location, String regionId) {
        doorToRegion.put(location, regionId);
        saveDoorData();
    }

    public void unlinkDoor(Location location) {
        doorToRegion.remove(location);
        saveDoorData();
    }

    public String getLinkedRegion(Location location) {
        return doorToRegion.get(location);
    }

    public boolean isDoorLinked(Location location) {
        return doorToRegion.containsKey(location);
    }

    public Set<Location> getDoorsForRegion(String regionId) {
        return doorToRegion.entrySet().stream()
                .filter(entry -> entry.getValue().equals(regionId))
                .map(Map.Entry::getKey)
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
    }

    public boolean isValidDoorMaterial(Material material) {
        return validDoorMaterials.contains(material);
    }

    public Block getBottomDoorBlock(Block block) {
        if (block == null || !isValidDoorMaterial(block.getType())) {
            return null;
        }

        BlockData data = block.getBlockData();
        if (data instanceof Door) {
            Door door = (Door) data;
            if (door.getHalf() == Bisected.Half.TOP) {
                return block.getRelative(BlockFace.DOWN);
            }
        }
        return block;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !isValidDoorMaterial(block.getType())) {
            return;
        }

        Player player = event.getPlayer();
        Block bottomDoor = getBottomDoorBlock(block);
        if (bottomDoor == null) {
            return;
        }

        String regionId = getLinkedRegion(bottomDoor.getLocation());
        if (regionId == null) {
            return; // Not a linked door
        }

        // Check cooldown
        long lastUse = playerCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long now = System.currentTimeMillis();
        if (now - lastUse < COOLDOWN_TIME) {
            event.setCancelled(true);
            return;
        }

        // Find the region
        Region region = plugin.findRegionById(regionId);
        if (region == null) {
            player.sendMessage(Component.text("Error: Region not found!").color(NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        // Check permissions - owners, members, and WorldGuard region members can use doors
        boolean hasPermission = false;
        
        // Check ARM region permissions
        UUID regionOwner = region.getOwner();
        if (regionOwner != null && regionOwner.equals(player.getUniqueId())) {
            hasPermission = true;
        } else if (region.getRegion().getMembers().contains(player.getUniqueId())) {
            hasPermission = true;
        }

        // Check WorldGuard region permissions if ARM check failed
        if (!hasPermission) {
            try {
                WorldGuard worldGuard = WorldGuard.getInstance();
                RegionManager regionManager = worldGuard.getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(player.getWorld()));
                
                if (regionManager != null) {
                    BlockVector3 location = BukkitAdapter.asBlockVector(player.getLocation());
                    for (ProtectedRegion wgRegion : regionManager.getApplicableRegions(location)) {
                        if (wgRegion.getId().equals(regionId)) {
                            if (wgRegion.isOwner(WorldGuardPlugin.inst().wrapPlayer(player)) || 
                                wgRegion.isMember(WorldGuardPlugin.inst().wrapPlayer(player))) {
                                hasPermission = true;
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error checking WorldGuard permissions: " + e.getMessage());
            }
        }

        // Allow admins to use doors
        if (!hasPermission && player.hasPermission("edencells.admin.doors")) {
            hasPermission = true;
        }

        if (!hasPermission) {
            player.sendMessage(Component.text("You don't have permission to use this door!").color(NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        playerCooldowns.put(player.getUniqueId(), now);

        // Toggle door
        BlockData blockData = bottomDoor.getBlockData();
        if (blockData instanceof Door) {
            Door doorData = (Door) blockData;
            doorData.setOpen(!doorData.isOpen());
            bottomDoor.setBlockData(doorData);

            // Handle double doors
            Block doubleDoor = findDoubleDoor(bottomDoor);
            if (doubleDoor != null) {
                BlockData doubleData = doubleDoor.getBlockData();
                if (doubleData instanceof Door) {
                    Door doubleDoorData = (Door) doubleData;
                    doubleDoorData.setOpen(doorData.isOpen());
                    doubleDoor.setBlockData(doubleData);
                }
            }

            // Play sound if enabled
            if (playSounds) {
                try {
                    Sound sound = Sound.valueOf(doorData.isOpen() ? openSound : closeSound);
                    bottomDoor.getWorld().playSound(
                        bottomDoor.getLocation(),
                        sound,
                        (float) soundVolume,
                        1.0f
                    );
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound: " + (doorData.isOpen() ? openSound : closeSound));
                }
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isValidDoorMaterial(block.getType())) {
            return;
        }

        Player player = event.getPlayer();
        Block bottomDoor = getBottomDoorBlock(block);
        if (bottomDoor == null) {
            return;
        }

        String regionId = getLinkedRegion(bottomDoor.getLocation());
        if (regionId != null) {
            if (!player.hasPermission("edencells.admin.doors")) {
                event.setCancelled(true);
                player.sendMessage(Component.text("You cannot break doors linked to cells!")
                    .color(NamedTextColor.RED));
                return;
            }
            unlinkDoor(bottomDoor.getLocation());
            player.sendMessage(Component.text("Door unlinked from region: " + regionId)
                .color(NamedTextColor.YELLOW));
        }
    }

    private Block findDoubleDoor(Block door) {
        BlockData data = door.getBlockData();
        if (!(data instanceof Door)) return null;

        Door doorData = (Door) data;
        BlockFace facing = ((Directional) doorData).getFacing();
        
        // Check both sides for potential double door
        Block[] checkBlocks = {
            door.getRelative(facing.getOppositeFace()),
            door.getRelative(facing)
        };

        for (Block checkBlock : checkBlocks) {
            if (checkBlock.getType() == door.getType()) {
                BlockData checkData = checkBlock.getBlockData();
                if (checkData instanceof Door) {
                    Door checkDoor = (Door) checkData;
                    if (((Directional) checkDoor).getFacing() == facing &&
                        checkDoor.getHalf() == doorData.getHalf()) {
                        return checkBlock;
                    }
                }
            }
        }
        return null;
    }

    public void onDisable() {
        saveDoorData();
    }

    public void cleanupCooldowns() {
        long now = System.currentTimeMillis();
        playerCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > COOLDOWN_TIME);
    }

    private Location parseLocation(String locationStr) {
        try {
            String[] parts = locationStr.split(",");
            if (parts.length >= 4) {
                World world = Bukkit.getWorld(parts[0]);
                double x = Double.parseDouble(parts[1]);
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]);
                return new Location(world, x, y, z);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse location: " + locationStr);
        }
        return null;
    }

    private String locationToString(Location location) {
        return String.format("%s,%.2f,%.2f,%.2f",
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ());
    }

    public int getDoorCount() {
        return doorToRegion.size();
    }
}


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT.jar!\dev\lsdmc\doors\DoorManager.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */