# FlariumAPI — Database API

> **Package:** `com.flarium.api.data.sql`
> **Core Class:** `DatabaseManager`

A `DatabaseManager` a HikariCP connection poolozót és a `CompletableFuture` aszinkron API-t használja. Minden metódus a háttérben, virtuális szálakon fut.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, adatbázis műveletekre **KIZÁRÓLAG** az itt leírt aszinkron metódusokat használhatod.

| Tilos | Helyette |
|-------|----------|
| Szinkron DB hívás a Folia Tick szálakon | `databaseManager.executeQuery(...)` aszinkron |
| SQL string összefűzés (`+` operátor) | `Consumer<PreparedStatement>` setter |

---

## 1. Architekturális Szabályok

1. **Aszinkronitás** — Minden adatbázis hívás a háttérben történik a `CompletableFuture` API-n keresztül.
2. **Folia Szálváltás (Kritikus)** — Adatbázis lekérdezés után, ha a Bukkit API-hoz kell nyúlnod, a `.thenAcceptAsync()` blokkban **KÖTELEZŐ** visszaváltani a megfelelő Folia szálra a `Scheduler` segítségével.
3. **Prepared Statement** — A paramétereket kötelező a `Consumer<PreparedStatement>` setter-ben beállítani (SQL Injection védelem).
4. **Hibakezelés** — A `DatabaseManager` elkapja az `SQLException`-öket és logolja őket. Hiba esetén a `CompletableFuture` `null`-lal vagy üres listával tér vissza.

---

## 2. A `config.yml` Formátuma

```yaml
database:
  type: ""              # Üres = lokális SQLite, "MYSQL" = távoli MySQL

  settings:
    address: 127.0.0.1
    port: 3306
    database: flarium
    username: admin
    password: 'password'
```

---

## 3. API Referencia — `DatabaseManager`

### Konstruktor

```java
public DatabaseManager(JavaPlugin plugin, DatabaseConfig config)
```

Inicializálja a HikariCP connection poolt a konfiguráció alapján.

---

### `executeUpdate(String sql, Consumer<PreparedStatement> setter)`

```java
public CompletableFuture<Void> executeUpdate(String sql, Consumer<PreparedStatement> setter)
```

Aszinkron INSERT/UPDATE/DELETE/DDL művelet (fire-and-forget).

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `sql` | `String` | Az SQL lekérdezés `?` helyőrzőkkel. |
| `setter` | `Consumer<PreparedStatement>` | A paraméterek beállítása. |
| **Visszatérés** | `CompletableFuture<Void>` | A művelet befejezése. |

**Példa:**
```java
public void savePlayerCoins(UUID uuid, int coins) {
    String sql = "INSERT INTO players (uuid, coins) VALUES (?, ?) ON DUPLICATE KEY UPDATE coins = ?";

    databaseManager.executeUpdate(sql, ps -> {
        try {
            ps.setString(1, uuid.toString());
            ps.setInt(2, coins);
            ps.setInt(3, coins);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving player coins: " + e.getMessage());
        }
    });
}
```

---

### `executeQuery(String sql, Consumer<PreparedStatement> setter, Function<ResultSet, T> mapper)`

```java
public <T> CompletableFuture<T> executeQuery(String sql, Consumer<PreparedStatement> setter, Function<ResultSet, T> mapper)
```

Aszinkron SELECT lekérdezés, ami egyetlen értéket ad vissza.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `sql` | `String` | Az SQL lekérdezés `?` helyőrzőkkel. |
| `setter` | `Consumer<PreparedStatement>` | A paraméterek beállítása. |
| `mapper` | `Function<ResultSet, T>` | A `ResultSet` → objektum leképezés. |
| **Visszatérés** | `CompletableFuture<T>` | A lekérdezett érték (vagy `null` hiba esetén). |

**Példa (szálváltással):**
```java
public void showStats(Player player) {
    String sql = "SELECT coins FROM players WHERE uuid = ?";

    databaseManager.executeQuery(sql, ps -> {
        ps.setString(1, player.getUniqueId().toString());
    }, rs -> {
        try {
            if (rs.next()) return rs.getInt("coins");
        } catch (SQLException e) { }
        return 0;
    }).thenAcceptAsync(coins -> {
        // [!] FOLIA SZÁLVÁLTÁS: Ez a blokk a Player saját szálán fut!
        player.sendMessage("Coins: " + coins);
    }, scheduler.forEntity(player));
}
```

---

### `executeQueryList(String sql, Consumer<PreparedStatement> setter, Function<ResultSet, T> mapper)`

```java
public <T> CompletableFuture<List<T>> executeQueryList(String sql, Consumer<PreparedStatement> setter, Function<ResultSet, T> mapper)
```

Aszinkron SELECT lekérdezés, ami több sort ad vissza (listaként).

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `sql` | `String` | Az SQL lekérdezés. |
| `setter` | `Consumer<PreparedStatement>` | A paraméterek beállítása. |
| `mapper` | `Function<ResultSet, T>` | A `ResultSet` → objektum leképezés (soronként). |
| **Visszatérés** | `CompletableFuture<List<T>>` | A lekérdezett értékek listája. |

---

### `executeTransaction(Consumer<Connection> consumer)`

```java
public CompletableFuture<Void> executeTransaction(Consumer<Connection> consumer)
```

Aszinkron tranzakció. Hiba esetén automatikus Rollback történik. Kritikus, több táblát érintő műveleteknél használd!

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `consumer` | `Consumer<Connection>` | A tranzakció logikája. |
| **Visszatérés** | `CompletableFuture<Void>` | A tranzakció befejezése. |

**Példa:**
```java
public void completeContractTransaction(Player player, String contractId) {
    databaseManager.executeTransaction(conn -> {
        try (PreparedStatement ps1 = conn.prepareStatement("UPDATE players SET completed = completed + 1 WHERE uuid = ?")) {
            ps1.setString(1, player.getUniqueId().toString());
            ps1.executeUpdate();
        }

        try (PreparedStatement ps2 = conn.prepareStatement("DELETE FROM active_contracts WHERE id = ?")) {
            ps2.setString(1, contractId);
            ps2.executeUpdate();
        }
    });
}
```

---

### `executeBatch(String sql, Consumer<PreparedStatement> setter)`

```java
public CompletableFuture<Void> executeBatch(String sql, Consumer<PreparedStatement> setter)
```

Aszinkron tömeges adatfeldolgozás. Leálláskor vagy nagy mennyiségű mentésnél tilos ciklusban `executeUpdate`-ot hívni — használd ezt!

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `sql` | `String` | Az SQL lekérdezés. |
| `setter` | `Consumer<PreparedStatement>` | A batch feltöltése (`ps.addBatch()` hívásokkal). |
| **Visszetérés** | `CompletableFuture<Void>` | A művelet befejezése. |

**Példa:**
```java
public void saveAllPlayers(Map<UUID, Integer> playersData) {
    String sql = "INSERT INTO players (uuid, coins) VALUES (?, ?) ON DUPLICATE KEY UPDATE coins = ?";

    databaseManager.executeBatch(sql, ps -> {
        try {
            for (Map.Entry<UUID, Integer> entry : playersData.entrySet()) {
                ps.setString(1, entry.getKey().toString());
                ps.setInt(2, entry.getValue());
                ps.setInt(3, entry.getValue());
                ps.addBatch();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error preparing batch: " + e.getMessage());
        }
    });
}
```

---

### `close()`

```java
public void close()
```

Lezárja a HikariCP connection poolt. **Kötelező meghívni az `onDisable()`-ben!**

---

## 4. Inicializálás és Táblák (onEnable)

A plugin `onEnable()` metódusában a táblák létrehozásánál kivételesen használható a `.join()`, hogy a szerver indulása megvárja az adatbázis sémák felépülését.

```java
public class MyPlugin extends JavaPlugin {
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        DatabaseConfig dbConfig = DatabaseConfig.load(getConfig().getConfigurationSection("database"));
        this.databaseManager = new DatabaseManager(this, dbConfig);

        databaseManager.executeUpdate(
            "CREATE TABLE IF NOT EXISTS players (uuid VARCHAR(36), coins INT)", ps -> {}
        ).join();
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
    }
}
```

---

## 5. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **SQL Összefűzés** | `"SELECT * FROM players WHERE uuid = '" + uuid + "'"` | `executeQuery("... WHERE uuid = ?", ps -> ps.setString(1, uuid.toString()), ...)` |
| **Szálváltás (Folia)** | `.thenAcceptAsync(..., scheduler.sync())` | `.thenAcceptAsync(..., scheduler.forEntity(player))` |
| **Tábla létrehozás** | `Bukkit.getScheduler().runTaskAsynchronously(...)` | `databaseManager.executeUpdate("CREATE TABLE...", ps -> {}).join()` |
| **Tömeges mentés** | `for (UUID u : map) { databaseManager.executeUpdate(...); }` | `databaseManager.executeBatch(sql, ps -> { for(...) ps.addBatch(); })` |

---

## 6. Összefoglaló AI Szabály

**AI Directive:** Ha adatot kell menteni, használd a `databaseManager.executeUpdate()`-et aszinkron módon. Az SQL lekérdezésben kötelező a `?` helyőrző és a `Consumer<PreparedStatement>` használata! Ha adatot kell lekérni és azt megjeleníteni a játékosnak, használd az `executeQuery`-t, majd a `CompletableFuture.thenAcceptAsync()` blokk második paramétereként **KÖTELEZŐEN** add át a `scheduler.forEntity(player)` executort a Folia kompatibilitás miatt!
