package com.flarium.api;

import com.flarium.api.core.scheduler.Scheduler;
import com.flarium.api.data.pdc.PDCManager;
import com.flarium.api.ui.hologram.HologramManager;
import com.flarium.api.ui.hologram.impl.FlariumHologramManager;
import com.flarium.api.ui.hologram.impl.HologramListener;
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
        // Interfész deklarálva, impl példányosítva
        this.hologramManager = new FlariumHologramManager(this, scheduler, pdcManager);

        getServer().getPluginManager().registerEvents(new HologramListener(hologramManager, pdcManager, scheduler), this);

        getLogger().info("FlariumAPI enabled!");
    }

    @Override
    public void onDisable() {
        for (com.flarium.api.ui.menu.window.AbstractWindow window : com.flarium.api.ui.menu.window.AbstractWindow.ACTIVE_WINDOWS) {
            window.close();
        }

        if (hologramManager != null) hologramManager.shutdown();
        if (scheduler != null) scheduler.shutdown();
        getLogger().info("FlariumAPI disabled!");
    }

    public static FlariumAPI getInstance() { return instance; }

    public Scheduler getScheduler() { return scheduler; }
    public PDCManager getPdcManager() { return pdcManager; }
    public HologramManager getHologramManager() { return hologramManager; }
}