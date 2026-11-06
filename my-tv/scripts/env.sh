#!/usr/bin/env bash
# Source this before running anything in tools/ or launcher/ against the TV:
#     source my-tv/scripts/env.sh
#
# Toolchain installed with Homebrew on the Mac mini (see my-tv/RUNBOOK.md, "Toolchain"):
#   brew install --cask android-platform-tools      -> /opt/homebrew/bin/adb
#   brew install openjdk@17 android-commandlinetools
#   sdkmanager "platforms;android-34" "build-tools;34.0.0"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"

# The TV only offers encrypted Wireless debugging (no plain port 5555), so tools/tv.py goes through Google's adb.
export TV_ADB_BINARY=/opt/homebrew/bin/adb

# ALWAYS the same local port. Undo records in ~/.cleanyourtv are filed under this host name
# ("127.0.0.1:25560"), so a different port here means tools/restore.sh cannot find them.
export TV_LOCAL_PORT=25560
export TV_HOST="127.0.0.1:${TV_LOCAL_PORT}"
