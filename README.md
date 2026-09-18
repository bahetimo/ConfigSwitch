# ConfigSwitch

[![Build](https://github.com/bahetimo/ConfigSwitch/actions/workflows/build.yml/badge.svg)](https://github.com/bahetimo/ConfigSwitch/actions/workflows/build.yml)

Synchronize your mod configs and `options.txt` between modpacks. Push your tuned
settings to a global repository once, then fetch them into any other instance —
**your configs follow you, not the pack.**

## The problem

Every modpack has its own `config/` folder and `options.txt`. When you switch packs
(or install a new one), all your carefully tuned settings are gone and you have to
redo them — keybinds, graphics options, and the configs of dozens of mods.

## What it does

ConfigSwitch keeps a **global repository** of your configs, stored outside any single
pack, and moves files in either direction:

- **Push** — send the current pack's configs into the global repository
- **Fetch** — pull the global repository's configs into the current pack

## Usage

1. Press **H** in-game to open ConfigSwitch.
2. Tick the mods you want to sync.
3. Click **Push** to save them to the global repository, or **Fetch** to load them.

Two more screens:

- **Backup** — browse past snapshots and restore one. Every restore is itself backed
  up first, so it can be undone.
- **Settings** — change the repository location and how many backups to keep.

## Notes

- **`options.txt` applies immediately. Mod configs take effect after a game restart**,
  because mods read their configs at startup.
- Configs that can't be matched to an installed mod are grouped as **uncategorized**
  and can still be synced.
- Existing config files are **backed up before being overwritten**; the most recent
  ones are kept and can be restored from the Backup screen.

## Requirements

- Minecraft **1.21.1**
- Fabric Loader **0.15.10** or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)

## Reporting issues

Please include your `logs/latest.log` — it shows what ConfigSwitch was doing when
things went wrong.

## License

[MIT](LICENSE.txt)
