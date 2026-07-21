# FlariumAPI — Command API

> **Package:** `com.flarium.api.command`
> **Core Classes:** `CommandNode`, `CommandDispatcher`, `CommandContext`

A FlariumAPI parancskezelő rendszere fa-struktúrájú (`CommandNode`), automatikus Tab Complete-tel (`ArgumentType`) és beépített argumentum-parsolással.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, a parancsok, alparancsok és Tab Complete létrehozására **KIZÁRÓLAG** az itt leírt módszereket használhatod.

| Tilos | Helyette |
|-------|----------|
| `CommandExecutor` + `if (args.length == 1)` | `CommandNode` fa-struktúra |
| `if (args[0].equalsIgnoreCase("give"))` | `addChild(new GiveNode())` külön osztályban |

---

## 1. Architekturális Szabályok

1. **Nincs Spagetti** — Minden alparancs egy külön `CommandNode` leszármazott.
2. **Fa-struktúra** — A parancsok memóriabeli fát alkotnak. A `CommandDispatcher` bejárja ezt a fát.
3. **ArgumentType** — Az alparancsok konstruktorában regisztrálni kell az `ArgumentType`-okat a dinamikus Tab Complete-hez.
4. **Automatikus Tab Complete** — A `CommandNode` az `ArgumentType` alapján ajánlja fel a játékosokat, számokat, vagy enum értékeket.
5. **IoC** — A `CommandNode` osztályok kizárólag konstruktoron keresztül kapják a függőségeket.

---

## 2. API Referencia

### `CommandNode` (absztrakt)

#### Konstruktor

```java
protected CommandNode(String name, String permission, String... aliases)
```

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `name` | `String` | A parancs neve (automatikusan lowercase). |
| `permission` | `String` | A szükséges permission (lehet `null`). |
| `aliases` | `String...` | Alternatív nevek. |

#### `addChild(CommandNode node)`

```java
public void addChild(CommandNode node)
```

Hozzáad egy alparancsot (gyermek node-ot) a fához.

**Példa:**
```java
addChild(new ShopOpenNode(shopManager));
addChild(new ShopReloadNode(messageService));
```

#### `addArgument(String name, ArgumentType<?> arg)`

```java
protected void addArgument(String name, ArgumentType<?> arg)
```

Regisztrál egy argumentumot a Tab Complete-hez és az automatikus parsoláshoz. A Dispatcher a `name` alapján tárolja el a parse-olt értéket.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `name` | `String` | Az argumentum neve (pl. `"target"`). |
| `arg` | `ArgumentType<?>` | Az argumentum típusa (pl. `new PlayerArgument()`). |

**Példa:**
```java
addArgument("target", new PlayerArgument());
addArgument("tier", new EnumArgument<>(ContractTier.class));
```

#### `execute(CommandContext context)` (absztrakt)

```java
public abstract void execute(CommandContext context)
```

A parancs logikája. A Dispatcher már elő-feldolgozta és parse-olta az argumentumokat.

---

### `CommandContext`

A Dispatcher által átadott kontextus objektum.

#### `getSender()`

```java
public CommandSender getSender()
```

Visszaadja a parancs küldőjét.

#### `getPlayer()`

```java
public Player getPlayer()
```

Visszaadja a küldőt `Player`-ként, vagy `null`-t ha konzol.

**Példa:**
```java
Player player = context.getPlayer();
if (player == null) return;
```

#### `getArgument(String name)`

```java
public <T> T getArgument(String name)
```

Visszaadja a Dispatcher által parse-olt argumentumot név alapján.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `name` | `String` | Az argumentum neve (amit az `addArgument`-nél megadtál). |
| **Visszatérés** | `<T> T` | A parse-olt érték (cast-olva). |

**Példa:**
```java
Player target = context.getArgument("target");
ContractTier tier = context.getArgument("tier");
```

#### `getString(int index)`

```java
public String getString(int index)
```

Visszaadja a nyers argumentum stringet index alapján (0 = első argumentum a node után).

---

### `CommandDispatcher`

#### Konstruktor és regisztráció

```java
public CommandDispatcher(JavaPlugin plugin, CommandNode root)
public void register(String commandName)
```

**Példa:**
```java
CommandDispatcher dispatcher = new CommandDispatcher(this, rootCommand);
dispatcher.register("shop");
```

---

## 3. Elérhető ArgumentType Implementációk

| Osztály | Leírás | Példa |
|---------|--------|-------|
| `PlayerArgument` | Online játékosok neveit ajánlja fel. | `new PlayerArgument()` |
| `EnumArgument` | Egy enum összes értékét ajánlja fel. | `new EnumArgument<>(ContractTier.class)` |

---

## 4. Gyakorlati Példák

### 4.1 Gyökér Parancs (Root Node)

```java
public class ShopCommandNode extends CommandNode {
    public ShopCommandNode(MessageService<Lang> messageService, ShopManager shopManager) {
        super("shop", "flarium.shop.admin");

        addChild(new ShopOpenNode(shopManager));
        addChild(new ShopReloadNode(messageService));
    }

    @Override
    public void execute(CommandContext context) {
        // Ez akkor hívódik meg, ha valaki csak /shop-ot ír.
        // Tipikusan help menü küldése.
    }
}
```

### 4.2 Alparancs Argumentumokkal

```java
public class GiveContractNode extends CommandNode {

    public GiveContractNode(ContractManager contractManager) {
        super("give", "perial.contract.admin");

        addArgument("target", new PlayerArgument());
        addArgument("tier", new EnumArgument<>(ContractTier.class));
        addArgument("type", new EnumArgument<>(ContractType.class));
    }

    @Override
    public void execute(CommandContext context) {
        Player target = context.getArgument("target");
        ContractTier tier = context.getArgument("tier");
        ContractType type = context.getArgument("type");

        if (target == null || tier == null || type == null) return;

        // Üzleti logika...
    }
}
```

### 4.3 Regisztráció a Plugin Főosztályában

```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        ShopManager shopManager = new ShopManager();
        ShopCommandNode rootCommand = new ShopCommandNode(messageService, shopManager);

        CommandDispatcher dispatcher = new CommandDispatcher(this, rootCommand);
        dispatcher.register("shop");
    }
}
```

---

## 5. A `plugin.yml` Szabályai

A `plugin.yml` fájlban **csak a gyökér parancsot** kell megadni.

```yaml
commands:
  shop:
    description: Fő bolt parancs
    usage: /shop
    permission: flarium.shop.admin
```

---

## 6. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Alparancsok** | `if (args[0].equals("open")) { ... }` | `addChild(new ShopOpenNode());` külön osztályban |
| **Tab Complete** | `if (args.length == 1) return List.of("open");` | `addArgument("target", new PlayerArgument());` |
| **Sender ellenőrzés** | `if (sender instanceof Player) { ... }` | `Player p = context.getPlayer(); if (p == null) return;` |
| **Argumentumok** | `Bukkit.getPlayerExact(args[1]);` | `context.getArgument("target");` |
| **Regisztráció** | `getCommand("shop").setExecutor(new ShopCommand());` | `new CommandDispatcher(this, root).register("shop");` |

---

## 7. Összefoglaló AI Szabály

**AI Directive:** Ha egy játékos azt kéri, hogy "írj egy /shop give parancsot", hozz létre egy `ShopCommandNode`-ot, adj hozzá egy `GiveCommandNode` gyermeket. A `GiveCommandNode`-ban regisztrálj be `addArgument("target", new PlayerArgument())` típusokat. Az `execute` metódusban a `context.getArgument("target")` metódussal kérd le az argumentumokat! A `plugin.yml`-be csak a `shop` parancs kerüljön!
