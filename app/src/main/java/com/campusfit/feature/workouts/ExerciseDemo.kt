package com.campusfit.feature.workouts

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campusfit.domain.workouts.Exercise
import com.campusfit.domain.workouts.HumanScene
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun ExerciseDemo(exercise: Exercise, modifier: Modifier = Modifier, playing: Boolean = true, interactive: Boolean = false) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    var active by remember(lifecycle) { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var reducedMotion by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    var yaw by rememberSaveable(exercise.id) { mutableFloatStateOf(HumanScene.defaultYaw(exercise)) }
    var ready by remember(exercise.id) { mutableStateOf(false) }
    var failed by remember(exercise.id) { mutableStateOf(false) }
    var loading by remember(exercise.id) { mutableStateOf(false) }
    LaunchedEffect(exercise.id,ready,failed,active) {
        loading=false
        if(active && !ready && !failed) {
            delay(350);loading=true
            delay(19650);failed=true
        }
    }
    val background=MaterialTheme.colorScheme.surfaceVariant
    val dark=MaterialTheme.colorScheme.surface.luminance()<.3f
    DisposableEffect(lifecycle,context) {
        val observer=LifecycleEventObserver{_,_->active=lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)}
        val animationObserver=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reducedMotion=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f }
        }
        lifecycle.lifecycle.addObserver(observer)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,animationObserver)
        onDispose { lifecycle.lifecycle.removeObserver(observer);context.contentResolver.unregisterContentObserver(animationObserver) }
    }
    val spec=HumanRenderSpec(exercise,playing,reducedMotion,active,yaw,background.toArgb(),dark,if(interactive)30 else 15)
    val drag=if(interactive) Modifier.pointerInput(exercise.id) {
        detectHorizontalDragGestures { change,amount -> change.consume();yaw=(yaw+amount*.45f)%360f }
    } else Modifier
    BoxWithConstraints(modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)).background(background).then(drag)
        .semantics { contentDescription="${exercise.name}: 3D human demonstration. ${exercise.cue}" }) {
        if(!failed) AndroidView(
            factory={ HumanGlView(it,spec).apply { importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO;onReady={ready=true};onFailure={failed=true} } },
            update={it.onReady={ready=true};it.onFailure={failed=true};it.update(spec)},
            onRelease={it.dispose()},
            modifier=Modifier.fillMaxSize(),
        )
        if(failed) {
            val imageHeight=(384f/(maxWidth/maxHeight)).toInt().coerceIn(160,384)
            val poster by produceState<ImageBitmap?>(null,exercise.id,yaw,dark) {
                value=withContext(Dispatchers.Default){HumanSnapshot.render(exercise,yaw,dark,background.toArgb(),384,imageHeight).asImageBitmap()}
            }
            poster?.let{Image(it,null,Modifier.fillMaxSize())}
            Text("Still preview",Modifier.align(Alignment.TopStart).padding(10.dp),style=MaterialTheme.typography.labelSmall)
        }
        if(loading && !ready && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp),strokeWidth=2.dp)
        if(interactive) Row(Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha=.88f))) {
            IconButton(onClick={yaw=(yaw-45f)%360f},modifier=Modifier.size(34.dp)){Icon(Icons.Default.RotateLeft,"Turn view left",Modifier.size(18.dp))}
            IconButton(onClick={yaw=HumanScene.defaultYaw(exercise)},modifier=Modifier.size(34.dp)){Icon(Icons.Default.CenterFocusStrong,"Reset 3D view",Modifier.size(18.dp))}
            IconButton(onClick={yaw=(yaw+45f)%360f},modifier=Modifier.size(34.dp)){Icon(Icons.Default.RotateRight,"Turn view right",Modifier.size(18.dp))}
        }
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
