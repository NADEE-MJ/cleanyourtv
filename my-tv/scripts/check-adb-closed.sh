#!/usr/bin/env bash
# Is ADB really closed on the TV? Checks from the network side, with Apple's /usr/bin/nc and dns-sd, because
# tools/adb-closed.py is Python and is blocked by macOS Local Network privacy inside an assistant sandbox
# (it then says "No route to host", which proves nothing).
#
#   my-tv/scripts/check-adb-closed.sh          exit 0 = nothing answers, 1 = something is still open
#
# Checks: (1) mDNS: is Wireless debugging still advertised?  (2) plain adbd on 5555: a TCP connect, and if it
# connects, a real ADB CNXN handshake. Replying AUTH means plaintext adbd is alive (it asks for the RSA key, i.e. the
# on-screen "Allow USB debugging?" prompt), which is still exposure. Nothing here logs in or changes anything.
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$HERE/../.." && pwd)
[ -f "$ROOT/backup/tv.env" ] && source "$ROOT/backup/tv.env"
: "${TV_IP:?set TV_IP in backup/tv.env}"
open=0

echo "== Wireless debugging (mDNS, 4 s)"
OUT=$(mktemp); trap 'rm -f "$OUT" "$OUT.b"' EXIT
dns-sd -B _adb-tls-connect._tcp local. >"$OUT" 2>&1 & p=$!; sleep 4; kill $p 2>/dev/null; wait $p 2>/dev/null
if grep -q ' Add ' "$OUT"; then echo "STILL ADVERTISED:"; grep ' Add ' "$OUT"; open=1; else echo "closed: no _adb-tls-connect service"; fi

echo "== plain adbd on $TV_IP:5555"
if nc -z -G 3 "$TV_IP" 5555 >/dev/null 2>&1; then
  printf 'CNXN\x01\x00\x00\x01\x00\x10\x00\x00\x07\x00\x00\x00\x32\x02\x00\x00\xbc\xb1\xa7\xb1host::\x00' > "$OUT.b"
  magic=$(nc -w 4 "$TV_IP" 5555 < "$OUT.b" 2>/dev/null | head -c 4)
  case "$magic" in
    AUTH) echo "OPEN: plaintext adbd answers (asks for a key). Turn USB debugging OFF in Developer options and reboot the TV." ;;
    CNXN) echo "OPEN AND UNAUTHENTICATED (ro.adb.secure=0?). Fix immediately." ;;
    *)    echo "port is open but did not speak ADB (got '${magic}')" ;;
  esac
  open=1
else
  echo "closed: 5555 refuses / does not answer"
fi

[ "$open" -eq 0 ] && echo "ALL CLOSED" || echo "NOT FULLY CLOSED"
exit "$open"
