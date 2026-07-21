# FlariumAPI — PlaceholderAPI Hook

> **Package:** `com.flarium.api.hook.placeholder`
> **Core Classes:** `PlaceholderService`, `FlariumPlaceholder`

A `PlaceholderService` egy soft-depend, ClassLoader-safe PlaceholderAPI hook. A placeholdereket egy O(1) hozzáférésű `Map`-ben tárolja a maximális teljesítmény érdekében.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, PlaceholderAPI integrációra **KIZÁRÓLAG** az itt leírt `PlaceholderService` módszereket használhatja.

| Tilos | Helyette |
|-------|----------|
| `public class MyExp extends PlaceholderExpansion` | `new PlaceholderService(this, "prefix")` |
| `switch(params) { case "coins": ... }` | `placeholderService.registerPlaceholder("coins", p -> ...)` |

---

## 1. Architekturális Szabályok

1. **Nincs Óriás Switch** — Minden placeholder önálló funkcióként van regisztrálva. A `PlaceholderService` O(1) `Map`-ben tárolja őket.
2. **Automatikus Regisztráció és Soft-Depend** — Ha nincs PAPI a szerveren, a kód nem omlik össze, csak üres stringet ad vissza. Nincs szükség manuális ellenőrzésre!
3. **Thread-Safety** — A PAPI kérések aszinkron szálakról is jöhetnek. Tilos a `process` metóduson belül szinkron adatbázis-lekérdezést indítani! Kizárólag a `ProfileManager` vagy a `CooldownManager` memóriájából (Cache) szabad olvasni!
4. **NPE Védelem** — A placeholdereket a konzol is lekérheti (ilyenkor a játékos `null`). Mindig használni kell a null checket!

---

## 2. API Referencia — `PlaceholderService`

### Konstruktor

```java
public PlaceholderService(Plugin plugin, String rootIdentifier)
```

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `plugin` | `Plugin` | A plugin példány. |
| `rootIdentifier` | `String` | A PAPI prefix (pl. `"myplugin"` → `%myplugin_...%`). |

Automatikusan ellenőrzi a PAPI meglétét és beregisztrálja az expansion-t.

---

### `registerPlaceholder(String identifier, Function<OfflinePlayer, String> function)`

```java
public void registerPlaceholder(String identifier, Function<OfflinePlayer, String> function)
```

Egyszerű, paraméter nélküli placeholder regisztrálása (pl. `%myplugin_coins%`).

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `identifier` | `String` | A placeholder neve (pl. `"coins"`). |
| `function` | `Function<OfflinePlayer, String>` | A logika, ami visszaadja az értéket. |

**Példa:**
```java
placeholderService.registerPlaceholder("coins", player -> {
    // GUARD CLAUSE: Konzol vagy hiba esetén a player null lehet!
    if (player == null) return "0";

    // O(1) memória hozzáférés, nincs SQL hívás!
    PlayerProfile profile = profileManager.getProfile(player.getUniqueId());
    return profile != null ? String.valueOf(profile.getCoins()) : "0";
});
```

---

### `registerPlaceholder(FlariumPlaceholder placeholder)`

```java
public void registerPlaceholder(FlariumPlaceholder placeholder)
```

Paraméterezhető placeholder regisztrálása (pl. `%myplugin_cooldown_<namespace>%`).

**Példa:**
```java
placeholderService.registerPlaceholder(new FlariumPlaceholder() {
    @Override
    public String getIdentifier() {
        return "cooldown"; // Erre fog reagálni: %myplugin_cooldown_valami%
    }

    @Override
    public String process(OfflinePlayer player, String args) {
        // Guard clause konzolra és üres argumentumokra
        if (player == null || args == null || args.isEmpty()) return "N/A";

        // args tartalma pl. "contract_reroll" lesz
        return cooldownManager.getFormattedRemaining(
            player.getUniqueId(),
            args,
            timeFormat
        );
    }
});
```

---

### `parse(OfflinePlayer player, String text)`

```java
public String parse(OfflinePlayer player, String text)
```

Feloldja az összes PAPI placeholdert a szövegben.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `player` | `OfflinePlayer` | A játékos. |
| `text` | `String` | A nyers szöveg. |
| **Visszatérés** | `String` | A feloldott szöveg. |

**Példa:**
```java
String rawText = "%myplugin_coins% érméd van!";
String parsed = placeholderService.parse(player, rawText);
```

---

## 3. A `FlariumPlaceholder` Interfész

```java
public interface FlariumPlaceholder {
    String getIdentifier();
    String process(OfflinePlayer player, String args);
}
```

| Metódus | Leírás |
|---------|--------|
| `getIdentifier()` | A placeholder azonosítója (pl. `"cooldown"`). |
| `process(OfflinePlayer, String args)` | A logika. Az `args` az azonosító utáni maradék rész. |

---

## 4. Inicializálás a Plugin Főosztályában

```java
public class MyPlugin extends JavaPlugin {
    private PlaceholderService placeholderService;

    @Override
    public void onEnable() {
        this.placeholderService = new PlaceholderService(this, "myplugin");
    }
}
```

---

## 5. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Expansion regisztráció** | `public class MyExp extends PlaceholderExpansion { ... }` | `new PlaceholderService(this, "prefix");` |
| **Placeholder logika** | `switch(params) { case "coins": return ...; }` | `placeholderService.registerPlaceholder("coins", p -> ...);` |
| **Konzol hívás (NPE)** | `return String.valueOf(getCoins(player.getUniqueId()));` | `if (player == null) return "0"; return ...;` |
| **Adatlekérés (Thread)** | `database.executeQuery("SELECT coins FROM ...")` a process blokkban | `profileManager.getProfile(uuid).getCoins()` (memória cache) |
| **Paraméteres placeholder** | `if (params.startsWith("cd_")) { String ns = params.substring(3); }` | `FlariumPlaceholder` interfész, ahol az `args` a paraméter |
| **PAPI ellenőrzés** | `if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)` | Sose ellenőrizd! A `PlaceholderService` elintézi (Soft-depend). |

---

## 6. Összefoglaló AI Szabály

**AI Directive:** Ha a kérés az, hogy "hozz létre egy placeholdert a játékos rangjához", hívd meg a `placeholderService.registerPlaceholder("rank", player -> ...)` metódust. Szigorúan tilos natív `PlaceholderExpansion` osztályt írni és `switch` blokkot használni! Ha a placeholder további paramétert is vár, implementáld a `FlariumPlaceholder` interfészt. **MINDIG** ellenőrizd, hogy a `player != null`, mert a PAPI kérések jöhetnek a szerverkonzolból is! Sose használj adatbázis hívást a placeholder parse-olásakor, kizárólag memóriából (cache) olvass!
