#!/usr/bin/env bash
# Starts, stops and inspects the per-user editor containers of the streaming setup (TD-037).
#
#   editor-session.sh start  <user-id>   start the user's editor container if none is running
#   editor-session.sh stop   <user-id>   stop and remove it (the user's volume, with the project, is kept)
#   editor-session.sh status <user-id>   print the state
#   editor-session.sh list               print "<user-id> <docker status>" for every running session
#
# <user-id> becomes part of container and volume names, so it must match ^[a-z0-9][a-z0-9-]{0,39}$ (for example a
# numeric account id or a lower-case UUID). Anything else is refused before docker is called.
#
# status and start print one line: "none", "stopped", "starting <ip>:6080" or "ready <ip>:6080".
# "ready" means the noVNC page is served; the editor window follows a few seconds later.
# Exit status: 0 ok, 2 bad usage or user id, 3 no free session slot (GDXUNITY_MAX_SESSIONS), 4 docker failed.
#
# Intended caller: the web application's backend, through a sudoers rule that allows exactly this script
# (docs/editor-streaming.md). The script publishes no ports: sessions are reached over an internal network.
#
# Settings (environment, all optional): GDXUNITY_EDITOR_IMAGE (gdxunity-editor-stream), GDXUNITY_EDITOR_NETWORK
# (gdxunity-editors), GDXUNITY_EDITOR_BRIDGE (gdxeditors0), GDXUNITY_MAX_SESSIONS (4), GDXUNITY_SESSION_MEMORY (3g), GDXUNITY_SESSION_CPUS (2),
# GDXUNITY_IDLE_SECONDS (1800), GDXUNITY_SCREEN_SIZE (1600x900x24), DOCKER (docker).
set -euo pipefail

DOCKER="${DOCKER:-docker}"
IMAGE="${GDXUNITY_EDITOR_IMAGE:-gdxunity-editor-stream}"
NETWORK="${GDXUNITY_EDITOR_NETWORK:-gdxunity-editors}"
BRIDGE="${GDXUNITY_EDITOR_BRIDGE:-gdxeditors0}"
MAX_SESSIONS="${GDXUNITY_MAX_SESSIONS:-4}"
MEMORY="${GDXUNITY_SESSION_MEMORY:-3g}"
CPUS="${GDXUNITY_SESSION_CPUS:-2}"
IDLE_SECONDS="${GDXUNITY_IDLE_SECONDS:-1800}"
SCREEN_SIZE="${GDXUNITY_SCREEN_SIZE:-1600x900x24}"
LABEL="gdxunity.editor"
USER_LABEL="gdxunity.editor.user"
PORT=6080

die() {
    local code="$1"
    shift
    echo "editor-session: $*" >&2
    exit "$code"
}

require_user_id() {
    [[ "${1:-}" =~ ^[a-z0-9][a-z0-9-]{0,39}$ ]] || die 2 "invalid user id '${1:-}' (use lower-case letters, digits and '-', at most 40 characters)"
}

# Prints "none", "stopped", "starting <ip>:6080" or "ready <ip>:6080"
session_status() {
    local name="gdxunity-editor-$1" info state health ip
    if ! info=$("$DOCKER" inspect --format "{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}|{{with index .NetworkSettings.Networks \"$NETWORK\"}}{{.IPAddress}}{{end}}" "$name" 2> /dev/null); then
        echo none
        return
    fi
    IFS='|' read -r state health ip <<< "$info"
    case "$state" in
        running | restarting)
            if [ "$health" = healthy ] && [ -n "$ip" ]; then
                echo "ready $ip:$PORT"
            elif [ -n "$ip" ]; then
                echo "starting $ip:$PORT"
            else
                echo starting
            fi
            ;;
        *) echo stopped ;;
    esac
}

start_session() {
    local id="$1" name="gdxunity-editor-$1" current running
    current=$(session_status "$id")
    case "$current" in
        ready* | starting*)
            echo "$current"
            return
            ;;
        stopped) "$DOCKER" rm -f "$name" > /dev/null || die 4 "could not remove the stopped session $name" ;;
    esac

    running=$("$DOCKER" ps --quiet --filter "label=$LABEL" | wc -l)
    if [ "$running" -ge "$MAX_SESSIONS" ]; then
        die 3 "capacity: $running of $MAX_SESSIONS editor sessions are running"
    fi

    # --internal: containers on this network have no route to the internet, so scripts cannot call out.
    # enable_icc=false: sessions cannot connect to each other (an editor runs its user's code).
    # A fixed bridge name lets the host firewall refuse connections from sessions to the VPS itself
    # (docs/editor-streaming.md, "Stop sessions from reaching the VPS").
    "$DOCKER" network inspect "$NETWORK" > /dev/null 2>&1 \
        || "$DOCKER" network create --internal \
            --opt com.docker.network.bridge.enable_icc=false \
            --opt "com.docker.network.bridge.name=$BRIDGE" "$NETWORK" > /dev/null \
        || die 4 "could not create the network $NETWORK"
    "$DOCKER" volume create "gdxunity-editor-data-$id" > /dev/null || die 4 "could not create the volume for $id"

    # No published ports (-p): the web application reaches the session over the internal network only.
    # /tmp needs exec: libGDX and LWJGL extract their native libraries there and load them.
    if ! "$DOCKER" run --detach --name "$name" \
        --label "$LABEL=1" --label "$USER_LABEL=$id" \
        --network "$NETWORK" \
        --memory "$MEMORY" --memory-swap "$MEMORY" --cpus "$CPUS" --pids-limit 512 \
        --cap-drop ALL --security-opt no-new-privileges \
        --read-only --tmpfs /tmp:rw,exec,nosuid,size=1g \
        --volume "gdxunity-editor-data-$id:/data" \
        --env "SCREEN_SIZE=$SCREEN_SIZE" --env "IDLE_TIMEOUT_SECONDS=$IDLE_SECONDS" \
        "$IMAGE" session > /dev/null; then
        # A second request for the same user can win the race for the container name
        current=$(session_status "$id")
        case "$current" in
            ready* | starting*)
                echo "$current"
                return
                ;;
        esac
        die 4 "could not start the editor container for $id"
    fi
    session_status "$id"
}

stop_session() {
    local name="gdxunity-editor-$1"
    "$DOCKER" stop --time 20 "$name" > /dev/null 2>&1 || true
    "$DOCKER" rm --force "$name" > /dev/null 2>&1 || true
    echo none
}

command="${1:-}"
case "$command" in
    start | stop | status)
        require_user_id "${2:-}"
        [ "$#" -eq 2 ] || die 2 "usage: $0 $command <user-id>"
        case "$command" in
            start) start_session "$2" ;;
            stop) stop_session "$2" ;;
            status) session_status "$2" ;;
        esac
        ;;
    list)
        [ "$#" -eq 1 ] || die 2 "usage: $0 list"
        "$DOCKER" ps --filter "label=$LABEL" --format "{{.Label \"$USER_LABEL\"}} {{.Status}}"
        ;;
    *) die 2 "usage: $0 start|stop|status <user-id> | list" ;;
esac
