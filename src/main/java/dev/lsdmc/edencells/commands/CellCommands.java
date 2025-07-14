package dev.lsdmc.edencells.commands;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.gui.CellGUI;
import dev.lsdmc.edencells.gui.CellSelectionGUI;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import net.alex9849.arm.regions.Region;
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
 * Handles cell-related commands
 * Main entry point for cell management
 */
public final class CellCommands implements CommandExecutor, TabCompleter {
    
    private final EdenCells plugin;
    
    public CellCommands(EdenCells plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            // Log the command execution only if debug is enabled
            if (plugin.getConfig().getBoolean(Constants.Config.DEBUG, false)) {
                plugin.getLogger().info("[CellCommands] Command executed: /" + label + " " + String.join(" ", args));
            }
            
            if (!(sender instanceof Player player)) {
                MessageUtils.send(sender, Constants.Messages.PREFIX + Constants.Messages.ERROR_COLOR + 
                    "This command can only be used by players!");
                return true;
            }
            
            // Debug logging - use plugin's debug method
            plugin.debug("CellCommands: args.length = " + args.length);
            if (args.length > 0) {
                plugin.debug("CellCommands: args[0] = " + args[0]);
            }
            
            if (args.length == 0) {
                // No arguments - show help menu instead of cell list
                handleHelp(player);
                return true;
            }
            
            String subCommand = args[0].toLowerCase();
            if (plugin.getConfig().getBoolean(Constants.Config.DEBUG, false)) {
                plugin.getLogger().info("[CellCommands] Subcommand: " + subCommand);
            }
            
            if (subCommand.equals("info")) {
                return handleInfo(player, args);
            } else if (subCommand.equals("list")) {
                return handleList(player, args);
            } else if (subCommand.equals("addmember")) {
                return handleAddMember(player, args);
            } else if (subCommand.equals("removemember")) {
                return handleRemoveMember(player, args);
            } else if (subCommand.equals("help")) {
                return handleHelp(player);
            } else if (subCommand.equals("gui")) {
                // Open the main GUI interface
                openMainCellInterface(player);
                return true;
            } else {
                // Unknown subcommand - show help instead
                MessageUtils.sendError(player, "Unknown subcommand: " + subCommand);
                handleHelp(player);
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[CellCommands] Error handling command: " + e.getMessage());
            e.printStackTrace();
            sender.sendMessage("An error occurred while processing the command. Check console for details.");
            return true;
        }
    }
    
    private void openMainCellInterface(Player player) {
        try {
            // Get player's cells
            var cells = plugin.getCellManager().getPlayerCells(player);
            
            if (cells.isEmpty()) {
                MessageUtils.sendInfo(player, "You don't own any cells yet!");
                MessageUtils.sendInfo(player, "Look for cell signs to rent or purchase cells.");
                return;
            }
            
            if (cells.size() == 1) {
                // Single cell - open management GUI directly
                var cellGUI = new CellGUI(plugin, plugin.getCellManager(), plugin.getSecurityManager());
                cellGUI.openCellGUI(player, cells.get(0));
            } else {
                // Multiple cells - show selection GUI
                var selectionGUI = new CellSelectionGUI(plugin);
                selectionGUI.openSelectionGUI(player, cells);
            }
            
        } catch (Exception e) {
            MessageUtils.sendError(player, "Failed to open cell interface!");
            plugin.getLogger().warning("Error opening cell interface for " + player.getName() + ": " + e.getMessage());
            if (plugin.getConfig().getBoolean(Constants.Config.DEBUG, false)) {
                e.printStackTrace();
            }
        }
    }
    
    private boolean handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendError(player, "Usage: /cell info <cellId>");
            return true;
        }
        
        String cellId = args[1];
        Region cell = plugin.getCellManager().getCell(cellId, player.getWorld());
        
        if (cell == null) {
            MessageUtils.sendError(player, "Cell '%s' not found!", cellId);
            return true;
        }
        
        // Open viewer GUI for this cell
        var cellGUI = new CellGUI(plugin, plugin.getCellManager(), plugin.getSecurityManager());
        cellGUI.openCellGUI(player, cell);
        
        return true;
    }
    
    private boolean handleList(Player player, String[] args) {
        var cells = plugin.getCellManager().getPlayerCells(player);
        
        if (cells.isEmpty()) {
            MessageUtils.sendInfo(player, "You don't own any cells.");
            return true;
        }
        
        MessageUtils.sendInfo(player, "=== Your Cells ===");
        
        for (Region cell : cells) {
            var info = plugin.getCellManager().getCellInfo(cell);
            String message = String.format("• %s - %s (%s)", 
                info.get("id"),
                info.get("world"), 
                info.get("type"));
            MessageUtils.sendInfo(player, message);
        }
        
        MessageUtils.sendInfo(player, "Total: %d cells", cells.size());
        return true;
    }
    
    private boolean handleAddMember(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(player, "Usage: /cell addmember <cellId> <playerName>");
            return true;
        }
        
        String cellId = args[1];
        String targetName = args[2];
        
        Region cell = plugin.getCellManager().getCell(cellId, player.getWorld());
        if (cell == null) {
            MessageUtils.sendError(player, "Cell '%s' not found!", cellId);
            return true;
        }
        
        // Rate limit check
        if (plugin.getSecurityManager().isRateLimited(player, "member_add")) {
            MessageUtils.sendError(player, "You're adding members too quickly! Please wait.");
            return true;
        }
        
        plugin.getCellManager().addMember(cell, player, targetName);
        return true;
    }
    
    private boolean handleRemoveMember(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(player, "Usage: /cell removemember <cellId> <playerName>");
            return true;
        }
        
        String cellId = args[1];
        String targetName = args[2];
        
        Region cell = plugin.getCellManager().getCell(cellId, player.getWorld());
        if (cell == null) {
            MessageUtils.sendError(player, "Cell '%s' not found!", cellId);
            return true;
        }
        
        plugin.getCellManager().removeMember(cell, player, targetName);
        return true;
    }
    
    private boolean handleHelp(Player player) {
        MessageUtils.sendInfo(player, "=== Cell Commands ===");
        MessageUtils.sendInfo(player, "/cell - Show this help menu");
        MessageUtils.sendInfo(player, "/cell gui - Open cell management interface");
        MessageUtils.sendInfo(player, "/cell list - List your cells");
        MessageUtils.sendInfo(player, "/cell info <cellId> - View cell information");
        MessageUtils.sendInfo(player, "/cell addmember <cellId> <player> - Add a member");
        MessageUtils.sendInfo(player, "/cell removemember <cellId> <player> - Remove a member");
        MessageUtils.sendInfo(player, "/cell help - Show this help");
        
        MessageUtils.sendInfo(player, "");
        MessageUtils.sendInfo(player, "Tip: Right-click cell signs for interactive management!");
        MessageUtils.sendInfo(player, "Tip: Use Cell NPCs to teleport to your cells!");
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            // Subcommands
            return Arrays.asList("gui", "info", "list", "addmember", "removemember", "help")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("info") || subCommand.equals("addmember") || subCommand.equals("removemember")) {
                // Cell IDs
                return plugin.getCellManager().getPlayerCells(player)
                    .stream()
                    .map(cell -> cell.getRegion().getId())
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("addmember") || subCommand.equals("removemember")) {
                // Player names - return empty for now, could be enhanced with online players
                return Collections.emptyList();
            }
        }
        
        return Collections.emptyList();
    }
}