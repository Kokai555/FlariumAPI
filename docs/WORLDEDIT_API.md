# FlariumAPI - WorldEdit / FAWE API Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `WorldEditService` osztályának használatát írja le, ami a FastAsyncWorldEdit (FAWE) és a WorldEdit API-ra épül.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, schematicek (.schem) beillesztésére **KIZÁRÓLAG** az itt leírt `WorldEditService` módszereket használhatod. Tilos a fő szálon fájlbeolvasást végezni! Tilos a belső logikában `org.bukkit.Location`-t használni a ciklusokban!

## 1. Architekturális Szabályok (Szigorú)

1. **Try-With-Resources:** A `WorldEditService` automatikusan kezeli az `EditSession` lezárását, így nincs memóriaszivárgás. Soha ne nyiss saját `EditSession`-t a pluginjaidban!
2. **Bukkit-mentes mag:** A bemeneti `org.bukkit.Location`-t az API azonnal átalakítja WorldEdit `BlockVector3`-ra. A belső műveletek kizárólag WE típusokkal dolgoznak a maximális teljesítmény érdekében.
3. **Async I/O:** A fájl beolvasása és a beillesztés aszinkron módon történik. A metódus egy `CompletableFuture`-et ad vissza.
4. **Folia Szálváltás:** A `CompletableFuture` befejezése után a kód a FAWE szálán fut. Ha Bukkit API-t akarsz hívni (pl. hologram spawn), kötelező visszaváltani a Region szálra a `Scheduler.runAtLocation`-nel!

## 2. Inicializálás a Plugin Főosztályában

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

## 3. Schematic Beillesztése (Paste)

A beillesztés teljesen aszinkron. Használd a `thenAccept` láncolást a utána következő logikához, de figyelj a szálakra!

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

## 4. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **EditSession** | `EditSession session = WorldEdit.getInstance().newEditSession(world); ...` (Nincs lezárás) | `worldEditService.pasteSchematic(...)` (Automatikus Try-With-Resources) |
| **Fájl beolvasás** | `ClipboardReader reader = ...` a fő szálon (Lag!) | `worldEditService.pasteSchematic()` (Async I/O) |
| **Helymeghatározás** | `org.bukkit.Location` használata a WE műveletekben | `BukkitAdapter.asBlockVector(location)` (A Service ezt automatikusan csinálja) |
| **Utólagos logika** | `worldEditService.paste(...); player.sendMessage("Done!");` (Vakmeletlen szinkron hívás) | `worldEditService.paste(...).thenAccept(v -> scheduler.runGlobal(() -> player.sendMessage("Done!")));` |

## 5. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha egy játékos azt kéri, hogy "illessz be egy házat a játékos elé egy schematicek segítségével", te a `worldEditService.pasteSchematic(file, location, true)` metódust használd. Soha ne példányosíts `EditSession`-t manuálisan! A beillesztés utáni Bukkit logikát (pl. entitás spawn) mindig a `CompletableFuture.thenAccept` blokkba tedd, és abban is használj `scheduler.runAtLocation`-t a Folia kompatibilitás miatt!