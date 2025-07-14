package dev.lsdmc.edencells.commands;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.models.CellGroup;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import dev.lsdmc.edencells.utils.PermissionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles configuration management commands
 * Provides access to all config options via commands
 */
public final class ConfigCommands implements CommandExecutor, TabCompleter {
    
    private final EdenCells plugin;
    
    // Map of config sections to their sub-options for tab completion
    private final Map<String, List<String>> configSections = new HashMap<>();
    
    public ConfigCommands(EdenCells plugin) {
        this.plugin = plugin;
        initializeConfigSections();
    }
    
    private void initializeConfigSections() {
        // General settings
        configSections.put("general", Arrays.asList("debug", "locale"));
        
        // Messages
        configSections.put("messages", Arrays.asList("prefix", "colors"));
        
        // Cells
        configSections.put("cells", Arrays.asList("max-per-player", "default-max-members", "sign-keywords", "gui"));
        
        // Economy
        configSections.put("economy", Arrays.asList("currency-symbol", "currency-suffix", "members", "fees"));
        
        // Cell groups (stored in cell-groups.yml)
        configSections.put("cell-groups", Arrays.asList("global-limit", "groups"));
        
        // Teleportation
        configSections.put("teleportation", Arrays.asList("base-cost", "require-payment", "cooldown", "effects"));
        
        // Doors
        configSections.put("doors", Arrays.asList("valid-materials", "sounds", "auto-close-delay"));
        
        // Security
        configSections.put("security", Arrays.asList("rate-limits", "validation", "audit"));
        
        // Integrations
        configSections.put("integrations", Arrays.asList("arm", "citizens", "worldguard"));
        
        // Performance
        configSections.put("performance", Arrays.asList("cache", "async"));
        
        // Maintenance
        configSections.put("maintenance", Arrays.asList("auto-save-interval", "backup", "cleanup"));
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
            MessageUtils.sendNoPermission(sender);
            return true;
        }
        
        if (args.length == 0) {
            sendConfigHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "get":
                return handleGet(sender, args);
            case "set":
                return handleSet(sender, args);
            case "list":
                return handleList(sender, args);
            case "reload":
                return handleReload(sender);
            case "save":
                return handleSave(sender);
            case "help":
                sendConfigHelp(sender);
                return true;
            default:
                MessageUtils.sendError(sender, "Unknown subcommand: " + subCommand);
                sendConfigHelp(sender);
                return true;
        }
    }
    
    private boolean handleGet(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendError(sender, "Usage: /econfig get <path>");
            return true;
        }
        
        String path = String.join(".", Arrays.copyOfRange(args, 1, args.length));
        
        // Handle cell-groups.yml paths
        if (path.startsWith("cell-groups.")) {
            return handleCellGroupsGet(sender, path);
        }
        
        if (!plugin.getConfig().contains(path)) {
            MessageUtils.sendError(sender, "Config path '%s' not found!", path);
            return true;
        }
        
        Object value = plugin.getConfig().get(path);
        String valueStr = formatConfigValue(value);
        
        MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + valueStr + "</color>");
        
        return true;
    }
    
    private boolean handleCellGroupsGet(CommandSender sender, String path) {
        String subPath = path.substring("cell-groups.".length());
        
        if (subPath.equals("global-limit")) {
            int globalLimit = plugin.getCellGroupManager().getGlobalCellLimit();
            MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + globalLimit + "</color>");
            return true;
        }
        
        if (subPath.startsWith("groups.")) {
            String groupPath = subPath.substring("groups.".length());
            String[] parts = groupPath.split("\\.", 2);
            String groupName = parts[0];
            
            CellGroup group = plugin.getCellGroupManager().getGroup(groupName);
            if (group == null) {
                MessageUtils.sendError(sender, "Cell group '%s' not found!", groupName);
                return true;
            }
            
            if (parts.length == 1) {
                // Show entire group info
                MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color>");
                MessageUtils.send(sender, "  <color:#06FFA5>display-name:</color> <color:#51CF66>" + group.getDisplayName() + "</color>");
                MessageUtils.send(sender, "  <color:#06FFA5>regions:</color> <color:#51CF66>" + group.getRegions().size() + " regions</color>");
                if (group.getCellLimit() != -1) {
                    MessageUtils.send(sender, "  <color:#06FFA5>cell-limit:</color> <color:#51CF66>" + group.getCellLimit() + "</color>");
                }
                if (group.getTeleportCost() != -1) {
                    MessageUtils.send(sender, "  <color:#06FFA5>teleport-cost:</color> <color:#51CF66>" + group.getTeleportCost() + "</color>");
                }
                if (group.getRequiredPermission() != null) {
                    MessageUtils.send(sender, "  <color:#06FFA5>permission:</color> <color:#51CF66>" + group.getRequiredPermission() + "</color>");
                }
                if (group.isDonorGroup()) {
                    MessageUtils.send(sender, "  <color:#06FFA5>is-donor:</color> <color:#51CF66>true</color>");
                }
                return true;
            }
            
            String property = parts[1];
            switch (property) {
                case "display-name":
                    MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + group.getDisplayName() + "</color>");
                    break;
                case "cell-limit":
                    MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + group.getCellLimit() + "</color>");
                    break;
                case "teleport-cost":
                    MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + group.getTeleportCost() + "</color>");
                    break;
                case "permission":
                    String perm = group.getRequiredPermission();
                    MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + (perm != null ? perm : "null") + "</color>");
                    break;
                case "is-donor":
                    MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + group.isDonorGroup() + "</color>");
                    break;
                case "regions":
                    MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + formatConfigValue(group.getRegions()) + "</color>");
                    break;
                default:
                    MessageUtils.sendError(sender, "Unknown group property: " + property);
                    return true;
            }
            return true;
        }
        
        MessageUtils.sendError(sender, "Invalid cell-groups path: " + path);
        return true;
    }
    
    private boolean handleSet(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendError(sender, "Usage: /econfig set <path> <value>");
            return true;
        }
        
        String path = args[1];
        String valueStr = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        
        // Handle cell-groups.yml paths
        if (path.startsWith("cell-groups.")) {
            return handleCellGroupsSet(sender, path, valueStr);
        }
        
        // Parse the value based on current type
        Object currentValue = plugin.getConfig().get(path);
        Object newValue = parseValue(valueStr, currentValue);
        
        if (newValue == null && !valueStr.equalsIgnoreCase("null")) {
            MessageUtils.sendError(sender, "Invalid value format for path: " + path);
            return true;
        }
        
        // Apply the change
        plugin.getConfig().set(path, newValue);
        plugin.saveConfig();
        
        // Apply specific changes immediately
        applyConfigChange(path, newValue);
        
        MessageUtils.sendSuccess(sender, "Successfully updated configuration:");
        MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + formatConfigValue(newValue) + "</color>");
        
        return true;
    }
    
    private boolean handleCellGroupsSet(CommandSender sender, String path, String valueStr) {
        String subPath = path.substring("cell-groups.".length());
        
        if (subPath.equals("global-limit")) {
            try {
                int limit = Integer.parseInt(valueStr);
                if (limit < -1) {
                    MessageUtils.sendError(sender, "Global limit cannot be less than -1!");
                    return true;
                }
                
                plugin.getCellGroupManager().setGlobalCellLimit(limit);
                plugin.getCellGroupManager().saveGroups();
                
                MessageUtils.sendSuccess(sender, "Successfully updated global cell limit:");
                MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + limit + "</color>");
                return true;
            } catch (NumberFormatException e) {
                MessageUtils.sendError(sender, "Invalid number format for global limit!");
                return true;
            }
        }
        
        if (subPath.startsWith("groups.")) {
            String groupPath = subPath.substring("groups.".length());
            String[] parts = groupPath.split("\\.", 2);
            String groupName = parts[0];
            
            CellGroup group = plugin.getCellGroupManager().getGroup(groupName);
            if (group == null) {
                MessageUtils.sendError(sender, "Cell group '%s' not found!", groupName);
                return true;
            }
            
            if (parts.length < 2) {
                MessageUtils.sendError(sender, "Must specify a property to set for group!");
                return true;
            }
            
            String property = parts[1];
            try {
                switch (property) {
                    case "display-name":
                        // Note: CellGroup doesn't have setDisplayName, this would require enhancement
                        MessageUtils.sendError(sender, "Display name cannot be changed after creation!");
                        return true;
                    case "cell-limit":
                        int limit = Integer.parseInt(valueStr);
                        group.setCellLimit(limit);
                        break;
                    case "teleport-cost":
                        double cost = Double.parseDouble(valueStr);
                        group.setTeleportCost(cost);
                        break;
                    case "permission":
                        if (valueStr.equalsIgnoreCase("null") || valueStr.trim().isEmpty()) {
                            group.setRequiredPermission(null);
                        } else {
                            group.setRequiredPermission(valueStr);
                        }
                        break;
                    case "is-donor":
                        boolean isDonor = Boolean.parseBoolean(valueStr);
                        group.setDonorGroup(isDonor);
                        break;
                    default:
                        MessageUtils.sendError(sender, "Unknown group property: " + property);
                        MessageUtils.sendInfo(sender, "Available properties: cell-limit, teleport-cost, permission, is-donor");
                        return true;
                }
                
                plugin.getCellGroupManager().saveGroups();
                MessageUtils.sendSuccess(sender, "Successfully updated group property:");
                MessageUtils.send(sender, "<color:#FFB3C6>" + path + ":</color> <color:#51CF66>" + valueStr + "</color>");
                return true;
                
            } catch (NumberFormatException e) {
                MessageUtils.sendError(sender, "Invalid number format for property: " + property);
                return true;
            } catch (Exception e) {
                MessageUtils.sendError(sender, "Failed to set property: " + e.getMessage());
                return true;
            }
        }
        
        MessageUtils.sendError(sender, "Invalid cell-groups path: " + path);
        return true;
    }
    
    private boolean handleList(CommandSender sender, String[] args) {
        String section = args.length > 1 ? args[1] : "";
        
        if (section.isEmpty()) {
            // List main sections
            MessageUtils.send(sender, "<color:#9D4EDD>=== Configuration Sections ===</color>");
            MessageUtils.send(sender, "");
            for (String key : configSections.keySet()) {
                MessageUtils.send(sender, "<color:#06FFA5>• <color:#FFB3C6>" + key + "</color></color>");
            }
            MessageUtils.send(sender, "");
            MessageUtils.send(sender, "<color:#ADB5BD>Use /econfig list <section> for details</color>");
        } else if (section.equals("cell-groups")) {
            // Handle cell-groups section specially
            MessageUtils.send(sender, "<color:#9D4EDD>=== Cell Groups Configuration ===</color>");
            MessageUtils.send(sender, "");
            
            // Global limit
            int globalLimit = plugin.getCellGroupManager().getGlobalCellLimit();
            MessageUtils.send(sender, "<color:#FFB3C6>global-limit:</color> <color:#51CF66>" + globalLimit + "</color>");
            MessageUtils.send(sender, "");
            
            // List all groups
            MessageUtils.send(sender, "<color:#FFB3C6>groups:</color>");
            for (CellGroup group : plugin.getCellGroupManager().getAllGroups().values()) {
                MessageUtils.send(sender, "  <color:#06FFA5>" + group.getName() + ":</color>");
                MessageUtils.send(sender, "    <color:#FFB3C6>display-name:</color> <color:#51CF66>" + group.getDisplayName() + "</color>");
                MessageUtils.send(sender, "    <color:#FFB3C6>regions:</color> <color:#51CF66>" + group.getRegions().size() + " regions</color>");
                if (group.getCellLimit() != -1) {
                    MessageUtils.send(sender, "    <color:#FFB3C6>cell-limit:</color> <color:#51CF66>" + group.getCellLimit() + "</color>");
                }
                if (group.getTeleportCost() != -1) {
                    MessageUtils.send(sender, "    <color:#FFB3C6>teleport-cost:</color> <color:#51CF66>" + group.getTeleportCost() + "</color>");
                }
                if (group.getRequiredPermission() != null) {
                    MessageUtils.send(sender, "    <color:#FFB3C6>permission:</color> <color:#51CF66>" + group.getRequiredPermission() + "</color>");
                }
                if (group.isDonorGroup()) {
                    MessageUtils.send(sender, "    <color:#FFB3C6>is-donor:</color> <color:#51CF66>true</color>");
                }
                MessageUtils.send(sender, "");
            }
            
            MessageUtils.send(sender, "<color:#ADB5BD>Use /econfig set cell-groups.<path> <value> to modify</color>");
        } else {
            // List options in section
            var options = plugin.getConfig().getConfigurationSection(section);
            if (options == null) {
                MessageUtils.sendError(sender, "Section '%s' not found!", section);
                return true;
            }
            
            MessageUtils.send(sender, "<color:#9D4EDD>=== " + section.substring(0, 1).toUpperCase() + section.substring(1) + " Configuration ===</color>");
            MessageUtils.send(sender, "");
            
            // Create a sorted list of keys
            List<String> sortedKeys = new ArrayList<>();
            for (String key : options.getKeys(true)) {
                Object value = options.get(key);
                if (!(value instanceof org.bukkit.configuration.ConfigurationSection)) {
                    sortedKeys.add(key);
                }
            }
            Collections.sort(sortedKeys);
            
            // Display each config value
            for (String key : sortedKeys) {
                Object value = options.get(key);
                String fullPath = section + "." + key;
                String formattedValue = formatConfigValue(value);
                
                // Format the display nicely
                String displayKey = key.replace("-", " ").replace(".", " > ");
                
                MessageUtils.send(sender, "<color:#FFB3C6>" + displayKey + ":</color> <color:#51CF66>" + formattedValue + "</color>");
            }
            
            MessageUtils.send(sender, "");
            MessageUtils.send(sender, "<color:#ADB5BD>Use /econfig set <path> <value> to modify</color>");
        }
        
        return true;
    }
    
    private boolean handleReload(CommandSender sender) {
        plugin.reloadConfig();
        
        // Reload all managers that depend on config
        plugin.getCellGroupManager().loadGroups();
        plugin.getDoorManager().loadDoors();
        
        MessageUtils.sendSuccess(sender, "Configuration reloaded successfully!");
        MessageUtils.sendInfo(sender, "Reloaded config.yml and cell-groups.yml");
        MessageUtils.sendInfo(sender, "Some changes may require a server restart to take full effect.");
        
        return true;
    }
    
    private boolean handleSave(CommandSender sender) {
        plugin.saveConfig();
        plugin.getCellGroupManager().saveGroups();
        plugin.getDoorManager().saveDoors();
        
        MessageUtils.sendSuccess(sender, "All configurations saved successfully!");
        MessageUtils.sendInfo(sender, "Saved config.yml, cell-groups.yml, and doors.yml");
        
        return true;
    }
    
    private void sendConfigHelp(CommandSender sender) {
        MessageUtils.send(sender, "<color:#9D4EDD>=== Configuration Commands ===</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/econfig get <path></color> <color:#06FFA5>- Get a config value</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/econfig set <path> <value></color> <color:#06FFA5>- Set a config value</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/econfig list [section]</color> <color:#06FFA5>- List config options</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/econfig reload</color> <color:#06FFA5>- Reload configuration</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/econfig save</color> <color:#06FFA5>- Save all configurations</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>/econfig help</color> <color:#06FFA5>- Show this help</color>");
        MessageUtils.send(sender, "");
        MessageUtils.send(sender, "<color:#ADB5BD>Examples:</color>");
        MessageUtils.send(sender, "<color:#ADB5BD>  /econfig set teleportation.base-cost 100</color>");
        MessageUtils.send(sender, "<color:#ADB5BD>  /econfig get cell-groups.global-limit</color>");
        MessageUtils.send(sender, "<color:#ADB5BD>  /econfig set cell-groups.groups.jcells.cell-limit 1</color>");
        MessageUtils.send(sender, "<color:#ADB5BD>  /econfig list cell-groups</color>");
    }
    
    private String formatConfigValue(Object value) {
        if (value == null) return "null";
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "[]";
            }
            return "[" + list.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ")) + "]";
        }
        if (value instanceof String) {
            String str = value.toString();
            // Don't show color codes in the display
            if (str.contains("&") || str.contains("§")) {
                return "\"" + str + "\"";
            }
            return str;
        }
        return value.toString();
    }
    
    private Object parseValue(String valueStr, Object currentValue) {
        if (valueStr.equalsIgnoreCase("null")) return null;
        if (valueStr.equalsIgnoreCase("true")) return true;
        if (valueStr.equalsIgnoreCase("false")) return false;
        
        // Try to parse based on current type
        if (currentValue instanceof Integer) {
            try {
                return Integer.parseInt(valueStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        if (currentValue instanceof Double) {
            try {
                return Double.parseDouble(valueStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        if (currentValue instanceof List) {
            // Parse as comma-separated list
            return Arrays.stream(valueStr.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        }
        
        // Default to string
        return valueStr;
    }
    
    private void applyConfigChange(String path, Object value) {
        // Apply certain config changes immediately
        switch (path) {
            case "general.debug":
                plugin.debug("Debug mode " + (((boolean) value) ? "enabled" : "disabled"));
                break;
            case "cell-groups.global-limit":
                plugin.getCellGroupManager().setGlobalCellLimit((int) value);
                break;
            // Add more immediate applications as needed
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!PermissionManager.hasPermission(sender, Constants.Permissions.ADMIN)) {
            return Collections.emptyList();
        }
        
        if (args.length == 1) {
            return Arrays.asList("get", "set", "list", "reload", "save", "help")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("get") || subCommand.equals("set")) {
                // Suggest config paths
                List<String> suggestions = new ArrayList<>();
                
                // Add main config sections
                suggestions.addAll(configSections.keySet().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList()));
                
                // Add specific cell-groups paths
                if ("cell-groups".startsWith(args[1].toLowerCase())) {
                    suggestions.add("cell-groups.global-limit");
                    for (CellGroup group : plugin.getCellGroupManager().getAllGroups().values()) {
                        suggestions.add("cell-groups.groups." + group.getName());
                    }
                }
                
                return suggestions;
            } else if (subCommand.equals("list")) {
                return configSections.keySet().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            // Could suggest current value or type hints
            String path = args[1];
            
            // Handle cell-groups paths
            if (path.startsWith("cell-groups.groups.")) {
                String[] parts = path.split("\\.");
                if (parts.length >= 3) {
                    return Arrays.asList("cell-limit", "teleport-cost", "permission", "is-donor");
                }
            } else if (path.equals("cell-groups.global-limit")) {
                return Arrays.asList("15", "10", "-1");
            }
            
            Object currentValue = plugin.getConfig().get(path);
            if (currentValue instanceof Boolean) {
                return Arrays.asList("true", "false");
            }
        }
        
        return Collections.emptyList();
    }
} 