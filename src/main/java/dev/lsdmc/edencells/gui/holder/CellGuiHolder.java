package dev.lsdmc.edencells.gui.holder;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom inventory holder for GUI identification
 */
public class CellGuiHolder implements InventoryHolder {
    
    private final String pageKey;
    private Inventory inventory;
    
    public CellGuiHolder(String pageKey) {
        this.pageKey = pageKey;
    }
    
    @Override
    public Inventory getInventory() {
        return inventory;
    }
    
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
    
    public String getPageKey() {
        return pageKey;
    }
}


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT (1).jar!\dev\lsdmc\gui\holder\CellGuiHolder.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */