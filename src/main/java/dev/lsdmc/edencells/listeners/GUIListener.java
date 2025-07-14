package dev.lsdmc.edencells.listeners;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.gui.CellGUI;
import dev.lsdmc.edencells.managers.CellManager;
import dev.lsdmc.edencells.security.SecurityManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import net.alex9849.arm.regions.Region;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles GUI interactions for EdenCells
 */
public final class GUIListener implements Listener {
    
    private final EdenCells plugin;
    private final CellManager cellManager;
    private final SecurityManager security;
    
    public GUIListener(EdenCells plugin, CellManager cellManager, SecurityManager security) {
        this.plugin = plugin;
        this.cellManager = cellManager;
        this.security = security;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        var session = CellGUI.getSession(player);
        if (session == null) {
            return;
        }
        
        // Cancel all clicks in our GUIs
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        
        // Play click sound
        if (plugin.getConfig().getBoolean(Constants.Config.GUI_CLICK_SOUNDS, true)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
        
        // Handle based on session type and slot
        int slot = event.getSlot();
        String sessionType = session.type();
        
        switch (sessionType) {
            case "purchase" -> handlePurchaseClick(player, slot, session);
            case "management" -> handleManagementClick(player, slot, session);
            case "viewer" -> handleViewerClick(player, slot, session);
            case "selection" -> handleCellSelectionClick(player, slot, session);
            // Legacy support
            case "vacant" -> handlePurchaseClick(player, slot, session);
            case "occupied" -> handleManagementClick(player, slot, session);
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            CellGUI.closeSession(player);
        }
    }
    
    private void handleCellSelectionClick(Player player, int slot, CellGUI.GUISession session) {
        // Handle cell selection for management
        if (session.data() instanceof java.util.List<?> list && !list.isEmpty()) {
            @SuppressWarnings("unchecked")
            var cells = (java.util.List<Region>) list;
            
            // Close button check
            if (session.inventory().getItem(slot) != null && 
                session.inventory().getItem(slot).getType() == Material.BARRIER) {
                player.closeInventory();
                return;
        }
        
            // Calculate which cell was clicked based on slot
            if (slot < cells.size()) {
                Region selectedCell = cells.get(slot);
            player.closeInventory();
                
                // Open management GUI for the selected cell
                var cellGUI = new CellGUI(plugin, cellManager, security);
                cellGUI.openCellGUI(player, selectedCell);
            }
        }
    }
    
    private void handlePurchaseClick(Player player, int slot, CellGUI.GUISession session) {
        if (!(session.data() instanceof Region cell)) return;
        
        if (slot == 22) { // Purchase button
            // Check if player can afford it
            double price = cellManager.getPrice(cell);
            if (!plugin.getEconomy().has(player, price)) {
                MessageUtils.sendError(player, "You cannot afford this cell!");
                return;
            }
            
            // Attempt to purchase the cell
            player.closeInventory();
            boolean success = cellManager.purchaseCell(player, cell);
            
            if (success) {
                // Play success sound
                player.playSound(player.getLocation(), "minecraft:entity.player.levelup", 1.0f, 1.0f);
            }
            
        } else if (slot == 40) { // Close button
            player.closeInventory();
        }
    }
    
    private void handleManagementClick(Player player, int slot, CellGUI.GUISession session) {
        if (!(session.data() instanceof Region cell)) return;
        
        switch (slot) {
            case 20 -> { // Extend rental button (for RentRegion)
                if (cell instanceof net.alex9849.arm.regions.RentRegion) {
                    player.closeInventory();
                    boolean success = cellManager.extendRental(player, cell, 1);
                    if (success) {
                        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 1.0f, 1.0f);
                    }
                }
            }
            
            case 24 -> { // Sell/Unrent button (moved from slot 33)
                player.closeInventory();
                // Show confirmation or directly sell
                boolean success = cellManager.sellCell(player, cell);
                if (success) {
                    player.playSound(player.getLocation(), "minecraft:block.note_block.chime", 1.0f, 1.0f);
                }
            }
            
            case 49 -> player.closeInventory(); // Close button
        }
    }
    
    private void handleViewerClick(Player player, int slot, CellGUI.GUISession session) {
        if (slot == 31) { // Close button
            player.closeInventory();
        }
        // Viewer GUI is mostly informational, limited interactions
    }
} 