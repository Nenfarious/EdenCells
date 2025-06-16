package dev.lsdmc.gui;

import dev.lsdmc.EdenCells;
import dev.lsdmc.gui.holder.CellGuiHolder;
import dev.lsdmc.utils.ConfirmationManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

public class PlayerResetGUI implements Listener {
    private final EdenCells plugin;
    private final CellGUIManager guiManager;
    private final ConfirmationManager confirmationManager;
    private final NavigationManager navigationManager;
    private final NamespacedKey playerUuidKey;
    private final NamespacedKey actionKey;
    private static final int PLAYERS_PER_PAGE = 36;
    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerResetGUI(EdenCells plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGuiManager();
        this.confirmationManager = plugin.getConfirmationManager();
        this.navigationManager = plugin.getNavigationManager();
        this.playerUuidKey = new NamespacedKey(plugin, "player_uuid");
        this.actionKey = new NamespacedKey(plugin, "action");

        // Register this as a listener to handle click events
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private Component colorize(String text) {
        return miniMessage.deserialize(text);
    }

    private void setAction(ItemMeta meta, String action) {
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
    }

    private String getAction(ItemMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(actionKey, PersistentDataType.STRING, "");
    }

    public void openPlayerResetGUI(Player admin, int page) {
        if (!admin.hasPermission("edencells.admin.reset")) {
            admin.sendMessage(Component.text("You don't have permission to reset cells!").color(NamedTextColor.RED));
            return;
        }

        this.navigationManager.pushPage(admin, "RESET", "player:" + page);

        CellGuiHolder holder = new CellGuiHolder("PLAYER_RESET");
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("Reset Player Cells - Page " + (page + 1)).color(TextColor.color(10040012)));
        holder.setInventory(inv);

        List<OfflinePlayer> playersWithRegions = getPlayersWithRegions();

        int totalPages = (playersWithRegions.size() + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE;
        int startIndex = page * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, playersWithRegions.size());

        if (playersWithRegions.isEmpty()) {
            ItemStack noPlayers = new ItemStack(Material.BARRIER);
            ItemMeta meta = noPlayers.getItemMeta();
            meta.displayName(colorize("<red>No Players Found With Cells</red>"));
            meta.lore(Arrays.asList(
                    colorize("<gray>No players currently own any cells.</gray>"),
                    colorize("<gray>There are no cells to reset.</gray>")
            ));
            noPlayers.setItemMeta(meta);
            inv.setItem(22, noPlayers);
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                OfflinePlayer offlinePlayer = playersWithRegions.get(i);

                String playerName = offlinePlayer.getName();
                if (playerName == null) playerName = "Unknown Player";
                UUID playerUuid = offlinePlayer.getUniqueId();
                int regionCount = getPlayerRegionCount(playerUuid);

                ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
                skullMeta.displayName(colorize("<yellow>" + playerName + "</yellow>"));
                skullMeta.setOwningPlayer(offlinePlayer);

                List<Component> lore = new ArrayList<>();
                lore.add(colorize("<gray>Owns <white>" + regionCount + "</white> cells</gray>"));
                lore.add(Component.empty());
                lore.add(colorize("<red>Click to reset ALL cells</red>"));
                lore.add(colorize("<red>This cannot be undone!</red>"));
                skullMeta.lore(lore);

                skullMeta.getPersistentDataContainer().set(this.playerUuidKey, PersistentDataType.STRING, playerUuid.toString());
                setAction(skullMeta, "RESET_PLAYER");

                playerHead.setItemMeta(skullMeta);
                inv.setItem(i - startIndex, playerHead);
            }
        }

        if (page > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta meta = prevButton.getItemMeta();
            meta.displayName(colorize("<yellow>Previous Page</yellow>"));
            setAction(meta, "RESET_PAGE");
            meta.getPersistentDataContainer().set(new NamespacedKey(this.plugin, "page"), PersistentDataType.INTEGER, page - 1);
            prevButton.setItemMeta(meta);
            inv.setItem(45, prevButton);
        }

        ItemStack pageIndicator = new ItemStack(Material.PAPER);
        ItemMeta pageMeta = pageIndicator.getItemMeta();
        pageMeta.displayName(colorize("<yellow>Page " + (page + 1) + "/" + Math.max(1, totalPages) + "</yellow>"));
        pageIndicator.setItemMeta(pageMeta);
        inv.setItem(49, pageIndicator);

        if (page < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta meta = nextButton.getItemMeta();
            meta.displayName(colorize("<yellow>Next Page</yellow>"));
            setAction(meta, "RESET_PAGE");
            meta.getPersistentDataContainer().set(new NamespacedKey(this.plugin, "page"), PersistentDataType.INTEGER, page + 1);
            nextButton.setItemMeta(meta);
            inv.setItem(53, nextButton);
        }

        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.displayName(colorize("<red>Back to Admin Menu</red>"));
        setAction(backMeta, "BACK_ADMIN");
        backButton.setItemMeta(backMeta);
        inv.setItem(46, backButton);

        admin.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof CellGuiHolder)) {
            return;
        }

        CellGuiHolder holder = (CellGuiHolder) event.getInventory().getHolder();
        if (!holder.isMenuType("PLAYER_RESET")) {
            return; // Not our GUI
        }

        // Cancel all inventory interactions in our GUIs
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        String action = getAction(meta);

        switch (action) {
            case "RESET_PLAYER":
                String playerUuidStr = meta.getPersistentDataContainer().getOrDefault(
                        playerUuidKey,
                        PersistentDataType.STRING,
                        ""
                );
                if (!playerUuidStr.isEmpty()) {
                    try {
                        UUID targetPlayerUuid = UUID.fromString(playerUuidStr);
                        handlePlayerReset(player, targetPlayerUuid);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(Component.text("Invalid player UUID")
                                .color(NamedTextColor.RED));
                    }
                }
                break;

            case "RESET_PAGE":
                int page = meta.getPersistentDataContainer().getOrDefault(
                        new NamespacedKey(plugin, "page"),
                        PersistentDataType.INTEGER,
                        0
                );
                player.closeInventory();
                openPlayerResetGUI(player, page);
                break;

            case "BACK_ADMIN":
                player.closeInventory();
                guiManager.openAdminToolsGUI(player);
                break;
        }
    }

    public void handlePlayerReset(Player admin, UUID targetPlayerUuid) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerUuid);
        String targetName = target.getName();
        if (targetName == null) targetName = targetPlayerUuid.toString();

        admin.closeInventory();

        boolean confirmed = this.confirmationManager.requestConfirmation(admin, ConfirmationManager.ConfirmationType.RESET_PLAYER, targetPlayerUuid.toString());

        if (confirmed) {
            admin.sendMessage(Component.text("Resetting all cells owned by " + targetName + "...").color(NamedTextColor.YELLOW));
            this.confirmationManager.resetPlayerRegions(admin, targetPlayerUuid);
        }
    }

    private List<OfflinePlayer> getPlayersWithRegions() {
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) return new ArrayList<>();

        List<UUID> ownerUuids = new ArrayList<>();
        for (Region region : arm.getRegionManager()) {
            if (region.isSold() && region.getOwner() != null) {
                ownerUuids.add(region.getOwner());
            }
        }

        ownerUuids = ownerUuids.stream().distinct().collect(Collectors.toList());

        List<OfflinePlayer> players = ownerUuids.stream()
                .map(Bukkit::getOfflinePlayer)
                .filter(p -> p.getName() != null)
                .sorted(Comparator.comparing(OfflinePlayer::getName))
                .collect(Collectors.toList());

        return players;
    }

    private int getPlayerRegionCount(UUID playerUuid) {
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) return 0;

        int count = 0;
        for (Region region : arm.getRegionManager()) {
            if (region.isSold() && region.getOwner() != null && playerUuid.equals(region.getOwner())) {
                count++;
            }
        }

        return count;
    }
}