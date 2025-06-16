package dev.lsdmc.models;

import net.alex9849.arm.regions.Region;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Represents a logical grouping of cell regions.
 * Groups can be used to organize cells, apply group-wide settings,
 * and restrict certain operations to specific groups.
 */
public class CellGroup implements ConfigurationSerializable {
    private final String id;
    private String displayName;
    private final Set<String> regionIds;
    private final Map<String, Object> metadata;

    public CellGroup(String id) {
        this(id, id, new HashSet<>(), new HashMap<>());
    }

    public CellGroup(String id, String displayName, Set<String> regionIds, Map<String, Object> metadata) {
        this.id = id;
        this.displayName = displayName;
        this.regionIds = new HashSet<>(regionIds);
        this.metadata = new HashMap<>(metadata);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Set<String> getRegionIds() {
        return Collections.unmodifiableSet(regionIds);
    }

    public boolean addRegion(String regionId) {
        return regionIds.add(regionId);
    }

    public boolean removeRegion(String regionId) {
        return regionIds.remove(regionId);
    }

    public boolean containsRegion(String regionId) {
        return regionIds.contains(regionId);
    }

    public int getRegionCount() {
        return regionIds.size();
    }

    public void setMetadata(String key, Object value) {
        if (value == null) {
            metadata.remove(key);
        } else {
            metadata.put(key, value);
        }
    }

    public Object getMetadata(String key) {
        return metadata.get(key);
    }

    public Object getMetadata(String key, Object defaultValue) {
        return metadata.getOrDefault(key, defaultValue);
    }

    public Map<String, Object> getAllMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Checks if a location is within any region of this group
     */
    public boolean containsLocation(Location location, List<Region> regions) {
        if (location == null || regions == null) return false;
        
        for (Region region : regions) {
            if (regionIds.contains(region.getRegion().getId()) && 
                region.getRegion().contains(location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a player is within any region of this group
     */
    public boolean containsPlayer(Player player, List<Region> regions) {
        return player != null && containsLocation(player.getLocation(), regions);
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("displayName", displayName);
        data.put("regions", new ArrayList<>(regionIds));
        data.put("metadata", metadata);
        return data;
    }

    public static CellGroup deserialize(Map<String, Object> data) {
        String id = (String) data.get("id");
        String displayName = (String) data.get("displayName");
        @SuppressWarnings("unchecked")
        List<String> regions = (List<String>) data.get("regions");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");

        return new CellGroup(
            id,
            displayName,
            new HashSet<>(regions != null ? regions : Collections.emptyList()),
            metadata != null ? metadata : new HashMap<>()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CellGroup)) return false;
        CellGroup other = (CellGroup) o;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "CellGroup{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", regions=" + regionIds.size() +
                '}';
    }
} 