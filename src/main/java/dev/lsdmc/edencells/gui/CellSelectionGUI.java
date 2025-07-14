package dev.lsdmc.edencells.gui;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.models.CellGroup;
import dev.lsdmc.edencells.models.CellGroupManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import net.alex9849.arm.regions.Region;
import net.alex9849.arm.regions.RentRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI for selecting between multiple cells
 */
public final class CellSelectionGUI {
    
    private final EdenCells plugin;
    private final CellGroupManager groupManager;
    
    public CellSelectionGUI(EdenCells plugin) {
        this.plugin = plugin;
        this.groupManager = plugin.getCellGroupManager();
    }
    
    /**
     * Open the cell selection GUI
     * @param player The player
     * @param cells The player's cells
     */
    public void openSelectionGUI(Player player, List<Region> cells) {
        if (cells.isEmpty()) {
            MessageUtils.sendInfo(player, "You don't own any cells yet!");
            MessageUtils.sendInfo(player, "Look for cell signs to rent or purchase cells.");
            return;
        }
        
        // Calculate inventory size (multiple of 9, minimum 27, maximum 54)
        int size = Math.min(54, Math.max(27, ((cells.size() + 8) / 9) * 9));
        
        Inventory gui = Bukkit.createInventory(null, size, MessageUtils.fromMiniMessage(
            Constants.Messages.PRIMARY_COLOR + "Your Cells " + 
            Constants.Messages.SECONDARY_COLOR + "(" + cells.size() + " owned)"));
        
        // Add cell items
        for (int i = 0; i < Math.min(cells.size(), 45); i++) { // Max 45 cells (leave room for info)
            Region cell = cells.get(i);
            ItemStack cellItem = createCellItem(cell);
            gui.setItem(i, cellItem);
        }
        
        // Add info item at bottom right
        ItemStack infoItem = createInfoItem(player);
        gui.setItem(size - 1, infoItem);
        
        // Add close button at bottom center
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.displayName(Component.text("Close", NamedTextColor.RED, TextDecoration.BOLD));
        closeButton.setItemMeta(closeMeta);
        gui.setItem(size - 5, closeButton);
        
        // Fill empty slots with glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        
        for (int i = cells.size(); i < size; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
        
        player.openInventory(gui);
        CellGUI.openGUIs.put(player.getUniqueId(), 
            new CellGUI.GUISession(gui, "selection", cells, System.currentTimeMillis()));
    }
    
    /**
     * Create an item representing a cell
     */
    private ItemStack createCellItem(Region cell) {
        Map<String, String> info = plugin.getCellManager().getCellInfo(cell);
        
        // Determine material and color based on cell type and group
        Material material = Material.EMERALD_BLOCK;
        NamedTextColor nameColor = NamedTextColor.GREEN;
        
        CellGroup group = groupManager.getGroupByRegion(cell.getRegion().getId());
        if (group != null && group.isDonorGroup()) {
            material = Material.DIAMOND_BLOCK;
            nameColor = NamedTextColor.AQUA;
        } else if (cell instanceof RentRegion) {
            material = Material.GOLD_BLOCK;
            nameColor = NamedTextColor.GOLD;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // Set display name
        meta.displayName(Component.text("Cell: " + info.get("id"), nameColor, TextDecoration.BOLD));
        
        // Create lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("World: " + info.get("world"), NamedTextColor.GRAY));
        
        if (group != null) {
            lore.add(Component.text("Group: " + group.getDisplayName(), NamedTextColor.YELLOW));
        }
        
        // Add type info
        String type = info.get("type");
        if ("rent".equalsIgnoreCase(type)) {
            lore.add(Component.text("Type: Rental", NamedTextColor.GOLD));
            Map<String, String> rentalInfo = plugin.getCellManager().getRentalInfo(cell);
            if (rentalInfo != null) {
                lore.add(Component.text("Time Left: " + rentalInfo.get("timeLeft"), NamedTextColor.YELLOW));
            }
        } else {
            lore.add(Component.text("Type: Owned", NamedTextColor.GREEN));
        }
        
        // Add member count
        String memberCount = info.get("memberCount");
        if (memberCount != null && !memberCount.equals("0")) {
            lore.add(Component.text("Members: " + memberCount, NamedTextColor.AQUA));
        }
        
        lore.add(Component.empty());
        lore.add(Component.text("Click to manage this cell!", NamedTextColor.GREEN));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create the info item showing cell limits
     */
    private ItemStack createInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(Component.text("Cell Limits", NamedTextColor.GOLD, TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        Map<String, String> limits = groupManager.getPlayerLimitInfo(player);
        
        // Global limit
        lore.add(Component.text("Total Cells: " + limits.get("global"), NamedTextColor.YELLOW));
        
        // Group limits
        for (Map.Entry<String, String> entry : limits.entrySet()) {
            if (!entry.getKey().equals("global")) {
                CellGroup group = groupManager.getGroup(entry.getKey());
                if (group != null) {
                    lore.add(Component.text(group.getDisplayName() + ": " + entry.getValue(), 
                        NamedTextColor.AQUA));
                }
            }
        }
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
} 