package com.flarium.api.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class Scheduler {

    private final Plugin plugin;
    private final Executor asyncExecutor;
    private final Executor globalExecutor;
    private final ConcurrentLinkedQueue<Task> pendingTimers = new ConcurrentLinkedQueue<>();

    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
        this.asyncExecutor = command -> Bukkit.getAsyncScheduler().runNow(plugin, t -> command.run());
        this.globalExecutor = command -> Bukkit.getGlobalRegionScheduler().run(plugin, t -> command.run());
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, asyncExecutor);
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, asyncExecutor);
    }

    public CompletableFuture<Void> runGlobal(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, globalExecutor);
    }

    public CompletableFuture<Void> runForEntity(Entity entity, Runnable runnable) {
        return CompletableFuture.runAsync(runnable, forEntity(entity));
    }

    public CompletableFuture<Void> runAtLocation(Location location, Runnable runnable) {
        return CompletableFuture.runAsync(runnable, atLocation(location));
    }

    public Task runAsyncDelayed(Runnable runnable, Duration delay) {
        ScheduledTask task = Bukkit.getAsyncScheduler().runDelayed(plugin, t -> runnable.run(), delay.toMillis(), TimeUnit.MILLISECONDS);
        return () -> task.cancel();
    }

    public Task runGlobalDelayed(Runnable runnable, Duration delay) {
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> runnable.run(), toTicks(delay));
        return () -> task.cancel();
    }

    public Task runForEntityDelayed(Entity entity, Runnable runnable, Duration delay) {
        ScheduledTask task = entity.getScheduler().runDelayed(plugin, t -> runnable.run(), null, toTicks(delay));
        return () -> task.cancel();
    }

    public Task runAtLocationDelayed(Location location, Runnable runnable, Duration delay) {
        ScheduledTask task = Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> runnable.run(), toTicks(delay));
        return () -> task.cancel();
    }

    public Task runAsyncTimer(Runnable runnable, Duration delay, Duration period) {
        ScheduledTask task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> runnable.run(), delay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
        return wrapTimer(task);
    }

    public Task runGlobalTimer(Runnable runnable, Duration delay, Duration period) {
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> runnable.run(), toTicks(delay), toTicks(period));
        return wrapTimer(task);
    }

    public Task runForEntityTimer(Entity entity, Runnable runnable, Duration delay, Duration period) {
        ScheduledTask task = entity.getScheduler().runAtFixedRate(plugin, t -> runnable.run(), null, toTicks(delay), toTicks(period));
        return wrapTimer(task);
    }

    public Task runAtLocationTimer(Location location, Runnable runnable, Duration delay, Duration period) {
        ScheduledTask task = Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, t -> runnable.run(), toTicks(delay), toTicks(period));
        return wrapTimer(task);
    }

    public Executor async() {
        return asyncExecutor;
    }

    public Executor global() {
        return globalExecutor;
    }

    public Executor forEntity(Entity entity) {
        return command -> entity.getScheduler().run(plugin, t -> command.run(), null);
    }

    public Executor atLocation(Location location) {
        return command -> Bukkit.getRegionScheduler().run(plugin, location, t -> command.run());
    }

    private long toTicks(Duration duration) {
        return Math.max(1L, duration.toMillis() / 50L);
    }

    private Task wrapTimer(ScheduledTask task) {
        Task wrapped = new Task() {
            @Override
            public void cancel() {
                task.cancel();
                pendingTimers.remove(this);
            }
        };
        pendingTimers.add(wrapped);
        return wrapped;
    }

    public void shutdown() {
        for (Task task : pendingTimers) {
            task.cancel();
        }
        pendingTimers.clear();
    }
}