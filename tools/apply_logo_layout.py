from pathlib import Path
import re

p = Path('MonCycleApp/app/src/main/java/fr/moncycle/app/MainActivity.kt')
s = p.read_text(encoding='utf-8')

if 'import androidx.compose.ui.layout.ContentScale' not in s:
    s = s.replace('import androidx.compose.ui.graphics.asImageBitmap\n', 'import androidx.compose.ui.graphics.asImageBitmap\nimport androidx.compose.ui.layout.ContentScale\n', 1)

# Force the logo helper to display the complete selected image without cropping.
logo_pattern = re.compile(r'@Composable private fun MonCycleLogo\(.*?\n\}\n\n@Composable private fun BrandHeader', re.S)
logo_replacement = '''@Composable private fun MonCycleLogo(
    modifier:Modifier=Modifier,
    contentDescription:String?=null
){
    val context=LocalContext.current
    val bitmap=remember{
        BitmapFactory.decodeResource(context.resources,R.drawable.mon_cycle_selected)
    }
    if(bitmap!=null){
        Image(
            bitmap=bitmap.asImageBitmap(),
            contentDescription=contentDescription,
            modifier=modifier,
            contentScale=ContentScale.Fit
        )
    }
}

@Composable private fun BrandHeader'''
if not logo_pattern.search(s):
    raise SystemExit('MonCycleLogo helper not found')
s = logo_pattern.sub(logo_replacement, s, count=1)

# Put the exact selected logo to the right of the MON CYCLE title.
pattern = re.compile(r'@Composable private fun BrandHeader\(\)\{.*?\n\}\n\n@Composable private fun HeroCycleCard', re.S)
replacement = '''@Composable private fun BrandHeader(){
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.SpaceBetween
    ){
        Column(Modifier.weight(1f)){
            Text("MON CYCLE",fontSize=28.sp,fontWeight=FontWeight.ExtraBold,color=TextDark)
            Text(
                "Mieux me connaître, chaque jour",
                fontSize=13.sp,
                color=Muted
            )
        }
        Spacer(Modifier.width(14.dp))
        MonCycleLogo(
            contentDescription="Logo Mon Cycle",
            modifier=Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(18.dp))
        )
    }
}

@Composable private fun HeroCycleCard'''
if not pattern.search(s):
    raise SystemExit('BrandHeader block not found')
s = pattern.sub(replacement, s, count=1)

# Remove any duplicate logo from the large Today card.
hero_pattern = re.compile(r'\s*Surface\(\s*modifier=Modifier\s*\.align\(Alignment\.CenterEnd\)\s*\.size\(112\.dp\).*?\n\s*\}\n', re.S)
s = hero_pattern.sub('\n', s, count=1)

p.write_text(s, encoding='utf-8')
print('V3.6 logo layout applied: full selected logo at right of MON CYCLE, no crop, no duplicate hero logo.')
