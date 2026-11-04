package com.campusfit.feature.tracking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.campusfit.ui.components.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campusfit.domain.tracking.OutdoorRun
import com.google.android.gms.location.*

@Composable fun OutdoorScreen(state: TrackingState, vm: TrackingViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    fun permitted() = ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(permitted()) }
    var locationIssue by remember { mutableStateOf<String?>(null) }
    var foreground by remember { mutableStateOf(lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission = permitted(); if (!permission) { vm.pauseOutdoor(); locationIssue = "Location access was not granted. Guided indoor workouts still work." } }
    val run = state.outdoor
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { foreground = false; vm.pauseOutdoor() }
            if (event == Lifecycle.Event.ON_START) { foreground = true; permission = permitted(); if (!permission) vm.pauseOutdoor() }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); vm.pauseOutdoor() }
    }
    DisposableEffect(permission, run?.paused, run?.id, foreground) {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) { result.locations.forEach { location ->
                @Suppress("DEPRECATION") val mock = location.isFromMockProvider
                vm.point(location.latitude,location.longitude,location.accuracy,location.elapsedRealtimeNanos/1_000_000,mock)
                locationIssue = if (mock) "Mock locations are ignored." else if (location.accuracy > 35f) "Waiting for a precise fix. Distance is not updated from low-accuracy points." else null
            } }
            override fun onLocationAvailability(availability: LocationAvailability) { if (!availability.isLocationAvailable) locationIssue = "GPS is unavailable. Check system location settings or pause your session." }
        }
        if (permission && run?.paused == false && !run.finished && foreground) {
            try { client.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,2000).setMinUpdateIntervalMillis(1000).build(),callback,Looper.getMainLooper()).addOnFailureListener { locationIssue = "Could not start location updates. Check location settings."; vm.pauseOutdoor() } }
            catch (_: SecurityException) { permission = false; vm.pauseOutdoor() }
        }
        onDispose { client.removeLocationUpdates(callback) }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppHeader("Catch some air.","Campus walk / run")
        SoftTag("Pauses when you leave",Icons.Default.PauseCircle)
        if(run==null)BuddyArt(BuddyMood.MOVE,Modifier.fillMaxWidth().height(150.dp))
        InfoBubble("Your route stays yours","Routes stay encrypted on this device; only session totals sync. Tracking pauses when you leave this screen or background the app. There is no screen-off recording. Time may advance before a precise GPS fix, so pause if you are not moving.",Icons.Default.Lock,onOpen=vm::pauseOutdoor)
        TrackingNotice(state)
        if (!permission) OutlinedButton(onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Allow foreground location") }
        locationIssue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (run == null || run.finished) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.startOutdoor("walk") },enabled = permission && !state.saving) { Text("New walk") }
                Button(onClick = { vm.startOutdoor("run") },enabled = permission && !state.saving) { Text("New run") }
            }
        }
        run?.let {
            Text("${it.mode.replaceFirstChar(Char::uppercase)} · ${duration((it.elapsedMillis/1000).toInt())}",style = MaterialTheme.typography.headlineSmall)
            Text("${"%.0f".format(it.distanceMeters)} metres from accepted GPS fixes")
            RouteSketch(it)
            Text(if (it.finished && state.error == null && !state.saving) "Session finished and stored locally." else if (it.finished) "Finished; saving has not been confirmed. Retry if an error is shown." else if (it.paused) "Paused. Tap Resume when ready." else "Recording while this screen is in the foreground. Maximum 20 minutes.")
            if (!it.finished) {
                Button(onClick = vm::toggleOutdoor, enabled = permission && !state.saving) { Text(if (it.paused) "Resume" else "Pause") }
                OutlinedButton(onClick = vm::finishOutdoor,enabled = !state.saving) { Text("Finish & save") }
            }
            if (state.error != null) OutlinedButton(onClick = vm::retryOutdoor) { Text("Retry saving") }
        }
        InfoBubble("About GPS estimates","Poor accuracy, implausible jumps and gaps are excluded. Paused segments are not joined. The route sketch scales each axis independently; it is not a navigation map.",Icons.Default.Route,onOpen=vm::pauseOutdoor)
        OutlinedButton(onClick = { vm.pauseOutdoor(); back() }, enabled = !state.saving) { Text("Leave paused / back") }
        state.routes.filter { it.finished }.take(3).forEach { saved -> Text("Saved ${saved.mode} · ${duration((saved.elapsedMillis/1000).toInt())}"); RouteSketch(saved) }
    }
}
@Composable private fun RouteSketch(run: OutdoorRun) {
    val color = MaterialTheme.colorScheme.primary
    if (run.points.isEmpty()) { Text("No accurate route points yet."); return }
    val latMin = run.points.minOf { it.latitude }; val latSpan = (run.points.maxOf { it.latitude }-latMin).coerceAtLeast(.0001)
    val lonMin = run.points.minOf { it.longitude }; val lonSpan = (run.points.maxOf { it.longitude }-lonMin).coerceAtLeast(.0001)
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val pad=16.dp.toPx()
        fun point(i: Int) = run.points[i].let { Offset(pad+((it.longitude-lonMin)/lonSpan*(size.width-pad*2)).toFloat(),size.height-pad-((it.latitude-latMin)/latSpan*(size.height-pad*2)).toFloat()) }
        run.points.indices.drop(1).forEach { i -> if (run.points[i].segment == run.points[i-1].segment) drawLine(color,point(i-1),point(i),4.dp.toPx()) }
        drawCircle(color,6.dp.toPx(),point(0)); drawCircle(color,6.dp.toPx(),point(run.points.lastIndex))
    }
    Text("Route sketch · independently scaled axes, not a navigation map",style = MaterialTheme.typography.labelSmall)
}
