# BedWars1058-MobSpawn

A BedWars1058 add-on (Kotlin, Gradle) that lets admins - or anyone in creative - spawn any mob from a GUI and
control it: map damage, despawn timer, attributes and who it attacks. One jar, Minecraft 1.8 up to 26.3.

* Group: `dev.etran` - package `dev.etran.mobspawn`
* Kotlin `2.2.21`, Java 8 bytecode, Gradle (Kotlin DSL), shaded + relocated Kotlin stdlib
* Hard dependency: `BedWars1058`

## Build

```
./gradlew build
```

The server-ready jar is `build/libs/BedWars1058-MobSpawn-<version>.jar` (not the `-plain` one).

## Commands

| Command | What it does |
|---|---|
| `/mobspawn` | Opens the spawn menu (aliases: `/mobs`, `/bwmob`) |
| `/mobspawn spawn <mob> [amount]` | Quick spawn at your position using your current menu settings |
| `/mobspawn edit` | Arms edit mode: hit or right-click a mob to open its settings UI |
| `/mobspawn delete` | Arms delete mode: hit or right-click a mob to remove it |
| `/mobspawn cancel` | Leaves edit/delete mode (modes also time out, see config) |
| `/mobspawn clear [all\|radius]` | Removes mobs managed by the plugin (admin) |
| `/mobspawn reload` | Reloads `config.yml` (admin) |

You can also simply kill a mob yourself.

## Permissions

| Node | Default | Purpose |
|---|---|---|
| `mobspawn.use` | op | Open the UI, spawn, change timer / targeting / map damage, delete |
| `mobspawn.admin` | op | Edit attributes, `clear`, `reload` |

Anyone in **creative** can do everything under `mobspawn.use` (toggle `access.allow-creative`).
Attribute editing needs `mobspawn.admin` unless `access.creative-can-edit-attributes` is `true`.

## The menus

* **Spawn menu** - every spawnable mob on your server version, filterable (all / hostile / passive).
  Left-click = configure & spawn, right-click = quick spawn x1, shift-click = quick spawn x amount.
* **Spawn / edit menu** (same layout for both)
  * *Map damage* ON/OFF - ON lets the mob's explosions and block changes (creepers, ghasts, wither, dragon,
    endermen, ...) destroy the map, even where BedWars1058 would normally protect it. OFF means it never breaks blocks.
  * *Despawn timer* - none by default; adjust in 1s / 10s / 1m steps. A countdown shows in the mob's name like the golems.
  * *Targeting* (aggressive mobs only) - All players (default) / everyone but my team / one player / one BedWars team / nobody.
  * *Attributes* (live mobs) - max health, movement speed, attack damage, follow range, knockback resistance,
    scale (1.20.5+). Left/right = small step, shift = big step.
  * *Spawn & edit attributes* spawns one mob and opens its live menu.
* Mobs you did not spawn through the plugin (BedWars golems, dragons, ...) are left completely alone until you
  actually change something on them.

## How one jar covers 1.8 - 26.3

The plugin compiles against the **1.8.8** API only. Everything that does not exist there is reached by reflection
or by name lookup, so no removed class/constant is ever linked:

* Materials are resolved by name (modern name first, pre-1.13 name + data value as fallback).
* Attributes use `org.bukkit.attribute` on 1.9+ (constant names differ before/after 1.21.3) and CraftBukkit/NMS on 1.8.
* `EntityType` is enumerated reflectively, so renamed mobs (e.g. Pig Zombie -> Zombified Piglin) just work.
* GUIs identify themselves through the inventory *holder*; no `InventoryView` method is ever called.
* BedWars1058 (teams, spectators) is also accessed reflectively and degrades gracefully if it is missing.
* `api-version: 1.13` stops modern servers from treating the plugin as legacy; older servers ignore it.

## Known limits

* Per-mob settings live in memory; they are not saved across a server restart (BedWars arenas reset anyway).
* Not Folia-aware.
* The 26.x and 1.8 code paths are written defensively but need real-server testing (see below).
