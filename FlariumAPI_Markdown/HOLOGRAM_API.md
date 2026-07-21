# FlariumAPI — Hologram API

> **Package:** `com.flarium.api.ui.hologram`
> **Core Classes:** `HologramManager`, `Hologram`, `TextLine`, `ItemLine`

A FlariumAPI hologram rendszere a Paper 1.21 `TextDisplay` és `ItemDisplay` entitásokra, az Anchor (Passenger) rendszerre, és a dinamikus `Interaction` hitboxra épül.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, lebegő vizuális elemek (hologramok) létrehozására **KIZÁRÓLAG** az itt leírt módszereket használhatja.

| Tilos | Helyette |
|-------|----------|
| ArmorStand alapú hologram | `hologramManager.createHologram(loc)` |
| Manuális `Transformation` mátrix matek | Fluent `.scale()`, `.billboard()` metódusok |

---

## 1. Architekturális Szabályok

1. **HologramLine (Fluent Builder):** A hologramok egymás alá épülő sorokból állnak (`TextLine`, `ItemLine`). A sorok létrehozásakor láncolható (Fluent) metódusokat használhatsz.
2. **Anchor & Interaction System:** Minden hologram rendelkezik egy láthatatlan `ArmorStand` horgonnyal (Anchor) és egy láthatatlan `Interaction` hitbox-szal. A hitbox mérete automatikusan igazodik a hologram magasságához.
3. **Láthatóság (RenderMode):** A hologramok láthatósága játékosonként szabályozható.
4. **Folia Thread-Safety:** Ha egy sort módosítasz a hologram leidézése után, az API a háttérben automatikusan az entitás szálán hajtja végre a frissítést.

---

## 2. API Referencia

### `HologramManager`

#### `createHologram(Location location)`

```java
CompletableFuture<Hologram> createHologram(Location location)
```

Aszinkron létrehoz egy hologramot a megadott lokáción.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `location` | `Location` | A hologram pozíciója. |
| **Visszatérés** | `CompletableFuture<Hologram>` | A létrehozott hologram. |

#### `getHologram(UUID uuid)`

```java
Hologram getHologram(UUID uuid)
```

Visszaadja a hologramot UUID alapján.

#### `getHolograms()`

```java
Collection<Hologram> getHolograms()
```

Visszaadja az összes aktív hologramot.

#### `removeHologram(UUID uuid)`

```java
void removeHologram(UUID uuid)
```

Törli a hologramot UUID alapján.

#### `shutdown()`

```java
void shutdown()
```

Leállítja az összes hologramot. Kötelező meghívni az `onDisable()`-ben.

---

### `Hologram`

#### `addLine(HologramLine line)`

```java
void addLine(HologramLine line)
```

Új sort ad a hologramhoz (alulra építkezik).

#### `removeLine(int index)`

```java
void removeLine(int index)
```

Töröl egy sort, újraszámolja a pozíciókat.

#### `setClickAction(Consumer<Player> action)`

```java
void setClickAction(Consumer<Player> action)
```

Beállítja, mi történjen, ha egy játékos rákattint a hitboxra.

#### `setRenderMode(RenderMode mode)`

```java
void setRenderMode(RenderMode mode)
```

Beállítja a láthatóságot.

| `RenderMode` érték | Leírás |
|---------------------|--------|
| `ALL` | Mindenki látja. |
| `NONE` | Senki sem látja. |
| `VIEWER_LIST` | Csak a hozzáadott nézők látják. |
| `NOT_ATTACHED_PLAYER` | Csak a nem csatlakozott játékosok látják. |

#### `addViewer(UUID uuid)` / `removeViewer(UUID uuid)`

```java
void addViewer(UUID uuid)
void removeViewer(UUID uuid)
```

Hozzáad/töröl egy játékost a `VIEWER_LIST` módból.

#### `attachTo(Entity entity)`

```java
void attachTo(Entity entity)
```

A hologramot utasként ráülteti egy mozgó entitásra (pl. zuhanó ládára).

#### `remove()`

```java
void remove()
```

Törli a teljes hologramot a világból.

#### `getAnchor()`

```java
ArmorStand getAnchor()
```

Visszaadja a hologram horgonyát (Anchor).

---

### `TextLine`

#### Konstruktor

```java
public TextLine(Scheduler scheduler, Component text)
```

> ⚠️ A konstruktor **KÖTELEZŐEN** várja a `Scheduler` példányt!

#### Fluent metódusok

| Metódus | Leírás |
|---------|--------|
| `.text(Component text)` | Szöveg frissítése. |
| `.backgroundColor(Color color)` | Háttérdoboz színe (`Color.fromARGB(0,0,0,0)` = átlátszó). |
| `.alignment(TextAlignment alignment)` | Többsoros szöveg igazítása (`LEFT`, `CENTER`, `RIGHT`). |
| `.lineWidth(int width)` | Maximális sorhossz, ami után sortörést csinál. |
| `.opacity(byte opacity)` | Szöveg átlátszósága (0-255). |
| `.scale(float x, float y, float z)` | Nagyítás/kicsinyítés. |
| `.billboard(Display.Billboard mode)` | Forgás (`CENTER`, `VERTICAL`, `FIXED`). |

---

### `ItemLine`

#### Konstruktor

```java
public ItemLine(Scheduler scheduler, ItemStack item)
```

#### Fluent metódusok

| Metódus | Leírás |
|---------|--------|
| `.item(ItemStack item)` | Tárgy frissítése. |
| `.scale(float x, float y, float z)` | Nagyítás/kicsinyítés. |
| `.billboard(Display.Billboard mode)` | Forgás. |

---

## 3. Gyakorlati Példák

### Példa 1: Modern UI Hologram Kattintással és RenderMode-dal

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

## 4. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Konstruktor** | `new TextLine(Component)` | `new TextLine(scheduler, Component)` |
| **Nagyítás (Scale)** | `Transformation t = display.getTransformation(); ...` | `new ItemLine(...).scale(2f, 2f, 2f)` |
| **Átlátszó háttér** | NMS vagy Packet küldés | `.backgroundColor(Color.fromARGB(0,0,0,0))` |
| **Kattintás kezelés** | Bukkit EntityInteractEvent regisztrálása | `hologram.setClickAction(player -> ...)` |
| **Láthatóság** | Kézi `player.hideEntity(...)` ciklusok | `hologram.setRenderMode(...)` és `addViewer(...)` |
| **Dinamikus update** | Sor letörlése és újrakreálása | `.text(Component)` meghívása a meglévő referencián |

---

## 5. Összefoglaló AI Szabály

**AI Directive:** Ha lebegő szöveget vagy tárgyat kell kreálnod, a `hologramManager.createHologram(loc)`-ot használd aszinkron `thenAccept` láncban. A sorokat a `TextLine(scheduler, text)` és `ItemLine(scheduler, item)` osztályokkal hozd létre, és használj láncolt (fluent) formázást. Ha a láthatóságot korlátozni kell, használd a `setRenderMode()`-ot! Ha a szöveget időzítve frissíted, KÖTELEZŐ a `scheduler.runForEntityTimer()` használata, ahol a kimentett sorpéldány `.text()` metódusát hívod meg! Szigorúan tilos manuális `Transformation` mátrixokkal és `ArmorStand`-ekkel spagetti kódot generálni!
