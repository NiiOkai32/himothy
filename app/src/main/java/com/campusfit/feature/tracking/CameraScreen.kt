package com.campusfit.feature.tracking

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.campusfit.ui.components.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campusfit.domain.tracking.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.*
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private fun metric(pose: Pose, mode: String): Double? {
    fun landmark(type: Int) = pose.getPoseLandmark(type)?.takeIf { it.inFrameLikelihood >= .75f }
    fun angle(a: Int,b: Int,c: Int): Double? {
        val pa=landmark(a)?.position ?: return null; val pb=landmark(b)?.position ?: return null; val pc=landmark(c)?.position ?: return null
        return PoseAngles.angle(pa.x.toDouble(),pa.y.toDouble(),pb.x.toDouble(),pb.y.toDouble(),pc.x.toDouble(),pc.y.toDouble())
    }
    return when(mode) {
        "Squats" -> angle(PoseLandmark.LEFT_HIP,PoseLandmark.LEFT_KNEE,PoseLandmark.LEFT_ANKLE)
            ?: angle(PoseLandmark.RIGHT_HIP,PoseLandmark.RIGHT_KNEE,PoseLandmark.RIGHT_ANKLE)
        "Push-ups" -> angle(PoseLandmark.LEFT_SHOULDER,PoseLandmark.LEFT_ELBOW,PoseLandmark.LEFT_WRIST)
            ?: angle(PoseLandmark.RIGHT_SHOULDER,PoseLandmark.RIGHT_ELBOW,PoseLandmark.RIGHT_WRIST)
        else -> {
            val left=landmark(PoseLandmark.LEFT_SHOULDER)?.position ?: return null
            val right=landmark(PoseLandmark.RIGHT_SHOULDER)?.position ?: return null
            val wristL=landmark(PoseLandmark.LEFT_WRIST)?.position ?: return null
            val wristR=landmark(PoseLandmark.RIGHT_WRIST)?.position ?: return null
            val ankleL=landmark(PoseLandmark.LEFT_ANKLE)?.position ?: return null
            val ankleR=landmark(PoseLandmark.RIGHT_ANKLE)?.position ?: return null
            val width=kotlin.math.abs(left.x-right.x).coerceAtLeast(1f)
            val spread=kotlin.math.abs(ankleL.x-ankleR.x)/width
            if (wristL.y < left.y && wristR.y < right.y && spread > 1.5) 0.0
            else if (wristL.y > left.y && wristR.y > right.y && spread < 1.2) 1.0 else .5
        }
    }
}
@Composable fun CameraScreen(state: TrackingState, vm: TrackingViewModel, back: () -> Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    var mode by rememberSaveable { mutableStateOf("Squats") }
    val counter=remember(mode) { if(mode=="Jumping jacks") RepCounter(.3,.7) else RepCounter(if(mode=="Squats")100.0 else 90.0,155.0) }
    var reps by remember(mode) { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var validAt by remember { mutableLongStateOf(-10_000L) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    val id=remember { java.util.UUID.randomUUID().toString() }
    val startedAt=remember { System.currentTimeMillis() }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission=it; cameraError=if(it)null else "Camera access was declined. Manual and guided workouts remain available." }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_STOP) { active=false; foreground=false; counter.reset(); validAt=-10_000 }
            if(event==Lifecycle.Event.ON_START) { foreground=true; permission=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED }
        }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer); active=false; counter.reset() }
    }
    LaunchedEffect(active,foreground) {
        var previous=SystemClock.elapsedRealtime()
        while(active && foreground) {
            delay(200); val now=SystemClock.elapsedRealtime()
            if(now-validAt <= 1500) elapsed=(elapsed+(now-previous)).coerceAtMost(20*60*1000)
            previous=now
            if(elapsed>=20*60*1000) active=false
        }
    }
    LaunchedEffect(state.cameraSaved) { if(state.cameraSaved==id) back() }
    fun leave() { active=false; if(elapsed>0 && state.cameraSaved!=id) discard=true else back() }
    BackHandler { leave() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        AppHeader("Find your rhythm.","Optional rep counter")
        SoftTag("On-device · no video saved",Icons.Default.Lock)
        InfoBubble("Before you start","Camera frames are processed on-device, then discarded. Nothing is recorded or uploaded. Counts are estimates, not medical form advice or independent activity verification. Use good light and keep your whole body in frame.",onOpen={active=false;counter.reset();validAt=-10_000})
        TrackingNotice(state)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("Squats","Push-ups","Jumping jacks").forEach { name -> FilterChip(mode==name,{mode=name; elapsed=0},enabled=!active && elapsed==0L,label={Text(name,style=MaterialTheme.typography.labelSmall)}) } }
        if(!permission) Button(onClick={launcher.launch(Manifest.permission.CAMERA)}) { Text("Allow camera") }
        if(permission && foreground) PoseCameraPreview(mode, Modifier.fillMaxWidth().height(280.dp), { value ->
            val time=SystemClock.elapsedRealtime()
            if(active && foreground) { if(value!=null) validAt=time; reps=counter.frame(value,time) } else counter.reset()
        }, { cameraError=it; active=false })
        cameraError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Text("$reps estimated reps · ${duration((elapsed/1000).toInt())} movement",style=MaterialTheme.typography.headlineSmall)
        Text(if(active && SystemClock.elapsedRealtime()-validAt>1500) "Position your whole body in good light. Time only advances while a usable pose is visible." else "Start from a neutral position, move through a comfortable range, then return. Use a side view for squats/push-ups and face the camera for jacks.")
        Button(onClick={active=!active; counter.reset(); validAt=-10_000},enabled=permission && foreground && cameraError==null && !state.saving && elapsed<20*60*1000) { Text(if(active)"Pause counting" else "Start / resume counting") }
        Button(onClick={active=false; vm.saveCamera(id,startedAt,(elapsed/1000).toInt(),reps,if(mode=="Jumping jacks")8.0 else if(mode=="Push-ups")6.0 else 3.5)},enabled=elapsed>=1000 && !state.saving) { Text("Save session & return") }
        InfoBubble("Save your move","Interrupted practice is not saved automatically. Save before leaving. Frames are never stored. Stop for pain or dizziness; guided movements work without a camera.",Icons.Default.Save,onOpen={active=false;counter.reset();validAt=-10_000})
        OutlinedButton(onClick={leave()},enabled=!state.saving) { Text("Back") }
    }
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text("Discard this unsaved practice?")},text={Text("Your unsaved time and rep estimate will be discarded. Return to the counter to save first.")},confirmButton={TextButton(onClick={discard=false;back()}){Text("Discard & leave")}},dismissButton={TextButton(onClick={discard=false}){Text("Return to save")}})
}
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable private fun PoseCameraPreview(mode: String, modifier: Modifier = Modifier, result: (Double?) -> Unit, error: (String) -> Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val previewView=remember { PreviewView(context) }
    val onResult by rememberUpdatedState(result)
    val onError by rememberUpdatedState(error)
    AndroidView(factory={previewView},modifier=modifier)
    DisposableEffect(lifecycle,mode) {
        val alive=AtomicBoolean(true)
        val executor=Executors.newSingleThreadExecutor()
        val detector=PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
        val preview=Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }
        val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis.setAnalyzer(executor) { proxy ->
            val image=proxy.image
            if(image==null || !alive.get()) proxy.close()
            else try {
                detector.process(InputImage.fromMediaImage(image,proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { pose -> if(alive.get()) onResult(metric(pose,mode)) }
                    .addOnFailureListener { if(alive.get()) { onResult(null); onError("Pose detection failed. Pause, check camera availability, and reopen this screen.") } }
                    .addOnCompleteListener { proxy.close() }
            } catch(_:Exception) { proxy.close() }
        }
        val future=ProcessCameraProvider.getInstance(context)
        future.addListener({ if(alive.get()) try { future.get().bindToLifecycle(lifecycle,CameraSelector.DEFAULT_FRONT_CAMERA,preview,analysis) } catch(_:Exception) { onError("A front camera could not be opened. Try another device or use guided workouts.") } },ContextCompat.getMainExecutor(context))
        onDispose { alive.set(false); analysis.clearAnalyzer(); if(future.isDone) try{future.get().unbind(preview,analysis)}catch(_:Exception){}; detector.close(); executor.shutdown() }
    }
}
