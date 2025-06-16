package dev.lsdmc.commands;

import dev.lsdmc.EdenCells;
import dev.lsdmc.npc.CellBlockNPC;
import dev.lsdmc.utils.MessageUtils;
import dev.lsdmc.utils.PermissionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NPCCommands implements CommandExecutor, TabCompleter {
    private final EdenCells plugin;
    private final CellBlockNPC npcManager;

    public NPCCommands(EdenCells plugin) {
        this.plugin = plugin;
        this.npcManager = plugin.getCellBlockNPC();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        if (!PermissionManager.hasPermission(player, PermissionManager.ADMIN)) {
            return true;
        }

        if (args.length == 0) {
            sendNPCHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Usage: /cellnpc create <blockId> <displayName>")
                        .color(NamedTextColor.RED));
                    return true;
                }
                String blockId = args[1];
                String displayName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                createNPC(player, blockId, displayName);
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /cellnpc remove <blockId>")
                        .color(NamedTextColor.RED));
                    return true;
                }
                removeNPC(player, args[1]);
            }
            case "list" -> listNPCs(player);
            case "info" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /cellnpc info <blockId>")
                        .color(NamedTextColor.RED));
                    return true;
                }
                showNPCInfo(player, args[1]);
            }
            case "teleport", "tp" -> {
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /cellnpc tp <blockId>")
                        .color(NamedTextColor.RED));
                    return true;
                }
                teleportToNPC(player, args[1]);
            }
            case "help" -> sendNPCHelp(player);
            default -> {
                player.sendMessage(Component.text("Unknown subcommand. Use /cellnpc help for available commands.")
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

        if (!PermissionManager.hasPermission(player, PermissionManager.ADMIN)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("create", "remove", "list", "info", "teleport", "tp", "help");
            return filterStartingWith(subCommands, args[0]);
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("remove") || subCommand.equals("info") || 
                subCommand.equals("teleport") || subCommand.equals("tp")) {
                return filterStartingWith(new ArrayList<>(npcManager.getBlockIds()), args[1]);
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

    private void createNPC(Player player, String blockId, String displayName) {
        if (npcManager.hasNPC(blockId)) {
            player.sendMessage(Component.text("An NPC for block '" + blockId + "' already exists!")
                .color(NamedTextColor.RED));
            return;
        }

        if (npcManager.createNPC(blockId, displayName, player.getLocation())) {
            player.sendMessage(Component.text("Created NPC '" + displayName + "' for block '" + blockId + "' at your location.")
                .color(NamedTextColor.GREEN));
            plugin.debug("Player " + player.getName() + " created NPC for block: " + blockId);
        } else {
            player.sendMessage(Component.text("Failed to create NPC. Make sure Citizens is installed and working.")
                .color(NamedTextColor.RED));
        }
    }

    private void removeNPC(Player player, String blockId) {
        if (!npcManager.hasNPC(blockId)) {
            player.sendMessage(Component.text("No NPC found for block '" + blockId + "'!")
                .color(NamedTextColor.RED));
            return;
        }

        if (npcManager.removeNPC(blockId)) {
            player.sendMessage(Component.text("Removed NPC for block '" + blockId + "'.")
                .color(NamedTextColor.GREEN));
            plugin.debug("Player " + player.getName() + " removed NPC for block: " + blockId);
        } else {
            player.sendMessage(Component.text("Failed to remove NPC.")
                .color(NamedTextColor.RED));
        }
    }

    private void listNPCs(Player player) {
        List<String> blockIds = npcManager.getBlockIds();
        
        if (blockIds.isEmpty()) {
            player.sendMessage(Component.text("No cell block NPCs are currently active.")
                .color(NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== Active Cell Block NPCs ===")
            .color(NamedTextColor.GOLD));
        
        for (String blockId : blockIds) {
            String displayName = npcManager.getNPCDisplayName(blockId);
            player.sendMessage(Component.text("- " + blockId + ": ")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(displayName)
                    .color(NamedTextColor.WHITE)));
        }

        player.sendMessage(Component.text("Total: " + blockIds.size() + " NPCs")
            .color(NamedTextColor.GOLD));
    }

    private void showNPCInfo(Player player, String blockId) {
        if (!npcManager.hasNPC(blockId)) {
            player.sendMessage(Component.text("No NPC found for block '" + blockId + "'!")
                .color(NamedTextColor.RED));
            return;
        }

        String displayName = npcManager.getNPCDisplayName(blockId);
        String location = npcManager.getNPCLocationString(blockId);
        
        player.sendMessage(Component.text("=== NPC Information ===")
            .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("Block ID: " + blockId)
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Display Name: " + displayName)
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Location: " + location)
            .color(NamedTextColor.YELLOW));
    }

    private void teleportToNPC(Player player, String blockId) {
        if (!npcManager.hasNPC(blockId)) {
            player.sendMessage(Component.text("No NPC found for block '" + blockId + "'!")
                .color(NamedTextColor.RED));
            return;
        }

        if (npcManager.teleportToNPC(player, blockId)) {
            player.sendMessage(Component.text("Teleported to NPC for block '" + blockId + "'.")
                .color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Failed to teleport to NPC.")
                .color(NamedTextColor.RED));
        }
    }

    private void sendNPCHelp(Player player) {
        player.sendMessage(Component.text("=== Cell Block NPC Commands ===")
            .color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/cellnpc create <blockId> <displayName> - Create NPC at your location")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellnpc remove <blockId> - Remove an NPC")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellnpc list - List all NPCs")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellnpc info <blockId> - Show NPC information")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellnpc tp <blockId> - Teleport to an NPC")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/cellnpc help - Show this help message")
            .color(NamedTextColor.YELLOW));
    }
} 