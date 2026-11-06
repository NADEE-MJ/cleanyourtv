#!/usr/bin/env bash
# Read-only health check of the TV against my-tv/debloat.txt. Changes nothing.
#   my-tv/scripts/tv-status.sh
# Run my-tv/scripts/connect-tv.sh first.
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
source "$HERE/env.sh"
a() { "$TV_ADB_BINARY" -s "$TV_HOST" "$@"; }

[ "$(a get-state 2>&1)" = device ] || { echo "TV not connected: run my-tv/scripts/connect-tv.sh"; exit 2; }

echo "== device"
a shell 'echo "$(getprop ro.product.manufacturer) $(getprop ro.product.model) / $(getprop ro.product.name) / Android $(getprop ro.build.version.release)"; getprop ro.build.display.id; uptime'

echo "== Home owner (want com.gathin.tvhome/.MainActivity)"
a shell 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1'

echo "== key helper (want com.gathin.tvhome/.HomeKeyService, 1)"
a shell 'settings get secure enabled_accessibility_services; settings get secure accessibility_enabled'

echo "== Wi-Fi networks owned by (want android.uid.system or tv.settings, NOT setupwraith)"
a shell dumpsys wifi 2>/dev/null | grep -o 'cname=[^ ]*' | sort | uniq -c

echo "== list drift: packages in my-tv/debloat.txt that are enabled again"
want=$(grep -vE '^\s*(#|$)' "$ROOT/my-tv/debloat.txt" | awk '{print $1}' | sort -u)
enabled=$(a shell 'pm list packages -e --user 0' | tr -d '\r' | sed 's/^package://' | sort -u)
drift=$(comm -12 <(printf '%s\n' "$want") <(printf '%s\n' "$enabled"))
if [ -z "$drift" ]; then echo "none: the TV matches the list"; else printf '%s\n' "$drift" | sed 's/^/  ENABLED AGAIN: /'; echo "fix: tools/venv/bin/python -I tools/check.py --fix my-tv/debloat.txt com.gathin.tvhome"; fi

echo "== stock launcher (want disabled)"
a shell 'pm list packages -d --user 0' | grep -E 'launcherx|setupwraith' || echo "stock launcher / setupwraith are ENABLED"
