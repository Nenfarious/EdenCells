package dev.lsdmc.edencells.commands;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.managers.TeleportNPCManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles teleport NPC commands (Citizens)
 */
public final class TeleportNPCCommands implements CommandExecutor, TabCompleter {
    
    private final EdenCells plugin;
    private final TeleportNPCManager teleportNPCManager;
    
    public TeleportNPCCommands(EdenCells plugin, TeleportNPCManager teleportNPCManager) {
        this.plugin = plugin;
        this.teleportNPCManager = teleportNPCManager;
        
        // Register command
        plugin.getCommand("teleportnpc").setExecutor(this);
        plugin.getCommand("teleportnpc").setTabCompleter(this);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.send(sender, Constants.Messages.PREFIX + Constants.Messages.ERROR_COLOR + 
                "This command can only be used by players!");
            return true;
        }
        
        if (!player.hasPermission(Constants.Permissions.NPC_MANAGE)) {
            MessageUtils.sendNoPermission(player);
            return true;
        }
        
        if (!CitizensAPI.hasImplementation()) {
            MessageUtils.sendError(player, "Citizens is not installed or enabled!");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        return switch (subCommand) {
            case "create" -> handleCreate(player, args);
            case "remove" -> handleRemove(player, args);
            case "set" -> handleSet(player, args);
            case "unset" -> handleUnset(player, args);
            case "list" -> handleList(player);
            case "reload" -> handleReload(player);
            default -> {
                sendHelp(player);
                yield true;
            }
        };
    }
    
    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(player, "Usage: /teleportnpc create <name> <cellgroup>");
            return true;
        }
        
        // Build NPC name from args
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
        String cellGroupName = args[args.length - 1];
        
        // Verify cell group exists
        if (plugin.getCellGroupManager().getGroup(cellGroupName) == null) {
            MessageUtils.sendError(player, "Cell group '%s' does not exist!", cellGroupName);
            MessageUtils.sendInfo(player, "Available groups: %s", 
                String.join(", ", plugin.getCellGroupManager().getAllGroups().keySet()));
            return true;
        }
        
        // Create NPC at player location
        var npc = teleportNPCManager.createTeleportNPC(name, player.getLocation(), cellGroupName);
        
        if (npc == null) {
            MessageUtils.sendError(player, "Failed to create NPC!");
            return true;
        }
        
        MessageUtils.send(player, "<color:#51CF66>Created teleport NPC '<color:#FFB3C6>" + name + "</color>' with ID <color:#FFB3C6>" + npc.getId() + "</color></color>");
        MessageUtils.send(player, "<color:#06FFA5>Linked to cell group: <color:#FFB3C6>" + cellGroupName + "</color></color>");
        
        return true;
    }
    
    private boolean handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendError(player, "Usage: /teleportnpc remove <id>");
            return true;
        }
        
        int npcId;
        try {
            npcId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtils.sendError(player, "Invalid NPC ID!");
            return true;
        }
        
        if (teleportNPCManager.removeTeleportNPC(npcId)) {
            MessageUtils.send(player, "<color:#51CF66>Removed teleport NPC with ID <color:#FFB3C6>" + npcId + "</color></color>");
        } else {
            MessageUtils.sendError(player, "NPC with ID %d not found!", npcId);
        }
        
        return true;
    }
    
    private boolean handleSet(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(player, "Usage: /teleportnpc set <id> <cellgroup>");
            return true;
        }
        
        int npcId;
        try {
            npcId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtils.sendError(player, "Invalid NPC ID!");
            return true;
        }
        
        String cellGroupName = args[2];
        
        // Verify cell group exists
        if (plugin.getCellGroupManager().getGroup(cellGroupName) == null) {
            MessageUtils.sendError(player, "Cell group '%s' does not exist!", cellGroupName);
            MessageUtils.sendInfo(player, "Available groups: %s", 
                String.join(", ", plugin.getCellGroupManager().getAllGroups().keySet()));
            return true;
        }
        
        if (teleportNPCManager.setExistingNPCAsTeleport(npcId, cellGroupName)) {
            MessageUtils.send(player, "<color:#51CF66>Set NPC '<color:#FFB3C6>" + npcId + "</color>' as teleport NPC for group '<color:#FFB3C6>" + cellGroupName + "</color>'</color>");
        } else {
            MessageUtils.sendError(player, "NPC with ID %d not found!", npcId);
        }
        
        return true;
    }
    
    private boolean handleUnset(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendError(player, "Usage: /teleportnpc unset <id>");
            return true;
        }
        
        int npcId;
        try {
            npcId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtils.sendError(player, "Invalid NPC ID!");
            return true;
        }
        
        if (teleportNPCManager.unsetTeleportNPC(npcId)) {
            MessageUtils.send(player, "<color:#51CF66>Unset teleport NPC with ID <color:#FFB3C6>" + npcId + "</color></color>");
        } else {
            MessageUtils.sendError(player, "NPC with ID %d not found!", npcId);
        }
        
        return true;
    }
    
    private boolean handleList(Player player) {
        var npcs = CitizensAPI.getNPCRegistry().sorted();
        
        MessageUtils.sendInfo(player, "=== Teleport NPCs ===");
        
        int count = 0;
        for (var npc : npcs) {
            if (npc.hasTrait(dev.lsdmc.edencells.npc.TeleportNPC.class)) {
                var config = teleportNPCManager.getNPCConfig(npc.getId());
                if (config != null) {
                    MessageUtils.send(player, "• ID <color:#FFB3C6>" + npc.getId() + "</color>: <color:#ADB5BD>" + npc.getName() + "</color> - Group: <color:#FFB3C6>" + config.cellGroupName() + "</color>");
                    count++;
                }
            }
        }
        
        if (count == 0) {
            MessageUtils.sendInfo(player, "No teleport NPCs found.");
        }
        
        return true;
    }
    
    private boolean handleReload(Player player) {
        teleportNPCManager.reloadTeleportNPCs();
        MessageUtils.send(player, "<color:#51CF66>Reloaded teleport NPCs!</color>");
        return true;
    }
    
    private void sendHelp(Player player) {
        MessageUtils.send(player, "<color:#9D4EDD>=== Teleport NPC Commands ===</color>");
        MessageUtils.send(player, "<color:#FFB3C6>/teleportnpc create <name> <cellgroup></color> <color:#ADB5BD>- Create teleport NPC</color>");
        MessageUtils.send(player, "<color:#FFB3C6>/teleportnpc remove <id></color> <color:#ADB5BD>- Remove teleport NPC</color>");
        MessageUtils.send(player, "<color:#FFB3C6>/teleportnpc set <id> <cellgroup></color> <color:#ADB5BD>- Set teleport NPC</color>");
        MessageUtils.send(player, "<color:#FFB3C6>/teleportnpc unset <id></color> <color:#ADB5BD>- Unset teleport NPC</color>");
        MessageUtils.send(player, "<color:#FFB3C6>/teleportnpc list</color> <color:#ADB5BD>- List all teleport NPCs</color>");
        MessageUtils.send(player, "<color:#FFB3C6>/teleportnpc reload</color> <color:#ADB5BD>- Reload teleport NPCs</color>");
        MessageUtils.send(player, "");
        MessageUtils.send(player, "<color:#51CF66>How NPCs Work:</color>");
        MessageUtils.send(player, "  <color:#06FFA5>• Right-click:</color> <color:#ADB5BD>Teleport to your cell in the NPC's ward group</color>");
        MessageUtils.send(player, "  <color:#06FFA5>• Left-click:</color> <color:#ADB5BD>Teleport to your donor cell (if you have one)</color>");
        MessageUtils.send(player, "");
        MessageUtils.send(player, "<color:#51CF66>Teleport Costs:</color>");
        
        // Show configurable free groups
        List<String> freeGroups = plugin.getConfig().getStringList("teleportation.free-groups");
        if (!freeGroups.isEmpty()) {
            MessageUtils.send(player, "  <color:#FFB3C6>• Free Groups:</color> <color:#51CF66>" + String.join(", ", freeGroups) + "</color>");
        }
        MessageUtils.send(player, "  <color:#FFB3C6>• Other Ranks:</color> <color:#ADB5BD>Pay the group's teleport cost</color>");
        MessageUtils.send(player, "  <color:#FFB3C6>• Donor Cells:</color> <color:#ADB5BD>Usually free or reduced cost</color>");
        
        // Show personalized tip based on player's status
        boolean hasFreeTeleport = hasFreeTeleportation(player);
        if (hasFreeTeleport) {
            MessageUtils.send(player, "");
            MessageUtils.send(player, "<color:#FFB3C6>👑 VIP Benefit:</color> <color:#51CF66>You teleport for free!</color>");
        } else if (player.hasPermission(Constants.Permissions.DONOR_ACCESS)) {
            MessageUtils.send(player, "");
            MessageUtils.send(player, "<color:#FFB3C6>💎 Donor Tip:</color> <color:#06FFA5>Left-click any NPC for instant donor cell access!</color>");
        }
    }
    
    /**
     * Check if player has free teleportation
     */
    private boolean hasFreeTeleportation(Player player) {
        // Check bypass permission
        if (player.hasPermission(Constants.Permissions.BYPASS_PAYMENT)) {
            return true;
        }
        
        // Check configurable free groups
        List<String> freeGroups = plugin.getConfig().getStringList("teleportation.free-groups");
        for (String group : freeGroups) {
            if (player.hasPermission("group." + group) || 
                player.hasPermission("edencells.group." + group) ||
                player.hasPermission(group)) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            return Arrays.asList("create", "remove", "set", "unset", "list", "reload")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        // Tab complete for "set" command - NPC ID first, then cell group
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            // Could return NPC IDs but that might be too many - leave empty for now
            return Collections.emptyList();
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            // Cell group names for set command
            String partial = args[2].toLowerCase();
            return plugin.getCellGroupManager().getAllGroups().keySet()
                .stream()
                .filter(s -> s.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }
        
        // Tab complete cell groups for create command
        if (args.length >= 3 && args[0].equalsIgnoreCase("create")) {
            // Last argument is the cell group
            String partial = args[args.length - 1].toLowerCase();
            return plugin.getCellGroupManager().getAllGroups().keySet()
                .stream()
                .filter(s -> s.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        }
        
        return Collections.emptyList();
    }
} 