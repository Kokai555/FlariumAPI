# FlariumAPI — Üzenetrendszer (MessageService)

> **Package:** `com.flarium.api.ui.message`
> **Core Classes:** `MessageService`, `MessageKey`, `PluginConfig`

A `MessageService` a FlariumAPI központosított üzenetkezelője. Kezeli a prefixeket, a `{placeholder}` cserét, és a MiniMessage formázást. Induláskor aszinkron (virtuális szálakon) beolvassa, lefordítja és cache-eli az üzeneteket.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, az üzenetek küldésére, formázására és a konfigurációból (YAML) történő lokalizációra **KIZÁRÓLAG** az itt leírt módszereket használhatja.

| Tilos | Helyette |
|-------|----------|
| `player.sendMessage("§cHiba: " + nevet)` | `messageService.send(player, Lang.ERROR, Placeholder.unparsed("name", nevet))` |
| Hardcode-olt prefix a Java kódban | `{prefix}` placeholder a YAML-ben |

---

## 1. Architekturális Szabályok

1. **Nincs String összefűzés a Java-ban** — Tilos a `player.sendMessage("§cHiba: " + nevet)` formátum.
2. **Nincs hardcode-olt prefix** — A YAML fájlban kell használni a `{prefix}` placeholdert, amit az API automatikusan felold.
3. **Saját Prefix (IoC)** — Minden plugin a saját `config.yml`-jéből olvassa ki a prefixet.
4. **Színkezelés (MiniMessage)** — A szövegek alapszíne kötelezően `<white>`. Az API a háttérben automatikusan parse-olja a régi `&` és `&#` kódokat is.
5. **Placeholderek YAML-ben** — A YAML fájlokban a változókat kötelezően kapcsos zárójelbe kell tenni: `{player}`, `{amount}`.
6. **Async Cache-elés** — Tilos futásidőben IO művelettel YAML fájlt olvasni!

---

## 2. API Referencia — `MessageService<T>`

### Konstruktor

```java
public MessageService(JavaPlugin plugin, Class<T> enumClass, PluginConfig config)
```

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `plugin` | `JavaPlugin` | A plugin példány. |
| `enumClass` | `Class<T>` | A `Lang` enum osztály (implementálja a `MessageKey`-t). |
| `config` | `PluginConfig` | A prefixet tartalmazó konfiguráció. |

---

### `send(CommandSender sender, T key, TagResolver... resolvers)`

```java
public void send(CommandSender sender, T key, TagResolver... resolvers)
```

Üzenetet küld a megadott címzettnek. Automatikusan hozzáadja a prefixet.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `sender` | `CommandSender` | A címzett (játékos vagy konzol). |
| `key` | `T` | A `Lang` enum kulcs. |
| `resolvers` | `TagResolver...` | A placeholder értékek. |

**Példa:**
```java
public void sellItem(Player player, int amount, String itemName) {
    messageService.send(player, MyPluginLang.ITEM_SOLD,
        Placeholder.unparsed("amount", String.valueOf(amount)),
        Placeholder.unparsed("item", itemName)
    );
}
```

---

### `reload(PluginConfig config)`

```java
public CompletableFuture<Void> reload(PluginConfig config)
```

Újratölti az üzeneteket a `messages.yml` fájlból aszinkron módon.

---

## 3. A `MessageKey` Interfész

```java
public interface MessageKey {
    String getPath();
}
```

Minden pluginnak saját `Lang` enummal kell rendelkeznie, ami implementálja ezt az interfészt.

**Példa:**
```java
package com.myplugin.config;

import com.flarium.api.ui.message.MessageKey;

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

---

## 4. A `messages.yml` Fájl Felépítése

**Kritikus Szabályok:**

1. Minden üzenet alapszíne kötelezően `&f` (fehér).
2. Minden placeholder (pl. `{player}`, `{amount}`) a prefix **világosabb (Szín 2)** színét használja (pl. `&#FF6666`).
3. A parancs használati üzenetekben a paraméterek formátuma: `<player>` (kötelező) és `[reason]` (opcionális).

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

---

## 5. Inicializálás a Főosztályban

```java
public class MyPlugin extends JavaPlugin {
    private MessageService<MyPluginLang> messageService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String prefix = getConfig().getString("prefix", "<gradient:#FF0000:#FF6666:#FF0000><b>fmMyPlugin</b></gradient>");
        PluginConfig apiConfig = new PluginConfig(prefix);

        this.messageService = new MessageService<>(this, MyPluginLang.class, apiConfig);
    }
}
```

---

## 6. TagResolver-ek (Placeholder értékek)

Az Adventure API `TagResolver` rendszerét használjuk:

| Metódus | Használat |
|---------|----------|
| `Placeholder.unparsed(key, value)` | Sima szöveghez és számokhoz (nem engedi a játékosnak, hogy színkódokat írjon a nevébe). |
| `Placeholder.component(key, component)` | Ha a behelyettesítendő érték is egy már formázott `Component`. |

---

## 7. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Chat üzenet** | `player.sendMessage("§cSikeres eladás: " + amount);` | `messageService.send(player, Lang.SOLD, Placeholder.unparsed("amount", String.valueOf(amount)));` |
| **GUI/Item Cím** | `builder.name(lang.getString("title"));` | `builder.name(messageService.getMessage(Lang.TITLE));` |
| **Prefix a kódba** | `player.sendMessage(prefix + " Üdv!");` | Csak a YAML-ben: `"{prefix} &fÜdv!"` |
| **API Config** | `new PluginConfig(this);` | `new PluginConfig(getConfig().getString("prefix"))` |
| **Színkódok** | `&c`, `&#FF0000`, `ChatColor.RED` | `&f` (alapszín), `&#FF6666` (placeholder kiemelés) |
| **Változók** | `%player%`, `${player}` a YAML-ben | `{player}` (az API `<player>` tag-gé alakítja) |
| **Help lista** | `player.sendMessage("Help:\n/parancs");` | YAML lista a `usage` alatt |
| **Kommentek** | `// komment a kódban` | Soha ne írj kommentet — önmagát magyarázó kód |

---

## 8. Összefoglaló AI Szabály

**AI Directive:** Ha egy funkcióhoz üzenetet kell küldened, vagy egy GUI címet kell megadnod, TILOS Java string összefűzést és `§` jeleket használnod!

1. Generáld le az üzenet kulcsát a `Lang` enumban.
2. Írd meg a `messages.yml` fájlban a sort a `{prefix}` placeholderrel és a világos színkódokkal.
3. Java kódban hívd meg a `messageService.send()` metódust.
4. A változókat (pl. `{player}`) mindig `Placeholder.unparsed()` segítségével add át! A prefixet a plugin a saját `config.yml`-jéből olvassa ki induláskor.
