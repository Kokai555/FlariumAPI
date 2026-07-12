# FlariumAPI - Master Context & Architecture Guide for AI

Ez a dokumentum a FlariumAPI hivatalos keretrendszerének áttekintése. 
**AI Direktíva:** Ez egy Paper 1.21.8 / Java 21 alapú, Folia-kompatibilis, Enterprise-level Minecraft plugin API. Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, az alábbi szabályokat és modulokat **KIZÁRÓLAG** az itt leírt módon használhatod. Tilos a hagyományos Bukkit/Paper spagettikód, a `Bukkit.getScheduler()`, a `HashMap` cache, és a `if (args.length == 1)` logika!

## 1. Architekturális Alapelvek (Szigorú)

1. **Inversion of Control (IoC):** Tilos a `public static Plugin getInstance()`. Minden függőséget (Managerek, Service-ek) **konstruktoron keresztül** kell injektálni.
2. **Folia-Ready Async:** A Bukkit API (World, Block, Entity, Player) hívása aszinkron szálról **szigorúan tilos**. Szálváltáshoz kötelező a `Scheduler` API használata.
3. **Adventure API (MiniMessage):** Szövegek formázásához **kizárólag** MiniMessage (`<white>`, `<#hex>`) használható. Tilos a `§` vagy a `ChatColor`. A Legacy kódok (`&a`, `#fff`) only a YAML fájlokban megengedettek, az API automatikusan konvertálja őket.
4. **KISS & DRY:** Ne írj felesleges absztrakciókat. Használd az API beépített metódusait. Guard Clause-ok (korai return) használata kötelező.

---

## 2. Az API Moduljai (Mik állnak rendelkezésre?)

A FlariumAPI a következő modulokat tartalmazza. (Részletes használati útmutatóért lásd a megfelelő `*_API.md` fájlt).

### 1. MessageService (`MESSAGING_API.md`)
Központosított üzenetkezelő. Kezeli a prefixeket, a `{placeholder}` cserét, és a MiniMessage formázást.
*   **Inicializálás:** `new MessageService<>(plugin, Lang.class, new PluginConfig(prefix))`
*   **Használat:** `messageService.send(player, Lang.NO_PERMISSION, Placeholder.unparsed("target", name))`
*   **Szabály:** Soha ne használj `player.sendMessage()`. A prefixet mindig a YAML-ben lévő `{prefix}` placeholderrel add meg.

### 2. Command API (`COMMAND_API.md`)
Fa-struktúrájú (`CommandNode`) parancskezelő automata Tab Complete-tel (`ArgumentType`).
*   **Használat:** `addChild(new GiveNode())`, `addArgument("target", new PlayerArgument())`
*   **Dispatching:** `new CommandDispatcher(plugin, rootCommand).register("shop")`
*   **Szabály:** Tilos az `if (args[0].equals("give"))`. Az argumentumokat a `context.getArgument("target")` metódussal kell lekérni (a Dispatcher már elő-feldolgozta őket).

### 3. ItemBuilder (`ITEM_API.md`)
Fluent API itemek építéséhez és YAML-ből való beolvasásához.
*   **Használat:** `new ItemBuilder(Material.DIAMOND).name("<white>Kard").glow(true).build()`
*   **Config:** `ItemBuilder.fromConfig(config.getConfigurationSection("item"))`
*   **Szabály:** Tilos a `ItemMeta.setDisplayName()`. A 1.21-es glow effektushoz `setEnchantmentGlintOverride(true)`-t használ.

### 4. Menu API (`MENU_API.md`)
Biztonságos (Drag/Shift védelem), YAML pattern alapú GUI rendszer, generikus lapozóval (`PaginatedMenuView<T>`).
*   **Használat:** `new MainMenuView(player, layout).open()`
*   **Szabály:** Tilos az `event.getSlot() == 14` logika. A kattintásokat a `resolveAction` switch és a `ClickableItem` record kezeli.

### 5. Scheduler (`SCHEDULER_API.md`)
Folia-kompatibilis, Virtual Thread alapú ütemező és `CompletableFuture` láncoló.
*   **Használat:** `scheduler.runAsync(...)`, `scheduler.runForEntity(player, ...)`, `scheduler.runAtLocation(loc, ...)`
*   **Szabály:** Tilos a `Bukkit.getScheduler()`. Minden időmegadás `Duration` objektum kell, hogy legyen.

### 6. DatabaseManager (`DATABASE_API.md`)
HikariCP connection poolozó, Virtual Thread-eket használó, Prepared Statement alapú aszinkron adatbázis-kezelő.
*   **Használat:** `databaseManager.executeUpdate(sql, ps -> ...)` vagy `executeQuery(...)`
*   **Szabály:** Tilos a szinkron DB hívás. Tilos a string összefűzés (`+`) SQL query-kben. Tranzakciókhoz `executeTransaction`.

### 7. AbstractProfileManager (`CACHE_API.md`)
Caffeine cache-t használó játékos profil manager, ami `AsyncPlayerPreLoginEvent`-ben aszinkron betölt, és `PlayerQuitEvent`-kor ment.
*   **Használat:** `extends AbstractProfileManager<PlayerProfile>`, implementáld a `loadFromDatabase` és `saveToDatabase` metódusokat.
*   **Szabály:** Tilos a `HashMap<UUID, Profile>`. Az adatokat a `getProfile()`-lal memóriából kell olvasni.

### 8. CooldownManager (`COOLDOWN_API.md`)
Memória (Ephemeral) és Adatbázis (Persistent) alapú visszaszámláló, Executor-alapú biztonságos callbackekkel.
*   **Használat:** `cooldownManager.set(uuid, "namespace", Duration.ofSeconds(10), runnable, scheduler.forEntity(player))`
*   **Szabály:** Soha ne használj `System.currentTimeMillis()` matematikát a Java kódban.

### 9. PDCManager (`PDC_API.md`)
Univerzális NBT/Custom Data wrapper. Kezeli az ItemMeta és a TileState update-eket automatikusan. Tartalmaz `UUIDDataType` és `JsonDataType` egyedi típusokat.
*   **Használat:** `pdcManager.set(item, "id", new UUIDDataType(), uuid)`
*   **Szabály:** Tilos az NMS és a Reflection. Tilos a `new NamespacedKey()` (a KeyRegistry automatikusan cache-eli).

### 10. PlaceholderService (`PLACEHOLDER_API.md`)
Soft-depend, ClassLoader-safe PlaceholderAPI hook. O(1) Map-alapú routinggal.
*   **Használat:** `placeholderService.registerPlaceholder("coins", player -> ...)`
*   **Szabály:** Tilos az óriás `switch` az `onRequest`-ben. A placeholderek csak a memóriából (Cache) olvashatnak, nem indíthatnak DB lekérdezést.

### 11. HologramManager (`HOLOGRAM_API.md`)
Paper 1.21 `TextDisplay` és `Interaction` entitásokra épülő, Folia-safe hologram rendszer.
*   **Használat:** `hologramManager.createHologram(loc, text, true).thenAccept(h -> h.setClickAction(...))`
*   **Szabály:** Tilos az ArmorStand hologram. A kattinthatóságot a láthatatlan `Interaction` entitás biztosítja.

### 12. WorldEditService (`WORLDEDIT_API.md`)
Soft-depend, ClassLoader-safe FAWE/WorldEdit híd aszinkron schematic beillesztéshez.
*   **Használat:** `worldEditService.pasteSchematic(file, loc, true).thenAccept(...)`
*   **Szabály:** A beillesztés csak aszinkron történhet. A beillesztés utáni Bukkit logikát `scheduler.runAtLocation`-ba kell csomagolni.

### 13. Utility API (`UTILS_API.md`)
`NumberUtil` (CompactNumberFormat) és `TimeUtil` (Duration alapú, konfigurálható szuffixumokkal).
*   **Használat:** `NumberUtil.formatCommas(money)`, `TimeUtil.formatDuration(seconds, timeFormat)`
*   **Szabály:** Tilos a "magic number" tick és milliszekundum számolás.

---

## 3. Standard Plugin Inicializálás (Példa)

Egy FlariumAPI-t használó plugin `onEnable` metódusa így néz ki. Az AI-nak ezt a mintát kell követnie.

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
        String prefix = getConfig().getString("settings.prefix", "<white>Plugin</white> ");
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

## 4. AI Munkafolyamat (Kötelező végrehajtás)

Amikor egy játékos kér tőled egy plugint:
1. **Importálás:** A fenti modulokat és osztályokat használd. Ne találj ki saját managereket a már megoldott problémekre (pl. cache, scheduler).
2. **Kódolás:** Írd meg a kódot szigorúan a fenti szabályok alapján. Használj Guard Clause-okat, Records-okat és Lambda kifejezéseket.
3. **Önkritika:** A kód végén ellenőrizd, hogy nem maradt-e benne `Bukkit.getScheduler()`, `HashMap<UUID`, `ChatColor` vagy `if (args.length == 1)`. Ha igen, javítsd ki a FlariumAPI megfelelő moduljára.