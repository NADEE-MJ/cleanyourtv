Claude Code reads this file. The guide for every coding agent in this repo is AGENTS.md, imported below.

@AGENTS.md

## This fork: my TV

This fork is set up for one specific TV (TCL QM6K Pro). Before touching the TV, the launcher, or any tool that talks to it, read `my-tv/README.md` and `my-tv/RUNBOOK.md`, and use the `my-tv` skill (`.claude/skills/my-tv/SKILL.md`).
Private TV data stays in the git-ignored `backup/` and `shots/` folders; this repo is public.

`AGENTS.md` above is upstream's guide. Its **safety rules for operating a TV** (back up first, disable never uninstall,
protected packages, Home guard, small batches, close ADB afterwards) apply here in full. Its **"Hard rules" and "Where a
change goes" sections are rules for pull requests to upstream** (no new permissions or dependencies, APK size gate, minSdk,
"heavy restyles go in your own repo"). This fork IS that own repo, so they are not binding here unless the owner says so;
when a change would break one (e.g. adding a dependency, the INTERNET permission, raising the APK size), tell the owner
and get an explicit yes first.

## Public repo: nothing private gets pushed

This fork (github.com/NADEE-MJ/cleanyourtv) is **public**. Anything committed and pushed can be read, cached and
forked by anyone, and deleting it later does not take it back. Before every commit and every push:

1. **Never commit** the TV's IP address, the Wi-Fi network name, the TV's serial or device ids (android_id, MAC
   addresses, the mDNS name `adb-...`), pairing codes, the device name ("<name>'s TV"), account emails, ADB keys,
   tokens or passwords. In docs write `<tv ip>` or `192.168.1.50`. Quote only the model, Android version and
   firmware build (the upstream rule in `AGENTS.md`).
2. **Never commit** anything from `backup/`, `shots/`, `work/` or `tools/adbkey/`, and no screenshots or photos at all
   outside `themes/<name>/` (TV screenshots show the Wi-Fi name, the clock and accounts). Show screenshots to the
   owner in chat; they stay in `shots/`.
3. **Run `my-tv/scripts/check-public.sh` before pushing** and read its output. It scans what the push adds on top of
   upstream (added lines, new files, commit messages) for LAN addresses, MAC addresses, keys and tokens, and for this
   TV's own values listed in the git-ignored `backup/private-patterns.txt`. Add any new private value (a new IP after
   a router change, a new network name) to that file, never to a tracked one.
4. The **`pre-push` hook** in `.git/hooks/` runs the same check and blocks a push that fails it. Hooks are not
   cloned: on a fresh clone, reinstall it (the hook is a few lines; see the header of `check-public.sh`). **Never
   bypass it with `--no-verify`**, never edit a pattern out to make a push pass, and never force-push to "undo" a
   leak without telling the owner: a leaked value must be treated as published, and the owner decides what to rotate.
5. Commit and push only to the owner's fork (`origin`), never to `upstream` (Gathin23), and open no pull request or
   issue upstream unless the owner asks.
6. Commit author: the owner chose to keep their own name and email on commits (2026-11-06); do not rewrite history
   to change it unless they ask.

### Branches

* `main` is an untouched copy of upstream (`Gathin23/cleanyourtv`). Never commit to it. To take upstream's updates:
  `git fetch upstream && git checkout main && git merge --ff-only upstream/main && git push origin main`.
* `my-tv` (the fork's default branch) holds everything of the owner's: `my-tv/`, this file, the skill, the launcher
  UI rewrite. Work there or on a short branch off it. Bring upstream in with `git checkout my-tv && git merge main`,
  then rebuild and check the launcher on the TV before pushing.
