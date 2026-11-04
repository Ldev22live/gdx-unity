#!/usr/bin/env bash
# Tests editor-session.sh with a fake "docker" that records its calls (no Docker needed).
#   bash editor-desktop/deploy/stream/test-editor-session.sh
# Exit status 0 when every check passes.
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$HERE/editor-session.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# The fake keeps its state in files: "inspect" is what `docker inspect` prints (absent = no such container),
# "network" exists once the network was created, "calls" has one line per docker call, and FAKE_RUNNING is the
# number of running editor containers `docker ps --quiet` reports.
cat > "$WORK/docker" << 'FAKE'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_DIR/calls"
case "$1" in
    inspect) [ -f "$FAKE_DIR/inspect" ] && cat "$FAKE_DIR/inspect" || exit 1 ;;
    ps)
        if [[ " $* " == *" --quiet "* ]]; then
            for ((i = 0; i < ${FAKE_RUNNING:-0}; i++)); do echo "container$i"; done
        else
            echo "u7 Up 2 minutes (healthy)"
        fi
        ;;
    network)
        case "$2" in
            inspect) [ -f "$FAKE_DIR/network" ] || exit 1 ;;
            create) touch "$FAKE_DIR/network" ;;
        esac
        ;;
    run)
        [ -z "${FAKE_RUN_FAILS:-}" ] || exit 125
        echo "running|starting|172.30.0.9" > "$FAKE_DIR/inspect"
        ;;
    rm) rm -f "$FAKE_DIR/inspect" ;;
esac
exit 0
FAKE
chmod +x "$WORK/docker"

failures=0
checks=0
fail() {
    failures=$((failures + 1))
    echo "  FAIL: $*"
}
assert_eq() { # name expected actual
    checks=$((checks + 1))
    [ "$2" = "$3" ] || fail "$1: expected '$2', got '$3'"
}
assert_calls_contain() { # name text
    checks=$((checks + 1))
    grep -qF -- "$2" "$FAKE_DIR/calls" || fail "$1: docker was not called with '$2'"
}
assert_calls_lack() { # name text
    checks=$((checks + 1))
    ! grep -qF -- "$2" "$FAKE_DIR/calls" || fail "$1: docker was called with '$2'"
}

# Runs the script against a fresh fake; sets $out, $err and $rc
run() {
    FAKE_DIR="$WORK/state"
    export FAKE_DIR
    DOCKER="$WORK/docker" bash "$SCRIPT" "$@" > "$WORK/out" 2> "$WORK/err"
    rc=$?
    out=$(cat "$WORK/out")
    err=$(cat "$WORK/err")
}
fresh() {
    rm -rf "$WORK/state"
    mkdir -p "$WORK/state"
    : > "$WORK/state/calls"
    unset FAKE_RUNNING FAKE_RUN_FAILS
}

echo "ids that could escape the container name are refused before docker is called"
for bad in "" "A" "../etc" "a b" "a;b" '$(id)' "-rf" "$(printf 'a%.0s' {1..41})"; do
    fresh
    run start "$bad"
    assert_eq "start '$bad' exit status" 2 "$rc"
    assert_eq "start '$bad' docker calls" "" "$(cat "$FAKE_DIR/calls")"
done
fresh
run status "Bad_Id"
assert_eq "status of a bad id" 2 "$rc"
run stop "../x"
assert_eq "stop of a bad id" 2 "$rc"
run frobnicate u7
assert_eq "unknown command" 2 "$rc"
run start u7 extra
assert_eq "extra argument" 2 "$rc"
assert_eq "no docker call for bad usage" "" "$(cat "$FAKE_DIR/calls")"

echo "status"
fresh
run status u7
assert_eq "no container" "none" "$out"
echo "running|healthy|172.30.0.5" > "$FAKE_DIR/inspect"
run status u7
assert_eq "healthy container" "ready 172.30.0.5:6080" "$out"
echo "running|starting|172.30.0.5" > "$FAKE_DIR/inspect"
run status u7
assert_eq "starting container" "starting 172.30.0.5:6080" "$out"
echo "running||" > "$FAKE_DIR/inspect"
run status u7
assert_eq "running without an address yet" "starting" "$out"
echo "exited||" > "$FAKE_DIR/inspect"
run status u7
assert_eq "exited container" "stopped" "$out"

echo "start launches a locked-down container on an internal network"
fresh
run start u7
assert_eq "exit status" 0 "$rc"
assert_eq "printed state" "starting 172.30.0.9:6080" "$out"
assert_calls_contain "internal network" "network create --internal"
assert_calls_contain "sessions cannot reach each other" "--opt com.docker.network.bridge.enable_icc=false"
assert_calls_contain "fixed bridge name for the host firewall" "--opt com.docker.network.bridge.name=gdxeditors0 gdxunity-editors"
assert_calls_contain "named after the user" "--name gdxunity-editor-u7"
assert_calls_contain "user volume" "--volume gdxunity-editor-data-u7:/data"
assert_calls_contain "no capabilities" "--cap-drop ALL"
assert_calls_contain "no new privileges" "--security-opt no-new-privileges"
assert_calls_contain "read-only root" "--read-only"
assert_calls_contain "exec tmp for native libraries" "--tmpfs /tmp:rw,exec,nosuid,size=1g"
assert_calls_contain "memory limit" "--memory 3g"
assert_calls_contain "cpu limit" "--cpus 2"
assert_calls_contain "process limit" "--pids-limit 512"
assert_calls_contain "internal network used" "--network gdxunity-editors"
assert_calls_lack "no published port" " -p "
assert_calls_lack "no published port (long form)" "--publish"
assert_calls_lack "no host networking" "--network host"
assert_calls_lack "never privileged" "--privileged"
assert_calls_lack "no docker socket" "docker.sock"

echo "start reuses a running session and replaces a stopped one"
fresh
echo "running|healthy|172.30.0.5" > "$FAKE_DIR/inspect"
run start u7
assert_eq "running session is returned" "ready 172.30.0.5:6080" "$out"
assert_calls_lack "no second container" "run --detach"
fresh
echo "exited||" > "$FAKE_DIR/inspect"
run start u7
assert_eq "stopped session is replaced" "starting 172.30.0.9:6080" "$out"
assert_calls_contain "stopped container removed first" "rm -f gdxunity-editor-u7"
assert_calls_contain "new container started" "run --detach"

echo "capacity"
fresh
export FAKE_RUNNING=4
run start u7
assert_eq "exit status when full" 3 "$rc"
assert_calls_lack "nothing started when full" "run --detach"
case "$err" in *capacity*) ;; *) fail "capacity message missing: $err" ;; esac
fresh
export FAKE_RUNNING=3
run start u7
assert_eq "last free slot is usable" 0 "$rc"
fresh
export FAKE_RUNNING=1
GDXUNITY_MAX_SESSIONS=1 run start u7
assert_eq "limit from the environment" 3 "$rc"

echo "docker failures are reported"
fresh
export FAKE_RUN_FAILS=1
run start u7
assert_eq "exit status when docker run fails" 4 "$rc"

echo "stop keeps the volume"
fresh
echo "running|healthy|172.30.0.5" > "$FAKE_DIR/inspect"
run stop u7
assert_eq "stop prints none" "none" "$out"
assert_calls_contain "container stopped" "stop --time 20 gdxunity-editor-u7"
assert_calls_contain "container removed" "rm --force gdxunity-editor-u7"
assert_calls_lack "volume not removed" "volume rm"
run status u7
assert_eq "no container after stop" "none" "$out"

echo "list"
fresh
run list
assert_eq "list output" "u7 Up 2 minutes (healthy)" "$out"

echo
if [ "$failures" -eq 0 ]; then
    echo "All $checks checks passed"
else
    echo "$failures of $checks checks FAILED"
    exit 1
fi
