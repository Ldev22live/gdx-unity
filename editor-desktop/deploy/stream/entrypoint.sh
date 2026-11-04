#!/bin/bash
# Entry point of the editor streaming image (TD-037): a virtual display, a window manager, a VNC server, a noVNC
# web page and the Forgeroom editor, all in one container.
#
#   entrypoint session   (default) run a session. The container exits when the editor closes, when any of the
#                        helper processes dies, or when no browser has been connected for IDLE_TIMEOUT_SECONDS.
#   entrypoint smoke     start the display and run the editor's own --smoke-test; the exit status is the result.
#
# Settings (environment): SCREEN_SIZE (default 1600x900x24), IDLE_TIMEOUT_SECONDS (default 1800),
# PROJECT_DIR (default /data/project).
set -euo pipefail

MODE="${1:-session}"
SCREEN_SIZE="${SCREEN_SIZE:-1600x900x24}"
IDLE_TIMEOUT_SECONDS="${IDLE_TIMEOUT_SECONDS:-1800}"
PROJECT_DIR="${PROJECT_DIR:-/data/project}"
APP_DIR=/opt/gdxunity/app
export DISPLAY=:0

case "$MODE" in
    session | smoke) ;;
    *) echo "Unknown mode '$MODE'; use 'session' or 'smoke'" >&2; exit 2 ;;
esac
if [[ ! "$SCREEN_SIZE" =~ ^[0-9]{3,4}x[0-9]{3,4}x(16|24|32)$ ]]; then
    echo "SCREEN_SIZE must look like 1600x900x24, got '$SCREEN_SIZE'" >&2
    exit 2
fi
if [[ ! "$IDLE_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]]; then
    echo "IDLE_TIMEOUT_SECONDS must be a whole number of seconds, got '$IDLE_TIMEOUT_SECONDS'" >&2
    exit 2
fi

# Writable state lives in the /data volume: the root file system is read-only
export HOME=/data/home
export XDG_CONFIG_HOME=/data/config
export XDG_STATE_HOME=/data/state
mkdir -p "$HOME" "$XDG_CONFIG_HOME" "$XDG_STATE_HOME"

# No GPU in the container: Mesa's llvmpipe draws on the CPU
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
# zenity looks for a desktop accessibility bus that does not exist here
export NO_AT_BRIDGE=1

pids=()
start() {
    "$@" &
    pids+=("$!")
}
cleanup() {
    if [ "${#pids[@]}" -gt 0 ]; then
        kill "${pids[@]}" 2> /dev/null || true
    fi
}
trap cleanup EXIT
trap 'exit 143' TERM INT

start Xvfb "$DISPLAY" -screen 0 "$SCREEN_SIZE" -nolisten tcp
for _ in $(seq 1 50); do
    [ -S /tmp/.X11-unix/X0 ] && break
    sleep 0.1
done
if [ ! -S /tmp/.X11-unix/X0 ]; then
    echo "The virtual display (Xvfb) did not start" >&2
    exit 1
fi
start openbox

# First start of a user's volume: install the sample project. The editor never guesses a project folder
# (TD-033), so a missing marker would be an error.
if [ ! -f "$PROJECT_DIR/gdxunity.project" ]; then
    echo "First start: installing the sample project into $PROJECT_DIR"
    mkdir -p "$PROJECT_DIR"
    cp -a "$APP_DIR/sample-project/." "$PROJECT_DIR/"
fi

editor=(java -XX:MaxRAMPercentage=60 --enable-native-access=ALL-UNNAMED
    "-Dgdxunity.sampleProject=$APP_DIR/sample-project"
    -cp "$APP_DIR/*" io.github.ldev22live.gdxunity.editor.desktop.EditorLauncher
    --project "$PROJECT_DIR")

if [ "$MODE" = smoke ]; then
    status=0
    "${editor[@]}" --smoke-test || status=$?
    echo "Editor smoke test finished with status $status"
    exit "$status"
fi

# The VNC server only accepts connections from this container; websockify is the only way in
start x11vnc -display "$DISPLAY" -forever -shared -nopw -localhost -rfbport 5900 -quiet
start websockify --web=/usr/share/novnc --idle-timeout="$IDLE_TIMEOUT_SECONDS" 6080 127.0.0.1:5900
"${editor[@]}" &
pids+=("$!")

# The first process to exit ends the session; its status becomes the container's status
status=0
wait -n || status=$?
echo "A process of the editor session exited (status $status); stopping the container" >&2
exit "$status"
