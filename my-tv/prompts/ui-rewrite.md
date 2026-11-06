# Prompt: launcher UI rewrite (new session)

How to use: open a **new** Claude Code session with the working directory set to this repo
(`~/code/random/cleanyourtv`, remote `NADEE-MJ/cleanyourtv`), and paste everything inside the fence below.
Have the TV on with Wireless debugging ON when the assistant first needs it (it will ask).

```text
I want to redesign the user interface of my TV launcher. This repo is my fork of Gathin23/cleanyourtv, set up
for one TV; the launcher is in launcher/ (package com.gathin.tvhome) and is already installed and working
as Home on my TV. Work on a branch named ui-rewrite. Do not touch main until I say so.

START by reading, in this order: my-tv/README.md, my-tv/RUNBOOK.md, my-tv/LOG.md,
.claude/skills/my-tv/SKILL.md, docs/launcher.md, themes/README.md. They describe the TV, how to connect to
it from this Mac (macOS Local Network privacy blocks adb/python in your sandbox; my-tv/scripts/connect-tv.sh
works around it), the rules for operating it safely, and what is already done. Follow them.

## Step 0: do not write code yet. Interview me, then show me designs.
I have not decided what I want. Ask me (a few focused questions at a time, with options, not an essay):
  * what I dislike about the current home screen (look at shots/2026-11-06/after-launcher.png and a fresh screenshot);
  * references I like (Apple TV, Google TV, Plex, Kodi, console dashboards, ...), mood, colours, density;
  * what must be on screen: clock/date, weather (the launcher has no network permission; ask before changing that),
    Inputs, Wi-Fi, Settings, app grid vs rows, dock, recent/last-opened, now playing, wallpaper/photo, screensaver;
  * what I never use and can be deleted (the upstream launcher carries a lot: festivals, lunar calendar, sky,
    cards/modules, 31 languages, gamepad passthrough, many switches).
Then produce 2-3 visual directions as HTML mockups (render them so I can look at them, at 1920x1080, with a visible
focus state) and let me pick. Only after I approve a direction: write a short design doc in my-tv/ui/DESIGN.md
and start building.

## What you are changing (facts, verified 2026-11-06; re-verify before relying on them)
  * Native Android, plain Java, View-based (not Compose). ~15,000 lines in launcher/app/src/main/java/com/gathin/tvhome/.
    MainActivity.java is ~4,100 lines and owns most of the UI; AppAdapter (grid/dock, RecyclerView), Cards, Config
    (~930 lines, reads res/values/config.xml with ~250 keys and overlays a config.json pushed by tools/theme.sh),
    HomeKeyService + RemoteKeys (accessibility key filter), ClockScene/ClockDream (screensaver), SkyView, many drawables.
  * Layouts: res/layout/ activity_main.xml (FrameLayout, SkyView + wallpaper + clock row + button row + app area), item_app,
    popup_inputs, popup_cards, activity_launcher_settings, activity_quick_settings, dream_clock, ...
  * Dependencies: only androidx.recyclerview and androidx.dynamicanimation. minSdk 26, targetSdk 34, release build
    uses R8 and is signed with ~/.android/debug.keystore (keep that file or the APK will not install over the old one).
  * Upstream's design goals: ~572 KB APK, 1 GB RAM TVs, no network, no services, no blur/video. MY TV is a 2025
    TCL QM6K Pro (Android 14, ~2.4 GB RAM) so I am willing to trade some of that for looks. Measure before deciding;
    tools/bench.py reports key latency, janky frames, cold start and memory. Take a baseline FIRST and keep the numbers
    in my-tv/ui/BENCH.md. Anything that makes it feel slow on the remote is a regression.
  * Upstream CI caps the APK at 576 KB. That is their rule, not mine: ignore it in this fork, but tell me what the size becomes.
  * Approaches, from least to most risk: (a) restyle through layouts, styles, drawables and config/theme keys;
    (b) restructure MainActivity into smaller pieces while changing the UI; (c) rewrite the UI in Jetpack Compose for TV
    (tv-material). Recommend one with reasons after the interview. Don't choose (c) just because it is fashionable;
    it adds MBs and startup cost, and D-pad focus handling is the hard part of any TV UI.

## Hard requirements (do not regress these)
  * Home must always work. This launcher IS the TV's Home; a crash loop or blank screen is bad and I may be on the
    sofa with only a remote. Pressing Home must return to it, Back must behave sanely, and a user must always be able to reach
    Settings, Inputs (HDMI sources) and Wi-Fi, and launch every installed app.
  * Remote only: everything reachable and visibly focused with D-pad, OK, Back, Home. No touch assumptions.
    Hold-OK tile menu (move/remove) behaviour should survive unless we agree to drop it.
  * Gear key (83) -> Settings, YouTube key (190) -> YouTube must keep working (HomeKeyService, RemoteKeys,
    my-tv/theme/config.json). If config keys change, update my-tv/theme/config.json and keep old keys working or migrate them.
  * Keep the manifest's permissions as they are unless I approve a change in writing (no INTERNET now).
  * New user-visible strings: add them to res/values AND the other values-* folders (or make them config-only),
    and keep the layout working right-to-left. If I tell you only English matters, you may delete locales; ask first.
  * Do not run debloat/disable scripts, do not change which packages are enabled, do not pull from upstream. The TV's
    system state is out of scope for this session.

## How to work
  * Branch ui-rewrite. Small commits. Never commit the TV's IP, serial, ids or screenshots of my Wi-Fi/account
    (backup/, shots/ are git-ignored; keep it that way). The fork is public.
  * Build + install: source my-tv/scripts/env.sh; my-tv/scripts/connect-tv.sh; then
    HOME_KEY_SERVICE=1 launcher/build-and-install.sh for a full build (the flag keeps the Gear/YouTube remote buttons
    working; push my-tv/theme again with tools/theme.sh if they die). The script verifies Home resolved to the new build and rolls back to the stock launcher if not. Note that
    reinstalling restarts Home and may drop the key helper binding; re-check with my-tv/scripts/tv-status.sh.
  * Safety net before the first install of risky UI work: confirm you know the way back
    (`tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'` then set it as Home, the Projectivy launcher
    com.spocky.projengmenu is also installed) and tell me what to press on the remote if the screen goes black.
  * Verify visually, not by assumption: take a screenshot after each meaningful change
    (tools/venv/bin/python -I tools/tv.py --screenshot shots/ui-rewrite/<name>.png) and LOOK at it. Drive the UI with
    `adb -s 127.0.0.1:25560 shell input keyevent KEYCODE_DPAD_RIGHT` etc. and check focus order, edge wrapping and
    the focus state of every element, including the Inputs popup and settings panel.
  * Run the unit tests and lint: cd launcher && ./gradlew testReleaseUnitTest lintRelease (JDK 17 via my-tv/scripts/env.sh);
    update or delete tests for code you remove. Run tools/bench.py before/after and report the numbers.
  * Keep my-tv/LOG.md updated and put the design doc, before/after screenshots and bench numbers under my-tv/ui/.

## Finish
  * Leave the TV on a working launcher. Show me before/after screenshots and the numbers.
  * Close ADB when done: tell me to turn Wireless debugging and USB debugging OFF, run my-tv/scripts/connect-tv.sh stop,
    and verify the ports are closed (RUNBOOK section 5).
  * Push the branch to my fork (origin) and give me the link. Do not merge to main or open a PR against upstream.
```

## Notes for later (not part of the prompt)

* The rewrite session reuses everything in `my-tv/scripts/`; nothing about the TV's debloat state changes.
* If the redesign should become a separate project, copy `launcher/` out and keep the `launcher/build-and-install.sh`
  contract (it asks the TV for Home, installs, verifies, rolls back).
* Upstream accepts only small launcher changes (their `.github/maintainer/MAINTAINER.md`); a rewrite belongs in the fork.
