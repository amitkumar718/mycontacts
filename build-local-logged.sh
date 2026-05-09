#!/usr/bin/env sh
set -u

ROOT_DIR=$(cd "$(dirname "$0")" && pwd)
LOG_DIR="$ROOT_DIR/build/logs"
mkdir -p "$LOG_DIR"

STAMP=$(date '+%Y%m%d-%H%M%S')
LOG_FILE="$LOG_DIR/build-local-$STAMP.log"
LATEST_LOG="$LOG_DIR/latest.log"

run_and_log() {
    sh "$ROOT_DIR/build-local.sh" >>"$LOG_FILE" 2>&1
    return $?
}

{
    echo "MyContacts Archive local build log"
    echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S %z')"
    echo "Working directory: $ROOT_DIR"
    echo "User: $(id 2>/dev/null || true)"
    echo "Shell: ${SHELL:-unknown}"
    echo "Termux PREFIX: ${PREFIX:-unset}"
    echo "ANDROID_JAR: ${ANDROID_JAR:-unset}"
    echo "FRAMEWORK_RES: ${FRAMEWORK_RES:-unset}"
    echo "ANDROID_HOME: ${ANDROID_HOME:-unset}"
    echo "ANDROID_SDK_ROOT: ${ANDROID_SDK_ROOT:-unset}"
    echo
    echo "System"
    uname -a 2>/dev/null || true
    getprop ro.build.version.release 2>/dev/null || true
    getprop ro.product.model 2>/dev/null || true
    ls -l /system/framework/framework-res.apk 2>/dev/null || true
    echo
    echo "Tool paths"
    for tool in sh aapt aapt2 javac java d8 apksigner keytool find cp date; do
        printf '%s: ' "$tool"
        command -v "$tool" 2>/dev/null || printf 'missing\n'
    done
    echo
    echo "Tool versions"
    java -version 2>&1 || true
    javac -version 2>&1 || true
    aapt version 2>&1 || true
    aapt2 version 2>&1 || true
    d8 --version 2>&1 || true
    apksigner --version 2>&1 || true
    echo
    echo "Project files"
    find "$ROOT_DIR" -maxdepth 8 -type f \
        ! -path "$ROOT_DIR/build/*" \
        ! -path "$ROOT_DIR/.git/*" \
        ! -path "$ROOT_DIR/.local-keystore/*" \
        | sort
    echo
    echo "Build output"
    echo "============"
} >"$LOG_FILE"

if run_and_log; then
    STATUS=0
else
    STATUS=$?
fi

{
    echo
    echo "============"
    echo "Final status: $STATUS"
    echo "Finished: $(date '+%Y-%m-%d %H:%M:%S %z')"
    if [ -f "$ROOT_DIR/build/local/mycontacts-debug.apk" ]; then
        echo "APK: $ROOT_DIR/build/local/mycontacts-debug.apk"
        ls -l "$ROOT_DIR/build/local/mycontacts-debug.apk" 2>/dev/null || true
    fi
} >>"$LOG_FILE"

cp "$LOG_FILE" "$LATEST_LOG"

echo "Log: $LOG_FILE"
echo "Latest log: $LATEST_LOG"
exit "$STATUS"
