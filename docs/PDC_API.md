# FlariumAPI - Persistent Data Container (PDC) API Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `PDCManager` osztályának használatát írja le, ami a Paper 1.21 Data Component API-ra (régi NBT) épül.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, egyedi adatok itemekhez, blokkokhoz vagy entitásokhoz rendelésére **KIZÁRÓLAG** az itt leírt `PDCManager` módszereket használhatod. Szigorúan tilos az NMS (`net.minecraft.server`) és a Reflection használata! Tilos a `new NamespacedKey()` közvetlen példányosítása a kódban!

## 1. Architekturális Szabályok (Szigorú)

1. **Univerzális Wrapper (Kényelem):** A `PDCManager` automatikusan elvégzi a "piszkos munkát". Itemeknél automatikusan lekéri és visszaírja az `ItemMeta`-t, blokkoknál a `TileState` cast-olást és az `update()` hívást is elintézi.
2. **KeyRegistry (Teljesítmény):** Minden kulcs a `KeyRegistry.getKey()` metóduson keresztül jön létre a háttérben, ami cache-eli a kulcsokat a memóriahatékonyság érdekében.
3. **Custom Data Types (Típusbiztonság):** Tilos UUID-t vagy Location-t Stringként lementeni, majd kézzel parse-olni! Az API tartalmaz beépített `UUIDDataType`, `LocationDataType` és `JsonDataType<T>` osztályokat erre a célra.

---

## 2. Inicializálás a Pluginban

A `PDCManager`-t a plugin főosztályában kell példányosítani, és konstruktoron keresztül (IoC) átadni a többi managernek.

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

## 3. Adatok Mentése és Olvasása

### 3.1 Tárgyak (ItemStack)

Az ItemMeta módosítása és visszaírása automatikusan megtörténik (In-place modification).

```java
// Mentés egyedi típussal (UUID)
pdcManager.set(item, "contract_owner", new UUIDDataType(), player.getUniqueId());

// Olvasás
if (pdcManager.has(item, "contract_owner", new UUIDDataType())) {
    UUID owner = pdcManager.get(item, "contract_owner", new UUIDDataType());
}

```

### 3.2 Blokkok (Block) - [!] KRITIKUS SZABÁLY [!]

**A Minecraftban NEM lehet bármilyen blokkra adatot menteni!** Csak a `TileState`-et implementáló blokkok (pl. Láda, Tábla, Kemence, Hordó, Spawner, Fejek) rendelkeznek PDC-vel. Sima földre vagy kőre dobott hiba (`ClassCastException`) lesz az eredmény!

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

### 3.3 Entitások (Entity)

Az entitások natívan támogatják a PDC-t.

```java
pdcManager.set(entity, "spawn_time", PersistentDataType.LONG, System.currentTimeMillis());
Long spawnTime = pdcManager.get(entity, "spawn_time", PersistentDataType.LONG);

```

### 3.4 Komplex Objektumok (JSON DataType)

Ha egy komplett Java Recordot vagy osztályt (pl. `ContractData`) akarsz rámenteni egy tárgyra, használd a `JsonDataType`-ot a kézi stringgé alakítás helyett!

```java
public record ContractData(String tier, int amount) {}

// Mentés (A JsonDataType automatikusan szerializál)
pdcManager.set(item, "contract_data", new JsonDataType<>(ContractData.class), new ContractData("RARE", 10));

// Olvasás
ContractData data = pdcManager.get(item, "contract_data", new JsonDataType<>(ContractData.class));

```

---

## 4. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Item NBT írás** | `ItemMeta m = i.getItemMeta(); m.getPersistentDataContainer().set(new NamespacedKey(...)); i.setItemMeta(m);` | `pdcManager.set(item, "id", type, value);` |
| **Block NBT** | Címkézés sima `Material.STONE` blokkon. | `if (!(block.getState() instanceof TileState)) return;` ellenőrzés előtte! |
| **Block Frissítés** | `(TileState) block.getState(); ... state.update();` (Kézi castolás és update) | Csak használd a `pdcManager.set(block, ...)`-ot, elintézi az update-et! |
| **Kulcsok** | `new NamespacedKey(plugin, "my_key")` példányosítása mindenhol. | `pdcManager` hívása, ami a `KeyRegistry`-t használja. |
| **UUID tárolás** | `pdcManager.set(item, "uuid", PersistentDataType.STRING, uuid.toString());` | `pdcManager.set(item, "uuid", new UUIDDataType(), uuid);` |
| **Komplex adat** | Record mentése GSON-nal stringgé alakítva. | `new JsonDataType<>(MyClass.class)` használata. |
| **NMS / Reflection** | `net.minecraft.server...` kódok hívása. | A Paper API PDC rendszerének használata tilos NMS-re cserélni! |

## 5. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha egy funkcióhoz rejtett azonosítót (NBT) kell egy tárgyhoz adnod, a `pdcManager.set(item, "namespace", type, value)` metódust használd. Soha ne írj `new NamespacedKey()`-t és ne végezz kézi `setItemMeta` hívásokat! Ha blokkra mentesz, először **mindig** ellenőrizd, hogy a blokk állapota `TileState`-e, különben a kód elszáll! Bonyolult objektumokhoz használd a `JsonDataType`-ot, UUID-hoz a `UUIDDataType`-ot. Az `ItemBuilder` is rendelkezik `.addPersistentData` metódussal, de az csak és kizárólag `String` értéket fogad el az item építésekor!