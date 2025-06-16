package dev.lsdmc.commands;

import dev.lsdmc.EdenCells;
import dev.lsdmc.models.CellGroup;
import dev.lsdmc.models.CellGroupManager;
import dev.lsdmc.utils.MessageUtils;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CellGroupCommands implements CommandExecutor, TabCompleter {
    private final EdenCells plugin;
    private final CellGroupManager groupManager;

    public CellGroupCommands(EdenCells plugin) {
        this.plugin = plugin;
        this.groupManager = plugin.getCellGroupManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("edencells.admin.groups")) {
            player.sendMessage(MessageUtils.noPermission("edencells.admin.groups"));
            return true;
        }

        if (args.length == 0) {
            sendGroupHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /cellgroup create <name> [displayName]").color(NamedTextColor.RED));
                    return true;
                }
                String groupId = args[1];
                String displayName = args.length > 2 ? String.join(" ", args).substring(args[0].length() + args[1].length() + 2) : groupId;
                createGroup(player, groupId, displayName);
            }
            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /cellgroup delete <name>").color(NamedTextColor.RED));
                    return true;
                }
                deleteGroup(player, args[1]);
            }
            case "add" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /cellgroup add <group> <regionId>").color(NamedTextColor.RED));
                    return true;
                }
                addRegionToGroup(player, args[1], args[2]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /cellgroup remove <group> <regionId>").color(NamedTextColor.RED));
                    return true;
                }
                removeRegionFromGroup(player, args[1], args[2]);
            }
            case "list" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /cellgroup list <group>").color(NamedTextColor.RED));
                    return true;
                }
                listGroupRegions(player, args[1]);
            }
            case "listall" -> listAllGroups(player);
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /cellgroup info <group>").color(NamedTextColor.RED));
                    return true;
                }
                showGroupInfo(player, args[1]);
            }
            case "help" -> sendGroupHelp(player);
            default -> {
                player.sendMessage(Component.text("Unknown subcommand. Use /cellgroup help for available commands.")
                    .color(NamedTextColor.RED));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (!player.hasPermission("edencells.admin.groups")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subCommands = List.of("create", "delete", "add", "remove", "list", "listall", "info", "help");
            return filterStartingWith(subCommands, args[0]);
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("delete") || subCommand.equals("add") || 
                subCommand.equals("remove") || subCommand.equals("list") || 
                subCommand.equals("info")) {
                return filterStartingWith(
                    new ArrayList<>(groupManager.getAllGroups().stream()
                        .map(CellGroup::getId)
                        .collect(Collectors.toList())), 
                    args[1]
                );
            }
        }

        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if ((subCommand.equals("add") || subCommand.equals("remove")) && 
                groupManager.getGroup(args[1]) != null) {
                AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
                if (arm != null) {
                    List<String> regionIds = new ArrayList<>();
                    for (Region region : arm.getRegionManager()) {
                        regionIds.add(region.getRegion().getId());
                    }
                    return filterStartingWith(regionIds, args[2]);
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterStartingWith(List<String> list, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    private void sendGroupHelp(Player player) {
        player.sendMessage(Component.text("=== Cell Group Commands ===").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/cellgroup create <name> [displayName] - Create a new group")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellgroup delete <name> - Delete a group")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellgroup add <group> <regionId> - Add region to group")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellgroup remove <group> <regionId> - Remove region from group")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellgroup list <group> - List regions in group")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellgroup listall - List all groups")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellgroup info <group> - Show group information")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellgroup help - Show this help message")
            .color(NamedTextColor.YELLOW));
    }

    private void createGroup(Player player, String groupId, String displayName) {
        if (groupManager.createGroup(groupId, displayName)) {
            player.sendMessage(Component.text("Created group '" + groupId + "' with display name '" + displayName + "'")
                .color(NamedTextColor.GREEN));
            plugin.debug("Player " + player.getName() + " created group: " + groupId);
        } else {
            player.sendMessage(Component.text("A group with that name already exists!")
                .color(NamedTextColor.RED));
        }
    }

    private void deleteGroup(Player player, String groupId) {
        CellGroup group = groupManager.getGroup(groupId);
        if (group == null) {
            player.sendMessage(Component.text("Group '" + groupId + "' doesn't exist!")
                .color(NamedTextColor.RED));
            return;
        }

        if (!group.getRegionIds().isEmpty()) {
            player.sendMessage(Component.text("Group '" + groupId + "' still contains regions. Remove them first!")
                .color(NamedTextColor.RED));
            return;
        }

        if (groupManager.deleteGroup(groupId)) {
            player.sendMessage(Component.text("Deleted group '" + groupId + "'")
                .color(NamedTextColor.GREEN));
            plugin.debug("Player " + player.getName() + " deleted group: " + groupId);
        } else {
            player.sendMessage(Component.text("Failed to delete group!")
                .color(NamedTextColor.RED));
        }
    }

    private void addRegionToGroup(Player player, String groupId, String regionId) {
        CellGroup group = groupManager.getGroup(groupId);
        if (group == null) {
            player.sendMessage(Component.text("Group '" + groupId + "' doesn't exist!")
                .color(NamedTextColor.RED));
            return;
        }

        Region region = plugin.findRegionById(regionId);
        if (region == null) {
            player.sendMessage(Component.text("Region '" + regionId + "' doesn't exist!")
                .color(NamedTextColor.RED));
            return;
        }

        if (group.containsRegion(regionId)) {
            player.sendMessage(Component.text("Region '" + regionId + "' is already in group '" + groupId + "'!")
                .color(NamedTextColor.RED));
            return;
        }

        if (groupManager.addRegionToGroup(groupId, regionId)) {
            player.sendMessage(Component.text("Added region '" + regionId + "' to group '" + groupId + "'")
                .color(NamedTextColor.GREEN));
            plugin.debug("Player " + player.getName() + " added region " + regionId + " to group: " + groupId);
        } else {
            player.sendMessage(Component.text("Failed to add region to group!")
                .color(NamedTextColor.RED));
        }
    }

    private void removeRegionFromGroup(Player player, String groupId, String regionId) {
        CellGroup group = groupManager.getGroup(groupId);
        if (group == null) {
            player.sendMessage(Component.text("Group '" + groupId + "' doesn't exist!")
                .color(NamedTextColor.RED));
            return;
        }

        if (!group.containsRegion(regionId)) {
            player.sendMessage(Component.text("Region '" + regionId + "' is not in group '" + groupId + "'!")
                .color(NamedTextColor.RED));
            return;
        }

        if (groupManager.removeRegionFromGroup(groupId, regionId)) {
            player.sendMessage(Component.text("Removed region '" + regionId + "' from group '" + groupId + "'")
                .color(NamedTextColor.GREEN));
            plugin.debug("Player " + player.getName() + " removed region " + regionId + " from group: " + groupId);
        } else {
            player.sendMessage(Component.text("Failed to remove region from group!")
                .color(NamedTextColor.RED));
        }
    }

    private void listGroupRegions(Player player, String groupId) {
        CellGroup group = groupManager.getGroup(groupId);
        if (group == null) {
            player.sendMessage(Component.text("Group '" + groupId + "' doesn't exist!")
                .color(NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("=== Regions in group '" + group.getDisplayName() + "' ===")
            .color(NamedTextColor.GOLD));

        List<Region> regions = groupManager.getRegionsInGroup(groupId);
        if (regions.isEmpty()) {
            player.sendMessage(Component.text("No regions in this group.")
                .color(NamedTextColor.YELLOW));
            return;
        }

        for (Region region : regions) {
            Component status = region != null ? 
                Component.text("Valid").color(NamedTextColor.GREEN) : 
                Component.text("Invalid").color(NamedTextColor.RED);

            player.sendMessage(Component.text("- " + region.getRegion().getId() + " ")
                .color(NamedTextColor.YELLOW)
                .append(status));
        }

        player.sendMessage(Component.text("Total: " + regions.size() + " regions")
            .color(NamedTextColor.GOLD));
    }

    private void listAllGroups(Player player) {
        player.sendMessage(Component.text("=== All Cell Groups ===")
            .color(NamedTextColor.GOLD));

        Collection<CellGroup> groups = groupManager.getAllGroups();
        if (groups.isEmpty()) {
            player.sendMessage(Component.text("No cell groups defined.")
                .color(NamedTextColor.YELLOW));
            return;
        }

        for (CellGroup group : groups) {
            player.sendMessage(Component.text("- " + group.getId() + " ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text("(" + group.getRegionCount() + " regions)")
                    .color(NamedTextColor.WHITE)));
        }

        player.sendMessage(Component.text("Total: " + groups.size() + " groups")
            .color(NamedTextColor.GOLD));
    }

    private void showGroupInfo(Player player, String groupId) {
        CellGroup group = groupManager.getGroup(groupId);
        if (group == null) {
            player.sendMessage(Component.text("Group '" + groupId + "' doesn't exist!")
                .color(NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("=== Group Information ===")
            .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("ID: " + group.getId())
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Display Name: " + group.getDisplayName())
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Regions: " + group.getRegionCount())
            .color(NamedTextColor.YELLOW));

        Map<String, Object> metadata = group.getAllMetadata();
        if (!metadata.isEmpty()) {
            player.sendMessage(Component.text("Metadata:")
                .color(NamedTextColor.YELLOW));
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                player.sendMessage(Component.text("  " + entry.getKey() + ": " + entry.getValue())
                    .color(NamedTextColor.WHITE));
            }
        }
    }
} 