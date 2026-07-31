package com.flarium.api.nms.v1_21_10;

import com.flarium.api.nms.shared.SharedDisplayAdapter;
import org.bukkit.World;

public class DisplayAdapterImpl extends SharedDisplayAdapter {

    public DisplayAdapterImpl(World world, boolean text) {
        super(world, text);
    }
}
