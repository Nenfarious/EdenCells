package dev.lsdmc.edencells.gui;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.managers.CellManager;
import dev.lsdmc.edencells.security.SecurityManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import net.alex9849.arm.regions.Region;
import net.alex9849.arm.regions.RentRegion;
import net.alex9849.arm.regions.SellRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Professional GUI system for EdenCells
 * Provides functional interfaces for cell management
 */
public final class CellGUI {
    
    private final EdenCells plugin;
    private final CellManager cellManager;
    private final SecurityManager security;
    
    // Track open GUIs for cleanup - synchronized for thread safety
    public static final Map<UUID, GUISession> openGUIs = new ConcurrentHashMap<>();
    
    // Cleanup task to prevent memory leaks
    private static final long SESSION_TIMEOUT = 300000; // 5 minutes
    
    public record GUISession(
        Inventory inventory,
        String type,
        Object data,
        long openTime
    ) {}
    
    public CellGUI(EdenCells plugin, CellManager cellManager, SecurityManager security) {
        this.plugin = plugin;
        this.cellManager = cellManager;
        this.security = security;
        
        // Schedule periodic cleanup task
        if (plugin != null) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, 
                CellGUI::cleanupSessions, 200L, 1200L); // Every minute
        }
    }
    
    /**
     * Open the appropriate GUI based on cell status and player relationship
     * @param player The player
     * @param cell The cell
     */
    public void openCellGUI(Player player, Region cell) {
        if (player == null || cell == null) {
            plugin.getLogger().warning("Attempted to open GUI with null player or cell");
            return;
        }
        
        if (!player.isOnline()) {
            plugin.getLogger().warning("Attempted to open GUI for offline player: " + player.getName());
            return;
        }
        
        if (!cellManager.isSold(cell)) {
            // Cell is available for purchase/rent
            openPurchaseGUI(player, cell);
        } else if (cellManager.isOwner(player, cell)) {
            // Player owns this cell - show management GUI
            openManagementGUI(player, cell);
        } else {
            // Viewing someone else's cell
            openViewerGUI(player, cell);
        }
    }
    
    /**
     * GUI for purchasing/renting an available cell
     */
    private void openPurchaseGUI(Player player, Region cell) {
        if (player == null || cell == null) return;
        
        Map<String, String> info = cellManager.getCellInfo(cell);
        String cellId = info.get("id");
        
        Inventory gui = Bukkit.createInventory(null, 45, MessageUtils.fromMiniMessage(
            "<color:#9D4EDD>Purchase Cell: <color:#FFB3C6>" + cellId + "</color></color>"));
        
        // Cell info item (center top)
        ItemStack infoItem = createCellInfoItem(info, Material.EMERALD_BLOCK);
        gui.setItem(13, infoItem);
        
        // Purchase button (center)
        ItemStack purchaseButton = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta purchaseMeta = purchaseButton.getItemMeta();
        if (purchaseMeta == null) return; // Safety check
        
        String actionText = cell instanceof RentRegion ? "Rent Cell" : "Purchase Cell";
        purchaseMeta.displayName(Component.text(actionText, NamedTextColor.GREEN, TextDecoration.BOLD));
        
        List<Component> purchaseLore = new ArrayList<>();
        purchaseLore.add(Component.text("Price: " + info.get("price"), NamedTextColor.AQUA));
        purchaseLore.add(Component.text("Type: " + info.get("type"), NamedTextColor.DARK_AQUA));
        purchaseLore.add(Component.empty());
        purchaseLore.add(Component.text("Click to " + (cell instanceof RentRegion ? "rent" : "purchase") + "!", 
            NamedTextColor.YELLOW));
        
        // Check if player has enough money - null safety
        double price = cellManager.getPrice(cell);
        boolean canAfford = plugin.getEconomy() != null && plugin.getEconomy().has(player, price);
        if (!canAfford) {
            purchaseLore.add(Component.empty());
            purchaseLore.add(Component.text("⚠ Insufficient Funds", NamedTextColor.RED, TextDecoration.BOLD));
            purchaseButton.setType(Material.RED_CONCRETE);
            purchaseMeta.displayName(Component.text("Cannot Afford", NamedTextColor.RED, TextDecoration.BOLD));
        }
        
        purchaseMeta.lore(purchaseLore);
        purchaseButton.setItemMeta(purchaseMeta);
        gui.setItem(22, purchaseButton);
        
        // Balance display - null safety
        if (plugin.getEconomy() != null) {
            ItemStack balanceItem = new ItemStack(Material.GOLD_INGOT);
            ItemMeta balanceMeta = balanceItem.getItemMeta();
            if (balanceMeta != null) {
                balanceMeta.displayName(Component.text("Your Balance", NamedTextColor.GOLD, TextDecoration.BOLD));
                balanceMeta.lore(List.of(
                    Component.text("$" + String.format("%.2f", plugin.getEconomy().getBalance(player)), 
                        canAfford ? NamedTextColor.GREEN : NamedTextColor.RED)
                ));
                balanceItem.setItemMeta(balanceMeta);
                gui.setItem(31, balanceItem);
            }
        }
        
        addCloseButton(gui, 40);
        fillBorders(gui, Material.GRAY_STAINED_GLASS_PANE);
        
        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), new GUISession(gui, "purchase", cell, System.currentTimeMillis()));
    }
    
    /**
     * GUI for managing an owned cell
     */
    private void openManagementGUI(Player player, Region cell) {
        if (player == null || cell == null) return;
        
        Map<String, String> info = cellManager.getCellInfo(cell);
        String cellId = info.get("id");
        
        Inventory gui = Bukkit.createInventory(null, 54, MessageUtils.fromMiniMessage(
            "<color:#9D4EDD>Manage Cell: <color:#FFB3C6>" + cellId + "</color></color>"));
        
        // Cell info with ownership details
        ItemStack infoItem = createCellInfoItem(info, Material.DIAMOND_BLOCK);
        gui.setItem(13, infoItem);
        
        // Add rental info for rent regions
        if (cell instanceof RentRegion) {
            Map<String, String> rentalInfo = cellManager.getRentalInfo(cell);
            ItemStack rentalItem = new ItemStack(Material.CLOCK);
            ItemMeta rentalMeta = rentalItem.getItemMeta();
            if (rentalMeta != null) {
                rentalMeta.displayName(Component.text("Rental Information", NamedTextColor.AQUA, TextDecoration.BOLD));
                
                List<Component> rentalLore = new ArrayList<>();
                rentalLore.add(Component.text("Time Left: " + rentalInfo.getOrDefault("timeLeft", "Unknown"), NamedTextColor.YELLOW));
                rentalLore.add(Component.text("Period Price: " + rentalInfo.getOrDefault("periodPrice", "Unknown"), NamedTextColor.GREEN));
                
                rentalMeta.lore(rentalLore);
                rentalItem.setItemMeta(rentalMeta);
                gui.setItem(11, rentalItem);
                
                // Extend rental button
                ItemStack extendButton = new ItemStack(Material.EMERALD);
                ItemMeta extendMeta = extendButton.getItemMeta();
                if (extendMeta != null) {
                    extendMeta.displayName(Component.text("Extend Rental", NamedTextColor.GREEN, TextDecoration.BOLD));
                    extendMeta.lore(List.of(
                        Component.text("Extend your rental period", NamedTextColor.GRAY),
                        Component.text("Click to extend by 1 period", NamedTextColor.YELLOW)
                    ));
                    extendButton.setItemMeta(extendMeta);
                    gui.setItem(20, extendButton);
                }
            }
        }
        
        // Member management
        ItemStack memberButton = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta memberMeta = memberButton.getItemMeta();
        if (memberMeta != null) {
            memberMeta.displayName(Component.text("Manage Members", NamedTextColor.BLUE, TextDecoration.BOLD));
            
            String memberCount = info.getOrDefault("memberCount", "0");
            List<Component> memberLore = new ArrayList<>();
            memberLore.add(Component.text("Current Members: " + memberCount, NamedTextColor.GRAY));
            memberLore.add(Component.empty());
            memberLore.add(Component.text("Use /cell addmember <player>", NamedTextColor.YELLOW));
            memberLore.add(Component.text("Use /cell removemember <player>", NamedTextColor.YELLOW));
            
            memberMeta.lore(memberLore);
            memberButton.setItemMeta(memberMeta);
            gui.setItem(15, memberButton);
        }
        
        // Sell/Unrent button
        ItemStack sellButton = new ItemStack(Material.RED_CONCRETE);
        ItemMeta sellMeta = sellButton.getItemMeta();
        if (sellMeta != null) {
            String sellText = cell instanceof RentRegion ? "Unrent Cell" : "Sell Cell";
            sellMeta.displayName(Component.text(sellText, NamedTextColor.RED, TextDecoration.BOLD));
            sellMeta.lore(List.of(
                Component.text("⚠ This action cannot be undone!", NamedTextColor.RED),
                Component.text("You will lose access to this cell", NamedTextColor.GRAY),
                Component.text("Use Cell NPCs to teleport to your cells", NamedTextColor.GOLD)
            ));
            sellButton.setItemMeta(sellMeta);
            gui.setItem(24, sellButton);
        }
        
        addCloseButton(gui, 49);
        fillBorders(gui, Material.BLUE_STAINED_GLASS_PANE);
        
        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), new GUISession(gui, "management", cell, System.currentTimeMillis()));
    }
    
    /**
     * GUI for viewing someone else's cell
     */
    private void openViewerGUI(Player player, Region cell) {
        if (player == null || cell == null) return;
        
        Map<String, String> info = cellManager.getCellInfo(cell);
        String cellId = info.get("id");
        
        Inventory gui = Bukkit.createInventory(null, 36, MessageUtils.fromMiniMessage(
            "<color:#9D4EDD>Cell Info: <color:#FFB3C6>" + cellId + "</color></color>"));
        
        // Cell info
        ItemStack infoItem = createCellInfoItem(info, Material.IRON_BLOCK);
        gui.setItem(13, infoItem);
        
        // Owner head
        String ownerName = info.get("owner");
        if (ownerName != null && !ownerName.equals("Unknown") && !ownerName.equals("Available")) {
            ItemStack ownerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) ownerHead.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName));
                skullMeta.displayName(Component.text("Owner: " + ownerName, NamedTextColor.GOLD, TextDecoration.BOLD));
                ownerHead.setItemMeta(skullMeta);
                gui.setItem(11, ownerHead);
            }
        }
        
        // Members list
        String members = info.get("members");
        if (members != null && !members.isEmpty() && !members.equals("None")) {
            ItemStack memberBook = new ItemStack(Material.BOOK);
            ItemMeta bookMeta = memberBook.getItemMeta();
            if (bookMeta != null) {
                bookMeta.displayName(Component.text("Members", NamedTextColor.AQUA, TextDecoration.BOLD));
                
                List<Component> memberLore = new ArrayList<>();
                for (String member : members.split(", ")) {
                    if (member != null && !member.trim().isEmpty()) {
                        memberLore.add(Component.text("• " + member.trim(), NamedTextColor.WHITE));
                    }
                }
                
                bookMeta.lore(memberLore);
                memberBook.setItemMeta(bookMeta);
                gui.setItem(15, memberBook);
            }
        }
        
        addCloseButton(gui, 31);
        fillBorders(gui, Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        
        player.openInventory(gui);
        openGUIs.put(player.getUniqueId(), new GUISession(gui, "viewer", cell, System.currentTimeMillis()));
    }
    
    /**
     * Create a cell information item
     */
    private ItemStack createCellInfoItem(Map<String, String> info, Material material) {
        if (info == null) {
            plugin.getLogger().warning("Attempted to create cell info item with null info");
            return new ItemStack(Material.BARRIER);
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item; // Safety check
        
        meta.displayName(Component.text("Cell: " + info.getOrDefault("id", "Unknown"), 
            NamedTextColor.GOLD, TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("World: " + info.getOrDefault("world", "Unknown"), NamedTextColor.GRAY));
        lore.add(Component.text("Type: " + info.getOrDefault("type", "Unknown"), NamedTextColor.DARK_AQUA));
        lore.add(Component.text("Price: " + info.getOrDefault("price", "Unknown"), NamedTextColor.AQUA));
        lore.add(Component.text("Status: " + info.getOrDefault("sold", "Unknown"), NamedTextColor.YELLOW));
        
        String owner = info.get("owner");
        if (owner != null && !owner.equals("Available") && !owner.equals("Unknown")) {
            lore.add(Component.text("Owner: " + owner, NamedTextColor.GREEN));
        }
        
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Add a close button to the GUI
     */
    private void addCloseButton(Inventory gui, int slot) {
        if (gui == null) return;
        
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.displayName(Component.text("Close", NamedTextColor.RED, TextDecoration.BOLD));
            closeButton.setItemMeta(closeMeta);
            gui.setItem(slot, closeButton);
        }
    }
    
    /**
     * Fill GUI borders with glass panes
     */
    private void fillBorders(Inventory gui, Material material) {
        if (gui == null) return;
        
        ItemStack filler = new ItemStack(material);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(Component.empty());
            filler.setItemMeta(fillerMeta);
            
            int size = gui.getSize();
            
            // Top and bottom rows
            for (int i = 0; i < 9; i++) {
                if (gui.getItem(i) == null) gui.setItem(i, filler);
                if (gui.getItem(size - 9 + i) == null) gui.setItem(size - 9 + i, filler);
            }
            
            // Left and right columns
            for (int i = 1; i < (size / 9) - 1; i++) {
                if (gui.getItem(i * 9) == null) gui.setItem(i * 9, filler);
                if (gui.getItem(i * 9 + 8) == null) gui.setItem(i * 9 + 8, filler);
            }
        }
    }
    
    /**
     * Get GUI session for player
     * @param player The player
     * @return The session or null
     */
    public static GUISession getSession(Player player) {
        if (player == null) return null;
        return openGUIs.get(player.getUniqueId());
    }
    
    /**
     * Close GUI session
     * @param player The player
     */
    public static void closeSession(Player player) {
        if (player == null) return;
        openGUIs.remove(player.getUniqueId());
    }
    
    /**
     * Clean up old sessions - improved to prevent memory leaks
     */
    public static void cleanupSessions() {
        long now = System.currentTimeMillis();
        openGUIs.entrySet().removeIf(entry -> {
            if (entry == null || entry.getValue() == null) {
                return true;
            }
            
            // Remove sessions older than timeout
            if (now - entry.getValue().openTime() > SESSION_TIMEOUT) {
                return true;
            }
            
            // Remove sessions for offline players
            var player = Bukkit.getPlayer(entry.getKey());
            return player == null || !player.isOnline();
        });
    }
    
    // Backward compatibility methods - redirect to new system
    public void openVacantCellGUI(Player player, Region cell) {
        openCellGUI(player, cell);
    }
    
    public void openOccupiedCellGUI(Player player, Region cell) {
        openCellGUI(player, cell);
    }
} 