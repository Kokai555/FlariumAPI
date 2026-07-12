# FlariumAPI - ItemBuilder & Item API Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `ItemBuilder` osztályának használatát írja le.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, új tárgyak (itemek) létrehozására, meglévők módosítására és YAML-ből való beolvasására **KIZÁRÓLAG** az itt leírt módszereket használhatod. Szigorúan tilos a hagyományos `ItemMeta meta = item.getItemMeta(); meta.setDisplayName("§c...")` struktúra használata!

## 1. Architekturális Szabályok (Szigorú)

1. **Adventure API (MiniMessage):** Az itemek neveinek és lore-jainak (leírásainak) formázásához **kizárólag** MiniMessage formátumot használj. Tilos a `§` vagy a `ChatColor` használata!
2. **Fluent Design (Láncolhatóság):** Az `ItemBuilder` metódusai magával az építő (builder) példánnyal térnek vissza, így a hívások láncolhatók (method chaining). A láncot mindig a `.build()` zárja.
3. **Klónozás:** Az `ItemBuilder` képes egy már meglévő `ItemStack`-et is átvenni konstruktorban, így könnyedén módosíthatsz egy már játékosnál lévő tárgyat anélkül, hogy nulláról újraépítenéd.
4. **Biztonság:** Az API automatikusan megakadályozza, hogy az `amount` 1 alá csökkenjen, és az `unbreakable(true)` hívás esetén magától hozzáadja a `HIDE_ATTRIBUTES` flaget a letisztult kinézetért.

---

## 2. YAML Konfigurációs Formátum

Amikor egy itemet a `config.yml` vagy `menus.yml` fájlba írsz, kövesd ezt a struktúrát. Az `ItemBuilder.fromConfig()` ezt egy az egyben képes beolvasni.

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

# Játékosfejek (Player Head) támogatása
player-head-example:
  material: PLAYER_HEAD
  name: "<white>Egyedi Titkos Fej"
  # VAGY a skull-texture (Base64) VAGY a skull-owner (Játékosnév) használandó!
  skull-texture: "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYz..."
  # skull-owner: "Notch"

```

---

## 3. Item Létrehozása és Módosítása (Java Kód)

### Új tárgy készítése

```java
public ItemStack createSword() {
    return new ItemBuilder(Material.DIAMOND_SWORD)
            .name("<white>Kard of <#6CAAD3>Igazság</#6CAAD3>")
            .lore(List.of("<gray>Legendás fegyver."))
            .customModelData(10001)
            .glow(true)
            .unbreakable(true) // Ez automatikusan hozzáadja a HIDE_ATTRIBUTES-t is!
            .build();
}

```

### Meglévő tárgy klónozása és szerkesztése

Ha van egy tárgy a játékos kezében, és csak egy Lore sort akarsz hozzáadni vagy átnevezni.

```java
ItemStack handItem = player.getInventory().getItemInMainHand();

// Beletöltjük az építőbe a meglévő itemet
ItemStack updatedItem = new ItemBuilder(handItem)
        .name("<red>Véres " + handItem.getType().name())
        .glow(true)
        .build();
        
player.getInventory().setItemInMainHand(updatedItem);

```

### Egyedi Játékosfej (Base64) készítése

```java
public ItemStack createCustomHead() {
    return new ItemBuilder(Material.PLAYER_HEAD)
            .name("<white>Kincsesláda Kulcs")
            .skullTexture("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYz...")
            .build();
}

```

---

## 4. Item Beolvasása Configból

A `fromConfig` metódus automatikusan lekezeli a kötőjeles (pl. `DIAMOND-SWORD`) material neveket, és az összes tulajdonságot betölti.

```java
// config.yml -> "rewards.rare-sword" szekció beolvasása
public ItemStack getRewardItem() {
    ConfigurationSection section = config.getConfigurationSection("rewards.rare-sword");
    return ItemBuilder.fromConfig(section);
}

```

---

## 5. PersistentData (NBT Tag-ek mentése)

Ha egy itemhez egyedi azonosítót (pl. contract ID, tulajdonos) kell rendelni.
**[!] FONTOS SZABÁLY:** Az `ItemBuilder.addPersistentData` metódusa **KIZÁRÓLAG STRING** (szöveg) értéket képes elmenteni! Ha UUID-t vagy számot akarsz beletenni, használd a `String.valueOf()`-ot, vagy ha komplex adat kell, használd az API `PDCManager` osztályát a `.build()` után!

```java
ItemStack contract = new ItemBuilder(Material.PAPER)
    .name("<white>Szerződés")
    .addPersistentData(plugin, "contract_id", "12345") // Csak String!
    .addPersistentData(plugin, "owner_uuid", player.getUniqueId().toString()) 
    .build();

```

---

## 6. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Item név** | `meta.setDisplayName("§cKard");` | `builder.name("<white>Kard");` |
| **Item lore** | `meta.setLore(Arrays.asList("§aElső sor"));` | `builder.lore(List.of("<gray>Első sor"));` |
| **Item létrehozás** | `ItemStack i = new ItemStack(M); ItemMeta m = i.getItemMeta(); ...` | `ItemStack i = new ItemBuilder(Material.M).name("...").build();` |
| **Módosítás** | `ItemMeta m = hand.getItemMeta(); m.setGlow...` | `new ItemBuilder(handItem).glow(true).build();` |
| **Config beolvasás** | 20 soros if-else láncolat a config értékeinek manuális ellenőrzésére. | `ItemBuilder.fromConfig(config.getConfigurationSection("path"));` |
| **Játékosfej (Skull)** | `SkullMeta meta = (SkullMeta) item.getItemMeta(); meta.setOwningPlayer(...);` | `builder.skullOwner("Notch");` VAGY `builder.skullTexture("base64...");` |
| **Adattárolás (PDC)** | `addPersistentData(plugin, "coins", 50);` (Hibás típus) | `addPersistentData(plugin, "coins", "50");` (Csak String!) |

## 7. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha egy funkcióhoz custom itemet kell létrehoznod vagy adnod a játékosnak, **KÖTELEZŐ** az `ItemBuilder`-t használnod láncolt (fluent) formában. Az item nevét és lore-ját kizárólag MiniMessage formátummal formázhatod. Ha az item a configból jön, használd az `ItemBuilder.fromConfig()` statikus metódust. Ha NBT (PDC) adatot teszel a tárgyra a builderrel, azt szigorúan `String` típusként tedd! A játékosfejekhez a `skullTexture` vagy `skullOwner` metódusokat használd, a manuális `ProfileProperty` állítgatás tilos!