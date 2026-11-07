#!/usr/bin/env bash
# Is it safe to make this public? Scans what a push would add on top of upstream (Gathin23) for private data:
# the lines the commits add, the files they add, and their messages. Exit 0 = clean, 1 = something private.
#
#   my-tv/scripts/check-public.sh [ref ...]      default: HEAD. The git pre-push hook runs it on every pushed ref.
#
# Two kinds of pattern:
#   * generic, below: LAN addresses (except the docs' 192.168.1.50), MAC addresses, ADB keys, tokens, android ids;
#   * this TV's own values (IP, Wi-Fi name, serial, device name...) from backup/private-patterns.txt, which is
#     git-ignored so the list itself is never published.
# Commit author names and emails are not checked: the owner chose to keep theirs.
set -uo pipefail
ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT" || exit 2
BASE=upstream/main
git rev-parse -q --verify "$BASE" >/dev/null || { echo "check-public: no $BASE (git fetch upstream)"; exit 2; }
refs=("$@"); [ ${#refs[@]} -eq 0 ] && refs=(HEAD)

GENERIC='(^|[^0-9])(10\.[0-9]+\.[0-9]+\.[0-9]+|192\.168\.([0-9]|[1-9][0-9]+)\.[0-9]+|172\.(1[6-9]|2[0-9]|3[01])\.[0-9]+\.[0-9]+)([^0-9]|$)|([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}|BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY|android_id[=: ]+[0-9a-f]{8,}|(api[_-]?key|token|password|secret)["'"'"' ]*[:=]["'"'"' ]*[A-Za-z0-9_\-]{16,}'
ALLOWED='192\.168\.1\.50|<tv ip>'
PRIVATE=$(grep -vE '^\s*(#|$)' backup/private-patterns.txt 2>/dev/null | paste -sd'|' -)
[ -z "$PRIVATE" ] && echo "check-public: warning, backup/private-patterns.txt is missing or empty (only generic checks run)"

bad=0
for ref in "${refs[@]}"; do
  range="$BASE..$ref"
  # Added lines only: what this push makes public that upstream does not already publish.
  added=$(git diff -U0 "$BASE...$ref" -- . ':!examples' ':!addons' ':!docs/press' | grep -E '^\+[^+]' )
  msgs=$(git log --format='%B' "$range")
  names=$(git diff --name-only --diff-filter=A "$BASE...$ref")
  # Pictures and dumps never belong in the tree (screenshots show Wi-Fi names and accounts).
  pics=$(printf '%s\n' "$names" | grep -iE '(^|/)(backup|shots|work)/|\.(png|jpe?g|webp|mp4)$|adbkey' | grep -vE '^themes/[^/]+/(screenshot|wallpaper)' )
  hits=$( { printf '%s\n' "$added" | grep -nE "$GENERIC" | grep -vE "$ALLOWED";
            [ -n "$PRIVATE" ] && printf '%s\n' "$added" "$msgs" | grep -niE "$PRIVATE"; } 2>/dev/null )
  if [ -n "$hits" ] || [ -n "$pics" ]; then
    bad=1
    echo "check-public: $ref is NOT safe to publish:"
    [ -n "$hits" ] && printf '%s\n' "$hits" | cut -c1-160 | sed 's/^/  line: /'
    [ -n "$pics" ] && printf '%s\n' "$pics" | sed 's/^/  file: /'
  else
    echo "check-public: $ref clean ($(git rev-list --count "$range") commits on top of upstream)"
  fi
done
exit $bad
