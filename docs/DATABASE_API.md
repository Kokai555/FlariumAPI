# FlariumAPI - Database API Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `DatabaseManager` osztályának használatát írja le, ami a HikariCP-t és a `CompletableFuture`-et használja.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, adatbázis műveletekre (SELECT, INSERT, UPDATE, tábla létrehozás) **KIZÁRÓLAG** az itt leírt aszinkron metódusokat használhatod. Szigorúan tilos a Folia Tick szálakon (Region/Entity) adatbázishoz nyúlni, és tilos SQL stringeket összefűzni (`+` operátor)! Csak Prepared Statement használható!

## 1. Architekturális Szabályok (Szigorú)

1. **Aszinkronitás:** Minden adatbázis hívás a háttérben történik a `CompletableFuture` API-n keresztül.
2. **Folia Szálváltás (Kritikus):** Adatbázis lekérdezés után, ha a Bukkit API-hoz (Player, Block, Inventory) kell nyúlnod, a `.thenAcceptAsync()` blokkban **KÖTELEZŐ** visszaváltani a megfelelő Folia szálra a `FlariumScheduler` segítségével (pl. `scheduler.forEntity(player)`). Tilos a régi `sync()` vagy `Bukkit.getScheduler()` használata!
3. **Prepared Statement:** A paramétereket kötelező a `Consumer<PreparedStatement>` setter-ben beállítani (pl. `ps.setInt(1, id)`), soha ne a String-be direkt drótozd be (SQL Injection védelem)!
4. **Hibakezelés:** A `DatabaseManager` elkapja az `SQLException`-öket és logolja őket. Hiba esetén a `CompletableFuture` nullával vagy üres listával tér vissza, ezt a kódodnak kezelnie kell (Guard clauses).

## 2. A `config.yml` Formátuma

A FlariumAPI automatikusan kezeli a kapcsolatot. Ha a `type` üres, lokális SQLite (`database.db`) indul. Ha `MYSQL`, akkor a HikariCP csatlakozik a távoli szerverhez.

```yaml
database:
  type: "" # Vagy "MYSQL"

  settings:
    address: 127.0.0.1
    port: 3306
    database: flarium
    username: admin
    password: 'password'

```

## 3. Inicializálás és Táblák (onEnable)

A plugin `onEnable()` metódusában a táblák létrehozásánál kivételesen használható a `.join()`, hogy a szerver indulása megvárja az adatbázis sémák felépülését.

```java
public class MyPlugin extends JavaPlugin {
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        DatabaseConfig dbConfig = DatabaseConfig.load(getConfig().getConfigurationSection("database"));
        this.databaseManager = new DatabaseManager(this, dbConfig);
        
        // Induláskor megvárjuk (.join()), hogy a tábla biztosan létrejöjjön
        databaseManager.executeUpdate("CREATE TABLE IF NOT EXISTS players (uuid VARCHAR(36), coins INT)", ps -> {}).join();
    }
    
    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
    }
}

```

## 4. Adatok Mentése (INSERT / UPDATE)

Használd az `executeUpdate` metódust a "tüzelj és felejtsd el" (fire-and-forget) műveletekhez.

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

## 5. Adatok Lekérdezése (SELECT) és Szálváltás

Használd az `executeQuery` metódust. **Figyelem:** A lekérdezés után azonnal szálat kell váltanod az Entitás szálára, ha üzenetet küldesz vagy GUI-t frissítesz!

```java
public void showStats(Player player) {
    String sql = "SELECT coins FROM players WHERE uuid = ?";
    
    databaseManager.executeQuery(sql, ps -> {
        ps.setString(1, player.getUniqueId().toString());
    }, rs -> {
        try {
            if (rs.next()) return rs.getInt("coins");
        } catch (SQLException e) { }
        return 0; // Alapértelmezett érték, ha nincs a DB-ben
    }).thenAcceptAsync(coins -> {
        // [!] FOLIA SZÁLVÁLTÁS: Ez a blokk már a Player saját szálán fut!
        player.sendMessage("Coins: " + coins);
        menuManager.updateCoinsGui(player, coins);
    }, scheduler.forEntity(player)); 
}

```

## 6. Tranzakciók (Transaction Support)

Kritikus, több táblát érintő műveleteknél (pl. vásárlás és küldetés teljesítés egyszerre), használd az `executeTransaction` metódust. Hiba esetén automatikus Rollback történik.

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

## 7. Tömeges Adatfeldolgozás (Batch Processing)

Leálláskor vagy nagy mennyiségű mentésnél tilos ciklusban `executeUpdate`-ot hívni. Használd az `executeBatch`-et, ami egyetlen csomagban küldi el az összes adatot a szervernek!

```java
public void saveAllPlayers(Map<UUID, Integer> playersData) {
    String sql = "INSERT INTO players (uuid, coins) VALUES (?, ?) ON DUPLICATE KEY UPDATE coins = ?";
    
    databaseManager.executeBatch(sql, ps -> {
        try {
            for (Map.Entry<UUID, Integer> entry : playersData.entrySet()) {
                ps.setString(1, entry.getKey().toString());
                ps.setInt(2, entry.getValue());
                ps.setInt(3, entry.getValue());
                ps.addBatch(); // Batch-hez adás
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error preparing batch: " + e.getMessage());
        }
    });
}

```

---

## 8. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **SQL Összefűzés** | `executeQuery("SELECT * FROM players WHERE uuid = '" + uuid + "'");` | `executeQuery("... WHERE uuid = ?", ps -> ps.setString(1, uuid.toString()), ...);` |
| **Szálváltás (Folia)** | `.thenAcceptAsync(data -> player.sendMessage(...), scheduler.sync());` | `.thenAcceptAsync(data -> player.sendMessage(...), scheduler.forEntity(player));` |
| **Tábla létrehozás** | `Bukkit.getScheduler().runTaskAsynchronously(...)` az onEnable-ban | `databaseManager.executeUpdate("CREATE TABLE...", ps -> {}).join();` az onEnable-ban |
| **Tömeges mentés** | `for (UUID u : map) { databaseManager.executeUpdate(...); }` | `databaseManager.executeBatch(sql, ps -> { for(...) ps.addBatch(); });` |
| **Logolás** | `plugin.getLogger().severe("Hiba!");` | `plugin.getLogger().severe("Error saving data: " + e.getMessage());` |

## 9. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha egy játékos azt kéri, hogy "mentsd el a játékos pénzét az adatbázisba", hozz létre egy metódust, ami `databaseManager.executeUpdate()`-et hív aszinkron módon. Az SQL lekérdezésben kötelező a `?` helyőrző és a `Consumer<PreparedStatement>` használata! Ha adatot kell lekérni és azt megjeleníteni a játékosnak (üzenet vagy GUI), használd az `executeQuery`-t, majd a `CompletableFuture.thenAcceptAsync()` blokk második paramétereként **KÖTELEZŐEN** add át a `scheduler.forEntity(player)` executort a Folia kompatibilitás miatt!