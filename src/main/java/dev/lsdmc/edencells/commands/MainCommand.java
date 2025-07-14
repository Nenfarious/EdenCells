package dev.lsdmc.edencells.commands;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

/**
 * Main plugin command handler
 */
public final class MainCommand implements CommandExecutor, TabCompleter {
    
    private final EdenCells plugin;
    
    public MainCommand(EdenCells plugin) {
        this.plugin = plugin;
        
        // Register command
        plugin.getCommand("edencells").setExecutor(this);
        plugin.getCommand("edencells").setTabCompleter(this);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendInfo(sender);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(Constants.Permissions.RELOAD)) {
                MessageUtils.sendNoPermission(sender);
                return true;
            }
            
            plugin.reload();
            MessageUtils.sendInfo(sender, Constants.Messages.SUCCESS_COLOR + "EdenCells configuration reloaded!");
            return true;
        }
        
        sendInfo(sender);
        return true;
    }
    
    private void sendInfo(CommandSender sender) {
        MessageUtils.send(sender, "<color:#9D4EDD>[EdenCells]</color> <color:#06FFA5>v" + plugin.getDescription().getVersion() + "</color>");
        MessageUtils.send(sender, "<color:#06FFA5>Prison cell management plugin with ARM integration</color>");
        MessageUtils.send(sender, "<color:#FFB3C6>Commands: </color><color:#ADB5BD>/cell, /door, /teleportnpc</color>");
        
        if (sender.hasPermission(Constants.Permissions.RELOAD)) {
            MessageUtils.send(sender, "<color:#51CF66>Admin: </color><color:#ADB5BD>/edencells reload to reload config</color>");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(Constants.Permissions.RELOAD)) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                return Collections.singletonList("reload");
            }
        }
        
        return Collections.emptyList();
    }
} 