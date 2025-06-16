package dev.lsdmc.gui;

import dev.lsdmc.EdenCells;
import dev.lsdmc.doors.DoorManager;
import dev.lsdmc.gui.holder.CellGuiHolder;
import dev.lsdmc.utils.ConfirmationManager;
import dev.lsdmc.utils.MessageUtils;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.exceptions.*;
import net.alex9849.arm.regions.Region;
import net.alex9849.arm.regions.SellType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redesigned Cell GUI Manager that focuses on cell management without teleportation.
 * Players must use NPCs for teleportation. GUIs are for information and management only.
 */
public class CellGUIManager implements Listener {
    private final EdenCells plugin;
    private final DoorManager doorManager;
    private final Economy economy;
    private final NamespacedKey regionIdKey;
    private final NamespacedKey actionKey;

    // GUI configuration
    private static final int ITEMS_PER_PAGE = 28;
    private static final String SORT_BY_NAME = "name";
    private static final String SORT_BY_DATE = "date";
    private static final String SORT_BY_PRICE = "price";

    // GUI state
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, String> playerSortBy = new HashMap<>();
    private final Map<UUID, String> playerFilters = new HashMap<>();

    public CellGUIManager(EdenCells plugin) {
        this.plugin = plugin;
        this.doorManager = plugin.getDoorManager();
        this.economy = plugin.getEconomy();

        this.regionIdKey = new NamespacedKey(plugin, "region_id");
        this.actionKey = new NamespacedKey(plugin, "action");

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private Component createTitle(String text) {
        return Component.text(text).color(TextColor.color(10040012));
    }

    private String colorize(String text) {
        return text.replace("&", "§");
    }

    private void setRegionId(ItemMeta meta, String regionId) {
        meta.getPersistentDataContainer().set(regionIdKey, PersistentDataType.STRING, regionId);
    }

    private String getRegionId(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(regionIdKey, PersistentDataType.STRING, "");
    }

    private void setAction(ItemMeta meta, String action) {
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
    }

    private String getAction(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(actionKey, PersistentDataType.STRING, "");
    }

    /**
     * Opens the main cell management menu
     */
    public void openMainCellGUI(Player player) {
        CellGuiHolder holder = new CellGuiHolder("MAIN");
        Inventory inv = Bukkit.createInventory(holder, 27, createTitle("Cell Management"));
        holder.setInventory(inv);

        ItemStack myCells = new ItemStack(Material.CHEST);
        ItemMeta myCellsMeta = myCells.getItemMeta();
        myCellsMeta.setDisplayName(colorize("&aMy Cells"));
        myCellsMeta.setLore(Arrays.asList(
                colorize("&7View and manage your owned cells"),
                colorize("&7• View cell information"),
                colorize("&7• Manage members"),
                colorize("&7• Extend rental periods"),
                colorize("&7• Unrent cells")
        ));
        setAction(myCellsMeta, "MY_CELLS");
        myCells.setItemMeta(myCellsMeta);
        inv.setItem(11, myCells);

        ItemStack cellInfo = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = cellInfo.getItemMeta();
        infoMeta.setDisplayName(colorize("&bCell Information"));
        infoMeta.setLore(Arrays.asList(
                colorize("&7Get information about the"),
                colorize("&7cell you're currently in"),
                colorize("&7(if any)")
        ));
        setAction(infoMeta, "CURRENT_CELL_INFO");
        cellInfo.setItemMeta(infoMeta);
        inv.setItem(13, cellInfo);

        ItemStack npcTeleport = new ItemStack(Material.ENDER_PEARL);
        ItemMeta npcMeta = npcTeleport.getItemMeta();
        npcMeta.setDisplayName(colorize("&eCell Teleportation"));
        npcMeta.setLore(Arrays.asList(
                colorize("&7To teleport to your cells,"),
                colorize("&7find a Cell Block NPC and"),
                colorize("&7right-click to teleport."),
                colorize("&7"),
                colorize("&6Note: Teleportation is only"),
                colorize("&6available through NPCs!")
        ));
        npcTeleport.setItemMeta(npcMeta);
        inv.setItem(15, npcTeleport);

        if (player.hasPermission("edencells.admin")) {
            ItemStack adminItem = new ItemStack(Material.REDSTONE);
            ItemMeta aMeta = adminItem.getItemMeta();
            aMeta.setDisplayName(colorize("&cAdmin Tools"));
            aMeta.setLore(Collections.singletonList(colorize("&fManage players, groups, resets")));
            setAction(aMeta, "ADMIN_TOOLS");
            adminItem.setItemMeta(aMeta);
            inv.setItem(22, adminItem);
        }

        player.openInventory(inv);
    }

    /**
     * Opens the player's cell management GUI - NO teleportation, only management
     */
    public void openMyCellsGUI(Player player) {
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) {
            player.sendMessage(Component.text("AdvancedRegionMarket is not available!")
                    .color(NamedTextColor.RED));
            return;
        }

        List<Region> ownedRegions = arm.getRegionManager().getRegionsByOwner(player.getUniqueId());
        String currentSort = playerSortBy.getOrDefault(player.getUniqueId(), SORT_BY_NAME);
        String currentFilter = playerFilters.getOrDefault(player.getUniqueId(), "");
        int currentPage = playerPages.getOrDefault(player.getUniqueId(), 0);

        // Apply sorting
        switch (currentSort) {
            case SORT_BY_NAME:
                ownedRegions.sort(Comparator.comparing(r -> r.getRegion().getId()));
                break;
            case SORT_BY_DATE:
                ownedRegions.sort(Comparator.comparing(Region::getLastreset));
                break;
            case SORT_BY_PRICE:
                ownedRegions.sort(Comparator.comparing(Region::getPricePerPeriod));
                break;
        }

        // Apply filtering
        if (!currentFilter.isEmpty()) {
            ownedRegions = ownedRegions.stream()
                    .filter(r -> r.getRegion().getId().toLowerCase().contains(currentFilter.toLowerCase()))
                    .collect(Collectors.toList());
        }

        int totalPages = Math.max(1, (ownedRegions.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        currentPage = Math.min(currentPage, totalPages - 1);
        playerPages.put(player.getUniqueId(), currentPage);

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, ownedRegions.size());

        CellGuiHolder holder = new CellGuiHolder("MY_CELLS");
        Inventory inv = Bukkit.createInventory(holder, 54, createTitle("My Cells - Management"));
        holder.setInventory(inv);

        if (ownedRegions.isEmpty()) {
            ItemStack noCells = new ItemStack(Material.BARRIER);
            ItemMeta noMeta = noCells.getItemMeta();
            noMeta.setDisplayName(colorize("&cNo Cells Owned"));
            noMeta.setLore(Arrays.asList(
                    colorize("&eYou don't own any cells yet!"),
                    colorize("&7Visit cell signs to rent cells.")
            ));
            noCells.setItemMeta(noMeta);
            inv.setItem(22, noCells);
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                Region region = ownedRegions.get(i);
                String regionId = region.getRegion().getId();

                ItemStack cellItem = new ItemStack(Material.CHEST);
                ItemMeta cellMeta = cellItem.getItemMeta();
                cellMeta.setDisplayName(colorize("&a" + regionId));

                List<String> lore = new ArrayList<>();
                lore.add(colorize("&eWorld: &f" + region.getRegionworld().getName()));
                if (region.getSellType() == SellType.RENT) {
                    lore.add(colorize("&eRented until: &f" + region.replaceVariables("%remainingtime-countdown-writtenout%")));
                }
                lore.add(colorize("&ePrice: &f" + region.getPricePerPeriod() + " " + (economy != null ? economy.currencyNamePlural() : "")));
                lore.add(colorize("&eMembers: &f" + region.getRegion().getMembers().size()));
                
                // Get linked doors count
                int doorCount = doorManager.getDoorsForRegion(regionId).size();
                lore.add(colorize("&eDoors: &f" + doorCount));
                
                lore.add("");
                lore.add(colorize("&eClick to manage this cell"));
                lore.add(colorize("&7Use Cell NPCs to teleport!"));

                cellMeta.setLore(lore);
                setAction(cellMeta, "MANAGE_CELL");
                setRegionId(cellMeta, regionId);
                cellItem.setItemMeta(cellMeta);
                inv.setItem(i - startIndex, cellItem);
            }
        }

        addNavigationControls(inv, currentPage, totalPages);
        addSortingControls(inv, currentSort);
        addFilterControls(inv, currentFilter);

        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&cBack to Main Menu"));
        setAction(backMeta, "BACK_MAIN");
        backButton.setItemMeta(backMeta);
        inv.setItem(49, backButton);

        player.openInventory(inv);
    }

    /**
     * Opens cell management GUI for a specific cell - replaces teleportation with useful actions
     */
    public void openCellManagementGUI(Player player, Region region) {
        String regionId = region.getRegion().getId();
        boolean isOwner = region.getOwner() != null && region.getOwner().equals(player.getUniqueId());
        boolean isAdmin = player.hasPermission("edencells.admin");

        if (!isOwner && !isAdmin) {
            player.sendMessage(Component.text("You don't have permission to manage this cell!")
                    .color(NamedTextColor.RED));
            return;
        }

        CellGuiHolder holder = new CellGuiHolder("CELL_MANAGEMENT");
        Inventory inv = Bukkit.createInventory(holder, 45, createTitle("Manage: " + regionId));
        holder.setInventory(inv);

        // Cell Information
        ItemStack cellInfo = new ItemStack(Material.OAK_SIGN);
        ItemMeta cellMeta = cellInfo.getItemMeta();
        cellMeta.setDisplayName(colorize("&6Cell Information"));
        List<String> cellLore = new ArrayList<>();
        cellLore.add(colorize("&eID: &f" + regionId));
        cellLore.add(colorize("&eOwner: &f" + (region.getOwner() != null ? Bukkit.getOfflinePlayer(region.getOwner()).getName() : "None")));
        cellLore.add(colorize("&eMembers: &f" + region.getRegion().getMembers().size()));
        if (region.getSellType() == SellType.RENT) {
            cellLore.add(colorize("&eRent Until: &f" + region.replaceVariables("%remainingtime-countdown-writtenout%")));
        }
        cellLore.add(colorize("&ePrice: &f" + region.getPricePerPeriod() + " " + (economy != null ? economy.currencyNamePlural() : "")));
        cellMeta.setLore(cellLore);
        cellInfo.setItemMeta(cellMeta);
        inv.setItem(4, cellInfo);

        // Extend Rental (if applicable)
        if (region.getSellType() == SellType.RENT) {
            ItemStack extendItem = new ItemStack(Material.CLOCK);
            ItemMeta extendMeta = extendItem.getItemMeta();
            extendMeta.setDisplayName(colorize("&aExtend Rental"));
            List<String> extendLore = new ArrayList<>();
            extendLore.add(colorize("&eExtend your rental period"));
            extendLore.add(colorize("&eCost: &6" + region.getPricePerPeriod() + " " + (economy != null ? economy.currencyNamePlural() : "")));
            extendLore.add(colorize("&eDuration: &f" + region.replaceVariables("%extendtime-writtenout%")));
            extendLore.add("");
            extendLore.add(colorize("&eClick to extend rental"));
            extendMeta.setLore(extendLore);
            setAction(extendMeta, "EXTEND_RENTAL");
            setRegionId(extendMeta, regionId);
            extendItem.setItemMeta(extendMeta);
            inv.setItem(10, extendItem);
        }

        // Manage Members
        ItemStack membersItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta membersMeta = membersItem.getItemMeta();
        membersMeta.setDisplayName(colorize("&eManage Members"));
        List<String> membersLore = new ArrayList<>();
        membersLore.add(colorize("&eAdd or remove cell members"));
        membersLore.add(colorize("&eCurrent members: &f" + region.getRegion().getMembers().size()));
        if (region.getMaxMembers() >= 0) {
            membersLore.add(colorize("&eMax members: &f" + region.getMaxMembers()));
        }
        membersLore.add("");
        membersLore.add(colorize("&eClick to manage members"));
        membersMeta.setLore(membersLore);
        setAction(membersMeta, "MANAGE_MEMBERS");
        setRegionId(membersMeta, regionId);
        membersItem.setItemMeta(membersMeta);
        inv.setItem(12, membersItem);

        // Reset Cell
        ItemStack resetItem = new ItemStack(Material.TNT);
        ItemMeta resetMeta = resetItem.getItemMeta();
        resetMeta.setDisplayName(colorize("&cReset Cell"));
        resetMeta.setLore(Arrays.asList(
                colorize("&7Reset this cell to its original state"),
                colorize("&cWarning: This will clear all blocks!"),
                colorize("&cThis cannot be undone!"),
                "",
                colorize("&eClick to reset cell")
        ));
        setAction(resetMeta, "RESET_CELL");
        setRegionId(resetMeta, regionId);
        resetItem.setItemMeta(resetMeta);
        inv.setItem(14, resetItem);

        // Unrent/Abandon Cell
        ItemStack unrentItem = new ItemStack(Material.BARRIER);
        ItemMeta unrentMeta = unrentItem.getItemMeta();
        if (region.getSellType() == SellType.RENT) {
            unrentMeta.setDisplayName(colorize("&cUnrent Cell"));
            unrentMeta.setLore(Arrays.asList(
                    colorize("&eReturn this cell and stop paying rent"),
                    colorize("&cWarning: You will lose all access!"),
                    colorize("&cThis cannot be undone!"),
                    "",
                    colorize("&eClick to unrent cell")
            ));
        } else {
            unrentMeta.setDisplayName(colorize("&cAbandon Cell"));
            unrentMeta.setLore(Arrays.asList(
                    colorize("&eReturn this cell to the market"),
                    colorize("&cWarning: You will lose ownership!"),
                    colorize("&cThis cannot be undone!"),
                    "",
                    colorize("&eClick to abandon cell")
            ));
        }
        setAction(unrentMeta, "ABANDON_CELL");
        setRegionId(unrentMeta, regionId);
        unrentItem.setItemMeta(unrentMeta);
        inv.setItem(16, unrentItem);

        // Door Management
        ItemStack doorItem = new ItemStack(Material.IRON_DOOR);
        ItemMeta doorMeta = doorItem.getItemMeta();
        doorMeta.setDisplayName(colorize("&9Door Information"));
        Set<org.bukkit.Location> doors = doorManager.getDoorsForRegion(regionId);
        List<String> doorLore = new ArrayList<>();
        doorLore.add(colorize("&eLinked doors: &f" + doors.size()));
        if (doors.isEmpty()) {
            doorLore.add(colorize("&7No doors linked to this cell"));
            doorLore.add(colorize("&7Ask an admin to link doors"));
        } else {
            doorLore.add(colorize("&7Doors can only be opened by"));
            doorLore.add(colorize("&7cell owners and members"));
        }
        doorMeta.setLore(doorLore);
        doorItem.setItemMeta(doorMeta);
        inv.setItem(22, doorItem);

        // Teleport Information
        ItemStack teleportInfo = new ItemStack(Material.ENDER_EYE);
        ItemMeta teleportMeta = teleportInfo.getItemMeta();
        teleportMeta.setDisplayName(colorize("&dTeleportation Info"));
        teleportMeta.setLore(Arrays.asList(
                colorize("&7To teleport to this cell:"),
                colorize("&71. Find a Cell Block NPC"),
                colorize("&72. Right-click the NPC"),
                colorize("&73. Pay the teleport fee (if required)"),
                "",
                colorize("&eSpecial ranks may teleport for free!")
        ));
        teleportInfo.setItemMeta(teleportMeta);
        inv.setItem(31, teleportInfo);

        // Back Button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&cBack to My Cells"));
        setAction(backMeta, "BACK_MY_CELLS");
        backButton.setItemMeta(backMeta);
        inv.setItem(40, backButton);

        player.openInventory(inv);
    }

    /**
     * Opens member management GUI for a specific region
     */
    public void openRegionMembersGUI(Player player, Region region) {
        String regionId = region.getRegion().getId();
        CellGuiHolder holder = new CellGuiHolder("MEMBERS");
        Inventory inv = Bukkit.createInventory(holder, 27, createTitle("Members: " + regionId));
        holder.setInventory(inv);

        ItemStack infoItem = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(colorize("&6Region Info"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(colorize("&eID: &f" + regionId));
        infoLore.add(colorize("&eMembers: &f" + region.getRegion().getMembers().size()));
        if (region.getMaxMembers() >= 0) {
            infoLore.add(colorize("&eMax Members: &f" + region.getMaxMembers()));
        }
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        ItemStack addMember = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta addMeta = addMember.getItemMeta();
        addMeta.setDisplayName(colorize("&aAdd Member"));
        List<String> addLore = new ArrayList<>();
        addLore.add(colorize("&7Click to add a new member"));
        if (plugin.getMemberAddCost() > 0) {
            addLore.add(colorize("&7Cost: &6$" + plugin.getMemberAddCost()));
        }
        addMeta.setLore(addLore);
        setAction(addMeta, "ADD_MEMBER");
        setRegionId(addMeta, regionId);
        addMember.setItemMeta(addMeta);
        inv.setItem(11, addMember);

        ItemStack removeMember = new ItemStack(Material.BARRIER);
        ItemMeta removeMeta = removeMember.getItemMeta();
        removeMeta.setDisplayName(colorize("&cRemove Member"));
        List<String> removeLore = new ArrayList<>();
        removeLore.add(colorize("&7Click to remove a member"));
        if (plugin.getMemberRemoveCost() > 0) {
            removeLore.add(colorize("&7Cost: &6$" + plugin.getMemberRemoveCost()));
        }
        removeMeta.setLore(removeLore);
        setAction(removeMeta, "REMOVE_MEMBER");
        setRegionId(removeMeta, regionId);
        removeMember.setItemMeta(removeMeta);
        inv.setItem(13, removeMember);

        ItemStack listMembers = new ItemStack(Material.BOOK);
        ItemMeta listMeta = listMembers.getItemMeta();
        listMeta.setDisplayName(colorize("&eMember List"));
        listMeta.setLore(Collections.singletonList(colorize("&7Click to view all members")));
        setAction(listMeta, "LIST_MEMBERS");
        setRegionId(listMeta, regionId);
        listMembers.setItemMeta(listMeta);
        inv.setItem(15, listMembers);

        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&cBack"));
        setAction(backMeta, "BACK_CELL_MANAGEMENT");
        setRegionId(backMeta, regionId);
        backButton.setItemMeta(backMeta);
        inv.setItem(22, backButton);

        player.openInventory(inv);
    }

    private void addNavigationControls(Inventory inv, int currentPage, int totalPages) {
        if (totalPages <= 1) return;

        if (currentPage > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName(colorize("&ePrevious Page"));
            setAction(prevMeta, "MY_CELLS_PAGE");
            prevMeta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "page"),
                    PersistentDataType.INTEGER,
                    currentPage - 1
            );
            prevPage.setItemMeta(prevMeta);
            inv.setItem(45, prevPage);
        }

        ItemStack pageIndicator = new ItemStack(Material.PAPER);
        ItemMeta pageMeta = pageIndicator.getItemMeta();
        pageMeta.setDisplayName(colorize("&ePage " + (currentPage + 1) + "/" + totalPages));
        pageIndicator.setItemMeta(pageMeta);
        inv.setItem(49, pageIndicator);

        if (currentPage < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName(colorize("&eNext Page"));
            setAction(nextMeta, "MY_CELLS_PAGE");
            nextMeta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "page"),
                    PersistentDataType.INTEGER,
                    currentPage + 1
            );
            nextPage.setItemMeta(nextMeta);
            inv.setItem(53, nextPage);
        }
    }

    private void addSortingControls(Inventory inv, String currentSort) {
        ItemStack sortByName = new ItemStack(Material.NAME_TAG);
        ItemMeta nameMeta = sortByName.getItemMeta();
        nameMeta.setDisplayName(colorize("&eSort by Name"));
        if (SORT_BY_NAME.equals(currentSort)) {
            nameMeta.setLore(Collections.singletonList(colorize("&aCurrently selected")));
        }
        setAction(nameMeta, "MY_CELLS_SORT");
        sortByName.setItemMeta(nameMeta);
        inv.setItem(46, sortByName);

        ItemStack sortByDate = new ItemStack(Material.CLOCK);
        ItemMeta dateMeta = sortByDate.getItemMeta();
        dateMeta.setDisplayName(colorize("&eSort by Date"));
        if (SORT_BY_DATE.equals(currentSort)) {
            dateMeta.setLore(Collections.singletonList(colorize("&aCurrently selected")));
        }
        setAction(dateMeta, "MY_CELLS_SORT");
        sortByDate.setItemMeta(dateMeta);
        inv.setItem(47, sortByDate);

        ItemStack sortByPrice = new ItemStack(Material.GOLD_INGOT);
        ItemMeta priceMeta = sortByPrice.getItemMeta();
        priceMeta.setDisplayName(colorize("&eSort by Price"));
        if (SORT_BY_PRICE.equals(currentSort)) {
            priceMeta.setLore(Collections.singletonList(colorize("&aCurrently selected")));
        }
        setAction(priceMeta, "MY_CELLS_SORT");
        sortByPrice.setItemMeta(priceMeta);
        inv.setItem(48, sortByPrice);
    }

    private void addFilterControls(Inventory inv, String currentFilter) {
        ItemStack filterItem = new ItemStack(Material.HOPPER);
        ItemMeta filterMeta = filterItem.getItemMeta();
        filterMeta.setDisplayName(colorize("&eFilter"));
        List<String> lore = new ArrayList<>();
        if (currentFilter != null && !currentFilter.isEmpty()) {
            lore.add(colorize("&7Current filter: &f" + currentFilter));
            lore.add(colorize("&7Click to clear filter"));
        } else {
            lore.add(colorize("&7Click to set filter"));
        }
        filterMeta.setLore(lore);
        setAction(filterMeta, "MY_CELLS_FILTER");
        filterItem.setItemMeta(filterMeta);
        inv.setItem(50, filterItem);
    }

    public void updateFilter(Player player, String filter) {
        if (filter == null || filter.trim().isEmpty() || filter.equalsIgnoreCase("none")) {
            playerFilters.remove(player.getUniqueId());
            player.sendMessage(Component.text("Filter cleared.")
                    .color(NamedTextColor.GREEN));
        } else {
            if (filter.length() > 32) {
                player.sendMessage(Component.text("Filter text is too long (maximum 32 characters).")
                        .color(NamedTextColor.RED));
                return;
            }

            playerFilters.put(player.getUniqueId(), filter.toLowerCase());
            player.sendMessage(Component.text("Filter set to: ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(filter)
                            .color(NamedTextColor.YELLOW)));
        }

        playerPages.put(player.getUniqueId(), 0);
        openMyCellsGUI(player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof CellGuiHolder holder)) return;

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        String action = getAction(meta);
        String regionId = getRegionId(meta);

        switch (action) {
            case "MY_CELLS":
                player.closeInventory();
                openMyCellsGUI(player);
                break;

            case "MANAGE_CELL":
                if (!regionId.isEmpty()) {
                    Region region = plugin.findRegionById(regionId);
                    if (region != null) {
                        player.closeInventory();
                        openCellManagementGUI(player, region);
                    }
                }
                break;

            case "EXTEND_RENTAL":
                if (!regionId.isEmpty()) {
                    player.closeInventory();
                    handleExtendRental(player, regionId);
                }
                break;

            case "MANAGE_MEMBERS":
                if (!regionId.isEmpty()) {
                    Region region = plugin.findRegionById(regionId);
                    if (region != null) {
                        player.closeInventory();
                        openRegionMembersGUI(player, region);
                    }
                }
                break;

            case "RESET_CELL":
                if (!regionId.isEmpty()) {
                    player.closeInventory();
                    handleResetCell(player, regionId);
                }
                break;

            case "ABANDON_CELL":
                if (!regionId.isEmpty()) {
                    player.closeInventory();
                    handleAbandonCell(player, regionId);
                }
                break;

            case "ADD_MEMBER":
                if (!regionId.isEmpty()) {
                    player.closeInventory();
                    plugin.getChatListener().addPendingAction(player, 
                        dev.lsdmc.listeners.ChatListener.ActionType.ADD_MEMBER, regionId);
                }
                break;

            case "REMOVE_MEMBER":
                if (!regionId.isEmpty()) {
                    player.closeInventory();
                    plugin.getChatListener().addPendingAction(player, 
                        dev.lsdmc.listeners.ChatListener.ActionType.REMOVE_MEMBER, regionId);
                }
                break;

            case "LIST_MEMBERS":
                if (!regionId.isEmpty()) {
                    Region region = plugin.findRegionById(regionId);
                    if (region != null) {
                        player.closeInventory();
                        openMemberListGUI(player, region);
                    }
                }
                break;

            case "CURRENT_CELL_INFO":
                player.closeInventory();
                handleCurrentCellInfo(player);
                break;

            case "ADMIN_TOOLS":
                player.closeInventory();
                openAdminToolsGUI(player);
                break;

            case "BACK_MAIN":
                player.closeInventory();
                openMainCellGUI(player);
                break;

            case "BACK_MY_CELLS":
                player.closeInventory();
                openMyCellsGUI(player);
                break;

            case "BACK_CELL_MANAGEMENT":
                if (!regionId.isEmpty()) {
                    Region region = plugin.findRegionById(regionId);
                    if (region != null) {
                        player.closeInventory();
                        openCellManagementGUI(player, region);
                    }
                }
                break;

            case "BACK_MEMBER_MANAGEMENT":
                if (!regionId.isEmpty()) {
                    Region region = plugin.findRegionById(regionId);
                    if (region != null) {
                        player.closeInventory();
                        openRegionMembersGUI(player, region);
                    }
                }
                break;

            case "MY_CELLS_PAGE":
                int page = event.getCurrentItem().getItemMeta().getPersistentDataContainer()
                        .getOrDefault(new NamespacedKey(plugin, "page"), PersistentDataType.INTEGER, 0);
                playerPages.put(player.getUniqueId(), page);
                openMyCellsGUI(player);
                break;

            case "MY_CELLS_SORT":
                String currentSort = playerSortBy.getOrDefault(player.getUniqueId(), SORT_BY_NAME);
                String nextSort = switch (currentSort) {
                    case SORT_BY_NAME -> SORT_BY_DATE;
                    case SORT_BY_DATE -> SORT_BY_PRICE;
                    default -> SORT_BY_NAME;
                };
                playerSortBy.put(player.getUniqueId(), nextSort);
                openMyCellsGUI(player);
                break;

            case "MY_CELLS_FILTER":
                player.closeInventory();
                plugin.getChatListener().addPendingAction(player, 
                    dev.lsdmc.listeners.ChatListener.ActionType.FILTER, "");
                break;

            case "ADMIN_PLAYER_RESET":
                player.closeInventory();
                plugin.getPlayerResetGUI().openPlayerResetGUI(player, 0);
                break;

            case "ADMIN_CELL_GROUPS":
                player.closeInventory();
                player.sendMessage(Component.text("Cell Groups Management").color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("Use /cellgroup commands to manage groups:").color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("• /cellgroup listall - View all groups").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("• /cellgroup create <name> - Create new group").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("• /cellgroup info <name> - View group details").color(NamedTextColor.GRAY));
                break;

            case "ADMIN_DOOR_INFO":
                player.closeInventory();
                player.sendMessage(Component.text("Door Management Information").color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("Total linked doors: " + doorManager.getDoorCount()).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Use /door commands to manage doors:").color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("• /door link <region> - Link door to region").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("• /door unlink - Unlink door").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("• /door info - View door information").color(NamedTextColor.GRAY));
                break;

            case "ADMIN_NPC_INFO":
                player.closeInventory();
                List<String> npcList = plugin.getCellBlockNPC().getBlockIds();
                player.sendMessage(Component.text("NPC Management Information").color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("Active NPCs: " + npcList.size()).color(NamedTextColor.YELLOW));
                if (!npcList.isEmpty()) {
                    player.sendMessage(Component.text("NPC Block IDs: " + String.join(", ", npcList)).color(NamedTextColor.WHITE));
                }
                player.sendMessage(Component.text("Use /cellnpc commands to manage NPCs:").color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("• /cellnpc create <blockId> <name> - Create NPC").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("• /cellnpc list - List all NPCs").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("• /cellnpc remove <blockId> - Remove NPC").color(NamedTextColor.GRAY));
                break;

            case "PURCHASE_CELL":
                if (!regionId.isEmpty()) {
                    player.closeInventory();
                    Region region = plugin.findRegionById(regionId);
                    if (region != null) {
                        try {
                            region.signClickAction(player);
                            player.sendMessage(Component.text("Successfully purchased cell: " + regionId)
                                .color(NamedTextColor.GREEN));
                        } catch (Exception e) {
                            player.sendMessage(Component.text("Failed to purchase cell: " + e.getMessage())
                                .color(NamedTextColor.RED));
                        }
                    }
                }
                break;

            case "PREVIEW_CELL":
                if (!regionId.isEmpty()) {
                    player.closeInventory();
                    handlePreviewCell(player, regionId);
                }
                break;
        }
    }

    private void handleExtendRental(Player player, String regionId) {
        Region region = plugin.findRegionById(regionId);
        if (region == null) {
            player.sendMessage(Component.text("Region not found!").color(NamedTextColor.RED));
            return;
        }

        if (region.getSellType() != SellType.RENT) {
            player.sendMessage(Component.text("This cell is not rented!").color(NamedTextColor.RED));
            return;
        }

        // For now, direct the player to extend through signs or commands
        double cost = region.getPricePerPeriod();
        player.sendMessage(Component.text("To extend your rental:").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("• Right-click the cell sign").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("• Or use the ARM commands").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("Cost: " + plugin.formatCurrency(cost)).color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("Duration: " + region.replaceVariables("%extendtime-writtenout%")).color(NamedTextColor.GREEN));
    }

    private void handleResetCell(Player player, String regionId) {
        plugin.getConfirmationManager().requestConfirmation(player, 
            ConfirmationManager.ConfirmationType.RESET_REGION, regionId);
    }

    private void handleAbandonCell(Player player, String regionId) {
        Region region = plugin.findRegionById(regionId);
        if (region == null) {
            player.sendMessage(Component.text("Region not found!").color(NamedTextColor.RED));
            return;
        }

        plugin.getConfirmationManager().requestConfirmation(player, 
            "Are you sure you want to abandon this cell? This cannot be undone!",
            confirmed -> {
                if (confirmed) {
                    try {
                        region.resetRegion(Region.ActionReason.MANUALLY_BY_ADMIN, true);
                        player.sendMessage(Component.text("Cell abandoned successfully!").color(NamedTextColor.GREEN));
                    } catch (Exception e) {
                        player.sendMessage(Component.text("Failed to abandon cell: " + e.getMessage()).color(NamedTextColor.RED));
                    }
                }
            }, 30);
    }

    private void handleCurrentCellInfo(Player player) {
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        Region currentRegion = null;
        if (arm != null) {
            for (Region region : arm.getRegionManager()) {
                if (region.getRegion().contains(player.getLocation().getBlockX(), 
                    player.getLocation().getBlockY(), player.getLocation().getBlockZ())) {
                    currentRegion = region;
                    break;
                }
            }
        }

        if (currentRegion != null) {
            openCellInfoGUI(player, currentRegion);
        } else {
            player.sendMessage(MessageUtils.warning("You are not currently in a cell region."));
            player.sendMessage(Component.text("Use /cell to manage your cells, or right-click a cell sign for more info.")
                .color(NamedTextColor.GRAY));
        }
    }

    public void openCellInfoGUI(Player player, Region region) {
        String regionId = region.getRegion().getId();
        UUID ownerUUID = region.getOwner();
        String ownerName = ownerUUID != null ? Bukkit.getOfflinePlayer(ownerUUID).getName() : "None";

        CellGuiHolder holder = new CellGuiHolder("INFO");
        Inventory inv = Bukkit.createInventory(holder, 27, createTitle("Cell Info: " + regionId));
        holder.setInventory(inv);

        // Cell Information Display
        ItemStack cellInfo = new ItemStack(Material.OAK_SIGN);
        ItemMeta cellMeta = cellInfo.getItemMeta();
        cellMeta.setDisplayName(colorize("&6" + regionId + " Information"));
        List<String> cellLore = new ArrayList<>();
        cellLore.add(colorize("&eOwner: &f" + ownerName));
        cellLore.add(colorize("&eWorld: &f" + region.getRegionworld().getName()));
        
        if (region.getSellType() == SellType.RENT) {
            cellLore.add(colorize("&eType: &fRental"));
            cellLore.add(colorize("&eRented until: &f" + region.replaceVariables("%remainingtime-countdown-writtenout%")));
        } else {
            cellLore.add(colorize("&eType: &fPurchased"));
        }
        
        cellLore.add(colorize("&ePrice: &f" + region.getPricePerPeriod() + " " + (economy != null ? economy.currencyNamePlural() : "")));
        cellMeta.setLore(cellLore);
        cellInfo.setItemMeta(cellMeta);
        inv.setItem(4, cellInfo);

        // Members Information
        ItemStack membersInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta membersMeta = membersInfo.getItemMeta();
        membersMeta.setDisplayName(colorize("&eCell Members"));
        List<String> membersLore = new ArrayList<>();
        membersLore.add(colorize("&eTotal members: &f" + region.getRegion().getMembers().size()));
        if (region.getMaxMembers() >= 0) {
            membersLore.add(colorize("&eMax members: &f" + region.getMaxMembers()));
        } else {
            membersLore.add(colorize("&eMax members: &fUnlimited"));
        }
        
        if (!region.getRegion().getMembers().isEmpty()) {
            membersLore.add("");
            membersLore.add(colorize("&7Recent members:"));
            int count = 0;
            for (UUID memberUUID : region.getRegion().getMembers()) {
                if (count >= 3) {
                    membersLore.add(colorize("&7... and " + (region.getRegion().getMembers().size() - 3) + " more"));
                    break;
                }
                OfflinePlayer member = Bukkit.getOfflinePlayer(memberUUID);
                String memberName = member.getName() != null ? member.getName() : "Unknown";
                String status = member.isOnline() ? "&a● " : "&7● ";
                membersLore.add(colorize(status + memberName));
                count++;
            }
        }
        membersMeta.setLore(membersLore);
        membersInfo.setItemMeta(membersMeta);
        inv.setItem(11, membersInfo);

        // Door Information
        ItemStack doorInfo = new ItemStack(Material.IRON_DOOR);
        ItemMeta doorMeta = doorInfo.getItemMeta();
        doorMeta.setDisplayName(colorize("&9Linked Doors"));
        Set<org.bukkit.Location> doors = doorManager.getDoorsForRegion(regionId);
        List<String> doorLore = new ArrayList<>();
        doorLore.add(colorize("&eLinked doors: &f" + doors.size()));
        if (doors.isEmpty()) {
            doorLore.add(colorize("&7No doors linked to this cell"));
        } else {
            doorLore.add(colorize("&7Doors can only be opened by"));
            doorLore.add(colorize("&7the owner and members"));
        }
        doorMeta.setLore(doorLore);
        doorInfo.setItemMeta(doorMeta);
        inv.setItem(13, doorInfo);

        // Your Status
        ItemStack statusInfo = new ItemStack(Material.EMERALD);
        ItemMeta statusMeta = statusInfo.getItemMeta();
        statusMeta.setDisplayName(colorize("&aYour Status"));
        List<String> statusLore = new ArrayList<>();
        if (region.getRegion().hasOwner(player.getUniqueId())) {
            statusLore.add(colorize("&aYou are the owner"));
            statusLore.add(colorize("&7You have full management access"));
        } else if (region.getRegion().hasMember(player.getUniqueId())) {
            statusLore.add(colorize("&eYou are a member"));
            statusLore.add(colorize("&7You can use doors and access the cell"));
        } else {
            statusLore.add(colorize("&7You are not a member"));
            statusLore.add(colorize("&7You cannot access this cell"));
        }
        statusMeta.setLore(statusLore);
        statusInfo.setItemMeta(statusMeta);
        inv.setItem(15, statusInfo);

        // Back Button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&cBack"));
        setAction(backMeta, "BACK_MAIN");
        backButton.setItemMeta(backMeta);
        inv.setItem(22, backButton);

        player.openInventory(inv);
    }

    public void openAdminToolsGUI(Player player) {
        if (!player.hasPermission("edencells.admin")) {
            player.sendMessage(MessageUtils.error("You don't have permission to use admin tools!"));
            return;
        }

        CellGuiHolder holder = new CellGuiHolder("ADMIN");
        Inventory inv = Bukkit.createInventory(holder, 27, createTitle("Admin Tools"));
        holder.setInventory(inv);

        // Player Reset Tool
        ItemStack playerReset = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta resetMeta = playerReset.getItemMeta();
        resetMeta.setDisplayName(colorize("&6Reset Player Cells"));
        resetMeta.setLore(Arrays.asList(
                colorize("&7Reset all cells owned by a player"),
                colorize("&7View and manage player regions"),
                colorize("&c⚠ Use with caution!")
        ));
        setAction(resetMeta, "ADMIN_PLAYER_RESET");
        playerReset.setItemMeta(resetMeta);
        inv.setItem(10, playerReset);

        // Cell Groups Management
        ItemStack cellGroups = new ItemStack(Material.CHEST);
        ItemMeta groupsMeta = cellGroups.getItemMeta();
        groupsMeta.setDisplayName(colorize("&bCell Groups"));
        groupsMeta.setLore(Arrays.asList(
                colorize("&7Manage cell groups"),
                colorize("&7Create, delete, and modify groups"),
                colorize("&7Organize cells by category")
        ));
        setAction(groupsMeta, "ADMIN_CELL_GROUPS");
        cellGroups.setItemMeta(groupsMeta);
        inv.setItem(12, cellGroups);

        // Door Management
        ItemStack doorMgmt = new ItemStack(Material.IRON_DOOR);
        ItemMeta doorMeta = doorMgmt.getItemMeta();
        doorMeta.setDisplayName(colorize("&9Door Management"));
        doorMeta.setLore(Arrays.asList(
                colorize("&7Manage linked doors"),
                colorize("&7View door statistics"),
                colorize("&7Total linked doors: &f" + doorManager.getDoorCount())
        ));
        setAction(doorMeta, "ADMIN_DOOR_INFO");
        doorMgmt.setItemMeta(doorMeta);
        inv.setItem(14, doorMgmt);

        // NPC Management
        ItemStack npcMgmt = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta npcMeta = npcMgmt.getItemMeta();
        npcMeta.setDisplayName(colorize("&eNPC Management"));
        npcMeta.setLore(Arrays.asList(
                colorize("&7Manage Cell Block NPCs"),
                colorize("&7Create and remove teleport NPCs"),
                colorize("&7Active NPCs: &f" + plugin.getCellBlockNPC().getBlockIds().size())
        ));
        setAction(npcMeta, "ADMIN_NPC_INFO");
        npcMgmt.setItemMeta(npcMeta);
        inv.setItem(16, npcMgmt);

        // Plugin Information
        ItemStack pluginInfo = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = pluginInfo.getItemMeta();
        infoMeta.setDisplayName(colorize("&aPlugin Information"));
        String version = plugin.getDescription() != null ? plugin.getDescription().getVersion() : "Unknown";
        infoMeta.setLore(Arrays.asList(
                colorize("&7EdenCells v" + version),
                colorize("&7Economy: " + (plugin.getEconomy() != null ? "&aEnabled" : "&cDisabled")),
                colorize("&7Debug Mode: " + (plugin.getConfig().getBoolean("general.debug") ? "&aOn" : "&cOff"))
        ));
        pluginInfo.setItemMeta(infoMeta);
        inv.setItem(22, pluginInfo);

        // Back Button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&cBack to Main Menu"));
        setAction(backMeta, "BACK_MAIN");
        backButton.setItemMeta(backMeta);
        inv.setItem(18, backButton);

        player.openInventory(inv);
    }

    /**
     * Opens a GUI showing all members of a region
     */
    public void openMemberListGUI(Player player, Region region) {
        String regionId = region.getRegion().getId();
        ArrayList<UUID> membersList = new ArrayList<>(region.getRegion().getMembers());
        
        CellGuiHolder holder = new CellGuiHolder("MEMBER_LIST");
        Inventory inv = Bukkit.createInventory(holder, 54, createTitle("Members: " + regionId));
        holder.setInventory(inv);

        // Info item
        ItemStack infoItem = new ItemStack(Material.OAK_SIGN);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(colorize("&6" + regionId + " Members"));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(colorize("&eTotal members: &f" + membersList.size()));
        if (region.getMaxMembers() >= 0) {
            infoLore.add(colorize("&eMax members: &f" + region.getMaxMembers()));
        } else {
            infoLore.add(colorize("&eMax members: &fUnlimited"));
        }
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // List members
        int slot = 9;
        for (UUID memberUUID : membersList) {
            if (slot >= 45) break; // Don't exceed inventory slots

            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUUID);
            String memberName = member.getName() != null ? member.getName() : "Unknown";
            
            ItemStack memberItem = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta memberMeta = memberItem.getItemMeta();
            if (memberMeta instanceof SkullMeta) {
                ((SkullMeta) memberMeta).setOwningPlayer(member);
            }
            memberMeta.setDisplayName(colorize("&a" + memberName));
            List<String> memberLore = new ArrayList<>();
            memberLore.add(colorize("&7Status: " + (member.isOnline() ? "&aOnline" : "&7Offline")));
            if (member.getLastPlayed() > 0) {
                memberLore.add(colorize("&7Last seen: &f" + formatTime(member.getLastPlayed())));
            }
            memberMeta.setLore(memberLore);
            memberItem.setItemMeta(memberMeta);
            inv.setItem(slot, memberItem);
            slot++;
        }

        // Back button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&cBack to Member Management"));
        setAction(backMeta, "BACK_MEMBER_MANAGEMENT");
        setRegionId(backMeta, regionId);
        backButton.setItemMeta(backMeta);
        inv.setItem(49, backButton);

        player.openInventory(inv);
    }

    private String formatTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        if (diff < 60000) { // Less than 1 minute
            return "Just now";
        } else if (diff < 3600000) { // Less than 1 hour
            return (diff / 60000) + " minutes ago";
        } else if (diff < 86400000) { // Less than 1 day
            return (diff / 3600000) + " hours ago";
        } else {
            return (diff / 86400000) + " days ago";
        }
    }

    private void handlePreviewCell(Player player, String regionId) {
        Region region = plugin.findRegionById(regionId);
        if (region == null) {
            player.sendMessage(Component.text("Region not found!").color(NamedTextColor.RED));
            return;
        }

        try {
            region.teleport(player, false);
            player.sendMessage(Component.text("You are now previewing the cell.").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("Use /spawn or /home to return.").color(NamedTextColor.GRAY));
        } catch (NoSaveLocationException e) {
            player.sendMessage(Component.text("No safe teleport location found.").color(NamedTextColor.RED));
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to preview cell: " + e.getMessage()).color(NamedTextColor.RED));
        }
    }

    public void openRentalGUI(Player player, Region region) {
        String regionId = region.getRegion().getId();
        CellGuiHolder holder = new CellGuiHolder("RENTAL");
        Inventory inv = Bukkit.createInventory(holder, 27, createTitle("Rent: " + regionId));
        holder.setInventory(inv);

        // Cell Information
        ItemStack cellInfo = new ItemStack(Material.OAK_SIGN);
        ItemMeta cellMeta = cellInfo.getItemMeta();
        cellMeta.setDisplayName(colorize("&6Cell Information"));
        List<String> cellLore = new ArrayList<>();
        cellLore.add(colorize("&eID: &f" + regionId));
        cellLore.add(colorize("&eWorld: &f" + region.getRegionworld().getName()));
        cellLore.add(colorize("&eType: &f" + (region.getSellType() == SellType.RENT ? "Rental" : "Purchase")));
        cellLore.add(colorize("&ePrice: &f" + region.getPricePerPeriod() + " " + (economy != null ? economy.currencyNamePlural() : "")));
        if (region.getSellType() == SellType.RENT) {
            cellLore.add(colorize("&eDuration: &f" + region.replaceVariables("%extendtime-writtenout%")));
        }
        cellMeta.setLore(cellLore);
        cellInfo.setItemMeta(cellMeta);
        inv.setItem(4, cellInfo);

        // Purchase Button
        ItemStack purchaseItem = new ItemStack(Material.EMERALD);
        ItemMeta purchaseMeta = purchaseItem.getItemMeta();
        purchaseMeta.setDisplayName(colorize("&aPurchase Cell"));
        List<String> purchaseLore = new ArrayList<>();
        purchaseLore.add(colorize("&eCost: &f" + region.getPricePerPeriod() + " " + (economy != null ? economy.currencyNamePlural() : "")));
        if (region.getSellType() == SellType.RENT) {
            purchaseLore.add(colorize("&eDuration: &f" + region.replaceVariables("%extendtime-writtenout%")));
        }
        purchaseLore.add("");
        purchaseLore.add(colorize("&eClick to purchase instantly"));
        purchaseMeta.setLore(purchaseLore);
        setAction(purchaseMeta, "PURCHASE_CELL");
        setRegionId(purchaseMeta, regionId);
        purchaseItem.setItemMeta(purchaseMeta);
        inv.setItem(11, purchaseItem);

        // Preview Button
        ItemStack previewItem = new ItemStack(Material.ENDER_EYE);
        ItemMeta previewMeta = previewItem.getItemMeta();
        previewMeta.setDisplayName(colorize("&bPreview Cell"));
        previewMeta.setLore(Arrays.asList(
            colorize("&7Click to preview the cell"),
            colorize("&7You will be teleported to the cell"),
            colorize("&7Use /spawn or /home to return")
        ));
        setAction(previewMeta, "PREVIEW_CELL");
        setRegionId(previewMeta, regionId);
        previewItem.setItemMeta(previewMeta);
        inv.setItem(15, previewItem);

        // Back Button
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(colorize("&cBack to Main Menu"));
        setAction(backMeta, "BACK_MAIN");
        backButton.setItemMeta(backMeta);
        inv.setItem(22, backButton);

        player.openInventory(inv);
    }

    public void onDisable() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof CellGuiHolder) {
                player.closeInventory();
            }
        }

        playerPages.clear();
        playerSortBy.clear();
        playerFilters.clear();
    }
}