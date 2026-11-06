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
