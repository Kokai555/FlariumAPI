# FlariumAPI — Sound API

> **Package:** `com.flarium.api.core.util`
> **Core Class:** `SoundUtil`

A `SoundUtil` egységes hangkezelést biztosít Java kódból és YAML konfigurációkból.

---

## 🤖 AI Direktíva

Amikor olyan plugint írsz, ami a FlariumAPI-ra épül, hangok lejátszására **KIZÁRÓLAG** az itt leírt módszereket használhatja.

| Tilos | Helyette |
|-------|----------|
| Manuális `String.split()` és `Sound.valueOf` | `SoundUtil.playSoundFromString(player, configString)` |
| NMS csomagok (`PacketPlayOutStopSound`) | `SoundUtil.stopSound(player, sound)` |

---

## 1. API Referencia — `SoundUtil`

### `playSoundFromString(Player player, String format)`

```java
public static void playSoundFromString(Player player, String format)
```

Lejátszik egy hangot egy config stringből. A rendszer automatikusan lecseréli a pontokat (`.`) aláhúzásra (`_`).

**Formátum:** `[SOUND] HANG_NEVE|Hangerő|Pitch`

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `player` | `Player` | A játékos. |
| `format` | `String` | A config string (pl. `"[SOUND] ENTITY_PLAYER_LEVELUP|1.0|2.0"`). |

**Példa:**
```java
String action = config.getString("success-action");
SoundUtil.playSoundFromString(player, action);
```

---

### `playSound(Player player, Sound sound, float volume, float pitch)`

```java
public static void playSound(Player player, Sound sound, float volume, float pitch)
```

Lejátszik egy hangot a játékosnak.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `player` | `Player` | A játékos. |
| `sound` | `Sound` | A hang típusa. |
| `volume` | `float` | Hangerő (0.0 - 1.0+). |
| `pitch` | `float` | Hangmagasság (0.5 - 2.0). |

**Példa:**
```java
SoundUtil.playSound(player, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
```

---

### `playSoundAtLocation(Location location, Sound sound, float volume, float pitch)`

```java
public static void playSoundAtLocation(Location location, Sound sound, float volume, float pitch)
```

Lejátszik egy hangot egy konkrét lokáción (mindenki hallja a közelben).

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `location` | `Location` | A pozíció. |
| `sound` | `Sound` | A hang típusa. |
| `volume` | `float` | Hangerő. |
| `pitch` | `float` | Hangmagasság. |

**Példa:**
```java
SoundUtil.playSoundAtLocation(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
```

---

### `stopSound(Player player, Sound sound)`

```java
public static void stopSound(Player player, Sound sound)
```

Leállít egy hangot a játékosnál.

| Paraméter | Típus | Leírás |
|-----------|-------|--------|
| `player` | `Player` | A játékos. |
| `sound` | `Sound` | A leállítandó hang. |

**Példa:**
```java
SoundUtil.stopSound(player, Sound.MUSIC_DISC_PIGSTEP);
```

---

## 2. YAML Konfiguráció

```yaml
# Példák config fájlokban:
success-action: "[SOUND] ENTITY.PLAYER.LEVELUP|1.0|2.0"
error-action: "[SOUND] BLOCK_NOTE_BLOCK_BASS|1.0|0.5"
simple-action: "[SOUND] ENTITY_EXPERIENCE_ORB_PICKUP" # Hangerő és pitch nélkül (alapból 1.0)
```

---

## 3. DO vs DON'T

| Funkció | DON'T (Tilos) | DO (Helyes) |
|---------|---------------|-------------|
| **Config beolvasás** | Manuális `String.split()` és `Sound.valueOf` | `SoundUtil.playSoundFromString(player, configString);` |
| **Hang megállítása** | NMS csomagok (`PacketPlayOutStopSound`) | `SoundUtil.stopSound(player, sound);` |

---

## 4. Összefoglaló AI Szabály

**AI Directive:** Ha config fájlból kell hangot lejátszanod, használd a `SoundUtil.playSoundFromString()` metódust a `[SOUND]` szintaxissal. Ha közvetlenül Java kódból, használd a `playSound()` vagy `playSoundAtLocation()` metódusokat. Hang leállításához a `stopSound()` metódust használd — tilos NMS csomagokat írni!
