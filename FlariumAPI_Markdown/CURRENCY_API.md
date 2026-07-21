# FlariumAPI — Currency API

> **Package:** `com.flarium.api.feature.currency`
> **Core Class:** `CurrencyManager`

A `CurrencyManager` egy dinamikus, YAML-vezérelt valuta rendszer. A szerver gazdasága configból konfigurálható — a pluginok nem közvetlenül hívják a Vault vagy PlayerPoints API-ját, hanem ezen a manageren keresztül.

---

## 🤖 AI Direktíva

A FlariumAPI-ra épülő pluginokban fizetési tranzakciókhoz **KIZÁRÓLAG** ezt a managert használhatod!

| Tilos | Helyette |
|-------|----------|
| `Vault.getEconomy().has(...)` | `currencyManager.hasEnough(player, "Gem", 100)` |
| `Bukkit.dispatchCommand(...)` kézi parancs | `currencyManager.take(player, "ID", összeg)` |

---

## 1. YAML Konfiguráció

A valutákat a plugin `config.yml` vagy dedikált `currencies.yml` fájljában kell definiálni:

```yaml
currencies:
    Gem:                          # Valuta ID (Java-ban ezzel hivatkozol rá)
        enabled: true
        display-format: "<#9fcc2e>%amount% Gem"
        allow-decimals: false     # true = double, false = kerekített long
        works-offline: false
        options:
            placeholder: "%playerpoints_points%"       # PlaceholderAPI azonosító
            give-command: "points give %player% %amount%"
            take-command: "points take %player% %amount%"
```

---

## 2. API Referencia — `CurrencyManager`

### `loadCurrencies(ConfigurationSection section)`

```java
public void loadCurrencies(ConfigurationSection section)
```

Betölti a valutákat a YAML konfigurációból. Törli a korábbi regisztrációkat.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `section` | `ConfigurationSection` | A `currencies` szekció. |

**Példa:**
```java
currencyManager.loadCurrencies(getConfig().getConfigurationSection("currencies"));
```

---

### `getBalance(Player player, String currencyId)`

```java
public double getBalance(Player player, String currencyId)
```

Lekérdezi a játékos egyenlegét. A rendszer automatikusan parse-olja a PlaceholderAPI kimenetét számmá.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `player` | `Player` | A játékos. |
| `currencyId` | `String` | A valuta ID-ja (pl. `"Gem"`). |
| **Visszatérés** | `double` | Az egyenleg, vagy `0.0` ha hiba van. |

**Példa:**
```java
double balance = currencyManager.getBalance(player, "Gem");
player.sendMessage("Egyenleged: " + balance);
```

---

### `hasEnough(Player player, String currencyId, double amount)`

```java
public boolean hasEnough(Player player, String currencyId, double amount)
```

Ellenőrzi, hogy a játékosnak van-e elég pénze.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `player` | `Player` | A játékos. |
| `currencyId` | `String` | A valuta ID-ja. |
| `amount` | `double` | A szükséges összeg. |
| **Visszatérés** | `boolean` | `true` ha van elég. |

**Példa:**
```java
if (currencyManager.hasEnough(player, "Gem", 500)) {
    currencyManager.take(player, "Gem", 500);
    player.sendMessage("Sikeres vásárlás!");
} else {
    player.sendMessage("Nincs elég Gem-ed!");
}
```

---

### `give(Player player, String currencyId, double amount)`

```java
public void give(Player player, String currencyId, double amount)
```

Jóváír egy összeget a játékosnak. A rendszer automatikusan formázza a számot (`allow-decimals` alapján), kicseréli a `%player%` és `%amount%` változókat, majd futtatja a parancsot a konzolból.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `player` | `Player` | A játékos. |
| `currencyId` | `String` | A valuta ID-ja. |
| `amount` | `double` | A jóváírandó összeg. |

**Példa:**
```java
currencyManager.give(player, "Gem", 100);
```

---

### `take(Player player, String currencyId, double amount)`

```java
public void take(Player player, String currencyId, double amount)
```

Levon egy összeget a játékostól. Ugyanazt a parancsfuttatási mechanizmust használja, mint a `give()`.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `player` | `Player` | A játékos. |
| `currencyId` | `String` | A valuta ID-ja. |
| `amount` | `double` | A levonandó összeg. |

**Példa:**
```java
currencyManager.take(player, "Gem", 500);
```

---

## 3. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Egyenleg ellenőrzés** | `Vault.getEconomy().has(...)` | `currencyManager.hasEnough(player, "Gem", 100)` |
| **Pénz levonás** | Kézi `Bukkit.dispatchCommand(...)` | `currencyManager.take(player, "ID", összeg)` |
| **Valuta kiírása UI-ba** | `player.sendMessage(amount + " Pénz")` | Configból olvasott `Currency#formatDisplay(amount)` |

---

## 4. Összefoglaló AI Szabály

**AI Directive:** Ha fizetési tranzakciót kell implementálnod, először `hasEnough()`-tal ellenőrizd az egyenleget, majd `take()`-kel vond le az árat, vagy `give()`-vel adj jutalmat. Szigorúan tilos közvetlenül a Vault vagy PlayerPoints API-ját hívni!
