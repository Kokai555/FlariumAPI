# FlariumAPI - Hologram API Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `HologramManager` és láncolható (Fluent) `HologramLine` architektúrájának használatát írja le, ami a Paper 1.21 `Display` entitásokra, az Anchor (Passenger) rendszerre, és a dinamikus `Interaction` hitboxra épül.
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, lebegő vizuális elemek (hologramok) létrehozására **KIZÁRÓLAG** az itt leírt módszereket használhatod. Tilos a régi ArmorStand alapú hologramok használata, és tilos a manuális `Transformation` mátrix matek!

## 1. Architekturális Szabályok (Szigorú)

1. **HologramLine (Fluent Builder):** A hologramok egymás alá épülő sorokból állnak (`TextLine`, `ItemLine`). A sorok létrehozásakor láncolható (Fluent) metódusokat használhatsz a formázásra (pl. `.scale()`, `.billboard()`).
2. **Anchor & Interaction System:** Minden hologram rendelkezik egy láthatatlan `ArmorStand` horgonnyal (Anchor) és egy láthatatlan `Interaction` hitbox-szal. A sorok és a hitbox az Anchor utasai (Passengers). A hitbox mérete automatikusan igazodik a hologram magasságához, így a kattintás tökéletesen működik az egész felületen!
3. **Láthatóság (RenderMode):** A hologramok láthatósága játékosonként szabályozható (pl. csak a tulajdonos lássa).
4. **Folia Thread-Safety:** Ha egy sort módosítasz (pl. `.text(...)` hívás) a hologram leidézése után, az API a háttérben automatikusan az entitás szálán (Entity Thread) hajtja végre a frissítést.

---

## 2. API Referencia (Elérhető Metódusok)

### `Hologram`
* `void addLine(HologramLine line)`: Új sort ad a hologramhoz (alulra építkezik).
* `void removeLine(int index)`: Töröl egy sort, újraszámolja a pozíciókat.
* `void setClickAction(Consumer<Player> action)`: Beállítja, mi történjen, ha egy játékos rákattint/ráüt a hitboxra.
* `void setRenderMode(RenderMode mode)`: Beállítja a láthatóságot (`ALL`, `NONE`, `VIEWER_LIST`, `NOT_ATTACHED_PLAYER`).
* `void addViewer(UUID uuid)` / `void removeViewer(UUID uuid)`: Hozzáad/töröl egy játékost a `VIEWER_LIST` módból.
* `void attachTo(Entity entity)`: A hologramot utasként ráülteti egy mozgó entitásra (pl. zuhanó ládára).
* `void remove()`: Törli a teljes hologramot a világból.

### `HologramLine` (A sorok típusai és Fluent formázás)
A konstruktorok **KÖTELEZŐEN** várják a `Scheduler` példányt!
* `new TextLine(scheduler, Component text)`: Szöveges sor.
* `new ItemLine(scheduler, ItemStack item)`: Tárgy sor.

**Közös Fluent metódusok (Mindkét típusnál):**
* `.scale(float x, float y, float z)`: Nagyítás/kicsinyítés (pl. `2f, 2f, 2f`).
* `.billboard(Display.Billboard mode)`: Forgás (`CENTER`: feléd néz, `VERTICAL`: Y tengelyen forog, `FIXED`: fix).

**TextLine specifikus Fluent metódusok:**
* `.text(Component text)`: Szöveg frissítése.
* `.backgroundColor(Color color)`: Háttérdoboz színe (Használj `Color.fromARGB(0,0,0,0)`-t az átlátszóhoz).
* `.alignment(TextAlignment alignment)`: Többsoros szöveg igazítása (`LEFT`, `CENTER`, `RIGHT`).
* `.lineWidth(int width)`: Maximális sorhossz, ami után sortörést csinál (pl. `200`).
* `.opacity(byte opacity)`: A szöveg átlátszósága (0-255).

---

## 3. Gyakorlati Példák

### Példa 1: Modern UI Hologram Kattintással és RenderMode-dal
Lebegő, árnyékmentes felirat egy nagy tárggyal, amit csak bizonyos játékosok látnak.

```java
hologramManager.createHologram(loc).thenAccept(hologram -> {
    // 1. Sor: Modern szöveg fekete doboz nélkül, mindig felénk fordul
    TextLine title = new TextLine(scheduler, MiniMessage.miniMessage().deserialize("<red><bold>Titkos Láda"))
        .backgroundColor(Color.fromARGB(0, 0, 0, 0))
        .billboard(Display.Billboard.CENTER);
    hologram.addLine(title);
    
    // 2. Sor: Egy 2x-es méretű, lassan forgó lebegő gyémánt
    ItemLine item = new ItemLine(scheduler, new ItemStack(Material.DIAMOND))
        .scale(2f, 2f, 2f)
        .billboard(Display.Billboard.VERTICAL);
    hologram.addLine(item);
    
    // Láthatóság beállítása (Csak 'targetPlayer' fogja látni)
    hologram.setRenderMode(RenderMode.VIEWER_LIST);
    hologram.addViewer(targetPlayer.getUniqueId());
    
    // Kattintás (A masszív Interaction hitbox fogja fel)
    hologram.setClickAction(player -> {
        player.sendMessage("Kinyitottad a titkos ládát!");
        hologram.remove();
    });
});

```

### Példa 2: Dinamikusan Frissülő Szöveg (Visszaszámláló)

Ha egy sort frissíteni kell a világban, egyszerűen hívd meg rajta a Fluent beállítót (pl. `.text()`). A FlariumAPI a háttérben automatikusan szálbiztosan frissíti a Folia entitást!

```java
hologramManager.createHologram(loc).thenAccept(hologram -> {
    TextLine timeLine = new TextLine(scheduler, MiniMessage.miniMessage().deserialize("<yellow>10mp"));
    hologram.addLine(timeLine);

    // KÖTELEZŐ: Entity Timer használata!
    scheduler.runForEntityTimer(hologram.getAnchor(), new Runnable() {
        int seconds = 10;
        @Override
        public void run() {
            if (seconds <= 0) {
                hologram.remove();
                return;
            }
            seconds--;
            // A .text() hívás a háttérben biztonságosan módosítja a TextDisplay-t
            timeLine.text(MiniMessage.miniMessage().deserialize("<yellow>" + seconds + "mp"));
        }
    }, Duration.ofSeconds(1), Duration.ofSeconds(1));
});

```

---

## 4. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Konstruktor** | `new TextLine(Component)` | KÖTELEZŐ átadni a Schedulert: `new TextLine(scheduler, Component)` |
| **Nagyítás (Scale)** | `Transformation t = display.getTransformation(); ...` | Láncolás használata: `new ItemLine(...).scale(2f, 2f, 2f)` |
| **Átlátszó háttér** | NMS vagy Packet küldés | `.backgroundColor(Color.fromARGB(0,0,0,0))` |
| **Kattintás kezelés** | Bukkit EntityInteractEvent regisztrálása | `hologram.setClickAction(player -> ...)` használata. |
| **Láthatóság** | Kézi `player.hideEntity(...)` ciklusok | `hologram.setRenderMode(...)` és `hologram.addViewer(...)` |
| **Dinamikus update** | Sor letörlése és újrakreálása | `.text(Component)` meghívása a meglévő referencián. |

## 5. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha lebegő szöveget vagy tárgyat kell kreálnod, a `hologramManager.createHologram(loc)`-ot használd aszinkron `thenAccept` láncban. A sorokat a `TextLine(scheduler, text)` és `ItemLine(scheduler, item)` osztályokkal hozd létre, és **használj láncolt (fluent) formázást** (pl. `.scale()`, `.billboard()`, `.backgroundColor()`). Ha a láthatóságot korlátozni kell, használd a `setRenderMode()`-ot! Ha a szöveget időzítve frissíted, KÖTELEZŐ a `scheduler.runForEntityTimer()` használata, ahol a kimentett sorpéldány `.text()` metódusát hívod meg! Szigorúan tilos manuális `Transformation` mátrixokkal és `ArmorStand`-ekkel spagetti kódot generálni!