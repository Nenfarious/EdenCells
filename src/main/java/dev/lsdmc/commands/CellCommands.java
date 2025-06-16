package dev.lsdmc.commands;

import dev.lsdmc.EdenCells;
import dev.lsdmc.gui.CellGUIManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CellCommands implements CommandExecutor, TabCompleter {
    private final EdenCells plugin;
    private final CellGUIManager guiManager;

    public CellCommands(EdenCells plugin, CellGUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }
        // For now, all subcommands open the main cell GUI
        guiManager.openMainCellGUI(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        if (!command.getName().equalsIgnoreCase("cell")) return Collections.emptyList();
        List<String> subCommands = new ArrayList<>();
        subCommands.add("info");
        subCommands.add("purchase");
        subCommands.add("members");
        subCommands.add("reset");
        subCommands.add("help");
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String sub : subCommands) {
                if (sub.startsWith(prefix)) matches.add(sub);
            }
            return matches;
        }
        return Collections.emptyList();
    }
} 