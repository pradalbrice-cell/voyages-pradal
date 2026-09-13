from pathlib import Path

p = Path('MonCycleApp/app/src/main/java/fr/moncycle/app/MainActivity.kt')
s = p.read_text(encoding='utf-8')

# Idempotent patch: once MainActivity contains the marker-color integration,
# subsequent builds leave it untouched.
if 'MarkerColorState.load(this)' not in s:
    s = s.replace(
        'super.onCreate(savedInstanceState)\n        setContent {',
        'super.onCreate(savedInstanceState)\n        MarkerColorState.load(this)\n        setContent {'
    )

if 'item{MarkerColorSettings()}' not in s:
    needle = '''        item{\n            ModernSettingsCard{\n                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){\n                    Text("Confidentialité",fontWeight=FontWeight.ExtraBold,color=TextDark)'''
    replacement = '''        item{MarkerColorSettings()}\n        item{\n            ModernSettingsCard{\n                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){\n                    Text("Confidentialité",fontWeight=FontWeight.ExtraBold,color=TextDark)'''
    if needle not in s:
        raise SystemExit('Could not find confidentiality settings insertion point')
    s = s.replace(needle, replacement, 1)

old_status = '''private fun statusColor(t:DayType?)=when(t){\n    DayType.PERIOD->Period\n    DayType.FERTILE->Fertile\n    DayType.OVULATION->Ovulation\n    DayType.LOW->Low\n    else->Color(0xFFD8D1DA)\n}'''
new_status = '''private fun statusColor(t:DayType?)=MarkerColorState.color(t)'''
if old_status in s:
    s = s.replace(old_status, new_status, 1)

old_fg = '''    val fg=when(status?.type){\n        DayType.FERTILE,DayType.OVULATION->Color.White\n        else->TextDark\n    }'''
if old_fg in s:
    s = s.replace(old_fg, '    val fg=readableTextColor(bg)', 1)

old_month_text = '''color=if(st?.type==DayType.FERTILE||st?.type==DayType.OVULATION)Color.White else TextDark,'''
new_month_text = '''color=readableTextColor(if(st?.type==null||st.type==DayType.NONE)Color(0xFFF7F5F8) else bg),'''
if old_month_text in s:
    s = s.replace(old_month_text, new_month_text, 1)

# Use the selected period/ovulation colors on the two key home cards.
s = s.replace(
    '''iconColor=Primary,\n                    iconBg=PinkSoft,''',
    '''iconColor=statusColor(DayType.PERIOD),\n                    iconBg=statusColor(DayType.PERIOD).copy(alpha=.16f),''',
    1
)
s = s.replace(
    '''iconColor=Ovulation,\n                    iconBg=PurpleSoft,''',
    '''iconColor=statusColor(DayType.OVULATION),\n                    iconBg=statusColor(DayType.OVULATION).copy(alpha=.16f),''',
    1
)

# Use the selected fertile color on the fertile-window icon.
header = '''@Composable private fun FertileWindowCard(\n    range:String,\n    enabled:Boolean,\n    onClick:()->Unit\n){\n    Card('''
if header in s:
    s = s.replace(
        header,
        '''@Composable private fun FertileWindowCard(\n    range:String,\n    enabled:Boolean,\n    onClick:()->Unit\n){\n    val marker=statusColor(DayType.FERTILE)\n    Card(''',
        1
    )
    s = s.replace('Modifier.size(48.dp).background(PinkSoft,CircleShape)',
                  'Modifier.size(48.dp).background(marker.copy(alpha=.16f),CircleShape)', 1)
    s = s.replace('Icon(Icons.Default.CalendarMonth,null,tint=Primary)',
                  'Icon(Icons.Default.CalendarMonth,null,tint=marker)', 1)

p.write_text(s, encoding='utf-8')
print('Marker color integration applied.')
