package com.campusfit.feature.tracking

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.campusfit.ui.components.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campusfit.domain.Profile
import com.campusfit.domain.tracking.*
import com.campusfit.data.local.WeighInEntity
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

fun duration(seconds: Int): String = "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
@Composable fun TrackingNotice(state: TrackingState) {
    if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
}
@Composable fun ProgressScreen(state: TrackingState, profile: Profile, vm: TrackingViewModel, gps: () -> Unit, camera: () -> Unit) {
    val context = LocalContext.current
    var days by rememberSaveable { mutableIntStateOf(7) }
    var input by rememberSaveable { mutableStateOf("") }
    var weighPopup by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val today = WeightPolicy.utcDay(now)
    val entries = state.weights.filter { it.epochDay in (today-days+1)..today }
    val todayWeight = state.weights.firstOrNull { it.epochDay == today }
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.connectHealth() }
    val lifecycle = LocalLifecycleOwner.current
    var healthStatus by remember { mutableIntStateOf(vm.health.status()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { healthStatus = vm.health.status(); vm.load(profile); vm.refresh(); now = System.currentTimeMillis() } }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val pending = state.weights.count { it.syncPending } + state.sessions.count { it.syncPending }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AppHeader("Look at you go.","Your progress",trailing={FilledTonalIconButton(onClick={weighPopup=true}){Icon(Icons.Default.Add,"Log a private weigh-in")}}) }
        item { Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Every move counts.",style=MaterialTheme.typography.titleLarge);Text("Your pace. Your progress.",color=MaterialTheme.colorScheme.onSurfaceVariant)};BuddyArt(BuddyMood.GROW,Modifier.size(120.dp))} }
        item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            StatTile("${state.sessions.filter{WeightPolicy.utcDay(it.startedAt) in today-6..today}.sumOf{it.durationSeconds}/60}","Minutes this UTC week",Icons.Default.Bolt,Modifier.weight(1f))
            StatTile("${state.sessions.size}","Saved moves",Icons.Default.FitnessCenter,Modifier.weight(1f))
        } }
        item { TrackingNotice(state) }
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Your weight trend",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);TextButton(onClick={weighPopup=true}){Text(if(todayWeight==null)"Log weight"else"Today ✓")}} }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf(1 to "Day",7 to "Week",30 to "Month").forEach { (value,label) -> FilterChip(days == value, { days = value }, label = { Text(label) }) } } }
        item { Card{Column(Modifier.fillMaxWidth().padding(16.dp)){WeightChart(entries)}} }
        item { InfoBubble("Private, always","Weight logging is optional and dates use UTC. Latest logged weight: ${state.weights.lastOrNull()?.weightKg?:profile.weightKg} kg. Only recorded days appear. Short-term changes can reflect hydration; these values are a record, not a score. Choose gradual changes and qualified advice for concerns.",Icons.Default.Lock) }
        if (entries.size >= 2) item {
            val change = entries.last().weightKg - entries.first().weightKg
            SoftTag("${"%+.1f".format(Locale.getDefault(),change)} kg across logged days",Icons.Default.Timeline)
        }
        items(entries.reversed(), key = { it.epochDay }) { entry ->
            val movement = state.sessions.filter { WeightPolicy.utcDay(it.startedAt) == entry.epochDay }.sumOf { it.durationSeconds }
            Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${LocalDate.ofEpochDay(entry.epochDay)} · ${entry.weightKg} kg")
                if (entry.flagged)InfoBubble("Large change flagged","Hydration or measurement can affect weight. Prioritize feeling well; seek qualified advice if concerned.",Icons.Default.WarningAmber)
                else if (WeightPolicy.supported(false,movement)) Text("Activity-supported · self-reported", color = MaterialTheme.colorScheme.primary)
                Text(if (!vm.cloudAvailable) "Saved on this phone" else if (entry.syncPending) "Waiting to sync" else "Synced privately", style = MaterialTheme.typography.labelSmall)
                if (entry.syncIssue.isNotBlank()) Text(entry.syncIssue, style = MaterialTheme.typography.bodySmall)
            } }
        }
        item { HorizontalDivider(); Text("Activity & steps", style = MaterialTheme.typography.titleLarge) }
        item { InfoBubble("How steps work","Health Connect reads today’s steps and exercise time in your device’s time zone, with your permission. They stay private and are not uploaded or added again to CampusFit sessions.",Icons.AutoMirrored.Filled.DirectionsWalk) }
        state.health?.let { summary -> item { Text("${summary.steps} steps · ${summary.exerciseMinutes} exercise minutes", style = MaterialTheme.typography.titleMedium) } }
        state.healthStatus?.let { message -> item { Text(message) } }
        item {
            if (state.healthEnabled) Row { OutlinedButton(onClick = vm::refresh) { Text("Refresh health totals") }; TextButton(onClick = vm::disconnectHealth) { Text("Disconnect") } }
            else OutlinedButton(onClick = {
                if (healthStatus == HealthConnectClient.SDK_AVAILABLE) healthLauncher.launch(vm.health.permissions)
                else if (healthStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata".toUri()))
            }, enabled = healthStatus != HealthConnectClient.SDK_UNAVAILABLE) { Text(if (healthStatus == HealthConnectClient.SDK_AVAILABLE) "Connect steps" else if (healthStatus == HealthConnectClient.SDK_UNAVAILABLE) "Steps unavailable" else "Set up Health Connect") }
        }
        item { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){ActionTile("Get some air",Icons.Default.Route,gps,Modifier.weight(1f),"Walk or run");ActionTile("Count my reps",Icons.Default.CameraAlt,camera,Modifier.weight(1f),"Optional camera")} }
        item { HorizontalDivider(); Text("Workout history", style = MaterialTheme.typography.titleLarge) }
        if (state.sessions.isEmpty()) item { EmptyMoment("Your story starts here.","Finish a move to see it land here.",BuddyMood.MOVE) }
        items(state.sessions.take(50), key = { it.id }) { session -> Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("${LocalDate.ofEpochDay(WeightPolicy.utcDay(session.startedAt))} · ${session.source.replace('-', ' ')}")
            Text("${duration(session.durationSeconds)} movement · ~${"%.1f".format(session.calories)} kcal estimated")
            if (session.source.startsWith("gps")) Text("${"%.0f".format(session.distanceMeters)} metres · route stays on this device")
            if (session.source == "camera") Text("${session.reps} estimated reps · not independently verified")
            Text(if (!vm.cloudAvailable) "Saved on this phone" else if (session.syncPending) "Waiting to sync" else "Synced privately", style = MaterialTheme.typography.labelSmall)
            if (session.syncIssue.isNotBlank()) Text(session.syncIssue, color = MaterialTheme.colorScheme.error)
        } } }
        if (state.sessions.size > 50) item { Text("Showing the latest 50 sessions; older records are retained.") }
        item { SoftTag(if(vm.cloudAvailable)"$pending records waiting to sync"else"Saved on your phone",Icons.Default.CloudQueue)
            OutlinedButton(onClick = vm::sync, enabled = vm.cloudAvailable) { Text("Try sync again") }
        }
    }
    if(weighPopup)DetailPopup("A quick check-in.",{weighPopup=false},BuddyMood.GROW){
        TrackingNotice(state)
        Text("Optional · one private entry per UTC day. Saved daily entries cannot be replaced.",style=MaterialTheme.typography.bodyMedium)
        todayWeight?.let{SoftTag("${it.weightKg} kg saved today",Icons.Default.CheckCircle)}
        OutlinedTextField(input,{input=it},label={Text("Weight · kg")},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal),enabled=todayWeight==null,modifier=Modifier.fillMaxWidth())
        Button(onClick={vm.weigh(input)},enabled=todayWeight==null&&!state.saving&&!state.loading,modifier=Modifier.fillMaxWidth()){Text(if(todayWeight!=null)"Saved for today"else"Save check-in")}
        TextButton(onClick={weighPopup=false}){Text("Done")}
    }

}
@Composable private fun WeightChart(entries: List<WeighInEntity>) {
    if (entries.isEmpty()) { EmptyMoment("A fresh page.","Log a check-in whenever you want.",BuddyMood.GROW); return }
    val foreground = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val min = entries.minOf { it.weightKg } - 0.5
    val max = entries.maxOf { it.weightKg } + 0.5
    val span = (entries.last().epochDay-entries.first().epochDay).coerceAtLeast(1)
    Text("Weight trend · ${"%.1f".format(min)}–${"%.1f".format(max)} kg", style = MaterialTheme.typography.labelMedium)
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "Weight trend for ${entries.size} recorded days. Individual values are listed below." }) {
        val pad = 14.dp.toPx()
        fun point(e: WeighInEntity) = Offset(pad + ((e.epochDay-entries.first().epochDay).toFloat()/span) * (size.width-2*pad), size.height-pad-((e.weightKg-min)/(max-min)*(size.height-2*pad)).toFloat())
        drawLine(outline, Offset(pad,size.height-pad),Offset(size.width-pad,size.height-pad),2f)
        entries.zipWithNext().forEach { (a,b) -> drawLine(foreground,point(a),point(b),4.dp.toPx()) }
        entries.forEach { drawCircle(if (it.flagged) pointColor else foreground,5.dp.toPx(),point(it)) }
    }
}
