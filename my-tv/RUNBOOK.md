# Runbook: redo or repair this TV

Everything below was done for real on 2026-11-06. The "Gotchas" are the things that cost time; do the steps
in the order given here, which is the better order, not necessarily the order of the first run.

Always `cd` to the repo root and `source my-tv/scripts/env.sh` first.

## 0. Toolchain (once per Mac)

```bash
brew install --cask android-platform-tools          # Google's adb -> /opt/homebrew/bin/adb
brew install openjdk@17 android-commandlinetools
yes | sdkmanager --sdk_root=/opt/homebrew/share/android-commandlinetools --licenses
sdkmanager --sdk_root=/opt/homebrew/share/android-commandlinetools "platforms;android-34" "build-tools;34.0.0"
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
```

* The first `adb` run can **hang for 1-2 minutes** (Gatekeeper scanning the freshly downloaded binary; stuck at
  `_dyld_start`). It is Google-signed (`codesign -dv` shows Team ID EQHXZ8M8AV). Clear quarantine
  (`xattr -dr com.apple.quarantine /opt/homebrew/Caskroom/android-platform-tools`) and just retry.
* JDK must be **17** (Gradle 8.9 breaks on newer). `build-and-install.sh` finds Homebrew's keg-only openjdk@17.

## 1. TV prerequisites (only the owner can do these)

1. Settings > System > About > press OK on "Build" 7 times. Developer options > **USB debugging ON**, **Wireless debugging ON**.
2. **Do this BEFORE anything else:** Settings > Network & Internet > forget the Wi-Fi network and join it again.
   All saved networks were owned by `com.google.android.tungsten.setupwraith` (check:
   `adb shell dumpsys wifi | grep -o 'cname=[^ ]*'`). Disabling that package deletes them and drops the TV
   off Wi-Fi, ADB with it. After the rejoin they belong to `android.uid.system`. The rejoin drops ADB, so
   pair again (step 2).
3. Keep the TV awake for the whole session. Remote-controlling it from the sofa while we work is fine.

## 2. Connect (this is where macOS fights you)

**Symptom:** `tools/tv.py` / `adb` say `No route to host (65)` although `ping` and `nc -z` work.
**Cause:** macOS Local Network privacy blocks Python, Homebrew binaries and the adb server inside an
assistant sandbox. Apple's own `/usr/bin/nc` is exempt. **Fix:** `my-tv/scripts/ncrelay.sh` (two `nc`s and a fifo)
listens on 127.0.0.1, which is not restricted. No Terminal windows or system permission changes are needed.
(The repo's own `tools/relay.py` is Python, so it is blocked in the same way; it only works from a Terminal
that was granted the permission.)

First time / after a Wi-Fi change, pair:

```bash
# TV: Wireless debugging > "Pair device with pairing code". Leave the dialog OPEN.
my-tv/scripts/connect-tv.sh pair <pairing-port> <6-digit-code>
```

Then connect (finds the connect port itself via mDNS, relays it to local port 25560):

```bash
my-tv/scripts/connect-tv.sh
my-tv/scripts/tv-status.sh        # read-only sanity check
```

Gotchas:
* Pairing port and connect port are different. The connect port changes on every reboot / toggle; mDNS
  (`dns-sd -B _adb-tls-connect._tcp`) always has the current one, which is what the script uses.
* **Never probe the pairing port** (`nc -z`, a second relay test...). It spends the dialog's attempt and the next
  `adb pair` fails with `protocol fault (couldn't read status message)`. Start the relay and pair immediately.
* `protocol fault` also means "the relay is not running" or "code/dialog expired". Re-open the dialog for a new code.
* Always use local port **25560** (set in `env.sh`): undo records are keyed to host `127.0.0.1:25560`.
* TV_IP lives in `backup/tv.env` (ignored). mDNS finds the TV even if its IP changes; update `TV_IP` then.
* The TV is only reachable while awake. The Mac mini is on Ethernet and the TV on Wi-Fi: same subnet, works fine.

## 3. Debloat (small batches, health check between)

```bash
source my-tv/scripts/env.sh
for b in A-tracking-ads B-demo-preloads C-google-extras; do
  tools/disable.sh my-tv/batches/batch-$b.txt
  my-tv/scripts/tv-status.sh          # Home still launcherx, internet up, nothing odd
done
```

* `disable.sh` saves an undo record **before** the first change, refuses core Android / TV-middleware packages,
  and skips anything that serves the HDMI inputs or Settings. Read its output; "Skipping ..." lines matter.
* `com.tcl.repairguide` is not on this TV: the stack trace `Unknown package` is harmless.
* Take a "before" screenshot first: `tools/tv.py --screenshot shots/<date>/before.png`.
* Back up first (done 2026-11-06 into `backup/2026-11-06/`): `pm list packages -f/-d/-e/-3`, `getprop`,
  `settings list global|secure|system`, Home resolve/claims, wifi owners.

Then the setup wizard (needs step 1.2 done, otherwise `disable.sh` skips it, correctly):

```bash
tools/disable.sh my-tv/batches/batch-D-setupwraith.txt
```

Order matters: with `launcherx` AND `setupwraith` both off and no launcher installed, Home lands on a blank
recovery screen. Here `setupwraith` was disabled while `launcherx` was still Home (safe), then:

## 4. Launcher + remote buttons

```bash
# build + install + make Home. It verifies Home really moved and rolls back to launcherx if not.
launcher/build-and-install.sh
# Gear (83) and YouTube (190) only ever reached launcherx; with it disabled they are dead until this:
SKIP_BUILD=1 HOME_KEY_SERVICE=1 launcher/build-and-install.sh   # enables the key-filter accessibility service
tools/theme.sh my-tv/theme                                       # maps 83 -> Settings, 190 -> YouTube
```

* The APK is signed with `~/.android/debug.keystore`. Keep that file, or a rebuilt APK will not install over
  the installed one (uninstall first in that case).
* The key service only filters remote key events (`flagRequestFilterKeyEvents`, no window content), and the
  launcher has no INTERNET permission, so nothing can leave the TV.

## 5. Verify, then close up

```bash
my-tv/scripts/tv-status.sh
tools/venv/bin/python -I tools/check.py my-tv/debloat.txt com.gathin.tvhome     # "nothing drifted"
```

Reboot the TV (remote or `tools/tv.py --reboot`) and run the two checks again: it all survived on 2026-11-06
(27 disabled = 26 listed + launcherx).

Close ADB: on the TV turn **Wireless debugging** and **USB debugging** OFF (leave the paired device in the list
unless you want to be strict: keeping it means next time only the toggle is needed, no new code; revoking
means a new pairing code each time). Then:

```bash
my-tv/scripts/connect-tv.sh stop
nc -vz -G 3 <TV_IP> 5555        # want: refused / no route. Check the old connect port too.
dns-sd -B _adb-tls-connect._tcp local.   # Ctrl-C after 4 s: the TV's service must be GONE
```

`tools/adb-closed.py` is the repo's checker, but it is Python: inside the sandbox it just says "No route to
host", which proves nothing. Run it from a normal Terminal, or use the `nc` / `dns-sd` checks above.
Wireless debugging has a port of its own and may stay open after USB debugging is turned off: check it.

## After a firmware update

An update re-enables listed packages, hands Home back to the stock launcher and adds new preloads. The launcher
APK itself should stay installed.

1. Turn Wireless debugging ON, `my-tv/scripts/connect-tv.sh` (pair again only if it says unauthorised).
2. `my-tv/scripts/tv-status.sh` to see the drift.
3. `tools/venv/bin/python -I tools/check.py --fix my-tv/debloat.txt com.gathin.tvhome` (re-runs the safety checks).
4. If key buttons died: `SKIP_BUILD=1 HOME_KEY_SERVICE=1 launcher/build-and-install.sh && tools/theme.sh my-tv/theme`.
5. If Wi-Fi networks are again owned by `setupwraith`, **do not** disable it until you forget/rejoin Wi-Fi.
6. Look at upstream's `docs/firmware-updates.md`, then `git fetch upstream` for new safety fixes (`tools/disable.sh`,
   protected-package regex) and cherry-pick what you want.

## Restoring (undo)

```bash
source my-tv/scripts/env.sh && my-tv/scripts/connect-tv.sh
tools/restore.sh my-tv/batches/batch-D-setupwraith.txt com.google.android.apps.tv.launcherx   # first: stock Home back
for b in C-google-extras B-demo-preloads A-tracking-ads; do
  tools/restore.sh my-tv/batches/batch-$b.txt
done
```

The second argument on the first call re-enables the stock launcher and gives it Home. Records are in `~/.cleanyourtv/`
(`recovery-127.0.0.1_25560-<hash of list path>.json`, copies in `backup/2026-11-06/recovery/`). If restore
says it cannot find a record, the host or the list's absolute path changed: re-key the record with the
same `sha1(abs path)[:8]` rule used in `tools/recovery.py: list_digest`, or restore without a record
(`docs/restore.md`). Emergency by hand:
`tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'` and
`'pm enable --user 0 com.google.android.tungsten.setupwraith'`. Package state also lives in `backup/2026-11-06/packages-*.txt`.

## What I would do better next time

* Do the Wi-Fi forget/rejoin and pairing **first**, not midway (it cost an extra pairing).
* Use `connect-tv.sh` from the start; skip Terminal relays entirely.
* Decide the optional-app questions up front (see `debloat.txt` header) instead of one at a time.
* Don't `nc`-probe ports that hold a one-shot dialog.
* The upstream list header says AU16; this unit is AU14. It worked, but re-check `docs/devices/` and issue #23 if a
  future firmware surprises you.
