# FlariumAPI - Command API Használati Útmutató AI számára

Ez a dokumentum a FlariumAPI `CommandNode` és `ArgumentType` alapú parancskezelő rendszerének használatát írja le. 
**AI Direktíva:** Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, a parancsok, alparancsok és Tab Complete létrehozására **KIZÁRÓLAG** az itt leírt módszereket használhatod. Tilos a `CommandExecutor` és az `if (args.length == 1)` spagettikód!

## 1. Architekturális Szabályok (Szigorú)

1. **Nincs Spagetti:** Tilos az `if (args[0].equalsIgnoreCase("give"))` formátum. Minden alparancs egy külön `CommandNode` leszármazott.
2. **Fa-struktúra:** A parancsok memóriabeli fát alkotnak. A `CommandDispatcher` bejárja ezt a fát.
3. **ArgumentType:** Az alparancsok konstruktorában regisztrálni kell az `ArgumentType`-okat a dinamikus Tab Complete-hez. (pl. `addArgument(new PlayerArgument())`).
4. **Tab Complete:** A tab complete automatikus. A `CommandNode` az `ArgumentType` alapján ajánlja fel a játékosokat, számokat, vagy enum értékeket.
5. **IoC:** A `CommandNode` osztályok kizárólag konstruktoron keresztül kapják a függőségeket (pl. `MessageService`, `Manager`).

## 2. A Parancsok Felépítése

Minden parancs egy gyökér `CommandNode`, amibe hozzáadjuk az alparancsokat (gyermekeket).

### 2.1 Gyökér Parancs (Root Node)
A fő parancs (pl. `/shop`) a plugin főosztályában regisztrálódik.

```java
public class ShopCommandNode extends CommandNode {
    public ShopCommandNode(MessageService<Lang> messageService, ShopManager shopManager) {
        super("shop", "flarium.shop.admin"); // Név, permission
        
        // Gyermekek hozzáadása
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

### 2.2 Alparancs (Child Node) és Argumentumok
Egy konkrét alparancs (pl. `/shop give <játékos> <tier>`).

```java
public class GiveContractNode extends CommandNode {

    public GiveContractNode(ContractManager contractManager) {
        super("give", "perial.contract.admin");
        
        // Argumentumok regisztrálása NÉVVEL és Típussal!
        addArgument("target", new PlayerArgument());
        addArgument("tier", new EnumArgument<>(ContractTier.class));
        addArgument("type", new EnumArgument<>(ContractType.class));
    }

    @Override
    public void execute(CommandContext context) {
        // A Dispatcher már elő-feldolgozta és parse-olta az argumentumokat!
        // Csak be kell kérni őket a Context-ből a nevük alapján.
        Player target = context.getArgument("target");
        ContractTier tier = context.getArgument("tier");
        ContractType type = context.getArgument("type");
        
        if (target == null || tier == null || type == null) return;
        
        // Üzleti logika...
    }
}
```
Egy konkrét alparancs (pl. `/shop give <játékos> <tier>`).

```java
public class GiveContractNode extends CommandNode {

    public GiveContractNode(ContractManager contractManager) {
        super("give", "perial.contract.admin");
        
        // ÚJ: Argumentumok regisztrálása a Tab Complete-hez!
        addArgument(new PlayerArgument()); // 1. arg: Játékos neve
        addArgument(new EnumArgument<>(ContractTier.class)); // 2. arg: COMMON, RARE, stb.
        addArgument(new EnumArgument<>(ContractType.class)); // 3. arg: KILL_MOB, stb.
    }

    @Override
    public void execute(CommandContext context) {
        // A Dispatcher levágta a "give" szót, így a 0. index az első argumentum!
        Player target = Bukkit.getPlayerExact(context.getString(0));
        ContractTier tier = ContractTier.valueOf(context.getString(1).toUpperCase());
        // ...
    }
}
```

## 3. Elérhető ArgumentType Implementációk

Az API-ban az alábbi alapértelmezett ArgumentType-ok érhetőek el:
* `PlayerArgument()`: Online játékosok neveit ajánlja fel.
* `EnumArgument<>(MyEnum.class)`: Az enum összes értékét ajánlja fel.
* `IntegerArgument()`: Számokat ajánlja fel (ha van ilyen, vagy üres listát ami engedi a beírást).
* `BooleanArgument()`: `true` és `false` értékeket ajánl fel.

## 4. Regisztráció a Plugin Főosztályában

A `CommandDispatcher` segítségével regisztráljuk a parancsot a Paper API-ba. A `plugin.yml`-ben kötelezően szerepelnie kell a gyökér parancsnak (pl. `shop`).

```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // ... MessageService init ...
        
        ShopManager shopManager = new ShopManager();
        ShopCommandNode rootCommand = new ShopCommandNode(messageService, shopManager);
        
        // Dispatcher létrehozása és regisztráció
        CommandDispatcher dispatcher = new CommandDispatcher(this, rootCommand);
        dispatcher.register("shop"); // A plugin.yml-ben megadott parancs neve
    }
}
```

## 5. A plugin.yml Szabályai

A `plugin.yml` fájlban **csak a gyökér parancsot** kell megadni. Az alparancsokat (open, reload, stb.) nem szabad beírni!

```yaml
commands:
  shop:
    description: Fő bolt parancs
    usage: /shop
    permission: flarium.shop.admin
```

---

## 6. AI Generálási Irányelvek (DO vs DON'T)

| Funkció | DON'T (Tilos / Elavult) | DO (Helyes / Modern Flarium API) |
| --- | --- | --- |
| **Alparancsok** | `if (args[0].equals("open")) { ... }` | `addChild(new ShopOpenNode());` egy külön osztályban |
| **Tab Complete** | `if (args.length == 1) return List.of("open", "reload");` | `addArgument("target", new PlayerArgument());` használata a Node konstruktorában. |
| **Sender ellenőrzés** | `if (sender instanceof Player) { Player p = (Player) sender; }` | `Player player = context.getPlayer(); if (player == null) return;` |
| **Argumentumok** | `Player target = Bukkit.getPlayerExact(args[1]);` | `Player target = context.getArgument("target");` *(A Dispatcher már leparse-olta!)* |
| **Regisztráció** | `getCommand("shop").setExecutor(new ShopCommand());` | `new CommandDispatcher(this, rootCommand).register("shop");` |

## 7. Összefoglaló AI Prompt Szabály

**AI Directive:** Ha egy játékos azt kéri, hogy "írj egy /shop give parancsot", hozz létre egy `ShopCommandNode`-ot, adj hozzá egy `GiveCommandNode` gyermeket. A `GiveCommandNode`-ban regisztrálj be `addArgument("target", new PlayerArgument())` típusokat. Az `execute` metódusban a `context.getArgument("target")` metódussal kérd le az argumentumokat! A `plugin.yml`-be csak a `shop` parancs kerüljön!