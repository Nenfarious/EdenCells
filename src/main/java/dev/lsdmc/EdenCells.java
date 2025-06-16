/*     */ package dev.lsdmc;
/*     */
/*     */ import dev.lsdmc.commands.CellCommands;
/*     */ import dev.lsdmc.commands.CellGroupCommands;
/*     */ import dev.lsdmc.commands.DoorCommands;
/*     */ import dev.lsdmc.commands.NPCCommands;
/*     */ import dev.lsdmc.doors.DoorManager;
/*     */ import dev.lsdmc.gui.CellGUIManager;
/*     */ import dev.lsdmc.gui.NavigationManager;
/*     */ import dev.lsdmc.gui.PlayerResetGUI;
/*     */ import dev.lsdmc.gui.SignInteractionHandler;
/*     */ import dev.lsdmc.listeners.ChatListener;
/*     */ import dev.lsdmc.listeners.CellSignListener;
/*     */ import dev.lsdmc.models.CellGroupManager;
/*     */ import dev.lsdmc.utils.ConfirmationManager;
/*     */ import dev.lsdmc.utils.MessageUtils;
/*     */ import dev.lsdmc.utils.PermissionManager;
/*     */ import java.io.File;
/*     */ import java.util.ArrayList;
/*     */ import java.util.Arrays;
/*     */ import java.util.Collections;
/*     */ import java.util.HashMap;
/*     */ import java.util.HashSet;
/*     */ import java.util.List;
/*     */ import java.util.Map;
/*     */ import java.util.Set;
/*     */ import java.util.logging.Level;
/*     */ import java.util.stream.Collectors;
/*     */ import net.alex9849.arm.AdvancedRegionMarket;
/*     */ import net.alex9849.arm.regions.Region;
/*     */ import net.kyori.adventure.text.Component;
/*     */ import net.kyori.adventure.text.TextComponent;
/*     */ import net.kyori.adventure.text.format.NamedTextColor;
/*     */ import net.kyori.adventure.text.format.TextColor;
/*     */ import net.milkbowl.vault.economy.Economy;
/*     */ import org.bukkit.Bukkit;
/*     */ import org.bukkit.ChatColor;
/*     */ import org.bukkit.command.Command;
/*     */ import org.bukkit.command.CommandExecutor;
/*     */ import org.bukkit.command.CommandSender;
/*     */ import org.bukkit.command.TabCompleter;
/*     */ import org.bukkit.configuration.ConfigurationSection;
/*     */ import org.bukkit.configuration.file.FileConfiguration;
/*     */ import org.bukkit.entity.Player;
/*     */ import org.bukkit.event.Listener;
/*     */ import org.bukkit.plugin.Plugin;
/*     */ import org.bukkit.plugin.RegisteredServiceProvider;
/*     */ import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import dev.lsdmc.npc.CellBlockNPC;
import org.bukkit.NamespacedKey;

/*     */
/*     */ public class EdenCells
        /*     */   extends JavaPlugin
        /*     */   implements Listener, CommandExecutor, TabCompleter {
    /*     */   private static EdenCells instance;
    /*  45 */   private Economy economy = null;
    /*     */
    /*     */   private boolean economyEnabled = false;
    /*     */
    /*     */   private DoorManager doorManager;
    /*     */
    /*     */   private SignInteractionHandler signHandler;
    /*     */
    /*     */   private NavigationManager navigationManager;
    /*     */
    /*     */   private ConfirmationManager confirmationManager;
    /*     */   private ChatListener chatListener;
    /*     */   private CellSignListener cellSignListener;
    /*     */   private PlayerResetGUI playerResetGUI;
    /*     */   private CellBlockNPC cellBlockNPC;
    /*     */   private double memberAddCost;
    /*     */   private double memberRemoveCost;
    /*     */   private boolean debugMode;
    /*     */   private Set<String> cellSignKeywords;
    /*  64 */   private final Map<String, List<String>> cellGroups = new HashMap<>();
    /*     */
    /*     */
    /*     */   private CellGroupManager cellGroupManager;
    /*     */
    /*     */
    /*     */   private CellGUIManager guiManager;
    /*     */
    /*     */
    /*     */   private AdvancedRegionMarket arm;
    /*     */
    /*     */
    /*     */   private PermissionManager permissionManager;
    /*     */
    /*     */
    /*     */   private void loadConfig() {
        /* 142 */     FileConfiguration config = getConfig();
        /*     */
        /*     */
        /* 145 */     config.addDefault("economy.member-add-cost", Double.valueOf(100.0D));
        /* 146 */     config.addDefault("economy.member-remove-cost", Double.valueOf(50.0D));
        /* 147 */     config.addDefault("general.debug", Boolean.valueOf(false));
        /* 148 */     config.addDefault("cell-sign-keywords", Arrays.asList(new String[] { "Cell", "Rented" }));
        /*     */
        /*     */
        /* 151 */     config.options().copyDefaults(true);
        /* 152 */     saveConfig();
        /*     */
        /*     */
        /* 155 */     this.memberAddCost = config.getDouble("economy.member-add-cost", 100.0);
        /* 156 */     this.memberRemoveCost = config.getDouble("economy.member-remove-cost", 50.0);
        /* 157 */     this.debugMode = config.getBoolean("general.debug", false);
        /*     */
        /*     */
        /* 160 */     List<String> signWords = config.getStringList("cell-sign-keywords");
        /* 161 */     this.cellSignKeywords = new HashSet<>(signWords);
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   private void loadCellGroups() {
        /* 168 */     this.cellGroups.clear();
        /* 169 */     ConfigurationSection groupsSection = getConfig().getConfigurationSection("groups");
        /*     */
        /* 171 */     if (groupsSection != null) {
            /* 172 */       for (String groupName : groupsSection.getKeys(false)) {
                /* 173 */         List<String> regions = getConfig().getStringList("groups." + groupName);
                /* 174 */         this.cellGroups.put(groupName, regions);
                /*     */       }
            /*     */     }
        /*     */
        /* 178 */     log("Loaded " + this.cellGroups.size() + " cell groups");
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */
    public void saveCellGroups() {
        /* 186 */     getConfig().set("groups", null);
        /*     */
        /*     */
        /* 189 */     for (Map.Entry<String, List<String>> entry : this.cellGroups.entrySet()) {
            /* 190 */       getConfig().set("groups." + (String)entry.getKey(), entry.getValue());
            /*     */     }
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   private boolean setupEconomy() {
        /* 198 */     if (getServer().getPluginManager().getPlugin("Vault") == null) {
            /* 199 */       log("Vault not found, economy features disabled.", Level.WARNING);
            /* 200 */       return false;
            /*     */     }
        /*     */
        /* 203 */     RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        /* 204 */     if (rsp == null) {
            /* 205 */       log("Economy provider not found, economy features disabled.", Level.WARNING);
            /* 206 */       return false;
            /*     */     }
        /*     */
        /* 209 */     this.economy = (Economy)rsp.getProvider();
        /* 210 */     this.economyEnabled = (this.economy != null);
        /*     */
        /* 212 */     if (this.economyEnabled) {
            /* 213 */       log("Vault economy hooked successfully!");
            /*     */     } else {
            /* 215 */       log("Failed to hook into Vault economy.", Level.WARNING);
            /*     */     }
        /*     */
        /* 218 */     return this.economyEnabled;
        /*     */   }
    /*     */
    /*     */   public static EdenCells getInstance() {
        /* 222 */     return instance;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public DoorManager getDoorManager() {
        /* 229 */     return this.doorManager;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public Economy getEconomy() {
        /* 236 */     return this.economy;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public CellGUIManager getGuiManager() {
        /* 243 */     return this.guiManager;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public ConfirmationManager getConfirmationManager() {
        /* 250 */     return this.confirmationManager;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public NavigationManager getNavigationManager() {
        /* 257 */     return this.navigationManager;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public PlayerResetGUI getPlayerResetGUI() {
        /* 264 */     return this.playerResetGUI;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public ChatListener getChatListener() {
        /* 271 */     return this.chatListener;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public double getMemberAddCost() {
        /* 278 */     return this.memberAddCost;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public double getMemberRemoveCost() {
        /* 285 */     return this.memberRemoveCost;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public Set<String> getCellSignKeywords() {
        /* 292 */     return this.cellSignKeywords;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public Map<String, List<String>> getCellGroups() {
        /* 299 */     return this.cellGroups;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public void debug(String message) {
        /* 306 */     if (this.debugMode) {
            /* 307 */       getLogger().info("[DEBUG] " + message);
            /*     */     }
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public void log(String message) {
        /* 315 */     getLogger().info(message);
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public void log(String message, Level level) {
        /* 322 */     getLogger().log(level, message);
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        /* 331 */     if (!(sender instanceof Player)) {
            /* 332 */       sender.sendMessage(Component.text("This command can only be used by players.").color((TextColor)NamedTextColor.RED));
            /* 333 */       return true;
            /*     */     }
        /*     */
        /* 336 */     Player player = (Player)sender;
        /*     */
        /* 338 */     if (cmd.getName().equalsIgnoreCase("edencells")) {
            /* 339 */       if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                /* 340 */         if (!player.hasPermission("edencells.admin")) {
                    /* 341 */           player.sendMessage(Component.text("You don't have permission to reload the plugin.").color((TextColor)NamedTextColor.RED));
                    /* 342 */           return true;
                    /*     */         }
                
                /* 344 */           reloadConfig();
                /* 345 */           loadConfig();
                /* 346 */           doorManager.loadConfig();
                
                /* 347 */           player.sendMessage(Component.text("EdenCells configuration reloaded successfully!").color((TextColor)NamedTextColor.GREEN));
                /* 348 */           log("Configuration reloaded by " + player.getName());
                /* 349 */           return true;
                /*     */         }
            /*     */     }
        /*     */
        /* 352 */     return false;
        /*     */   }
    /*     */
    /*     */
    /*     */   public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        /* 357 */     if (!(sender instanceof Player)) {
            /* 358 */       return Collections.emptyList();
            /*     */     }
        /*     */
        /* 361 */     if (cmd.getName().equalsIgnoreCase("edencells")) {
            /* 362 */       if (args.length == 1) {
                /* 363 */         return filterStartingWith(Arrays.asList("reload"), args[0]);
                /*     */       }
            /*     */     }
        /*     */
        /* 367 */     return Collections.emptyList();
        /*     */   }

    private @NotNull List<String> getStrings() {
        List<String> regions = new ArrayList<>();
        /*     */
        /* 372 */
        AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        /* 373 */
        if (arm != null) {
            /* 374 */             for (Region r : arm.getRegionManager()) {
                /* 375 */               if (r.getRegion() != null) {
                    /* 376 */                 regions.add(r.getRegion().getId());
                    /*     */               }
                /*     */             }
            /*     */           }
        return regions;
    }

    /*     */
    /*     */   private List<String> filterStartingWith(List<String> list, String prefix) {
        /* 382 */     String lowerPrefix = prefix.toLowerCase();
        /* 383 */     return (List<String>)list.stream()
/* 384 */       .filter(s -> s.toLowerCase().startsWith(lowerPrefix))
/* 385 */       .collect(Collectors.toList());
        /*     */   }

    public Region findRegionById(String regionId) {
        /* 630 */     AdvancedRegionMarket arm = AdvancedRegionMarket.getInstance();
        /* 631 */     if (arm == null) return null;
        /*     */
        /* 633 */     for (Region r : arm.getRegionManager()) {
            /* 634 */       if (r.getRegion() != null && r.getRegion().getId().equalsIgnoreCase(regionId)) {
                /* 635 */         return r;
                /*     */       }
            /*     */     }
        /*     */
        /* 639 */     return null;
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   public void saveDefaultConfig() {
        /* 647 */     if (!getDataFolder().exists()) {
            /* 648 */       getDataFolder().mkdirs();
            /*     */     }
        /*     */
        /* 651 */     if (!(new File(getDataFolder(), "config.yml")).exists())
            /* 652 */       saveResource("config.yml", false);
        /*     */   }
    /*     */
    /*     */   public String formatCurrency(double amount) {
        /* 655 */     if (this.economy != null) {
            /* 656 */       return this.economy.format(amount);
            /*     */     }
        /* 658 */     return String.format("$%.2f", amount);
        /*     */   }
    /*     */
    /*     */   public CellGroupManager getCellGroupManager() {
        /* 661 */     return this.cellGroupManager;
        /*     */   }
    /*     */
    /*     */   public CellBlockNPC getCellBlockNPC() {
        /* 664 */     return this.cellBlockNPC;
        /*     */   }
    /*     */
    /*     */   public SignInteractionHandler getSignHandler() {
        /* 667 */     return this.signHandler;
        /*     */   }
    /*     */
    /*     */   public AdvancedRegionMarket getARM() {
        /* 670 */     return this.arm;
        /*     */   }
    /*     */
    /*     */   @Override
    /*     */   public void onEnable() {
        /* 675 */     instance = this;
        /*     */
        /*     */
        /*  678 */     // Check if Citizens is present
        /*  679 */     if (!getServer().getPluginManager().isPluginEnabled("Citizens")) {
            /*  680 */       getLogger().severe("Citizens not found or not enabled!");
            /*  681 */       getServer().getPluginManager().disablePlugin(this);
            /*  682 */       return;
            /*     */     }
        /*     */
        /*  685 */     saveDefaultConfig();
        /*  686 */     loadConfig();
        /*     */
        /*     */
        /*  689 */     setupEconomy();
        /*     */
        /*     */
        /*  692 */     this.navigationManager = new NavigationManager();
        /*  693 */     this.confirmationManager = new ConfirmationManager(this);
        /*  694 */     this.doorManager = new DoorManager(this);
        /*  695 */     this.guiManager = new CellGUIManager(this);
        /*  696 */     this.playerResetGUI = new PlayerResetGUI(this);
        /*  697 */     this.signHandler = new SignInteractionHandler(this, this.guiManager);
        /*  698 */     this.chatListener = new ChatListener(this, this.guiManager);
        /*  699 */     this.cellGroupManager = new CellGroupManager(this);
        /*  700 */     this.cellBlockNPC = new CellBlockNPC(this);
        /*  701 */     this.cellSignListener = new CellSignListener(this);
        /*     */
        /*     */
        /*  703 */     CellCommands cellCommands = new CellCommands(this, this.guiManager);
        /*  704 */     getCommand("cell").setExecutor(cellCommands);
        /*  705 */     getCommand("cell").setTabCompleter(cellCommands);
        /*  706 */     CellGroupCommands groupCommands = new CellGroupCommands(this);
        /*  707 */     getCommand("cellgroup").setExecutor(groupCommands);
        /*  708 */     getCommand("cellgroup").setTabCompleter(groupCommands);
        /*     */
        /*     */
        /*  711 */     DoorCommands doorCommands = new DoorCommands(this, this.doorManager);
        /*  712 */     getCommand("door").setExecutor(doorCommands);
        /*  713 */     getCommand("door").setTabCompleter(doorCommands);
        /*     */
        /*     */
        /*  715 */     NPCCommands npcCommands = new NPCCommands(this);
        /*  716 */     getCommand("cellnpc").setExecutor(npcCommands);
        /*  717 */     getCommand("cellnpc").setTabCompleter(npcCommands);
        /*     */
        /*     */
        /*  719 */     getServer().getPluginManager().registerEvents(this, this);
        /*     */
        /*     */
        /*  721 */     loadCellGroups();
        /*     */
        /*  723 */     log("EdenCells plugin enabled with configuration:");
        /*  724 */     log("Member add cost: $" + this.memberAddCost);
        /*  725 */     log("Member remove cost: $" + this.memberRemoveCost);
        /*  726 */     log("Economy enabled: " + this.economyEnabled);
        /*  727 */     log("Debug mode: " + this.debugMode);
        /*     */
        /*     */
        /*  730 */     this.arm = AdvancedRegionMarket.getInstance();
        /*  731 */     if (this.arm == null) {
            /*  732 */       getLogger().severe("AdvancedRegionMarket not found! Plugin will be disabled.");
            /*  733 */       getServer().getPluginManager().disablePlugin(this);
            /*  734 */       return;
            /*     */     }
        /*     */
        /*  737 */     getLogger().info("EdenCells has been enabled!");
        /*     */   }
    /*     */
    /*     */
    /*     */
    /*     */
    /*     */   @Override
    /*     */   public void onDisable() {
        /*  745 */     if (this.cellGroupManager != null) {
            /*  746 */       this.cellGroupManager.saveGroups();
            /*     */     }
        /*  748 */     saveConfig();
        /*     */
        /*     */
        /*  751 */     if (this.chatListener != null) {
            /*  752 */       this.chatListener.shutdown();
            /*     */     }
        /*     */
        /*  755 */     if (this.doorManager != null) {
            /*  756 */       this.doorManager.onDisable();
            /*     */     }
        /*     */
        /*  759 */     if (this.cellBlockNPC != null) {
            /*  760 */       this.cellBlockNPC.cleanup();
            /*     */     }
        /*     */
        /*  762 */     if (this.guiManager != null) {
            /*  763 */       this.guiManager.onDisable();
            /*     */     }
        /*  765 */     if (this.signHandler != null) {
            /*  766 */       this.signHandler.onDisable();
            /*     */     }
        /*     */
        /*  768 */     log("EdenCells plugin disabled - configuration saved.");
        /*  770 */     getLogger().info("EdenCells has been disabled!");
        /*     */   }
    /*     */
    /*     */   public PermissionManager getPermissionManager() {
        /* 771 */     return this.permissionManager;
        /*     */   }
    /*     */
    /*     */   public NamespacedKey getKey(String key) {
        /* 775 */     return new NamespacedKey(this, key);
        /*     */   }
    /*     */ }


/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT.jar!\dev\lsdmc\EdenCells.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */