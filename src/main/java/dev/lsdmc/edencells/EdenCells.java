package dev.lsdmc.edencells;

import dev.lsdmc.edencells.commands.CellCommands;
import dev.lsdmc.edencells.commands.CellGroupCommands;
import dev.lsdmc.edencells.commands.ConfigCommands;
import dev.lsdmc.edencells.commands.DoorCommands;
import dev.lsdmc.edencells.commands.MainCommand;
import dev.lsdmc.edencells.commands.TeleportNPCCommands;
import dev.lsdmc.edencells.commands.SecurityCommands;
import dev.lsdmc.edencells.managers.DoorManager;
import dev.lsdmc.edencells.managers.CellManager;
import dev.lsdmc.edencells.managers.TeleportNPCManager;
import dev.lsdmc.edencells.managers.SyncManager;
import dev.lsdmc.edencells.gui.CellGUIManager;
import dev.lsdmc.edencells.listeners.CellSignListener;
import dev.lsdmc.edencells.listeners.DoorInteractionListener;
import dev.lsdmc.edencells.listeners.GUIListener;
import dev.lsdmc.edencells.models.CellGroupManager;
import dev.lsdmc.edencells.security.SecurityManager;
import dev.lsdmc.edencells.utils.ConfigManager;
import dev.lsdmc.edencells.utils.Constants;
import dev.lsdmc.edencells.utils.MessageUtils;
import dev.lsdmc.edencells.utils.PermissionManager;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public class EdenCells extends JavaPlugin implements Listener {
    private static EdenCells instance;
    
    private Economy economy;
    private AdvancedRegionMarket arm;
    private SecurityManager securityManager;
    private CellManager cellManager;
    private DoorManager doorManager;
    private TeleportNPCManager teleportNPCManager;
    private CellGUIManager guiManager;
    private CellSignListener cellSignListener;
    private GUIListener guiListener;
    private CellGroupManager cellGroupManager;
    private ConfigManager configManager;
    private SyncManager syncManager;
    
    @Override
    public void onEnable() {
        try {
            instance = this;
            
            // Initialize Constants keys
            Constants.Keys.init(this);
            
            // Check dependencies
            if (!getServer().getPluginManager().isPluginEnabled("AdvancedRegionMarket")) {
                getLogger().severe("AdvancedRegionMarket not found! Plugin will be disabled.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // Setup ARM
            this.arm = AdvancedRegionMarket.getInstance();
            if (this.arm == null) {
                getLogger().severe("Failed to get AdvancedRegionMarket instance!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // Setup economy with error handling
            if (!setupEconomy()) {
                getLogger().warning("Economy setup failed, some features may be limited.");
            }
            
            // Save default config
            saveDefaultConfig();
            
            // Initialize config manager
            this.configManager = new ConfigManager(this);
            
            // Initialize managers with null safety
            this.securityManager = new SecurityManager(this);
            this.cellManager = new CellManager(this, arm, economy, securityManager);
            this.cellGroupManager = new CellGroupManager(this);
            this.doorManager = new DoorManager(this, cellManager, securityManager);
            this.teleportNPCManager = new TeleportNPCManager(this, cellManager, economy, securityManager);
            this.syncManager = new SyncManager(this);
            
            // Register Citizens trait if Citizens is available
            if (getServer().getPluginManager().isPluginEnabled("Citizens")) {
                try {
                    net.citizensnpcs.api.CitizensAPI.getTraitFactory().registerTrait(
                        net.citizensnpcs.api.trait.TraitInfo.create(dev.lsdmc.edencells.npc.TeleportNPC.class)
                    );
                    getLogger().info("Registered TeleportNPC trait with Citizens");
                } catch (Exception e) {
                    getLogger().warning("Failed to register Citizens trait: " + e.getMessage());
                }
            }
            
            // Initialize GUI manager with error handling
            try {
                this.guiManager = new CellGUIManager(this);
            } catch (Exception e) {
                getLogger().warning("Failed to initialize GUI manager: " + e.getMessage());
                // Create a fallback to prevent null pointer exceptions
                this.guiManager = null;
            }
            
            // Initialize listeners with null checks
            try {
                this.cellSignListener = new CellSignListener(this);
                this.guiListener = new GUIListener(this, cellManager, securityManager);
                
                // Create door interaction listener
                DoorInteractionListener doorInteractionListener = new DoorInteractionListener(this, doorManager, securityManager);
                
                // Register events
                getServer().getPluginManager().registerEvents(cellSignListener, this);
                getServer().getPluginManager().registerEvents(guiListener, this);
                getServer().getPluginManager().registerEvents(doorInteractionListener, this);
            } catch (Exception e) {
                getLogger().severe("Failed to initialize listeners: " + e.getMessage());
                throw e;
            }
            
            // Initialize commands
            try {
                new MainCommand(this);
                
                // Register cell command
                CellCommands cellCommands = new CellCommands(this);
                getCommand("cell").setExecutor(cellCommands);
                getCommand("cell").setTabCompleter(cellCommands);
                
                // Register cellgroup command
                CellGroupCommands cellGroupCommands = new CellGroupCommands(this);
                getCommand("cellgroup").setExecutor(cellGroupCommands);
                getCommand("cellgroup").setTabCompleter(cellGroupCommands);
                
                // Register door command
                DoorCommands doorCommands = new DoorCommands(this, doorManager);
                getCommand("door").setExecutor(doorCommands);
                getCommand("door").setTabCompleter(doorCommands);
                
                // Register teleportnpc command
                TeleportNPCCommands teleportNPCCommands = new TeleportNPCCommands(this, teleportNPCManager);
                getCommand("teleportnpc").setExecutor(teleportNPCCommands);
                getCommand("teleportnpc").setTabCompleter(teleportNPCCommands);
                
                // Register config command
                ConfigCommands configCommands = new ConfigCommands(this);
                getCommand("econfig").setExecutor(configCommands);
                getCommand("econfig").setTabCompleter(configCommands);
                
                // Register security command
                SecurityCommands securityCommands = new SecurityCommands(this);
                getCommand("esecurity").setExecutor(securityCommands);
                getCommand("esecurity").setTabCompleter(securityCommands);
            } catch (Exception e) {
                getLogger().severe("Failed to register commands: " + e.getMessage());
                throw e;
            }
            
            getLogger().info("EdenCells has been enabled successfully!");
            
        } catch (Exception e) {
            getLogger().severe("Critical error during plugin initialization: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Disabling EdenCells...");
        
        try {
            // Save all configurations
            if (cellGroupManager != null) {
                cellGroupManager.saveGroups();
                getLogger().info("Saved cell group configurations");
            }
            
            if (doorManager != null) {
                doorManager.saveDoors();
                getLogger().info("Saved door configurations");
            }
            
            if (teleportNPCManager != null) {
                teleportNPCManager.saveNPCs();
                getLogger().info("Saved teleport NPC configurations");
            }
            
            // Clean up sessions
            if (guiManager != null) {
                dev.lsdmc.edencells.gui.CellGUI.cleanupSessions();
                getLogger().info("Cleaned up GUI sessions");
            }
            
            getLogger().info("EdenCells disabled successfully");
            
        } catch (Exception e) {
            getLogger().severe("Error during plugin shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found, economy features disabled.");
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("Economy provider not found, economy features disabled.");
            return false;
        }
        
        economy = rsp.getProvider();
        return economy != null;
    }
    
    // Getters
    public static EdenCells getInstance() {
        return instance;
    }
    
    public Economy getEconomy() {
        return economy;
    }
    
    public AdvancedRegionMarket getARM() {
        return arm;
    }
    
    public SecurityManager getSecurityManager() {
        return securityManager;
    }
    
    public CellManager getCellManager() {
        return cellManager;
    }
    
    public DoorManager getDoorManager() {
        return doorManager;
    }
    
    public TeleportNPCManager getTeleportNPCManager() {
        return teleportNPCManager;
    }
    
    public CellGUIManager getGuiManager() {
        return guiManager;
    }
    
    // Additional getters for managers and config values
    public CellGroupManager getCellGroupManager() {
        return cellGroupManager;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public SyncManager getSyncManager() {
        return syncManager;
    }
    
    public double getMemberAddCost() {
        return getConfig().getDouble("economy.members.add-cost", 100.0);
    }
    
    public double getMemberRemoveCost() {
        return getConfig().getDouble("economy.members.remove-cost", 50.0);
    }
    
    // Missing reload method
    public void reload() {
        reloadConfig();
        // Could add additional reload logic here if needed
    }
    
    // Utility methods
    public Region findRegionById(String regionId) {
        if (regionId == null || regionId.trim().isEmpty() || arm == null) {
            return null;
        }
        
        try {
            for (Region r : arm.getRegionManager()) {
                if (r != null && r.getRegion() != null && 
                    r.getRegion().getId().equalsIgnoreCase(regionId.trim())) {
                    return r;
                }
            }
        } catch (Exception e) {
            getLogger().warning("Error finding region '" + regionId + "': " + e.getMessage());
        }
        return null;
    }
    
    public String formatCurrency(double amount) {
        if (economy == null) {
            return String.format("$%.2f", amount);
        }
        
        try {
            return economy.format(amount);
        } catch (Exception e) {
            getLogger().warning("Error formatting currency: " + e.getMessage());
            return String.format("$%.2f", amount);
        }
    }
    
    public void debug(String message) {
        if (message == null) return;
        
        try {
            if (getConfig().getBoolean(Constants.Config.DEBUG, false)) {
                getLogger().info("[DEBUG] " + message);
            }
        } catch (Exception e) {
            // Fallback to basic logging if config is broken
            getLogger().info("[DEBUG] " + message);
        }
    }
}