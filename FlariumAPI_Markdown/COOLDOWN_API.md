# FlariumAPI — Cooldown & Expire API

> **Package:** `com.flarium.api.feature.cooldown`
> **Core Class:** `CooldownManager`

A `CooldownManager` a Caffeine cache-t, az aszinkron `DatabaseManager`-t és a Folia-biztos `Scheduler`-t ötvözi. Kétféle visszaszámlálót támogat: **ideiglenes** (ephemeral, csak memória) és **perzisztens** (memória + adatbázis).

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, visszaszámlálások (cooldown), napi jutalmak (daily rewards), vagy lejárati idők (expire) kezelésére **KIZÁRÓLAG** az itt leírt módszereket használhatod.

| Tilos | Helyette |
|-------|----------|
| `HashMap<UUID, Long>` | `cooldownManager.set(uuid, "ns", duration)` |
| `System.currentTimeMillis()` matek | `cooldownManager.isActive(uuid, "ns")` |

---

## 1. Architekturális Szabályok

1. **Kettős Tárolás (Dual Storage)** — Az ideiglenes cooldownok csak memóriában (Caffeine) élnek és újrainduláskor elvesznek. A perzisztens cooldownok memóriában ÉS az adatbázisban is élnek.
2. **Névtér-alapú (Namespaced)** — Minden cooldown egy `UUID` és egy `String` névtér kombinációjából alkot kulcsot (pl. `"daily_reward"`, `"combat_tag"`).
3. **Aktív Lejárati Callbackök** — A Manager képes egy `Runnable`-t lefuttatni a cooldown lejártakor. Folia szálbiztonság miatt kötelező megadni a megfelelő `Executor`-t.
4. **Thread-Safety** — Minden metódus teljesen szálbiztos.

---

## 2. API Referencia — `CooldownManager`

### Konstruktor

```java
public CooldownManager(DatabaseManager databaseManager, Scheduler scheduler)
```

Automatikusan létrehozza a `flarium_cooldowns` táblát.

---

### Ideiglenes (Ephemeral) Cooldownok

#### `set(UUID uuid, String namespace, Duration duration)`

```java
public void set(UUID uuid, String namespace, Duration duration)
```

Beállít egy ideiglenes cooldownot (csak memória). Újrainduláskor elveszik.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos azonosítója. |
| `namespace` | `String` | A cooldown névtere (pl. `"heal_spell"`). |
| `duration` | `Duration` | A cooldown időtartama. |

**Példa:**
```java
cooldownManager.set(player.getUniqueId(), "heal_spell", Duration.ofSeconds(15));
```

#### `set(UUID uuid, String namespace, Duration duration, Runnable onExpire, Executor executor)`

```java
public void set(UUID uuid, String namespace, Duration duration, Runnable onExpire, Executor executor)
```

Beállít egy ideiglenes cooldownot lejárati callback-kel. Ha a callback Bukkit API-t hív, **kötelező** a Scheduler `Executor`-át átadni!

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos azonosítója. |
| `namespace` | `String` | A cooldown névtere. |
| `duration` | `Duration` | A cooldown időtartama. |
| `onExpire` | `Runnable` | A lejáratkor futtatandó kód. |
| `executor` | `Executor` | A Folia szál, amelyen a callback fut (pl. `scheduler.forEntity(player)`). |

**Példa:**
```java
cooldownManager.set(player.getUniqueId(), "combat_tag", Duration.ofSeconds(10),
    () -> {
        player.sendMessage("<green>Kikerültél a harcból!");
    },
    scheduler.forEntity(player) // KÖTELEZŐ Folia szálváltás!
);
```

---

### Perzisztens (Adatbázis) Cooldownok

#### `setPersistent(UUID uuid, String namespace, Duration duration)`

```java
public CompletableFuture<Void> setPersistent(UUID uuid, String namespace, Duration duration)
```

Beállít egy perzisztens cooldownot (memória + adatbázis). Túléli a szerver újraindítását.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos azonosítója. |
| `namespace` | `String` | A cooldown névtere. |
| `duration` | `Duration` | A cooldown időtartama. |
| **Visszatérés** | `CompletableFuture<Void>` | Az aszinkron SQL mentés befejezése. |

**Példa:**
```java
cooldownManager.setPersistent(player.getUniqueId(), "daily_reward", Duration.ofHours(24)).join();
```

#### `loadPersistent(UUID uuid)`

```java
public CompletableFuture<Void> loadPersistent(UUID uuid)
```

Betölti a játékos összes perzisztens cooldownját az adatbázisból a memóriába. Belépéskor (`AsyncPlayerPreLoginEvent`) kell meghívni.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos azonosítója. |
| **Visszatérés** | `CompletableFuture<Void>` | A betöltés befejezése. |

**Példa:**
```java
cooldownManager.loadPersistent(uuid).join();
```

#### `invalidatePersistent(UUID uuid)`

```java
public void invalidatePersistent(UUID uuid)
```

Eltávolítja a játékos összes perzisztens cooldownját a memóriából (az adatbázisból nem!).

---

### Lekérdezés

#### `isActive(UUID uuid, String namespace)`

```java
public boolean isActive(UUID uuid, String namespace)
```

Ellenőrzi, hogy a cooldown aktív-e (O(1) memória hozzáférés). Ha egy perzisztens cooldown már lejárt, automatikusan kitakarítja.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos azonosítója. |
| `namespace` | `String` | A cooldown névtere. |
| **Visszatérés** | `boolean` | `true` ha aktív, `false` ha nem. |

**Példa:**
```java
if (cooldownManager.isActive(player.getUniqueId(), "heal_spell")) {
    String left = cooldownManager.getFormattedRemaining(player.getUniqueId(), "heal_spell", timeFormat);
    player.sendMessage("Még várnod kell: " + left);
    return;
}
```

#### `getRemaining(UUID uuid, String namespace)`

```java
public Duration getRemaining(UUID uuid, String namespace)
```

Visszaadja a hátralévő időt `Duration` objektumként. Ha nem aktív, `Duration.ZERO`.

#### `getFormattedRemaining(UUID uuid, String namespace, TimeFormat format)`

```java
public String getFormattedRemaining(UUID uuid, String namespace, TimeFormat format)
```

Visszaadja a hátralévő időt formázott stringként (pl. `"5p 30mp"`).

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `uuid` | `UUID` | A játékos azonosítója. |
| `namespace` | `String` | A cooldown névtere. |
| `format` | `TimeFormat` | Az időformátum (lásd [UTILS_API.md](UTILS_API.md)). |
| **Visszatérés** | `String` | A formázott hátralévő idő. |

---

### Törlés és Kitakarítás

#### `remove(UUID uuid, String namespace)`

```java
public void remove(UUID uuid, String namespace)
```

Törli a cooldownot (memória + adatbázis + leállítja az expire taskot).

**Példa:**
```java
cooldownManager.remove(player.getUniqueId(), "daily_reward");
```

#### `clearOnQuit(UUID uuid)`

```java
public void clearOnQuit(UUID uuid)
```

Kitakarítja a játékos összes cooldownját a memóriából és leállítja a folyamatban lévő expire taskokat. **Kötelező meghívni a `PlayerQuitEvent`-ben!**

**Példa:**
```java
@EventHandler
public void onQuit(PlayerQuitEvent event) {
    cooldownManager.clearOnQuit(event.getPlayer().getUniqueId());
}
```

---

## 3. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Cooldown tárolás** | `private Map<UUID, Long> cooldowns = new HashMap<>();` | `cooldownManager.set(uuid, "namespace", duration);` |
| **Idő ellenőrzés** | `if (System.currentTimeMillis() - map.get(uuid) < 15000)` | `if (cooldownManager.isActive(uuid, "namespace"))` |
| **Hátralévő idő** | Kézi `long left = (cd - System.currentTimeMillis()) / 1000;` | `cooldownManager.getFormattedRemaining(uuid, "ns", format);` |
| **Napi jutalom** | SQL lekérdezés minden gombnyomásnál. | `isActive()` (O(1)), majd `setPersistent()`. |
| **Lejárati task** | `Bukkit.getScheduler().runTaskLater(...)` | `cooldownManager.set(uuid, "ns", dur, () -> ..., executor)` |
| **Kilépés kezelés** | Semmi, vagy manuális Map takarítás. | `cooldownManager.clearOnQuit(uuid);` |

---

## 4. Összefoglaló AI Szabály

**AI Directive:** Ha egy funkcióhoz visszaszámlálót kérnek, az ellenőrzéshez a `cooldownManager.isActive(uuid, namespace)` metódust használd. A hátralévő idő kiírásához használd a `getFormattedRemaining`-et egy `TimeFormat` objektummal. Alkalmazásakor hívd meg a `setPersistent(uuid, namespace, Duration...)` metódust. Szigorúan tilos kézi `HashMap`-et, `System.currentTimeMillis()` matekot, vagy natív `BukkitRunnable` alapú időzítőt írni!
