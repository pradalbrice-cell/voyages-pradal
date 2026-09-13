from pathlib import Path

p = Path('MonCycleApp/app/src/main/java/fr/moncycle/app/MainActivity.kt')
s = p.read_text(encoding='utf-8')

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

if 'import androidx.compose.material.icons.filled.Favorite' not in s:
    s=s.replace('import androidx.compose.material.icons.filled.Edit\n','import androidx.compose.material.icons.filled.Edit\nimport androidx.compose.material.icons.filled.Favorite\n')

brand='''        Image(\n            painter=painterResource(R.drawable.mon_cycle_icon),\n            contentDescription="Logo Mon Cycle",\n            modifier=Modifier\n                .size(58.dp)\n                .clip(RoundedCornerShape(17.dp))\n        )'''
brand2='''        Box(\n            modifier=Modifier.size(58.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF7789),Color(0xFFF33166)))),\n            contentAlignment=Alignment.Center\n        ){\n            Icon(Icons.Default.Favorite,contentDescription="Logo Mon Cycle",tint=Color.White,modifier=Modifier.size(36.dp))\n        }'''
s=s.replace(brand,brand2,1)

hero='''            Image(\n                painter=painterResource(R.drawable.mon_cycle_icon),\n                contentDescription=null,\n                modifier=Modifier.padding(10.dp).clip(RoundedCornerShape(22.dp))\n            )'''
hero2='''            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){\n                Icon(Icons.Default.Favorite,contentDescription=null,tint=Color.White,modifier=Modifier.size(68.dp))\n            }'''
s=s.replace(hero,hero2,1)

p.write_text(s,encoding='utf-8')
print('Mon Cycle build patch applied.')
