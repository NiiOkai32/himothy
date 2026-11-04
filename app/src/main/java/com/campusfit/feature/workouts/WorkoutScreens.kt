package com.campusfit.feature.workouts

import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.campusfit.ui.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import com.campusfit.domain.Profile
import com.campusfit.domain.workouts.*
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode

private fun clock(seconds: Int): String = "%d:%02d".format(Locale.ROOT, seconds / 60, seconds % 60)
private fun estimate(calories: Double) = "~%.1f kcal".format(Locale.getDefault(), calories)
@Composable private fun WorkoutHeading(title:String,back:(()->Unit)?=null){AppHeader(title,"Move",back)}
@Composable private fun ErrorNotice(state: WorkoutsState, retry: () -> Unit) {
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { error -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) { Text(error); TextButton(onClick = retry, enabled = !state.saving) { Text("Retry") } }
    } }
}
@Composable fun WorkoutLibraryScreen(state: WorkoutsState, profile: Profile, retry: () -> Unit, detail: (String) -> Unit, quick: () -> Unit, resume: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var quiet by rememberSaveable { mutableStateOf(false) }
    var gentle by rememberSaveable { mutableStateOf(false) }
    var previews by rememberSaveable { mutableStateOf(true) }
    val items = remember(state.exercises, search, category, quiet, gentle) {
        state.exercises.filter { e -> (category == null || e.category == category) && (!quiet || e.lowNoise) && (!gentle || e.gentle) &&
            (e.name.contains(search, true) || e.muscles.any { it.contains(search, true) }) }
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { WorkoutHeading("Pick your vibe.") }
        item { HeroCard("Small move.\nBig energy.","2–20 minutes · no gear","Mix my workout",quick) }
        state.run?.takeIf { !it.finished }?.let { run -> item { Card { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your saved workout", fontWeight = FontWeight.Bold); Text(run.plan.title)
            SoftTag("Ready when you are · ${run.stageIndex+1}/${run.plan.stages.size}",Icons.Default.PauseCircle)
            Button(onClick = resume) { Text("Resume workout") }
        } } } }
        item { ErrorNotice(state, retry) }
        item { OutlinedTextField(search, { search = it }, label = { Text("Find a move") }, singleLine = true, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }) }
        item { CategoryPicker(category) { category = it } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(quiet, { quiet = !quiet }, label = { Text("Low noise") })
            FilterChip(gentle, { gentle = !gentle }, label = { Text("Take it easy") })
        } }
        if (!state.loading && items.isEmpty()) item { EmptyMoment("New search, new vibe","Try another category or clear your search.",BuddyMood.REST) }
        item { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${items.size} moves",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);IconToggleButton(previews,{previews=it}){Icon(if(previews)Icons.Default.PauseCircle else Icons.Default.PlayCircle,if(previews)"Pause previews" else "Animate previews")};InfoBubble("About estimates","Energy estimates use MET values and your saved weight. They vary with pace, body size and technique; they are not a workout score.")} }
        items(items.chunked(2),key={it.first().id}){pair->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                pair.forEach{exercise->
                    Card(onClick={detail(exercise.id)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(24.dp)){
                        ExerciseDemo(exercise,Modifier.height(144.dp),playing=previews)
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                            Text(exercise.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                            Text(exercise.difficulty,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(exercise.lowNoise)Icons.AutoMirrored.Filled.VolumeOff else if(exercise.campusOnly)Icons.Default.Park else Icons.Default.Bolt,null,Modifier.size(15.dp));Text(if(exercise.campusOnly)"Outdoor"else if(exercise.lowNoise)"Dorm-friendly"else"Room to move",style=MaterialTheme.typography.labelSmall)}
                        }
                    }
                }
                if(pair.size==1)Spacer(Modifier.weight(1f))
            }
        }
    }
}
@Composable private fun CategoryPicker(selected: String?, change: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected == null, { change(null) }, label = { Text("All") }) }
        items(Categories.all) { category -> FilterChip(selected == category, { change(category) }, label = { Text(category) }) }
    }
}
@Composable fun WorkoutDetailScreen(exerciseId: String, state: WorkoutsState, profile: Profile, vm: WorkoutsViewModel, back: () -> Unit, alternative: (String) -> Unit, plan: () -> Unit, outdoor: (() -> Unit)? = null) {
    val exercise = state.exercises.firstOrNull { it.id == exerciseId }
    var animate by rememberSaveable(exerciseId) { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WorkoutHeading(exercise?.name ?: "Exercise", back)
        if (exercise == null) { Text(if (state.loading) "Loading exercise…" else "This exercise could not be found."); return@Column }
        ExerciseDemo(exercise, playing = animate)
        TextButton(onClick = { animate = !animate }) { Text(if (animate) "Pause demo" else "Play demo") }
        SoftTag("Follow the form tips below",Icons.Default.CheckCircleOutline)
        Text("${exercise.category} · ${exercise.difficulty}", color = MaterialTheme.colorScheme.primary)
        InfoBubble("Energy estimate","${estimate(Energy.calories(exercise.met,profile.weightKg,60_000))} per minute at your logged weight; ${exercise.met} MET. Estimates vary with pace, body size and technique. They are not a target or a measure of workout quality.",Icons.Default.Bolt)
        Text("Target areas", fontWeight = FontWeight.Bold); Text(exercise.muscles.joinToString(" · "))
        Text("How to move", fontWeight = FontWeight.Bold)
        exercise.instructions.forEachIndexed { index, instruction -> Text("${index + 1}. $instruction") }
        if (exercise.instructions.any { it.contains("halfway") }) Text("For one-sided movements, switch sides halfway through the work interval.", color = MaterialTheme.colorScheme.primary)
        if (exercise.campusOnly) {
            Text("Use a safe campus route. Walks and runs can use foreground GPS; stair climbing is not distance-tracked.")
            if (exercise.category == "Walking & running" && outdoor != null) OutlinedButton(onClick = outdoor) { Text("Track an outdoor session") }
        }
        InfoBubble("Move safely","Stop for pain, dizziness or unusual breathlessness. An easier alternative is not a treatment for an injury. Check with a qualified professional when needed.",Icons.Default.FavoriteBorder)
        OutlinedButton(onClick = { alternative(exercise.alternativeId) }) { Text("See an easier alternative") }
        val withinLevel = listOf("Beginner", "Intermediate", "Advanced").indexOf(exercise.difficulty) <= listOf("Beginner", "Intermediate", "Advanced").indexOf(profile.level)
        if (!withinLevel) Text("This is above your saved fitness level. Start with the easier alternative or change the level in your profile.")
        Button(onClick = { vm.generate(2, profile.level, false, false, exerciseId = exercise.id); plan() }, enabled = !exercise.campusOnly && withinLevel, modifier = Modifier.fillMaxWidth()) { Text("Build a 2-minute practice") }
    }
}
@Composable fun QuickWorkoutScreen(state: WorkoutsState, profile: Profile, vm: WorkoutsViewModel, back: () -> Unit, player: () -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(profile.minutes.coerceIn(2,20)) }
    var quiet by rememberSaveable { mutableStateOf(true) }
    var gentle by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    LaunchedEffect(state.preview?.id){state.preview?.let{minutes=(it.durationSeconds/60).coerceIn(2,20)}}
    LaunchedEffect(state.run?.id, state.saved, state.error) {
        if (starting && state.run?.plan?.id == state.preview?.id && state.run?.finished == false && state.saved) { starting = false; player() }
        if (state.error != null) starting = false
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WorkoutHeading("Make a little time.", back)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("$minutes",style=MaterialTheme.typography.displayLarge);Text("minutes. That’s your window.")};BuddyArt(modifier=Modifier.size(140.dp))}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(5,10,15,20).forEach { value -> FilterChip(minutes == value, { minutes = value; vm.clearPreview() }, label = { Text("$value min") }) } }
        Slider(minutes.toFloat(), { minutes = it.toInt(); vm.clearPreview() }, valueRange = 2f..20f, steps = 17)
        SoftTag(profile.level,Icons.Default.FitnessCenter)
        CategoryPicker(category) { category = it; vm.clearPreview() }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Keep it quiet", Modifier.weight(1f)); Switch(quiet, { quiet = it; vm.clearPreview() }, modifier = Modifier.semantics { contentDescription = "Keep it quiet" }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Gentle movements only", Modifier.weight(1f)); Switch(gentle, { gentle = it; vm.clearPreview() }, modifier = Modifier.semantics { contentDescription = "Gentle movements only" }) }
        InfoBubble("What’s in my mix?","A warm-up, work intervals, recovery breaks and a gentle finish. Each movement gets an extra 3-second count-in; those seconds do not earn activity credit. Gentle options use small ranges and no floor work. For an injury or medical concern, choose movements with your clinician’s advice.")
        ErrorNotice(state) { if (state.run != null && !state.saved) vm.retrySave() else vm.generate(minutes, profile.level, quiet, gentle, category) }
        Button(onClick = { vm.generate(minutes, profile.level, quiet, gentle, category) }, enabled = !state.loading && state.exercises.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Mix it up") }
        state.preview?.let { preview ->
            Text(preview.title, style = MaterialTheme.typography.titleLarge)
            SoftTag("${clock(preview.durationSeconds)} + count-ins · ${estimate(WorkoutGenerator.estimate(preview,state.exercises,profile.weightKg))} estimated",Icons.Default.Timer)
            var planDetails by remember{mutableStateOf(false)}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SoftTag("${preview.stages.count{it.kind!=StageKind.REST}} intervals",Icons.Default.Bolt);TextButton(onClick={planDetails=true}){Text("See the lineup")}}
            if(planDetails)DetailPopup("Your lineup",{planDetails=false}){
                preview.stages.forEachIndexed{index,stage->val name=stage.exerciseId?.let{id->state.exercises.firstOrNull{it.id==id}?.name}?:"Rest & breathe";ListItem(headlineContent={Text(name)},supportingContent={Text("${stage.seconds}s · ${stage.kind.name.lowercase()}")},leadingContent={Text("${index+1}",style=MaterialTheme.typography.titleLarge)})}
                Button(onClick={planDetails=false},modifier=Modifier.fillMaxWidth()){Text("Looks good")}
            }
            if (state.run?.finished == false) {
                Text("You already have a saved workout. Resume it or end it before starting this plan.")
                OutlinedButton(onClick = player) { Text("Open saved workout") }
            }
            Button(onClick = { starting = true; vm.startPreview() }, enabled = !state.saving && state.run?.finished != false, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (starting || state.saving) "Getting ready…" else "Let’s do this") }
        }
    }
}

@Composable fun WorkoutPlayerScreen(state: WorkoutsState, vm: WorkoutsViewModel, leave: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var voice by rememberSaveable { mutableStateOf(true) }
    var sounds by rememberSaveable { mutableStateOf(true) }
    val audio = remember(context) { WorkoutSounds(context) }
    var vibration by rememberSaveable { mutableStateOf(true) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var initialized by remember { mutableStateOf(false) }
    var voiceAvailable by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var cueSettings by remember { mutableStateOf(false) }
    val run = state.run
    val stage = run?.currentStage
    val exercise = stage?.exerciseId?.let { id -> state.exercises.firstOrNull { it.id == id } }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { vm.pause(); tts?.stop(); audio.stop() } }
        lifecycle.lifecycle.addObserver(observer)
        val speech = TextToSpeech(context) { status -> initialized = status == TextToSpeech.SUCCESS }
        tts = speech
        onDispose { lifecycle.lifecycle.removeObserver(observer); vm.pause(); speech.stop(); speech.shutdown(); audio.release(); tts = null }
    }
    LaunchedEffect(initialized, tts) {
        if (initialized) {
            val voices = tts?.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
            val preferred = voices.firstOrNull { it.locale.language == Locale.getDefault().language }
                ?: voices.firstOrNull { it.locale.language == Locale.ENGLISH.language }
            voiceAvailable = preferred != null && tts?.setVoice(preferred) == TextToSpeech.SUCCESS
        }
    }
    LaunchedEffect(run?.id, run?.stageIndex, run?.paused, run?.finished, run?.preparationSeconds, run?.remainingSeconds, sounds, audio.ready) {
        audio.stop()
        val cue = run?.audioCue()
        if (run?.paused != false && run?.finished != true) tts?.stop()
        if (cue != null) {
            tts?.stop()
            if (sounds) audio.play(cue)
            if (vibration && run?.finished != true) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    LaunchedEffect(run?.stageIndex, run?.paused, run?.finished, (run?.preparationMillis ?: 0) > 0, voice, initialized, voiceAvailable) {
        tts?.stop()
        if (run != null && !run.paused && !run.finished && run.preparationMillis == 0L && run.remainingSeconds > 3) {
            // Let the short 'go' clip finish before optional form narration.
            delay(700)
            if (voice && voiceAvailable) tts?.speak("${if (stage?.kind == StageKind.REST) "Rest and breathe" else exercise?.name.orEmpty()}. ${exercise?.cue.orEmpty()}", TextToSpeech.QUEUE_FLUSH, null, "stage-${run.stageIndex}")
        }
    }
    LaunchedEffect(run?.remainingSeconds) {
        if (run?.paused == false && run.preparationMillis == 0L && run.remainingSeconds == 20 && exercise?.instructions?.any { it.contains("halfway") } == true && voice && voiceAvailable) tts?.speak("Switch sides when ready", TextToSpeech.QUEUE_FLUSH, null, "switch")
    }
    BackHandler { vm.pause(); leave() }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if(run == null || run.finished) WorkoutHeading(if(run?.finished == true) "That’s a little win!" else "Your workout") { vm.pause(); leave() }
        else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={audio.stop();tts?.stop();vm.pause();leave()}) { Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back") }
            Text(exercise?.name ?: "Rest & breathe",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            IconButton(onClick={audio.stop();tts?.stop();vm.pause();cueSettings=true}) { Icon(Icons.Default.Tune,"Workout sound settings") }
        }
        ErrorNotice(state, vm::retrySave)
        if (run == null) { Text("No saved workout. Choose an exercise or generate a plan to begin."); Button(onClick = leave) { Text("Back to workouts") }; return@Column }
        if (run.finished) {
            CompletionCelebration()
            BuddyArt(BuddyMood.TROPHY,Modifier.size(180.dp),animate=true)
            Text(if (run.endedEarly) "Every bit of movement counts." else "Workout complete.", style = MaterialTheme.typography.titleLarge)
            Text("${clock((run.activeMillis / 1000).toInt())} movement · ${clock((run.elapsedMillis / 1000).toInt())} elapsed")
            SoftTag("${estimate(run.calories)} estimated",Icons.Default.Bolt)
            if(state.saved)SoftTag("Saved on your device",Icons.Default.CheckCircle)else Text("Not saved yet. Keep this screen open and retry.",color=MaterialTheme.colorScheme.error)
            InfoBubble("What counts?","Only completed movement earns activity credit. Skips and rest intervals earn none. Signed-in accounts queue private cloud sync; the local demo stays on-device.")
            if (run.activeMillis < 1000) Text("No movement time was recorded, so no activity entry was added.")
            Button(onClick = leave, enabled = state.saved) { Text("Back to workouts") }
            return@Column
        }
        Text("${stage?.kind?.name?.lowercase()?.replaceFirstChar(Char::uppercase)} · ${run.stageIndex + 1}/${run.plan.stages.size}", style = MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
        if (exercise != null) ExerciseDemo(exercise,Modifier.height(160.dp), playing = !run.paused && run.preparationMillis == 0L) else Icon(Icons.Default.SelfImprovement, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
        Text(if(run.preparationSeconds > 0) run.preparationSeconds.toString() else clock(run.remainingSeconds),
            modifier = Modifier.semantics { if(run.preparationSeconds > 0 || run.remainingSeconds <= 3) liveRegion = LiveRegionMode.Polite; contentDescription = if(run.preparationSeconds > 0) "Get ready. ${run.preparationSeconds}" else "${run.remainingSeconds} seconds remaining" },
            style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold,
            color = if(run.preparationSeconds > 0 || run.remainingSeconds <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        SoftTag(if(run.paused)"Paused"else if(run.preparationSeconds>0)"Get ready"else if(run.stageMillis<1000 && exercise!=null)"Go!"else if(run.remainingSeconds<=3 && exercise!=null)"Almost there!"else"Your pace. Always.",if(run.paused)Icons.Default.Pause else Icons.Default.FavoriteBorder)
        }
        LinearProgressIndicator(progress = { (run.stageMillis / ((stage?.seconds ?: 1) * 1000f)).coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (!run.paused) { audio.stop(); tts?.stop() }; vm.toggle() }, enabled = !state.saving, modifier = Modifier.weight(1f)) { Text(if (run.paused) if (run.elapsedMillis == 0L) "Start" else "Resume" else "Pause") }
            OutlinedButton(onClick = vm::skip, enabled = !state.saving, modifier = Modifier.weight(1f)) { Text("Skip interval") }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            exercise?.let{InfoBubble("Form tips",it.instructions.mapIndexed{i,tip->"${i+1}. $tip"}.joinToString("\n\n"),onOpen={audio.stop();tts?.stop();vm.pause()})}
            InfoBubble("Move safely","Stop if you feel pain, dizziness or unusual breathlessness. Rest is always an option.",Icons.Default.FavoriteBorder,onOpen={audio.stop();tts?.stop();vm.pause()})
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            TextButton(onClick={audio.stop();tts?.stop();vm.pause();leave()},enabled=!state.saving){Text("Save & leave")}
            TextButton(onClick={audio.stop();tts?.stop();vm.pause();confirmEnd=true},enabled=!state.saving){Text("End workout")}
        }
    }
    if(cueSettings) DetailPopup("Workout cues",{cueSettings=false}) {
        Text(run?.plan?.title.orEmpty(),style=MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Countdown sound", Modifier.weight(1f)); Switch(sounds, { sounds = it; if (!it) audio.stop() }, modifier = Modifier.semantics { contentDescription = "Countdown sound" }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Voice cues", Modifier.weight(1f)); Switch(voice, { voice = it; if (!it) tts?.stop() }, enabled = voiceAvailable, modifier = Modifier.semantics { contentDescription = "Voice cues" }) }
        if (!voiceAvailable) InfoBubble("Voice unavailable","Install an offline Android text-to-speech voice to use voice cues. Spoken countdowns still work without one.",onOpen=vm::pause)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Haptic cues", Modifier.weight(1f)); Switch(vibration, { vibration = it }, modifier = Modifier.semantics { contentDescription = "Haptic cues" }) }
        Button(onClick={cueSettings=false},modifier=Modifier.fillMaxWidth()){Text("Done")}
    }
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false }, title = { Text("Finish this workout early?") }, text = { Text("Only movement already completed will be saved. You can always take a break and return instead.") }, confirmButton = { TextButton(onClick = { confirmEnd = false; vm.end() }) { Text("Finish and save") } }, dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep paused") } })
}
