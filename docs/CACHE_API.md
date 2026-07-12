# FlariumAPI - Cache & Profile API Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `AbstractProfileManager` osztályának használatát írja le, ami a Caffeine cache-t és a HikariCP/CompletableFuture adatbázis-réteget ötvözi.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, játékos adatok (profilok, pénz, statisztikák, küldetések) memóriában való tárolására **KIZÁRÓLAG** az itt leírt módszereket használhatod. Tilos a nyers `HashMap<UUID, Profile>` használata! Tilos a `PlayerJoinEvent`-ben szinkron adatbázis lekérdezést indítani!

## 1. Architekturális Szabályok (Szigorú)

1. **Nincs Nyers HashMap:** A memóriaszivárgások (memory leak) elkerülése végett kötelező a Caffeine Cache használata (amit az `AbstractProfileManager` csomagol be) `expireAfterAccess` beállítással.
2. **Async PreLogin (Thread-Safe):** Az adatok betöltése **kötelezően** az `AsyncPlayerPreLoginEvent`-ben történik (ezt a Manager alapból kezeli, a te kódod a háttérben fut le), sosem a főszálon lévő `PlayerJoinEvent`-ben!
3. **Automatikus Mentés (Eviction & Quit):** Ha a cache ejti az elemet (mert a játékos inaktív), vagy a játékos kilép (`PlayerQuitEvent`), a rendszer automatikusan, aszinkron módon meghívja a `saveToDatabase` metódust. Nincs szükség kézi mentésre a kilépésnél!
4. **Biztonságos Leállás (Shutdown Timeout):** A szerver leállásakor (`onDisable`) a Manager maximum 5 másodpercet vár a még folyamatban lévő aszinkron mentésekre, így megakadályozza a szerver kifagyását.

---

## 2. A ProfileManager Implementálása

A pluginodban hozz létre egy osztályt, ami örökli az `AbstractProfileManager<T>`-t. Itt kell megírnod a konkrét SQL lekérdezéseket.

```java
public class PlayerProfileManager extends AbstractProfileManager<PlayerProfile> {

    private final DatabaseManager db;

    public PlayerProfileManager(JavaPlugin plugin, DatabaseManager db) {
        // 300 másodperc (5 perc) inaktivitás után a cache magától ejti és menti az adatot
        super(plugin, 300, 1000); 
        this.db = db;
    }

    @Override
    protected CompletableFuture<PlayerProfile> loadFromDatabase(UUID uuid) {
        String sql = "SELECT * FROM players WHERE uuid = ?";
        return db.executeQuery(sql, ps -> {
            ps.setString(1, uuid.toString());
        }, rs -> {
            try {
                if (rs.next()) {
                    return new PlayerProfile(uuid, rs.getInt("coins"));
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            // Ha nincs az adatbázisban, létrehozunk egy új, üres profilt
            return new PlayerProfile(uuid, 0); 
        });
    }

    @Override
    protected CompletableFuture<Void> saveToDatabase(PlayerProfile profile) {
        String sql = "INSERT INTO players (uuid, coins) VALUES (?, ?) ON DUPLICATE KEY UPDATE coins = ?";
        return db.executeUpdate(sql, ps -> {
            try {
                ps.setString(1, profile.getUuid().toString());
                ps.setInt(2, profile.getCoins());
                ps.setInt(3, profile.getCoins());
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        });
    }
}

```

## 3. Regisztráció a Plugin Főosztályában

A `ProfileManager`-t regisztrálnod kell listenerként, hogy a belépés/kilépés eventek működjenek.

```java
public class MyPlugin extends JavaPlugin {
    private PlayerProfileManager profileManager;

    @Override
    public void onEnable() {
        DatabaseManager dbManager = new DatabaseManager(this, dbConfig);
        profileManager = new PlayerProfileManager(this, dbManager);
        Bukkit.getPluginManager().registerEvents(profileManager, this);
    }

    @Override
    public void onDisable() {
        if (profileManager != null) {
            profileManager.shutdown(); // Végrehajtja a biztonságos, 5 másodperces mentést
        }
    }
}

```

---

## 4. Adatok Lekérdezése és Módosítása (Gyakorlati Példák)

### Online Játékos Adatának Módosítása (O(1) Memória hívás)

Mivel az online játékosok adatai a RAM-ban (Cache) vannak, lekérdezésük azonnali, és nem igényel adatbázis-hívást. A módosítás automatikusan el lesz mentve kilépéskor.

```java
public void addCoins(Player player, int amount) {
    PlayerProfile profile = profileManager.getProfile(player.getUniqueId());
    if (profile != null) {
        profile.setCoins(profile.getCoins() + amount);
        player.sendMessage("Kaptál " + amount + " érmét!");
    }
}

```

### Offline Játékos Adatának Módosítása (Kritikus!)

Ha egy *offline* játékosnak akarsz pénzt adni, az adata valószínűleg nincs a memóriában. Ilyenkor **aszinkron be kell tölteni**, módosítani, majd menteni és azonnal törölni a cache-ből.

```java
public void addCoinsOffline(UUID targetUuid, int amount) {
    // A loadProfile aszinkron betölti az adatbázisból és beteszi a cache-be
    profileManager.loadProfile(targetUuid).thenAccept(v -> {
        PlayerProfile profile = profileManager.getProfile(targetUuid);
        if (profile != null) {
            profile.setCoins(profile.getCoins() + amount);
            
            // Ha a játékos továbbra sincs online, azonnal elmentjük és kivesszük a memóriából
            if (Bukkit.getPlayer(targetUuid) == null) {
                profileManager.saveAndInvalidate(targetUuid);
            }
        }
    });
}

```

---

## 5. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Adat tárolás** | `private Map<UUID, Profile> cache = new HashMap<>();` | `extends AbstractProfileManager<Profile>` (Caffeine beépítve) |
| **Adat betöltés** | `@EventHandler public void onJoin(PlayerJoinEvent e) { db.load(e.getPlayer()); }` | Csak implementáld a `loadFromDatabase`-t, a Manager intézi az AsyncPreLogint! |
| **Adat lekérdezés** | `db.getCoins(uuid);` (SQL lekérdezés minden kattintásnál) | `profileManager.getProfile(uuid).getCoins();` (RAM hozzáférés) |
| **Adat mentés** | `@EventHandler public void onQuit(PlayerQuitEvent e) { db.save(e.getPlayer()); }` | Csak implementáld a `saveToDatabase`-t, a Manager intézi a kilépést! |
| **Offline módosítás** | Szinkron `ResultSet` lekérdezés a parancs futtatásakor. | `profileManager.loadProfile(uuid).thenAccept(...)` aszinkron láncolás. |

## 6. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha egy játékos azt kéri, hogy "mentse el a játékos statisztikáját", készíts egy `XProfile` adat-osztályt, és hozz létre egy `XProfileManager`-t, ami kiterjeszti az `AbstractProfileManager`-t. Implementáld az aszinkron SQL hívásokat a betöltéshez és mentéshez. A logika során (pl. egy GUI kattintásnál) mindig a `profileManager.getProfile()` memóriából olvasó metódust használd! Ha explicit offline játékost kell módosítanod, használd a `loadProfile(uuid).thenAccept(...)` metódust, módosítsd a lekérdezett profilt, majd hívd meg a `saveAndInvalidate(uuid)`-t!