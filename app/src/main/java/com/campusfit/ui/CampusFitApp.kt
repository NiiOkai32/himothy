package com.campusfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.campusfit.R
import androidx.credentials.exceptions.NoCredentialException
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.campusfit.domain.*
import com.campusfit.feature.auth.*
import com.campusfit.feature.workouts.*
import com.campusfit.feature.tracking.*
import com.campusfit.feature.social.*
import com.campusfit.feature.engagement.*
import com.campusfit.ui.components.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import java.time.*
import com.campusfit.domain.social.SocialPolicy
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable fun CampusFitApp(vm: AuthViewModel = hiltViewModel(), invite: String? = null, onInviteHandled: () -> Unit = {}, openScreen:String?=null, inboxUid:String?=null, onOpenHandled:()->Unit={}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (state.loading && state.account == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    BuddyArt(modifier=Modifier.size(160.dp),animate=true)
                    Text("CampusFit", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    CircularProgressIndicator()
                }
            }
        } else if (state.account == null) AuthScreen(state, vm)
        else if (state.deletionPending) DeletionRecoveryScreen(state, vm)
        else if (!state.account!!.verified) VerificationScreen(state, vm)
        else if (state.profile == null) ProfileForm(state, vm)
        else MainGraph(state, vm, invite, onInviteHandled,openScreen,inboxUid,onOpenHandled,syncStatus)
    }
}
@Composable private fun Page(title: String, eyebrow: String, content: @Composable ColumnScope.() -> Unit) {
    val pageScroll=rememberScrollState()
    LaunchedEffect(title){pageScroll.scrollTo(0)}
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(pageScroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AppHeader(title,eyebrow)
        content()
    }
}
@Composable private fun Feedback(state: AuthUiState) {
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
}
@Composable private fun AuthScreen(state:AuthUiState,vm:AuthViewModel){
    var signup by rememberSaveable{mutableStateOf(false)}
    var form by rememberSaveable{mutableStateOf(false)}
    var email by rememberSaveable{mutableStateOf("")}
    var password by remember{mutableStateOf("")} // Passwords never enter saved instance state.
    var visible by remember{mutableStateOf(false)}
    val context=LocalContext.current
    val clientId=stringResource(R.string.default_web_client_id)
    val scope=rememberCoroutineScope()
    var googleLoading by remember{mutableStateOf(false)}
    Page("More movement.\nMore you.","CampusFit"){
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(32.dp)).background(Lime),contentAlignment=Alignment.Center){BuddyArt(modifier=Modifier.size(240.dp,210.dp),animate=true)}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SoftTag("2–20 min",Icons.Default.Timer);SoftTag("No gear",Icons.Default.Bolt)}
        Text("Little workouts. Big campus energy.",style=MaterialTheme.typography.titleMedium)
        if(!form)Feedback(state)
        Button(onClick={scope.launch{
            googleLoading=true
            try{val option=GetSignInWithGoogleOption.Builder(clientId).build();val request=GetCredentialRequest.Builder().addCredentialOption(option).build();val result=CredentialManager.create(context).getCredential(context,request);vm.google(GoogleIdTokenCredential.createFrom(result.credential.data).idToken)}
            catch(_:NoCredentialException){vm.googleError("Add a Google account on your phone, or use email.")}
            catch(_:GetCredentialCancellationException){}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(_:Exception){vm.googleError()}
            finally{googleLoading=false}
        }},enabled=vm.cloudConfigured&&clientId.isNotBlank()&&!state.loading&&!googleLoading,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){
            Text(if(googleLoading)"One moment…"else"Continue with Google")
        }
        OutlinedButton(onClick={form=true},enabled=vm.cloudConfigured&&!state.loading,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Icon(Icons.Default.MailOutline,null);Spacer(Modifier.width(8.dp));Text("Continue with email")}
        TextButton(onClick=vm::demo,enabled=!state.loading,modifier=Modifier.fillMaxWidth()){Text("Just looking? Try it out")}
        InfoBubble("The CampusFit vibe","Quick, equipment-free workouts for the gaps between lectures. Local demo needs no account and stays on-device. Real accounts use verified email or Google. Your health profile is private; social sharing is opt-in.",Icons.Default.AutoAwesome)
    }
    if(form)DetailPopup(if(signup)"Join the crew."else"Welcome back.",{form=false}){
        OutlinedTextField(email,{email=it},label={Text("Your email")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),trailingIcon={IconButton(onClick={visible=!visible}){Icon(if(visible)Icons.Default.VisibilityOff else Icons.Default.Visibility,if(visible)"Hide password"else"Show password")}},modifier=Modifier.fillMaxWidth())
        Feedback(state)
        Button(onClick={vm.submit(email,password,signup)},enabled=!state.loading,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(if(signup)"Let’s go"else"Let me in")}
        TextButton(onClick={signup=!signup},enabled=!state.loading){Text(if(signup)"Already here? Sign in"else"New here? Join us")}
        TextButton(onClick={vm.reset(email)},enabled=!state.loading){Text("Forgot password?")}
    }
}
@Composable private fun VerificationScreen(state: AuthUiState, vm: AuthViewModel) {
    Page("Check your inbox", "VERIFY EMAIL") {
        BuddyArt(BuddyMood.MAIL,Modifier.fillMaxWidth().height(180.dp),animate=true)
        Text("Tap the link we sent to ${state.account?.email.orEmpty()}.")
        Feedback(state)
        Button(onClick = vm::refresh, enabled = !state.loading) { Text("I've verified my email") }
        OutlinedButton(onClick = vm::resend, enabled = !state.loading) { Text("Resend verification") }
        TextButton(onClick = vm::signOut, enabled = !state.loading) { Text("Use another account") }
    }
}
@Composable private fun Entry(value: String, onChange: (String) -> Unit, label: String, numeric: Boolean = false) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text), modifier = Modifier.fillMaxWidth())
}
@Composable private fun ProfileForm(state: AuthUiState, vm: AuthViewModel, onDone: (() -> Unit)? = null) {
    val p=state.profile
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(p?.name ?: "") }
    var age by rememberSaveable { mutableStateOf(p?.age?.toString() ?: "") }
    var height by rememberSaveable { mutableStateOf(p?.heightCm?.toString() ?: "") }
    var weight by rememberSaveable { mutableStateOf(p?.weightKg?.toString() ?: "") }
    var goal by rememberSaveable { mutableStateOf(p?.goalKg?.toString() ?: "") }
    var university by rememberSaveable { mutableStateOf(p?.university ?: "") }
    var group by rememberSaveable { mutableStateOf(p?.group ?: "") }
    var level by rememberSaveable { mutableStateOf(p?.level ?: "Beginner") }
    var minutes by rememberSaveable { mutableIntStateOf(p?.minutes ?: 10) }
    var activityOnly by rememberSaveable { mutableStateOf(p?.activityOnly ?: true) }
    var percentage by rememberSaveable { mutableStateOf(p?.percentageRanking ?: true) }
    var acknowledged by rememberSaveable { mutableStateOf(p?.healthAcknowledged ?: false) }
    var concern by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var stepError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.loading,state.error,state.profile){if(saving&&!state.loading){if(state.error==null&&state.profile!=null)onDone?.invoke();saving=false}}
    androidx.activity.compose.BackHandler(step>0){step--;stepError=null}
    Page(listOf("Make it yours.","Find your pace.","Your campus crew.")[step],if(p==null)"A quick hello · ${step+1}/3" else "Edit profile · ${step+1}/3"){
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){repeat(3){index->LinearProgressIndicator(progress={if(index<=step)1f else 0f},modifier=Modifier.weight(1f).height(5.dp))}}
        when(step){
            0->{
                BuddyArt(BuddyMood.MOVE,Modifier.fillMaxWidth().height(140.dp))
                Entry(name,{name=it},"What should we call you?")
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(Modifier.weight(1f)){Entry(age,{age=it},"Age · 18+",true)}
                    Box(Modifier.weight(1f)){Entry(height,{height=it},"Height · cm",true)}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Box(Modifier.weight(1f)){Entry(weight,{weight=it},"Weight · kg",true)}
                    Box(Modifier.weight(1f)){Entry(goal,{goal=it},"Goal · kg",true)}
                }
                InfoBubble("Just between us", "Your health values stay in your encrypted device database and private cloud profile. They are not a public campus profile. Goals below BMI 18.5 are blocked. Weight rankings are optional.",Icons.Default.Lock)
            }
            1->{
                BuddyArt(BuddyMood.REST,Modifier.fillMaxWidth().height(130.dp))
                Text("$minutes minutes a day",style=MaterialTheme.typography.headlineMedium)
                Slider(minutes.toFloat(),{minutes=it.toInt()},valueRange=2f..20f,steps=17)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Beginner","Intermediate","Advanced").forEach{item->FilterChip(level==item,{level=item},label={Text(item,style=MaterialTheme.typography.labelMedium)})}}
                Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("Feel good first",style=MaterialTheme.typography.titleMedium)
                    Text("Stop for pain or dizziness. Rest days count as taking care of yourself.")
                    InfoBubble("Read the health guide","CampusFit is not medical advice. Aim for gradual weight change, around 0.5–1 kg per week at most. Goals below BMI 18.5 are blocked; BMI is a screening tool, not a diagnosis. Pain, dizziness, injury, a heart condition, pregnancy or other concerns are reasons to speak with a qualified health professional before starting. Choose comfortable movements and stop if unwell.",Icons.Default.FavoriteBorder)
                    Toggle("Need a gentler start?","Injury, pregnancy, pain or another health concern",concern){concern=it}
                    if(concern)Text("Check with a qualified health professional first.",color=MaterialTheme.colorScheme.primary)
                    ConsentCheck("I’ve read and understand the health guide",acknowledged){acknowledged=it}
                }}
                val current=weight.toDoubleOrNull();val target=goal.toDoubleOrNull()
                if(current!=null&&target!=null&&current.isFinite()&&target.isFinite()&&current>target)InfoBubble("A gradual goal","At 0.5 kg per week, this change would take about ${HealthPolicy.suggestedWeeks(current,target)} weeks. This is guidance, not a deadline; rapid loss is not rewarded.")
            }
            else->{
                BuddyArt(BuddyMood.TROPHY,Modifier.fillMaxWidth().height(120.dp))
                Entry(university,{university=it},"University")
                Entry(group,{group=it},"Campus / department · optional")
                Toggle("Activity-only competition","Keep weight out of shared rankings",activityOnly){activityOnly=it}
                if(!activityOnly)Toggle("Use percentage change","Share % change instead of kilograms",percentage){percentage=it}
                InfoBubble("You choose what’s shared","Activity-only is the default. Weight competition needs a separate opt-in and challenge consent. Percentage rankings share relative change; kilogram rankings share change in kg. Raw weights stay private. Campus affiliation is self-reported.",Icons.Default.PrivacyTip)
            }
        }
        Feedback(state)
        stepError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={
            stepError=null
            if(step==0){
                val preview=Profile(state.account!!.uid,name.trim(),age.toIntOrNull()?:0,height.toDoubleOrNull()?:0.0,weight.toDoubleOrNull()?:0.0,goal.toDoubleOrNull()?:0.0,level,minutes,"Campus",group,activityOnly,percentage,true)
                stepError=HealthPolicy.validate(preview)
                if(stepError==null)step++
            }else if(step==1){if(!acknowledged)stepError="Read and acknowledge the health guide to continue."else step++}
            else{val profile=Profile(state.account!!.uid,name.trim(),age.toIntOrNull()?:0,height.toDoubleOrNull()?:0.0,weight.toDoubleOrNull()?:0.0,goal.toDoubleOrNull()?:0.0,level,minutes,university.trim(),group.trim(),activityOnly,percentage,acknowledged);saving=true;vm.save(profile)}
        },enabled=!state.loading,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){
            Text(if(step<2)"Next" else if(p==null)"Let’s move" else "Save changes");Spacer(Modifier.width(8.dp));Icon(Icons.AutoMirrored.Filled.ArrowForward,null)
        }
        Row{if(step>0)TextButton(onClick={step--;stepError=null}){Text("Back")};TextButton(onClick={onDone?.invoke()?:vm.signOut()},enabled=!state.loading){Text(if(onDone!=null)"Cancel"else"Sign out")}}
        if(p==null)TextButton(onClick=vm::retry,enabled=!state.loading){Text("Already set up? Retry profile")}
    }
}
@Composable private fun Toggle(title: String, detail: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(detail, style = MaterialTheme.typography.bodySmall) }
        Switch(value, onChange)
    }
}
@Composable private fun MainGraph(state: AuthUiState, vm: AuthViewModel, invite: String?, onInviteHandled: () -> Unit,openScreen:String?,inboxUid:String?,onOpenHandled:()->Unit,syncStatus:String?) {
    val workouts: WorkoutsViewModel = hiltViewModel(key = "workouts:${state.account!!.uid}")
    val workoutState by workouts.state.collectAsStateWithLifecycle()
    val social: SocialViewModel = hiltViewModel(key = "social:${state.account!!.uid}")
    val socialState by social.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.profile) { social.load(state.profile!!) }
    DisposableEffect(social) { onDispose { social.stopWatching(); social.stopBoardWatch() } }
    val tracking: TrackingViewModel = hiltViewModel(key = "tracking:${state.account!!.uid}")
    val trackingState by tracking.state.collectAsStateWithLifecycle()
    val latestProfile = state.profile!!.copy(weightKg = trackingState.weights.lastOrNull()?.weightKg ?: state.profile!!.weightKg)
    LaunchedEffect(state.profile) { tracking.load(state.profile!!) }
    DisposableEffect(tracking) { onDispose { tracking.pauseOutdoor() } }
    LaunchedEffect(latestProfile) { workouts.load(latestProfile) }
    DisposableEffect(workouts) { onDispose { workouts.pause() } }
    val engagement:EngagementViewModel=hiltViewModel(key="engagement:${state.account!!.uid}")
    val engagementState by engagement.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.profile){engagement.load(state.profile!!)}
    DisposableEffect(engagement){onDispose{engagement.stopWatching()}}
    val nav = rememberNavController()
    LaunchedEffect(openScreen,inboxUid){if(openScreen in listOf("notifications","challenges")&&(inboxUid==null||inboxUid==state.account!!.uid)){nav.navigate(openScreen!!){launchSingleTop=true}};if(openScreen!=null)onOpenHandled()}
    var pendingFriend by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(invite) { invite?.let { raw ->
        try { pendingFriend = SocialPolicy.friendCode(raw); nav.navigate("friends") { launchSingleTop = true } }
        catch (_: Exception) { social.info("That is not a valid CampusFit friend link."); onInviteHandled() }
    } }
    pendingFriend?.takeIf { socialState.summary != null && social.cloud }?.let { handle -> AlertDialog(onDismissRequest = { pendingFriend = null; onInviteHandled() }, title = { Text("Add @$handle?") }, text = { Text("Send a friend request only if you recognize this username. The other person must accept. An alias is required first.") }, confirmButton = { TextButton(onClick = { pendingFriend = null; onInviteHandled(); social.friend(handle,"request") }, enabled = social.cloud && socialState.summary != null) { Text("Send request") } }, dismissButton = { TextButton(onClick = { pendingFriend = null; onInviteHandled() }) { Text("Cancel") } }) }

    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route
    val destinations = listOf("home" to Icons.Default.Home, "workouts" to Icons.Default.FitnessCenter, "progress" to Icons.Default.Insights, "challenges" to Icons.Default.EmojiEvents, "profile" to Icons.Default.Person)
    Scaffold(bottomBar = {
        if (route !in listOf("player", "gps", "camera", "scan-friend")) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) { destinations.forEach { (name, icon) -> NavigationBarItem(selected = route == name || (name == "challenges" && (route?.startsWith("challenge-detail") == true || route in listOf("friends", "badges", "campus-board", "league"))) || (name == "workouts" && (route?.startsWith("workout-detail") == true || route == "quick-workout")), onClick = { nav.navigate(name) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(icon, name) }, label = { Text(when(name){"workouts"->"Move";"progress"->"You";"challenges"->"Play";else->name.replaceFirstChar(Char::uppercase)}, style = MaterialTheme.typography.labelSmall) }) } }
    }) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            NavHost(nav, startDestination = "home") {
                composable("home") { Dashboard(state,engagementState,engagement,socialState.summary?.game?.streak,trackingState.sessions.filter{Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()==LocalDate.now()}.sumOf{it.durationSeconds},syncStatus) { if (it == "quick-workout") workouts.clearPreview(); nav.navigate(it) } }
                composable("workouts") { WorkoutLibraryScreen(workoutState, latestProfile, { workouts.load(state.profile!!) }, { nav.navigate("workout-detail/$it") }, { workouts.clearPreview(); nav.navigate("quick-workout") }, { nav.navigate("player") }) }
                composable("workout-detail/{exerciseId}") { entry -> WorkoutDetailScreen(entry.arguments?.getString("exerciseId").orEmpty(), workoutState, latestProfile, workouts, { nav.popBackStack() }, { nav.navigate("workout-detail/$it") }, { nav.navigate("quick-workout") }, { nav.navigate("gps") }) }
                composable("quick-workout") { QuickWorkoutScreen(workoutState, latestProfile, workouts, { nav.popBackStack() }, { nav.navigate("player") }) }
                composable("player") { WorkoutPlayerScreen(workoutState, workouts) { nav.navigate("workouts") { popUpTo("home"); launchSingleTop = true } } }
                composable("progress") { ProgressScreen(trackingState, state.profile!!, tracking, { nav.navigate("gps") }, { nav.navigate("camera") }) }
                composable("gps") { OutdoorScreen(trackingState, tracking) { nav.navigate("progress") { popUpTo("home"); launchSingleTop = true } } }
                composable("camera") { CameraScreen(trackingState, tracking) { nav.navigate("progress") { popUpTo("home"); launchSingleTop = true } } }
                composable("challenges") { ChallengesScreen(state.profile!!,socialState,social,{nav.navigate("challenge-detail/$it")},{nav.navigate("friends")},{nav.navigate("campus-board")},{nav.navigate("league")},{nav.navigate("badges")}) }
                composable("challenge-detail/{id}") { entry -> ChallengeDetailScreen(entry.arguments?.getString("id").orEmpty(),state.profile!!,socialState,social,{nav.popBackStack()}) }
                composable("friends") { FriendsScreen(state.profile!!,socialState,social,{nav.navigate("scan-friend")},{nav.popBackStack()}) }
                composable("scan-friend") { ScanFriendScreen(social,{nav.popBackStack()}) }
                composable("campus-board") { PublicBoardScreen("campus",socialState,social,{nav.popBackStack()}) }
                composable("league") { PublicBoardScreen("league",socialState,social,{nav.popBackStack()}) }
                composable("badges") { LaunchedEffect(socialState.summary?.awards){engagement.awards(socialState.summary?.awards.orEmpty().map{it.id})}; AchievementsScreen(socialState,social,{nav.popBackStack()}) }
                composable("profile") { Settings(state, vm) { nav.navigate("edit-profile") } }
                composable("edit-profile") { ProfileForm(state, vm) { nav.popBackStack() } }
                composable("notifications") { EngagementScreen(engagementState,engagement,{nav.popBackStack()}){nav.navigate(it){launchSingleTop=true}} }

            }
            EngagementCelebration(engagementState,engagement)
        }
    }
}
@Composable private fun Dashboard(state:AuthUiState,engagementState:EngagementState,engagement:EngagementViewModel,streak:Int?,movement:Int,syncStatus:String?,navigate:(String)->Unit){
    val p=state.profile!!
    var resting by rememberSaveable(LocalDate.now().toEpochDay()){mutableStateOf(false)}
    LaunchedEffect(engagementState.message){if(engagementState.message?.startsWith("Movement reminders paused")==true)resting=true}
    var restPopup by rememberSaveable{mutableStateOf(false)}
    val goal=engagementState.preference?.minutes?:p.minutes
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        AppHeader("Hey, ${p.name.substringBefore(' ')}!","A little move. A little mood boost.",trailing={FilledTonalIconButton(onClick={navigate("notifications")}){Icon(Icons.Default.NotificationsNone,"Reminders and inbox")}})
        if(state.account!!.demo)SoftTag("Just exploring · local demo",Icons.Default.CloudOff)
        HeroCard(if(resting)"Recharge\nmode." else "Got $goal\nminutes?",if(resting)"Rest is part of the plan" else "Between lectures?",if(resting)"Pick a gentle move" else "Let’s move",{navigate("quick-workout")},if(resting)BuddyMood.REST else BuddyMood.MOVE)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            StatTile(streak?.let{"$it days"}?:"—","Streak",Icons.Default.LocalFireDepartment,Modifier.weight(1f))
            StatTile("${movement/60}/$goal min","Today",Icons.Default.Bolt,Modifier.weight(1f))
        }
        engagementState.message?.let{Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)}
        engagementState.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        MiniChallengeCard(engagementState,engagement,navigate)
        Text("Make it your kind of day",style=MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            ActionTile("Find your crew",Icons.Default.People,{navigate("friends")},Modifier.weight(1f))
            ActionTile("Your wins",Icons.Default.EmojiEvents,{navigate("badges")},Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            ActionTile("Campus play",Icons.Default.School,{navigate("challenges")},Modifier.weight(1f),p.university)
            ActionTile("Take a breather",Icons.Default.SelfImprovement,{restPopup=true},Modifier.weight(1f),if(resting)"Rest mode on"else"Pause reminders")
        }
        syncStatus?.let{InfoBubble(if(it.contains("failed",true))"Profile sync needs attention"else"Profile sync",it,Icons.Default.CloudSync)}
    }
    if(restPopup)DetailPopup("Recharge, guilt-free.",{restPopup=false},BuddyMood.REST){
        Text("Pause movement reminders for today in your selected time zone. No activity credit is added. Come back whenever you’re ready.")
        Button(onClick={engagement.rest();restPopup=false},enabled=!engagementState.busy,modifier=Modifier.fillMaxWidth()){Text("Rest today")}
        TextButton(onClick={restPopup=false}){Text("Keep my reminders")}
    }
}
@Composable private fun Settings(state:AuthUiState,vm:AuthViewModel,edit:()->Unit){
    var confirm by remember{mutableStateOf(false)}
    val sync by vm.syncStatus.collectAsStateWithLifecycle()
    Page("Your space.","Profile & preferences"){
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
            Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(state.profile!!.name,style=MaterialTheme.typography.titleLarge);Text(state.account!!.email?:"Local explorer",style=MaterialTheme.typography.bodySmall)}
                BuddyArt(BuddyMood.REST,Modifier.size(90.dp))
            }
        }
        Feedback(state)
        ActionTile("Edit your profile",Icons.Default.Tune,edit,Modifier.fillMaxWidth(),"Your pace, your privacy")
        InfoBubble("Your data, explained","Health values are encrypted on this device. Cloud profiles are private to your verified account. Signing out keeps your encrypted device copy. Account deletion erases it after cloud deletion is confirmed. Device light/dark mode is followed automatically.",Icons.Default.Lock)
        sync?.let{InfoBubble("Profile sync",it,Icons.Default.CloudSync)}
        OutlinedButton(onClick=vm::signOut,enabled=!state.loading,modifier=Modifier.fillMaxWidth()){Text("Sign out")}
        TextButton(onClick={confirm=true},enabled=!state.loading){Text("Delete account",color=MaterialTheme.colorScheme.error)}
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},icon={Icon(Icons.Default.DeleteOutline,null)},title={Text("Delete your account?")},text={Text("Permanently remove your account and its data. Cloud deletion needs an internet connection, deployed backend and recent sign-in. This cannot be undone.")},confirmButton={TextButton(onClick={confirm=false;vm.delete()}){Text("Delete permanently")}},dismissButton={TextButton(onClick={confirm=false}){Text("Keep my account")}})
}

@Composable private fun DeletionRecoveryScreen(state: AuthUiState, vm: AuthViewModel) {
    Page("Finish removing your account", "PRIVACY") {
        Text("Account deletion was requested but has not been confirmed. New health writes are paused. Retry online after a recent sign-in.")
        Feedback(state)
        Button(onClick = vm::delete, enabled = !state.loading) { Text("Retry cloud deletion") }
        Text("Removing the device copy clears local health records and signs out. It does not confirm cloud deletion; sign in again to retry it.")
        OutlinedButton(onClick = vm::clearDeviceData, enabled = !state.loading) { Text("Remove device copy and sign out") }
        TextButton(onClick = vm::signOut, enabled = !state.loading) { Text("Sign out to sign in again") }
    }
}
