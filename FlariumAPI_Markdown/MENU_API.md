# FlariumAPI — Menu API (GUI)

> **Package:** `com.flarium.api.ui.menu`
> **Core Classes:** `AbstractGui`, `AbstractWindow`, `Structure`, `ConfigMenuFactory`

A FlariumAPI modern, állapotvezérelt (State-Driven), objektum-orientált Menu architektúrája. A rétegek: **Item → Gui → Window**. Beépített Spillover (alsó inventory) védelemmel és automatikus biztonsági blokkolással.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, grafikus felületek (GUI) létrehozására **KIZÁRÓLAG** az itt leírt módszereket használhatja.

| Tilos | Helyette |
|-------|----------|
| Bukkit `Inventory` + `InventoryClickEvent` spagetti | `NormalGui` + `NormalWindow` |
| Hardcode-olt slot indexek | `Structure` (rajz) alapú felépítés |

> **Fontos:** A FlariumAPI nem támogatja a megosztott (Shared) GUI-kat. Minden Window megnyitásakor egy **FRISS** Gui példányt kell generálni!

---

## 1. Import Cheat Sheet

| Kategória | Osztályok |
|-----------|-----------|
| **Gui rendszerek** | `NormalGui`, `PaginatedGui`, `Gui` |
| **Ablakok (Windows)** | `NormalWindow`, `AnvilWindow`, `HopperWindow` |
| **Gombok (Items)** | `SimpleItem`, `PageItem`, `TakeableItem` |
| **Rajzoló API** | `Structure`, `Ingredient` |
| **YAML Gyár** | `ConfigMenuFactory` |

---

## 2. Architekturális Szabályok és Biztonság

1. **Életciklus (Item → Gui → Window):** Az adatmodell a `Gui`, amit ráhúzunk egy `Window`-ra. A frissítések diff-alapúak (csak a változást küldi újra).
2. **Automatikus Biztonság:** A `MenuListener` alapértelmezetten blokkol minden exploitot (kattintás, drag, shift-click).
3. **Memóriaszivárgás védelem:** A dinamikus tickereket az API automatikusan leállítja az ablak bezárásakor.
4. **Adatvesztés védelem (Spillover Crash Guard):** Szerverleálláskor a rendszer automatikusan bezárja az összes nyitott ablakot, garantálva, hogy a Spillover menük visszaadják a játékosok eredeti tárgyait.
5. **Structure Validáció:** A `Structure` példányosításakor ellenőrzi a sorok azonos hosszúságát. Az `applyStructure()` hívásakor szigorúan validálja, hogy a rajz nem haladja-e meg a `Gui` kapacitását.

---

## 3. API Referencia

### `Structure`

#### Konstruktor

```java
public Structure(String... lines)
```

Egy menü felépítése deklaratívan történik a rajzok segítségével. Minden sornak azonos hosszúságúnak kell lennie! A `.` és szóköz karakterek üres slotot jelentenek.

**Példa:**
```java
Structure structure = new Structure(
    "# # # # # # # # #",
    "# . . . x . . . #",
    "# # # # # # # # #"
);
```

#### `getSlots(char character)`

```java
public List<Integer> getSlots(char character)
```

Visszaadja az összes slot indexet, ahol a megadott karakter szerepel.

---

### `AbstractGui` / `NormalGui`

#### Konstruktor

```java
public NormalGui(int rows)
```

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `rows` | `int` | A GUI sorainak száma (1-6). |

#### `setItem(int slot, Item item)`

```java
public void setItem(int slot, Item item)
```

Beállítja egy slot tartalmát.

#### `getItem(int slot)`

```java
public Item getItem(int slot)
```

Visszaadja egy slot tartalmát.

#### `applyStructure(Structure structure, Map<Character, Ingredient> ingredients)`

```java
public void applyStructure(Structure structure, Map<Character, Ingredient> ingredients)
```

Felépíti a GUI-t a rajz és a hozzávalók alapján. `IllegalArgumentException`-t dob, ha a structure túl nagy.

**Példa:**
```java
NormalGui gui = new NormalGui(3);

Structure structure = new Structure(
    "# # # # # # # # #",
    "# . . . x . . . #",
    "# # # # # # # # #"
);

Map<Character, Ingredient> ingredients = new HashMap<>();
ingredients.put('#', () -> new SimpleItem(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build()));
ingredients.put('x', () -> new SimpleItem(new ItemBuilder(Material.DIAMOND).name("<green>Vásárlás").build()) {
    @Override
    public void handleClick(ItemClickEvent event) {
        event.getPlayer().sendMessage("Kattintottál!");
        event.getWindow().close();
    }
});

gui.applyStructure(structure, ingredients);
new NormalWindow(player, gui, 27, "<white>Bolt</white>").open();
```

#### `tick()`

```java
public void tick()
```

Felülírható metódus élő frissítéshez. Soha ne példányosíts új objektumot (`new SimpleItem`) a `tick()` metóduson belül! Használd a meglévő item `setItemStack()` metódusát.

---

### `PaginatedGui`

#### Konstruktor

```java
public PaginatedGui(int rows, List<Integer> listSlots)
```

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `rows` | `int` | A GUI sorainak száma. |
| `listSlots` | `List<Integer>` | Azok a slotok, ahová a listaelemek kerülnek. |

#### `setItems(List<Item> items)`

```java
public void setItems(List<Item> items)
```

Betölti a listaelemeket. Automatikusan kezeli az oldalakat. Ha egy rövidebb listát töltesz be és a játékos egy nem létező oldalon áll, automatikusan visszaugrik az utolsó létező oldalra.

#### `nextPage()` / `previousPage()`

```java
public void nextPage()
public void previousPage()
```

Lapozás a következő/előző oldalra.

#### `getMaxPages()`

```java
public int getMaxPages()
```

Visszaadja a maximális oldalszámot.

**Példa:**
```java
List<Integer> listSlots = List.of(10, 11, 12, 13, 14, 15, 16);
PaginatedGui gui = new PaginatedGui(3, listSlots);

gui.setItem(18, new PageItem(new ItemBuilder(Material.ARROW).name("Előző").build(), gui, false));
gui.setItem(26, new PageItem(new ItemBuilder(Material.ARROW).name("Következő").build(), gui, true));

gui.setItems(itemList);
```

---

### `AbstractWindow` / `NormalWindow`

#### Konstruktor

```java
public NormalWindow(Player player, Gui gui, int size, String title)
public NormalWindow(Player player, Gui gui, String title)
```

#### `open()`

```java
public void open()
```

Megnyitja az ablakot a játékosnak.

#### `close()`

```java
public void close()
```

Bezárja az ablakot, leállítja a tickereket és visszaadja a Spillover tárgyakat.

#### `updateContent()`

```java
public void updateContent()
```

Diff-alapú frissítés — csak a változást küldi újra.

#### `startTicking(Duration period)`

```java
public void startTicking(Duration period)
```

Elindítja a `gui.tick()` periodikus hívását a játékos szálán.

---

### `ConfigMenuFactory`

#### `createGui(ConfigurationSection section, Map<String, Consumer<ItemClickEvent>> actions)` (statikus)

```java
public static NormalGui createGui(ConfigurationSection section, Map<String, Consumer<ItemClickEvent>> actions)
```

Felépít egy GUI-t YAML konfigurációból. A lambdákat lokálisan kell átadni, így elkerülhető a "State Sharing".

**Példa:**
```java
Map<String, Consumer<ItemClickEvent>> actions = new HashMap<>();
actions.put("COLLECT", event -> {
    generator.collect(player);
    event.getWindow().close();
});

NormalGui gui = ConfigMenuFactory.createGui(
    plugin.getConfig().getConfigurationSection("generator"), actions
);
new NormalWindow(player, gui, "Generátor").open();
```

---

## 4. Config-alapú Menük (YAML)

```yaml
generator:
  title: "Generátor"
  size: 27
  pattern:
    - "#########"
    - "####C####"
    - "#########"
  items:
    'C':
      material: CHEST
      hide-tooltip: true
      name: "<green>Begyűjtés"
      action: COLLECT
```

> **`hide-tooltip: true`:** Ha ez szerepel egy itemnél, az `ItemBuilder` automatikusan alkalmazza a Paper 1.21-es `ItemFlag.HIDE_TOOLTIP` tulajdonságát. Ismeretlen action string esetén a rendszer nem omlik össze, de sárga Warningot logol!

---

## 5. Dinamikus Frissítés (Tick)

```java
public class TimerGui extends NormalGui {
    private int seconds = 10;

    public TimerGui() { super(3); }

    @Override
    public void tick() {
        seconds--;
        // JÓ MEGOLDÁS (In-place frissítés):
        Item item = getItem(13);
        if (item instanceof SimpleItem simple) {
            simple.setItemStack(new ItemBuilder(Material.CLOCK).name("Idő: " + seconds).build());
        }
    }
}
```

> A `startTicking()` a FlariumAPI Scheduler osztályát használja. Folia szerver esetén ez a játékos dedikált Entitás-szálán fut, Paper szerveren a főszálon.

---

## 6. Üllő (AnvilWindow) és Spillover API

Ha a `Gui` mérete nagyobb, mint a Bukkit Inventory alapmérete (pl. egy Üllőnél 3 helyett 39-es GUI-t adsz meg), az API **automatikusan átveszi az irányítást a játékos alsó inventory-ja felett**.

```java
Gui gui = new NormalGui(39);
gui.setItem(0, new SimpleItem(new ItemBuilder(Material.PAPER).name(" ").build()));

AnvilWindow window = new AnvilWindow(player, gui, "Keresés...");
window.setRenameHandler(text -> {
    // Frissítjük a 3-38 közötti raw slotokat a találatokkal...
    window.updateContent();
});
window.open();
```

> ⚠️ **Biztonsági figyelmeztetés:** Fizikai szerver-összeomlás (OOM, kill -9, JVM crash) esetén a Bukkit leállási folyamata nem fut le, így a Spillover menükben hagyott tárgyak elveszhetnek. Kritikus tárgyakat hosszabb ideig ne tároltass Spillover felületekben!

---

## 7. Kivehető Tárgyak (TakeableItem)

```java
ingredients.put('w', () -> new TakeableItem(new ItemBuilder(Material.DIAMOND_SWORD).build()));
```

A `TakeableItem` automatikusan kezeli a védelmek feloldását és a virtuális rácsból való törlést.

---

## 8. Menü Navigáció (Back Stack)

A menük közötti "Vissza" navigációra a **Callback (Runnable) minta** a hivatalos standard. Tilos a korábbi `Window` példányokat memóriában tárolni — a Vissza gombnak mindig egy lambdát kell kapnia, ami FRISSEN újragenerálja az előző menüt.

```java
public void openSubMenu(Player player, Runnable openPreviousMenu) {
    Gui subGui = new NormalGui(3);

    subGui.setItem(26, new SimpleItem(new ItemBuilder(Material.ARROW).name("Vissza").build()) {
        @Override
        public void handleClick(ItemClickEvent event) {
            if (openPreviousMenu != null) {
                openPreviousMenu.run();
            } else {
                event.getWindow().close();
            }
        }
    });

    new NormalWindow(player, subGui, 27, "Almenü").open();
}

// Használat: openSubMenu(player, () -> openMainMenu(player));
```

---

## 9. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Kivehető tárgyak** | `event.getBukkitEvent().setCancelled(false)` | `new TakeableItem(itemStack)` |
| **GUI frissítés** | `new SimpleItem(...)` a `tick()`-ben | `((SimpleItem) item).setItemStack(újItemStack);` |
| **Config Menük** | `layouts.put("menu", gui)` (Singleton) | `ConfigMenuFactory.createGui()` minden nyitáskor |
| **Rajz (Structure)** | Eltérő sorhosszúság | Szigorú, egyforma hosszú sorok |
| **Lapozós logika** | Kézi for-ciklusok | `PaginatedGui` + `.setItems(List<Item>)` |
| **Vissza Gomb** | `Window previous` tárolása | `Runnable previous` callback tárolása |

---

## 10. Összefoglaló AI Szabály

**AI Directive:** Ha GUI-t kell készítened, használj `NormalGui`-t vagy `PaginatedGui`-t, építsd fel a `Structure` API-val, és nyisd meg egy `NormalWindow`-ban. Tilos a Bukkit `InventoryClickEvent` manuális kezelése és a hardcode-olt slot indexek! Dinamikus frissítéshez írd felül a `tick()` metódust és használd a `setItemStack()`-et (sose `new SimpleItem`-et a tick-ben). Config-alapú menükhöz használd a `ConfigMenuFactory.createGui()`-t. Vissza navigációhoz mindig `Runnable` callback-et használj!
