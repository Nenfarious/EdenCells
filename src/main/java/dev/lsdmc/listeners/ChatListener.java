package dev.lsdmc.listeners;

import dev.lsdmc.EdenCells;
import dev.lsdmc.gui.CellGUIManager;
import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.logging.Level;

/**
 * Enhanced chat listener with multiple event handlers and advanced chat capture techniques.
 * Uses multiple approaches to ensure chat messages are properly intercepted and processed.
 */
public class ChatListener implements Listener {

    private final EdenCells plugin;
    private final CellGUIManager guiManager;
    private final ConcurrentHashMap<UUID, PendingAction> pendingActions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "EdenCells-ChatListener-Cleanup");
        t.setDaemon(true);
        return t;
    });

    // Valid username pattern
    private static final Pattern VALID_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
    private static final long ACTION_TIMEOUT = 30_000; // 30 seconds
    private static final int MAX_ATTEMPTS = 3;

    public static class PendingAction {
        private final ActionType type;
        private final String regionId;
        private final long timestamp;
        private int attempts;

        public PendingAction(ActionType type, String regionId) {
            this.type = type;
            this.regionId = regionId != null ? regionId : "";
            this.timestamp = System.currentTimeMillis();
            this.attempts = 0;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > ACTION_TIMEOUT;
        }

        public ActionType getType() { return type; }
        public String getRegionId() { return regionId; }
        public int getAttempts() { return attempts; }
        public void incrementAttempts() { this.attempts++; }
        public long getTimeRemaining() {
            long elapsed = System.currentTimeMillis() - timestamp;
            return Math.max(0, ACTION_TIMEOUT - elapsed);
        }
    }

    public enum ActionType {
        ADD_MEMBER("add a member to", "added as a member", NamedTextColor.GREEN),
        REMOVE_MEMBER("remove a member from", "removed as a member", NamedTextColor.RED),
        FILTER("filter", "filter applied", NamedTextColor.YELLOW);

        private final String actionDescription;
        private final String successMessage;
        private final NamedTextColor color;

        ActionType(String actionDescription, String successMessage, NamedTextColor color) {
            this.actionDescription = actionDescription;
            this.successMessage = successMessage;
            this.color = color;
        }
        public String getActionDescription() { return actionDescription; }
        public String getSuccessMessage() { return successMessage; }
        public NamedTextColor getColor() { return color; }
    }

    public ChatListener(EdenCells plugin, CellGUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Schedule cleanup
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredActions, 30L, 30L, TimeUnit.SECONDS);
        plugin.debug("ChatListener initialized with advanced chat capture");
    }

    private String validateInput(String input, ActionType type) {
        if (input == null || input.isEmpty()) {
            return type == ActionType.FILTER ? "Filter text cannot be empty." : "Player name cannot be empty.";
        }

        if (type == ActionType.FILTER) {
            // Only validate length for filters
            if (input.length() > 32) {
                return "Filter text is too long (maximum 32 characters).";
            }
            return null;
        }

        // Validate player name for member actions
        if (!VALID_USERNAME_PATTERN.matcher(input).matches()) {
            return "Invalid player name. 3-16 chars, alphanumeric/_ only.";
        }
        return null;
    }

    /**
     * Primary chat event handler using AsyncChatEvent with highest priority
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAsyncChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PendingAction action = pendingActions.get(playerId);
        
        // Only process if we have a pending action for this player
        if (action == null) {
            return;
        }

        plugin.debug("Intercepted AsyncChatEvent for " + player.getName() + " with pending action: " + action.getType());

        // CRITICAL: Cancel the event IMMEDIATELY to prevent chat from being sent
        event.setCancelled(true);
        
        // Get message as plain text
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        
        // Process the input on the main thread
        Bukkit.getScheduler().runTask(plugin, () -> processInput(player, action, input));
    }

    /**
     * Fallback handler for legacy chat events
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onLegacyChat(org.bukkit.event.player.PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PendingAction action = pendingActions.get(playerId);
        
        // Only process if we have a pending action for this player
        if (action == null) {
            return;
        }

        plugin.debug("Intercepted legacy PlayerChatEvent for " + player.getName() + " with pending action: " + action.getType());

        // CRITICAL: Cancel the event IMMEDIATELY
        event.setCancelled(true);
        
        // Get the message
        String input = event.getMessage().trim();
        
        // Process the input on the main thread (we're already on main thread for legacy event)
        processInput(player, action, input);
    }

    /**
     * Extra safety net - catch command preprocessing in case someone tries to use commands
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PendingAction action = pendingActions.get(playerId);
        
        // Only process if we have a pending action for this player
        if (action == null) {
            return;
        }

        String command = event.getMessage().toLowerCase();
        
        // Allow certain commands during chat input
        if (command.startsWith("/cancel") || command.startsWith("/edencells") || command.startsWith("/cell")) {
            return;
        }

        plugin.debug("Blocked command during chat input for " + player.getName() + ": " + command);
        
        // Cancel command and remind player they're in chat input mode
        event.setCancelled(true);
        player.sendMessage(Component.text("You're currently in chat input mode. Type your message or 'cancel' to cancel.")
                .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("Action: " + action.getType().getActionDescription())
                .color(NamedTextColor.GRAY));
    }

    /**
     * Core input processing logic
     */
    private void processInput(Player player, PendingAction action, String input) {
        UUID playerId = player.getUniqueId();
        
        plugin.debug("Processing input '" + input + "' for " + player.getName() + " with action: " + action.getType());

        try {
            if ("cancel".equalsIgnoreCase(input)) {
                pendingActions.remove(playerId);
                player.sendMessage(Component.text("Action cancelled.")
                        .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, true));
                playSuccessSound(player);
                
                // Reopen appropriate GUI
                if (action.getType() == ActionType.FILTER) {
                    guiManager.openMyCellsGUI(player);
                } else {
                    Region region = plugin.findRegionById(action.getRegionId());
                    if (region != null) {
                        guiManager.openRegionMembersGUI(player, region);
                    } else {
                        guiManager.openMainCellGUI(player);
                    }
                }
                return;
            }

            String error = validateInput(input, action.getType());
            if (error != null) {
                action.incrementAttempts();
                if (action.getAttempts() >= MAX_ATTEMPTS) {
                    pendingActions.remove(playerId);
                    player.sendMessage(Component.text("Too many invalid attempts. Action cancelled.")
                            .color(NamedTextColor.RED));
                    playErrorSound(player);
                    
                    // Reopen appropriate GUI
                    if (action.getType() == ActionType.FILTER) {
                        guiManager.openMyCellsGUI(player);
                    } else {
                        reopenMembersGUI(player, action.getRegionId());
                    }
                    return;
                }
                
                player.sendMessage(Component.text("Error: " + error).color(NamedTextColor.RED));
                player.sendMessage(Component.text("Please try again (" +
                                (MAX_ATTEMPTS - action.getAttempts()) + " attempts remaining)")
                        .color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Type 'cancel' to cancel.").color(NamedTextColor.GRAY));
                playErrorSound(player);
                return;
            }

            // Input is valid, remove pending action and process
            pendingActions.remove(playerId);
            
            if (action.getType() == ActionType.FILTER) {
                guiManager.updateFilter(player, input);
            } else {
                processAction(player, action, input);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling chat action for " + player.getName(), e);
            pendingActions.remove(playerId);
            player.sendMessage(Component.text("An error occurred processing your input.")
                    .color(NamedTextColor.RED));
            
            // Reopen appropriate GUI
            if (action.getType() == ActionType.FILTER) {
                guiManager.openMyCellsGUI(player);
            } else {
                guiManager.openMainCellGUI(player);
            }
        }
    }

    public void addPendingAction(Player player, ActionType type, String regionId) {
        if (player == null || type == null) return;
        
        UUID playerId = player.getUniqueId();
        
        // Clear any existing pending action
        pendingActions.remove(playerId);
        
        // Add new pending action
        pendingActions.put(playerId, new PendingAction(type, regionId));
        
        plugin.debug("Added pending action for " + player.getName() + ": " + type + " (region: " + regionId + ")");
        
        showActionInstructions(player, type, regionId);
        playNotificationSound(player);
    }

    public void clearPendingAction(Player player) {
        if (player == null) return;
        pendingActions.remove(player.getUniqueId());
        plugin.debug("Cleared pending action for " + player.getName());
    }

    private void processAction(Player player, PendingAction action, String targetName) {
        ActionType type = action.getType();
        String regionId = action.getRegionId();
        
        plugin.debug("Processing action " + type + " for " + player.getName() + " with target: " + targetName);
        
        try {
            OfflinePlayer target = findPlayerByName(targetName);
            if (target == null) {
                player.sendMessage(Component.text("Player not found: " + targetName).color(NamedTextColor.RED));
                playErrorSound(player);
                reopenMembersGUI(player, regionId);
                return;
            }
            
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage(Component.text("You cannot modify yourself.").color(NamedTextColor.RED));
                playErrorSound(player);
                reopenMembersGUI(player, regionId);
                return;
            }
            
            Region region = plugin.findRegionById(regionId);
            if (region == null) {
                player.sendMessage(Component.text("Region not found: " + regionId).color(NamedTextColor.RED));
                playErrorSound(player);
                guiManager.openMainCellGUI(player);
                return;
            }
            
            if (!region.getRegion().hasOwner(player.getUniqueId()) && !player.hasPermission("edencells.admin")) {
                player.sendMessage(Component.text("You don't own this region.").color(NamedTextColor.RED));
                playErrorSound(player);
                guiManager.openMainCellGUI(player);
                return;
            }

            boolean success = switch (type) {
                case ADD_MEMBER -> addMember(player, region, target);
                case REMOVE_MEMBER -> removeMember(player, region, target);
                case FILTER -> {
                    guiManager.updateFilter(player, targetName);
                    yield true;
                }
            };

            if (success) {
                playSuccessSound(player);
            } else {
                playErrorSound(player);
            }
            
            // Always reopen the GUI after processing
            reopenMembersGUI(player, regionId);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error processing action for " + player.getName(), e);
            player.sendMessage(Component.text("Error processing action: " + e.getMessage())
                    .color(NamedTextColor.RED));
            playErrorSound(player);
            reopenMembersGUI(player, action.getRegionId());
        }
    }

    private OfflinePlayer findPlayerByName(String name) {
        if (name == null) return null;
        
        // Try exact match first
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        
        // Try cached offline player
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.hasPlayedBefore()) return cached;
        
        // Try case-insensitive search of online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        
        return null;
    }

    private boolean addMember(Player player, Region region, OfflinePlayer target) {
        String tName = target.getName() != null ? target.getName() : "Unknown";
        
        if (region.getRegion().hasMember(target.getUniqueId())) {
            player.sendMessage(Component.text(tName + " is already a member.").color(NamedTextColor.RED));
            return false;
        }
        
        int max = region.getMaxMembers();
        if (max >= 0 && region.getRegion().getMembers().size() >= max) {
            player.sendMessage(Component.text("Member limit of " + max + " reached.").color(NamedTextColor.RED));
            return false;
        }
        
        double cost = plugin.getMemberAddCost();
        if (cost > 0) {
            Economy eco = plugin.getEconomy();
            if (eco == null || !eco.has(player, cost)) {
                player.sendMessage(Component.text("Insufficient funds. Cost: $" + plugin.formatCurrency(cost))
                        .color(NamedTextColor.RED));
                return false;
            }
            if (!eco.withdrawPlayer(player, cost).transactionSuccess()) {
                player.sendMessage(Component.text("Payment failed.").color(NamedTextColor.RED));
                return false;
            }
            player.sendMessage(Component.text("$" + plugin.formatCurrency(cost) + " withdrawn.")
                    .color(NamedTextColor.YELLOW));
        }
        
        region.getRegion().addMember(target.getUniqueId());
        saveRegion(region);
        
        player.sendMessage(Component.text("Added ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(tName).color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" to region ").color(NamedTextColor.GREEN))
                .append(Component.text(region.getRegion().getId()).color(NamedTextColor.YELLOW)));
        
        if (target.isOnline()) {
            Player to = (Player) target;
            to.sendMessage(Component.text("You have been added to cell ")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(region.getRegion().getId()).color(NamedTextColor.WHITE))
                    .append(Component.text(" by ").color(NamedTextColor.YELLOW))
                    .append(Component.text(player.getName()).color(NamedTextColor.WHITE)));
            playNotificationSound(to);
        }
        
        return true;
    }

    private boolean removeMember(Player player, Region region, OfflinePlayer target) {
        String tName = target.getName() != null ? target.getName() : "Unknown";
        
        if (!region.getRegion().hasMember(target.getUniqueId())) {
            player.sendMessage(Component.text(tName + " is not a member.").color(NamedTextColor.RED));
            return false;
        }
        
        double cost = plugin.getMemberRemoveCost();
        if (cost > 0) {
            Economy eco = plugin.getEconomy();
            if (eco == null || !eco.has(player, cost)) {
                player.sendMessage(Component.text("Insufficient funds. Cost: " + plugin.formatCurrency(cost))
                        .color(NamedTextColor.RED));
                return false;
            }
            if (!eco.withdrawPlayer(player, cost).transactionSuccess()) {
                player.sendMessage(Component.text("Payment failed.").color(NamedTextColor.RED));
                return false;
            }
            player.sendMessage(Component.text("$" + plugin.formatCurrency(cost) + " withdrawn.")
                    .color(NamedTextColor.YELLOW));
        }
        
        region.getRegion().removeMember(target.getUniqueId());
        saveRegion(region);
        
        player.sendMessage(Component.text("Removed ")
                .color(NamedTextColor.GREEN)
                .append(Component.text(tName).color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" from region ").color(NamedTextColor.GREEN))
                .append(Component.text(region.getRegion().getId()).color(NamedTextColor.YELLOW)));
        
        if (target.isOnline()) {
            Player to = (Player) target;
            to.sendMessage(Component.text("You have been removed from cell ")
                    .color(NamedTextColor.RED)
                    .append(Component.text(region.getRegion().getId()).color(NamedTextColor.WHITE))
                    .append(Component.text(" by ").color(NamedTextColor.YELLOW))
                    .append(Component.text(player.getName()).color(NamedTextColor.WHITE)));
            playNotificationSound(to);
        }
        
        return true;
    }

    private void saveRegion(Region region) {
        try {
            region.queueSave();
        } catch (Exception e) {
            try {
                AdvancedRegionMarket.getInstance().getRegionManager().saveFile();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to save region", ex);
            }
        }
    }

    private void reopenMembersGUI(Player player, String regionId) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Region region = plugin.findRegionById(regionId);
            if (region != null) {
                guiManager.openRegionMembersGUI(player, region);
            } else {
                guiManager.openMainCellGUI(player);
            }
        }, 20L); // Reduced delay from 30L to 20L
    }

    private void showActionInstructions(Player player, ActionType type, String regionId) {
        Region region = plugin.findRegionById(regionId);
        String name = region != null ? region.getRegion().getId() : regionId;

        player.sendMessage(Component.text("──────────────────────────────────").color(NamedTextColor.DARK_PURPLE));
        player.sendMessage(Component.text("Chat Input Required").color(NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
        player.sendMessage(Component.text("──────────────────────────────────").color(NamedTextColor.DARK_PURPLE));

        switch (type) {
            case ADD_MEMBER -> {
                player.sendMessage(Component.text("Enter player name to add to ").color(NamedTextColor.YELLOW)
                        .append(Component.text(name).color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true)));
                if (region != null) {
                    int cur = region.getRegion().getMembers().size();
                    int max = region.getMaxMembers();
                    if (max >= 0) {
                        int avail = max - cur;
                        if (avail <= 0) {
                            player.sendMessage(Component.text("⚠ Member limit reached!").color(NamedTextColor.RED));
                        } else {
                            player.sendMessage(Component.text("Available slots: " + avail).color(NamedTextColor.GREEN));
                        }
                    } else {
                        player.sendMessage(Component.text("Unlimited member slots").color(NamedTextColor.GREEN));
                    }
                }
                player.sendMessage(Component.text("💡 Tip: Player must have joined the server before").color(NamedTextColor.GRAY));
            }
            case REMOVE_MEMBER -> {
                player.sendMessage(Component.text("Enter player name to remove from ").color(NamedTextColor.YELLOW)
                        .append(Component.text(name).color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true)));
                if (region != null && !region.getRegion().getMembers().isEmpty()) {
                    player.sendMessage(Component.text("Current members: ")
                            .color(NamedTextColor.GRAY)
                            .append(Component.text(String.valueOf(region.getRegion().getMembers().size()))
                                    .color(NamedTextColor.WHITE)));
                } else {
                    player.sendMessage(Component.text("No members to remove").color(NamedTextColor.YELLOW));
                }
            }
            case FILTER -> {
                player.sendMessage(Component.text("Enter text to filter cell names").color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Current filter: ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text(regionId.isEmpty() ? "none" : regionId)
                                .color(NamedTextColor.WHITE)));
                player.sendMessage(Component.text("💡 Tip: Use partial names like 'j' for all J-block cells").color(NamedTextColor.GRAY));
            }
        }

        double cost = type == ActionType.ADD_MEMBER ? plugin.getMemberAddCost() : 
                     type == ActionType.REMOVE_MEMBER ? plugin.getMemberRemoveCost() : 0;
        if (cost > 0) {
            Economy eco = plugin.getEconomy();
            if (eco != null) {
                double bal = eco.getBalance(player);
                Component costComp = Component.text("Cost: $" + plugin.formatCurrency(cost))
                        .color(bal >= cost ? NamedTextColor.GREEN : NamedTextColor.RED);
                if (bal < cost) {
                    costComp = costComp.append(Component.text(" (Insufficient funds)").color(NamedTextColor.RED));
                    player.sendMessage(Component.text("⚠ You need $" + plugin.formatCurrency(cost - bal) + " more")
                        .color(NamedTextColor.RED));
                }
                player.sendMessage(costComp);
            }
        }

        player.sendMessage(Component.text("──────────────────────────────────").color(NamedTextColor.DARK_PURPLE));
        player.sendMessage(Component.text("Type player name, or 'cancel' to cancel.").color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("⏰ Expires in 30 seconds.").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("──────────────────────────────────").color(NamedTextColor.DARK_PURPLE));
    }

    // Sound helpers
    private void playSuccessSound(Player player) {
        player.playSound(player.getLocation(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1.0f, 1.2f);
    }
    
    private void playErrorSound(Player player) {
        player.playSound(player.getLocation(),
                Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 1.0f, 1.0f);
    }
    
    private void playNotificationSound(Player player) {
        player.playSound(player.getLocation(),
                Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.5f, 1.8f);
    }

    /**
     * Cleanup on disable
     */
    public void shutdown() {
        try {
            cleanupExecutor.shutdown();
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
            pendingActions.clear();
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cleanup expired actions periodically
     */
    private void cleanupExpiredActions() {
        for (var it = pendingActions.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().isExpired()) {
                it.remove();
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null && p.isOnline()) {
                    // Ensure GUI operations happen on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.sendMessage(Component.text("Action expired due to timeout.").color(NamedTextColor.YELLOW));
                        playErrorSound(p);
                        if (e.getValue().getType() == ActionType.FILTER) {
                            guiManager.openMyCellsGUI(p);
                        } else {
                            guiManager.openMainCellGUI(p);
                        }
                    });
                }
            }
        }
    }
    
    /**
     * Check if a player has a pending action
     */
    public boolean hasPendingAction(Player player) {
        return pendingActions.containsKey(player.getUniqueId());
    }
    
    /**
     * Get a player's pending action
     */
    public PendingAction getPendingAction(Player player) {
        return pendingActions.get(player.getUniqueId());
    }
}