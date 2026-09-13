#!/usr/bin/env bash
set -euo pipefail

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
adb logcat -c
adb shell am force-stop fr.moncycle.app
adb shell am start -W -n fr.moncycle.app/.MainActivity | tee /tmp/start.txt
grep -q "Status: ok" /tmp/start.txt
grep -q "Activity: fr.moncycle.app/.MainActivity" /tmp/start.txt
sleep 3

adb shell uiautomator dump /sdcard/home.xml >/dev/null
adb pull /sdcard/home.xml /tmp/home.xml >/dev/null
grep -q "Mon Cycle" /tmp/home.xml
grep -q "Accueil" /tmp/home.xml
grep -q "Calendrier" /tmp/home.xml
grep -q "Réglages" /tmp/home.xml

tap_label /tmp/home.xml "Calendrier"
sleep 2
adb shell uiautomator dump /sdcard/calendar.xml >/dev/null
adb pull /sdcard/calendar.xml /tmp/calendar.xml >/dev/null
test "$(sha256sum /tmp/home.xml | cut -d' ' -f1)" != "$(sha256sum /tmp/calendar.xml | cut -d' ' -f1)"
test "$(grep -o 'text=\"Calendrier\"' /tmp/calendar.xml | wc -l)" -ge 2

tap_label /tmp/calendar.xml "Réglages"
sleep 2
adb shell uiautomator dump /sdcard/settings.xml >/dev/null
adb pull /sdcard/settings.xml /tmp/settings.xml >/dev/null
test "$(sha256sum /tmp/calendar.xml | cut -d' ' -f1)" != "$(sha256sum /tmp/settings.xml | cut -d' ' -f1)"
grep -q "Calcul automatique" /tmp/settings.xml

tap_label /tmp/settings.xml "Accueil"
sleep 2
adb shell uiautomator dump /sdcard/home2.xml >/dev/null
adb pull /sdcard/home2.xml /tmp/home2.xml >/dev/null
grep -q "Mon Cycle" /tmp/home2.xml
grep -q "Aujourd" /tmp/home2.xml

if adb logcat -d -v time AndroidRuntime:E '*:S' | grep -q "Process: fr.moncycle.app"; then
  adb logcat -d -v time AndroidRuntime:E '*:S'
  exit 1
fi
if adb logcat -d -v time | grep -q "FATAL EXCEPTION"; then
  adb logcat -d -v time | grep -A20 -B5 "FATAL EXCEPTION"
  exit 1
fi

echo "MON_CYCLE_SMOKE_TEST_OK"
