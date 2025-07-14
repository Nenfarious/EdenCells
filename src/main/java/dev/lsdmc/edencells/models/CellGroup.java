package dev.lsdmc.edencells.models;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * Represents a group of cells with configurable limits and options
 * Optimized for security and performance with immutable design
 */
public final class CellGroup {
    
    // Validation patterns
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_DISPLAY_NAME_LENGTH = 64;
    private static final int MAX_REGIONS = 10000; // Prevent memory exhaustion
    
    // Immutable core fields
    private final String name;
    private final String displayName;
    
    // Thread-safe collections
    private final Set<String> regions = Collections.synchronizedSet(new LinkedHashSet<>());
    private final ConcurrentMap<String, Object> options = new ConcurrentHashMap<>();
    
    // Cached computed values for performance
    private volatile int cachedSize = -1;
    private volatile long lastModified = System.currentTimeMillis();
    
    // Default values
    private static final int DEFAULT_CELL_LIMIT = -1; // No limit
    private static final double DEFAULT_TELEPORT_COST = -1; // Use global default
    
    /**
     * Create a new cell group with validation
     * @param name The group identifier
     * @throws IllegalArgumentException if name is invalid
     */
    public CellGroup(String name) {
        this(name, name);
    }
    
    /**
     * Create a new cell group with display name
     * @param name The group identifier
     * @param displayName The display name
     * @throws IllegalArgumentException if parameters are invalid
     */
    public CellGroup(String name, String displayName) {
        this.name = validateName(name);
        this.displayName = validateDisplayName(displayName);
    }
    
    /**
     * Create cell group from configuration data (package-private for manager use)
     * @param name The group identifier
     * @param displayName The display name
     * @param regions Initial regions
     * @param options Initial options
     */
    CellGroup(String name, String displayName, Set<String> regions, ConcurrentMap<String, Object> options) {
        this.name = validateName(name);
        this.displayName = validateDisplayName(displayName);
        
        // Safely copy regions with validation
        if (regions != null) {
            for (String region : regions) {
                if (validateRegionId(region)) {
                    this.regions.add(region);
                }
            }
        }
        
        // Safely copy options
        if (options != null) {
            this.options.putAll(options);
        }
        
        invalidateCache();
    }
    
    /**
     * Validate group name
     * @param name The name to validate
     * @return The validated name
     * @throws IllegalArgumentException if invalid
     */
    private static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Group name cannot be null or empty");
        }
        
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Group name too long (max " + MAX_NAME_LENGTH + " characters)");
        }
        
        if (!VALID_NAME_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Group name contains invalid characters. Use only letters, numbers, underscores, and hyphens.");
        }
        
        return trimmed;
    }
    
    /**
     * Validate display name
     * @param displayName The display name to validate
     * @return The validated display name
     * @throws IllegalArgumentException if invalid
     */
    private static String validateDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("Display name cannot be null or empty");
        }
        
        String trimmed = displayName.trim();
        if (trimmed.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("Display name too long (max " + MAX_DISPLAY_NAME_LENGTH + " characters)");
        }
        
        return trimmed;
    }
    
    /**
     * Validate region ID
     * @param regionId The region ID to validate
     * @return true if valid
     */
    private static boolean validateRegionId(String regionId) {
        return regionId != null && 
               !regionId.trim().isEmpty() && 
               regionId.trim().length() <= MAX_NAME_LENGTH &&
               VALID_NAME_PATTERN.matcher(regionId.trim()).matches();
    }
    
    /**
     * Invalidate cached values
     */
    private void invalidateCache() {
        cachedSize = -1;
        lastModified = System.currentTimeMillis();
    }
    
    /**
     * Get the group name (identifier) - immutable
     * @return The name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Get the display name - immutable
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get regions in this group (defensive copy)
     * @return Immutable set of region IDs
     */
    public Set<String> getRegions() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(regions));
    }
    
    /**
     * Add a region to this group with validation
     * @param regionId The region ID
     * @return true if added, false if already exists or invalid
     * @throws IllegalStateException if too many regions
     */
    public boolean addRegion(String regionId) {
        if (!validateRegionId(regionId)) {
            return false;
        }
        
        String trimmed = regionId.trim();
        
        // Check size limit to prevent memory exhaustion
        if (regions.size() >= MAX_REGIONS) {
            throw new IllegalStateException("Too many regions in group (max " + MAX_REGIONS + ")");
        }
        
        boolean added = regions.add(trimmed);
        if (added) {
            invalidateCache();
        }
        return added;
    }
    
    /**
     * Remove a region from this group
     * @param regionId The region ID
     * @return true if removed
     */
    public boolean removeRegion(String regionId) {
        if (regionId == null) {
            return false;
        }
        
        boolean removed = regions.remove(regionId.trim());
        if (removed) {
            invalidateCache();
        }
        return removed;
    }
    
    /**
     * Check if this group contains a region
     * @param regionId The region ID
     * @return true if contains
     */
    public boolean containsRegion(String regionId) {
        return regionId != null && regions.contains(regionId.trim());
    }
    
    /**
     * Get the number of regions in this group (cached for performance)
     * @return Region count
     */
    public int size() {
        if (cachedSize == -1) {
            synchronized (regions) {
                if (cachedSize == -1) { // Double-check locking
                    cachedSize = regions.size();
                }
            }
        }
        return cachedSize;
    }
    
    /**
     * Get the cell limit for this group
     * @return Cell limit (-1 = no limit)
     */
    public int getCellLimit() {
        Object limit = options.get("cellLimit");
        if (limit instanceof Number) {
            return ((Number) limit).intValue();
        }
        return DEFAULT_CELL_LIMIT;
    }
    
    /**
     * Set the cell limit for this group
     * @param limit The limit (-1 = no limit)
     * @throws IllegalArgumentException if limit is invalid
     */
    public void setCellLimit(int limit) {
        if (limit < -1) {
            throw new IllegalArgumentException("Cell limit cannot be less than -1");
        }
        
        options.put("cellLimit", limit);
        lastModified = System.currentTimeMillis();
    }
    
    /**
     * Get the teleport cost for this group
     * @return Teleport cost (-1 = use global default)
     */
    public double getTeleportCost() {
        Object cost = options.get("teleportCost");
        if (cost instanceof Number) {
            return ((Number) cost).doubleValue();
        }
        return DEFAULT_TELEPORT_COST;
    }
    
    /**
     * Set the teleport cost for this group
     * @param cost The cost (-1 = use global default)
     * @throws IllegalArgumentException if cost is invalid
     */
    public void setTeleportCost(double cost) {
        if (cost < -1 || Double.isNaN(cost) || Double.isInfinite(cost)) {
            throw new IllegalArgumentException("Invalid teleport cost");
        }
        
        options.put("teleportCost", cost);
        lastModified = System.currentTimeMillis();
    }
    
    /**
     * Check if this is a donor group
     * @return true if donor group
     */
    public boolean isDonorGroup() {
        Object donor = options.get("isDonor");
        return donor instanceof Boolean && (Boolean) donor;
    }
    
    /**
     * Set whether this is a donor group
     * @param isDonor true if donor group
     */
    public void setDonorGroup(boolean isDonor) {
        options.put("isDonor", isDonor);
        lastModified = System.currentTimeMillis();
    }
    
    /**
     * Get the required permission for this group
     * @return Permission string or null
     */
    public String getRequiredPermission() {
        Object perm = options.get("permission");
        return perm instanceof String ? (String) perm : null;
    }
    
    /**
     * Set the required permission for this group
     * @param permission The permission string (null to remove)
     */
    public void setRequiredPermission(String permission) {
        if (permission == null || permission.trim().isEmpty()) {
            options.remove("permission");
        } else {
            // Basic permission validation
            String trimmed = permission.trim();
            if (trimmed.length() > 128) {
                throw new IllegalArgumentException("Permission string too long");
            }
            options.put("permission", trimmed);
        }
        lastModified = System.currentTimeMillis();
    }
    
    /**
     * Get all options (defensive copy)
     * @return Immutable map of options
     */
    public ConcurrentMap<String, Object> getOptions() {
        return new ConcurrentHashMap<>(options);
    }
    
    /**
     * Get a specific option
     * @param key The option key
     * @return The option value or null
     */
    public Object getOption(String key) {
        return key != null ? options.get(key) : null;
    }
    
    /**
     * Set a specific option with validation
     * @param key The option key
     * @param value The option value
     * @throws IllegalArgumentException if key/value is invalid
     */
    public void setOption(String key, Object value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Option key cannot be null or empty");
        }
        
        String trimmedKey = key.trim();
        if (trimmedKey.length() > 64) {
            throw new IllegalArgumentException("Option key too long");
        }
        
        if (value == null) {
            options.remove(trimmedKey);
        } else {
            options.put(trimmedKey, value);
        }
        lastModified = System.currentTimeMillis();
    }
    
    /**
     * Get when this group was last modified
     * @return Timestamp in milliseconds
     */
    public long getLastModified() {
        return lastModified;
    }
    
    /**
     * Check if this group is empty (no regions)
     * @return true if empty
     */
    public boolean isEmpty() {
        return regions.isEmpty();
    }
    
    /**
     * Clear all regions (for admin operations)
     */
    public void clearRegions() {
        regions.clear();
        invalidateCache();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CellGroup)) return false;
        
        CellGroup other = (CellGroup) obj;
        return Objects.equals(name, other.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
    
    @Override
    public String toString() {
        return String.format("CellGroup{name='%s', displayName='%s', regions=%d, options=%d}", 
            name, displayName, size(), options.size());
    }
}

/* Location:              C:\Users\purpt\OneDrive\Desktop\EdenCells-1.0-SNAPSHOT (1).jar!\dev\lsdmc\edencells\models\CellGroup.class
 * Java compiler version: 21 (65.0)
 * JD-Core Version:       1.1.3
 */
