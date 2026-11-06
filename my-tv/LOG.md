# Log

Newest first. Add an entry whenever the TV or this setup changes (what, why, how verified). No secrets here:
this repo is public. IPs, serials and pairing codes belong in `backup/` (git-ignored).

## 2026-11-06: first clean-up and launcher install

**Why:** wanted a TV without TCL tracking/ads and an Apple-TV-style home screen. Chose `Gathin23/cleanyourtv`.
(A look-alike desktop app, `wingedpanther/cleanmytv`, was audited first by mistake; it is not used. This repo
was security-reviewed: launcher has no INTERNET permission, tools make no unexpected network calls, safety
guards in `disable.sh`; main risks are third-party add-ons and the AI-driven install path, neither used.)

**Done**
1. Forked to `NADEE-MJ/cleanyourtv`; upstream snapshot `3c122bc0`.
2. Found the TV (mDNS), discovered it only offers Wireless debugging (TLS). Installed Google platform-tools.
3. macOS Local Network privacy blocked everything but Apple's `/usr/bin/nc`; built `my-tv/scripts/ncrelay.sh`.
   Paired, connected (see RUNBOOK for the rules learned).
4. Identified the TV as G08 / `G08_4K_US` / Android 14 / AU14. Chose `examples/tcl-qm6k-google-tv` and read it plus `disable.sh`.
5. Backup to `backup/2026-11-06/` (+ before screenshot).
6. All four saved Wi-Fi networks were owned by `setupwraith`: forgot and rejoined Wi-Fi on the TV -> owned by system.
7. Disabled 26 packages in batches A, B, C, D with a health check after each (Home, internet, Wi-Fi owners). Choices:
   removed TalkBack and the TCL soundbar updater; kept Netflix, Chromecast, AirPlay, Assistant, the phone remote
   service (iPhone Google TV app), game bar.
8. Installed JDK 17 + Android SDK 34 via Homebrew, built and installed the launcher (`com.gathin.tvhome` 3.4).
   Install script verified Home moved to it and disabled `launcherx`.
9. Re-ran install with `HOME_KEY_SERVICE=1` and pushed `my-tv/theme` so Gear and YouTube buttons work (confirmed by the owner).
10. Owner rebooted the TV: everything survived (27 disabled, Home = new launcher, key helper on). `tools/check.py`: nothing drifted.
11. Wrote `my-tv/` (this folder), the skill, and the UI-rewrite prompt. Closed ADB.

**Known leftovers / ideas**
* Projectivy launcher is still installed (unused). Remove it, or keep as a fallback.
* Google Assistant (`katniss`) kept; revisit if the TV feels slow.
* Next: launcher UI rewrite, in its own session: see `prompts/ui-rewrite.md`.
