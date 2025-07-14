package dev.lsdmc.edencells.commands;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.managers.DoorManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class DoorCommands implements CommandExecutor, TabCompleter {
    private final EdenCells plugin;
    private final DoorManager doorManager;
    
    public DoorCommands(EdenCells plugin, DoorManager doorManager) {
        this.plugin = plugin;
        this.doorManager = doorManager;
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!").color(NamedTextColor.RED));
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(player);
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "link":
                if (!player.hasPermission("edencells.admin.doors")) {
                    MessageUtils.sendNoPermission(player);
                    return true;
                }
                if (args.length < 2) {
                    MessageUtils.sendError(player, "Usage: /door link <region>");
                    return true;
                }
                handleLinkCommand(player, args[1]);
                return true;
                
            case "unlink":
                if (!player.hasPermission("edencells.admin.doors")) {
                    MessageUtils.sendNoPermission(player);
                    return true;
                }
                handleUnlinkCommand(player);
                return true;
                
            case "info":
                if (!player.hasPermission("edencells.admin.doors")) {
                    MessageUtils.sendNoPermission(player);
                    return true;
                }
                handleInfoCommand(player);
                return true;
                
            case "help":
                sendUsage(player);
                return true;
                
            default:
                sendUsage(player);
                return true;
        }
    }
    
    private void handleLinkCommand(Player player, String regionId) {
        // Find the region
        Region region = plugin.findRegionById(regionId);
        if (region == null) {
            MessageUtils.sendError(player, "Region '%s' not found!", regionId);
            return;
        }
        
        // Get the door the player is looking at
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !doorManager.isValidDoorMaterial(targetBlock.getType())) {
            MessageUtils.sendError(player, "Look at a door block to link it!");
            MessageUtils.sendInfo(player, "Valid door materials: " + 
                String.join(", ", plugin.getConfig().getStringList("doors.valid-materials")));
            return;
        }
        
        // Get bottom door block
        Block bottomDoor = doorManager.getBottomDoorBlock(targetBlock);
        if (bottomDoor == null) {
            MessageUtils.sendError(player, "Invalid door block!");
            return;
        }
        
        // Check if already linked
        if (doorManager.isDoorLinked(bottomDoor.getLocation())) {
            String existingRegion = doorManager.getLinkedRegion(bottomDoor.getLocation());
            MessageUtils.sendError(player, "This door is already linked to region: %s", existingRegion);
            return;
        }
        
        // Link the door
        doorManager.linkDoor(bottomDoor.getLocation(), regionId);
        MessageUtils.sendSuccess(player, "Door linked to region '%s' successfully!", regionId);
        MessageUtils.sendInfo(player, "Only the owner and members of this region can now use this door.");
    }
    
    private void handleUnlinkCommand(Player player) {
        // Get the door the player is looking at
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !doorManager.isValidDoorMaterial(targetBlock.getType())) {
            MessageUtils.sendError(player, "Look at a door block to unlink it!");
            return;
        }
        
        // Get bottom door block
        Block bottomDoor = doorManager.getBottomDoorBlock(targetBlock);
        if (bottomDoor == null) {
            MessageUtils.sendError(player, "Invalid door block!");
            return;
        }
        
        // Check if linked
        String linkedRegion = doorManager.getLinkedRegion(bottomDoor.getLocation());
        if (linkedRegion == null) {
            MessageUtils.sendError(player, "This door is not linked to any region!");
            return;
        }
        
        // Unlink the door
        doorManager.unlinkDoor(bottomDoor.getLocation());
        MessageUtils.sendSuccess(player, "Door unlinked from region '%s' successfully!", linkedRegion);
    }
    
    private void handleInfoCommand(Player player) {
        // Get the door the player is looking at
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !doorManager.isValidDoorMaterial(targetBlock.getType())) {
            MessageUtils.sendError(player, "Look at a door block to get info!");
            return;
        }
        
        // Get bottom door block
        Block bottomDoor = doorManager.getBottomDoorBlock(targetBlock);
        if (bottomDoor == null) {
            MessageUtils.sendError(player, "Invalid door block!");
            return;
        }
        
        // Get linked region
        String linkedRegion = doorManager.getLinkedRegion(bottomDoor.getLocation());
        if (linkedRegion == null) {
            MessageUtils.sendInfo(player, "This door is not linked to any region.");
        } else {
            MessageUtils.sendInfo(player, "This door is linked to region: %s", linkedRegion);
            
            // Get region info
            Region region = plugin.findRegionById(linkedRegion);
            if (region != null) {
                UUID ownerUUID = region.getOwner();
                String ownerName = "None";
                if (ownerUUID != null) {
                    String name = Bukkit.getOfflinePlayer(ownerUUID).getName();
                    ownerName = (name != null) ? name : "Unknown";
                }
                MessageUtils.sendInfo(player, "Region owner: %s", ownerName);
                
                int memberCount = region.getRegion().getMembers().size();
                MessageUtils.sendInfo(player, "Region members: %d", memberCount);
                
                // Door state
                BlockData blockData = bottomDoor.getBlockData();
                if (blockData instanceof Door doorData) {
                    MessageUtils.sendInfo(player, "Door state: %s", doorData.isOpen() ? "Open" : "Closed");
                }
            } else {
                MessageUtils.sendError(player, "Region not found!");
            }
        }
        
        // Door location
        MessageUtils.sendInfo(player, "Door location: %s", formatLocation(bottomDoor.getLocation()));
    }
    
    private void sendUsage(Player player) {
        MessageUtils.send(player, "<color:#9D4EDD>=== Door Management Commands ===</color>");
        if (player.hasPermission("edencells.admin.doors")) {
            MessageUtils.send(player, "<color:#FFB3C6>/door link <region></color> <color:#06FFA5>- Link the door you're looking at to a region</color>");
            MessageUtils.send(player, "<color:#FFB3C6>/door unlink</color> <color:#06FFA5>- Unlink the door you're looking at</color>");
            MessageUtils.send(player, "<color:#FFB3C6>/door info</color> <color:#06FFA5>- Get information about the door you're looking at</color>");
            MessageUtils.send(player, "<color:#FFB3C6>/door help</color> <color:#06FFA5>- Show this help message</color>");
            MessageUtils.send(player, "");
            MessageUtils.send(player, "<color:#ADB5BD>Note: Look at a door block when using these commands.</color>");
        } else {
            MessageUtils.send(player, "<color:#FF6B6B>You don't have permission to use door commands.</color>");
        }
    }
    
    private String formatLocation(Location loc) {
        return String.format("%s: %d, %d, %d", 
            loc.getWorld().getName(), 
            loc.getBlockX(), 
            loc.getBlockY(), 
            loc.getBlockZ());
    }
    
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (sender instanceof Player player && player.hasPermission("edencells.admin.doors")) {
            if (args.length == 1) {
                completions.addAll(Arrays.asList("link", "unlink", "info", "help"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("link")) {
                // Add region IDs for tab completion
                try {
                    AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
                    if (arm != null) {
                        for (Region region : arm.getRegionManager()) {
                            completions.add(region.getRegion().getId());
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to get regions for tab completion: " + e.getMessage());
                }
            }
            
            return StringUtil.copyPartialMatches(args[args.length - 1], completions, new ArrayList<>());
        }
        
        return completions;
    }
}


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT (1).jar!\dev\lsdmc\commands\DoorCommands.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */