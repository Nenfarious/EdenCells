package dev.lsdmc.gui;

import dev.lsdmc.EdenCells;
import dev.lsdmc.gui.holder.CellGuiHolder;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class SignInteractionHandler implements Listener {
    private final EdenCells plugin;
    private final CellGUIManager guiManager;
    private static final long CLICK_COOLDOWN = 500; // 0.5 seconds

    public SignInteractionHandler(EdenCells plugin, CellGUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) return;

        Player player = event.getPlayer();

        // Check if player has clicked recently
        if (player.hasMetadata("lastSignClick")) {
            long lastClick = player.getMetadata("lastSignClick").get(0).asLong();
            if (System.currentTimeMillis() - lastClick < CLICK_COOLDOWN) {
                return;
            }
        }
        player.setMetadata("lastSignClick", new FixedMetadataValue(plugin, System.currentTimeMillis()));

        // Check for region sign
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) {
            plugin.getLogger().warning("AdvancedRegionMarket not found!");
            return;
        }
        
        Region region = arm.getRegionManager().getRegion(sign);
        if (region != null) {
            event.setCancelled(true);
            handleRegionSignClick(player, region, event);
            return;
        }

        // Check for custom cell sign
        for (String line : sign.getLines()) {
            if (line != null && (line.contains("[Cell]") || line.contains("[Rent]") || line.contains("[Buy]"))) {
                event.setCancelled(true);
                guiManager.openMainCellGUI(player);
                return;
            }
        }
    }

    private void handleRegionSignClick(Player player, Region region, PlayerInteractEvent event) {
        if (event.getPlayer().isSneaking()) {
            // Handle shift-click for purchase/rent
            try {
                region.signClickAction(player);
                player.sendMessage(Component.text("Successfully purchased cell: " + region.getRegion().getId())
                    .color(NamedTextColor.GREEN));
            } catch (Exception e) {
                player.sendMessage(Component.text("Failed to purchase cell: " + e.getMessage())
                    .color(NamedTextColor.RED));
            }
            return;
        }

        // Regular right-click for info/management
        if (region.isSold()) {
            if (region.getRegion().hasOwner(player.getUniqueId())) {
                guiManager.openCellManagementGUI(player, region);
            } else if (region.getRegion().hasMember(player.getUniqueId())) {
                guiManager.openCellInfoGUI(player, region);
            } else {
                String ownerName = "Unknown";
                if (region.getOwner() != null) {
                    String name = plugin.getServer().getOfflinePlayer(region.getOwner()).getName();
                    if (name != null) {
                        ownerName = name;
                    }
                }
                player.sendMessage(Component.text("This cell is owned by: " + ownerName)
                    .color(NamedTextColor.YELLOW));
            }
        } else {
            guiManager.openRentalGUI(player, region);
        }
    }

    public void cleanup() {
        // Cancel any pending tasks
        plugin.getServer().getScheduler().cancelTasks(plugin);
    }

    public void onDisable() {
        // Close all open inventories that were opened through sign interaction
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof CellGuiHolder) {
                player.closeInventory();
            }
        }
    }
}


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT.jar!\dev\lsdmc\gui\SignInteractionHandler.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */