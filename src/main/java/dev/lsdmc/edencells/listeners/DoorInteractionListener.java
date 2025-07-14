package dev.lsdmc.edencells.listeners;

import dev.lsdmc.edencells.EdenCells;
import dev.lsdmc.edencells.managers.DoorManager;
import dev.lsdmc.edencells.security.SecurityManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Handles door interactions for cell access
 */
public final class DoorInteractionListener implements Listener {
    
    private final EdenCells plugin;
    private final DoorManager doorManager;
    private final SecurityManager security;
    
    public DoorInteractionListener(EdenCells plugin, DoorManager doorManager, SecurityManager security) {
        this.plugin = plugin;
        this.doorManager = doorManager;
        this.security = security;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onDoorInteract(PlayerInteractEvent event) {
        // Only right clicks on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        
        // Check if it's a valid door material
        if (!doorManager.isValidDoor(block.getType())) {
            return;
        }
        
        var player = event.getPlayer();
        var location = block.getLocation();
        
        // Check if door is linked
        String linkedRegion = doorManager.getLinkedRegion(location);
        if (linkedRegion == null) {
            return; // Not a linked door, let vanilla handle it
        }
        
        // Rate limit check
        if (security.isRateLimited(player, "door_interact")) {
            MessageUtils.sendError(player, "You're interacting with doors too quickly!");
            event.setCancelled(true);
            return;
        }
        
        // Check access
        if (!doorManager.canAccessDoor(player, location)) {
            event.setCancelled(true);
            
            // Send denial message
            MessageUtils.sendError(player, "You don't have access to this cell!");
            
            // Play sound
            if (plugin.getConfig().getBoolean("doors.sounds.enabled", true)) {
                player.playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            
            // Audit log
            security.auditLog(player, "DOOR_ACCESS_DENIED", linkedRegion, 
                "Attempted to access linked door");
            
            return;
        }
        
        // Cancel default behavior and handle manually
        event.setCancelled(true);
        
        // Toggle the door with cooldown check
        if (!doorManager.toggleDoorForPlayer(block, player)) {
            // Player is on cooldown, don't send a message to avoid spam
            return;
        }
        
        // Audit log successful access
        security.auditLog(player, "DOOR_ACCESS", linkedRegion, 
            "Accessed linked door");
            
        plugin.debug("Player " + player.getName() + " accessed door linked to region " + linkedRegion);
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onDoorBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        
        // Check if it's a valid door material
        if (!doorManager.isValidDoor(block.getType())) {
            return;
        }
        
        var player = event.getPlayer();
        
        // Check if door is linked
        String linkedRegion = doorManager.getLinkedRegion(block.getLocation());
        if (linkedRegion == null) {
            return; // Not a linked door
        }
        
        // Only admins can break linked doors
        if (!player.hasPermission("edencells.admin.doors")) {
            event.setCancelled(true);
            MessageUtils.sendError(player, "You cannot break doors linked to cells!");
            MessageUtils.sendInfo(player, "Contact an admin to unlink this door first.");
            return;
        }
        
        // Admin is breaking the door - unlink it
        doorManager.unlinkDoor(block.getLocation());
        MessageUtils.sendInfo(player, "Door unlinked from region: %s", linkedRegion);
        
        // Audit log
        security.auditLog(player, "DOOR_BREAK", linkedRegion, 
            "Broke and unlinked door");
    }
} 