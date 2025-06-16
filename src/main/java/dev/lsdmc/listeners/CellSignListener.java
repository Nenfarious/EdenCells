package dev.lsdmc.listeners;

import dev.lsdmc.EdenCells;
import dev.lsdmc.gui.CellGUIManager;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Handles interactions with ARM region signs to show cell information
 * and management options. Takes priority over ARM's default behavior.
 */
public class CellSignListener implements Listener {
    private final EdenCells plugin;
    private final CellGUIManager guiManager;

    public CellSignListener(EdenCells plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGuiManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) {
            return;
        }

        Player player = event.getPlayer();
        Sign sign = (Sign) block.getState();

        // Check if this is an ARM region sign
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) return;

        Region region = arm.getRegionManager().getRegion(sign);
        if (region == null) return;

        // Cancel the event to prevent ARM from handling it
        event.setCancelled(true);
        
        plugin.debug("Intercepted ARM sign click: Player=" + player.getName() + ", Region=" + region.getRegion().getId());
        
        // Open our comprehensive cell management GUI
        if (region.isSold()) {
            // Cell is owned - show appropriate GUI based on permissions
            if (region.getRegion().hasOwner(player.getUniqueId())) {
                // Owner - open full management GUI
                guiManager.openCellManagementGUI(player, region);
            } else if (region.getRegion().hasMember(player.getUniqueId())) {
                // Member - open info GUI
                guiManager.openCellInfoGUI(player, region);
            } else {
                // Non-member - show ownership information
                String ownerName = region.getOwner() != null ? 
                    org.bukkit.Bukkit.getOfflinePlayer(region.getOwner()).getName() : "Unknown";
                player.sendMessage(Component.text("Cell Information").color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("ID: " + region.getRegion().getId()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Owner: " + ownerName).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Members: " + region.getRegion().getMembers().size()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("This cell is private - only the owner and members can access it.")
                    .color(NamedTextColor.GRAY));
            }
        } else {
            // Cell is available - show purchase information
            player.sendMessage(Component.text("Cell Available for Purchase!").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("ID: " + region.getRegion().getId()).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Price: " + region.getPricePerPeriod() + " " +
                (plugin.getEconomy() != null ? plugin.getEconomy().currencyNamePlural() : ""))
                .color(NamedTextColor.YELLOW));
            if (region.getSellType() == net.alex9849.arm.regions.SellType.RENT) {
                player.sendMessage(Component.text("Type: Rental").color(NamedTextColor.AQUA));
                player.sendMessage(Component.text("Duration: " + region.replaceVariables("%extendtime-writtenout%"))
                    .color(NamedTextColor.AQUA));
            } else {
                player.sendMessage(Component.text("Type: Purchase").color(NamedTextColor.AQUA));
            }
            player.sendMessage(Component.text("Right-click the sign to purchase/rent this cell!")
                .color(NamedTextColor.GREEN));
        }
    }
} 