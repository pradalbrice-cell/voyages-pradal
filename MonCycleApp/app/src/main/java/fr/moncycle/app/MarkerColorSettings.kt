package fr.moncycle.app

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val DEFAULT_PERIOD = -14388        // #FFFFC7CC
private const val DEFAULT_FERTILE = -59648       // #FFFF1744
private const val DEFAULT_OVULATION = -9021483   // #FF7657D5
private const val DEFAULT_LOW = -3281713         // #FFCDECCF

private val SettingsText = Color(0xFF21172D)
private val SettingsMuted = Color(0xFF786D80)
private val SettingsPink = Color(0xFFF43B69)

data class MarkerColors(
    val period:Int = DEFAULT_PERIOD,
    val fertile:Int = DEFAULT_FERTILE,
    val ovulation:Int = DEFAULT_OVULATION,
    val low:Int = DEFAULT_LOW
)

object MarkerColorState {
    val colors = mutableStateOf(MarkerColors())

    fun load(context:Context){
        val p=context.getSharedPreferences("mon_cycle",Context.MODE_PRIVATE)
        colors.value=MarkerColors(
            period=p.getInt("color_period",DEFAULT_PERIOD),
            fertile=p.getInt("color_fertile",DEFAULT_FERTILE),
            ovulation=p.getInt("color_ovulation",DEFAULT_OVULATION),
            low=p.getInt("color_low",DEFAULT_LOW)
        )
    }

    fun color(type:DayType?):Color = Color(
        when(type){
            DayType.PERIOD->colors.value.period
            DayType.FERTILE->colors.value.fertile
            DayType.OVULATION->colors.value.ovulation
            DayType.LOW->colors.value.low
            else->0xFFD8D1DA.toInt()
        }
    )

    fun update(context:Context,type:DayType,color:Color){
        val key=when(type){
            DayType.PERIOD->"color_period"
            DayType.FERTILE->"color_fertile"
            DayType.OVULATION->"color_ovulation"
            DayType.LOW->"color_low"
            else->return
        }
        context.getSharedPreferences("mon_cycle",Context.MODE_PRIVATE)
            .edit().putInt(key,color.toArgb()).apply()
        load(context)
    }

    fun reset(context:Context){
        context.getSharedPreferences("mon_cycle",Context.MODE_PRIVATE).edit()
            .remove("color_period")
            .remove("color_fertile")
            .remove("color_ovulation")
            .remove("color_low")
            .apply()
        load(context)
    }
}

fun readableTextColor(background:Color):Color =
    if(background.luminance()>0.52f) Color(0xFF21172D) else Color.White

@Composable
fun MarkerColorSettings(){
    val context=LocalContext.current
    val colors=MarkerColorState.colors.value
    var editing by remember{mutableStateOf<DayType?>(null)}

    Card(
        shape=RoundedCornerShape(24.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),
        elevation=CardDefaults.cardElevation(defaultElevation=2.dp)
    ){
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(6.dp)
        ){
            Text("Couleurs des repères",fontWeight=FontWeight.ExtraBold,color=SettingsText)
            Text(
                "Choisissez une couleur différente pour chaque phase du cycle.",
                style=MaterialTheme.typography.bodySmall,
                color=SettingsMuted
            )
            Spacer(Modifier.height(5.dp))
            MarkerColorRow("Règles",Color(colors.period)){editing=DayType.PERIOD}
            HorizontalDivider(color=Color(0xFFF2EDF0))
            MarkerColorRow("Fenêtre fertile",Color(colors.fertile)){editing=DayType.FERTILE}
            HorizontalDivider(color=Color(0xFFF2EDF0))
            MarkerColorRow("Ovulation",Color(colors.ovulation)){editing=DayType.OVULATION}
            HorizontalDivider(color=Color(0xFFF2EDF0))
            MarkerColorRow("Fertilité estimée faible",Color(colors.low)){editing=DayType.LOW}
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick={MarkerColorState.reset(context)},
                modifier=Modifier.align(Alignment.End)
            ){
                Text("Revenir aux couleurs d’origine")
            }
        }
    }

    editing?.let{type->
        ColorPickerDialog(
            type=type,
            initial=MarkerColorState.color(type),
            dismiss={editing=null}
        ){chosen->
            MarkerColorState.update(context,type,chosen)
            editing=null
        }
    }
}

@Composable
private fun MarkerColorRow(label:String,color:Color,onClick:()->Unit){
    Row(
        Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=8.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        Box(
            Modifier.size(36.dp)
                .background(color,CircleShape)
                .border(1.dp,Color.Black.copy(alpha=.08f),CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(label,Modifier.weight(1f),color=SettingsText,fontWeight=FontWeight.SemiBold)
        Text("Modifier",color=SettingsPink,fontSize=13.sp,fontWeight=FontWeight.Bold)
    }
}

private data class Preset(val name:String,val color:Color)
private val presets=listOf(
    Preset("Rose",Color(0xFFFFC7CC)),
    Preset("Rouge",Color(0xFFFF1744)),
    Preset("Violet",Color(0xFF7657D5)),
    Preset("Vert",Color(0xFF63C174)),
    Preset("Bleu",Color(0xFF42A5F5)),
    Preset("Turquoise",Color(0xFF26A69A)),
    Preset("Orange",Color(0xFFFF9800)),
    Preset("Fuchsia",Color(0xFFEC407A))
)

@Composable
private fun ColorPickerDialog(
    type:DayType,
    initial:Color,
    dismiss:()->Unit,
    apply:(Color)->Unit
){
    val initialArgb=initial.toArgb()
    val baseHsv=remember(initialArgb){
        FloatArray(3).also{AndroidColor.colorToHSV(initialArgb,it)}
    }
    var hue by remember(initialArgb){mutableFloatStateOf(baseHsv[0])}
    var saturation by remember(initialArgb){mutableFloatStateOf(baseHsv[1])}
    var value by remember(initialArgb){mutableFloatStateOf(baseHsv[2])}

    fun useColor(c:Color){
        val hsv=FloatArray(3)
        AndroidColor.colorToHSV(c.toArgb(),hsv)
        hue=hsv[0]
        saturation=hsv[1]
        value=hsv[2]
    }

    val preview=Color(AndroidColor.HSVToColor(floatArrayOf(hue,saturation,value)))

    AlertDialog(
        onDismissRequest=dismiss,
        shape=RoundedCornerShape(28.dp),
        title={
            Column{
                Text("Couleur — ${markerLabel(type)}",fontWeight=FontWeight.ExtraBold)
                Text("Palette rapide ou couleur personnalisée HSV",fontSize=12.sp,color=SettingsMuted)
            }
        },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
                Box(
                    Modifier.fillMaxWidth().height(64.dp)
                        .background(preview,RoundedCornerShape(18.dp)),
                    contentAlignment=Alignment.Center
                ){
                    Text(
                        colorHex(preview),
                        color=readableTextColor(preview),
                        fontWeight=FontWeight.ExtraBold
                    )
                }

                Text("Couleurs prédéfinies",fontWeight=FontWeight.Bold,color=SettingsText)
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    presets.chunked(4).forEach{row->
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                            row.forEach{preset->
                                Column(
                                    Modifier.width(58.dp).clickable{useColor(preset.color)},
                                    horizontalAlignment=Alignment.CenterHorizontally
                                ){
                                    Box(
                                        Modifier.size(38.dp)
                                            .background(preset.color,CircleShape)
                                            .border(
                                                if(preset.color.toArgb()==preview.toArgb())2.dp else 1.dp,
                                                if(preset.color.toArgb()==preview.toArgb())SettingsPink else Color.Black.copy(alpha=.08f),
                                                CircleShape
                                            )
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(preset.name,fontSize=9.sp,color=SettingsMuted,maxLines=1)
                                }
                            }
                        }
                    }
                }

                Text("Personnalisée",fontWeight=FontWeight.Bold,color=SettingsText)
                HsvSlider("Teinte",hue,0f..360f,"${hue.roundToInt()}°"){hue=it}
                HsvSlider("Saturation",saturation,0f..1f,"${(saturation*100).roundToInt()} %"){saturation=it}
                HsvSlider("Valeur",value,0f..1f,"${(value*100).roundToInt()} %"){value=it}
            }
        },
        confirmButton={
            Button(onClick={apply(preview)}){Text("Appliquer")}
        },
        dismissButton={TextButton(onClick=dismiss){Text("Annuler")}}
    )
}

@Composable
private fun HsvSlider(
    label:String,
    value:Float,
    range:ClosedFloatingPointRange<Float>,
    display:String,
    update:(Float)->Unit
){
    Column{
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text(label,fontSize=13.sp,color=SettingsText)
            Text(display,fontSize=13.sp,fontWeight=FontWeight.Bold,color=SettingsPink)
        }
        Slider(value=value,onValueChange=update,valueRange=range)
    }
}

private fun markerLabel(type:DayType)=when(type){
    DayType.PERIOD->"Règles"
    DayType.FERTILE->"Fenêtre fertile"
    DayType.OVULATION->"Ovulation"
    DayType.LOW->"Fertilité faible"
    else->"Repère"
}

private fun colorHex(color:Color):String =
    "#%06X".format(color.toArgb() and 0xFFFFFF)
