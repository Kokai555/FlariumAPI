# FlariumAPI - Menu API (GUI) Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI modern, állapotvezérelt (State-Driven), objektum-orientált Menu architektúrájának használatát írja le.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, grafikus felületek (GUI) létrehozására **KIZÁRÓLAG** az itt leírt módszereket használhatod. Szigorúan tilos a nyers Bukkit `Inventory`, a `InventoryClickEvent` alapú spagettikód, vagy a hardcode-olt slot indexek (pl. `if (slot == 13)`) használata!

## 1. Import Cheat Sheet (Csomagok)

A kódgenerálásnál mindig ezeket az importokat kell használnod:
* **Gui rendszerek:** `com.flarium.api.ui.menu.gui.NormalGui`, `com.flarium.api.ui.menu.gui.PaginatedGui`, `com.flarium.api.ui.menu.gui.Gui`
* **Ablakok (Windows):** `com.flarium.api.ui.menu.window.NormalWindow`, `com.flarium.api.ui.menu.window.AnvilWindow`
* **Gombok (Items):** `com.flarium.api.ui.menu.item.SimpleItem`, `com.flarium.api.ui.menu.item.PageItem`, `com.flarium.api.ui.menu.item.Item`, `com.flarium.api.ui.menu.item.AbstractItem`
* **Események:** `com.flarium.api.ui.menu.event.ItemClickEvent`
* **Rajzoló API:** `com.flarium.api.ui.menu.structure.Structure`, `com.flarium.api.ui.menu.structure.Ingredient`
* **Tárgyépítő:** `com.flarium.api.ui.item.ItemBuilder`

---

## 2. Architekturális Szabályok (Szigorú)

1. **Életciklus és Rétegek (Item -> Gui -> Window):**
   - **`Item` (Okos Gomb):** A legkisebb elem. Rendelkezik saját kinézettel (`getItemProvider()`) és saját kattintáskezelővel (`handleClick(ItemClickEvent)`).
   - **`Gui` (Virtuális Rács):** Az adatmodell a memóriában (pl. `NormalGui`, `PaginatedGui`). Itt tároljuk az `Item`-eket.
   - **`Window` (Megjelenítő):** Ez a Bukkit-híd (`NormalWindow`, `AnvilWindow`). Ez kezeli a nyers Inventory-t, és a "Diff" (különbség) alapú `updateContent()` segítségével villogásmentesen frissíti a képernyőt.
2. **Structure API (Deklaratív Tervezés):** A menük kinézetét string-alapú rajzzal (`Structure`) és összetevőkkel (`Ingredient`) építjük.
3. **Automatikus Biztonság (Default Cancel):** A `MenuListener` automatikusan blokkol minden exploitot (kattintás, drag, shift-click).
4. **Memóriaszivárgás védelem:** A dinamikus tickereket és eseményeket az API automatikusan leállítja az ablak bezárásakor.

---

## 3. A Structure API (Statikus GUI Tervezése)

Egy menü felépítése deklaratívan történik a rajzok (Structure) segítségével.

```java
NormalGui gui = new NormalGui(3); // 3 sor (27 slot)

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

---

## 4. Lapozós Lista (PaginatedGui)

Listákhoz (pl. top játékosok, shop elemek) kötelező a `PaginatedGui` használata.

```java
List<Integer> listSlots = List.of(10, 11, 12, 13, 14, 15, 16);
PaginatedGui gui = new PaginatedGui(3, listSlots);

// Lapozó gombok beállítása
gui.setItem(18, new PageItem(new ItemBuilder(Material.ARROW).name("Előző").build(), gui, false));
gui.setItem(26, new PageItem(new ItemBuilder(Material.ARROW).name("Következő").build(), gui, true));

// Dinamikus Elemek betöltése
List<Item> items = new ArrayList<>();
for (String name : names) {
    items.add(new SimpleItem(new ItemBuilder(Material.PAPER).name(name).build()));
}
gui.setItems(items);

new NormalWindow(player, gui, 27, "<white>Játékos Lista</white>").open();

```

---

## 5. Dinamikus Frissítés (Tick Rendszer)

Élő frissítéshez (pl. visszaszámláló) írd felül a `Gui.tick()` metódust, majd hívd meg a `startTicking()`-et az ablakon.

```java
public class TimerGui extends NormalGui {
    private int seconds = 10;
    
    public TimerGui() { super(3); }

    @Override
    public void tick() {
        seconds--;
        setItem(13, new SimpleItem(new ItemBuilder(Material.CLOCK).name("Idő: " + seconds).build()));
    }
}

// Használat:
TimerGui gui = new TimerGui();
NormalWindow window = new NormalWindow(player, gui, 27, "<white>Időzítő</white>");
window.open();
window.startTicking(Duration.ofSeconds(1)); // Bezáráskor magától leáll!

```

---

## 6. Üllő (AnvilWindow) és Élő Keresés (Spillover API)

Az `AnvilWindow` a FlariumAPI legfejlettebb eszköze. Két zseniális funkciója van:

1. **Beépített Gépelés Figyelő:** A `setRenameHandler` segítségével azonnal reagálhatsz a játékos gépelésére.
2. **Spillover (Alsó inventory átvétel):** Ha a `Gui` mérete nagyobb, mint 3 (az üllő alapmérete), pl. 39, akkor az API automatikusan **átveszi az irányítást a játékos saját alsó inventory-ja felett** (raw slots: 3-38). A nyitásnál elmenti a tárgyakat, zárásnál visszaadja!

**Példa: Élő Tárgykereső (Live Search)**
Szigorúan tilos manuális `PrepareAnvilEvent` Listenert írni! Csak ezt a kódot használd:

```java
// 39 méretű GUI: 3 Anvil slot + 36 játékos alsó slot (Hotbar + Inventory)
Gui gui = new NormalGui(39); 
gui.setItem(0, new SimpleItem(new ItemBuilder(Material.PAPER).name(" ").build())); // Bemenet

AnvilWindow window = new AnvilWindow(player, gui, "Keresés...");

window.setRenameHandler(text -> {
    String filter = text.trim().toLowerCase();
    int slot = 3; // Alsó inventory kezdete!

    for (Material mat : getAvailableMaterials()) {
        if (slot >= 39) break;
        if (filter.isEmpty() || mat.name().toLowerCase().contains(filter)) {
            gui.setItem(slot++, new SimpleItem(new ItemBuilder(mat).build()) {
                @Override
                public void handleClick(ItemClickEvent event) {
                    player.sendMessage("Kiválasztottad: " + mat.name());
                    window.close(); // Automatikusan visszaadja a játékos eredeti tárgyait!
                }
            });
        }
    }

    while (slot < 39) gui.setItem(slot++, null);
    window.updateContent();
});

window.open(); 
window.handleRename(" "); 

```

---

## 7. Kivehető Tárgyak (Biztonsági Felülbírálás)

A FlariumAPI alapértelmezetten minden interakciót blokkol a menükben a maximális biztonság érdekében (`setCancelled(true)`). Ha egy tárgyat (pl. egy napi jutalmat) mégis ki akarsz vetetni a játékossal, a gomb `handleClick` metódusában vissza kell vonnod a tiltást, és törölnöd kell az `Item`-et a virtuális rácsból, nehogy a diff-renderer visszategye.

**Gyors megoldás (Névtelen osztályként egy konkrét menüben):**

```java
ingredients.put('j', () -> new SimpleItem(new ItemBuilder(Material.DIAMOND).name("Jutalom").build()) {
    @Override
    public void handleClick(ItemClickEvent event) {
        // 1. Bukkit szintű tiltás visszavonása
        event.getBukkitEvent().setCancelled(false); 
        // 2. Törlés a virtuális GUI rácsból
        event.getGui().setItem(event.getBukkitEvent().getRawSlot(), null);
    }
});

```

**Elegáns megoldás (Saját kiterjesztett osztály a pluginodban):**

```java
public class TakeableItem extends AbstractItem {
    private final ItemStack itemStack;

    public TakeableItem(ItemStack itemStack) { this.itemStack = itemStack; }

    @Override
    public ItemStack getItemProvider() { return itemStack; }

    @Override
    public void handleClick(ItemClickEvent event) {
        event.getBukkitEvent().setCancelled(false);
        event.getGui().setItem(event.getBukkitEvent().getRawSlot(), null);
    }
}
// Használat: ingredients.put('x', () -> new TakeableItem(rewardStack));

```

---

## 8. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Kattintás ellenőrzés** | `if (event.getSlot() == 13)` a Listenerben. | Saját `Item` osztályok, és `handleClick` felülírása. |
| **Kivehető tárgyak** | Külön listát vezetni arról a listenerben, hogy mi kivehető. | `event.getBukkitEvent().setCancelled(false)` hívása a gomb `handleClick`-jében. |
| **GUI építése** | `inventory.setItem(12, item)` slot matek. | `Structure` és `applyStructure` használata. |
| **Lapozós logika** | Kézi for-ciklusok, Bukkit Inventory clear. | `PaginatedGui` + `.setItems(List<Item>)`. |
| **Menü frissítése** | BukkitRunnable-ből `player.openInventory()`. | `gui.tick()` felülírása és `window.startTicking()`. |
| **Élő keresés / Üllő** | `PrepareAnvilEvent` Listener és HashMap. | 39-es méretű Gui + `window.setRenameHandler()`. |

---

## 9. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha GUI-t kell írnod, mindig a FlariumAPI `Item`, `Gui` és `Window` architektúráját alkalmazd a `com.flarium.api.ui.menu` csomagokból! A kinézetet `Structure` rajzzal építsd fel. A kattintásokat `handleClick` lambdákkal kezeld az `Item`-eken belül, és itt engedélyezd a kivehető tárgyakat (`setCancelled(false)`). Lapozáshoz a `PaginatedGui`-t, élő frissítéshez a `window.startTicking()`-et használd. Ha a játékostól szöveget kérsz be, vagy élő keresőt (live search) írsz, KÖTELEZŐ az `AnvilWindow`-t használnod a `setRenameHandler()`-el és a spillover (39-es méretű Gui) technikával, mindenféle külső Bukkit Listener megírása nélkül!

---

## 10. API Metódus Referencia (Cheat Sheet)

Itt található az összes publikusan hívható funkció rövid leírása.

### Rácsok (Gui) funkciói

* **`setItem(int slot, Item item)`** - Egy konkrét slotra helyez egy Okos Gombot.
* **`getItem(int slot)`** - Lekéri az adott sloton lévő `Item` objektumot.
* **`getSize()`** - Visszaadja a GUI virtuális méretét (slotok száma).
* **`applyStructure(Structure, Map<Character, Ingredient>)`** - Ráhúzza a karakteres rajzot a GUI-ra.
* **`tick()`** - Kiterjeszthető metódus élő frissítésekhez (alapból üres).

### Lapozó (PaginatedGui) extra funkciói

* **`setItems(List<Item> items)`** - Betölti a dinamikus listát és automatikusan generálja az oldalakat.
* **`nextPage()` / `previousPage()**` - Előre vagy hátra lapoz egyet a rácson.

### Ablakok (Window) funkciói

* **`open()` / `close()**` - Megnyitja vagy bezárja az ablakot (és automatikusan kezeli a mentéseket/takarítást).
* **`updateContent()`** - Diff-alapú frissítés (csak a megváltozott itemeket küldi újra, nincs villogás).
* **`startTicking(Duration period)`** - Elindít egy aszinkron tickert a játékos szálán, ami lefutási időközönként hívja a `gui.tick()` és `window.updateContent()` metódusokat.

### Üllő (AnvilWindow) extra funkciói

* **`setRenameHandler(Consumer<String>)`** - Azonnal lefut, amint a játékos gépel valamit (kiváló élő kereséshez).
* **`setConfirmHandler(Consumer<String>)`** - Akkor fut le, ha a játékos a 3. (kimeneti) slotra kattint.

### Események (ItemClickEvent) funkciói

Ezt kapja meg a gombod a `handleClick(ItemClickEvent event)` metódusban:

* **`getPlayer()`** - A kattintó játékos.
* **`getGui()`** - A rács, amin a kattintás történt.
* **`getWindow()`** - A megjelenítő ablak.
* **`getBukkitEvent()`** - A nyers Bukkit `InventoryClickEvent` (pl. `event.getBukkitEvent().setCancelled(false)` híváshoz).

### Speciális Ablakok (Special Windows)
A normál ládákon és az Üllőn kívül az API támogatja a Bukkit többi inventory típusát is. A Spillover (alsó inventory átvétel) mindegyiknél automatikusan működik, ha a Gui mérete nagyobb, mint a felső ablaké!

* **Rácsos speciális ablakok:** `HopperWindow` (5 slot), `DispenserWindow` (9 slot), `DropperWindow` (9 slot). Ezeknél nyugodtan használhatod a `Structure` API-t a tervezéshez.
* **Nem rácsos (szórt) ablakok:** `FurnaceWindow` (Kemence, 3 slot) és `BrewingWindow` (Főzőállvány, 5 slot). 
  * *Figyelem:* Ezeknél a típusoknál a Bukkit egyedi indexelést használ (pl. Kemencénél a 0 a felső, 1 az alsó, 2 a kimenet). Itt **NE** használd a `Structure` API-t, hanem közvetlenül a `gui.setItem(0, item)` metódussal helyezd el az Okos Gombokat a megfelelő indexeken!