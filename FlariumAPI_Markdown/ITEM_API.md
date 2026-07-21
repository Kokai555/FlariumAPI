# FlariumAPI — ItemBuilder & Item API

> **Package:** `com.flarium.api.ui.item`
> **Core Class:** `ItemBuilder`

Az `ItemBuilder` egy fluent (láncolható) API itemek építéséhez, módosításához és YAML-ből való beolvasásához.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, új tárgyak létrehozására és meglévők módosítására **KIZÁRÓLAG** az itt leírt módszereket használhatja.

| Tilos | Helyette |
|-------|----------|
| `ItemMeta meta = item.getItemMeta(); meta.setDisplayName("§c...")` | `new ItemBuilder(M).name("<white>...").build()` |
| `§` vagy `ChatColor` | MiniMessage formátum (`<white>`, `<#hex>`) |

---

## 1. Architekturális Szabályok

1. **Adventure API (MiniMessage)** — Az itemek neveinek és lore-jainak formázásához **kizárólag** MiniMessage formátumot használj.
2. **Fluent Design** — Az `ItemBuilder` metódusai magával a builder példánnyal térnek vissza (method chaining). A láncot mindig a `.build()` zárja.
3. **Klónozás** — Az `ItemBuilder` képes egy már meglévő `ItemStack`-et is átvenni konstruktorban.
4. **Biztonság** — Az API automatikusan megakadályozza, hogy az `amount` 1 alá csökkenjen, és az `unbreakable(true)` hívás esetén magától hozzáadja a `HIDE_ATTRIBUTES` flaget.

---

## 2. API Referencia — `ItemBuilder`

### Konstruktorok

```java
public ItemBuilder(Material material)
public ItemBuilder(ItemStack itemStack)
```

A második konstruktor klónozza a megadott itemet, így módosíthatod anélkül, hogy nulláról építenéd újra.

---

### `name(String miniMessage)`

```java
public ItemBuilder name(String miniMessage)
```

Beállítja az item nevét MiniMessage formátumban.

**Példa:**
```java
.name("<white>Kard of <#6CAAD3>Igazság</#6CAAD3>")
```

### `lore(List<String> miniMessages)`

```java
public ItemBuilder lore(List<String> miniMessages)
```

Beállítja az item lore-ját (leírását) MiniMessage formátumban.

**Példa:**
```java
.lore(List.of("<gray>Legendás fegyver.", "<dark_gray>Érték: <#6CAAD3>1000 érme"))
```

### `amount(int amount)`

```java
public ItemBuilder amount(int amount)
```

Beállítja az item mennyiségét (minimum 1).

### `customModelData(int data)`

```java
public ItemBuilder customModelData(int data)
```

Beállítja a Custom Model Data-t (erőforráscsomag textúrákhoz).

### `glow(boolean glow)`

```java
public ItemBuilder glow(boolean glow)
```

Ha `true`, hozzáad egy láthatatlan enchantmentet és elrejti azt (glow effektus).

### `hideTooltip(boolean hide)`

```java
public ItemBuilder hideTooltip(boolean hide)
```

Paper 1.21+: Elrejti a teljes tooltip-et (fekete információs doboz). Ideális menü-üvegekhez és dekorációs elemekhez.

### `color(Color color)`

```java
public ItemBuilder color(Color color)
```

Beállítja a bőrpáncél vagy potion színét. Csak `LeatherArmorMeta` és `PotionMeta` esetén működik.

### `unbreakable(boolean unbreakable)`

```java
public ItemBuilder unbreakable(boolean unbreakable)
```

Beállítja az item törhetetlenségét. Ha `true`, automatikusan hozzáadja a `HIDE_ATTRIBUTES` flaget.

### `addPersistentData(Plugin plugin, String key, String value)`

```java
public ItemBuilder addPersistentData(Plugin plugin, String key, String value)
```

Hozzáad egy NBT/PDC adatot az itemhez. **KIZÁRÓLAG STRING értéket fogad el!** Ha UUID-t vagy számot akarsz, használd a `String.valueOf()`-ot, vagy a `PDCManager`-t a `.build()` után.

**Példa:**
```java
.addPersistentData(plugin, "contract_id", "12345")
.addPersistentData(plugin, "owner_uuid", player.getUniqueId().toString())
```

### `skullTexture(String base64Texture)`

```java
public ItemBuilder skullTexture(String base64Texture)
```

Beállítja a játékosfej textúráját Base64 stringgel. Csak `PLAYER_HEAD` material esetén működik.

### `skullOwner(String playerName)`

```java
public ItemBuilder skullOwner(String playerName)
```

Beállítja a játékosfej tulajdonosát név alapján. Csak `PLAYER_HEAD` material esetén működik.

### `enchant(Enchantment enchantment, int level)`

```java
public ItemBuilder enchant(Enchantment enchantment, int level)
```

Hozzáad egy enchantmentet az itemhez.

### `flag(ItemFlag... flags)`

```java
public ItemBuilder flag(ItemFlag... flags)
```

Hozzáad ItemFlag-eket az itemhez.

### `build()`

```java
public ItemStack build()
```

Lezárja a láncot és visszaadja a kész `ItemStack`-et.

---

### `fromConfig(ConfigurationSection section)` (statikus)

```java
public static ItemStack fromConfig(ConfigurationSection section)
```

Beolvas egy itemet a YAML konfigurációból. Automatikusan lekezeli a kötőjeles material neveket és az összes tulajdonságot.

---

## 3. YAML Konfigurációs Formátum

```yaml
example-item:
  material: DIAMOND_SWORD
  amount: 1
  name: "<white>Kard of <#6CAAD3>Igazság</#6CAAD3>"
  lore:
    - "<gray>Egy legendás fegyver."
    - "<dark_gray>Érték: <#6CAAD3>1000 érme"
  custom-model-data: 10001
  glow: true
  unbreakable: false
  hide-tooltip: false
  color: "#FF0000"            # Csak bőrpáncél/potion esetén
  enchants:                   # Opcionális
    - "sharpness:5"
  flags:                      # Opcionális
    - HIDE_ENCHANTS

player-head-example:
  material: PLAYER_HEAD
  name: "<white>Egyedi Titkos Fej"
  skull-texture: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6..."
  # VAGY:
  # skull-owner: "Notch"
```

---

## 4. Gyakorlati Példák

### Új tárgy készítése

```java
public ItemStack createSword() {
    return new ItemBuilder(Material.DIAMOND_SWORD)
            .name("<white>Kard of <#6CAAD3>Igazság</#6CAAD3>")
            .lore(List.of("<gray>Legendás fegyver."))
            .customModelData(10001)
            .glow(true)
            .unbreakable(true)
            .build();
}
```

### Meglévő tárgy klónozása és szerkesztése

```java
ItemStack handItem = player.getInventory().getItemInMainHand();

ItemStack updatedItem = new ItemBuilder(handItem)
        .name("<red>Véres " + handItem.getType().name())
        .glow(true)
        .build();

player.getInventory().setItemInMainHand(updatedItem);
```

### Egyedi Játékosfej (Base64)

```java
public ItemStack createCustomHead() {
    return new ItemBuilder(Material.PLAYER_HEAD)
            .name("<white>Kincsesláda Kulcs")
            .skullTexture("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6...")
            .build();
}
```

### Item beolvasása Configból

```java
public ItemStack getRewardItem() {
    ConfigurationSection section = config.getConfigurationSection("rewards.rare-sword");
    return ItemBuilder.fromConfig(section);
}
```

---

## 5. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Item név** | `meta.setDisplayName("§cKard");` | `builder.name("<white>Kard");` |
| **Item lore** | `meta.setLore(Arrays.asList("§aElső sor"));` | `builder.lore(List.of("<gray>Első sor"));` |
| **Item létrehozás** | `ItemStack i = new ItemStack(M); ItemMeta m = i.getItemMeta(); ...` | `ItemStack i = new ItemBuilder(Material.M).name("...").build();` |
| **Módosítás** | `ItemMeta m = hand.getItemMeta(); m.setGlow...` | `new ItemBuilder(handItem).glow(true).build();` |
| **Config beolvasás** | 20 soros if-else láncolat | `ItemBuilder.fromConfig(config.getConfigurationSection("path"));` |
| **Játékosfej** | `SkullMeta meta = (SkullMeta) item.getItemMeta(); meta.setOwningPlayer(...);` | `builder.skullOwner("Notch");` vagy `builder.skullTexture("base64...");` |
| **Adattárolás (PDC)** | `addPersistentData(plugin, "coins", 50);` (hibás típus) | `addPersistentData(plugin, "coins", "50");` (csak String!) |

---

## 6. Összefoglaló AI Szabály

**AI Directive:** Ha egy funkcióhoz custom itemet kell létrehoznod, **KÖTELEZŐ** az `ItemBuilder`-t használnod láncolt (fluent) formában. Az item nevét és lore-ját kizárólag MiniMessage formátummal formázhatod. Ha az item a configból jön, használd az `ItemBuilder.fromConfig()` statikus metódust. Ha NBT (PDC) adatot teszel a tárgyra a builderrel, azt szigorúan `String` típusként tedd! A játékosfejekhez a `skullTexture` vagy `skullOwner` metódusokat használd!
