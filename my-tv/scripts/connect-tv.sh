#!/usr/bin/env bash
# Connect this Mac to the TV over encrypted Wireless debugging, from inside an assistant's sandbox.
#
#   my-tv/scripts/connect-tv.sh              find the TV's current connect port, relay it, adb connect
#   my-tv/scripts/connect-tv.sh pair <port> <code>   one-time (or after Wi-Fi changes) pairing
#   my-tv/scripts/connect-tv.sh stop         kill the relays and the adb server
#
# Needs backup/tv.env (git-ignored) with:  TV_IP=<the TV's LAN address>   and optionally
#                                          TV_MDNS_NAME=<adb-XXXX-YYYY, the service name, if other phones/TVs
#                                          on the network also have Wireless debugging on>
# On the TV: Settings > System > Developer options > Wireless debugging ON (keep that screen's "IP & port").
# For `pair`: open "Pair device with pairing code" and LEAVE IT OPEN. Its port + 6-digit code are what you pass.
#
# Hard-won rules (see my-tv/RUNBOOK.md):
#   * never nc/telnet-probe the pairing port: that spends the dialog's attempt. Start the relay, pair at once.
#   * the connect port changes at every reboot and every time Wireless debugging is toggled.
#   * pairing survives a reboot; changing Wi-Fi network (forget + rejoin) needs a new pairing.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
# shellcheck source=env.sh
source "$HERE/env.sh"
[ -f "$ROOT/backup/tv.env" ] && source "$ROOT/backup/tv.env"
: "${TV_IP:?create $ROOT/backup/tv.env containing TV_IP=<tv ip> (TV: Settings > Network shows it)}"
ADB=$TV_ADB_BINARY
PAIR_LOCAL_PORT=25559
SVC=_adb-tls-connect._tcp

kill_relay() {  # $1 = local port
  pkill -f "ncrelay.sh $1 " 2>/dev/null || true
  pkill -f "/usr/bin/nc -l 127.0.0.1 $1" 2>/dev/null || true
}

# Run a command for N seconds, then stop it (dns-sd browses forever).
run_for() { local secs=$1; shift; "$@" & local pid=$!; sleep "$secs"; kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; }

case "${1:-connect}" in
  stop)
    kill_relay "$TV_LOCAL_PORT"; kill_relay "$PAIR_LOCAL_PORT"
    "$ADB" disconnect >/dev/null 2>&1 || true
    "$ADB" kill-server 2>/dev/null || true
    echo "relays and adb server stopped"
    ;;

  pair)
    PORT=${2:?pairing port from the TV dialog}; CODE=${3:?6-digit code from the TV dialog}
    kill_relay "$PAIR_LOCAL_PORT"
    nohup "$HERE/ncrelay.sh" "$PAIR_LOCAL_PORT" "$TV_IP" "$PORT" >/dev/null 2>&1 &
    sleep 1   # no probing of the port: pair immediately
    "$ADB" pair "127.0.0.1:$PAIR_LOCAL_PORT" "$CODE"
    kill_relay "$PAIR_LOCAL_PORT"
    echo "paired. Now run: $0   (connect)"
    ;;

  connect)
    OUT=$(mktemp); trap 'rm -f "$OUT" "$OUT.l"' EXIT
    run_for 4 dns-sd -B "$SVC" local. >"$OUT" 2>&1
    NAME=${TV_MDNS_NAME:-$(awk '/ Add / && /'"$SVC"'/ {print $NF}' "$OUT" | head -1)}
    [ -n "$NAME" ] || { echo "no Wireless debugging service found by mDNS. Is it ON on the TV (and the TV awake)? $(cat "$OUT" | tail -3)"; exit 1; }
    run_for 3 dns-sd -L "$NAME" "$SVC" local. >"$OUT.l" 2>&1
    PORT=$(sed -nE 's/.*can be reached at [^:]+:([0-9]+).*/\1/p' "$OUT.l" | head -1)
    [ -n "$PORT" ] || { echo "found service '$NAME' but could not resolve its port:"; cat "$OUT.l"; exit 1; }
    echo "TV connect port: $PORT (service $NAME)"
    nc -z -G 3 "$TV_IP" "$PORT" >/dev/null 2>&1 || { echo "port $PORT is not answering at $TV_IP; wrong TV_IP, or the TV is asleep"; exit 1; }
    kill_relay "$TV_LOCAL_PORT"
    "$ADB" disconnect "127.0.0.1:$TV_LOCAL_PORT" >/dev/null 2>&1 || true
    nohup "$HERE/ncrelay.sh" "$TV_LOCAL_PORT" "$TV_IP" "$PORT" >/dev/null 2>&1 &
    sleep 1
    "$ADB" connect "127.0.0.1:$TV_LOCAL_PORT"
    sleep 1
    STATE=$("$ADB" -s "127.0.0.1:$TV_LOCAL_PORT" get-state 2>&1 || true)
    echo "adb state: $STATE"
    [ "$STATE" = device ] || { echo "not connected. If it says unauthorized/offline, pair again: $0 pair <port> <code>"; exit 1; }
    echo "ready.  source my-tv/scripts/env.sh   (sets TV_ADB_BINARY and TV_HOST=$TV_HOST for tools/*)"
    ;;

  *) sed -n '2,20p' "$0"; exit 2 ;;
esac
