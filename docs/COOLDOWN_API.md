# FlariumAPI - Cooldown & Expire API Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `CooldownManager` osztályának használatát írja le, ami a Caffeine cache-t, az aszinkron DatabaseManager-t és a Folia-biztos Scheduler-t ötvözi.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, visszaszámlálások (cooldown), napi jutalmak (daily rewards), vagy lejárati idők (expire) kezelésére **KIZÁRÓLAG** az itt leírt módszereket használhatod. Szigorúan tilos a `HashMap<UUID, Long>` és a kézi `System.currentTimeMillis()` alapú spagetti kód generálása!

## 1. Architekturális Szabályok (Szigorú)

1. **Kettős Tárolás (Dual Storage):** Az ideiglenes cooldownok (pl. Ender Pearl, Combat Tag) kizárólag memóriában (Caffeine) tárolódnak és újrainduláskor elvesznek. A perzisztens cooldownok (pl. Daily Reward) memóriában ÉS az adatbázisban is élnek.
2. **Névtér-alapú (Namespaced):** Minden cooldown egy `UUID` és egy `String` névtér (namespace) kombinációjából alkot kulcsot (pl. `"daily_reward"`, `"combat_tag"`). 
3. **Aktív Lejárati Callbackök (Expire Event):** A Manager képes egy kódrészletet lefuttatni a cooldown lejártakor. Ehhez kötelező megadni a Folia szálbiztonság miatt a megfelelő `Executor`-t (pl. az entitás szálát).
4. **Thread-Safety:** A `CooldownManager` minden metódusa teljesen szálbiztos, nyugodtan hívható aszinkron és szinkron szálakról is.

---

## 2. API Referencia és Gyakorlati Példák

### 2.1 Ideiglenes (Ephemeral) Cooldownok
Tökéletes spamelésgátlónak, képességekhez, vagy harci állapothoz. Csak memóriát használ.

**Egyszerű beállítás és ellenőrzés:**
```java
// Ha még aktív a cooldown, visszautasítjuk
if (cooldownManager.isActive(player.getUniqueId(), "heal_spell")) {
    String left = cooldownManager.getFormattedRemaining(player.getUniqueId(), "heal_spell", timeFormat);
    player.sendMessage("Még várnod kell: " + left);
    return;
}

// Ha nem aktív, végrehajtjuk, majd rárakjuk a 15 másodperces cooldownt
player.setHealth(20.0);
cooldownManager.set(player.getUniqueId(), "heal_spell", Duration.ofSeconds(15));

```

**Aktív lejárati callback (Pl. Combat Tag vége):**
Ha Bukkit API-t hívsz a lejárati blokkban, **kötelező** a Scheduler `Executor`-át átadni!

```java
cooldownManager.set(player.getUniqueId(), "combat_tag", Duration.ofSeconds(10), 
    () -> {
        // Ez a kód 10 másodperc múlva fut le a játékos szálán
        player.sendMessage("<green>Kikerültél a harcból, most már kiléphetsz!");
    }, 
    scheduler.forEntity(player) // KÖTELEZŐ Folia szálváltás!
);

```

### 2.2 Perzisztens (Adatbázis) Cooldownok

Tökéletes napi/heti jutalmakhoz. Túléli a szerver újraindítását.

**Beállítás (Aszinkron SQL hívást indít a háttérben):**

```java
cooldownManager.setPersistent(player.getUniqueId(), "daily_reward", Duration.ofHours(24)).join();

```

**Betöltés (Belépéskor):**
A Profil Managerekben, vagy az `AsyncPlayerPreLoginEvent`-ben meg kell hívni a betöltést.

```java
cooldownManager.loadPersistent(uuid).join();

```

### 2.3 Cooldownok Törlése és Kitakarítása

**Manuális törlés (Pl. admin parancs vagy "reset" tárgy):**

```java
cooldownManager.remove(player.getUniqueId(), "daily_reward");

```

**Memória felszabadítása (KÖTELEZŐ kilépéskor):**
A `PlayerQuitEvent`-ben mindig hívd meg az alábbi metódust. Ez törli a cache-t, és leállítja a folyamatban lévő lejárati (Expire) taszkokat, megelőzve a memory leak-et!

```java
@EventHandler
public void onQuit(PlayerQuitEvent event) {
    cooldownManager.clearOnQuit(event.getPlayer().getUniqueId());
}

```

---

## 3. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Cooldown tárolás** | `private Map<UUID, Long> cooldowns = new HashMap<>();` | `cooldownManager.set(uuid, "namespace", duration);` |
| **Idő ellenőrzés** | `if (System.currentTimeMillis() - map.get(uuid) < 15000)` | `if (cooldownManager.isActive(uuid, "namespace"))` |
| **Hátralévő idő** | Kézi `long left = (cd - System.currentTimeMillis()) / 1000;` | `cooldownManager.getFormattedRemaining(uuid, "ns", format);` |
| **Napi jutalom** | Adatbázis lekérdezés SQL-lel minden gombnyomásnál. | `isActive()` (O(1) memória), majd `setPersistent()` használata. |
| **Lejárati task** | `Bukkit.getScheduler().runTaskLater(...)` | `cooldownManager.set(uuid, "ns", dur, () -> ..., executor)` |
| **Kilépés kezelés** | Semmi, vagy manuális Map takarítás. | `cooldownManager.clearOnQuit(uuid);` hívása PlayerQuitEvent-ben. |

## 4. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha egy funkcióhoz visszaszámlálót kérnek (pl. "a játékos csak 24 óránként használhassa a /vote parancsot"), az ellenőrzéshez a `cooldownManager.isActive(uuid, namespace)` metódust kell használnod. A hátralévő idő kiírásához használd a `getFormattedRemaining`-et egy `TimeFormat` objektummal. Alkalmazásakor hívd meg a `cooldownManager.setPersistent(uuid, namespace, Duration...)` metódust. Szigorúan tilos kézi `HashMap`-et, `System.currentTimeMillis()` matekot, vagy natív `BukkitRunnable` alapú időzítőt írni a cooldownokhoz!