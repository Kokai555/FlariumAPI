# FlariumAPI - Scheduler API Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `Scheduler` és `Task` osztályának használatát írja le, ami a Paper 1.21 / Folia natív Region és Entity Scheduler API-jaira épül.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, késleltetett, ismétlődő vagy aszinkron feladatokra **KIZÁRÓLAG** az itt leírt módszereket használhatod. Szigorúan tilos a natív `Bukkit.getScheduler()` vagy a `BukkitRunnable` használata! Folia alatt ezek azonnal összeomlasztják a szervert!

## 1. Architekturális Szabályok (Szigorú)

1. **Nincs Bukkit Scheduler:** Tilos a `Bukkit.getScheduler().runTask(...)` és társai. Helyettük a Flarium `Scheduler` példányának metódusait kell használni.
2. **Folia Szálbiztos Visszatérés:** Aszinkron adatbázis lekérdezés vagy nehéz számítás után a Bukkit API (World, Block, Entity, Player) hívásához kötelező visszatérni a megfelelő szálra a beépített `Executor`-okkal: Globális, Region (Location), vagy Entity szálra.
3. **Duration API (Nincs Magic Number):** Minden időmegadáshoz kötelező a `java.time.Duration` osztályt használni. Tilos a nyers "ticks" (pl. 20L) használata a paraméterekben.
4. **Task Interfész:** Minden timer és delayed feladat egy Flarium `Task` objektumot ad vissza, amivel a `.cancel()` metóduson keresztül le lehet állítani a feladatot.
5. **Memóriakezelés (Shutdown):** A `scheduler.shutdown()` metódus leálláskor **kizárólag az ismétlődő Timer-eket (run...Timer)** állítja le és takarítja ki. Az egyszer lefutó (Delayed) taszkokra ez nem vonatkozik!

---

## 2. CompletableFuture Láncolás (Executors)

Adatok betöltése aszinkron módon (`supplyAsync`), majd a kapott adat feldolgozása a Folia szálbiztonságának megfelelő `Executor` segítségével.

### Entitás szál (Játékos módosítása, GUI nyitás, HP, Üzenet):
```java
scheduler.supplyAsync(() -> database.loadStats(uuid))
    .thenAcceptAsync(stats -> {
        // [!] Ez a blokk a játékos saját szálán (Entity Thread) fut!
        player.setHealth(stats.getHealth());
        player.sendMessage("Statisztika betöltve!");
    }, scheduler.forEntity(player)); // <--- Executor

```

### Régió szál (Blokkok, Világ manipuláció):

```java
scheduler.supplyAsync(() -> database.calculateBlockType())
    .thenAcceptAsync(material -> {
        // [!] Ez a blokk az adott régió szálán (Region Thread) fut!
        location.getBlock().setType(material);
    }, scheduler.atLocation(location)); // <--- Executor

```

### Globális szál (Konzol parancsok, Broadcast):

```java
scheduler.supplyAsync(() -> database.getTopPlayer())
    .thenAcceptAsync(name -> {
        Bukkit.broadcastMessage("A legjobb játékos: " + name);
    }, scheduler.global()); // <--- Executor

```

---

## 3. Késleltetett Feladatok (Delayed)

Ha valamit x idő múlva kell lefuttatni a megfelelő szálon. Visszatér egy `Task`-al, ha esetleg idő előtt le kéne mondani.

```java
// Aszinkron késleltetés (pl. adatbázis törlés 5mp múlva)
Task t1 = scheduler.runAsyncDelayed(() -> database.logEvent(), Duration.ofSeconds(5));

// Játékoshoz kötött késleltetés (Tűz levétele 3mp múlva)
Task t2 = scheduler.runForEntityDelayed(player, () -> player.setFireTicks(0), Duration.ofSeconds(3));

// Blokk manipuláció késleltetve (Eltűnik a láda 10mp múlva)
Task t3 = scheduler.runAtLocationDelayed(loc, () -> loc.getBlock().setType(Material.AIR), Duration.ofSeconds(10));

```

---

## 4. Ismétlődő Feladatok (Timers)

Rendszeresen ismétlődő feladatok (pl. visszaszámlálók, particle effektek). A visszatérési értéket mindig mentsd el egy `Task` változóba, hogy a logika végén le tudd állítani a `.cancel()` hívásával!

```java
// 5 mp késleltetéssel indul, majd percenként lefut a háttérben
Task task = scheduler.runAsyncTimer(() -> {
    database.cleanup();
}, Duration.ofSeconds(5), Duration.ofMinutes(1));

// Később, amikor már nincs rá szükség:
task.cancel();

```

---

## 5. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Szálváltás (Sync)** | `Bukkit.getScheduler().runTask(plugin, ...);` | `scheduler.runForEntity(player, ...);` vagy `scheduler.runGlobal(...)` |
| **Késleltetés (Ticks)** | `runTaskLater(plugin, ..., 100L);` | `scheduler.runForEntityDelayed(p, ..., Duration.ofSeconds(5));` |
| **Időzítő (Timer)** | `Bukkit.getScheduler().runTaskTimer(...)` | `scheduler.runAtLocationTimer(loc, ..., Duration.ZERO, Duration.ofSeconds(1))` |
| **Future Executing** | `.thenAcceptAsync(..., scheduler.sync());` | Folia alatt nincs "sync"! Helyette: `scheduler.forEntity(player)` |
| **Timer leállítás** | `BukkitTask task; task.cancel();` | A Flarium `Task` interfész használata: `Task task; task.cancel();` |
| **Nyers altatás** | `Thread.sleep(5000);` | Aszinkron taskon belül is inkább: `scheduler.runAsyncDelayed(...)` |

## 6. Összefoglaló AI Prompt Szabály

**AI Directive:** Amikor időzítést vagy szálváltást írsz, KÖTELEZŐ a Flarium `Scheduler` osztályát használnod a Folia kompatibilitás miatt. Tilos a `Bukkit.getScheduler()`! Ha egy `CompletableFuture` lefutása után Bukkit API-t hívsz, a `thenAcceptAsync` metódusnak add át a megfelelő Executort (pl. `scheduler.forEntity(player)` vagy `scheduler.atLocation(loc)`). Az időtartamokat mindig `Duration` objektummal add meg. Az ismétlődő feladatokat (`run...Timer`) mentsd egy `Task` változóba, hogy később a `.cancel()` metódussal meg tudd állítani!