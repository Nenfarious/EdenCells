package dev.lsdmc.gui.holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Marker holder for all cell-management GUIs.
 * The pageKey lets us know which GUI this is.
 */
public class CellGuiHolder implements InventoryHolder {
    // Menu type constants
    public static final String MENU_MAIN = "MAIN";
    public static final String MENU_MY_CELLS = "MY_CELLS";
    public static final String MENU_CELL_INFO = "CELL_INFO";
    public static final String MENU_CELL_MEMBERS = "CELL_MEMBERS";
    public static final String MENU_MEMBER_LIST = "MEMBER_LIST";
    public static final String MENU_MEMBER_SELECT = "MEMBER_SELECT";
    public static final String MENU_DOOR_TOOLS = "DOOR_TOOLS";
    public static final String MENU_ADMIN = "ADMIN";
    public static final String MENU_RESET = "RESET";
    public static final String MENU_OPTIONS = "OPTIONS";
    public static final String MENU_CONFIRM = "CONFIRM";

    private final String pageKey;
    private final String previousKey;
    private Inventory inventory;

    public CellGuiHolder(String pageKey) {
        this(pageKey, null);
    }

    public CellGuiHolder(String pageKey, String previousKey) {
        this.pageKey = pageKey != null ? pageKey : MENU_MAIN;
        this.previousKey = previousKey;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            // Create a fallback inventory if none exists to satisfy @NotNull contract
            inventory = org.bukkit.Bukkit.createInventory(this, 9,
                    net.kyori.adventure.text.Component.text("Error"));
        }
        return inventory;
    }

    /**
     * Set the inventory for this holder (called by Bukkit when creating the inventory)
     */
    public void setInventory(@Nullable Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * @return the key identifying which GUI page this is
     */
    public @NotNull String getPageKey() {
        return pageKey;
    }

    /**
     * @return the key of the previous menu to return to, or null if none
     */
    public @Nullable String getPreviousKey() {
        return previousKey;
    }

    /**
     * @return whether this menu is a specific type
     */
    public boolean isMenuType(String type) {
        return pageKey.equals(type);
    }

    /**
     * @return whether this menu should return to main menu
     */
    public boolean returnsToMain() {
        return previousKey == null || previousKey.equals(MENU_MAIN);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CellGuiHolder other)) return false;
        return pageKey.equals(other.pageKey);
    }

    @Override
    public int hashCode() {
        return pageKey.hashCode();
    }

    @Override
    public String toString() {
        return "CellGuiHolder{pageKey='" + pageKey + "', previousKey='" + previousKey + "'}";
    }
} 