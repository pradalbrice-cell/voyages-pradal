#!/usr/bin/env bash
set -euo pipefail

dump_crash_and_exit() {
  echo "=== PROCESS STATUS ==="
  adb shell pidof fr.moncycle.app || true
  echo "=== ANDROIDRUNTIME / FATAL LOGS ==="
  adb logcat -d -v time | grep -E -A40 -B10 "FATAL EXCEPTION|AndroidRuntime|fr.moncycle.app" | tail -n 240 || true
  exit 1
}

tap_label() {
  local file="$1"
  local label="$2"
  local coords x y
  coords=$(python3 - "$file" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, target = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
found = []
for node in root.iter('node'):
    if node.attrib.get('text') == target or node.attrib.get('content-desc') == target:
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2 = map(int,m.groups())
            found.append(((y1+y2)//2,(x1+x2)//2))
if not found:
    raise SystemExit('label not found: '+target)
y,x = max(found)
print(x,y)
PY
)
  x=$(echo "$coords" | cut -d' ' -f1)
  y=$(echo "$coords" | cut -d' ' -f2)
  adb shell input tap "$x" "$y"
}

APK="MonCycleApp/app/build/outputs/apk/debug/app-debug.apk"
adb install -r "$APK"
adb shell pm clear fr.moncycle.app
adb logcat -c
adb shell am start -W -n fr.moncycle.app/.MainActivity | tee /tmp/start.txt
grep -q "Status: ok" /tmp/start.txt || dump_crash_and_exit
sleep 4
PID=$(adb shell pidof fr.moncycle.app || true)
[ -n "$PID" ] || dump_crash_and_exit

# 1. Fresh install: onboarding opens automatically.
adb shell uiautomator dump /sdcard/setup1.xml >/dev/null || dump_crash_and_exit
adb pull /sdcard/setup1.xml /tmp/setup1.xml >/dev/null
grep -q "Bienvenue dans Mon Cycle" /tmp/setup1.xml
grep -q "Quel était le premier jour de vos dernières règles" /tmp/setup1.xml
grep -q "Commencer" /tmp/setup1.xml
grep -q 'content-desc="Fermer"' /tmp/setup1.xml

# 2. Closing with X does not complete setup.
tap_label /tmp/setup1.xml "Fermer"
sleep 2
adb shell uiautomator dump /sdcard/home_unconfigured.xml >/dev/null
adb pull /sdcard/home_unconfigured.xml /tmp/home_unconfigured.xml >/dev/null
! grep -q "Bienvenue dans Mon Cycle" /tmp/home_unconfigured.xml
grep -q "Accueil" /tmp/home_unconfigured.xml

# 3. Tapping Today reopens onboarding while still unconfigured.
tap_label /tmp/home_unconfigured.xml "Aujourd’hui"
sleep 2
adb shell uiautomator dump /sdcard/setup2.xml >/dev/null
adb pull /sdcard/setup2.xml /tmp/setup2.xml >/dev/null
grep -q "Bienvenue dans Mon Cycle" /tmp/setup2.xml
grep -q "Commencer" /tmp/setup2.xml

# 4. Validate date: onboarding completes permanently.
tap_label /tmp/setup2.xml "Commencer"
sleep 2
adb shell uiautomator dump /sdcard/configured.xml >/dev/null
adb pull /sdcard/configured.xml /tmp/configured.xml >/dev/null
! grep -q "Bienvenue dans Mon Cycle" /tmp/configured.xml
grep -q "Jour 1" /tmp/configured.xml || grep -q "Règles" /tmp/configured.xml

# 5. After setup, tapping Today must NOT reopen onboarding.
tap_label /tmp/configured.xml "Aujourd’hui"
sleep 2
adb shell uiautomator dump /sdcard/today_after_setup.xml >/dev/null
adb pull /sdcard/today_after_setup.xml /tmp/today_after_setup.xml >/dev/null
! grep -q "Bienvenue dans Mon Cycle" /tmp/today_after_setup.xml

# 6. Restart: onboarding must still never reappear.
adb shell am force-stop fr.moncycle.app
adb shell am start -W -n fr.moncycle.app/.MainActivity >/tmp/restart.txt
sleep 3
PID=$(adb shell pidof fr.moncycle.app || true)
[ -n "$PID" ] || dump_crash_and_exit
adb shell uiautomator dump /sdcard/restart.xml >/dev/null
adb pull /sdcard/restart.xml /tmp/restart.xml >/dev/null
! grep -q "Bienvenue dans Mon Cycle" /tmp/restart.xml

# 7. Basic navigation still works.
tap_label /tmp/restart.xml "Calendrier"
sleep 2
adb shell uiautomator dump /sdcard/calendar.xml >/dev/null
adb pull /sdcard/calendar.xml /tmp/calendar.xml >/dev/null
test "$(grep -o 'text=\"Calendrier\"' /tmp/calendar.xml | wc -l)" -ge 2

tap_label /tmp/calendar.xml "Réglages"
sleep 2
adb shell uiautomator dump /sdcard/settings.xml >/dev/null
adb pull /sdcard/settings.xml /tmp/settings.xml >/dev/null
grep -q "Calcul automatique" /tmp/settings.xml

if adb logcat -d -v time | grep -q "FATAL EXCEPTION"; then dump_crash_and_exit; fi

echo "MON_CYCLE_ONBOARDING_PERSISTENCE_TEST_OK"
