package fr.moncycle.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Period = Color(0xFFFFC7CC)
private val Fertile = Color(0xFFFF1744)
private val Ovulation = Color(0xFF7657D5)
private val Low = Color(0xFFCDECCF)
private val Bg = Color(0xFFFFF9FA)
private val Primary = Color(0xFFA93452)
private val Fr = Locale.FRENCH

enum class DayType { PERIOD, FERTILE, OVULATION, LOW, NONE }
data class SettingsData(val cycle:Int=28,val period:Int=5,val auto:Boolean=true,val lock:Boolean=false)
data class Status(val type:DayType,val predicted:Boolean)

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary=Primary, background=Bg)) {
                App(Store(this))
            }
        }
    }
}

class Store(context: Context) {
    private val p=context.getSharedPreferences("mon_cycle",Context.MODE_PRIVATE)
    fun settings()=SettingsData(p.getInt("cycle",28),p.getInt("period",5),p.getBoolean("auto",true),p.getBoolean("lock",false))
    fun setCycle(v:Int)=p.edit().putInt("cycle",v.coerceIn(20,45)).apply()
    fun setPeriod(v:Int)=p.edit().putInt("period",v.coerceIn(2,10)).apply()
    fun setAuto(v:Boolean)=p.edit().putBoolean("auto",v).apply()
    fun entries():Map<LocalDate,DayType> = p.getStringSet("days", emptySet()).orEmpty().mapNotNull { s ->
        val a=s.split('|'); if(a.size!=2) null else runCatching { LocalDate.parse(a[0]) to DayType.valueOf(a[1]) }.getOrNull()
    }.toMap()
    fun setDay(d:LocalDate,t:DayType?) { val m=entries().toMutableMap(); if(t==null)m.remove(d) else m[d]=t; p.edit().putStringSet("days",m.map{"${it.key}|${it.value}"}.toSet()).apply() }
    fun enablePassword(s:String){ p.edit().putString("pw",sha(s)).putBoolean("lock",true).apply() }
    fun disablePassword(){ p.edit().remove("pw").putBoolean("lock",false).apply() }
    fun verify(s:String)=!p.getBoolean("lock",false)||p.getString("pw","")==sha(s)
    private fun sha(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
}

private fun lastPeriodStart(entries:Map<LocalDate,DayType>, limit:LocalDate):LocalDate? = entries.filter{(d,t)->t==DayType.PERIOD&&!d.isAfter(limit)}.keys.filter{entries[it.minusDays(1)]!=DayType.PERIOD}.maxOrNull()

private fun statusFor(date:LocalDate, entries:Map<LocalDate,DayType>, s:SettingsData):Status? {
    entries[date]?.let{return Status(it,false)}
    if(!s.auto)return null
    var a=lastPeriodStart(entries,date)?:return null
    while(!a.plusDays(s.cycle.toLong()).isAfter(date)) a=a.plusDays(s.cycle.toLong())
    val day=java.time.temporal.ChronoUnit.DAYS.between(a,date).toInt()
    val ovu=(s.cycle-14).coerceAtLeast(1)
    return when {
        day in 0 until s.period -> Status(DayType.PERIOD,true)
        day==ovu -> Status(DayType.OVULATION,true)
        day in (ovu-5)..(ovu+1) -> Status(DayType.FERTILE,true)
        else -> Status(DayType.LOW,true)
    }
}

@Composable private fun App(store:Store){
    var settings by remember{mutableStateOf(store.settings())}
    var entries by remember{mutableStateOf(store.entries())}
    var unlocked by remember{mutableStateOf(!settings.lock)}
    if(settings.lock&&!unlocked){ LockScreen{if(store.verify(it)){unlocked=true;true}else false};return }
    var tab by remember{mutableIntStateOf(0)}
    var edit by remember{mutableStateOf<LocalDate?>(null)}
    Scaffold(containerColor=Bg,bottomBar={NavigationBar{NavigationBarItem(tab==0,{tab=0},{Icon(Icons.Default.Home,null)},{Text("Aujourd’hui")});NavigationBarItem(tab==1,{tab=1},{Icon(Icons.Default.CalendarMonth,null)},{Text("Calendrier")});NavigationBarItem(tab==2,{tab=2},{Icon(Icons.Default.Settings,null)},{Text("Réglages")})}}){pad->
        Box(Modifier.fillMaxSize().padding(pad)){when(tab){0->Today(settings,entries,{edit=it},{tab=1});1->Calendar(settings,entries){edit=it};else->Prefs(settings,
            onCycle={store.setCycle(it);settings=store.settings()},onPeriod={store.setPeriod(it);settings=store.settings()},onAuto={store.setAuto(it);settings=store.settings()},
            onEnable={store.enablePassword(it);settings=store.settings()},onDisable={store.disablePassword();settings=store.settings()})}}
    }
    edit?.let{d->DayEditor(d,entries[d],statusFor(d,entries,settings),settings.auto,{edit=null}){t->store.setDay(d,t);entries=store.entries();edit=null}}
}

@Composable private fun Today(s:SettingsData,e:Map<LocalDate,DayType>,edit:(LocalDate)->Unit,calendar:()->Unit){
    val today=LocalDate.now(); val anchor=lastPeriodStart(e,today); val st=statusFor(today,e,s)
    LazyColumn(Modifier.fillMaxSize().background(Bg),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Text("Mon Cycle",fontSize=30.sp,fontWeight=FontWeight.Bold);Text(today.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Fr)).replaceFirstChar{it.uppercase()})}
        item{Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(if(anchor==null)"Commencez par noter vos règles" else "Jour ${java.time.temporal.ChronoUnit.DAYS.between(anchor,today)+1} du cycle",fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text("Aujourd’hui : ${label(st?.type)}${if(st?.predicted==true)" • estimation" else ""}")
            if(anchor!=null&&s.auto){val next=anchor.plusDays(s.cycle.toLong());val ovu=anchor.plusDays((s.cycle-14).toLong());Text("Prochaines règles estimées : ${next.format(DateTimeFormatter.ofPattern("d MMM",Fr))}");Text("Ovulation estimée : ${ovu.format(DateTimeFormatter.ofPattern("d MMM",Fr))}")}
        }}}
        item{Button({edit(today)},Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)){Text("Noter ou corriger aujourd’hui",Modifier.padding(vertical=5.dp))}}
        item{OutlinedButton(calendar,Modifier.fillMaxWidth()){Text("Ouvrir le calendrier")}}
        item{Legend()}
        item{Text("Les estimations de fertilité sont indicatives et ne constituent pas une méthode contraceptive.",style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable private fun Calendar(s:SettingsData,e:Map<LocalDate,DayType>,edit:(LocalDate)->Unit){
    var month by remember{mutableStateOf(YearMonth.now())}
    LazyColumn(Modifier.fillMaxSize().background(Bg),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("‹",fontSize=36.sp,modifier=Modifier.clickable{month=month.minusMonths(1)});Text("${month.month.getDisplayName(TextStyle.FULL,Fr).replaceFirstChar{it.uppercase()}} ${month.year}",fontSize=22.sp,fontWeight=FontWeight.Bold);Text("›",fontSize=36.sp,modifier=Modifier.clickable{month=month.plusMonths(1)})}}
        item{MonthGrid(month,s,e,edit)}
        item{Legend()}
        item{Text("Touchez un jour pour remplacer l’estimation par un statut manuel. « Revenir au calcul automatique » supprime la correction.",style=MaterialTheme.typography.bodySmall)}
    }
}

@Composable private fun MonthGrid(m:YearMonth,s:SettingsData,e:Map<LocalDate,DayType>,edit:(LocalDate)->Unit){
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Row(Modifier.fillMaxWidth()){listOf("L","M","M","J","V","S","D").forEach{Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontWeight=FontWeight.SemiBold)}}
        val first=m.atDay(1); val offset=first.dayOfWeek.value-1; val cells=offset+m.lengthOfMonth(); val rows=(cells+6)/7
        repeat(rows){r->Row(Modifier.fillMaxWidth()){repeat(7){c->val n=r*7+c-offset+1;if(n in 1..m.lengthOfMonth()){val d=m.atDay(n);val st=statusFor(d,e,s);Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).background(color(st?.type),CircleShape).clickable{edit(d)},contentAlignment=Alignment.Center){Text("$n",fontWeight=if(d==LocalDate.now())FontWeight.Bold else FontWeight.Normal)}}else Spacer(Modifier.weight(1f).aspectRatio(1f))}}}
    }
}

@Composable private fun Legend(){Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text("Repères",fontWeight=FontWeight.Bold);LegendRow(Period,"Règles");LegendRow(Fertile,"Fenêtre fertile");LegendRow(Ovulation,"Ovulation");LegendRow(Low,"Fertilité estimée faible")}}}
@Composable private fun LegendRow(c:Color,t:String){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(16.dp).background(c,CircleShape));Spacer(Modifier.width(8.dp));Text(t)}}

@Composable private fun DayEditor(d:LocalDate,manual:DayType?,effective:Status?,auto:Boolean,dismiss:()->Unit,select:(DayType?)->Unit){AlertDialog(onDismissRequest=dismiss,title={Text(d.format(DateTimeFormatter.ofPattern("d MMMM yyyy",Fr)))},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("Statut actuel : ${label(effective?.type)}${if(effective?.predicted==true)" (estimé)" else ""}");listOf(DayType.PERIOD,DayType.FERTILE,DayType.OVULATION,DayType.LOW,DayType.NONE).forEach{t->OutlinedButton({select(t)},Modifier.fillMaxWidth()){Text(label(t))}};if(manual!=null&&auto)TextButton({select(null)},Modifier.fillMaxWidth()){Text("Revenir au calcul automatique")}}},confirmButton={},dismissButton={TextButton(dismiss){Text("Fermer")}})}

@Composable private fun Prefs(s:SettingsData,onCycle:(Int)->Unit,onPeriod:(Int)->Unit,onAuto:(Boolean)->Unit,onEnable:(String)->Unit,onDisable:()->Unit){var dialog by remember{mutableStateOf(false)};LazyColumn(Modifier.fillMaxSize().background(Bg),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{Text("Réglages",fontSize=30.sp,fontWeight=FontWeight.Bold)};item{Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Calcul automatique",fontWeight=FontWeight.Bold);Text("Activé par défaut",style=MaterialTheme.typography.bodySmall)};Switch(s.auto,onAuto)}}};item{Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Stepper("Durée moyenne du cycle",s.cycle,20..45,onCycle);Stepper("Durée moyenne des règles",s.period,2..10,onPeriod)}}};item{Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Protection par mot de passe",fontWeight=FontWeight.Bold);if(s.lock)OutlinedButton(onDisable){Text("Désactiver") } else Button({dialog=true}){Text("Activer un mot de passe")}}}}};if(dialog)PasswordDialog({dialog=false}){onEnable(it);dialog=false}}
@Composable private fun Stepper(label:String,v:Int,range:IntRange,set:(Int)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(label);Text("$v jours",fontWeight=FontWeight.Bold)};OutlinedButton({if(v>range.first)set(v-1)}){Text("−")};Spacer(Modifier.width(6.dp));OutlinedButton({if(v<range.last)set(v+1)}){Text("+")}}}
@Composable private fun PasswordDialog(dismiss:()->Unit,create:(String)->Unit){var a by remember{mutableStateOf("")};var b by remember{mutableStateOf("")};AlertDialog(onDismissRequest=dismiss,title={Text("Créer un mot de passe")},text={Column{OutlinedTextField(a,{a=it},label={Text("Mot de passe")},visualTransformation=PasswordVisualTransformation());OutlinedTextField(b,{b=it},label={Text("Confirmer")},visualTransformation=PasswordVisualTransformation())}},confirmButton={Button({create(a)},enabled=a.length>=4&&a==b){Text("Activer")}},dismissButton={TextButton(dismiss){Text("Annuler")}})}
@Composable private fun LockScreen(unlock:(String)->Boolean){var p by remember{mutableStateOf("")};var err by remember{mutableStateOf(false)};Box(Modifier.fillMaxSize().background(Bg).padding(24.dp),contentAlignment=Alignment.Center){Card{Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){Text("Mon Cycle",fontSize=28.sp,fontWeight=FontWeight.Bold);OutlinedTextField(p,{p=it;err=false},label={Text("Mot de passe")},visualTransformation=PasswordVisualTransformation(),isError=err);if(err)Text("Mot de passe incorrect",color=MaterialTheme.colorScheme.error);Button({err=!unlock(p)},enabled=p.isNotBlank()){Text("Ouvrir")}}}}}
private fun color(t:DayType?)=when(t){DayType.PERIOD->Period;DayType.FERTILE->Fertile;DayType.OVULATION->Ovulation;DayType.LOW->Low;else->Color.Transparent}
private fun label(t:DayType?)=when(t){DayType.PERIOD->"Règles";DayType.FERTILE->"Fenêtre fertile";DayType.OVULATION->"Ovulation";DayType.LOW->"Fertilité estimée faible";DayType.NONE->"Aucun statut";null->"Aucun statut"}
