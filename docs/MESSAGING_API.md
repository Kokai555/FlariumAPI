# FlariumAPI - Üzenetrendszer (MessageService) Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `MessageService` rendszerének használatát írja le. 
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, az üzenetek küldésére, formázására és a konfigurációból (YAML) történő lokalizációra **KIZÁRÓLAG** az itt leírt módszereket használhatod. Tilos alternatív, hagyományos megoldásokat (pl. hardcode-olt `§` jelek) generálni!

## 1. Architekturális Szabályok (Szigorú)

1. **Nincs String összefűzés a Java-ban:** Tilos a kódban a `player.sendMessage("§cHiba: " + nevet)` formátum.
2. **Nincs hardcode-olt prefix:** Tilos a Java kódban a prefixet kézzel hozzáfűzni az üzenetekhez. A YAML fájlban kell használni a `{prefix}` placeholdert, amit az API automatikusan felold.
3. **Saját Prefix (IoC):** Minden plugin a saját `config.yml`-jéből olvassa ki a prefixet, és azt adja át a FlariumAPI `PluginConfig` rekordjának.
4. **Színkezelés (MiniMessage):** A szövegek alapszíne kötelezően `<white>`. A kiemelések (változók, nevek, számok) a plugin saját világosabb hexa színét használják (pl. `<#6CAAD3>`). Az API a háttérben automatikusan parse-olja a régi `&` és `&#` kódokat is a visszamenőleges kompatibilitás miatt.
5. **Placeholderek YAML-ben:** A YAML fájlokban a változókat kötelezően kapcsos zárójelbe kell tenni: `{player}`, `{amount}`. A Java kódban ezeket `TagResolver`-rel fedjük le.
6. **Async Cache-elés:** A `MessageService` induláskor aszinkron (Virtuális szálakon) beolvassa, lefordítja és cache-eli az üzeneteket egy `EnumMap`-be. Tilos futásidőben IO művelettel YAML fájlt olvasni!

---

## 2. A MessageService inicializálása

Minden pluginnak saját `Lang` enummal kell rendelkeznie, ami implementálja a `MessageKey` interfészt.

### 2.1 Saját Lang Enum
```java
package com.myplugin.config;

import com.flarium.api.config.MessageKey;

public enum MyPluginLang implements MessageKey {
    WELCOME("general.welcome"),
    ERROR_NO_PERMISSION("error.no-permission"),
    HELP_COMMANDS("help.commands-list");

    private final String path;

    MyPluginLang(String path) {
        this.path = path;
    }

    @Override
    public String getPath() {
        return path;
    }
}

```

### 2.2 Inicializálás a Főosztályban

A plugin `onEnable()` metódusában példányosítjuk, és injektáljuk a saját prefixünket.

```java
public class MyPlugin extends JavaPlugin {
    private MessageService<MyPluginLang> messageService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        // Saját prefix kiolvasása a config.yml-ből
        String prefix = getConfig().getString("settings.prefix", "<dark_gray>[<white>MyPlugin</white>] <gray>»</gray> ");
        PluginConfig apiConfig = new PluginConfig(prefix);
        
        // MessageService inicializálása
        this.messageService = new MessageService<>(this, MyPluginLang.class, apiConfig);
    }
}

```

---

## 3. A messages.yml Fájl Felépítése

**Kritikus Szabályok:**

* Minden egysoros `general` és `error` üzenet kötelezően a `{prefix} <white>...` formátummal kezdődik.
* A `help` blokk egy többsoros YAML lista, aminek az első sora `{prefix}`, a többi sor formátuma `<dark_gray>▪ <gray>Használat: <#6CAAD3>/parancs...`.

```yaml
general:
  welcome: "{prefix} <white>Üdvözöllek a szerveren, <#6CAAD3>{player}</#6CAAD3>!"
  item-sold: "{prefix} <white>Sikeresen eladtál <#6CAAD3>{amount}x {item}</#6CAAD3> <white>tárgyat!</white>"

error:
  no-permission: "{prefix} <white>Nincs jogod ehhez a parancshoz!"

help:
  commands-list:
    - "{prefix}"
    - "<dark_gray>▪ <gray>Használat: <#6CAAD3>/shop open <dark_gray>- <gray>Bolt megnyitása"

```

---

## 4. Üzenetek Küldése és Komponensek Lekérése

Az Adventure API `TagResolver` rendszerét használjuk.

* `Placeholder.unparsed(key, value)`: Sima szöveghez és számokhoz (nem engedi a játékosnak, hogy színkódokat írjon a nevébe).
* `Placeholder.component(key, component)`: Ha a behelyettesítendő érték is egy már formázott MiniMessage/Adventure `Component`.

### Chat üzenet küldése:

```java
public void sellItem(Player player, int amount, String itemName) {
    messageService.send(player, MyPluginLang.ITEM_SOLD,
        Placeholder.unparsed("amount", String.valueOf(amount)),
        Placeholder.unparsed("item", itemName)
    );
}

```

### Nyers Component lekérése (GUI-khoz, Hologramokhoz, Itemekhez):

Ha nem a chatre akarod küldeni, hanem mondjuk egy Hologram szövegének vagy egy láda nevének szánod, használd a `getMessage` metódust!

```java
Component hologramTitle = messageService.getMessage(MyPluginLang.WELCOME,
    Placeholder.unparsed("player", player.getName())
);

hologram.addLine(new TextLine(hologramTitle));

```

---

## 5. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Chat üzenet** | `player.sendMessage("§cSikeres eladás: " + amount);` | `messageService.send(player, Lang.SOLD, Placeholder.unparsed("amount", String.valueOf(amount)));` |
| **GUI/Item Cím** | `builder.name(lang.getString("title"));` | `builder.name(messageService.getMessage(Lang.TITLE));` |
| **Prefix a kódba** | `player.sendMessage(prefix + " Üdv!");` | Csak a YAML-ben használd: `"{prefix} <white>Üdv!"` |
| **API Config** | `new PluginConfig(this);` | A saját `config.yml`-ből olvasott String átadása: `new PluginConfig(getConfig().getString("settings.prefix"))` |
| **Színkódok** | `&c`, `&#FF0000`, `ChatColor.RED` | `<white>`, `<#6CAAD3>`, `<dark_gray>` (MiniMessage formátum) |
| **Változók** | `%player%`, `${player}` a YAML-ben. | `{player}` (A MessageService ezt regex-szel `<player>` tag-gé alakítja a parse során!) |
| **Help lista** | `player.sendMessage("Help:\n/parancs");` | YAML lista használata a `help.commands-list` alatt. Az API automatikusan kezeli a sortörést! |

## 6. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha egy funkcióhoz üzenetet kell küldened, vagy egy GUI címet kell megadnod, TILOS Java string összefűzést és `§` jeleket használnod!

1. Generáld le az üzenet kulcsát a `Lang` enumban.
2. Írd meg a `messages.yml` fájlban a sort a `{prefix}` placeholderrel és a világos színkódokkal (pl. `<#6CAAD3>`).
3. Java kódban hívd meg a `messageService.send()` metódust (chatre), vagy a `messageService.getMessage()` metódust (Hologramhoz, GUI-hoz).
4. A változókat (pl. `{player}`) mindig `Placeholder.unparsed()` segítségével add át! A prefixet a plugin a saját `config.yml`-jéből olvassa ki induláskor.