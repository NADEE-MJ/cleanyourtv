# My TV: customisations on top of cleanyourtv

This fork (`NADEE-MJ/cleanyourtv`, upstream `Gathin23/cleanyourtv`) is set up for **one TV**. Everything specific
to it lives in `my-tv/` (tracked) and `backup/` + `shots/` (git-ignored, private). The rest of the repo is
untouched upstream code, so `git fetch upstream` stays easy.

## The TV

| | |
|---|---|
| Model | TCL QM6K Pro, 2025 (`ro.product.model` "Smart TV Pro") |
| Board / product | G08 / `G08_4K_US`, MediaTek tcl9618 |
| OS | Google TV, Android 14 (SDK 34) |
| Firmware | `tcl9618-user 14 UTT2.250416.001 AU14` |
| Network | Wi-Fi; no plain ADB port 5555, **only encrypted Wireless debugging** (TLS) |
| Stock launcher | `com.google.android.apps.tv.launcherx` (now disabled) |
| Other launcher present | Projectivy (`com.spocky.projengmenu`), user-installed, left alone |
| Controlled from | a Mac mini (macOS 27, Apple silicon) on Ethernet, same subnet as the TV |
| Upstream snapshot | `3c122bc073f7a9a83de251621e6e31d90dd96109` (2026-11-06) |

## State after the 2026-11-06 session

* 26 packages disabled (never uninstalled), see `debloat.txt`. Applied in four batches (`batches/`).
* Stock launcher `launcherx` and the setup wizard `setupwraith` disabled.
* Launcher `com.gathin.tvhome` v3.4 (built from this repo) is Home.
* `HomeKeyService` (accessibility, key filter only) is on, with `theme/config.json`: Gear -> Settings, YouTube button -> YouTube.
* Survived a reboot. Verified with `my-tv/scripts/tv-status.sh` and `tools/check.py` ("nothing drifted").
* Kept on purpose: Netflix, Chromecast, AirPlay, Google Assistant, the Google TV phone remote service
  (it is what the iPhone Google TV/Home app uses), the TCL game bar.
* ADB closed at the end of the session (Wireless + USB debugging off). Pairing was left in place on purpose.

## Files

| Path | What |
|---|---|
| `debloat.txt` | Combined list. Use it with `tools/check.py` after a firmware update. |
| `batches/batch-{A,B,C,D}-*.txt` | The same list split into small batches, in the order they were applied. Undo records exist for these. |
| `theme/config.json` | Remote button mapping pushed with `tools/theme.sh my-tv/theme`. |
| `scripts/env.sh` | `source` it: JDK, SDK, adb, and the **fixed** `TV_HOST=127.0.0.1:25560`. |
| `scripts/connect-tv.sh` | Connect / pair / stop. Works around macOS Local Network privacy. |
| `scripts/ncrelay.sh` | The loopback relay (built from Apple's `/usr/bin/nc`). |
| `scripts/tv-status.sh` | Read-only health + drift report. |
| `RUNBOOK.md` | How to redo or repair everything, with the gotchas from the first run. |
| `LOG.md` | Dated log of what was done and why. Append to it. |
| `prompts/ui-rewrite.md` | Starting prompt for the launcher UI rewrite session. |
| `../.claude/skills/my-tv/SKILL.md` | Skill so an assistant picks all this up automatically. |
| `../backup/2026-11-06/` (ignored) | Package lists, settings and getprop dumps taken before any change, plus copies of the undo records. **Contains the TV's serial and device ids.** |
| `../shots/2026-11-06/` (ignored) | Before/after screenshots. |
| `../backup/tv.env` (ignored) | `TV_IP` and the mDNS service name. |

## Undo records (important)

`tools/restore.sh` finds its record by **host + the absolute path of the list file**. Records live in
`~/.cleanyourtv/` (copies in `backup/2026-11-06/recovery/`). They are filed under host `127.0.0.1:25560`
and the paths `~/code/random/cleanyourtv/my-tv/batches/batch-*.txt`. If you move the repo, or connect on a
different local port, restore will not find them (see RUNBOOK "Restoring"). Do not rename or relocate these
files casually.
