package dev.lsdmc.edencells.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central constants repository for EdenCells plugin
 * All hardcoded values should be defined here
 */
public final class Constants {
    
    public static final class Permissions {
        // Admin permissions
        public static final String ADMIN = "edencells.admin";
        public static final String ADMIN_DOORS = "edencells.admin.doors";
        public static final String ADMIN_CELLS = "edencells.admin.cells";
        public static final String ADMIN_GROUPS = "edencells.admin.groups";
        public static final String ADMIN_CONFIG = "edencells.admin.config";
        public static final String ADMIN_SECURITY = "edencells.admin.security";
        
        // Basic permissions
        public static final String USE = "edencells.use";
        public static final String DOOR_LINK = "edencells.door.link";
        public static final String NPC_MANAGE = "edencells.npc.manage";
        public static final String BYPASS = "edencells.bypass";
        public static final String RELOAD = "edencells.reload";
        
        // Cell permissions
        public static final String CELL_INFO = "edencells.cell.info";
        public static final String CELL_LIST = "edencells.cell.list";
        public static final String CELL_ADD_MEMBER = "edencells.cell.addmember";
        public static final String CELL_REMOVE_MEMBER = "edencells.cell.removemember";
        public static final String CELL_AVAILABLE = "edencells.cell.available";
        public static final String CELL_GUI = "edencells.cell.gui";
        public static final String CELL_TELEPORT = "edencells.cell.teleport";
        public static final String CELL_PURCHASE = "edencells.cell.purchase";
        public static final String CELL_SELL = "edencells.cell.sell";
        
        // Group-specific permissions (dynamic)
        public static final String GROUP_PREFIX = "edencells.group.";
        
        // Donor permissions
        public static final String DONOR_PREFIX = "edencells.donor.";
        public static final String DONOR_ACCESS = "edencells.donor.access";
        
        // Bypass permissions
        public static final String BYPASS_LIMITS = "edencells.bypass.limits";
        public static final String BYPASS_COOLDOWN = "edencells.bypass.cooldown";
        public static final String BYPASS_PAYMENT = "edencells.bypass.payment";
        
        // Tips permissions (for smart tips)
        public static final String TIPS_DONOR = "edencells.tips.donor";
        public static final String TIPS_ADVANCED = "edencells.tips.advanced";
        
        // Rate limit bypass permissions
        public static final String BYPASS_RATE_LIMIT = "edencells.bypass.ratelimit";
        public static final String BYPASS_RATE_LIMIT_PURCHASE = "edencells.bypass.ratelimit.purchase";
        public static final String BYPASS_RATE_LIMIT_MEMBER = "edencells.bypass.ratelimit.member";
        public static final String BYPASS_RATE_LIMIT_DOOR = "edencells.bypass.ratelimit.door";
        public static final String BYPASS_RATE_LIMIT_GUI = "edencells.bypass.ratelimit.gui";
        public static final String BYPASS_RATE_LIMIT_NPC = "edencells.bypass.ratelimit.npc";
    }
    
    public static final class Messages {
        // Color scheme based on user preferences
        public static final String PRIMARY_COLOR = "<color:#9D4EDD>"; // Vibrant purple
        public static final String SECONDARY_COLOR = "<color:#06FFA5>"; // Bright cyan
        public static final String ACCENT_COLOR = "<color:#FFB3C6>"; // Soft pink
        public static final String ERROR_COLOR = "<color:#FF6B6B>"; // Coral red
        public static final String SUCCESS_COLOR = "<color:#51CF66>"; // Fresh green
        public static final String NEUTRAL_COLOR = "<color:#ADB5BD>"; // Light gray
        
        public static final String PREFIX = PRIMARY_COLOR + "[EdenCells]</color> " + SECONDARY_COLOR;
        
        // Common messages
        public static final String NO_PERMISSION = PREFIX + ERROR_COLOR + "You don't have permission to do that!";
        public static final String PLAYER_NOT_FOUND = PREFIX + ERROR_COLOR + "Player not found!";
        public static final String INVALID_CELL = PREFIX + ERROR_COLOR + "Invalid cell ID!";
        public static final String INSUFFICIENT_FUNDS = PREFIX + ERROR_COLOR + "Insufficient funds! You need %s";
    }
    
    public static final class Config {
        // General
        public static final String DEBUG = "general.debug";
        public static final String LOCALE = "general.locale";
        
        // Messages
        public static final String MESSAGE_PREFIX = "messages.prefix";
        public static final String COLOR_PRIMARY = "messages.colors.primary";
        public static final String COLOR_SECONDARY = "messages.colors.secondary";
        public static final String COLOR_ACCENT = "messages.colors.accent";
        public static final String COLOR_ERROR = "messages.colors.error";
        public static final String COLOR_SUCCESS = "messages.colors.success";
        public static final String COLOR_NEUTRAL = "messages.colors.neutral";
        
        // Cells
        public static final String MAX_CELLS_PER_PLAYER = "cells.max-per-player";
        public static final String DEFAULT_MAX_MEMBERS = "cells.default-max-members";
        public static final String CELL_SIGN_KEYWORDS = "cells.sign-keywords";
        
        // Cell GUI
        public static final String GUI_ITEMS_PER_PAGE = "cells.gui.items-per-page";
        public static final String GUI_DEFAULT_SORT = "cells.gui.default-sort";
        public static final String GUI_SHOW_PRICES = "cells.gui.show-prices";
        public static final String GUI_SHOW_EXPIRATION = "cells.gui.show-expiration";
        public static final String GUI_REQUIRE_CONFIRMATION = "cells.gui.require-confirmation";
        public static final String GUI_CLICK_SOUNDS = "cells.gui.click-sounds";
        
        // Economy
        public static final String CURRENCY_SYMBOL = "economy.currency-symbol";
        public static final String CURRENCY_SUFFIX = "economy.currency-suffix";
        public static final String MEMBER_ADD_COST = "economy.members.add-cost";
        public static final String MEMBER_REMOVE_COST = "economy.members.remove-cost";
        public static final String SELL_FEE_PERCENT = "economy.fees.sell-fee-percent";
        public static final String LISTING_FEE = "economy.fees.listing-fee";
        
        // Teleportation
        public static final String TELEPORT_BASE_COST = "teleportation.base-cost";
        public static final String TELEPORT_REQUIRE_PAYMENT = "teleportation.require-payment";
        public static final String TELEPORT_COOLDOWN = "teleportation.cooldown";
        public static final String TELEPORT_FREE_GROUPS = "teleportation.free-groups";
        public static final String TELEPORT_PLAY_SOUND = "teleportation.effects.play-sound";
        public static final String TELEPORT_SOUND = "teleportation.effects.sound";
        public static final String TELEPORT_SHOW_PARTICLES = "teleportation.effects.show-particles";
        public static final String TELEPORT_PARTICLE = "teleportation.effects.particle";
        
        // Doors
        public static final String DOOR_VALID_MATERIALS = "doors.valid-materials";
        public static final String DOOR_SOUNDS_ENABLED = "doors.sounds.enabled";
        public static final String DOOR_OPEN_SOUND = "doors.sounds.open-sound";
        public static final String DOOR_CLOSE_SOUND = "doors.sounds.close-sound";
        public static final String DOOR_SOUND_VOLUME = "doors.sounds.volume";
        public static final String DOOR_SOUND_PITCH = "doors.sounds.pitch";
        public static final String DOOR_AUTO_CLOSE = "doors.auto-close-delay";
        
        // Security
        public static final String RATE_LIMIT_PREFIX = "security.rate-limits.";
        public static final String AUDIT_ENABLED = "security.audit.enabled";
        public static final String AUDIT_LOG_FILE = "security.audit.log-file";
        public static final String AUDIT_LOG_ACTIONS = "security.audit.log-actions";
        
        // Integrations
        public static final String ARM_USE_ECONOMY = "integrations.arm.use-arm-economy";
        public static final String ARM_SYNC_DATA = "integrations.arm.sync-data";
        public static final String ARM_CHECK_PERMISSIONS = "integrations.arm.check-permissions";
        public static final String CITIZENS_ENABLED = "integrations.citizens.enabled";
        public static final String CITIZENS_NPC_FORMAT = "integrations.citizens.npc-name-format";
        public static final String WORLDGUARD_CHECK_REGIONS = "integrations.worldguard.check-regions";
        
        // Performance
        public static final String CACHE_PLAYER_TIME = "performance.cache.player-cache-time";
        public static final String CACHE_REGION_TIME = "performance.cache.region-cache-time";
        public static final String CACHE_MAX_SIZE = "performance.cache.max-cache-size";
        public static final String ASYNC_SAVES = "performance.async.async-saves";
        public static final String ASYNC_ECONOMY = "performance.async.async-economy";
        
        // Maintenance
        public static final String AUTO_SAVE_INTERVAL = "maintenance.auto-save-interval";
        public static final String BACKUP_ENABLED = "maintenance.backup.enabled";
        public static final String BACKUP_INTERVAL = "maintenance.backup.interval";
        public static final String CLEANUP_EXPIRED_RENTALS = "maintenance.cleanup.remove-expired-rentals";
    }
    
    public static final class Keys {
        // Will be initialized by plugin instance
        private static Plugin plugin;
        
        public static void init(Plugin pluginInstance) {
            plugin = pluginInstance;
        }
        
        public static NamespacedKey DOOR_LINK() {
            return new NamespacedKey(plugin, "door_link");
        }
        
        public static NamespacedKey CELL_DATA() {
            return new NamespacedKey(plugin, "cell_data");
        }
        
        public static NamespacedKey NPC_DATA() {
            return new NamespacedKey(plugin, "npc_data");
        }
        
        public static NamespacedKey TELEPORT_COOLDOWN() {
            return new NamespacedKey(plugin, "teleport_cooldown");
        }
    }
    
    public static final class RateLimits {
        public static final int PURCHASE_PER_MINUTE = 3;
        public static final int MEMBER_ADD_PER_MINUTE = 5;
        public static final int DOOR_INTERACT_PER_MINUTE = 20;
        public static final int GUI_OPEN_PER_MINUTE = 10;
        public static final int NPC_TELEPORT_COOLDOWN_SECONDS = 5;
        public static final int BULK_ADD_PER_MINUTE = 2; // Limit bulk operations to prevent abuse
    }
    
    public static final class Validation {
        public static final String REGION_ID_PATTERN = "^[a-zA-Z0-9_-]+$";
        public static final int MAX_REGION_ID_LENGTH = 32;
        public static final int MAX_USERNAME_LENGTH = 16;
        public static final double MAX_ECONOMY_AMOUNT = 1000000.0;
    }
    
    public static final class GUI {
        public static final String VACANT_CELL_TITLE = Messages.PRIMARY_COLOR + "Available Cell: " + Messages.ACCENT_COLOR + "%s";
        public static final String OCCUPIED_CELL_TITLE = Messages.PRIMARY_COLOR + "Cell " + Messages.ACCENT_COLOR + "%s " + Messages.NEUTRAL_COLOR + "(Occupied)";
        public static final String CELL_MANAGEMENT_TITLE = Messages.PRIMARY_COLOR + "Cell Management";
        public static final String CELL_SELECTION_TITLE = Messages.PRIMARY_COLOR + "Select a Cell";
        
        public static final int INVENTORY_SIZE = 54; // Standard double chest
        public static final int INFO_SLOT = 13; // Center of 3rd row
        public static final int ACTION_SLOT = 31; // Center of 4th row
        public static final int CLOSE_SLOT = 49; // Bottom center
    }
    
    public static final class Sounds {
        public static final String DOOR_OPEN = "minecraft:block.iron_door.open";
        public static final String DOOR_CLOSE = "minecraft:block.iron_door.close";
        public static final String ACCESS_DENIED = "minecraft:block.note_block.bass";
        public static final String SUCCESS = "minecraft:entity.experience_orb.pickup";
        public static final String ERROR = "minecraft:entity.villager.no";
        public static final String TELEPORT = "minecraft:entity.enderman.teleport";
    }
    
    public static final class Storage {
        public static final String DOORS_FILE = "doors.yml";
        public static final String NPCS_FILE = "npcs.yml";
        public static final String AUDIT_LOG_FILE = "logs/audit.log";
    }
} 