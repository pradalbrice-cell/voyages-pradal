from pathlib import Path

p = Path('MonCycleApp/app/src/main/java/fr/moncycle/app/MainActivity.kt')
s = p.read_text(encoding='utf-8')

# Explicit close icon for the onboarding dialog.
if 'import androidx.compose.material.icons.filled.Close' not in s:
    s = s.replace(
        'import androidx.compose.material.icons.filled.CalendarMonth\n',
        'import androidx.compose.material.icons.filled.CalendarMonth\nimport androidx.compose.material.icons.filled.Close\n',
        1
    )

# Persist a definitive "initial setup completed" flag.
store_anchor = '''    fun setAuto(v:Boolean)=p.edit().putBoolean("auto",v).apply()\n\n    fun entries():Map<LocalDate,DayType> ='''
store_replacement = '''    fun setAuto(v:Boolean)=p.edit().putBoolean("auto",v).apply()\n\n    fun setupComplete():Boolean {\n        if(p.getBoolean("setup_complete",false)) return true\n        // Migration for users who already entered period data before this onboarding existed.\n        val alreadyConfigured=entries().values.any{it==DayType.PERIOD}\n        if(alreadyConfigured) p.edit().putBoolean("setup_complete",true).apply()\n        return alreadyConfigured\n    }\n    fun markSetupComplete()=p.edit().putBoolean("setup_complete",true).apply()\n\n    fun entries():Map<LocalDate,DayType> ='''
if 'fun setupComplete():Boolean' not in s:
    if store_anchor not in s:
        raise SystemExit('Store insertion point not found')
    s = s.replace(store_anchor, store_replacement, 1)

# On a genuinely unconfigured install, open automatically at startup.
old_state = '''    var showFirstPeriodSetup by remember{\n        mutableStateOf(lastPeriodStart(entries,LocalDate.now())==null)\n    }'''
old_false_state = '''    var showFirstPeriodSetup by remember{mutableStateOf(false)}'''
new_state = '''    var setupComplete by remember{mutableStateOf(store.setupComplete())}\n    var showFirstPeriodSetup by remember{mutableStateOf(!setupComplete)}'''
if old_state in s:
    s = s.replace(old_state, new_state, 1)
elif old_false_state in s:
    s = s.replace(old_false_state, new_state, 1)
elif 'var setupComplete by remember' not in s:
    raise SystemExit('First-period setup state not found')

# Today reopens onboarding only while setup has never been completed.
old_click = '''0->HomeScreen(settings,entries,{d->\n                    if(d==LocalDate.now() && lastPeriodStart(entries,LocalDate.now())==null)\n                        showFirstPeriodSetup=true\n                    else edit=d\n                },{tab=1})'''
new_click = '''0->HomeScreen(settings,entries,{d->\n                    if(d==LocalDate.now() && !setupComplete)\n                        showFirstPeriodSetup=true\n                    else edit=d\n                },{tab=1})'''
if old_click in s:
    s = s.replace(old_click, new_click, 1)
elif '!setupComplete' not in s:
    raise SystemExit('Today click onboarding condition not found')

# Once a date is validated, permanently mark onboarding as complete.
old_dialog = '''    if(showFirstPeriodSetup && lastPeriodStart(entries,LocalDate.now())==null){\n        FirstPeriodSetupDialog(\n            dismiss={showFirstPeriodSetup=false}\n        ){d->\n            store.setDay(d,DayType.PERIOD)\n            entries=store.entries()\n            showFirstPeriodSetup=false\n        }\n    }'''
new_dialog = '''    if(showFirstPeriodSetup && !setupComplete){\n        FirstPeriodSetupDialog(\n            dismiss={showFirstPeriodSetup=false}\n        ){d->\n            store.setDay(d,DayType.PERIOD)\n            store.markSetupComplete()\n            entries=store.entries()\n            setupComplete=true\n            showFirstPeriodSetup=false\n        }\n    }'''
if old_dialog in s:
    s = s.replace(old_dialog, new_dialog, 1)
elif 'store.markSetupComplete()' not in s:
    raise SystemExit('Onboarding save block not found')

# Add a visible close X in the top-right of the popup.
old_title = '''        title={\n            Column(verticalArrangement=Arrangement.spacedBy(4.dp)){\n                Text("Bienvenue dans Mon Cycle",fontWeight=FontWeight.ExtraBold)\n                Text(\n                    "Configurons votre cycle",\n                    style=MaterialTheme.typography.bodyMedium,\n                    color=Primary\n                )\n            }\n        },'''
new_title = '''        title={\n            Row(\n                Modifier.fillMaxWidth(),\n                verticalAlignment=Alignment.Top,\n                horizontalArrangement=Arrangement.SpaceBetween\n            ){\n                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){\n                    Text("Bienvenue dans Mon Cycle",fontWeight=FontWeight.ExtraBold)\n                    Text(\n                        "Configurons votre cycle",\n                        style=MaterialTheme.typography.bodyMedium,\n                        color=Primary\n                    )\n                }\n                IconButton(onClick=dismiss){\n                    Icon(Icons.Default.Close,contentDescription="Fermer")\n                }\n            }\n        },'''
if old_title in s:
    s = s.replace(old_title, new_title, 1)
elif 'contentDescription="Fermer"' not in s:
    raise SystemExit('Onboarding title block not found')

# The X is the intentional skip action; remove the redundant "Plus tard" button.
old_dismiss = '''        dismissButton={\n            TextButton(onClick=dismiss){Text("Plus tard")}\n        }'''
if old_dismiss in s:
    s = s.replace(old_dismiss, '        dismissButton={}', 1)

p.write_text(s, encoding='utf-8')
print('Persistent onboarding applied: auto-open once, X dismiss, Today retry, never reopen after date validation.')
