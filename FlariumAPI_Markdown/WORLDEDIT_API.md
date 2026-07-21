# FlariumAPI — WorldEdit / FAWE API

> **Package:** `com.flarium.api.hook.worldedit`
> **Core Class:** `WorldEditService`

A `WorldEditService` a FastAsyncWorldEdit (FAWE) és a WorldEdit API-ra épülő, soft-depend, ClassLoader-safe híd aszinkron schematic beillesztéshez.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, schematicek (.schem) beillesztésére **KIZÁRÓLAG** az itt leírt `WorldEditService` módszereket használhatja.

| Tilos | Helyette |
|-------|----------|
| Fő szálon fájlbeolvasás | `worldEditService.pasteSchematic(...)` (async I/O) |
| Manuális `EditSession` nyitás | A Service automatikusan kezeli (Try-With-Resources) |

---

## 1. Architekturális Szabályok

1. **Try-With-Resources** — A `WorldEditService` automatikusan kezeli az `EditSession` lezárását. Soha ne nyiss saját `EditSession`-t!
2. **Bukkit-mentes mag** — A bemeneti `org.bukkit.Location`-t az API azonnal átalakítja WorldEdit `BlockVector3`-ra.
3. **Async I/O** — A fájl beolvasása és a beillesztés aszinkron módon történik.
4. **Folia Szálváltás** — A `CompletableFuture` befejezése után a kód a FAWE szálán fut. Ha Bukkit API-t akarsz hívni, kötelező visszaváltani a Region szálra a `Scheduler.runAtLocation`-nel!

---

## 2. API Referencia — `WorldEditService`

### Konstruktor

```java
public WorldEditService(Plugin plugin, Scheduler scheduler)
```

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `plugin` | `Plugin` | A plugin példány. |
| `scheduler` | `Scheduler` | A FlariumAPI Scheduler. |

Automatikusan ellenőrzi a WorldEdit/FAWE meglétét. Ha nincs fent, a `pasteSchematic()` `IllegalStateException`-t dob.

---

### `pasteSchematic(File file, Location location, boolean ignoreAir)`

```java
public CompletableFuture<Void> pasteSchematic(File file, Location location, boolean ignoreAir)
```

Aszinkron beilleszt egy schematic fájlt a megadott lokációra.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `file` | `File` | A `.schem` fájl. |
| `location` | `Location` | A beillesztés origója. |
| `ignoreAir` | `boolean` | `true` = levegő blokkok kihagyása. |
| **Visszatérés** | `CompletableFuture<Void>` | A beillesztés befejezése. |

**Példa:**
```java
File schemFile = new File(getDataFolder(), "schematics/spawn.schem");
Location loc = player.getLocation();

worldEditService.pasteSchematic(schemFile, loc, true).thenAccept(v -> {
    // Ez a blokk a FAWE szálán fut!
    plugin.getLogger().info("Schematic pasted successfully!");

    // Ha Bukkit API-t hívunk (pl. hologram), kötelező a szálváltás:
    scheduler.runAtLocation(loc, () -> {
        loc.getWorld().spawnEntity(loc, EntityType.CREEPER);
    });
});
```

---

## 3. Inicializálás a Plugin Főosztályában

```java
public class MyPlugin extends JavaPlugin {
    private WorldEditService worldEditService;

    @Override
    public void onEnable() {
        Scheduler scheduler = new Scheduler(this);
        this.worldEditService = new WorldEditService(this, scheduler);
    }
}
```

---

## 4. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **EditSession** | `EditSession session = WorldEdit.getInstance().newEditSession(world); ...` (nincs lezárás) | `worldEditService.pasteSchematic(...)` (automatikus Try-With-Resources) |
| **Fájl beolvasás** | `ClipboardReader reader = ...` a fő szálon (Lag!) | `worldEditService.pasteSchematic()` (async I/O) |
| **Helymeghatározás** | `org.bukkit.Location` használata a WE műveletekben | A Service automatikusan konvertálja |
| **Utólagos logika** | `paste(...); player.sendMessage("Done!");` (vakmeleten szinkron) | `paste(...).thenAccept(v -> scheduler.runGlobal(() -> player.sendMessage("Done!")));` |

---

## 5. Összefoglaló AI Szabály

**AI Directive:** Ha egy játékos azt kéri, hogy "illessz be egy házat a játékos elé egy schematic segítségével", használd a `worldEditService.pasteSchematic(file, location, true)` metódust. Soha ne példányosíts `EditSession`-t manuálisan! A beillesztés utáni Bukkit logikát mindig a `CompletableFuture.thenAccept` blokkba tedd, és abban is használj `scheduler.runAtLocation`-t a Folia kompatibilitás miatt!
