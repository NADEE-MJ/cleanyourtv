# Launcher UI revamp: design

Agreed with the owner on 2026-11-06 from the mockup board (private artifact, rounds 1-3). Branch `ui-rewrite`.
This is the target; `BENCH.md` holds the before/after numbers.

## Direction

Apple TV (tvOS) home screen, keeping the shelf the owner already likes.

* **Home = "C" from the board.** A frosted dock tray at the top with the favourite apps, then every installed app
  in a grid below it. Soft background wash (the focused app's colour or the theme wallpaper, dimmed). Clock at the
  top right. No status pills on screen: they move into the sidebar.
* **Apps per row: 6 by default**, changeable on the TV (4, 5 or 6). The dock has the same number of slots. Uses
  the existing `columns` key; add it to the on-TV settings screen.
* **Every installed app shows.** Apps the owner doesn't want on the grid go into folders or are hidden.

## Sidebar (overlay)

* Opens **on top of the grid without moving it** (the grid dims slightly on the left). Two ways in:
  Left on the first column, or a menu button at the top left (focusable, reached with Up from the dock).
* Items: Inputs, Wi-Fi, Running apps, Sleep timer (shows its state, e.g. "Off" / "45 min"), Screensaver (start
  now), Hidden apps, Settings. Launcher settings is reached from there or from hold-OK, as today.
* Back or Right closes it and puts focus back on the tile it came from. Home closes it too.

## Folders

* Hold OK on an app → "Move to folder" → pick a folder or "New folder". Hold OK in a folder → "Remove from folder".
* **Exactly 12 apps max, laid out 4 × 3, never scrolls.** A full folder is not offered as a target. Folder tile
  shows the first four icons in miniature plus the folder's name under it when focused.
* Opens as a glass panel over the grid; Back closes it. Rename and delete from the folder tile's hold-OK menu
  (deleting puts its apps back on the grid).
* Stored in the launcher's own preferences (no theme key needed); survives app updates.

## Hidden apps

* Hold OK → "Hide". The sidebar's Hidden apps opens a list where each can be shown again or launched.
* Builds on the existing `hidden_apps` key (theme-set hides stay; owner hides are stored in preferences).

## Running apps

* A full screen from the sidebar: total memory in use (existing `MemInfo`), then apps used recently with how long
  ago, newest first. A Close button per app.
* **Android 14 limit:** `killBackgroundProcesses()` only stops the caller's own processes since Android 14, so the
  launcher can't close other apps itself. Close opens that app's system App info page, where Force stop is one
  press away. Per-app memory may not be readable without extra grants; verify on the TV and drop the column if not.
* Needs **usage access** (`PACKAGE_USAGE_STATS`, granted once with `appops set com.gathin.tvhome GET_USAGE_STATS allow`).
  That is a manifest permission change: **needs the owner's written OK before it goes in.** No INTERNET.

## Sleep timer

* Sidebar item cycles Off → 15 → 30 → 45 → 60 → 90 min. When it runs out the TV goes to standby, whatever app is on
  screen. A small toast 1 minute before, with any key cancelling.
* Lives in `HomeKeyService` (already a long-running accessibility service, already does the `sleep` action via
  `GLOBAL_ACTION_LOCK_SCREEN`), so no new service or alarm. If the key helper is off, the item says so.

## Screensavers

Five styles, chosen in the launcher's settings screen with a live preview; used by the Home idle timer and by
Android's screensaver (`ClockDream`):

| Style | What moves |
|---|---|
| Drift clock + Aurora | clock drifts over slow colour clouds |
| Drift clock | clock drifts on black with a faint shifting glow (today's look) |
| Aurora | colour clouds only |
| Dunes | layered dusk ridges sliding, sun crossing slowly |
| Ripples | thin rings on black, fewest lit pixels |

Nothing stays in one place. All drawn in code (Canvas), no video or images, frame rate capped low (~30 fps or
less; Aurora and Dunes can run much slower). Any key exits. New config key `screensaver_style`, default
`drift_clock` so other setups don't change; this TV's theme sets the owner's choice.

## Kept

* All upstream extras stay (festivals, lunar calendar, sky, cards, languages): the owner chose not to delete them.
* Hold-OK tile menu (move, remove), Gear key → Settings, YouTube key → YouTube, Inputs, Wi-Fi, Settings always reachable.
* No recently-opened row (owner said no). No weather, no "now playing", no photo slideshow for now.

## Approach

Restyle plus restructure (options a + b in `prompts/ui-rewrite.md`), still plain Java and Views. New pieces go in
their own classes instead of growing `MainActivity`: `Sidebar`, `Folders` (store) and `FolderPanel`,
`HiddenApps`, `RunningAppsActivity`, `SleepTimer`, and one `Screensaver` view per style behind a small interface
shared by `ClockActivity` and `ClockDream`. No Compose: it would add MBs and startup time, and D-pad focus is
already solved in the View code.

## Build order

1. Baseline `tools/bench.py` → `BENCH.md`.
2. Screensaver styles + picker (self-contained, low risk to Home).
3. Grid at 6 across + C layout (dock tray, wash, clock).
4. Sidebar overlay.
5. Folders, then hidden apps.
6. Sleep timer.
7. Running apps (after the permission OK).
8. Bench again, screenshots before/after, D-pad from every edge, real remote for Gear/YouTube.

## Background and focus (owner, 2026-11-06)

* Keep today's dark look as the base, with a **light colour wash** across the main background taken from the
  focused app (today's glow, spread wider and softer, cross-fading as focus moves).
* **A glow around the focused tile** in that app's colour, on top of the white focus rim (a soft halo just outside
  the tile). Drawn with a prebuilt gradient, not live blur.

## Home press animates back to the top

Today, pressing Home while already on Home calls `grid.scrollToPosition(0)` in `MainActivity.onNewIntent`
(`MainActivity.java:1057`), so the grid jumps from wherever it is to the top. New behaviour:

* Home already in front and not at the top: **smooth-scroll to the top** (quick, ~300-400 ms, eased; long grids
  first snap to a few rows from the top, then glide, so it never takes long), then focus the first dock tile.
* Coming back from an app (Home not visible): keep the instant jump, since nothing is on screen to animate.
* A folder or the sidebar is open: Home closes it first, then scrolls.
* Respects the launcher's `animations` switch (off = instant, as today).

## Decisions

* Running apps: owner approved adding `PACKAGE_USAGE_STATS` (2026-11-06). Still no INTERNET.
