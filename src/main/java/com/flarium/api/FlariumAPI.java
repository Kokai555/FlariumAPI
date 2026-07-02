package com.flarium.api;

import com.flarium.api.data.PDCManager;
import com.flarium.api.hologram.HologramListener;
import com.flarium.api.hologram.HologramManager;
import com.flarium.api.scheduler.Scheduler;
import org.bukkit.plugin.java.JavaPlugin;

public class FlariumAPI extends JavaPlugin {

    private static FlariumAPI instance;

    private Scheduler scheduler;
    private PDCManager pdcManager;
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        instance = this;

        this.scheduler = new Scheduler(this);
        this.pdcManager = new PDCManager(this);
        this.hologramManager = new HologramManager(this, scheduler, pdcManager);

        getServer().getPluginManager().registerEvents(new HologramListener(hologramManager, pdcManager, scheduler), this);

        getLogger().info("FlariumAPI enabled!");
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) hologramManager.shutdown();
        if (scheduler != null) scheduler.shutdown();
        getLogger().info("FlariumAPI disabled!");
    }

    public static FlariumAPI getInstance() { return instance; }

    public Scheduler getScheduler() { return scheduler; }
    public PDCManager getPdcManager() { return pdcManager; }
    public HologramManager getHologramManager() { return hologramManager; }
}