# Flarium — Általános Fejlesztési Szabályok & AI Viselkedés

> **Ez a dokumentum a Flarium Minecraft szerverhálózat hivatalos fejlesztési alapelveit tartalmazza.**

---

## 🤖 AI Direktíva

Ez a hálózat saját, egyedi keretrendszerrel rendelkezik (`FlariumAPI`). Bármilyen funkció fejlesztésekor (GUI, Parancsok, Adatbázis, Hologramok) **KÖTELEZŐ** a mellékelt specifikus API dokumentációk (`*_API.md` fájlok) használata! Tilos natív Bukkit megoldásokat generálni ott, ahol a FlariumAPI nyújt alternatívát.

---

## 1. Architektúra és Csomagszerkezet (Strict Package Layout)

A fájloknak kizárólag a `com.flarium.[plugin_neve]` főcsomag alá kell kerülniük.

| Csomag | Tartalom |
|--------|----------|
| Gyökérkönyvtár | Főosztály (pl. `FlariumAeroDrop.java`) |
| `.config` | Beállítások, `Lang` enum és config recordok |
| `.listener` | Bukkit `@EventHandler` osztályok (nem GUI/Hologram kötött) |
| `.commands` | Parancsok implementációi (`CommandNode` leszármazottak) |
| `.menu` | Tokozott Inventory (`MenuView`) osztályok |
| `.manager` / `.service` | Az üzleti logika központjai |
| `.model` | A játékos/plugin állapotát reprezentáló DTO-k |

---

## 2. Dependency Injection (DI) és Életciklus

- **Nincs Singleton a Managereknél:** Szigorúan tilos a statikus managerek használata. Minden függőséget **konstruktoron keresztül** kell injektálni (IoC).
- A kivétel csak maga az API magja (`FlariumAPI.getInstance()`), ahonnan a core szolgáltatásokat kéred le.

---

## 3. Üzenetek és Vizualitás (Design Szabályok)

A [`MessageService`](MESSAGING_API.md) használata kötelező, de a design kialakításánál az alábbiakat kell követni:

### 3.1 Prefix és Színpaletta

- **Automatikus Prefix és Színpaletta:** Új plugin generálásakor az AI-nak választania kell egy, a plugin témájához illő világos Hexa alap színt (Szín 1). A Szín 2 (középső szín) kötelezően a Szín 1 pontosan **30%-kal világosabb** árnyalata.
- **Prefix formátuma (config.yml):** A prefixet a `config.yml`-ben kell megadni, és a színeket később le lehet cserélni:

```yaml
prefix: "<gradient:#FF0000:#FF6666:#FF0000><b>fmPluginNeve</b></gradient>"
```

> A fenti példában `#FF0000` a Szín 1, `#FF6666` a Szín 2 (30%-kal világosabb). A `fmPluginNeve` helyére mindig az aktuális plugin nevét írd!

### 3.2 messages.yml Szabályok

A `messages.yml` fájlban az alábbi szabályokat **KÖTELEZŐ** betartani:

```yaml
#Rules to use with every message
#1. Only use &f as the color for every message.
#2. Every Placeholder (like {player} or {amount}) Should use the brighter color of the Plugins prefix. Example:
#If the Prefix Color is <gradient:#FF0000:#FF6666:#FF0000>, then every placeholder should use #FF6666
#3. Command usage messages should have parameters like:
#<player> <--- Required parameter
#[reason] <--- Optional parameter


message-examples:
    no-permission: "{prefix} &fYou don't have permission to use this command!"
    reload: "{prefix} &fYou successfully reloaded the Configuration files."
    give: "{prefix} &fSuccessfully given &#FF6666{amount}x {item-name} &fto &#FF6666{player}&f."
    usage:
        - ""
        - " &ffmChatGames &7[ᴄᴏᴍᴍᴀɴᴅs]"
        - " <#FF0000>  <#FF6666>/chatgame start"
        - " <#FF0000>  <#FF6666>/chatgame stop"
        - " <#FF0000>  <#FF6666>/chatgame reload"
        - ""
```

| Szabály | Leírás |
|---------|--------|
| **1. Alapszín** | Minden üzenet alapszíne kötelezően `&f` (fehér). |
| **2. Placeholder színek** | Minden placeholder (pl. `{player}`, `{amount}`) a prefix **világosabb (Szín 2)** színét használja (pl. `&#FF6666`). |
| **3. Parancs használat** | A parancs használati üzenetekben a paraméterek formátuma: `<player>` (kötelező) és `[reason]` (opcionális). |
| **4. Help menü** | A `usage` lista első sora üres, majd a plugin neve `[ᴄᴏᴍᴍᴀɴᴅs]` formátumban, utána a parancsok Szín 1 és Szín 2 színekkel. |

---

## 4. Eseménykezelés (Event API)

| Szabály | Leírás |
|---------|--------|
| **Event Prioritás** | Ha egy eseményt csak figyelni kell (nem módosítjuk), használj `@EventHandler(priority = EventPriority.MONITOR)`. Ha le kell mondani, használd a `HIGH` vagy `LOWEST` prioritást. |
| **ignoreCancelled** | Zászló kötelező block break/place eventeknél: `@EventHandler(ignoreCancelled = true)`. |
| **Szálbiztonság** | Eventekben (pl. BlockBreak, PlayerInteract) **SOSEM** indíthatsz szinkron adatbázis lekérdezést! |

---

## 5. Emberi Kódolási Filozófia (Kötelező stílus)

| Elv | Leírás |
|-----|--------|
| **KISS** | Ne hozz létre felesleges absztrakciókat. Lapos, olvasható kód kell. |
| **Guard Clauses** | Null és jogosultság ellenőrzések a metódus elején `return` megszakítással. Nincs mély egymásba ágyazott `if` blokk! |
| **Nincs komment** | A kódban **SOHA** ne legyenek kommentek. Az angol kód legyen önmagát magyarázó — a változó- és metódusnevek tükrözzék a célt. |
| **Naplózás** | Tilos a `System.out.println()`. Csak `plugin.getLogger().severe(...)`. Ne nyelj el hibákat üres `catch` blokkban! |

---

## 6. Függőségek és Build

- A projekt **Java 21**-et és **Paper 1.21.8** API-t használ.
- Minden saját plugin a `build.gradle.kts`-ben hivatkozik a FlariumAPI-ra: `compileOnly("com.flarium:flarium-api:1.0")` a `mavenLocal()` repóból.
- Nincs szükség a külső libek (Caffeine, Hikari) újra-árnyékolására (shading), mert azt a FlariumAPI már megteszi.

---

## 7. AI Munkafolyamat (Kötelező végrehajtás lépései)

Mielőtt bármilyen kódot generálnál, kövesd ezt a sorrendet:

1. **Tervezés (Thinking):** Írj egy rövid vázlatot arról, milyen csomagokat és osztályokat hozol létre.
2. **API Ellenőrzés:** Gondold át és írd le, hogy a kért funkciókhoz melyik Flarium `_API.md` rendszereket kell használnod (pl. "GUI kell, tehát a `PaginatedGui`-t fogom örökölni").
3. **Generálás:** Írd meg a kódot szigorúan a Flarium standardok alapján.
