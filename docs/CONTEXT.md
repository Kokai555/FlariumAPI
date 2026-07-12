# Flarium - Általános Fejlesztési Szabályok & AI Viselkedés

Ez a dokumentum a Flarium Minecraft szerverhálózat hivatalos fejlesztési alapelveit tartalmazza. 
**[!] AI DIREKTÍVA:** Ez a hálózat saját, egyedi keretrendszerrel rendelkezik (`FlariumAPI`). Bármilyen funkció fejlesztésekor (GUI, Parancsok, Adatbázis, Hologramok) **KÖTELEZŐ** a mellékelt specifikus API dokumentációk (`_API.md` fájlok) használata! Tilos natív Bukkit megoldásokat generálni ott, ahol a FlariumAPI nyújt alternatívát.

## 1. Architektúra és Csomagszerkezet (Strict Package Layout)
A fájloknak kizárólag a `com.flarium.[plugin_neve]` főcsomag alá kell kerülniük.
* **Főosztály:** A gyökérkönyvtárba kerül (pl. `FlariumAeroDrop.java`).
* **`.config`:** Beállítások, `Lang` enum és config recordok.
* **`.listener`:** A Bukkit `@EventHandler` osztályok (amik nem GUI-hoz vagy Hologramhoz kötődnek).
* **`.commands`:** A parancsok implementációi (kiterjesztik a CommandNode-ot).
* **`.menu`:** A tokozott Inventory (MenuView) osztályok.
* **`.manager` / `.service`:** Az üzleti logika központjai.
* **`.model`:** A játékos/plugin állapotát reprezentáló DTO-k.

## 2. Dependency Injection (DI) és Életciklus
* **Nincs Singleton a Managereknél:** Szigorúan tilos a statikus managerek használata a pluginjaidban. Minden függőséget **konstruktoron keresztül** kell injektálni (IoC).
* A kivétel csak maga az API magja (`FlariumAPI.getInstance()`), ahonnan a core szolgáltatásokat kéred le.

## 3. Üzenetek és Vizualitás (Design Szabályok)
A `MessageService` használata kötelező (lásd: `MESSAGING_API.md`), de a design kialakításánál az alábbiakat kell követni:
* **Automatikus Prefix és Színpaletta:** Új plugin generálásakor az AI-nak választania kell egy, a plugin témájához illő világos Hexa alap színt (Szín 1). A Szín 2 kötelezően a Szín 1 pontosan **30%-kal világosabb** árnyalata.
* **Prefix formátuma:** `<gradient:[Szín 1]:[Szín 2]><b>PLUGINNÉV</b></gradient> <dark_gray>→`
* **Üzenetek alapformátuma:** Minden üzenet alapszíne kötelezően `<white>`.
* **Kiemelések:** A változókat és számokat a **világosabb (Szín 2) Hexa kóddal** kell kiemelni.
* **Help menü:** A `help.commands-list` YAML listának így kell kinéznie: `<dark_gray>▪ <gray>Használat: <[Szín 2]>/parancs...`

## 4. Eseménykezelés (Event API)
* **Event Prioritás:** Ha egy eseményt csak figyelni kell (nem módosítjuk), használj `@EventHandler(priority = EventPriority.MONITOR)`. Ha le kell mondani, használd a `HIGH` vagy `LOWEST` prioritást.
* **ignoreCancelled:** Zászló kötelező block break/place eventeknél: `@EventHandler(ignoreCancelled = true)`.
* **Szálbiztonság:** Eventekben (pl. BlockBreak, PlayerInteract) **SOSEM** indíthatsz szinkron adatbázis lekérdezést!

## 5. Emberi Kódolási Filozófia (Kötelező stílus)
* **KISS (Keep It Simple):** Ne hozz létre felesleges absztrakciókat. Lapos, olvasható kód kell.
* **Guard Clauses:** Null és jogosultság ellenőrzések a metódus elején `return` megszakítással. Nincs mély egymásba ágyazott `if` blokk!
* **Kommentelés:** Csak akkor írj kommentet, ha a logika *miért*je nem nyilvánvaló. Az angol kód legyen önmagát magyarázó.
* **Naplózás (Logging):** Tilos a `System.out.println()`. Csak `plugin.getLogger().severe(...)`. Ne nyelj el hibákat üres `catch` blokkban!

## 6. Függőségek és Build
* A projekt Java 21-et és Paper 1.21.8 API-t használ.
* Minden saját plugin a `build.gradle.kts`-ben hivatkozik a FlariumAPI-ra: `compileOnly("com.flarium:flarium-api:1.0")` a `mavenLocal()` repóból.
* Nincs szükség a külső libek (Caffeine, Hikari) újra-árnyékolására (shading), mert azt a FlariumAPI már megteszi.

## 7. AI Munkafolyamat (Kötelező végrehajtás lépései)
Mielőtt bármilyen kódot generálnál, kövesd ezt a sorrendet az üzeneted elején:
1. **Tervezés (Thinking):** Írj egy rövid vázlatot arról, milyen csomagokat és osztályokat hozol létre.
2. **API Ellenőrzés:** Gondold át és írd le, hogy a kért funkciókhoz melyik Flarium `_API.md` rendszereket kell használnod (pl. "GUI kell, tehát a PaginatedMenuView-t fogom örökölni").
3. **Generálás:** Írd meg a kódot szigorúan a Flarium standardok alapján.