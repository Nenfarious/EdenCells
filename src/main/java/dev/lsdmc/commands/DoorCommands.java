package dev.lsdmc.commands;

import dev.lsdmc.EdenCells;
import dev.lsdmc.doors.DoorManager;
import dev.lsdmc.utils.MessageUtils;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
                    player.sendMessage(MessageUtils.noPermission("edencells.admin.doors"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(MessageUtils.error("Usage: /door link <region>"));
                    return true;
                }
                handleLinkCommand(player, args[1]);
                break;

            case "unlink":
                if (!player.hasPermission("edencells.admin.doors")) {
                    player.sendMessage(MessageUtils.noPermission("edencells.admin.doors"));
                    return true;
                }
                handleUnlinkCommand(player);
                break;

            case "info":
                if (!player.hasPermission("edencells.admin.doors")) {
                    player.sendMessage(MessageUtils.noPermission("edencells.admin.doors"));
                    return true;
                }
                handleInfoCommand(player);
                break;

            case "help":
                sendUsage(player);
                break;

            default:
                sendUsage(player);
                break;
        }

        return true;
    }

    private void handleLinkCommand(Player player, String regionId) {
        // Check if the region exists
        Region region = plugin.findRegionById(regionId);
        if (region == null) {
            player.sendMessage(MessageUtils.error("Region '" + regionId + "' not found!"));
            return;
        }

        // Get the block the player is looking at
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !doorManager.isValidDoorMaterial(targetBlock.getType())) {
            player.sendMessage(MessageUtils.error("Look at a door block to link it!"));
            player.sendMessage(MessageUtils.info("Valid door materials: " + 
                String.join(", ", plugin.getConfig().getStringList("door-settings.materials"))));
            return;
        }

        Block bottomDoor = doorManager.getBottomDoorBlock(targetBlock);
        if (bottomDoor == null) {
            player.sendMessage(MessageUtils.error("Invalid door block!"));
            return;
        }

        // Check if door is already linked
        if (doorManager.isDoorLinked(bottomDoor.getLocation())) {
            String existingRegion = doorManager.getLinkedRegion(bottomDoor.getLocation());
            player.sendMessage(MessageUtils.error("This door is already linked to region: " + existingRegion));
            return;
        }

        // Link the door
        doorManager.linkDoor(bottomDoor.getLocation(), regionId);
        player.sendMessage(MessageUtils.success("Door linked to region '" + regionId + "' successfully!"));
        player.sendMessage(MessageUtils.info("Only the owner and members of this region can now use this door."));
    }

    private void handleUnlinkCommand(Player player) {
        // Get the block the player is looking at
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !doorManager.isValidDoorMaterial(targetBlock.getType())) {
            player.sendMessage(MessageUtils.error("Look at a door block to unlink it!"));
            return;
        }

        Block bottomDoor = doorManager.getBottomDoorBlock(targetBlock);
        if (bottomDoor == null) {
            player.sendMessage(MessageUtils.error("Invalid door block!"));
            return;
        }

        String linkedRegion = doorManager.getLinkedRegion(bottomDoor.getLocation());
        if (linkedRegion == null) {
            player.sendMessage(MessageUtils.error("This door is not linked to any region!"));
            return;
        }

        doorManager.unlinkDoor(bottomDoor.getLocation());
        player.sendMessage(MessageUtils.success("Door unlinked from region '" + linkedRegion + "' successfully!"));
    }

    private void handleInfoCommand(Player player) {
        // Get the block the player is looking at
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !doorManager.isValidDoorMaterial(targetBlock.getType())) {
            player.sendMessage(MessageUtils.error("Look at a door block to get info!"));
            return;
        }

        Block bottomDoor = doorManager.getBottomDoorBlock(targetBlock);
        if (bottomDoor == null) {
            player.sendMessage(MessageUtils.error("Invalid door block!"));
            return;
        }

        String linkedRegion = doorManager.getLinkedRegion(bottomDoor.getLocation());
        if (linkedRegion == null) {
            player.sendMessage(MessageUtils.info("This door is not linked to any region."));
        } else {
            player.sendMessage(MessageUtils.info("This door is linked to region: " + linkedRegion));
            Region region = plugin.findRegionById(linkedRegion);
            if (region != null) {
                UUID ownerUUID = region.getOwner();
                String ownerName = "None";
                if (ownerUUID != null) {
                    String name = Bukkit.getOfflinePlayer(ownerUUID).getName();
                    ownerName = name != null ? name : "Unknown";
                }
                player.sendMessage(MessageUtils.info("Region owner: " + ownerName));
                
                int memberCount = region.getRegion().getMembers().size();
                player.sendMessage(MessageUtils.info("Region members: " + memberCount));
                
                // Show door state
                BlockData blockData = bottomDoor.getBlockData();
                if (blockData instanceof Door) {
                    Door doorData = (Door) blockData;
                    player.sendMessage(MessageUtils.info("Door state: " + (doorData.isOpen() ? "Open" : "Closed")));
                }
            } else {
                player.sendMessage(MessageUtils.error("Region not found!"));
            }
        }

        player.sendMessage(MessageUtils.info("Door location: " + formatLocation(bottomDoor.getLocation())));
    }

    private void sendUsage(Player player) {
        player.sendMessage(MessageUtils.heading("Door Management Commands"));
        if (player.hasPermission("edencells.admin.doors")) {
            player.sendMessage(Component.text("/door link <region> - Link the door you're looking at to a region").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/door unlink - Unlink the door you're looking at").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/door info - Get information about the door you're looking at").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/door help - Show this help message").color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("").color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Note: Look at a door block when using these commands.").color(NamedTextColor.GRAY));
        } else {
            player.sendMessage(MessageUtils.error("You don't have permission to use door commands."));
        }
    }

    private String formatLocation(org.bukkit.Location loc) {
        return String.format("%s: %d, %d, %d",
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player player) || !player.hasPermission("edencells.admin.doors")) {
            return completions;
        }

        if (args.length == 1) {
            completions.addAll(Arrays.asList("link", "unlink", "info", "help"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("link")) {
            // Add region suggestions for link command
            try {
                net.alex9849.arm.AdvancedRegionMarket arm = net.alex9849.arm.AdvancedRegionMarket.getInstance();
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
}


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT.jar!\dev\lsdmc\commands\DoorCommands.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */