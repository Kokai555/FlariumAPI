# FlariumAPI - Utility API Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `NumberUtil`, `TimeUtil` és `TimeFormat` osztályainak használatát írja le.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, számok és idő formázására **KIZÁRÓLAG** ezeket az osztályokat használd. Szigorúan tilos kézi String összefűzéssel, matematikával vagy if-else láncolattal formázni az időt vagy a nagy számokat!

## 1. Számok Formázása (`NumberUtil`)

### Ezres elválasztók (10,683,918)
Ha egy nagy számot olvasható formátumban kell megjeleníteni (pl. gazdasági rendszerben), használd a `formatCommas` metódust. Támogatja a `long` és a `double` típusokat is!

```java
long money = 10683918;
String formattedLong = NumberUtil.formatCommas(money); // Eredmény: "10,683,918"

double exactMoney = 1500.50;
String formattedDouble = NumberUtil.formatCommas(exactMoney); // Eredmény: "1,500.5" (vagy lokalizációtól függő)

```

### Rövidített formátum (10.24k)

Ha a szám túl nagy, és röviden szeretnéd megjeleníteni (pl. DPS metríkák, leaderboardok), használd a `formatShort` metódust.

```java
double damage = 10242;
String formatted = NumberUtil.formatShort(damage); // Eredmény: "10.24k"

double bigNumber = 1500000;
String formattedBig = NumberUtil.formatShort(bigNumber); // Eredmény: "1.50M"

```

---

## 2. Idő Formázása (`TimeUtil` & `TimeFormat`)

Az idő formázása teljesen konfigurálható a `messages.yml` (vagy `config.yml`) fájlból a `TimeFormat` rekord segítségével.

### A YAML felépítése:

```yaml
time-format:
  second: "mp"
  minute: "p"
  hour: "ó"
  day: "nap"

```

### Inicializálás a Plugin Főosztályában (IoC):

A `TimeFormat` rekordot a plugin indulásakor be kell tölteni és át kell adni a managereknek. Ha a config szekció üres vagy hiányzik, az API automatikusan az angol ("s", "m", "h", "d") alapértelmezett értékeket használja.

```java
// Nem kell null check, a load() lekezeli!
TimeFormat timeFormat = TimeFormat.load(getConfig().getConfigurationSection("time-format"));

```

### Formázás Java Kódból:

A `TimeUtil.formatDuration` metódus **másodperceket** (`long`) vár paraméterként. A felesleges nullákat (pl. 0 nap) automatikusan kihagyja.
Ha egy `java.time.Duration` objektumod van (pl. a `CooldownManager`-ből), használd a `.getSeconds()` metódust!

```java
long seconds = 3753; // 1 óra, 2 perc, 33 másodperc
String formatted = TimeUtil.formatDuration(seconds, timeFormat); // Eredmény: "1ó 2p 33mp"

// Integráció a Flarium Duration alapú rendszereivel:
Duration cooldown = Duration.ofMinutes(5).plusSeconds(30);
String cdFormatted = TimeUtil.formatDuration(cooldown.getSeconds(), timeFormat); // Eredmény: "5p 30mp"

```

---

## 3. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Nagy számok** | `String formatted = String.format("%,d", money);` | `NumberUtil.formatCommas(money);` (Működik double-re is!) |
| **Rövidített számok** | Kézi `if (number > 1000) return number / 1000 + "k";` | `NumberUtil.formatShort(number);` |
| **Idő formázás** | `if (sec > 3600) { int h = sec/3600; ... }` | `TimeUtil.formatDuration(sec, timeFormat);` |
| **Idő stringek** | `String.format("%dh %dm", h, m);` (Hardcoded) | `time-format:` YAML blokk és `TimeFormat` record használata. |
| **Duration formázás** | `formatDuration(duration.toMillis() / 1000, tf)` | `formatDuration(duration.getSeconds(), timeFormat)` |

## 4. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha a feladat az, hogy "írd ki a játékos pénzét a chatre", a `NumberUtil.formatCommas()`-t vagy a `formatShort()`-ot használd a szám méretétől és a kontextustól függően. Ha cooldown vagy bármilyen lejárati idő maradt hátra, a `TimeUtil.formatDuration(seconds, timeFormat)` metódust hívd meg! Ha a bemeneti adatod egy `java.time.Duration`, használd a `.getSeconds()` metódust az átalakításhoz. A `TimeFormat` objektumot mindig konstruktoron keresztül (IoC) kapja meg az adott Manager osztály, soha ne hardcode-olj időstringeket (pl. "m" vagy "mp") a Java kódba!