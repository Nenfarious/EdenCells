package dev.lsdmc.edencells.commands;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.models.CellGroup;
import dev.lsdmc.edencells.models.CellGroupManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import dev.lsdmc.edencells.utils.PermissionManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Handles cell group management commands
 * Provides both individual and bulk operations for efficient cell management
 */
public final class CellGroupCommands implements CommandExecutor, TabCompleter {
  
  private final EdenCells plugin;
  private final CellGroupManager groupManager;
  
  public CellGroupCommands(EdenCells plugin) {
    this.plugin = plugin;
    this.groupManager = plugin.getCellGroupManager();
    
    // Register command
    plugin.getCommand("cellgroup").setExecutor(this);
    plugin.getCommand("cellgroup").setTabCompleter(this);
  }
  
  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      MessageUtils.sendError(sender, "This command can only be used by players.");
      return true;
    } 
    
    if (!PermissionManager.hasPermission(player, Constants.Permissions.ADMIN_GROUPS)) {
      MessageUtils.sendNoPermission(player);
      return true;
    } 
    
    if (args.length == 0) {
      sendGroupHelp(player);
      return true;
    } 
    
    String subCommand = args[0].toLowerCase();
    
    switch (subCommand) {
      case "create":
        if (args.length < 2) {
          MessageUtils.sendError(player, "Usage: /cellgroup create <name> [displayName]");
          return true;
        } 
        String groupId = args[1];
        String displayName = (args.length > 2) ? 
          String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : groupId;
        return handleCreateGroup(player, groupId, displayName);
        
      case "delete":
        if (args.length < 2) {
          MessageUtils.sendError(player, "Usage: /cellgroup delete <name>");
          return true;
        } 
        return handleDeleteGroup(player, args[1]);
        
      case "add":
        if (args.length < 3) {
          MessageUtils.sendError(player, "Usage: /cellgroup add <group> <regionId>");
          return true;
        } 
        return handleAddRegion(player, args[1], args[2]);
        
      case "bulkadd":
        if (args.length < 3) {
          MessageUtils.sendError(player, "Usage: /cellgroup bulkadd <group> <pattern>");
          MessageUtils.sendInfo(player, "Examples: jcell*, jcell1-50, donor*");
        return true;
        }
        String pattern = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        return handleBulkAdd(player, args[1], pattern);
        
      case "remove":
        if (args.length < 3) {
          MessageUtils.sendError(player, "Usage: /cellgroup remove <group> <regionId>");
          return true;
        } 
        return handleRemoveRegion(player, args[1], args[2]);
        
      case "list":
        if (args.length < 2) {
          MessageUtils.sendError(player, "Usage: /cellgroup list <group>");
          return true;
        } 
        return handleListGroup(player, args[1]);
        
      case "listall":
        return handleListAllGroups(player);
        
      case "info":
        if (args.length < 2) {
          MessageUtils.sendError(player, "Usage: /cellgroup info <group>");
          return true;
        } 
        return handleGroupInfo(player, args[1]);
        
      case "limit":
        if (args.length < 3) {
          MessageUtils.sendError(player, "Usage: /cellgroup limit <group> <limit>");
          MessageUtils.sendInfo(player, "Set limit to -1 for no limit");
        return true;
        }
        return handleSetLimit(player, args[1], args[2]);
      case "cost":
        return args.length >= 3 ? handleSetCost(player, args[1], args[2]) : false;
      case "permission":
        return args.length >= 3 ? handleSetPermission(player, args[1], args[2]) : false;
      case "donor":
        return args.length >= 3 ? handleSetDonor(player, args[1], args[2]) : false;
      case "debug":
        return handleDebug(player, args);
      case "help":
        sendGroupHelp(player);
        return true;
        
      default:
        MessageUtils.sendError(player, "Unknown subcommand: %s", subCommand);
        sendGroupHelp(player);
        return true;
    } 
  }
  
  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    if (!(sender instanceof Player player)) {
      return Collections.emptyList();
    } 
    
    if (!PermissionManager.hasPermission(player, Constants.Permissions.ADMIN_GROUPS)) {
      return Collections.emptyList(); 
    }
    
    if (args.length == 1) {
      List<String> subCommands = Arrays.asList(
        "create", "delete", "add", "bulkadd", "remove", "list", "listall", "info", "limit", "cost", "permission", "donor", "debug", "help"
      );
      return filterStartingWith(subCommands, args[0]);
    } 
    
    if (args.length == 2) {
      String subCommand = args[0].toLowerCase();
      if (Arrays.asList("delete", "add", "bulkadd", "remove", "list", "info", "limit", "cost", "permission", "donor", "debug").contains(subCommand)) {
        List<String> groupNames = groupManager.getAllGroups().values().stream()
              .map(CellGroup::getName)
          .collect(Collectors.toList());
        return filterStartingWith(groupNames, args[1]);
      }
    } 
    
    if (args.length == 3) {
      String subCommand = args[0].toLowerCase();
      if (Arrays.asList("add", "remove").contains(subCommand) && 
          groupManager.getGroup(args[1]) != null) {
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm != null) {
          List<String> regionIds = new ArrayList<>();
          for (Region region : arm.getRegionManager()) {
            regionIds.add(region.getRegion().getId()); 
          }
          return filterStartingWith(regionIds, args[2]);
        } 
      } else if (subCommand.equals("bulkadd")) {
        // Provide pattern examples for bulk add
        return Arrays.asList("jcell*", "jcell1-50", "donor*", "vip*");
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
    MessageUtils.send(player, "<color:#9D4EDD>=== Cell Group Commands ===</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup create <name> [displayName]</color> <color:#06FFA5>- Create a new group</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup delete <name></color> <color:#06FFA5>- Delete a group</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup add <group> <regionId></color> <color:#06FFA5>- Add region to group</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup bulkadd <group> <pattern></color> <color:#06FFA5>- Add multiple regions by pattern</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup remove <group> <regionId></color> <color:#06FFA5>- Remove region from group</color>");
    MessageUtils.send(player, "");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup list <group></color> <color:#06FFA5>- List regions in group</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup listall</color> <color:#06FFA5>- List all groups</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup info <group></color> <color:#06FFA5>- Show group information</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup limit <group> <limit></color> <color:#06FFA5>- Set cell limit for group</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup cost <group> <cost></color> <color:#06FFA5>- Set teleport cost for group</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup permission <group> <permission></color> <color:#06FFA5>- Set required permission for group</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup donor <group> <true|false></color> <color:#06FFA5>- Set donor group status</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup debug</color> <color:#06FFA5>- Debug command</color>");
    MessageUtils.send(player, "<color:#FFB3C6>/cellgroup help</color> <color:#06FFA5>- Show this help message</color>");
    MessageUtils.send(player, "");
    MessageUtils.send(player, "<color:#ADB5BD>Bulk patterns: jcell*, jcell1-50, donor*, vip*</color>");
  }
  
  private boolean handleCreateGroup(Player player, String groupId, String displayName) {
    // Validate group name
    if (!plugin.getSecurityManager().isValidRegionId(groupId)) {
      MessageUtils.sendError(player, "Invalid group name. Use only letters, numbers, underscores, and hyphens.");
      return true;
    }
    
    CellGroup group = groupManager.createGroup(groupId);
    if (group != null) {
      groupManager.saveGroups();
      MessageUtils.sendSuccess(player, "Created group '%s' with display name '%s'", groupId, displayName);
      plugin.debug("Player " + player.getName() + " created group: " + groupId);
    } else {
      MessageUtils.sendError(player, "A group with that name already exists!");
    } 
    return true;
  }
  
  private boolean handleDeleteGroup(Player player, String groupId) {
    CellGroup group = groupManager.getGroup(groupId);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' doesn't exist!", groupId);
      return true;
    } 
    
    if (!group.getRegions().isEmpty()) {
      MessageUtils.sendError(player, "Group '%s' still contains %d regions. Remove them first!", 
        groupId, group.getRegions().size());
      return true;
    } 
    
    if (groupManager.deleteGroup(groupId)) {
      groupManager.saveGroups();
      MessageUtils.sendSuccess(player, "Deleted group '%s'", groupId);
      plugin.debug("Player " + player.getName() + " deleted group: " + groupId);
    } else {
      MessageUtils.sendError(player, "Failed to delete group!");
    } 
    return true;
  }
  
  private boolean handleAddRegion(Player player, String groupId, String regionId) {
    CellGroup group = groupManager.getGroup(groupId);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' doesn't exist!", groupId);
      return true;
    }
    
    // Validate region ID
    if (!plugin.getSecurityManager().isValidRegionId(regionId)) {
      MessageUtils.sendError(player, "Invalid region ID format!");
      return true;
    } 
    
    Region region = plugin.findRegionById(regionId);
    if (region == null) {
      MessageUtils.sendError(player, "Region '%s' doesn't exist!", regionId);
      return true;
    } 
    
    if (group.containsRegion(regionId)) {
      MessageUtils.sendError(player, "Region '%s' is already in group '%s'!", regionId, groupId);
      return true;
    } 
    
    if (group.addRegion(regionId)) {
      groupManager.saveGroups();
      MessageUtils.sendSuccess(player, "Added region '%s' to group '%s'", regionId, groupId);
      plugin.debug("Player " + player.getName() + " added region " + regionId + " to group: " + groupId);
    } else {
      MessageUtils.sendError(player, "Failed to add region to group!");
    }
    return true;
  }
  
  /**
   * Handle bulk adding regions to a group using patterns
   */
  private boolean handleBulkAdd(Player player, String groupId, String pattern) {
    CellGroup group = groupManager.getGroup(groupId);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' doesn't exist!", groupId);
      return true;
    }
    
    // Rate limit check for bulk operations
    if (plugin.getSecurityManager().isRateLimited(player, "bulk_add")) {
      MessageUtils.sendError(player, "You're performing bulk operations too quickly! Please wait.");
      return true;
    }
    
    MessageUtils.sendInfo(player, "Processing bulk add with pattern: %s", pattern);
    
    List<String> matchingRegions = findRegionsByPattern(pattern);
    if (matchingRegions.isEmpty()) {
      MessageUtils.sendError(player, "No regions found matching pattern: %s", pattern);
      return true;
    }
    
    // Limit bulk operations for safety
    if (matchingRegions.size() > 500) {
      MessageUtils.sendError(player, "Pattern matches too many regions (%d). Please use a more specific pattern.", 
        matchingRegions.size());
      return true;
    }
    
    int added = 0;
    int skipped = 0;
    int errors = 0;
    
    for (String regionId : matchingRegions) {
      // Verify region exists
      Region region = plugin.findRegionById(regionId);
      if (region == null) {
        errors++;
        continue;
      }
      
      // Check if already in group
      if (group.containsRegion(regionId)) {
        skipped++;
        continue;
      }
      
      // Add to group
      if (group.addRegion(regionId)) {
        added++;
      } else {
        errors++;
      }
    }
    
    // Save changes
    if (added > 0) {
      groupManager.saveGroups();
    }
    
    // Report results
    MessageUtils.sendSuccess(player, "Bulk add completed:");
    MessageUtils.sendInfo(player, "• Added: %d regions", added);
    if (skipped > 0) {
      MessageUtils.sendInfo(player, "• Skipped (already in group): %d regions", skipped);
    }
    if (errors > 0) {
      MessageUtils.sendError(player, "• Errors: %d regions", errors);
    }
    
    plugin.debug("Player " + player.getName() + " bulk added " + added + " regions to group: " + groupId);
    return true;
  }
  
  private boolean handleRemoveRegion(Player player, String groupId, String regionId) {
    CellGroup group = groupManager.getGroup(groupId);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' doesn't exist!", groupId);
      return true;
    } 
    
    if (!group.containsRegion(regionId)) {
      MessageUtils.sendError(player, "Region '%s' is not in group '%s'!", regionId, groupId);
      return true;
    } 
    
    if (group.removeRegion(regionId)) {
      groupManager.saveGroups();
      MessageUtils.sendSuccess(player, "Removed region '%s' from group '%s'", regionId, groupId);
      plugin.debug("Player " + player.getName() + " removed region " + regionId + " from group: " + groupId);
    } else {
      MessageUtils.sendError(player, "Failed to remove region from group!");
    } 
    return true;
  }
  
  private boolean handleListGroup(Player player, String groupId) {
    CellGroup group = groupManager.getGroup(groupId);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' doesn't exist!", groupId);
      return true;
    } 
    
    MessageUtils.send(player, String.format("<color:#9D4EDD>=== Regions in group '%s' ===</color>", group.getName()));
    Set<String> regionIds = group.getRegions();
    
    if (regionIds.isEmpty()) {
      MessageUtils.sendInfo(player, "No regions in this group.");
      return true;
    } 
    
    for (String regionId : regionIds) {
      Region region = plugin.findRegionById(regionId);
      if (region != null) {
        MessageUtils.send(player, String.format("<color:#06FFA5>- %s</color> <color:#51CF66>(Valid)</color>", regionId));
      } else {
        MessageUtils.send(player, String.format("<color:#06FFA5>- %s</color> <color:#FF6B6B>(Invalid)</color>", regionId));
    } 
    }
    
    MessageUtils.send(player, String.format("<color:#9D4EDD>Total: %d regions</color>", regionIds.size()));
    return true;
  }
  
  private boolean handleListAllGroups(Player player) {
    MessageUtils.send(player, "<color:#9D4EDD>=== All Cell Groups ===</color>");
    Collection<CellGroup> groups = groupManager.getAllGroups().values();
    
    if (groups.isEmpty()) {
      MessageUtils.sendInfo(player, "No cell groups defined.");
      return true;
    }
    
    for (CellGroup group : groups) {
      MessageUtils.send(player, String.format("<color:#06FFA5>- %s</color> <color:#ADB5BD>(%d regions)</color>", 
        group.getName(), group.size()));
    }
    
    MessageUtils.send(player, String.format("<color:#9D4EDD>Total: %d groups</color>", groups.size()));
    return true;
  }
  
  private boolean handleGroupInfo(Player player, String groupId) {
    CellGroup group = groupManager.getGroup(groupId);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' doesn't exist!", groupId);
      return true;
    }
    
    MessageUtils.send(player, "<color:#9D4EDD>=== Group Information ===</color>");
    MessageUtils.send(player, String.format("<color:#FFB3C6>ID:</color> <color:#06FFA5>%s</color>", group.getName()));
    MessageUtils.send(player, String.format("<color:#FFB3C6>Display Name:</color> <color:#06FFA5>%s</color>", group.getDisplayName()));
    MessageUtils.send(player, String.format("<color:#FFB3C6>Regions:</color> <color:#06FFA5>%d</color>", group.size()));
    
    // Show additional group properties
    if (group.getCellLimit() != -1) {
      MessageUtils.send(player, String.format("<color:#FFB3C6>Cell Limit:</color> <color:#06FFA5>%d</color>", group.getCellLimit()));
    }
    
    if (group.getTeleportCost() != -1) {
      MessageUtils.send(player, String.format("<color:#FFB3C6>Teleport Cost:</color> <color:#06FFA5>%s</color>", 
        plugin.formatCurrency(group.getTeleportCost())));
    } 
    
    if (group.isDonorGroup()) {
      MessageUtils.send(player, "<color:#FFB3C6>Type:</color> <color:#51CF66>Donor Group</color>");
    }
    
    String permission = group.getRequiredPermission();
    if (permission != null) {
      MessageUtils.send(player, String.format("<color:#FFB3C6>Required Permission:</color> <color:#06FFA5>%s</color>", permission));
    }
    
    return true;
  }
  
  private boolean handleSetLimit(Player player, String groupId, String limitStr) {
    CellGroup group = groupManager.getGroup(groupId);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' doesn't exist!", groupId);
      return true;
    }
    
    int limit;
    try {
      limit = Integer.parseInt(limitStr);
    } catch (NumberFormatException e) {
      MessageUtils.sendError(player, "Invalid limit value! Use a number or -1 for no limit.");
      return true;
    }
    
    if (limit < -1) {
      MessageUtils.sendError(player, "Limit cannot be less than -1!");
      return true;
    }
    
    try {
      group.setCellLimit(limit);
      groupManager.saveGroups();
      
      if (limit == -1) {
        MessageUtils.sendSuccess(player, "Removed cell limit for group '%s'", groupId);
      } else {
        MessageUtils.sendSuccess(player, "Set cell limit for group '%s' to %d", groupId, limit);
      }
      
      plugin.debug("Player " + player.getName() + " set limit for group " + groupId + " to " + limit);
    } catch (Exception e) {
      MessageUtils.sendError(player, "Failed to set limit: %s", e.getMessage());
    }
    
    return true;
  }
  
  private boolean handleSetCost(Player player, String groupId, String costStr) {
    CellGroup group = groupManager.getGroup(groupId);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' doesn't exist!", groupId);
      return true;
    }
    
    double cost;
    try {
      cost = Double.parseDouble(costStr);
    } catch (NumberFormatException e) {
      MessageUtils.sendError(player, "Invalid cost format! Use a number.");
      return true;
    }
    
    if (cost < 0) {
      MessageUtils.sendError(player, "Cost cannot be negative!");
      return true;
    }
    
    try {
      group.setTeleportCost(cost);
      groupManager.saveGroups();
      
      MessageUtils.sendSuccess(player, "Set teleport cost for group '%s' to %s", groupId, plugin.formatCurrency(cost));
      
      plugin.debug("Player " + player.getName() + " set cost for group " + groupId + " to " + cost);
    } catch (Exception e) {
      MessageUtils.sendError(player, "Failed to set cost: %s", e.getMessage());
    }
    
    return true;
  }
  
  private boolean handleSetPermission(Player player, String groupId, String permission) {
    CellGroup group = groupManager.getGroup(groupId);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' doesn't exist!", groupId);
      return true;
    }
    
    // Basic validation - just check it's not empty if not "null"
    if (!permission.equalsIgnoreCase("null") && permission.trim().isEmpty()) {
      MessageUtils.sendError(player, "Permission cannot be empty! Use 'null' to remove permission.");
      return true;
    }
    
    try {
      if (permission.equalsIgnoreCase("null")) {
        group.setRequiredPermission(null);
        MessageUtils.sendSuccess(player, "Removed required permission for group '%s'", groupId);
      } else {
        group.setRequiredPermission(permission);
        MessageUtils.sendSuccess(player, "Set required permission for group '%s' to %s", groupId, permission);
      }
      
      groupManager.saveGroups();
      
      plugin.debug("Player " + player.getName() + " set permission for group " + groupId + " to " + permission);
    } catch (Exception e) {
      MessageUtils.sendError(player, "Failed to set permission: %s", e.getMessage());
    }
    
    return true;
  }
  
  private boolean handleSetDonor(Player player, String groupId, String donorStr) {
    CellGroup group = groupManager.getGroup(groupId);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' doesn't exist!", groupId);
      return true;
    }
    
    boolean isDonor;
    try {
      isDonor = Boolean.parseBoolean(donorStr);
    } catch (IllegalArgumentException e) {
      MessageUtils.sendError(player, "Invalid donor format! Use true or false.");
      return true;
    }
    
    try {
      group.setDonorGroup(isDonor);
      groupManager.saveGroups();
      
      MessageUtils.sendSuccess(player, "Set donor group status for group '%s' to %s", groupId, isDonor ? "true" : "false");
      
      plugin.debug("Player " + player.getName() + " set donor group status for group " + groupId + " to " + isDonor);
    } catch (Exception e) {
      MessageUtils.sendError(player, "Failed to set donor group status: %s", e.getMessage());
    }
    
    return true;
  }
  
  private boolean handleDebug(Player player, String[] args) {
    if (args.length < 2) {
      // General debug info
      MessageUtils.send(player, "<color:#9D4EDD>=== Cell Group Debug Information ===</color>");
      MessageUtils.send(player, "");
      
      // Show loaded groups
      MessageUtils.send(player, "<color:#FFB3C6>Loaded Groups:</color> <color:#51CF66>" + groupManager.getAllGroups().size() + "</color>");
      for (CellGroup group : groupManager.getAllGroups().values()) {
        MessageUtils.send(player, "  <color:#06FFA5>- " + group.getName() + "</color> <color:#ADB5BD>(" + group.getRegions().size() + " regions)</color>");
      }
      
      // Show player's cells
      MessageUtils.send(player, "");
      MessageUtils.send(player, "<color:#FFB3C6>Your Cells:</color>");
      var playerCells = plugin.getCellManager().getPlayerCells(player);
      if (playerCells.isEmpty()) {
        MessageUtils.send(player, "  <color:#FF6B6B>No cells found</color>");
      } else {
        for (var cell : playerCells) {
          String regionId = cell.getRegion().getId();
          CellGroup group = groupManager.getGroupByRegion(regionId);
          String groupName = group != null ? group.getName() : "none";
          MessageUtils.send(player, "  <color:#06FFA5>- " + regionId + "</color> <color:#ADB5BD>(group: " + groupName + ")</color>");
        }
      }
      
      MessageUtils.send(player, "");
      MessageUtils.send(player, "<color:#ADB5BD>Use: /cellgroup debug <group> for detailed group analysis</color>");
      return true;
    }
    
    String groupName = args[1];
    CellGroup group = groupManager.getGroup(groupName);
    if (group == null) {
      MessageUtils.sendError(player, "Group '%s' not found!", groupName);
      return true;
    }
    
    MessageUtils.send(player, "<color:#9D4EDD>=== Debug: Group '" + groupName + "' ===</color>");
    MessageUtils.send(player, "");
    MessageUtils.send(player, "<color:#FFB3C6>Group Information:</color>");
    MessageUtils.send(player, "  <color:#06FFA5>Name:</color> " + group.getName());
    MessageUtils.send(player, "  <color:#06FFA5>Display Name:</color> " + group.getDisplayName());
    MessageUtils.send(player, "  <color:#06FFA5>Regions Count:</color> " + group.getRegions().size());
    MessageUtils.send(player, "  <color:#06FFA5>Cell Limit:</color> " + (group.getCellLimit() == -1 ? "unlimited" : group.getCellLimit()));
    MessageUtils.send(player, "  <color:#06FFA5>Teleport Cost:</color> " + (group.getTeleportCost() == -1 ? "default" : plugin.formatCurrency(group.getTeleportCost())));
    MessageUtils.send(player, "  <color:#06FFA5>Is Donor:</color> " + group.isDonorGroup());
    if (group.getRequiredPermission() != null) {
      MessageUtils.send(player, "  <color:#06FFA5>Permission:</color> " + group.getRequiredPermission());
      MessageUtils.send(player, "  <color:#06FFA5>You Have Permission:</color> " + (player.hasPermission(group.getRequiredPermission()) ? "<color:#51CF66>Yes</color>" : "<color:#FF6B6B>No</color>"));
    }
    
    MessageUtils.send(player, "");
    MessageUtils.send(player, "<color:#FFB3C6>Region Validation:</color>");
    int validRegions = 0;
    int invalidRegions = 0;
    for (String regionId : group.getRegions()) {
      var region = plugin.findRegionById(regionId);
      if (region != null) {
        validRegions++;
        // Check if player owns this region
        boolean playerOwns = plugin.getCellManager().isOwner(player, region);
        boolean playerMember = plugin.getCellManager().hasAccess(player, region);
        String status = playerOwns ? "<color:#51CF66>OWNER</color>" : (playerMember ? "<color:#FFB3C6>MEMBER</color>" : "<color:#ADB5BD>no access</color>");
        MessageUtils.send(player, "  <color:#51CF66>✓ " + regionId + "</color> " + status);
      } else {
        invalidRegions++;
        MessageUtils.send(player, "  <color:#FF6B6B>✗ " + regionId + "</color> <color:#FF6B6B>(not found)</color>");
      }
    }
    
    MessageUtils.send(player, "");
    MessageUtils.send(player, "<color:#FFB3C6>Summary:</color>");
    MessageUtils.send(player, "  <color:#51CF66>Valid Regions:</color> " + validRegions);
    if (invalidRegions > 0) {
      MessageUtils.send(player, "  <color:#FF6B6B>Invalid Regions:</color> " + invalidRegions);
      MessageUtils.send(player, "  <color:#FF6B6B>Tip:</color> <color:#ADB5BD>Use /cellgroup remove <group> <regionId> to remove invalid regions</color>");
    }
    
    // Check how many cells player has in this group
    var playerCellsInGroup = plugin.getCellManager().getPlayerCells(player).stream()
      .filter(cell -> group.containsRegion(cell.getRegion().getId()))
      .toList();
    MessageUtils.send(player, "  <color:#06FFA5>Your Cells in Group:</color> " + playerCellsInGroup.size());
    
    return true;
  }
  
  /**
   * Find regions matching a pattern
   */
  private List<String> findRegionsByPattern(String pattern) {
    AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
    if (arm == null) {
      return new ArrayList<>();
    }
    
    List<String> matching = new ArrayList<>();
    
    for (Region region : arm.getRegionManager()) {
      String regionId = region.getRegion().getId();
      if (matchesPattern(regionId, pattern)) {
        matching.add(regionId);
      }
    }
    
    return matching;
  }
  
  /**
   * Check if a region ID matches a pattern
   */
  private boolean matchesPattern(String regionId, String pattern) {
    // Handle range patterns like jcell1-50
    if (pattern.contains("-") && !pattern.startsWith("-") && !pattern.endsWith("-")) {
      return matchesRangePattern(regionId, pattern);
    }
    
    // Handle wildcard patterns like jcell*
    if (pattern.contains("*")) {
      return matchesWildcardPattern(regionId, pattern);
    }
    
    // Exact match
    return regionId.equals(pattern);
  }
  
  /**
   * Match range patterns like jcell1-50
   */
  private boolean matchesRangePattern(String regionId, String pattern) {
    try {
      String[] parts = pattern.split("-");
      if (parts.length != 2) return false;
      
      String firstPart = parts[0];
      String prefix = "";
      int startNum = 0;
      
      // Find where numbers start in the first part
      for (int i = 0; i < firstPart.length(); i++) {
        if (Character.isDigit(firstPart.charAt(i))) {
          prefix = firstPart.substring(0, i);
          startNum = Integer.parseInt(firstPart.substring(i));
          break;
        }
      }
      
      int endNum = Integer.parseInt(parts[1]);
      
      // Check if regionId matches pattern
      if (!regionId.startsWith(prefix)) return false;
      
      String numPart = regionId.substring(prefix.length());
      if (numPart.isEmpty()) return false;
      
      try {
        int regionNum = Integer.parseInt(numPart);
        return regionNum >= startNum && regionNum <= endNum;
      } catch (NumberFormatException e) {
        return false;
      }
      
    } catch (Exception e) {
      return false;
    }
  }
  
  /**
   * Match wildcard patterns like jcell*
   */
  private boolean matchesWildcardPattern(String regionId, String pattern) {
    // Convert wildcard pattern to regex
    String regex = pattern.replace("*", ".*").replace("?", ".");
    return regionId.matches(regex);
  }
}


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT (1).jar!\dev\lsdmc\commands\CellGroupCommands.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */