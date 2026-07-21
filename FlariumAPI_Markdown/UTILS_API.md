# FlariumAPI — Utility API

> **Package:** `com.flarium.api.core.util`
> **Core Classes:** `NumberUtil`, `TimeUtil`, `TimeFormat`

A FlariumAPI segédosztályai számok és idő formázására. Szigorúan tilos kézi String összefűzéssel, matematikával vagy if-else láncolattal formázni az időt vagy a nagy számokat!

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, számok és idő formázására **KIZÁRÓLAG** ezeket az osztályokat használd.

| Tilos | Helyette |
|-------|----------|
| `String.format("%,d", money)` | `NumberUtil.formatCommas(money)` |
| `if (sec > 3600) { int h = sec/3600; ... }` | `TimeUtil.formatDuration(sec, timeFormat)` |

---

## 1. `NumberUtil` — Számok Formázása

### `formatCommas(long number)`

```java
public static String formatCommas(long number)
```

Ezres elválasztókkal formáz egy nagy számot (pl. `10,683,918`).

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `number` | `long` | A formázandó szám. |
| **Visszatérés** | `String` | A formázott szám. |

**Példa:**
```java
long money = 10683918;
String formatted = NumberUtil.formatCommas(money); // Eredmény: "10,683,918"
```

### `formatCommas(double number)` — *támogatott a `long` túlterhelésen keresztül*

### `formatShort(double number)`

```java
public static String formatShort(double number)
```

Rövidített formátum (pl. `10.24k`, `1.50M`). Leaderboardokhoz és DPS metrikákhoz ideális.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `number` | `double` | A formázandó szám. |
| **Visszatérés** | `String` | A rövidített szám. |

**Példa:**
```java
double damage = 10242;
String formatted = NumberUtil.formatShort(damage); // Eredmény: "10.24k"

double bigNumber = 1500000;
String formattedBig = NumberUtil.formatShort(bigNumber); // Eredmény: "1.50M"
```

---

## 2. `TimeUtil` — Idő Formázása

### `formatDuration(long seconds, TimeFormat format)`

```java
public static String formatDuration(long seconds, TimeFormat format)
```

Másodperceket formáz olvasható időstringgé. A felesleges nullákat (pl. 0 nap) automatikusan kihagyja.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `seconds` | `long` | A formázandó másodpercek. |
| `format` | `TimeFormat` | Az időformátum (suffixumokkal). |
| **Visszatérés** | `String` | A formázott idő (pl. `"1ó 2p 33mp"`). |

**Példa:**
```java
long seconds = 3753; // 1 óra, 2 perc, 33 másodperc
String formatted = TimeUtil.formatDuration(seconds, timeFormat); // Eredmény: "1ó 2p 33mp"

// Integráció a Flarium Duration alapú rendszereivel:
Duration cooldown = Duration.ofMinutes(5).plusSeconds(30);
String cdFormatted = TimeUtil.formatDuration(cooldown.getSeconds(), timeFormat); // Eredmény: "5p 30mp"
```

---

## 3. `TimeFormat` — Időformátum Rekord

A `TimeFormat` rekord a konfigurálható suffixumokat tartalmazza.

### YAML felépítés

```yaml
time-format:
  second: "mp"
  minute: "p"
  hour: "ó"
  day: "nap"
```

### `TimeFormat.load(ConfigurationSection section)` (statikus)

```java
public static TimeFormat load(ConfigurationSection section)
```

Betölti a `TimeFormat` rekordot a YAML konfigurációból. Ha a config szekció üres vagy hiányzik, automatikusan az angol (`"s"`, `"m"`, `"h"`, `"d"`) alapértelmezett értékeket használja. Nem kell null check!

**Példa:**
```java
TimeFormat timeFormat = TimeFormat.load(getConfig().getConfigurationSection("time-format"));
```

---

## 4. Inicializálás a Plugin Főosztályában (IoC)

A `TimeFormat` objektumot a plugin indulásakor be kell tölteni és át kell adni a managereknek konstruktoron keresztül.

```java
@Override
public void onEnable() {
    TimeFormat timeFormat = TimeFormat.load(getConfig().getConfigurationSection("time-format"));
    this.cooldownManager = new CooldownManager(databaseManager, scheduler, timeFormat);
}
```

---

## 5. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Nagy számok** | `String.format("%,d", money);` | `NumberUtil.formatCommas(money);` |
| **Rövidített számok** | `if (number > 1000) return number / 1000 + "k";` | `NumberUtil.formatShort(number);` |
| **Idő formázás** | `if (sec > 3600) { int h = sec/3600; ... }` | `TimeUtil.formatDuration(sec, timeFormat);` |
| **Idő stringek** | `String.format("%dh %dm", h, m);` (hardcoded) | `time-format:` YAML blokk és `TimeFormat` record |
| **Duration formázás** | `formatDuration(duration.toMillis() / 1000, tf)` | `formatDuration(duration.getSeconds(), timeFormat)` |

---

## 6. Összefoglaló AI Szabály

**AI Directive:** Ha a feladat az, hogy "írd ki a játékos pénzét a chatre", a `NumberUtil.formatCommas()`-t vagy a `formatShort()`-ot használd. Ha cooldown vagy bármilyen lejárati idő maradt hátra, a `TimeUtil.formatDuration(seconds, timeFormat)` metódust hívd meg! Ha a bemeneti adatod egy `java.time.Duration`, használd a `.getSeconds()` metódust. A `TimeFormat` objektumot mindig konstruktoron keresztül (IoC) kapja meg az adott Manager, soha ne hardcode-olj időstringeket a Java kódba!
