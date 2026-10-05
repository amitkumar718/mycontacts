#!/usr/bin/env bash
# Build APK, start AVD if not running, install and launch MyContacts.
# Usage: ./build-run.sh [avd-name]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
AVD_NAME="${1:-Pixel_7}"
PACKAGE="com.ldsa.mycontacts"
MAIN_ACTIVITY="$PACKAGE/.ui.MainActivity"

# ── Build ─────────────────────────────────────────────────────────────────────
echo "Building..."
cd "$SCRIPT_DIR"
bash build-wsl.sh

# ── Start emulator if not running ─────────────────────────────────────────────
if ! adb devices | grep -q "emulator"; then
    echo "Starting emulator $AVD_NAME..."
    powershell.exe -Command "Set-Location \$env:USERPROFILE; Start-Process \"\$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe\" -ArgumentList '-avd','$AVD_NAME','-no-snapshot-load'"

    echo "Waiting for emulator to appear..."
    until adb devices | grep -q "emulator"; do
        sleep 2
    done

    echo "Waiting for boot..."
    until adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' | grep -q "^1$"; do
        sleep 3
    done
    echo "Emulator ready"
else
    echo "Emulator already running"
fi

# ── Install ───────────────────────────────────────────────────────────────────
echo "Installing $APK..."
adb install -r "$APK"

# ── Grant runtime permissions ─────────────────────────────────────────────────
adb shell pm grant "$PACKAGE" android.permission.READ_CONTACTS  2>/dev/null || true
adb shell pm grant "$PACKAGE" android.permission.WRITE_CONTACTS 2>/dev/null || true
adb shell pm grant "$PACKAGE" android.permission.GET_ACCOUNTS   2>/dev/null || true

# ── Launch ────────────────────────────────────────────────────────────────────
echo "Launching MyContacts..."
adb shell am start -n "$MAIN_ACTIVITY"

echo ""
echo "── Quick test checklist ──────────────────────────────────────────────────"
echo " 1. Main screen loads: archived list, A-Z / Labels toggle, search bar"
echo " 2. Tap + (FAB) → device contacts list → select → Archive"
echo " 3. Tap contact → detail view → Restore / Delete"
echo " 4. Long-press → multi-select mode → bulk Restore / Delete / Label"
echo " 5. Menu → Sync Check → Duplicates and Archive Only tabs"
echo " 6. Menu → Labels → create label, browse contacts by label"
echo " 7. Menu → Share Backup → CSV share sheet appears"
echo "─────────────────────────────────────────────────────────────────────────"
