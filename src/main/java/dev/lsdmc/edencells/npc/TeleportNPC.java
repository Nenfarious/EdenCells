package dev.lsdmc.edencells.npc;

import dev.lsdmc.edencells.EdenCells;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Citizens trait for teleport NPCs
 */
@TraitName("edencells-teleport")
public class TeleportNPC extends Trait {
    
    private EdenCells plugin;
    
    public TeleportNPC() {
        super("edencells-teleport");
    }
    
    @Override
    public void onAttach() {
        plugin = JavaPlugin.getPlugin(EdenCells.class);
    }
    
    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (!event.getNPC().equals(this.getNPC())) {
            return;
        }
        
        var player = event.getClicker();
        var npcId = event.getNPC().getId();
        
        // Delegate to manager - right-click for normal teleportation
        if (plugin != null && plugin.getTeleportNPCManager() != null) {
            plugin.getTeleportNPCManager().handleTeleport(player, npcId, false);
        }
    }
    
    @EventHandler
    public void onLeftClick(NPCLeftClickEvent event) {
        if (!event.getNPC().equals(this.getNPC())) {
            return;
        }
        
        var player = event.getClicker();
        var npcId = event.getNPC().getId();
        
        // Delegate to manager - left-click for donor cell access
        if (plugin != null && plugin.getTeleportNPCManager() != null) {
            plugin.getTeleportNPCManager().handleTeleport(player, npcId, true);
        }
    }
} 