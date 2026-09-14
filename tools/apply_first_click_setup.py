from pathlib import Path

p = Path('MonCycleApp/app/src/main/java/fr/moncycle/app/MainActivity.kt')
s = p.read_text(encoding='utf-8')

old = '''    var showFirstPeriodSetup by remember{\n        mutableStateOf(lastPeriodStart(entries,LocalDate.now())==null)\n    }'''
new = '''    var showFirstPeriodSetup by remember{mutableStateOf(false)}'''

if old in s:
    s = s.replace(old, new, 1)
elif 'var showFirstPeriodSetup by remember{mutableStateOf(false)}' not in s:
    raise SystemExit('First-period setup state not found')

p.write_text(s, encoding='utf-8')
print('First-period setup now opens only after tapping Today.')
