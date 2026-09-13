package fr.moncycle.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val Period = Color(0xFFFFC7CC)
private val Fertile = Color(0xFFFF1744)
private val Ovulation = Color(0xFF7657D5)
private val Low = Color(0xFFCDECCF)
private val Bg = Color(0xFFFFF8FA)
private val Primary = Color(0xFFF43B69)
private val PrimaryDark = Color(0xFFA80F3B)
private val PinkSoft = Color(0xFFFFE8EE)
private val PurpleSoft = Color(0xFFF1EAFE)
private val TextDark = Color(0xFF21172D)
private val Muted = Color(0xFF786D80)
private val Fr = Locale.FRENCH

enum class DayType { PERIOD, FERTILE, OVULATION, LOW, NONE }
data class SettingsData(val cycle:Int=28,val period:Int=5,val auto:Boolean=true,val lock:Boolean=false)
data class Status(val type:DayType,val predicted:Boolean)

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MarkerColorState.load(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Primary,
                    onPrimary = Color.White,
                    secondary = Ovulation,
                    background = Bg,
                    surface = Color.White
                )
            ) {
                App(Store(this))
            }
        }
    }
}

class Store(context: Context) {
    private val p=context.getSharedPreferences("mon_cycle",Context.MODE_PRIVATE)
    fun settings()=SettingsData(
        p.getInt("cycle",28),
        p.getInt("period",5),
        p.getBoolean("auto",true),
        p.getBoolean("lock",false)
    )
    fun setCycle(v:Int)=p.edit().putInt("cycle",v.coerceIn(20,45)).apply()
    fun setPeriod(v:Int)=p.edit().putInt("period",v.coerceIn(2,10)).apply()
    fun setAuto(v:Boolean)=p.edit().putBoolean("auto",v).apply()

    fun entries():Map<LocalDate,DayType> =
        p.getStringSet("days", emptySet()).orEmpty().mapNotNull { s ->
            val a=s.split('|')
            if(a.size!=2) null else runCatching {
                LocalDate.parse(a[0]) to DayType.valueOf(a[1])
            }.getOrNull()
        }.toMap()

    fun setDay(d:LocalDate,t:DayType?) {
        val m=entries().toMutableMap()
        if(t==null)m.remove(d) else m[d]=t
        p.edit().putStringSet("days",m.map{"${it.key}|${it.value}"}.toSet()).apply()
    }

    fun enablePassword(s:String){
        p.edit().putString("pw",sha(s)).putBoolean("lock",true).apply()
    }
    fun disablePassword(){
        p.edit().remove("pw").putBoolean("lock",false).apply()
    }
    fun verify(s:String)=!p.getBoolean("lock",false)||p.getString("pw","")==sha(s)
    private fun sha(s:String)=MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray())
        .joinToString(""){"%02x".format(it)}
}

private fun lastPeriodStart(entries:Map<LocalDate,DayType>, limit:LocalDate):LocalDate? =
    entries
        .filter{(d,t)->t==DayType.PERIOD&&!d.isAfter(limit)}
        .keys
        .filter{entries[it.minusDays(1)]!=DayType.PERIOD}
        .maxOrNull()

private fun statusFor(date:LocalDate, entries:Map<LocalDate,DayType>, s:SettingsData):Status? {
    entries[date]?.let{return Status(it,false)}
    if(!s.auto)return null
    var a=lastPeriodStart(entries,date)?:return null
    while(!a.plusDays(s.cycle.toLong()).isAfter(date)) a=a.plusDays(s.cycle.toLong())
    val day=ChronoUnit.DAYS.between(a,date).toInt()
    val ovu=(s.cycle-14).coerceAtLeast(1)
    return when {
        day in 0 until s.period -> Status(DayType.PERIOD,true)
        day==ovu -> Status(DayType.OVULATION,true)
        day in (ovu-5)..(ovu+1) -> Status(DayType.FERTILE,true)
        else -> Status(DayType.LOW,true)
    }
}

private fun cycleDay(anchor:LocalDate?, today:LocalDate):Int? =
    anchor?.let { ChronoUnit.DAYS.between(it,today).toInt()+1 }

private fun relativeLabel(date:LocalDate?, today:LocalDate, period:Boolean=false):String {
    if(date==null)return "À calculer"
    val diff=ChronoUnit.DAYS.between(today,date).toInt()
    return when {
        diff>1 -> "Dans $diff jours"
        diff==1 -> "Demain"
        diff==0 -> "Aujourd’hui"
        diff==-1 -> if(period) "Retard estimé : 1 jour" else "Hier"
        period -> "Retard estimé : ${-diff} jours"
        else -> "Il y a ${-diff} jours"
    }
}

private fun shortDate(date:LocalDate?):String =
    date?.format(DateTimeFormatter.ofPattern("d MMMM",Fr)) ?: "—"

private fun dateRange(start:LocalDate?, end:LocalDate?):String {
    if(start==null || end==null)return "À calculer"
    val sameMonth=start.month==end.month
    return if(sameMonth) {
        "${start.dayOfMonth} → ${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.FULL,Fr)}"
    } else {
        "${shortDate(start)} → ${shortDate(end)}"
    }
}

@Composable private fun App(store:Store){
    var settings by remember{mutableStateOf(store.settings())}
    var entries by remember{mutableStateOf(store.entries())}
    var unlocked by remember{mutableStateOf(!settings.lock)}
    if(settings.lock&&!unlocked){
        LockScreen{if(store.verify(it)){unlocked=true;true}else false}
        return
    }

    var tab by remember{mutableIntStateOf(0)}
    var edit by remember{mutableStateOf<LocalDate?>(null)}

    Scaffold(
        containerColor=Bg,
        bottomBar={
            NavigationBar(containerColor=Color.White, tonalElevation=8.dp){
                NavigationBarItem(
                    selected=tab==0,
                    onClick={tab=0},
                    icon={Icon(Icons.Default.Home,null)},
                    label={Text("Accueil")}
                )
                NavigationBarItem(
                    selected=tab==1,
                    onClick={tab=1},
                    icon={Icon(Icons.Default.CalendarMonth,null)},
                    label={Text("Calendrier")}
                )
                NavigationBarItem(
                    selected=tab==2,
                    onClick={tab=2},
                    icon={Icon(Icons.Default.Settings,null)},
                    label={Text("Réglages")}
                )
            }
        }
    ){pad->
        Box(Modifier.fillMaxSize().padding(pad)){
            when(tab){
                0->HomeScreen(settings,entries,{edit=it},{tab=1})
                1->CalendarScreen(settings,entries){edit=it}
                else->SettingsScreen(
                    settings,
                    onCycle={store.setCycle(it);settings=store.settings()},
                    onPeriod={store.setPeriod(it);settings=store.settings()},
                    onAuto={store.setAuto(it);settings=store.settings()},
                    onEnable={store.enablePassword(it);settings=store.settings()},
                    onDisable={store.disablePassword();settings=store.settings()}
                )
            }
        }
    }

    edit?.let{d->
        DayEditor(
            d=d,
            manual=entries[d],
            effective=statusFor(d,entries,settings),
            auto=settings.auto,
            dismiss={edit=null}
        ){t->
            store.setDay(d,t)
            entries=store.entries()
            edit=null
        }
    }
}

@Composable private fun HomeScreen(
    s:SettingsData,
    e:Map<LocalDate,DayType>,
    edit:(LocalDate)->Unit,
    openCalendar:()->Unit
){
    val today=LocalDate.now()
    val anchor=lastPeriodStart(e,today)
    val currentStatus=statusFor(today,e,s)
    val day=cycleDay(anchor,today)
    val estimatedPeriod=if(anchor!=null && s.auto) anchor.plusDays(s.cycle.toLong()) else null
    val estimatedOvulation=if(anchor!=null && s.auto) anchor.plusDays((s.cycle-14).toLong()) else null
    val fertileStart=estimatedOvulation?.minusDays(5)
    val fertileEnd=estimatedOvulation?.plusDays(1)

    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding=PaddingValues(horizontal=16.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        item{BrandHeader()}
        item{
            HeroCycleCard(
                day=day,
                status=currentStatus,
                onClick={edit(today)}
            )
        }
        item{
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(12.dp)
            ){
                InfoCard(
                    modifier=Modifier.weight(1f),
                    iconColor=statusColor(DayType.PERIOD),
                    iconBg=statusColor(DayType.PERIOD).copy(alpha=.16f),
                    title="Prochaines règles",
                    primary=relativeLabel(estimatedPeriod,today,period=true),
                    secondary=shortDate(estimatedPeriod),
                    accent=PrimaryDark
                )
                InfoCard(
                    modifier=Modifier.weight(1f),
                    iconColor=statusColor(DayType.OVULATION),
                    iconBg=statusColor(DayType.OVULATION).copy(alpha=.16f),
                    title="Ovulation estimée",
                    primary=relativeLabel(estimatedOvulation,today),
                    secondary=shortDate(estimatedOvulation),
                    accent=Color(0xFF42258A)
                )
            }
        }
        item{
            FertileWindowCard(
                range=dateRange(fertileStart,fertileEnd),
                enabled=s.auto && anchor!=null,
                onClick=openCalendar
            )
        }
        item{
            WeekPreview(
                s=s,
                e=e,
                today=today,
                onDay=edit,
                openCalendar=openCalendar
            )
        }
        item{
            Button(
                onClick={edit(today)},
                modifier=Modifier.fillMaxWidth().height(58.dp),
                shape=RoundedCornerShape(22.dp),
                colors=ButtonDefaults.buttonColors(containerColor=Primary)
            ){
                Icon(Icons.Default.Edit,null)
                Spacer(Modifier.width(10.dp))
                Text("Noter aujourd’hui",fontSize=18.sp,fontWeight=FontWeight.Bold)
            }
        }
        item{
            Text(
                "Les estimations de fertilité sont indicatives et ne constituent pas une méthode contraceptive.",
                style=MaterialTheme.typography.bodySmall,
                color=Muted,
                modifier=Modifier.padding(horizontal=6.dp)
            )
        }
    }
}

@Composable private fun BrandHeader(){
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment=Alignment.CenterVertically
    ){
        Image(
            painter=painterResource(R.drawable.mon_cycle_icon),
            contentDescription="Logo Mon Cycle",
            modifier=Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(17.dp))
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)){
            Text("Mon Cycle",fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=TextDark)
            Text(
                "Mieux me connaître, chaque jour",
                fontSize=13.sp,
                color=Muted
            )
        }
    }
}

@Composable private fun HeroCycleCard(
    day:Int?,
    status:Status?,
    onClick:()->Unit
){
    Box(
        Modifier
            .fillMaxWidth()
            .height(216.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFFF7789),Color(0xFFF33166))
                )
            )
            .clickable(onClick=onClick)
            .padding(22.dp)
    ){
        Column(
            Modifier.align(Alignment.CenterStart).fillMaxWidth(0.63f),
            verticalArrangement=Arrangement.Center
        ){
            Text("Aujourd’hui",color=Color.White.copy(alpha=.92f),fontSize=20.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            if(day!=null){
                Text("Jour $day",color=Color.White,fontSize=48.sp,fontWeight=FontWeight.ExtraBold)
                Text("du cycle",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)
            }else{
                Text("À démarrer",color=Color.White,fontSize=38.sp,fontWeight=FontWeight.ExtraBold)
                Text("Notez le début de vos règles",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Medium)
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                color=Color.White.copy(alpha=.92f),
                contentColor=PrimaryDark,
                shape=RoundedCornerShape(50)
            ){
                Row(
                    Modifier.padding(horizontal=13.dp,vertical=8.dp),
                    verticalAlignment=Alignment.CenterVertically
                ){
                    Box(
                        Modifier.size(9.dp).background(statusColor(status?.type),CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if(day==null)"Touchez pour commencer"
                        else "${label(status?.type)}${if(status?.predicted==true)" estimée" else ""}",
                        fontWeight=FontWeight.Bold,
                        fontSize=13.sp,
                        maxLines=1
                    )
                }
            }
        }

        Surface(
            modifier=Modifier
                .align(Alignment.CenterEnd)
                .size(112.dp),
            color=Color.White.copy(alpha=.18f),
            shape=RoundedCornerShape(28.dp)
        ){
            Image(
                painter=painterResource(R.drawable.mon_cycle_icon),
                contentDescription=null,
                modifier=Modifier.padding(10.dp).clip(RoundedCornerShape(22.dp))
            )
        }
    }
}

@Composable private fun InfoCard(
    modifier:Modifier,
    iconColor:Color,
    iconBg:Color,
    title:String,
    primary:String,
    secondary:String,
    accent:Color
){
    Card(
        modifier=modifier,
        shape=RoundedCornerShape(24.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        elevation=CardDefaults.cardElevation(defaultElevation=2.dp)
    ){
        Column(
            Modifier.padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(7.dp)
        ){
            Box(
                Modifier.size(40.dp).background(iconBg,CircleShape),
                contentAlignment=Alignment.Center
            ){
                Icon(Icons.Default.WaterDrop,null,tint=iconColor,modifier=Modifier.size(22.dp))
            }
            Text(title,color=accent,fontSize=14.sp,fontWeight=FontWeight.Bold)
            Text(primary,color=TextDark,fontSize=21.sp,fontWeight=FontWeight.ExtraBold,maxLines=2)
            Text(secondary,color=accent.copy(alpha=.8f),fontSize=14.sp)
        }
    }
}

@Composable private fun FertileWindowCard(
    range:String,
    enabled:Boolean,
    onClick:()->Unit
){
    val marker=statusColor(DayType.FERTILE)
    Card(
        Modifier.fillMaxWidth().clickable(onClick=onClick),
        shape=RoundedCornerShape(24.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        elevation=CardDefaults.cardElevation(defaultElevation=2.dp)
    ){
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            Box(
                Modifier.size(48.dp).background(marker.copy(alpha=.16f),CircleShape),
                contentAlignment=Alignment.Center
            ){
                Icon(Icons.Default.CalendarMonth,null,tint=marker)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)){
                Text("Fenêtre fertile",color=PrimaryDark,fontWeight=FontWeight.Bold)
                Text(
                    if(enabled)range else "Calcul automatique désactivé",
                    color=TextDark,
                    fontSize=21.sp,
                    fontWeight=FontWeight.ExtraBold
                )
            }
            Text("›",fontSize=30.sp,color=Primary)
        }
    }
}

@Composable private fun WeekPreview(
    s:SettingsData,
    e:Map<LocalDate,DayType>,
    today:LocalDate,
    onDay:(LocalDate)->Unit,
    openCalendar:()->Unit
){
    val monday=today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    val days=(0..6).map{monday.plusDays(it.toLong())}

    Card(
        Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(26.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        elevation=CardDefaults.cardElevation(defaultElevation=2.dp)
    ){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(
                Modifier.fillMaxWidth().clickable(onClick=openCalendar),
                horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically
            ){
                Text("Mon cycle cette semaine",fontWeight=FontWeight.ExtraBold,color=TextDark)
                Text(
                    today.month.getDisplayName(TextStyle.FULL,Fr).replaceFirstChar{it.uppercase()},
                    color=Muted
                )
            }
            Row(Modifier.fillMaxWidth()){
                days.forEach{d->
                    val st=statusFor(d,e,s)
                    WeekDay(
                        modifier=Modifier.weight(1f),
                        d=d,
                        status=st,
                        isToday=d==today,
                        onClick={onDay(d)}
                    )
                }
            }
        }
    }
}

@Composable private fun WeekDay(
    modifier:Modifier,
    d:LocalDate,
    status:Status?,
    isToday:Boolean,
    onClick:()->Unit
){
    val c=statusColor(status?.type)
    val bg=when(status?.type){
        DayType.FERTILE->Fertile
        DayType.OVULATION->Ovulation
        DayType.PERIOD->Period
        DayType.LOW->Low
        else->Color(0xFFF3F1F5)
    }
    val fg=readableTextColor(bg)

    Column(
        modifier.clickable(onClick=onClick),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.spacedBy(6.dp)
    ){
        Text(
            d.dayOfWeek.getDisplayName(TextStyle.SHORT,Fr).take(3).replaceFirstChar{it.uppercase()},
            color=Muted,
            fontSize=11.sp
        )
        Box(
            Modifier
                .size(38.dp)
                .then(if(isToday)Modifier.border(2.dp,Primary,CircleShape)else Modifier)
                .background(bg,CircleShape),
            contentAlignment=Alignment.Center
        ){
            Text("${d.dayOfMonth}",color=fg,fontWeight=FontWeight.Bold)
        }
        Box(Modifier.size(6.dp).background(c,CircleShape))
    }
}

@Composable private fun CalendarScreen(
    s:SettingsData,
    e:Map<LocalDate,DayType>,
    edit:(LocalDate)->Unit
){
    var month by remember{mutableStateOf(YearMonth.now())}

    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Image(
                    painter=painterResource(R.drawable.mon_cycle_icon),
                    contentDescription=null,
                    modifier=Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text("Calendrier",fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=TextDark)
            }
        }
        item{
            Card(
                shape=RoundedCornerShape(26.dp),
                colors=CardDefaults.cardColors(containerColor=Color.White),
                elevation=CardDefaults.cardElevation(defaultElevation=2.dp)
            ){
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween
                    ){
                        Text(
                            "‹",
                            fontSize=36.sp,
                            color=Primary,
                            modifier=Modifier.clickable{month=month.minusMonths(1)}
                        )
                        Text(
                            "${month.month.getDisplayName(TextStyle.FULL,Fr).replaceFirstChar{it.uppercase()}} ${month.year}",
                            fontSize=21.sp,
                            fontWeight=FontWeight.ExtraBold,
                            color=TextDark
                        )
                        Text(
                            "›",
                            fontSize=36.sp,
                            color=Primary,
                            modifier=Modifier.clickable{month=month.plusMonths(1)}
                        )
                    }
                    MonthGrid(month,s,e,edit)
                }
            }
        }
        item{Legend()}
        item{
            Text(
                "Touchez un jour pour remplacer l’estimation par un statut manuel. « Revenir au calcul automatique » supprime la correction.",
                style=MaterialTheme.typography.bodySmall,
                color=Muted
            )
        }
    }
}

@Composable private fun MonthGrid(
    m:YearMonth,
    s:SettingsData,
    e:Map<LocalDate,DayType>,
    edit:(LocalDate)->Unit
){
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(Modifier.fillMaxWidth()){
            listOf("L","M","M","J","V","S","D").forEach{
                Text(
                    it,
                    Modifier.weight(1f),
                    textAlign=TextAlign.Center,
                    fontWeight=FontWeight.SemiBold,
                    color=Muted
                )
            }
        }
        val first=m.atDay(1)
        val offset=first.dayOfWeek.value-1
        val cells=offset+m.lengthOfMonth()
        val rows=(cells+6)/7
        repeat(rows){r->
            Row(Modifier.fillMaxWidth()){
                repeat(7){c->
                    val n=r*7+c-offset+1
                    if(n in 1..m.lengthOfMonth()){
                        val d=m.atDay(n)
                        val st=statusFor(d,e,s)
                        val bg=statusColor(st?.type)
                        val isToday=d==LocalDate.now()
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(3.dp)
                                .then(if(isToday)Modifier.border(2.dp,Primary,CircleShape) else Modifier)
                                .background(
                                    if(st?.type==null||st.type==DayType.NONE)Color(0xFFF7F5F8) else bg,
                                    CircleShape
                                )
                                .clickable{edit(d)},
                            contentAlignment=Alignment.Center
                        ){
                            Text(
                                "$n",
                                color=readableTextColor(if(st?.type==null||st.type==DayType.NONE)Color(0xFFF7F5F8) else bg),
                                fontWeight=if(isToday)FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }else{
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable private fun Legend(){
    Card(
        colors=CardDefaults.cardColors(containerColor=Color.White),
        shape=RoundedCornerShape(22.dp),
        elevation=CardDefaults.cardElevation(defaultElevation=1.dp)
    ){
        Column(
            Modifier.padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)
        ){
            Text("Repères",fontWeight=FontWeight.ExtraBold,color=TextDark)
            LegendRow(Period,"Règles")
            LegendRow(Fertile,"Fenêtre fertile")
            LegendRow(Ovulation,"Ovulation")
            LegendRow(Low,"Fertilité estimée faible")
        }
    }
}

@Composable private fun LegendRow(c:Color,t:String){
    Row(verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(16.dp).background(c,CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(t,color=TextDark)
    }
}

@Composable private fun DayEditor(
    d:LocalDate,
    manual:DayType?,
    effective:Status?,
    auto:Boolean,
    dismiss:()->Unit,
    select:(DayType?)->Unit
){
    AlertDialog(
        onDismissRequest=dismiss,
        shape=RoundedCornerShape(28.dp),
        title={
            Column{
                Text(
                    d.format(DateTimeFormatter.ofPattern("EEEE d MMMM",Fr))
                        .replaceFirstChar{it.uppercase()},
                    fontWeight=FontWeight.ExtraBold
                )
                Text(
                    "Statut : ${label(effective?.type)}${if(effective?.predicted==true)" • estimé" else ""}",
                    style=MaterialTheme.typography.bodyMedium,
                    color=Muted
                )
            }
        },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
                StatusChoice(DayType.PERIOD,select)
                StatusChoice(DayType.FERTILE,select)
                StatusChoice(DayType.OVULATION,select)
                StatusChoice(DayType.LOW,select)
                StatusChoice(DayType.NONE,select)
                if(manual!=null&&auto){
                    TextButton(
                        onClick={select(null)},
                        modifier=Modifier.fillMaxWidth()
                    ){
                        Text("Revenir au calcul automatique")
                    }
                }
            }
        },
        confirmButton={},
        dismissButton={TextButton(onClick=dismiss){Text("Fermer")}}
    )
}

@Composable private fun StatusChoice(t:DayType,select:(DayType?)->Unit){
    OutlinedButton(
        onClick={select(t)},
        modifier=Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(16.dp)
    ){
        Box(Modifier.size(12.dp).background(statusColor(t),CircleShape))
        Spacer(Modifier.width(9.dp))
        Text(label(t),Modifier.weight(1f),textAlign=TextAlign.Start)
    }
}

@Composable private fun SettingsScreen(
    s:SettingsData,
    onCycle:(Int)->Unit,
    onPeriod:(Int)->Unit,
    onAuto:(Boolean)->Unit,
    onEnable:(String)->Unit,
    onDisable:()->Unit
){
    var dialog by remember{mutableStateOf(false)}
    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Image(
                    painter=painterResource(R.drawable.mon_cycle_icon),
                    contentDescription=null,
                    modifier=Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text("Réglages",fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=TextDark)
            }
        }
        item{
            ModernSettingsCard{
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("Calcul automatique",fontWeight=FontWeight.ExtraBold,color=TextDark)
                        Text(
                            "Activé par défaut. Les corrections manuelles gardent toujours la priorité.",
                            style=MaterialTheme.typography.bodySmall,
                            color=Muted
                        )
                    }
                    Switch(checked=s.auto,onCheckedChange=onAuto)
                }
            }
        }
        item{
            ModernSettingsCard{
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("Mon cycle",fontWeight=FontWeight.ExtraBold,color=TextDark)
                    Stepper("Durée moyenne du cycle",s.cycle,20..45,onCycle)
                    HorizontalDivider(color=Color(0xFFF1EDF2))
                    Stepper("Durée moyenne des règles",s.period,2..10,onPeriod)
                }
            }
        }
        item{MarkerColorSettings()}
        item{
            ModernSettingsCard{
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("Confidentialité",fontWeight=FontWeight.ExtraBold,color=TextDark)
                    Text(
                        "Les données sont conservées localement sur ce téléphone.",
                        style=MaterialTheme.typography.bodySmall,
                        color=Muted
                    )
                    if(s.lock){
                        OutlinedButton(onClick=onDisable,shape=RoundedCornerShape(16.dp)){
                            Text("Désactiver le mot de passe")
                        }
                    }else{
                        Button(
                            onClick={dialog=true},
                            shape=RoundedCornerShape(16.dp)
                        ){
                            Text("Activer un mot de passe")
                        }
                    }
                }
            }
        }
    }
    if(dialog)PasswordDialog({dialog=false}){onEnable(it);dialog=false}
}

@Composable private fun ModernSettingsCard(content:@Composable ColumnScope.()->Unit){
    Card(
        shape=RoundedCornerShape(24.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        elevation=CardDefaults.cardElevation(defaultElevation=2.dp)
    ){
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp),
            content=content
        )
    }
}

@Composable private fun Stepper(label:String,v:Int,range:IntRange,set:(Int)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){
            Text(label,color=TextDark)
            Text("$v jours",fontWeight=FontWeight.ExtraBold,color=PrimaryDark)
        }
        OutlinedButton(
            onClick={if(v>range.first)set(v-1)},
            enabled=v>range.first
        ){Text("−")}
        Spacer(Modifier.width(6.dp))
        OutlinedButton(
            onClick={if(v<range.last)set(v+1)},
            enabled=v<range.last
        ){Text("+")}
    }
}

@Composable private fun PasswordDialog(dismiss:()->Unit,create:(String)->Unit){
    var a by remember{mutableStateOf("")}
    var b by remember{mutableStateOf("")}
    AlertDialog(
        onDismissRequest=dismiss,
        shape=RoundedCornerShape(28.dp),
        title={Text("Créer un mot de passe",fontWeight=FontWeight.ExtraBold)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(
                    value=a,
                    onValueChange={a=it},
                    label={Text("Mot de passe")},
                    visualTransformation=PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value=b,
                    onValueChange={b=it},
                    label={Text("Confirmer")},
                    visualTransformation=PasswordVisualTransformation()
                )
                Text("4 caractères minimum.",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
        },
        confirmButton={
            Button(onClick={create(a)},enabled=a.length>=4&&a==b){Text("Activer")}
        },
        dismissButton={TextButton(onClick=dismiss){Text("Annuler")}}
    )
}

@Composable private fun LockScreen(unlock:(String)->Boolean){
    var p by remember{mutableStateOf("")}
    var err by remember{mutableStateOf(false)}
    Box(
        Modifier.fillMaxSize().background(Bg).padding(24.dp),
        contentAlignment=Alignment.Center
    ){
        Card(
            shape=RoundedCornerShape(28.dp),
            colors=CardDefaults.cardColors(containerColor=Color.White)
        ){
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(12.dp)
            ){
                Image(
                    painter=painterResource(R.drawable.mon_cycle_icon),
                    contentDescription=null,
                    modifier=Modifier.size(78.dp).clip(RoundedCornerShape(22.dp))
                )
                Text("Mon Cycle",fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=TextDark)
                OutlinedTextField(
                    value=p,
                    onValueChange={p=it;err=false},
                    label={Text("Mot de passe")},
                    visualTransformation=PasswordVisualTransformation(),
                    isError=err
                )
                if(err)Text("Mot de passe incorrect",color=MaterialTheme.colorScheme.error)
                Button(
                    onClick={err=!unlock(p)},
                    enabled=p.isNotBlank(),
                    modifier=Modifier.fillMaxWidth(),
                    shape=RoundedCornerShape(16.dp)
                ){Text("Ouvrir")}
            }
        }
    }
}

private fun statusColor(t:DayType?)=MarkerColorState.color(t)

private fun label(t:DayType?)=when(t){
    DayType.PERIOD->"Règles"
    DayType.FERTILE->"Fenêtre fertile"
    DayType.OVULATION->"Ovulation"
    DayType.LOW->"Fertilité faible"
    DayType.NONE->"Aucun statut"
    null->"Aucun statut"
}
