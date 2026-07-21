# FlariumAPI — Persistent Data Container (PDC) API

> **Package:** `com.flarium.api.data.pdc`
> **Core Classes:** `PDCManager`, `KeyRegistry`, `UUIDDataType`, `LocationDataType`, `JsonDataType`

A `PDCManager` a Paper 1.21 Data Component API-ra (régi NBT) épülő univerzális wrapper. Automatikusan elvégzi az `ItemMeta` és `TileState` update-eket.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, egyedi adatok itemekhez, blokkokhoz vagy entitásokhoz rendelésére **KIZÁRÓLAG** az itt leírt `PDCManager` módszereket használhatja.

| Tilos | Helyette |
|-------|----------|
| NMS (`net.minecraft.server`) | `pdcManager.set(item, "key", type, value)` |
| `new NamespacedKey()` közvetlenül | A `PDCManager` a `KeyRegistry`-t használja |
| Reflection | Paper API PDC rendszer |

---

## 1. Architekturális Szabályok

1. **Univerzális Wrapper** — A `PDCManager` automatikusan elvégzi a "piszkos munkát". Itemeknél lekéri és visszaírja az `ItemMeta`-t, blokkoknál a `TileState` cast-olást és az `update()` hívást is elintézi.
2. **KeyRegistry (Teljesítmény)** — Minden kulcs a `KeyRegistry.getKey()` metóduson keresztül jön létre, ami cache-eli a kulcsokat.
3. **Custom Data Types** — Az API tartalmaz beépített `UUIDDataType`, `LocationDataType` és `JsonDataType<T>` osztályokat. Tilos UUID-t vagy Location-t Stringként lementeni és kézzel parse-olni!

---

## 2. API Referencia — `PDCManager`

### Konstruktor

```java
public PDCManager(Plugin plugin)
```

---

### Tárgyak (ItemStack)

#### `set(ItemStack item, String key, PersistentDataType<P, C> type, C value)`

```java
public <P, C> void set(ItemStack item, String key, PersistentDataType<P, C> type, C value)
```

Beállít egy PDC értéket az itemen. Az `ItemMeta` módosítása és visszaírása automatikusan megtörténik.

#### `get(ItemStack item, String key, PersistentDataType<P, C> type)`

```java
public <P, C> C get(ItemStack item, String key, PersistentDataType<P, C> type)
```

Visszaadja a PDC értéket, vagy `null`-t ha nincs.

#### `has(ItemStack item, String key, PersistentDataType<P, C> type)`

```java
public <P, C> boolean has(ItemStack item, String key, PersistentDataType<P, C> type)
```

Ellenőrzi, hogy a PDC érték létezik-e.

#### `remove(ItemStack item, String key)`

```java
public void remove(ItemStack item, String key)
```

Törli a PDC értéket.

**Példa:**
```java
// Mentés egyedi típussal (UUID)
pdcManager.set(item, "contract_owner", new UUIDDataType(), player.getUniqueId());

// Olvasás
if (pdcManager.has(item, "contract_owner", new UUIDDataType())) {
    UUID owner = pdcManager.get(item, "contract_owner", new UUIDDataType());
}
```

---

### Blokkok (Block)

#### `set(Block block, String key, PersistentDataType<P, C> type, C value)`

```java
public <P, C> void set(Block block, String key, PersistentDataType<P, C> type, C value)
```

Beállít egy PDC értéket a blokkon. A `TileState.update()` automatikusan lefut!

> ⚠️ **KRITIKUS SZABÁLY:** Csak a `TileState`-et implementáló blokkok (pl. Láda, Tábla, Kemence, Hordó, Spawner, Fejek) rendelkeznek PDC-vel! Sima földre vagy kőre `ClassCastException` lesz az eredmény!

**Példa:**
```java
// Guard clause a TileState ellenőrzésére!
if (!(block.getState() instanceof org.bukkit.block.TileState)) {
    return; // Ezt a blokkot nem lehet felcímkézni!
}

// Location mentése egy ládára (A TileState.update() automatikusan lefut!)
pdcManager.set(block, "linked_location", new LocationDataType(), targetLoc);

// Törlés
pdcManager.remove(block, "linked_location");
```

---

### Entitások (Entity) és PersistentDataHolder

#### `set(PersistentDataHolder holder, String key, PersistentDataType<P, C> type, C value)`

```java
public <P, C> void set(PersistentDataHolder holder, String key, PersistentDataType<P, C> type, C value)
```

Beállít egy PDC értéket bármely `PersistentDataHolder`-en (entitások, `ItemMeta`, `TileState`).

**Példa:**
```java
pdcManager.set(entity, "spawn_time", PersistentDataType.LONG, System.currentTimeMillis());
Long spawnTime = pdcManager.get(entity, "spawn_time", PersistentDataType.LONG);
```

---

### Komplex Objektumok (JSON DataType)

Ha egy komplett Java Recordot vagy osztályt akarsz rámenteni egy tárgyra, használd a `JsonDataType`-ot:

```java
public record ContractData(String tier, int amount) {}

// Mentés (A JsonDataType automatikusan szerializál)
pdcManager.set(item, "contract_data", new JsonDataType<>(ContractData.class), new ContractData("RARE", 10));

// Olvasás
ContractData data = pdcManager.get(item, "contract_data", new JsonDataType<>(ContractData.class));
```

---

## 3. Elérhető Custom Data Types

| Osztály | Leírás | Példa |
|---------|--------|-------|
| `UUIDDataType` | UUID mentése/olvasása byte tömbként. | `new UUIDDataType()` |
| `LocationDataType` | `org.bukkit.Location` mentése/olvasása. | `new LocationDataType()` |
| `JsonDataType<T>` | Komplex objektumok JSON szerializálása. | `new JsonDataType<>(MyClass.class)` |

---

## 4. Inicializálás a Pluginban

```java
public class MyPlugin extends JavaPlugin {
    private PDCManager pdcManager;

    @Override
    public void onEnable() {
        this.pdcManager = new PDCManager(this);
    }
}
```

---

## 5. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Item NBT írás** | `ItemMeta m = i.getItemMeta(); m.getPersistentDataContainer().set(new NamespacedKey(...)); i.setItemMeta(m);` | `pdcManager.set(item, "id", type, value);` |
| **Block NBT** | Címkézés sima `Material.STONE` blokkon. | `if (!(block.getState() instanceof TileState)) return;` ellenőrzés előtte! |
| **Block Frissítés** | Kézi `(TileState) block.getState(); ... state.update();` | `pdcManager.set(block, ...)` (elintézi az update-et!) |
| **Kulcsok** | `new NamespacedKey(plugin, "my_key")` mindenhol | `pdcManager` hívása (a `KeyRegistry`-t használja) |
| **UUID tárolás** | `pdcManager.set(item, "uuid", PersistentDataType.STRING, uuid.toString());` | `pdcManager.set(item, "uuid", new UUIDDataType(), uuid);` |
| **Komplex adat** | Record mentése GSON-nal stringgé | `new JsonDataType<>(MyClass.class)` |
| **NMS / Reflection** | `net.minecraft.server...` | Paper API PDC rendszer |

---

## 6. Összefoglaló AI Szabály

**AI Directive:** Ha egy funkcióhoz rejtett azonosítót (NBT) kell egy tárgyhoz adnod, a `pdcManager.set(item, "namespace", type, value)` metódust használd. Soha ne írj `new NamespacedKey()`-t és ne végezz kézi `setItemMeta` hívásokat! Ha blokkra mentesz, először **mindig** ellenőrizd, hogy a blokk állapota `TileState`-e! Bonyolult objektumokhoz használd a `JsonDataType`-ot, UUID-hoz a `UUIDDataType`-ot. Az `ItemBuilder` is rendelkezik `.addPersistentData` metódussal, de az csak és kizárólag `String` értéket fogad el az item építésekor!
