package dev.lsdmc.models;

import org.bukkit.Location;
import java.util.UUID;

public class Region {
    private final String id;
    private final String name;
    private final Location location;
    private final UUID ownerId;
    private final double price;
    private final boolean isRented;

    public Region(String id, String name, Location location, UUID ownerId, double price, boolean isRented) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.ownerId = ownerId;
        this.price = price;
        this.isRented = isRented;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public double getPrice() {
        return price;
    }

    public boolean isRented() {
        return isRented;
    }
} 