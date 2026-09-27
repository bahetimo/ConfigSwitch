# ConfigSwitch

[![Build](https://github.com/bahetimo/ConfigSwitch/actions/workflows/build.yml/badge.svg)](https://github.com/bahetimo/ConfigSwitch/actions/workflows/build.yml)

**Your Minecraft configs, portable.**

Every modpack has its own `config/` folder and `options.txt`. Switch packs, and all
your tuned settings are gone — keybinds, graphics, and the configs of dozens of mods.
ConfigSwitch keeps a **global repository** of your configs, outside any single pack,
and moves them in either direction:

- **Push** — save this pack's configs into the repository
- **Fetch** — load the repository's configs into this pack

Your configs follow you, not the pack.

![Config screen](https://raw.githubusercontent.com/bahetimo/ConfigSwitch/main/res/ConfigScreen.png)

## How to use it

1. Press **H** in-game
2. Tick the mods you want to sync
3. Hit **Push** to save them, or **Fetch** to load them

That's it — no config files to edit by hand.

## Features

### Sync by mod, not by file

The list groups configs **by mod**, so you tick *sodium* — not six separate `.json`
files. Select all / invert and a search box are there for when the list gets long.

### Nothing is lost when it doesn't match

Configs that can't be matched to an installed mod go to **Uncategorized**, and
can still be synced.

Leftovers from mods you **no longer have installed** are kept out of the way
instead — hidden rather than deleted, with a count shown on the Uncategorized
row. The files stay on disk, and they come back the moment you install that mod
again.

### You can teach it what things are

Some config files simply don't look like their mod — `yacl.json5` belongs to
*Yet Another Config Lib*. Press **Classify** on the Uncategorized row: pick a
file on the left, pick the mod it belongs to on the right (the most likely ones
are sorted to the top, and you can search by mod id or name), or mark it as
**Ignore**.

- **Nothing is written until you press Done** — pick wrong and you can change
  your mind.
- Your choices are **remembered across packs and across restarts**, so you only
  do it once. They live in `mappings.txt` next to your other ConfigSwitch
  settings — the built-in mappings are in there too, so you can see and edit
  them.

![Classify screen](https://raw.githubusercontent.com/bahetimo/ConfigSwitch/main/res/CategorizeScreen.png)

### Everything is backed up before it's overwritten

Every Push / Fetch copies the old files to a timestamped backup first, and older
backups are pruned automatically — so a bad sync is never permanent.

### Restore any snapshot

The **Backup** screen lists past snapshots from both sides. Restoring one is itself
backed up first, so **a restore can be undone**.

![Backup screen](https://raw.githubusercontent.com/bahetimo/ConfigSwitch/main/res/BackupScreen.png)

### Configurable

Change where the repository lives, and how many backups to keep.

![Settings screen](https://raw.githubusercontent.com/bahetimo/ConfigSwitch/main/res/SettingsScreen.png)

## Requirements

- Minecraft **1.21.1**
- Fabric Loader **0.15.10** or newer
- Fabric API
- **Windows** (for now)

## Known limitations

- **Mod configs take effect after a game restart** — `options.txt` applies
  immediately, but mods read their configs at startup
- Configs stored in **subfolders** of `config/` aren't scanned yet
- Config files whose names don't resemble their mod id, and that aren't in the
  built-in mappings, still end up in *Uncategorized*
- **Classify only offers mods you have installed** — a config for a mod you
  haven't installed yet can't be aimed at it in advance
- Windows only for now

## Reporting issues

Please include your `logs/latest.log` — or just the lines containing
`[ConfigSwitch]`. That prefix is how our entries can be told apart from the other
mods', since `latest.log` has no per-mod names in it.

If the problem is **"nothing happened"** — a config didn't change, a button did
nothing, a sync had no effect — `latest.log` usually isn't enough. In that case,
add `-Dconfigswitch.debug=true` to the instance's JVM arguments (launcher →
advanced options), start the game, reproduce the problem **once**, quit, and
attach **`logs/configswitch-debug.log`**. It contains *only* ConfigSwitch's own
log, including detailed step-by-step entries. Then remove the argument again —
it's a one-off and does not persist.

## License

MIT — see [LICENSE.txt](https://github.com/bahetimo/ConfigSwitch/blob/main/LICENSE.txt)

## For contributors

The screenshots live in [`res/`](res/) so they stay out of the built jar.
