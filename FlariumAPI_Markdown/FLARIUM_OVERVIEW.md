# FlariumAPI — Master Context & Architecture Guide

> **Ez a dokumentum a FlariumAPI hivatalos keretrendszerének áttekintése.**

---

## 🤖 AI Direktíva

Ez egy **Paper 1.21.8 / Java 21** alapú, **Folia-kompatibilis**, Enterprise-level Minecraft plugin API. Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, az alábbi szabályokat és modulokat **KIZÁRÓLAG** az itt leírt módon használhatod. Tilos a hagyományos Bukkit/Paper spagettikód, a `Bukkit.getScheduler()`, a `HashMap` cache, és az `if (args.length == 1)` logika!

---

## 1. Architekturális Alapelvek (Szigorú)

| Elv | Leírás |
|-----|--------|
| **Inversion of Control (IoC)** | Tilos a `public static Plugin getInstance()`. Minden függőséget konstruktoron keresztül kell injektálni. |
| **Folia-Ready Async** | A Bukkit API (World, Block, Entity, Player) hívása aszinkron szálról **szigorúan tilos**. Szálváltáshoz kötelező a `Scheduler` API használata. |
| **Adventure API (MiniMessage)** | Szövegek formázásához **kizárólag** MiniMessage (`<white>`, `<#hex>`) használható. Tilos a `§` vagy a `ChatColor`. |
| **KISS & DRY** | Ne írj felesleges absztrakciókat. Használd az API beépített metódusait. Guard Clause-ok (korai return) használata kötelező. |

---

## 2. Az API Moduljai

A FlariumAPI a következő modulokat tartalmazza. Részletes használati útmutatóért lásd a megfelelő `*_API.md` fájlt.

### 1. MessageService ([`MESSAGING_API.md`](MESSAGING_API.md))
Központosított üzenetkezelő. Kezeli a prefixeket, a `{placeholder}` cserét, és a MiniMessage formázást.

| Metódus | Leírás |
|---------|--------|
| `new MessageService<>(plugin, Lang.class, config)` | Inicializálás. |
| `messageService.send(player, Lang.KEY, Placeholder.unparsed("k", "v"))` | Chat üzenet küldése. |

> **Szabály:** Soha ne használj `player.sendMessage()`. A prefixet mindig a YAML-ben lévő `{prefix}` placeholderrel add meg.

---

### 2. Command API ([`COMMAND_API.md`](COMMAND_API.md))
Fa-struktúrájú (`CommandNode`) parancskezelő automata Tab Complete-tel (`ArgumentType`).

| Metódus | Leírás |
|---------|--------|
| `addChild(new GiveNode())` | Alparancs hozzáadása. |
| `addArgument("target", new PlayerArgument())` | Argumentum regisztrálása. |
| `new CommandDispatcher(plugin, rootCommand).register("shop")` | Parancs regisztrálása. |
| `context.getArgument("target")` | Argumentum lekérdezése. |

> **Szabály:** Tilos az `if (args[0].equals("give"))`.

---

### 3. ItemBuilder ([`ITEM_API.md`](ITEM_API.md))
Fluent API itemek építéséhez és YAML-ből való beolvasásához.

| Metódus | Leírás |
|---------|--------|
| `new ItemBuilder(Material.DIAMOND).name("<white>Kard").glow(true).build()` | Új item építése. |
| `ItemBuilder.fromConfig(config.getConfigurationSection("item"))` | Config beolvasás. |

> **Szabály:** Tilos a `ItemMeta.setDisplayName()`. A 1.21-es glow effektushoz `glow(true)`-t használj.

---

### 4. Menu API ([`MENU_API.md`](MENU_API.md))
Állapotvezérelt (State-Driven), objektum-orientált GUI rendszer (Item → Gui → Window rétegekkel) és beépített Spillover (alsó inventory) védelemmel.

| Metódus | Leírás |
|---------|--------|
| `new NormalGui(27)` | GUI létrehozása. |
| `gui.applyStructure(structure, ingredients)` | Rajz alapú felépítés. |
| `new NormalWindow(player, gui, "<white>Cím").open()` | Ablak megnyitása. |
| `new PaginatedGui(3, listSlots)` | Lapozós GUI. |

> **Szabály:** Tilos a Bukkit `InventoryClickEvent` manuális kezelése és a slot-indexelés.

---

### 5. Scheduler ([`SCHEDULER_API.md`](SCHEDULER_API.md))
Folia-kompatibilis, Virtual Thread alapú ütemező és `CompletableFuture` láncoló.

| Metódus | Leírás |
|---------|--------|
| `scheduler.runAsync(...)` | Aszinkron futtatás. |
| `scheduler.runForEntity(player, ...)` | Entitás szálon futtatás. |
| `scheduler.runAtLocation(loc, ...)` | Régió szálon futtatás. |
| `scheduler.forEntity(player)` | Executor future láncoláshoz. |

> **Szabály:** Tilos a `Bukkit.getScheduler()`. Minden időmegadás `Duration` objektum kell, hogy legyen.

---

### 6. DatabaseManager ([`DATABASE_API.md`](DATABASE_API.md))
HikariCP connection poolozó, Virtual Thread-eket használó, Prepared Statement alapú aszinkron adatbázis-kezelő.

| Metódus | Leírás |
|---------|--------|
| `databaseManager.executeUpdate(sql, ps -> ...)` | INSERT/UPDATE/DELETE. |
| `databaseManager.executeQuery(sql, ps -> ..., rs -> ...)` | SELECT. |
| `databaseManager.executeTransaction(conn -> ...)` | Tranzakció (rollback-kel). |
| `databaseManager.executeBatch(sql, ps -> ...)` | Tömeges mentés. |

> **Szabály:** Tilos a szinkron DB hívás. Tilos a string összefűzés (`+`) SQL query-kben.

---

### 7. AbstractProfileManager ([`CACHE_API.md`](CACHE_API.md))
Caffeine cache-t használó játékos profil manager, ami `AsyncPlayerPreLoginEvent`-ben aszinkron betölt, és `PlayerQuitEvent`-kor ment.

| Metódus | Leírás |
|---------|--------|
| `extends AbstractProfileManager<PlayerProfile>` | Manager implementálása. |
| `profileManager.getProfileOrThrow(uuid)` | Profil lekérdezése memóriából. |
| `profileManager.loadProfile(uuid)` | Aszinkron betöltés. |
| `profileManager.saveAndInvalidate(uuid)` | Mentés és eltávolítás. |

> **Szabály:** Tilos a `HashMap<UUID, Profile>`.

---

### 8. CooldownManager ([`COOLDOWN_API.md`](COOLDOWN_API.md))
Memória (Ephemeral) és Adatbázis (Persistent) alapú visszaszámláló, Executor-alapú biztonságos callbackekkel.

| Metódus | Leírás |
|---------|--------|
| `cooldownManager.set(uuid, "ns", duration)` | Ideiglenes cooldown. |
| `cooldownManager.setPersistent(uuid, "ns", duration)` | Perzisztens cooldown. |
| `cooldownManager.isActive(uuid, "ns")` | Aktív-e? |
| `cooldownManager.getFormattedRemaining(uuid, "ns", format)` | Hátralévő idő formázva. |
| `cooldownManager.clearOnQuit(uuid)` | Kilépéskori takarítás. |

> **Szabály:** Soha ne használj `System.currentTimeMillis()` matematikát.

---

### 9. PDCManager ([`PDC_API.md`](PDC_API.md))
Univerzális NBT/Custom Data wrapper. Kezeli az ItemMeta és a TileState update-eket automatikusan.

| Metódus | Leírás |
|---------|--------|
| `pdcManager.set(item, "id", new UUIDDataType(), uuid)` | Adat mentése. |
| `pdcManager.get(item, "id", new UUIDDataType())` | Adat olvasása. |
| `new JsonDataType<>(MyClass.class)` | Komplex objektum szerializálása. |

> **Szabály:** Tilos az NMS és a Reflection. Tilos a `new NamespacedKey()`.

---

### 10. PlaceholderService ([`PLACEHOLDER_API.md`](PLACEHOLDER_API.md))
Soft-depend, ClassLoader-safe PlaceholderAPI hook. O(1) Map-alapú routinggal.

| Metódus | Leírás |
|---------|--------|
| `placeholderService.registerPlaceholder("coins", player -> ...)` | Placeholder regisztrálása. |
| `placeholderService.parse(player, rawText)` | Szöveg feloldása. |

> **Szabály:** Tilos az óriás `switch` az `onRequest`-ben. A placeholderek csak a memóriából olvashatnak.

---

### 11. HologramManager ([`HOLOGRAM_API.md`](HOLOGRAM_API.md))
Paper 1.21 `TextDisplay` és `Interaction` entitásokra épülő, Folia-safe hologram rendszer.

| Metódus | Leírás |
|---------|--------|
| `hologramManager.createHologram(loc)` | Hologram létrehozása. |
| `new TextLine(scheduler, text).scale(2f, 2f, 2f)` | Szöveges sor fluent formázással. |
| `hologram.setClickAction(player -> ...)` | Kattintás kezelése. |
| `hologram.setRenderMode(RenderMode.VIEWER_LIST)` | Láthatóság beállítása. |

> **Szabály:** Tilos az ArmorStand hologram. A kattinthatóságot a láthatatlan `Interaction` entitás biztosítja.

---

### 12. WorldEditService ([`WORLDEDIT_API.md`](WORLDEDIT_API.md))
Soft-depend, ClassLoader-safe FAWE/WorldEdit híd aszinkron schematic beillesztéshez.

| Metódus | Leírás |
|---------|--------|
| `worldEditService.pasteSchematic(file, loc, true)` | Schematic beillesztése aszinkron. |

> **Szabály:** A beillesztés csak aszinkron történhet. A beillesztés utáni Bukkit logikát `scheduler.runAtLocation`-ba kell csomagolni.

---

### 13. Utility API ([`UTILS_API.md`](UTILS_API.md))
`NumberUtil` (CompactNumberFormat) és `TimeUtil` (Duration alapú, konfigurálható szuffixumokkal).

| Metódus | Leírás |
|---------|--------|
| `NumberUtil.formatCommas(money)` | Ezres elválasztók (pl. `10,683,918`). |
| `NumberUtil.formatShort(number)` | Rövidített (pl. `10.24k`). |
| `TimeUtil.formatDuration(seconds, timeFormat)` | Idő formázása (pl. `5p 30mp`). |

> **Szabály:** Tilos a "magic number" tick és milliszekundum számolás.

---

### 14. Sound API ([`SOUND_API.md`](SOUND_API.md))
Egységes hangkezelés Java kódból és YAML konfigurációkból.

| Metódus | Leírás |
|---------|--------|
| `SoundUtil.playSoundFromString(player, "[SOUND] ENTITY_PLAYER_LEVELUP|1.0|2.0")` | Config stringből. |
| `SoundUtil.playSound(player, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f)` | Közvetlenül. |

---

### 15. Currency API ([`CURRENCY_API.md`](CURRENCY_API.md))
Dinamikus, YAML-vezérelt valuta rendszer.

| Metódus | Leírás |
|---------|--------|
| `currencyManager.hasEnough(player, "Gem", 500)` | Egyenleg ellenőrzés. |
| `currencyManager.take(player, "Gem", 500)` | Levonás. |
| `currencyManager.give(player, "Gem", 100)` | Jóváírás. |

> **Szabály:** Tilos közvetlenül a Vault vagy PlayerPoints API-ját hívni.

---

## 3. Standard Plugin Inicializálás (Példa)

Egy FlariumAPI-t használó plugin `onEnable` metódusa így néz ki:

```java
public class MyPlugin extends JavaPlugin {
    private MessageService<Lang> messageService;
    private Scheduler scheduler;
    private DatabaseManager databaseManager;
    private CooldownManager cooldownManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1. Scheduler (Kötelező első, minden más ezt használja)
        this.scheduler = new Scheduler(this);

        // 2. Config & Messaging
        String prefix = getConfig().getString("prefix", "<gradient:#FF0000:#FF6666:#FF0000><b>fmPlugin</b></gradient>");
        TimeFormat timeFormat = TimeFormat.load(getConfig().getConfigurationSection("time-format"));
        this.messageService = new MessageService<>(this, Lang.class, new PluginConfig(prefix));

        // 3. Database & Cache
        DatabaseConfig dbConfig = DatabaseConfig.load(getConfig().getConfigurationSection("database"));
        this.databaseManager = new DatabaseManager(this, dbConfig);
        this.cooldownManager = new CooldownManager(databaseManager, scheduler);

        // 4. Commands
        CommandDispatcher dispatcher = new CommandDispatcher(this, new MainCommandNode());
        dispatcher.register("myplugin");

        // 5. Shutdown hook (Kritikus a memóriabiztos működéshez)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            databaseManager.close();
            scheduler.shutdown();
        }));
    }
}
```

---

## 4. AI Munkafolyamat (Kötelező végrehajtás)

Amikor egy játékos kér tőled egy plugint:

1. **Importálás:** A fenti modulokat és osztályokat használd. Ne találj ki saját managereket a már megoldott problémákra.
2. **Kódolás:** Írd meg a kódot szigorúan a fenti szabályok alapján. Használj Guard Clause-okat, Records-okat és Lambda kifejezéseket.
3. **Önkritika:** A kód végén ellenőrizd, hogy nem maradt-e benne `Bukkit.getScheduler()`, `HashMap<UUID`, `ChatColor` vagy `if (args.length == 1)`. Ha igen, javítsd ki a FlariumAPI megfelelő moduljára.
