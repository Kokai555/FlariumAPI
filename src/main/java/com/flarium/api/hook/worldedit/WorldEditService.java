package com.flarium.api.hook.worldedit;

import com.flarium.api.scheduler.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.concurrent.CompletableFuture;

public class WorldEditService {

    private final boolean enabled;
    private final InternalWEHook hook;

    public WorldEditService(Plugin plugin, Scheduler scheduler) {
        boolean we = Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
        boolean fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
        this.enabled = we || fawe;

        if (enabled) {
            this.hook = new WEHookHandler(plugin, scheduler);
            plugin.getLogger().info("WorldEdit/FAWE hook enabled.");
        } else {
            this.hook = null;
            plugin.getLogger().warning("WorldEdit/FAWE not found, schematics will not be pasted.");
        }
    }

    public CompletableFuture<Void> pasteSchematic(File file, Location location, boolean ignoreAir) {
        if (!enabled) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("WorldEdit/FAWE not installed"));
            return future;
        }
        return hook.pasteSchematic(file, location, ignoreAir);
    }

    // Belső interfész a ClassLoader izolációhoz
    private interface InternalWEHook {
        CompletableFuture<Void> pasteSchematic(File file, Location location, boolean ignoreAir);
    }

    // Belso Handler osztály. Ez tartalmazza az összes WE/FAWE importot.
    // Ha a WE nincs fent, a JVM ezt az osztályt sosem tölti be, így nem kapunk NoClassDefFoundError-t.
    private static class WEHookHandler implements InternalWEHook {
        private final Plugin plugin;
        private final Scheduler scheduler;

        public WEHookHandler(Plugin plugin, Scheduler scheduler) {
            this.plugin = plugin;
            this.scheduler = scheduler;
        }

        @Override
        public CompletableFuture<Void> pasteSchematic(File file, Location location, boolean ignoreAir) {
            CompletableFuture<Void> future = new CompletableFuture<>();

            scheduler.runAsync(() -> {
                if (!file.exists()) {
                    future.completeExceptionally(new java.io.IOException("Schematic file not found: " + file.getName()));
                    return;
                }

                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat format = com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(file);
                    if (format == null) {
                        future.completeExceptionally(new IllegalArgumentException("Unknown schematic format!"));
                        return;
                    }

                    com.sk89q.worldedit.world.World weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(location.getWorld());
                    com.sk89q.worldedit.math.BlockVector3 origin = com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(location);

                    try (com.sk89q.worldedit.extent.clipboard.io.ClipboardReader reader = format.getReader(fis)) {
                        com.sk89q.worldedit.extent.clipboard.Clipboard clipboard = reader.read();

                        try (com.sk89q.worldedit.EditSession session = com.sk89q.worldedit.WorldEdit.getInstance().newEditSession(weWorld)) {
                            com.sk89q.worldedit.function.operation.Operations.complete(
                                    new com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                                            .createPaste(session)
                                            .to(origin)
                                            .ignoreAirBlocks(ignoreAir) // JAVÍTOTT: ignoreAirBlocks
                                            .build()
                            );
                        }
                    }

                    future.complete(null);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to paste schematic: " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });

            return future;
        }
    }
}