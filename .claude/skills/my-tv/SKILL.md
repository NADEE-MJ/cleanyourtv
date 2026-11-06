---
name: my-tv
description: Work on the owner's TCL QM6K Pro Google TV (G08, Android 14) in this fork. Use when asked to connect to the TV, adb, debloat, check or fix drift after a firmware update, restore, rebuild or reinstall the launcher, change the remote button mapping, or anything about "my TV" / the launcher on it.
---

# my-tv: operating the owner's TV from this repo

Read `my-tv/README.md` (profile, state, file map) and `my-tv/RUNBOOK.md` (exact commands, gotchas) before acting.
They are the source of truth; this file is the short version.

## Rules

1. **The TV is a real device in someone's living room.** Back up before changing anything, change in small batches,
   check health between (`my-tv/scripts/tv-status.sh`), and ask before anything that is a preference call.
2. **Use the repo's tools, never raw `pm disable-user` loops**: `tools/disable.sh`, `tools/restore.sh`,
   `tools/check.py`, `tools/theme.sh`, `launcher/build-and-install.sh`. They carry the safety checks
   (core packages, HDMI/Settings handlers, Wi-Fi owned by setupwraith, Home guard). Read their "Skipping ..." output.
3. **Never disable** core Android/TV-middleware packages, `com.tcl.providers.config`, `com.tcl.initsetup`,
   `com.tcl.factory.view`, or `com.tcl.tv`/input services. Never launch factory/AT packages.
4. **Never disable `setupwraith` or the stock launcher unless the new launcher is installed AND the Wi-Fi networks
   are not owned by setupwraith** (`dumpsys wifi | grep -o 'cname=[^ ]*'`).
5. **No secrets in git.** This fork is public. TV IP, serial, android_id, pairing codes, Wi-Fi names go only in
   `backup/` or `shots/` (git-ignored). `git status` before every commit; `git diff --cached | grep -nE '192\.168|serial|android_id'`.
6. Close ADB when done (Wireless + USB debugging OFF on the TV) and say so.
7. Do not run anything on the TV from an unverified source (add-ons in `addons/` are third-party code).

## Connecting (macOS sandbox gotcha)

```bash
source my-tv/scripts/env.sh        # fixed TV_HOST=127.0.0.1:25560, adb, JDK 17, SDK
my-tv/scripts/connect-tv.sh        # mDNS port discovery + nc relay + adb connect
my-tv/scripts/connect-tv.sh pair <pairing-port> <code>   # if unauthorised; dialog must stay open
my-tv/scripts/connect-tv.sh stop   # when finished
```

Python/adb inside the sandbox get "No route to host" (macOS Local Network privacy); only `/usr/bin/nc` is exempt,
hence the relay. Do not `nc`-probe the pairing port. Keep local port 25560 (undo records are keyed to it).
The TV must be awake with Wireless debugging ON (the user flips that switch; you cannot).

## Common tasks

| Task | Command |
|---|---|
| Health / drift report | `my-tv/scripts/tv-status.sh` |
| Drift vs list (read-only) | `tools/venv/bin/python -I tools/check.py my-tv/debloat.txt com.gathin.tvhome` |
| Fix drift after a firmware update | same with `--fix` (then re-run `HOME_KEY_SERVICE` install + `tools/theme.sh my-tv/theme` if keys died) |
| Rebuild + reinstall launcher | `launcher/build-and-install.sh` (add `SKIP_BUILD=1 HOME_KEY_SERVICE=1` to reuse the APK with key helper) |
| Change remote mapping | edit `my-tv/theme/config.json`, `tools/theme.sh my-tv/theme` |
| Screenshot | `tools/venv/bin/python -I tools/tv.py --screenshot shots/<date>/name.png` |
| Undo everything | RUNBOOK "Restoring" |
| New list entry | add to `my-tv/debloat.txt` **and** a new `my-tv/batches/batch-*.txt`; apply the batch with `disable.sh` |

Look at screenshots (Read the PNG) to verify visual changes; do not assume.

## When you change something

Append a dated entry to `my-tv/LOG.md` (what / why / how verified), update `my-tv/debloat.txt` if the list changed,
and commit to the fork. Upstream is `upstream` (Gathin23); bring in fixes with `git fetch upstream` and cherry-pick,
do not merge wholesale (it is a very busy repo of other people's TV lists and add-ons).
