#!/usr/bin/env bash
set -euo pipefail

dump_crash_and_exit() {
  echo "=== PROCESS STATUS ==="
  adb shell pidof fr.moncycle.app || true
  echo "=== ANDROIDRUNTIME / FATAL LOGS ==="
  adb logcat -d -v time | grep -E -A40 -B10 "FATAL EXCEPTION|AndroidRuntime|fr.moncycle.app" | tail -n 240 || true
  exit 1
}

dump_ui() {
  local remote="$1"
  local localfile="$2"
  local i
  for i in 1 2 3 4 5; do
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    if adb shell uiautomator dump "$remote" >/dev/null 2>&1 && adb pull "$remote" "$localfile" >/dev/null 2>&1 && test -s "$localfile"; then
      return 0
    fi
    sleep 2
  done
  echo "UI dump failed after retries: $remote"
  dump_crash_and_exit
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
dump_ui /sdcard/setup1.xml /tmp/setup1.xml
grep -q "Bienvenue dans Mon Cycle" /tmp/setup1.xml
grep -q "Quel était le premier jour de vos dernières règles" /tmp/setup1.xml
grep -q "Commencer" /tmp/setup1.xml
grep -q 'content-desc="Fermer"' /tmp/setup1.xml

# 2. Closing with X does not complete setup, and the selected logo is to the right of MON CYCLE.
tap_label /tmp/setup1.xml "Fermer"
sleep 2
dump_ui /sdcard/home_unconfigured.xml /tmp/home_unconfigured.xml
! grep -q "Bienvenue dans Mon Cycle" /tmp/home_unconfigured.xml
grep -q "Accueil" /tmp/home_unconfigured.xml
grep -q 'text="MON CYCLE"' /tmp/home_unconfigured.xml
grep -q 'content-desc="Logo Mon Cycle"' /tmp/home_unconfigured.xml
python3 - /tmp/home_unconfigured.xml <<'PY'
import re, sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
def center(kind,value):
    for n in root.iter('node'):
        if n.attrib.get(kind)==value:
            m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
            if m:
                x1,y1,x2,y2=map(int,m.groups()); return ((x1+x2)//2,(y1+y2)//2)
    raise SystemExit(f'missing {kind}={value}')
title=center('text','MON CYCLE')
logo=center('content-desc','Logo Mon Cycle')
assert logo[0] > title[0], (title,logo)
PY

# 3. Tapping Today reopens onboarding while still unconfigured.
tap_label /tmp/home_unconfigured.xml "Aujourd’hui"
sleep 2
dump_ui /sdcard/setup2.xml /tmp/setup2.xml
grep -q "Bienvenue dans Mon Cycle" /tmp/setup2.xml
grep -q "Commencer" /tmp/setup2.xml

# 4. Validate date: onboarding completes permanently.
tap_label /tmp/setup2.xml "Commencer"
sleep 2
dump_ui /sdcard/configured.xml /tmp/configured.xml
! grep -q "Bienvenue dans Mon Cycle" /tmp/configured.xml
grep -q "Jour 1" /tmp/configured.xml || grep -q "Règles" /tmp/configured.xml

# 5. After setup, tapping Today must NOT reopen onboarding.
tap_label /tmp/configured.xml "Aujourd’hui"
sleep 2
dump_ui /sdcard/today_after_setup.xml /tmp/today_after_setup.xml
! grep -q "Bienvenue dans Mon Cycle" /tmp/today_after_setup.xml

# 6. Restart: onboarding must still never reappear.
adb shell am force-stop fr.moncycle.app
adb shell am start -W -n fr.moncycle.app/.MainActivity >/tmp/restart.txt
sleep 4
PID=$(adb shell pidof fr.moncycle.app || true)
[ -n "$PID" ] || dump_crash_and_exit
dump_ui /sdcard/restart.xml /tmp/restart.xml
! grep -q "Bienvenue dans Mon Cycle" /tmp/restart.xml
grep -q 'content-desc="Logo Mon Cycle"' /tmp/restart.xml

# 7. Basic navigation still works.
tap_label /tmp/restart.xml "Calendrier"
sleep 2
dump_ui /sdcard/calendar.xml /tmp/calendar.xml
test "$(grep -o 'text=\"Calendrier\"' /tmp/calendar.xml | wc -l)" -ge 2

tap_label /tmp/calendar.xml "Réglages"
sleep 2
dump_ui /sdcard/settings.xml /tmp/settings.xml
grep -q "Calcul automatique" /tmp/settings.xml

if adb logcat -d -v time | grep -q "FATAL EXCEPTION"; then dump_crash_and_exit; fi

echo "MON_CYCLE_V3_6_LOGO_AND_ONBOARDING_TEST_OK"
