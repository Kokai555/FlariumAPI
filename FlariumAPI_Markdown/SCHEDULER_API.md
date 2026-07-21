# FlariumAPI — Scheduler API

> **Package:** `com.flarium.api.core.scheduler`
> **Core Classes:** `Scheduler`, `Task`

A `Scheduler` a Paper 1.21 / Folia natív Region és Entity Scheduler API-jaira épül. Minden időmegadás `java.time.Duration` objektum kell, hogy legyen — tilos a nyers "ticks" (pl. `20L`) használata.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, késleltetett, ismétlődő vagy aszinkron feladatokra **KIZÁRÓLAG** az itt leírt módszereket használhatja.

| Tilos | Helyette |
|-------|----------|
| `Bukkit.getScheduler().runTask(...)` | `scheduler.runForEntity(player, ...)` |
| `BukkitRunnable` | `scheduler.runAsyncTimer(...)` |
| `20L` (magic number ticks) | `Duration.ofSeconds(1)` |

---

## 1. Architekturális Szabályok

1. **Nincs Bukkit Scheduler** — Tilos a `Bukkit.getScheduler()` és a `BukkitRunnable`. Folia alatt ezek azonnal összeomlasztják a szervert!
2. **Folia Szálbiztos Visszatérés** — Aszinkron művelet után a Bukkit API hívásához kötelező visszatérni a megfelelő szálra a beépített `Executor`-okkal.
3. **Duration API** — Minden időmegadáshoz kötelező a `java.time.Duration` osztály használata.
4. **Task Interfész** — Minden timer és delayed feladat egy `Task` objektumot ad vissza, amivel a `.cancel()` metóduson keresztül le lehet állítani.
5. **Memóriakezelés** — A `scheduler.shutdown()` leálláskor kizárólag az ismétlődő Timer-eket állítja le.

---

## 2. CompletableFuture Láncolás (Executors)

### `supplyAsync(Supplier<T> supplier)`

```java
public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier)
```

Aszinkron adatbetöltés a háttérben.

### `runAsync(Runnable runnable)`

```java
public CompletableFuture<Void> runAsync(Runnable runnable)
```

Aszinkron fire-and-forget feladat.

### `runGlobal(Runnable runnable)`

```java
public CompletableFuture<Void> runGlobal(Runnable runnable)
```

Futtatja a feladatot a globális régió szálon (konzol parancsok, broadcast).

### `runForEntity(Entity entity, Runnable runnable)`

```java
public CompletableFuture<Void> runForEntity(Entity entity, Runnable runnable)
```

Futtatja a feladatot az entitás saját szálán (játékos módosítása, GUI nyitás, HP, üzenet).

### `runAtLocation(Location location, Runnable runnable)`

```java
public CompletableFuture<Void> runAtLocation(Location location, Runnable runnable)
```

Futtatja a feladatot az adott lokáció régió szálán (blokkok, világ manipuláció).

---

## 3. Executor-ok (Future láncoláshoz)

Ezeket a `CompletableFuture.thenAcceptAsync()` második paramétereként kell átadni:

| Metódus | Szál | Használat |
|---------|------|----------|
| `async()` | Aszinkron szál | Nehéz számítások |
| `global()` | Globális régió szál | Konzol parancsok, broadcast |
| `forEntity(Entity)` | Entitás szál | Player módosítás, GUI, üzenet |
| `atLocation(Location)` | Régió szál | Blokk manipuláció |

**Példa (Entitás szál):**
```java
scheduler.supplyAsync(() -> database.loadStats(uuid))
    .thenAcceptAsync(stats -> {
        // [!] Ez a blokk a játékos saját szálán (Entity Thread) fut!
        player.setHealth(stats.getHealth());
        player.sendMessage("Statisztika betöltve!");
    }, scheduler.forEntity(player));
```

**Példa (Régió szál):**
```java
scheduler.supplyAsync(() -> database.calculateBlockType())
    .thenAcceptAsync(material -> {
        // [!] Ez a blokk az adott régió szálán (Region Thread) fut!
        location.getBlock().setType(material);
    }, scheduler.atLocation(location));
```

---

## 4. Késleltetett Feladatok (Delayed)

### `runAsyncDelayed(Runnable runnable, Duration delay)`

```java
public Task runAsyncDelayed(Runnable runnable, Duration delay)
```

Aszinkron késleltetett feladat.

### `runGlobalDelayed(Runnable runnable, Duration delay)`

```java
public Task runGlobalDelayed(Runnable runnable, Duration delay)
```

Globális régió szálon futó késleltetett feladat.

### `runForEntityDelayed(Entity entity, Runnable runnable, Duration delay)`

```java
public Task runForEntityDelayed(Entity entity, Runnable runnable, Duration delay)
```

Entitás szálon futó késleltetett feladat.

### `runAtLocationDelayed(Location location, Runnable runnable, Duration delay)`

```java
public Task runAtLocationDelayed(Location location, Runnable runnable, Duration delay)
```

Régió szálon futó késleltetett feladat.

**Példák:**
```java
// Aszinkron késleltetés (pl. adatbázis törlés 5mp múlva)
Task t1 = scheduler.runAsyncDelayed(() -> database.logEvent(), Duration.ofSeconds(5));

// Játékoshoz kötött késleltetés (Tűz levétele 3mp múlva)
Task t2 = scheduler.runForEntityDelayed(player, () -> player.setFireTicks(0), Duration.ofSeconds(3));

// Blokk manipuláció késleltetve (Eltűnik a láda 10mp múlva)
Task t3 = scheduler.runAtLocationDelayed(loc, () -> loc.getBlock().setType(Material.AIR), Duration.ofSeconds(10));
```

---

## 5. Ismétlődő Feladatok (Timers)

### `runAsyncTimer(Runnable runnable, Duration delay, Duration period)`

```java
public Task runAsyncTimer(Runnable runnable, Duration delay, Duration period)
```

Aszinkron ismétlődő feladat.

### `runGlobalTimer(Runnable runnable, Duration delay, Duration period)`

```java
public Task runGlobalTimer(Runnable runnable, Duration delay, Duration period)
```

Globális régió szálon futó ismétlődő feladat.

### `runForEntityTimer(Entity entity, Runnable runnable, Duration delay, Duration period)`

```java
public Task runForEntityTimer(Entity entity, Runnable runnable, Duration delay, Duration period)
```

Entitás szálon futó ismétlődő feladat.

### `runAtLocationTimer(Location location, Runnable runnable, Duration delay, Duration period)`

```java
public Task runAtLocationTimer(Location location, Runnable runnable, Duration delay, Duration period)
```

Régió szálon futó ismétlődő feladat.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `runnable` | `Runnable` | A futtatandó kód. |
| `delay` | `Duration` | Késleltetés az első futtatás előtt. |
| `period` | `Duration` | Ismétlődési időköz. |
| **Visszatérés** | `Task` | A task, amivel `.cancel()`-lel leállíthatod. |

**Példa:**
```java
// 5 mp késleltetéssel indul, majd percenként lefut a háttérben
Task task = scheduler.runAsyncTimer(() -> {
    database.cleanup();
}, Duration.ofSeconds(5), Duration.ofMinutes(1));

// Később, amikor már nincs rá szükség:
task.cancel();
```

---

## 6. `Task` Interfész

```java
public interface Task {
    void cancel();
}
```

Minden timer és delayed feladat egy `Task` objektumot ad vissza. A `.cancel()` metódussal leállíthatod a feladatot.

---

## 7. `shutdown()`

```java
public void shutdown()
```

Leállítja az összes ismétlődő Timer-t. **Kötelező meghívni az `onDisable()`-ben!**

---

## 8. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Szálváltás (Sync)** | `Bukkit.getScheduler().runTask(plugin, ...)` | `scheduler.runForEntity(player, ...)` vagy `scheduler.runGlobal(...)` |
| **Késleltetés (Ticks)** | `runTaskLater(plugin, ..., 100L)` | `scheduler.runForEntityDelayed(p, ..., Duration.ofSeconds(5))` |
| **Időzítő (Timer)** | `Bukkit.getScheduler().runTaskTimer(...)` | `scheduler.runAtLocationTimer(loc, ..., Duration.ZERO, Duration.ofSeconds(1))` |
| **Future Executing** | `.thenAcceptAsync(..., scheduler.sync())` | `scheduler.forEntity(player)` |
| **Timer leállítás** | `BukkitTask task; task.cancel()` | `Task task; task.cancel()` |
| **Nyers altatás** | `Thread.sleep(5000)` | `scheduler.runAsyncDelayed(...)` |

---

## 9. Összefoglaló AI Szabály

**AI Directive:** Amikor időzítést vagy szálváltást írsz, KÖTELEZŐ a Flarium `Scheduler` osztályát használnod a Folia kompatibilitás miatt. Tilos a `Bukkit.getScheduler()`! Ha egy `CompletableFuture` lefutása után Bukkit API-t hívsz, a `thenAcceptAsync` metódusnak add át a megfelelő Executort (pl. `scheduler.forEntity(player)` vagy `scheduler.atLocation(loc)`). Az időtartamokat mindig `Duration` objektummal add meg. Az ismétlődő feladatokat mentsd egy `Task` változóba, hogy később a `.cancel()` metódussal meg tudd állítani!
