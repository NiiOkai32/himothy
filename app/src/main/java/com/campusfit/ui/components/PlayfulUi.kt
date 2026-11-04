package com.campusfit.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val Lime = Color(0xFFD5F778)
val Violet = Color(0xFF7360DA)
val Peach = Color(0xFFFFC6A8)
val Ink = Color(0xFF22242A)
enum class BuddyMood { MOVE, REST, TROPHY, MAIL, GROW }

/** Offline vector artwork. Decorative illustrations never duplicate a spoken control label. */
@Composable fun BuddyArt(modifier:Modifier=Modifier,animate:Boolean=false)=BuddyArt(BuddyMood.MOVE,modifier,animate)

@Composable fun BuddyArt(mood: BuddyMood, modifier: Modifier = Modifier, animate: Boolean = false) {
    val bob = if (animate && ValueAnimator.areAnimatorsEnabled()) {
        rememberInfiniteTransition(label = "buddy").animateFloat(
            -3f, 3f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float"
        ).value
    } else 0f
    Canvas(modifier.clearAndSetSemantics {}) {
        val scale = minOf(size.width / 220f, size.height / 200f)
        withTransform({ translate((size.width - 220f * scale) / 2, (size.height - 200f * scale) / 2); scale(scale, scale, Offset.Zero) }) {
            fun line(a: Offset, b: Offset, color: Color = Ink, width: Float = 9f) = drawLine(color, a, b, width, StrokeCap.Round)
            fun star(x: Float, y: Float, radius: Float, color: Color) {
                val p = Path().apply { moveTo(x, y-radius); lineTo(x+radius*.3f,y-radius*.3f);lineTo(x+radius,y);lineTo(x+radius*.3f,y+radius*.3f);lineTo(x,y+radius);lineTo(x-radius*.3f,y+radius*.3f);lineTo(x-radius,y);lineTo(x-radius*.3f,y-radius*.3f);close() }
                drawPath(p,color)
            }
            drawOval(Ink.copy(alpha=.09f),Offset(52f,179f),Size(120f,12f))
            star(30f,43f,10f,Violet);star(190f,108f,8f,Color(0xFFFFA284))
            drawCircle(Violet.copy(alpha=.16f),7f,Offset(190f,34f));drawCircle(Color.White.copy(alpha=.65f),4f,Offset(26f,137f))
            translate(top=bob) {
                // Limbs are drawn behind the body; poses change with the surrounding task.
                if(mood==BuddyMood.REST){line(Offset(80f,143f),Offset(56f,170f));line(Offset(138f,143f),Offset(161f,168f))}
                else{line(Offset(91f,141f),Offset(80f,176f));line(Offset(131f,140f),Offset(153f,160f));line(Offset(153f,160f),Offset(169f,147f))}
                line(Offset(69f,95f),Offset(43f,73f));line(Offset(43f,73f),Offset(37f,51f))
                line(Offset(151f,93f),Offset(178f,74f));line(Offset(178f,74f),Offset(183f,51f))
                drawRoundRect(Violet,Offset(65f,172f),Size(28f,13f),CornerRadius(6f))
                drawRoundRect(Violet,Offset(if(mood==BuddyMood.REST)153f else 161f,if(mood==BuddyMood.REST)165f else 139f),Size(28f,13f),CornerRadius(6f))
                val body=Path().apply{moveTo(68f,67f);cubicTo(73f,35f,137f,24f,153f,59f);cubicTo(165f,86f,164f,121f,145f,140f);cubicTo(128f,156f,83f,155f,68f,135f);cubicTo(53f,116f,53f,90f,68f,67f);close()}
                drawPath(body,Ink.copy(alpha=.12f),style=Stroke(5f));drawPath(body,Lime)
                drawOval(Color.White.copy(alpha=.45f),Offset(72f,52f),Size(20f,36f))
                if(mood==BuddyMood.REST){
                    drawArc(Ink,0f,180f,false,Offset(84f,80f),Size(15f,9f),style=Stroke(4f,cap=StrokeCap.Round))
                    drawArc(Ink,0f,180f,false,Offset(120f,80f),Size(15f,9f),style=Stroke(4f,cap=StrokeCap.Round))
                }else{
                    drawOval(Ink,Offset(85f,75f),Size(12f,19f));drawOval(Ink,Offset(120f,75f),Size(12f,19f))
                    drawCircle(Color.White,2.5f,Offset(92f,79f));drawCircle(Color.White,2.5f,Offset(127f,79f))
                }
                drawCircle(Peach,7f,Offset(78f,100f));drawCircle(Peach,7f,Offset(140f,100f))
                drawArc(Ink,8f,164f,false,Offset(96f,94f),Size(25f,18f),style=Stroke(4f,cap=StrokeCap.Round))
                when(mood){
                    BuddyMood.TROPHY->{
                        drawRoundRect(Color(0xFFFFB85B),Offset(91f,116f),Size(38f,27f),CornerRadius(8f))
                        drawArc(Color(0xFFFFB85B),90f,180f,false,Offset(79f,116f),Size(25f,23f),style=Stroke(5f))
                        drawArc(Color(0xFFFFB85B),-90f,180f,false,Offset(117f,116f),Size(25f,23f),style=Stroke(5f))
                        line(Offset(110f,141f),Offset(110f,153f),Color(0xFFFFB85B),5f);line(Offset(99f,155f),Offset(121f,155f),Color(0xFFFFB85B),5f)
                        star(110f,126f,6f,Color.White)
                    }
                    BuddyMood.MAIL->{drawRoundRect(Color.White,Offset(78f,117f),Size(67f,35f),CornerRadius(6f));line(Offset(81f,120f),Offset(110f,136f),Violet,3f);line(Offset(110f,136f),Offset(141f,120f),Violet,3f)}
                    BuddyMood.GROW->{for(i in 0..2)drawRoundRect(Violet.copy(alpha=1f-i*.15f),Offset(84f+i*19,140f-i*10),Size(12f,15f+i*10),CornerRadius(4f))}
                    BuddyMood.REST->{drawCircle(Violet,12f,Offset(173f,34f));drawCircle(Color(0xFFF8F5FF),10f,Offset(179f,29f))}
                    else->star(111f,128f,10f,Color.White)
                }
            }
        }
    }
}

@Composable fun AppHeader(title:String, eyebrow:String="CampusFit", back:(()->Unit)?=null, trailing:(@Composable ()->Unit)?=null){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        if(back!=null)FilledTonalIconButton(onClick=back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
        Column(Modifier.weight(1f)){
            Text(eyebrow,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold)
        }
        trailing?.invoke()
    }
}

@Composable fun HeroCard(title:String,subtitle:String,button:String,onClick:()->Unit,mood:BuddyMood=BuddyMood.MOVE,color:Color=Lime){
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(30.dp),colors=CardDefaults.cardColors(containerColor=color,contentColor=Ink)){
        Box(Modifier.fillMaxWidth().padding(22.dp)){
            BuddyArt(mood,Modifier.align(Alignment.CenterEnd).width(126.dp).height(158.dp),animate=true)
            Column(Modifier.fillMaxWidth(.62f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text(subtitle,style=MaterialTheme.typography.labelLarge)
                Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold)
                Button(onClick=onClick,colors=ButtonDefaults.buttonColors(containerColor=Ink,contentColor=Color.White)){
                    Text(button);Spacer(Modifier.width(6.dp));Icon(Icons.AutoMirrored.Filled.ArrowForward,null,Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable fun ActionTile(title:String,icon:ImageVector,onClick:()->Unit,modifier:Modifier=Modifier,subtitle:String?=null){
    Card(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh)){
        Column(Modifier.fillMaxWidth().heightIn(min=112.dp).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(icon,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            subtitle?.let{Text(it,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
@Composable fun StatTile(value:String,label:String,icon:ImageVector,modifier:Modifier=Modifier){
    Card(modifier,shape=RoundedCornerShape(24.dp)){
        Row(Modifier.fillMaxWidth().heightIn(min=96.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Icon(icon,null,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary)
            Column{Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
@Composable fun SoftTag(label:String,icon:ImageVector?=null){
    Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)){
        icon?.let{Icon(it,null,Modifier.size(15.dp))};Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Details are user-triggered; no surprise modal interrupts a workout or hides an error. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DetailPopup(title:String,onDismiss:()->Unit,mood:BuddyMood=BuddyMood.MAIL,content:@Composable ColumnScope.()->Unit){
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        Column(Modifier.fillMaxWidth().heightIn(max=620.dp).verticalScroll(rememberScrollState()).padding(horizontal=24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(14.dp)){
            BuddyArt(mood,Modifier.align(Alignment.CenterHorizontally).size(100.dp))
            Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold)
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}
@Composable fun InfoBubble(title:String,body:String,icon:ImageVector=Icons.Default.Info,onOpen:()->Unit={}){
    var open by rememberSaveable{mutableStateOf(false)}
    TextButton(onClick={onOpen();open=true},contentPadding=PaddingValues(horizontal=0.dp)){
        Icon(icon,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(title)
    }
    if(open)DetailPopup(title,{open=false}){Text(body,style=MaterialTheme.typography.bodyLarge);Button(onClick={open=false},modifier=Modifier.fillMaxWidth()){Text("Got it")}}
}
@Composable fun EmptyMoment(title:String,caption:String,mood:BuddyMood=BuddyMood.MAIL){
    Column(Modifier.fillMaxWidth().padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){
        BuddyArt(mood,Modifier.size(120.dp));Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text(caption,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun ConsentCheck(label:String,checked:Boolean,onChange:(Boolean)->Unit){
 Row(Modifier.fillMaxWidth().toggleable(value=checked,role=Role.Checkbox,onValueChange=onChange).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
  Checkbox(checked,onCheckedChange=null);Spacer(Modifier.width(8.dp));Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
 }
}
