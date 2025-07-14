package dev.lsdmc.edencells.listeners;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.gui.CellGUI;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
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

import java.util.List;

/**
 * Handles sign interactions for ARM cell regions
 * Intercepts ARM sign clicks and opens custom GUIs
 */
public final class CellSignListener implements Listener {
    
    private final EdenCells plugin;
    private final List<String> cellKeywords;
    
    public CellSignListener(EdenCells plugin) {
        this.plugin = plugin;
        this.cellKeywords = plugin.getConfig().getStringList(Constants.Config.CELL_SIGN_KEYWORDS);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign sign)) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check if this is a cell sign by keywords
        if (!isCellSign(sign)) {
            return;
        }
        
        // Try to get the ARM region associated with this sign
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        if (arm == null) {
            return;
        }
        
        Region region = arm.getRegionManager().getRegion(sign);
        if (region == null) {
            return;
        }
        
        // Cancel the event to prevent ARM's default handling
        event.setCancelled(true);
        
        plugin.debug("Intercepted ARM sign click: Player=" + player.getName() + 
                    ", Region=" + region.getRegion().getId());
        
        // Open our custom GUI instead
        openCellGUI(player, region);
    }
    
    /**
     * Check if a sign is a cell sign based on keywords
     */
    private boolean isCellSign(Sign sign) {
        for (Component line : sign.lines()) {
            String lineText = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(line).toLowerCase();
            
            for (String keyword : cellKeywords) {
                if (lineText.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Open the appropriate GUI for the cell
     */
    private void openCellGUI(Player player, Region region) {
        try {
            var cellGUI = new CellGUI(plugin, plugin.getCellManager(), plugin.getSecurityManager());
            cellGUI.openCellGUI(player, region);
            
            // Play interaction sound
            player.playSound(player.getLocation(), "minecraft:ui.button.click", 1.0f, 1.0f);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error opening cell GUI for player " + player.getName() + 
                                     " and region " + region.getRegion().getId() + ": " + e.getMessage());
            
            // Fallback to basic info message
            MessageUtils.sendError(player, "Unable to open cell interface. Please try again.");
        }
    }
}


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT (1).jar!\dev\lsdmc\listeners\CellSignListener.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */