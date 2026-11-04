package com.campusfit.feature.workouts

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.campusfit.domain.workouts.Exercise
import kotlin.math.*

/** A clothed, layered 2D person driven by the catalogue's exercise-specific keyframes.
 * Everything is drawn locally; no video, network, or downloaded animation is needed. */
@Composable fun ExerciseDemo(exercise: Exercise, modifier: Modifier = Modifier, playing: Boolean = true) {
    val frames = exercise.demo
    val duration = when (exercise.id) {
        "fast-feet" -> 650
        "high-knees", "butt-kicks", "invisible-rope-skipping", "easy-jog" -> 1300
        "jumping-jacks", "step-jacks" -> 1800
        else -> if (exercise.category == "Yoga & stretch") 5500 else 3200
    }
    val progress = if (playing && ValueAnimator.areAnimatorsEnabled()) {
        val animation = rememberInfiniteTransition(label = "exercise demonstration")
        animation.animateFloat(0f, frames.size.toFloat(), infiniteRepeatable(tween(duration, easing = LinearEasing)), label = "human pose")
    } else rememberUpdatedState(1f) // A useful peak pose when system animations are disabled.
    val background = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(200.dp).background(background, RoundedCornerShape(24.dp))
        .semantics { contentDescription = "${exercise.name}: 2D human demonstration. ${exercise.cue} Form tips are available on the exercise screen." }) {
        // Read animation in the draw phase, avoiding recomposition of a scrolling card each frame.
        val value = progress.value
        val index = floor(value).toInt().mod(frames.size)
        val fraction = (1f - cos((value - floor(value)) * PI.toFloat())) / 2f
        val first = frames[index].points
        val next = frames[(index + 1).mod(frames.size)].points
        val height = size.height * .94f
        val width = min(size.width * .96f, height * 1.24f)
        val left = (size.width - width) / 2f
        fun at(i: Int): Offset = Offset(left + (first[i*2] + (next[i*2]-first[i*2])*fraction)*width,
            size.height*.025f + (first[i*2+1] + (next[i*2+1]-first[i*2+1])*fraction)*height)
        val unit = min(width, height)
        val skin = Color(0xFFDCA47D)
        val skinShade = Color(0xFFB97C5C)
        val shirt = Color(0xFFBC9BFA)
        val shirtShade = Color(0xFF9574D2)
        val trousers = Color(0xFF48405E)
        val shoe = Color(0xFFE0F5A2)
        val hair = Color(0xFF352D3D)
        fun limb(a: Offset, b: Offset, color: Color, thickness: Float) {
            drawLine(color,a,b,unit*thickness,StrokeCap.Round)
        }
        fun polygon(points: List<Offset>, color: Color) {
            val path=Path().apply { moveTo(points.first().x,points.first().y); points.drop(1).forEach{lineTo(it.x,it.y)}; close() }
            drawPath(path,color)
        }
        val ground = height*.95f
        drawOval(outline.copy(alpha=.25f),Offset(left+width*.16f,ground-height*.016f),Size(width*.70f,height*.045f))
        drawLine(outline,Offset(left+width*.08f,ground),Offset(left+width*.92f,ground),unit*.009f,StrokeCap.Round)
        if (exercise.id.contains("wall")) {
            drawLine(outline,Offset(left+width*.88f,height*.16f),Offset(left+width*.88f,ground),unit*.025f,StrokeCap.Round)
        }
        if (exercise.id == "stair-climbing") {
            drawRoundRect(outline.copy(alpha=.6f),Offset(left+width*.30f,height*.85f),Size(width*.40f,height*.09f),androidx.compose.ui.geometry.CornerRadius(unit*.02f))
        }
        // Hip width follows the torso angle, which also gives floor exercises a side profile.
        val shoulder = (at(2)+at(3))/2f
        val hip = at(8)
        val spine = hip-shoulder
        val length = spine.getDistance().coerceAtLeast(1f)
        val normal = Offset(-spine.y/length,spine.x/length)
        val front = abs(at(3).x-at(2).x) > width*.07f
        val hipWidth = unit * if(front) .055f else .035f
        val hips = listOf(hip+normal*hipWidth,hip-normal*hipWidth)
        fun leg(side: Int, far: Boolean) {
            val knee=at(9+side); val foot=at(11+side)
            val top=hips[side]
            limb(top,knee,if(far) Color(0xFF3B344E) else trousers,.071f)
            limb(knee,foot,if(far) skinShade else skin,.052f)
            limb(top,top+(knee-top)*.48f,if(far) Color(0xFF3B344E) else trousers,.082f)
            val toe = foot+Offset(unit*(if(front && side==0) -.04f else .04f),unit*.006f)
            limb(foot,toe,if(far) Color(0xFFBACD8B) else shoe,.052f)
            drawLine(Color.White.copy(alpha=.85f),foot+Offset(-unit*.012f,unit*.022f),toe+Offset(unit*.013f,unit*.022f),unit*.012f,StrokeCap.Round)
        }
        fun arm(side: Int, far: Boolean) {
            val top=at(2+side);val elbow=at(4+side);val hand=at(6+side)
            limb(top,elbow,if(far) skinShade else skin,.047f)
            limb(elbow,hand,if(far) skinShade else skin,.039f)
            limb(top,top+(elbow-top)*.30f,if(far) shirtShade else shirt,.067f)
            drawCircle(if(far) skinShade else skin,unit*.025f,hand)
        }
        leg(1,true);arm(1,true)
        leg(0,false)
        limb(at(0),shoulder,skin,.038f)
        val shoulderWidth = max((at(3)-at(2)).getDistance()/2f,unit*.043f)
        val shoulderEnds=listOf(shoulder+normal*shoulderWidth,shoulder-normal*shoulderWidth)
        polygon(listOf(shoulderEnds[0],shoulderEnds[1],hips[1]+normal*(-unit*.014f),hips[0]+normal*(unit*.014f)),shirt)
        limb(shoulderEnds[1],hips[1],shirtShade,.016f)
        limb(hips[0],hips[1],trousers,.047f)
        // Small chest badge and a collar make the silhouette read as a person in sportswear.
        drawCircle(Color.White.copy(alpha=.75f),unit*.012f,shoulder+(hip-shoulder)*.28f)
        arm(0,false)
        val head=at(0)
        val headAxis=head-at(1)
        val angle=atan2(headAxis.y,headAxis.x)*180f/PI.toFloat()+90f
        rotate(angle,head) {
            drawOval(skin,head-Offset(unit*.047f,unit*.062f),Size(unit*.094f,unit*.124f))
            drawArc(hair,180f,180f,true,head-Offset(unit*.050f,unit*.067f),Size(unit*.100f,unit*.088f))
            drawCircle(hair,unit*.021f,head+Offset(-unit*.035f,-unit*.022f))
            drawCircle(hair,unit*.006f,head+Offset(unit*.020f,-unit*.002f))
            drawLine(skinShade,head+Offset(unit*.014f,unit*.026f),head+Offset(unit*.029f,unit*.023f),unit*.006f,StrokeCap.Round)
        }
        // Quiet visual accent, clear of the demonstration and its floor contact points.
        drawCircle(accent.copy(alpha=.22f),unit*.018f,Offset(size.width*.90f,size.height*.15f))
    }
}

@Composable fun CompletionCelebration() {
    var launched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { launched = true }
    val fraction by animateFloatAsState(if (launched) 1f else 0f, tween(1800, easing = LinearEasing), label = "celebration")
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        if (ValueAnimator.areAnimatorsEnabled() && fraction < 1f) repeat(36) { n ->
            val x = ((n * 37 % 101) / 100f) * size.width
            val y = ((fraction + n / 36f) % 1f) * size.height
            drawCircle(colors[n % colors.size].copy(alpha = 1f - fraction), (3 + n % 3).dp.toPx(), Offset(x,y))
        }
    }
}
