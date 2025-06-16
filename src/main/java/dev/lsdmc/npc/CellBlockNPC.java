package dev.lsdmc.npc;

import dev.lsdmc.EdenCells;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.regex.Pattern;

public class CellBlockNPC implements Listener {
    private final EdenCells plugin;
    private final Map<String, NPCData> blockNPCs = new HashMap<>();
    private final Map<Integer, String> npcBlockMap = new HashMap<>();

    // Pattern to match cell IDs that belong to a specific block
    // Examples: j1, j-1, j_1, j.1, j001, etc.
    private static final Pattern CELL_ID_PATTERN = Pattern.compile("^([a-zA-Z]+)[\\-_\\.]*\\d+.*$");

    // Teleportation settings
    private List<String> freeRanks;
    private List<String> donorCellPrefixes;
    private double teleportCost;
    private boolean requirePayment;

    public CellBlockNPC(EdenCells plugin) {
        this.plugin = plugin;
        loadTeleportConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadNPCs();
    }

    private static class NPCData {
        final NPC npc;
        final String blockId;
        final String displayName;
        final Location location;

        NPCData(NPC npc, String blockId, String displayName, Location location) {
            this.npc = npc;
            this.blockId = blockId;
            this.displayName = displayName;
            this.location = location;
        }
    }

    private void loadTeleportConfig() {
        freeRanks = plugin.getConfig().getStringList("teleportation.free-teleport-ranks");
        donorCellPrefixes = plugin.getConfig().getStringList("teleportation.donor-cell-prefixes");
        teleportCost = plugin.getConfig().getDouble("teleportation.teleport-cost", 50.0);
        requirePayment = plugin.getConfig().getBoolean("teleportation.require-payment", true);
        
        if (freeRanks == null) freeRanks = new ArrayList<>();
        if (donorCellPrefixes == null) donorCellPrefixes = new ArrayList<>();
    }

    private void loadNPCs() {
        ConfigurationSection npcSection = plugin.getConfig().getConfigurationSection("cell-blocks.npcs");
        if (npcSection == null) return;

        for (String blockId : npcSection.getKeys(false)) {
            ConfigurationSection blockSection = npcSection.getConfigurationSection(blockId);
            if (blockSection == null) continue;

            String worldName = blockSection.getString("world");
            double x = blockSection.getDouble("x");
            double y = blockSection.getDouble("y");
            double z = blockSection.getDouble("z");
            float yaw = (float) blockSection.getDouble("yaw", 0.0);
            float pitch = (float) blockSection.getDouble("pitch", 0.0);
            String displayName = blockSection.getString("displayName", blockId + " Block");

            if (worldName == null || Bukkit.getWorld(worldName) == null) {
                plugin.getLogger().warning("Invalid world for NPC in block " + blockId);
                continue;
            }

            Location loc = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
            createNPCInternal(blockId, displayName, loc);
        }
    }

    public boolean createNPC(String blockId, String displayName, Location location) {
        if (blockNPCs.containsKey(blockId.toLowerCase())) {
            return false;
        }

        try {
            if (!plugin.getServer().getPluginManager().isPluginEnabled("Citizens")) {
                return false;
            }

            return createNPCInternal(blockId, displayName, location);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create NPC: " + e.getMessage());
            return false;
        }
    }

    private boolean createNPCInternal(String blockId, String displayName, Location location) {
        try {
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, displayName);
            npc.data().set("cellblock", blockId);
            npc.spawn(location);

            NPCData npcData = new NPCData(npc, blockId, displayName, location);
            blockNPCs.put(blockId.toLowerCase(), npcData);
            npcBlockMap.put(npc.getId(), blockId.toLowerCase());
            
            saveNPCs();
            plugin.debug("Created cell block NPC for " + blockId + " at " + location);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create NPC internally: " + e.getMessage());
            return false;
        }
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        NPC npc = event.getNPC();
        Player player = event.getClicker();
        String blockId = npcBlockMap.get(npc.getId());

        if (blockId == null) return;

        handleCellTeleport(player, blockId);
    }

    private void handleCellTeleport(Player player, String blockId) {
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) {
            player.sendMessage(Component.text("Cell system is currently unavailable.")
                .color(NamedTextColor.RED));
            return;
        }

        // Check if player has special rank for free donor cell teleportation
        if (hasSpecialRank(player)) {
            Region donorCell = findPlayerDonorCell(player);
            if (donorCell != null) {
                teleportPlayerToCell(player, donorCell, true);
                return;
            } else {
                player.sendMessage(Component.text("You don't own a donor cell to teleport to.")
                    .color(NamedTextColor.YELLOW));
                // Continue to check for regular cell in this block
            }
        }

        // Find player's cell in this specific block
        Region playerCell = findPlayerCellInBlock(player, blockId);

        if (playerCell == null) {
            player.sendMessage(Component.text("You don't own a cell in " + blockId.toUpperCase() + " block.")
                .color(NamedTextColor.RED));
            return;
        }

        // Check if payment is required and process it
        if (requirePayment && !hasSpecialRank(player)) {
            Economy economy = plugin.getEconomy();
            if (economy != null && teleportCost > 0) {
                if (!economy.has(player, teleportCost)) {
                    player.sendMessage(Component.text("You need $" + String.format("%.2f", teleportCost) + " to teleport to your cell.")
                        .color(NamedTextColor.RED));
                    return;
                }
                
                if (!economy.withdrawPlayer(player, teleportCost).transactionSuccess()) {
                    player.sendMessage(Component.text("Payment failed. Please try again.")
                        .color(NamedTextColor.RED));
                    return;
                }
                
                player.sendMessage(Component.text("$" + String.format("%.2f", teleportCost) + " charged for teleportation.")
                    .color(NamedTextColor.YELLOW));
            }
        }

        teleportPlayerToCell(player, playerCell, false);
    }

    private boolean hasSpecialRank(Player player) {
        if (freeRanks.isEmpty()) return false;
        
        for (String rank : freeRanks) {
            if (player.hasPermission("group." + rank.toLowerCase()) || 
                player.hasPermission("rank." + rank.toLowerCase()) ||
                player.hasPermission("edencells.rank." + rank.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private Region findPlayerDonorCell(Player player) {
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null || donorCellPrefixes.isEmpty()) return null;

        List<Region> ownedRegions = arm.getRegionManager().getRegionsByOwner(player.getUniqueId());
        
        for (Region region : ownedRegions) {
            String regionId = region.getRegion().getId().toLowerCase();
            for (String prefix : donorCellPrefixes) {
                if (regionId.startsWith(prefix.toLowerCase())) {
                    return region;
                }
            }
        }
        return null;
    }

    private void teleportPlayerToCell(Player player, Region region, boolean isDonorTeleport) {
        try {
            region.teleport(player, false);
            String cellType = isDonorTeleport ? "donor cell" : "cell";
            player.sendMessage(Component.text("Teleported to your " + cellType + " (" + region.getRegion().getId() + ").")
                .color(NamedTextColor.GREEN));
            
            if (isDonorTeleport) {
                player.sendMessage(Component.text("Free teleportation due to your rank!")
                    .color(NamedTextColor.GOLD));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to teleport: No safe location found.")
                .color(NamedTextColor.RED));
            plugin.getLogger().warning("Failed to teleport " + player.getName() + " to their cell: " + e.getMessage());
            
            // Refund if payment was taken
            if (requirePayment && !isDonorTeleport && teleportCost > 0) {
                Economy economy = plugin.getEconomy();
                if (economy != null) {
                    economy.depositPlayer(player, teleportCost);
                    player.sendMessage(Component.text("Teleportation fee refunded.")
                        .color(NamedTextColor.YELLOW));
                }
            }
        }
    }

    /**
     * Finds a player's cell in a specific block using multiple matching strategies
     */
    private Region findPlayerCellInBlock(Player player, String blockId) {
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) return null;

        List<Region> ownedRegions = arm.getRegionManager().getRegionsByOwner(player.getUniqueId());
        String normalizedBlockId = blockId.toLowerCase();

        // Strategy 1: Exact prefix match (most common)
        for (Region region : ownedRegions) {
            String regionId = region.getRegion().getId().toLowerCase();
            if (regionId.startsWith(normalizedBlockId)) {
                // Additional validation: ensure it's actually a cell in this block
                if (isCellInBlock(regionId, normalizedBlockId)) {
                    return region;
                }
            }
        }

        // Strategy 2: Pattern-based matching for complex naming schemes
        for (Region region : ownedRegions) {
            String regionId = region.getRegion().getId().toLowerCase();
            if (matchesCellPattern(regionId, normalizedBlockId)) {
                return region;
            }
        }

        // Strategy 3: Contains-based matching (fallback)
        for (Region region : ownedRegions) {
            String regionId = region.getRegion().getId().toLowerCase();
            if (regionId.contains(normalizedBlockId) && isCellInBlock(regionId, normalizedBlockId)) {
                return region;
            }
        }

        return null;
    }

    /**
     * Validates that a region ID represents a cell in the specified block
     */
    private boolean isCellInBlock(String regionId, String blockId) {
        // Check if the region ID follows a cell naming pattern
        if (!CELL_ID_PATTERN.matcher(regionId).matches()) {
            return false;
        }

        // Extract the block prefix from the region ID
        String extractedBlock = extractBlockFromRegionId(regionId);
        return extractedBlock != null && extractedBlock.equals(blockId);
    }

    /**
     * Extracts the block identifier from a region ID
     */
    private String extractBlockFromRegionId(String regionId) {
        // Handle common patterns: j1, j-1, j_1, j.1, etc.
        for (int i = 0; i < regionId.length(); i++) {
            char c = regionId.charAt(i);
            if (Character.isDigit(c) || c == '-' || c == '_' || c == '.') {
                return regionId.substring(0, i);
            }
        }
        return regionId; // If no separators found, return the whole string
    }

    /**
     * Advanced pattern matching for cell IDs
     */
    private boolean matchesCellPattern(String regionId, String blockId) {
        // Pattern 1: blockId followed by number (j1, j2, etc.)
        if (regionId.matches("^" + Pattern.quote(blockId) + "\\d+.*$")) {
            return true;
        }

        // Pattern 2: blockId with separators (j-1, j_1, j.1, etc.)
        if (regionId.matches("^" + Pattern.quote(blockId) + "[\\-_\\.]+\\d+.*$")) {
            return true;
        }

        // Pattern 3: blockId with zero-padded numbers (j001, j002, etc.)
        if (regionId.matches("^" + Pattern.quote(blockId) + "0+\\d+.*$")) {
            return true;
        }

        return false;
    }

    public boolean hasNPC(String blockId) {
        return blockNPCs.containsKey(blockId.toLowerCase());
    }

    public boolean removeNPC(String blockId) {
        NPCData npcData = blockNPCs.remove(blockId.toLowerCase());
        if (npcData != null) {
            npcBlockMap.remove(npcData.npc.getId());
            npcData.npc.destroy();
            saveNPCs();
            return true;
        }
        return false;
    }

    public List<String> getBlockIds() {
        return new ArrayList<>(blockNPCs.keySet());
    }

    public String getNPCDisplayName(String blockId) {
        NPCData npcData = blockNPCs.get(blockId.toLowerCase());
        return npcData != null ? npcData.displayName : "Unknown";
    }

    public String getNPCLocationString(String blockId) {
        NPCData npcData = blockNPCs.get(blockId.toLowerCase());
        if (npcData == null) return "Unknown";
        
        Location loc = npcData.location;
        return String.format("%s: %.1f, %.1f, %.1f", 
            loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    public boolean teleportToNPC(Player player, String blockId) {
        NPCData npcData = blockNPCs.get(blockId.toLowerCase());
        if (npcData == null) return false;

        try {
            player.teleport(npcData.location);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to teleport player to NPC: " + e.getMessage());
            return false;
        }
    }

    public void saveNPCs() {
        ConfigurationSection npcSection = plugin.getConfig().createSection("cell-blocks.npcs");

        for (NPCData npcData : blockNPCs.values()) {
            ConfigurationSection blockSection = npcSection.createSection(npcData.blockId);
            blockSection.set("world", npcData.location.getWorld().getName());
            blockSection.set("x", npcData.location.getX());
            blockSection.set("y", npcData.location.getY());
            blockSection.set("z", npcData.location.getZ());
            blockSection.set("yaw", npcData.location.getYaw());
            blockSection.set("pitch", npcData.location.getPitch());
            blockSection.set("displayName", npcData.displayName);
        }

        plugin.saveConfig();
    }

    public void cleanup() {
        for (NPCData npcData : blockNPCs.values()) {
            npcData.npc.destroy();
        }
        blockNPCs.clear();
        npcBlockMap.clear();
    }
} 