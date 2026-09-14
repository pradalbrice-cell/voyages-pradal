from pathlib import Path
import re

p = Path('MonCycleApp/app/src/main/java/fr/moncycle/app/MainActivity.kt')
s = p.read_text(encoding='utf-8')

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
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
        )
    }
}

@Composable private fun HeroCycleCard'''
if not pattern.search(s):
    raise SystemExit('BrandHeader block not found')
s = pattern.sub(replacement, s, count=1)

# Remove the second copy of the logo from the large Today card; it caused a broken/duplicated image.
hero_logo = '''        Surface(
            modifier=Modifier
                .align(Alignment.CenterEnd)
                .size(112.dp),
            color=Color.White.copy(alpha=.18f),
            shape=RoundedCornerShape(28.dp)
        ){
            MonCycleLogo(
                contentDescription=null,
                modifier=Modifier.padding(10.dp).clip(RoundedCornerShape(22.dp))
            )
        }
'''
if hero_logo in s:
    s = s.replace(hero_logo, '', 1)

p.write_text(s, encoding='utf-8')
print('Logo layout applied: exact selected logo at right of MON CYCLE header; duplicate hero logo removed.')
