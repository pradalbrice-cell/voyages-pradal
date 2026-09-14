from pathlib import Path

p = Path('MonCycleApp/app/src/main/java/fr/moncycle/app/MainActivity.kt')
s = p.read_text(encoding='utf-8')

# Marker colour customisation.
if 'MarkerColorState.load(this)' not in s:
    s = s.replace('super.onCreate(savedInstanceState)\n        setContent {','super.onCreate(savedInstanceState)\n        MarkerColorState.load(this)\n        setContent {')

if 'item{MarkerColorSettings()}' not in s:
    needle='''        item{\n            ModernSettingsCard{\n                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){\n                    Text("Confidentialité",fontWeight=FontWeight.ExtraBold,color=TextDark)'''
    replacement='''        item{MarkerColorSettings()}\n        item{\n            ModernSettingsCard{\n                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){\n                    Text("Confidentialité",fontWeight=FontWeight.ExtraBold,color=TextDark)'''
    if needle not in s: raise SystemExit('Could not find confidentiality settings insertion point')
    s=s.replace(needle,replacement,1)

old='''private fun statusColor(t:DayType?)=when(t){\n    DayType.PERIOD->Period\n    DayType.FERTILE->Fertile\n    DayType.OVULATION->Ovulation\n    DayType.LOW->Low\n    else->Color(0xFFD8D1DA)\n}'''
if old in s: s=s.replace(old,'private fun statusColor(t:DayType?)=MarkerColorState.color(t)',1)

oldfg='''    val fg=when(status?.type){\n        DayType.FERTILE,DayType.OVULATION->Color.White\n        else->TextDark\n    }'''
if oldfg in s: s=s.replace(oldfg,'    val fg=readableTextColor(bg)',1)

oldtxt='color=if(st?.type==DayType.FERTILE||st?.type==DayType.OVULATION)Color.White else TextDark,'
if oldtxt in s: s=s.replace(oldtxt,'color=readableTextColor(if(st?.type==null||st.type==DayType.NONE)Color(0xFFF7F5F8) else bg),',1)

s=s.replace('''iconColor=Primary,\n                    iconBg=PinkSoft,''','''iconColor=statusColor(DayType.PERIOD),\n                    iconBg=statusColor(DayType.PERIOD).copy(alpha=.16f),''',1)
s=s.replace('''iconColor=Ovulation,\n                    iconBg=PurpleSoft,''','''iconColor=statusColor(DayType.OVULATION),\n                    iconBg=statusColor(DayType.OVULATION).copy(alpha=.16f),''',1)

header='''@Composable private fun FertileWindowCard(\n    range:String,\n    enabled:Boolean,\n    onClick:()->Unit\n){\n    Card('''
if header in s:
    s=s.replace(header,'''@Composable private fun FertileWindowCard(\n    range:String,\n    enabled:Boolean,\n    onClick:()->Unit\n){\n    val marker=statusColor(DayType.FERTILE)\n    Card(''',1)
    s=s.replace('Modifier.size(48.dp).background(PinkSoft,CircleShape)','Modifier.size(48.dp).background(marker.copy(alpha=.16f),CircleShape)',1)
    s=s.replace('Icon(Icons.Default.CalendarMonth,null,tint=Primary)','Icon(Icons.Default.CalendarMonth,null,tint=marker)',1)

# First-use setup: ask for the first day of the latest period.
if 'import android.app.DatePickerDialog' not in s:
    s=s.replace('import android.content.Context\n','import android.app.DatePickerDialog\nimport android.content.Context\n',1)
if 'import androidx.compose.ui.platform.LocalContext' not in s:
    s=s.replace('import androidx.compose.ui.res.painterResource\n','import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.res.painterResource\n',1)

if 'showFirstPeriodSetup' not in s:
    s=s.replace(
        '''    var tab by remember{mutableIntStateOf(0)}\n    var edit by remember{mutableStateOf<LocalDate?>(null)}''',
        '''    var tab by remember{mutableIntStateOf(0)}\n    var edit by remember{mutableStateOf<LocalDate?>(null)}\n    var showFirstPeriodSetup by remember{\n        mutableStateOf(lastPeriodStart(entries,LocalDate.now())==null)\n    }''',
        1
    )
    s=s.replace(
        '0->HomeScreen(settings,entries,{edit=it},{tab=1})',
        '''0->HomeScreen(settings,entries,{d->\n                    if(d==LocalDate.now() && lastPeriodStart(entries,LocalDate.now())==null)\n                        showFirstPeriodSetup=true\n                    else edit=d\n                },{tab=1})''',
        1
    )

if 'FirstPeriodSetupDialog(' not in s:
    app_end='''        ){t->\n            store.setDay(d,t)\n            entries=store.entries()\n            edit=null\n        }\n    }\n}\n\n@Composable private fun HomeScreen('''
    app_replacement='''        ){t->\n            store.setDay(d,t)\n            entries=store.entries()\n            edit=null\n        }\n    }\n\n    if(showFirstPeriodSetup && lastPeriodStart(entries,LocalDate.now())==null){\n        FirstPeriodSetupDialog(\n            dismiss={showFirstPeriodSetup=false}\n        ){d->\n            store.setDay(d,DayType.PERIOD)\n            entries=store.entries()\n            showFirstPeriodSetup=false\n        }\n    }\n}\n\n@Composable private fun FirstPeriodSetupDialog(\n    dismiss:()->Unit,\n    save:(LocalDate)->Unit\n){\n    val context=LocalContext.current\n    var selected by remember{mutableStateOf(LocalDate.now())}\n\n    AlertDialog(\n        onDismissRequest=dismiss,\n        shape=RoundedCornerShape(28.dp),\n        title={\n            Column(verticalArrangement=Arrangement.spacedBy(4.dp)){\n                Text("Bienvenue dans Mon Cycle",fontWeight=FontWeight.ExtraBold)\n                Text(\n                    "Configurons votre cycle",\n                    style=MaterialTheme.typography.bodyMedium,\n                    color=Primary\n                )\n            }\n        },\n        text={\n            Column(verticalArrangement=Arrangement.spacedBy(14.dp)){\n                Text(\n                    "Quel était le premier jour de vos dernières règles ? Cette date permet de calculer le jour du cycle, les prochaines règles, l’ovulation et la fenêtre fertile.",\n                    color=TextDark\n                )\n                Surface(\n                    color=PinkSoft,\n                    shape=RoundedCornerShape(18.dp),\n                    modifier=Modifier.fillMaxWidth()\n                ){\n                    Column(Modifier.padding(16.dp)){\n                        Text("Date sélectionnée",fontSize=12.sp,color=Muted)\n                        Text(\n                            selected.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Fr))\n                                .replaceFirstChar{it.uppercase()},\n                            fontSize=19.sp,\n                            fontWeight=FontWeight.ExtraBold,\n                            color=PrimaryDark\n                        )\n                    }\n                }\n                OutlinedButton(\n                    onClick={\n                        DatePickerDialog(\n                            context,\n                            {_,year,month,day->selected=LocalDate.of(year,month+1,day)},\n                            selected.year,\n                            selected.monthValue-1,\n                            selected.dayOfMonth\n                        ).apply{datePicker.maxDate=System.currentTimeMillis()}.show()\n                    },\n                    modifier=Modifier.fillMaxWidth(),\n                    shape=RoundedCornerShape(16.dp)\n                ){\n                    Icon(Icons.Default.CalendarMonth,null)\n                    Spacer(Modifier.width(8.dp))\n                    Text("Choisir une autre date")\n                }\n                Text(\n                    "Vous pourrez modifier cette information plus tard depuis le calendrier.",\n                    style=MaterialTheme.typography.bodySmall,\n                    color=Muted\n                )\n            }\n        },\n        confirmButton={\n            Button(onClick={save(selected)},shape=RoundedCornerShape(16.dp)){\n                Text("Commencer")\n            }\n        },\n        dismissButton={\n            TextButton(onClick=dismiss){Text("Plus tard")}\n        }\n    )\n}\n\n@Composable private fun HomeScreen('''
    if app_end not in s: raise SystemExit('Could not find App insertion point for first-period setup')
    s=s.replace(app_end,app_replacement,1)

# Use exactly the logo chosen by the user, not a redrawn substitute.
s=s.replace('R.drawable.mon_cycle_icon','R.drawable.mon_cycle_selected')
s=s.replace('R.drawable.ic_mon_cycle_launcher','R.drawable.mon_cycle_selected')

p.write_text(s,encoding='utf-8')
print('Mon Cycle V3.3 patch applied: onboarding + selected logo + marker colours.')
