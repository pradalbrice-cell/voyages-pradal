from pathlib import Path

p = Path('MonCycleApp/app/src/main/java/fr/moncycle/app/MainActivity.kt')
s = p.read_text(encoding='utf-8')

# Imports for the first-start date picker.
if 'import androidx.compose.material3.DatePicker' not in s:
    s = s.replace('import androidx.compose.material3.*\n', 'import androidx.compose.material3.*\n')
if 'import java.time.Instant' not in s:
    s = s.replace('import java.time.LocalDate\n', 'import java.time.LocalDate\nimport java.time.Instant\nimport java.time.ZoneId\n')

old_call = '0->HomeScreen(settings,entries,{edit=it},{tab=1})'
new_call = '0->HomeScreen(settings,entries,{edit=it},{tab=1}){d->store.setDay(d,DayType.PERIOD);entries=store.entries()}'
if old_call in s:
    s = s.replace(old_call, new_call, 1)

old_sig = '''@Composable private fun HomeScreen(\n    s:SettingsData,\n    e:Map<LocalDate,DayType>,\n    edit:(LocalDate)->Unit,\n    openCalendar:()->Unit\n){'''
new_sig = '''@OptIn(ExperimentalMaterial3Api::class)\n@Composable private fun HomeScreen(\n    s:SettingsData,\n    e:Map<LocalDate,DayType>,\n    edit:(LocalDate)->Unit,\n    openCalendar:()->Unit,\n    setLastPeriod:(LocalDate)->Unit\n){'''
if old_sig in s:
    s = s.replace(old_sig, new_sig, 1)

anchor_needle = '''    val fertileStart=estimatedOvulation?.minusDays(5)\n    val fertileEnd=estimatedOvulation?.plusDays(1)\n'''
anchor_repl = '''    val fertileStart=estimatedOvulation?.minusDays(5)\n    val fertileEnd=estimatedOvulation?.plusDays(1)\n    var showFirstStart by remember{mutableStateOf(false)}\n    val openToday = { if(anchor==null) showFirstStart=true else edit(today) }\n'''
if anchor_needle in s and 'var showFirstStart by remember' not in s:
    s = s.replace(anchor_needle, anchor_repl, 1)

s = s.replace('onClick={edit(today)}', 'onClick=openToday', 2)

end_needle = '''        item{\n            Text(\n                "Les estimations de fertilité sont indicatives et ne constituent pas une méthode contraceptive.",'''
if end_needle in s and 'FirstStartPeriodDialog(' not in s:
    # Insert dialog after LazyColumn closes, before HomeScreen closes.
    target = '''        }\n    }\n}\n\n@Composable private fun BrandHeader()'''
    dialog = '''        }\n    }\n\n    if(showFirstStart){\n        FirstStartPeriodDialog(\n            onDismiss={showFirstStart=false},\n            onConfirm={d->\n                setLastPeriod(d)\n                showFirstStart=false\n            }\n        )\n    }\n}\n\n@OptIn(ExperimentalMaterial3Api::class)\n@Composable private fun FirstStartPeriodDialog(\n    onDismiss:()->Unit,\n    onConfirm:(LocalDate)->Unit\n){\n    val today=LocalDate.now()\n    val initialMillis=today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()\n    val state=rememberDatePickerState(\n        initialSelectedDateMillis=initialMillis,\n        selectableDates=object:SelectableDates{\n            override fun isSelectableDate(utcTimeMillis:Long):Boolean {\n                val d=Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneId.of("UTC")).toLocalDate()\n                return !d.isAfter(today) && !d.isBefore(today.minusYears(1))\n            }\n        }\n    )\n    AlertDialog(\n        onDismissRequest=onDismiss,\n        shape=RoundedCornerShape(28.dp),\n        title={\n            Column{\n                Text("Bien démarrer Mon Cycle",fontWeight=FontWeight.ExtraBold)\n                Spacer(Modifier.height(6.dp))\n                Text(\n                    "Quelle est la date du premier jour de vos dernières règles ?",\n                    style=MaterialTheme.typography.bodyMedium,\n                    color=Muted\n                )\n            }\n        },\n        text={\n            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){\n                DatePicker(state=state)\n                Text(\n                    "Cette date sert de point de départ pour estimer votre cycle, l’ovulation et la fenêtre fertile. Vous pourrez tout modifier ensuite.",\n                    style=MaterialTheme.typography.bodySmall,\n                    color=Muted\n                )\n            }\n        },\n        confirmButton={\n            Button(\n                onClick={\n                    state.selectedDateMillis?.let{ms->\n                        val d=Instant.ofEpochMilli(ms).atZone(ZoneId.of("UTC")).toLocalDate()\n                        onConfirm(d)\n                    }\n                },\n                enabled=state.selectedDateMillis!=null\n            ){Text("Démarrer mon cycle")}\n        },\n        dismissButton={TextButton(onClick=onDismiss){Text("Plus tard")}}\n    )\n}\n\n@Composable private fun BrandHeader()'''
    if target not in s:
        raise SystemExit('Could not find HomeScreen closing point')
    s = s.replace(target, dialog, 1)

p.write_text(s, encoding='utf-8')
print('First-start period setup patch applied.')
