package com.flarium.api.data.pdc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class LocationDataType implements PersistentDataType<String, Location> {

    @Override
    public Class<String> getPrimitiveType() {
        return String.class;
    }

    @Override
    public Class<Location> getComplexType() {
        return Location.class;
    }

    @Override
    public String toPrimitive(Location complex, PersistentDataAdapterContext context) {
        if (complex.getWorld() == null) return "";
        return complex.getWorld().getUID() + "," + complex.getX() + "," + complex.getY() + "," + complex.getZ() + "," + complex.getYaw() + "," + complex.getPitch();
    }

    @Override
    public Location fromPrimitive(String primitive, PersistentDataAdapterContext context) {
        if (primitive == null || primitive.isEmpty()) return null;
        String[] parts = primitive.split(",");
        if (parts.length != 6) return null;
        UUID worldId;
        double x, y, z;
        float yaw, pitch;
        try {
            worldId = UUID.fromString(parts[0]);
            x = Double.parseDouble(parts[1]);
            y = Double.parseDouble(parts[2]);
            z = Double.parseDouble(parts[3]);
            yaw = Float.parseFloat(parts[4]);
            pitch = Float.parseFloat(parts[5]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        World world = Bukkit.getWorld(worldId);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }
}