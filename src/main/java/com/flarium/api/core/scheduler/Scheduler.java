package com.flarium.api.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

public class Scheduler {

    private final Plugin plugin;
    private final Executor asyncExecutor;
    private final Executor globalExecutor;
    private final ConcurrentLinkedQueue<Task> pendingTimers = new ConcurrentLinkedQueue<>();
    private final Set<CompletableFuture<?>> pendingFutures = ConcurrentHashMap.newKeySet();
    private volatile boolean shutdown;

    public Scheduler(Plugin plugin) {
        this.plugin = plugin;
        this.asyncExecutor = command -> Bukkit.getAsyncScheduler().runNow(plugin, t -> command.run());
        this.globalExecutor = command -> Bukkit.getGlobalRegionScheduler().run(plugin, t -> command.run());
    }

    public CompletableFuture<Void> runAsync(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        CompletableFuture<Void> future = new CompletableFuture<>();
        track(future);
        try {
            asyncExecutor.execute(() -> runGuarded(future, runnable));
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        CompletableFuture<T> future = new CompletableFuture<>();
        track(future);
        try {
            asyncExecutor.execute(() -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<Void> runGlobal(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        CompletableFuture<Void> future = new CompletableFuture<>();
        track(future);
        try {
            globalExecutor.execute(() -> runGuarded(future, runnable));
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<Void> runForEntity(Entity entity, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        CompletableFuture<Void> future = new CompletableFuture<>();
        track(future);
        try {
            entity.getScheduler().run(plugin,
                    task -> runGuarded(future, runnable),
                    () -> future.completeExceptionally(
                            new IllegalStateException("Entity retired before task execution")));
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<Void> runAtLocation(Location location, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        CompletableFuture<Void> future = new CompletableFuture<>();
        track(future);
        try {
            atLocation(location).execute(() -> runGuarded(future, runnable));
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public Task runAsyncDelayed(Runnable runnable, Duration delay) {
        return wrapOneShot(runnable, action -> Bukkit.getAsyncScheduler().runDelayed(plugin, t -> action.run(), delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    public Task runGlobalDelayed(Runnable runnable, Duration delay) {
        return wrapOneShot(runnable, action -> Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> action.run(), toTicks(delay)));
    }

    public Task runForEntityDelayed(Entity entity, Runnable runnable, Duration delay) {
        return wrapOneShot(runnable, action -> entity.getScheduler().runDelayed(plugin, t -> action.run(), null, toTicks(delay)));
    }

    public Task runAtLocationDelayed(Location location, Runnable runnable, Duration delay) {
        return wrapOneShot(runnable, action -> Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> action.run(), toTicks(delay)));
    }

    public Task runAsyncTimer(Runnable runnable, Duration delay, Duration period) {
        return wrapTimer(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> runnable.run(), delay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS));
    }

    public Task runGlobalTimer(Runnable runnable, Duration delay, Duration period) {
        return wrapTimer(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> runnable.run(), toTicks(delay), toTicks(period)));
    }

    public Task runForEntityTimer(Entity entity, Runnable runnable, Duration delay, Duration period) {
        return wrapTimer(entity.getScheduler().runAtFixedRate(plugin, t -> runnable.run(), null, toTicks(delay), toTicks(period)));
    }

    public Task runAtLocationTimer(Location location, Runnable runnable, Duration delay, Duration period) {
        return wrapTimer(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, t -> runnable.run(), toTicks(delay), toTicks(period)));
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
        WrappedTask wrapped = new WrappedTask();
        wrapped.inner = task;
        pendingTimers.add(wrapped);
        return wrapped;
    }

    private Task wrapOneShot(Runnable runnable, Function<Runnable, ScheduledTask> schedule) {
        WrappedTask wrapped = new WrappedTask();
        wrapped.inner = schedule.apply(() -> {
            try {
                runnable.run();
            } finally {
                pendingTimers.remove(wrapped);
            }
        });
        pendingTimers.add(wrapped);
        return wrapped;
    }

    /**
     * C3/C4: deterministic terminal path for every returned future. The future
     * is tracked until it completes; shutdown drains the tracked set, and a
     * post-shutdown submission fails immediately instead of pending silently.
     */
    private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
        pendingFutures.add(future);
        future.whenComplete((result, error) -> pendingFutures.remove(future));
        if (shutdown) {
            future.completeExceptionally(new IllegalStateException("Scheduler is shut down"));
        }
        return future;
    }

    private static void runGuarded(CompletableFuture<Void> future, Runnable runnable) {
        try {
            runnable.run();
            future.complete(null);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }

    private final class WrappedTask implements Task {

        private volatile ScheduledTask inner;

        @Override
        public void cancel() {
            ScheduledTask task = inner;
            if (task != null) task.cancel();
            pendingTimers.remove(this);
        }
    }

    public void shutdown() {
        shutdown = true;
        for (Task task : pendingTimers) {
            task.cancel();
        }
        pendingTimers.clear();
        for (CompletableFuture<?> future : pendingFutures) {
            future.completeExceptionally(new IllegalStateException("Scheduler is shut down"));
        }
        pendingFutures.clear();
    }
}