#!/usr/bin/env bash
# Comparison screenshots for Phase 13 (T13.2, 16-PHASE13-PLAN §7).
#
#   scripts/screens/capture.sh <label> [width_dp ...]     default widths: 412 390 360
#
# Opens every screen of screens.txt (plus SCREENS_FILE, if set) in the installed DEBUG build through the
# debug-only screen extra (DebugScreenRoute), at each width set with `wm density`, and saves
# build/screens/<label>/<width>dp/<name>.png and <name>.xml (uiautomator, for targets.py).
# The status bar is frozen with demo mode. Density and demo mode are restored on exit, even after Ctrl-C.
#
# Only on a test phone or emulator with the debug build: it changes the display density while it runs.
# Language and theme are not changed: set them in the app first and use them in the label (base-id-dark).
set -euo pipefail

readonly PACKAGE="com.ridecomp.app.debug"   # debug build, installed next to the release app (D-144)
readonly ACTIVITY="$PACKAGE/com.ridecomp.app.MainActivity"
readonly EXTRA_SCREEN="com.ridecomp.debug.SCREEN"
readonly DP_PER_INCH=160
readonly DEFAULT_WIDTHS=(412 390 360)
readonly SETTLE_S=2
readonly STABLE_TRIES=5

label="${1:?usage: capture.sh <label> [width_dp ...]}"
shift
if (( $# > 0 )); then widths=("$@"); else widths=("${DEFAULT_WIDTHS[@]}"); fi

root="$(cd "$(dirname "$0")/../.." && pwd)"
out="$root/build/screens/$label"
screens_file="$(dirname "$0")/screens.txt"

adb get-state >/dev/null 2>&1 || { echo "No device: connect a test phone or start an emulator." >&2; exit 1; }
adb shell pm list packages "$PACKAGE" | grep -qx "package:$PACKAGE" || { echo "$PACKAGE is not installed." >&2; exit 1; }

# The physical width in pixels (portrait), whatever override is active.
physical="$(adb shell wm size | sed -n 's/^Physical size: //p' | tr -d '\r')"
width_px="${physical%x*}"
height_px="${physical#*x}"
(( width_px > height_px )) && width_px="$height_px"
original_density="$(adb shell wm density | sed -n 's/^Override density: //p' | tr -d '\r')"
original_demo="$(adb shell settings get global sysui_demo_allowed | tr -d '\r')"

demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }

restore() {
    if [[ -n "$original_density" ]]; then adb shell wm density "$original_density"; else adb shell wm density reset; fi
    demo exit || true
    if [[ "$original_demo" == "null" ]]; then
        adb shell settings delete global sysui_demo_allowed >/dev/null || true
    else
        adb shell settings put global sysui_demo_allowed "$original_demo" || true
    fi
    adb shell rm -f /sdcard/ridecomp-screen.xml || true
    echo "Device restored (density, demo mode)."
}
trap restore EXIT

adb shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide
demo notifications -e visible false

# Screens: "<name> <route>" lines, comments and blank lines skipped. SCREENS_ONLY=<file> replaces the list
# (to retake a few screens), SCREENS_FILE=<file> adds to it.
screens() {
    local base="${SCREENS_ONLY:-$screens_file}"
    { cat "$base"; [[ -n "${SCREENS_FILE:-}" ]] && cat "$SCREENS_FILE"; true; } | grep -v '^\s*#' | grep -v '^\s*$'
}

# Takes screenshots until two in a row are identical: a screen that keeps changing is reported, not compared.
capture_stable() {
    local file="$1" previous="$1.previous"
    adb exec-out screencap -p </dev/null >"$previous"
    for _ in $(seq "$STABLE_TRIES"); do
        sleep 1
        adb exec-out screencap -p </dev/null >"$file"
        if cmp -s "$file" "$previous"; then rm -f "$previous"; return 0; fi
        mv "$file" "$previous"
    done
    mv "$previous" "$file"
    return 1
}

unstable=()
for width_dp in "${widths[@]}"; do
    density=$(( (width_px * DP_PER_INCH + width_dp / 2) / width_dp ))
    dir="$out/${width_dp}dp"
    mkdir -p "$dir"
    adb shell wm density "$density"
    sleep "$SETTLE_S"
    # Some ROMs (realme ColorOS) ignore demo mode: compare.py then skips the status bar rows.
    status_bar_px="$(adb shell dumpsys window | sed -n 's/.*type=statusBars frame=\[0,0\]\[[0-9]*,\([0-9]*\)\].*/\1/p' | head -1 | tr -d '\r')"
    printf 'width_dp=%s\ndensity=%s\nwidth_px=%s\nstatus_bar_px=%s\n' \
        "$width_dp" "$density" "$width_px" "${status_bar_px:-0}" >"$dir/device.txt"
    echo "== $width_dp dp (density $density, status bar ${status_bar_px:-?} px)"
    while read -r name route; do
        # adb reads stdin: </dev/null keeps the screen list for the loop.
        adb shell am start -W -n "$ACTIVITY" --es "$EXTRA_SCREEN" "$route" </dev/null >/dev/null
        sleep "$SETTLE_S"
        if capture_stable "$dir/$name.png"; then echo "  $name"; else echo "  $name (UNSTABLE)"; unstable+=("$width_dp/$name"); fi
        adb shell uiautomator dump /sdcard/ridecomp-screen.xml </dev/null >/dev/null 2>&1 \
            && adb pull /sdcard/ridecomp-screen.xml "$dir/$name.xml" </dev/null >/dev/null 2>&1 || true
    done < <(screens)
done

echo "Saved to $out"
if (( ${#unstable[@]} > 0 )); then
    echo "Still changing after ${STABLE_TRIES} s (animation or live data), compare by eye: ${unstable[*]}"
fi
