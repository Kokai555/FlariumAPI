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
        if (primitive.isEmpty()) return null;
        String[] parts = primitive.split(",");
        World world = Bukkit.getWorld(UUID.fromString(parts[0]));
        return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
    }
}