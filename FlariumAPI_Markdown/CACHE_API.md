# FlariumAPI — Cache & Profile API

> **Package:** `com.flarium.api.data.cache`
> **Core Class:** `AbstractProfileManager<V>`

A `AbstractProfileManager` egy generikus, Caffeine cache-t és HikariCP/`CompletableFuture` adatbázis-réteget ötvöző absztrakt osztály. Célja, hogy a játékos profilok (pénz, statisztikák, küldetések) memóriabeli tárolása **biztonságos**, **szálbiztos** és **memóriahatékony** legyen.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, játékos adatok memóriában való tárolására **KIZÁRÓLAG** az itt leírt módszereket használhatod.

| Tilos | Helyette |
|-------|----------|
| `HashMap<UUID, Profile>` | `extends AbstractProfileManager<Profile>` |
| `PlayerJoinEvent`-ben szinkron DB lekérdezés | `AsyncPlayerPreLoginEvent` (a Manager automatikusan kezeli) |

---

## 1. Architekturális Szabályok

1. **Nincs nyers HashMap** — A memóriaszivárgások elkerülése végett kötelező a Caffeine Cache használata `expireAfterAccess` beállítással.
   - *Eviction Guard:* Az online játékosokat a Manager egy erős referenciás (`ConcurrentHashMap`) térképben is tárolja, így a Caffeine LRU algoritmusa sosem dobhat ki aktív játékost.
2. **Async PreLogin (Thread-Safe)** — Az adatok betöltése kötelezően az `AsyncPlayerPreLoginEvent`-ben történik, sosem a főszálon lévő `PlayerJoinEvent`-ben.
3. **Deduplikált Betöltés** — A `pendingLoads` mechanizmus összevonja a párhuzamos betöltési kérelmeket (coalescing), megakadályozva a "Lost Update" és TOCTOU hibákat.
4. **Automatikus Mentés** — Ha a cache ejti az elemet, vagy a játékos kilép, a rendszer aszinkron meghívja a [`saveToDatabase()`](#savetodatabase) metódust.
5. **Biztonságos Leállás** — Leálláskor a rendszer maximum 5 másodpercet vár a folyamatban lévő mentésekre.
   - ⚠️ Extrém lassú adatbázis esetén a mentés megszakadhat. Kritikus tranzakcióknál fontold meg az azonnali szinkron SQL mentést!

---

## 2. API Referencia — `AbstractProfileManager<V>`

### Konstruktor

```java
protected AbstractProfileManager(JavaPlugin plugin, long expireAfterAccessSeconds, long maxSize)
```

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `plugin` | `JavaPlugin` | A plugin példány. |
| `expireAfterAccessSeconds` | `long` | Másodpercek, amennyi inaktivitás után a cache ejti az elemet. |
| `maxSize` | `long` | A cache maximális elemszáma. |

**Példa:**
```java
public class PlayerProfileManager extends AbstractProfileManager<PlayerProfile> {
    private final DatabaseManager db;

    public PlayerProfileManager(JavaPlugin plugin, DatabaseManager db) {
        super(plugin, 300, 1000); // 5 perc inaktivitás, max 1000 profil
        this.db = db;
    }
}
```

---

### Absztrakt metódusok (kötelező implementálni)

#### `loadFromDatabase(UUID uuid)`

```java
protected abstract CompletableFuture<V> loadFromDatabase(UUID uuid)
```

Betölti a profilt az adatbázisból aszinkron módon.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos egyedi azonosítója. |
| **Visszatérés** | `CompletableFuture<V>` | A betöltött profil (vagy új, ha nem létezik). |

**Példa:**
```java
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
        return new PlayerProfile(uuid, 0); // Új profil, ha nincs DB-ben
    });
}
```

#### `saveToDatabase(V profile)`

```java
protected abstract CompletableFuture<Void> saveToDatabase(V profile)
```

Elmenti a profilt az adatbázisba aszinkron módon.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `profile` | `V` | A mentendő profil. |
| **Visszatérés** | `CompletableFuture<Void>` | A mentés befejezését jelző future. |

**Példa:**
```java
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
```

---

### Publikus metódusok

#### `getProfile(UUID uuid)`

```java
public V getProfile(UUID uuid)
```

Visszaadja a profilt a memóriából (O(1)). Először az `activeProfiles` erős referenciás térképben keres, majd a Caffeine cache-ben.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos egyedi azonosítója. |
| **Visszatérés** | `V` | A profil, vagy `null` ha nincs a memóriában. |

**Példa:**
```java
PlayerProfile profile = profileManager.getProfile(player.getUniqueId());
if (profile != null) {
    profile.setCoins(profile.getCoins() + 100);
}
```

#### `getProfileOrThrow(UUID uuid)`

```java
public V getProfileOrThrow(UUID uuid)
```

Visszaadja a profilt a memóriából, vagy `IllegalStateException`-t dob, ha nincs betöltve. **Kritikus módosításoknál kötelező használni** a csendes hibák (silent fail) elkerülése végett!

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos egyedi azonosítója. |
| **Visszatérés** | `V` | A profil (sosem `null`). |
| **Dob** | `IllegalStateException` | Ha a profil nincs a memóriában. |

**Példa:**
```java
public void addCoins(Player player, int amount) {
    PlayerProfile profile = profileManager.getProfileOrThrow(player.getUniqueId());
    profile.setCoins(profile.getCoins() + amount);
    player.sendMessage("Kaptál " + amount + " érmét!");
}
```

#### `loadProfile(UUID uuid)`

```java
public CompletableFuture<Void> loadProfile(UUID uuid)
```

Aszinkron betölti a profilt az adatbázisból (vagy csatlakozik egy folyamatban lévő betöltéshez a deduplikáció miatt). Betöltés után a cache-be és — ha a játékos online — az `activeProfiles` térképbe is bekerül.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos egyedi azonosítója. |
| **Visszetérés** | `CompletableFuture<Void>` | A betöltés befejezését jelző future. |

**Példa (offline játékos módosítása):**
```java
public void addCoinsOffline(UUID targetUuid, int amount) {
    profileManager.loadProfile(targetUuid).thenAccept(v -> {
        PlayerProfile profile = profileManager.getProfileOrThrow(targetUuid);
        profile.setCoins(profile.getCoins() + amount);

        if (Bukkit.getPlayer(targetUuid) == null) {
            profileManager.saveAndInvalidate(targetUuid);
        }
    });
}
```

#### `saveAndInvalidate(UUID uuid)`

```java
public CompletableFuture<Void> saveAndInvalidate(UUID uuid)
```

Eltávolítja a profilt a memóriából és aszinkron elmenti az adatbázisba.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos egyedi azonosítója. |
| **Visszatérés** | `CompletableFuture<Void>` | A mentés befejezését jelző future. |

**Példa:**
```java
profileManager.saveAndInvalidate(player.getUniqueId()).thenRun(() -> {
    plugin.getLogger().info("Profil elmentve és eltávolítva a memóriából.");
});
```

#### `shutdown()`

```java
public void shutdown()
```

Leállítja a managert: az összes cache-ben lévő profilt elmenti, majd maximum 5 másodpercet vár a folyamatban lévő mentésekre. **Kötelező meghívni az `onDisable()`-ben!**

**Példa:**
```java
@Override
public void onDisable() {
    if (profileManager != null) {
        profileManager.shutdown();
    }
}
```

---

## 3. Regisztráció a Plugin Főosztályában

A `ProfileManager`-t regisztrálni kell listenerként, hogy a belépés/kilépés eventek automatikusan működjenek.

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
            profileManager.shutdown();
        }
    }
}
```

---

## 4. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Adat tárolás** | `private Map<UUID, Profile> cache = new HashMap<>();` | `extends AbstractProfileManager<Profile>` |
| **Adat lekérdezés** | `Profile p = getProfile(uuid); if(p!=null) p.add();` | `getProfileOrThrow(uuid).add();` |
| **Offline módosítás** | Szinkron `ResultSet` lekérdezés. | `loadProfile(uuid).thenAccept(...)` aszinkron láncolás. |
| **Mentés / Belépés** | `PlayerJoinEvent` manuális DB hívás. | A Manager automatikusan intézi. |

---

## 5. Összefoglaló AI Szabály

**AI Directive:** Ha játékos statisztikát kell kezelned, készíts egy `XProfileManager`-t, ami kiterjeszti az `AbstractProfileManager`-t. A logika során mindig a `getProfileOrThrow(uuid)` metódust használd a csendes hibák elkerülése végett! Ha explicit offline játékost kell módosítanod, használd a `loadProfile(uuid).thenAccept(...)` metódust, módosítsd a profilt a `getProfileOrThrow(uuid)` segítségével, majd ha a játékos offline, hívd meg a `saveAndInvalidate(uuid)`-t!
