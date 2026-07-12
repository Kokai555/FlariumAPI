# FlariumAPI - PlaceholderAPI Hook Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `PlaceholderService` osztályának használatát írja le.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, PlaceholderAPI integrációra **KIZÁRÓLAG** az itt leírt `PlaceholderService` módszereket használhatod. Szigorúan tilos a natív `PlaceholderExpansion` osztály kiterjesztése, és az `onRequest` metódusba egy gigantikus `switch` vagy `if-else` lánc írása!

## 1. Architekturális Szabályok (Szigorú)

1. **Nincs Óriás Switch:** A FlariumAPI-ban minden placeholder önálló funkcióként vagy osztályként (`FlariumPlaceholder`) van regisztrálva. A `PlaceholderService` ezeket egy O(1) hozzáférésű `Map`-ben tárolja a maximális teljesítmény érdekében.
2. **Automatikus Regisztráció és Soft-Depend:** A `PlaceholderService` példányosítása automatikusan ellenőrzi a PAPI meglétét, és beregisztrálja a pluginhoz tartozó kiegészítőt (Expansion). Ha nincs PAPI a szerveren, a kód nem omlik össze, csak üres stringet ad vissza. Nincs szükség manuális ellenőrzésre!
3. **Thread-Safety (Szálbiztonság):** A PAPI kérések aszinkron szálakról is jöhetnek (pl. chat formázás, scoreboard frissítés). Tilos a `process` metóduson belül szinkron adatbázis-lekérdezést (SQL) indítani! Kizárólag a `ProfileManager` vagy a `CooldownManager` memóriájából (Cache) szabad olvasni!
4. **NPE Védelem (Kritikus):** A placeholdereket a konzol is lekérheti (ilyenkor a játékos `null`), vagy offline játékosra is lefuthatnak. Mindig használni kell a null checket (Guard clause)!

---

## 2. Inicializálás a Plugin Főosztályában

A `PlaceholderService` konstruktora két paramétert vár: a plugint és a fő prefixet (identifier), amit a % jelek között használnak majd (pl. `myplugin` -> `%myplugin_...%`).

```java
public class MyPlugin extends JavaPlugin {
    private PlaceholderService placeholderService;

    @Override
    public void onEnable() {
        // Beregisztrálja a %myplugin_...% placeholdereket
        this.placeholderService = new PlaceholderService(this, "myplugin");
    }
}

```

---

## 3. Egyszerű Placeholder Regisztrálása

Ha egy placeholder fix nevű és nem vár dinamikus paramétert (pl. `%myplugin_coins%`), használd a lambda-alapú (Function) regisztrációt.

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

## 4. Paraméterezhető Placeholder Regisztrálása

Ha egy placeholder dinamikus paramétert vár (pl. `%myplugin_cooldown_<namespace>%`), implementáld a `FlariumPlaceholder` interfészt. Az `args` paraméter az azonosító (underscore) utáni maradék részt tartalmazza.

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

## 5. Stringek Manuális Parse-olása

Ha a kódodban (pl. egy GUI névében vagy Lore-jában) kézzel akarsz feloldani egy PAPI stringet:

```java
String rawText = "%myplugin_coins% érméd van!";
// Feloldja az összes PAPI placeholdert a szövegben
String parsed = placeholderService.parse(player, rawText); 

```

---

## 6. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Expansion regisztráció** | `public class MyExp extends PlaceholderExpansion { ... }` | `new PlaceholderService(this, "prefix");` |
| **Placeholder logika** | `switch(params) { case "coins": return ...; }` | `placeholderService.registerPlaceholder("coins", p -> ...);` |
| **Konzol hívás (NPE)** | `return String.valueOf(getCoins(player.getUniqueId()));` | `if (player == null) return "0"; return ...;` |
| **Adatlekérés (Thread)** | `database.executeQuery("SELECT coins FROM ...")` a process blokkban | `profileManager.getProfile(uuid).getCoins()` (Memória cache) |
| **Paraméteres placeholder** | `if (params.startsWith("cd_")) { String ns = params.substring(3); }` | `FlariumPlaceholder` interfész használata, ahol az `args` a paraméter. |
| **PAPI ellenőrzés** | `if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)` | Sose ellenőrizd! A `PlaceholderService` elintézi (Soft-depend). |

## 7. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha a kérés az, hogy "hozz létre egy placeholdert a játékos rangjához", hívd meg a `placeholderService.registerPlaceholder("rank", player -> ...)` metódust a plugin indulásakor. Szigorúan tilos natív `PlaceholderExpansion` osztályt írni és `switch` blokkot használni! Ha a placeholder további paramétert is vár (pl. egy egyedi cooldown nevet), implementáld a `FlariumPlaceholder` interfészt, és dolgozz az `args` paraméterből. **MINDIG** ellenőrizd, hogy a `player != null`, mert a PAPI kérések jöhetnek a szerverkonzolból is, és a kód nem állhat le `NullPointerException`-nel! Sose használj adatbázis hívást a placeholder parse-olásakor, kizárólag memóriából (cache) olvass!