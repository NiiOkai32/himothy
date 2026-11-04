package com.campusfit.feature.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.campusfit.domain.*
import com.campusfit.ui.components.*
import com.campusfit.domain.social.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

fun dateUtc(millis:Long):String=DateTimeFormatter.ofPattern("dd MMM HH:mm 'UTC'",Locale.getDefault()).withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(millis))
fun metricLabel(metric:String)=when(metric){"percent"->"% body weight lost";"kilograms"->"kg lost";"calories"->"estimated kcal";else->"workout minutes"}
@Composable fun SocialNotice(state:SocialState){
 if(state.loading||state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
 if(state.cached)SoftTag("Saved view · refresh for latest",Icons.Default.CloudQueue)
 state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 state.message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}
}
@Composable fun SocialHeading(title:String,back:(()->Unit)?=null){AppHeader(title,"Play together",back)}
@Composable private fun ChoiceRow(values:List<Pair<String,String>>,selected:String,change:(String)->Unit){LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(values){(value,label)->FilterChip(selected==value,{change(value)},label={Text(label)})}}}
@Composable fun SocialSharing(profile:Profile,state:SocialState,vm:SocialViewModel){
 val me=state.summary?.me
 var handle by rememberSaveable(me?.handle){mutableStateOf(me?.handle?:"")}
 var alias by rememberSaveable(me?.alias){mutableStateOf(me?.alias?:profile.name)}
 var campus by rememberSaveable(me?.campusOptIn){mutableStateOf(me?.campusOptIn?:false)}
 var league by rememberSaveable(me?.leagueOptIn){mutableStateOf(me?.leagueOptIn?:false)}
 var phone by rememberSaveable(me?.discoverByPhone){mutableStateOf(me?.discoverByPhone?:false)}
 var consent by rememberSaveable(me?.uid){mutableStateOf(!me?.uid.isNullOrBlank())}
 Card{Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Text("Your social alias & sharing",style=MaterialTheme.typography.titleLarge)
  Text("Saving creates an exact-username directory entry and shares your alias and challenge participation with friends. Campus sharing and phone lookup are optional. Raw weights, height, age and phone number are never published.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(handle,{handle=it},label={Text("Unique username")},singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(alias,{alias=it},label={Text("Display alias")},singleLine=true,modifier=Modifier.fillMaxWidth())
  Text("Campus: ${profile.university} · self-reported affiliation",style=MaterialTheme.typography.bodySmall)
  ShareToggle("Share activity on my campus board",campus){campus=it}
  ShareToggle("Join weekly activity leagues",league){league=it}
  ShareToggle("Allow discovery by my verified phone",phone){phone=it}
  Text("Phone discovery needs a phone linked through the Friends screen. Weight-challenge preferences remain in your private profile settings.",style=MaterialTheme.typography.bodySmall)
  ConsentCheck("I agree to this social sharing",consent){consent=it}
  Button(onClick={vm.register(PublicPerson(uid=profile.uid,handle=handle,alias=alias,campusOptIn=campus,leagueOptIn=league,discoverByPhone=phone))},enabled=consent&&!state.busy){Text("Save sharing preferences")}
 }}
}
@Composable private fun ShareToggle(label:String,value:Boolean,change:(Boolean)->Unit){Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(value,change,modifier=Modifier.semanticsLabel(label))}}
private fun Modifier.semanticsLabel(label:String):Modifier=this.then(Modifier.semantics { contentDescription=label })
@Composable fun ChallengesScreen(profile:Profile,state:SocialState,vm:SocialViewModel,detail:(String)->Unit,friends:()->Unit,campus:()->Unit,league:()->Unit,badges:()->Unit){
 var settings by rememberSaveable{mutableStateOf(false)}
 var create by rememberSaveable{mutableStateOf(false)}
 var title by rememberSaveable{mutableStateOf("")};var period by rememberSaveable{mutableStateOf("weekly")};var metric by rememberSaveable{mutableStateOf("minutes")};var scope by rememberSaveable{mutableStateOf("friends")};var group by rememberSaveable{mutableStateOf("")};var teams by rememberSaveable{mutableStateOf("")};var next by rememberSaveable{mutableStateOf(true)}
 LaunchedEffect(state.openedId){state.openedId?.let{vm.clearOpened();detail(it)}}
 DisposableEffect(vm){vm.watch();onDispose{vm.stopWatching()}}
 LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  item{SocialHeading("Better together.")}
  item{HeroCard("Bring your\npeople.","A little friendly energy","Find my crew",friends,BuddyMood.TROPHY,Peach)}
  item{SocialNotice(state)}
  if(!vm.cloud)item{InfoBubble("Just exploring?","Your local achievements come from your actual saved moves. Friends, leagues and shared challenges need a signed-in online account.")}
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=friends){Text("Friends & groups")};OutlinedButton(onClick=badges){Text("Achievements")}}}
  state.summary?.game?.let{game->item{Card{Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
   Text("Level ${game.level} · ${game.xp} XP",style=MaterialTheme.typography.titleLarge)
   LinearProgressIndicator(progress={((game.xp%100)/100f).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
   SoftTag("${game.streak}${if(game.streakCapped)"+"else""}-day streak",Icons.Default.LocalFireDepartment)
   if(!game.backfillDone)Text("Older activity is being indexed. Refresh to continue building your history.",style=MaterialTheme.typography.bodySmall)
  }}}}
  item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=campus,enabled=vm.cloud&&state.summary!=null){Text("Campus board")};OutlinedButton(onClick=league,enabled=vm.cloud&&state.summary!=null){Text("Weekly league")}}}
  item{Row{TextButton(onClick=vm::refresh,enabled=!state.busy){Text("Refresh")};TextButton(onClick={settings=!settings}){Text("Social privacy")};TextButton(onClick=vm::rest,enabled=vm.cloud&&!state.busy){Text("Protect a rest day")}}}
  if(state.summary==null)item{EmptyMoment("Meet your future crew.","Set up your alias when you’re ready.",BuddyMood.TROPHY)}
  item{Text("Your challenges",style=MaterialTheme.typography.titleLarge)}
  if(state.summary!=null&&state.summary.challenges.isEmpty())item{EmptyMoment("Fresh challenge energy.","Add a friend, then pick a goal together.",BuddyMood.TROPHY)}
  items(state.summary?.challenges.orEmpty(),key={it.id}){c->OutlinedCard(onClick={detail(c.id)},modifier=Modifier.fillMaxWidth()){
   Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(c.title,fontWeight=FontWeight.Bold);Text("${c.period} · ${metricLabel(c.metric)}${if(c.teamMode)" · teams" else ""}");Text("${dateUtc(c.start)} → ${dateUtc(c.end)}",style=MaterialTheme.typography.bodySmall);Text("${c.memberUids.size}/50 joined · ${if(c.finalized)"finalized" else if(System.currentTimeMillis()<c.start)"upcoming" else if(System.currentTimeMillis()>=c.end)"ended / grace period" else "active"}",style=MaterialTheme.typography.labelSmall)}
  }}
  item{Button(onClick={create=!create},enabled=vm.cloud&&state.summary!=null){Text("Create a challenge")}}
  }
 if(settings)DetailPopup("Your social space.",{settings=false}){SocialNotice(state);SocialSharing(profile,state,vm)}
 if(create)DetailPopup("Start something fun.",{create=false},BuddyMood.TROPHY){
  LazyColumn(Modifier.heightIn(max=420.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{OutlinedTextField(title,{title=it},label={Text("Challenge title")},modifier=Modifier.fillMaxWidth())}
   item{ChoiceRow(listOf("daily" to "Daily","weekly" to "Weekly","monthly" to "Monthly"),period){period=it}}
   item{ChoiceRow(listOf("minutes" to "Minutes","calories" to "Estimated kcal","percent" to "% lost","kilograms" to "kg lost"),metric){metric=it}}
   item{ChoiceRow(listOf("friends" to "Private friends / group","campus" to "Campus activity"),scope){scope=it;group=""}}
   if(scope=="friends")item{LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){item{FilterChip(group=="",{group=""},label={Text("Accepted friends")})};items(state.summary?.groups.orEmpty()){g->FilterChip(group==g.id,{group=g.id},label={Text(g.name)})}}}
   item{ShareToggle("Use the next UTC calendar period",next){next=it};Text("Weight challenges always use the next period and require a baseline from the previous UTC day, synced before the start. Weight enrollment closes at the start.",style=MaterialTheme.typography.bodySmall)}
   item{OutlinedTextField(teams,{teams=it},label={Text("Optional teams, comma separated")},supportingText={Text("2–8 teams: hostels, departments, classes… Scores use participant averages.")},modifier=Modifier.fillMaxWidth())}
   item{Text("Activity ranks cap credit at 60 minutes/day. Weight changes above 1 kg/week or with unusual-loss flags are excluded; weight rankings award no XP or habit trophies.",style=MaterialTheme.typography.bodySmall)}
   item{Button(onClick={vm.create(title,period,metric,scope,group,teams,next)},enabled=!state.busy){Text("Create (join separately)")}}
  }
 }
}
@Composable fun ChallengeDetailScreen(id:String,profile:Profile,state:SocialState,vm:SocialViewModel,back:()->Unit){
 var consent by rememberSaveable(id){mutableStateOf(false)};var team by rememberSaveable(id){mutableStateOf("")}
 LaunchedEffect(id){vm.openBoard(id)}
 DisposableEffect(id){vm.watch(id);onDispose{vm.stopWatching()}}
 val board=state.board?.takeIf{it.challenge.id==id}
 LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{SocialHeading(board?.challenge?.title?:"Challenge",back)}
  item{SocialNotice(state);OutlinedButton(onClick={vm.openBoard(id)},enabled=!state.busy){Text("Refresh standings")}}
  if(board==null)item{Text("No current standings. Reconnect to load this challenge; weight snapshots expire after five minutes.")}
  else{
   val c=board.challenge;val weight=c.metric in listOf("percent","kilograms")
   item{Text("${c.period} · ${metricLabel(c.metric)}",style=MaterialTheme.typography.titleMedium);Text("${dateUtc(c.start)} → ${dateUtc(c.end)}",style=MaterialTheme.typography.bodySmall)}
   item{InfoBubble("How scoring works","Tied scores share ranks. Activity is self-reported, not independently verified. Uploads have a 24-hour grace period. Activity wins can earn trophies; weight results earn no XP or habit trophies. Weight snapshots expire after five minutes.",Icons.Default.EmojiEvents)}
   if(!board.joined){
    if(c.teamMode)item{ChoiceRow(c.teams.map{it to it},team){team=it}}
    if(weight)item{Row(verticalAlignment=Alignment.CenterVertically){Checkbox(consent,{consent=it});Text("I consent to sharing my ${metricLabel(c.metric)} with these participants. Raw weights stay private.",Modifier.weight(1f))};Text("Your private activity-only/percentage settings are enforced. Enrollment closes before the start; unsafe loss is not rewarded.",style=MaterialTheme.typography.bodySmall)}
    item{Button(onClick={vm.join(id,team,consent)},enabled=!state.busy&&(!weight||consent)&&(!c.teamMode||team.isNotBlank())){Text("Join this challenge")}}
   }else item{Text("You are participating${board.rows.find{it.uid==profile.uid}?.team?.takeIf{it.isNotBlank()}?.let{" for $it"}?:""}",color=MaterialTheme.colorScheme.primary);TextButton(onClick={vm.leave(id)},enabled=!state.busy){Text("Leave challenge / withdraw consent")}}
   if(board.rows.isEmpty())item{Text("No visible participants yet. Blocked accounts are hidden without changing the underlying ranks.")}
   items(board.rows,key={it.uid}){row->RankingRow(row,c.metric,row.uid==profile.uid,if(board.joined&&row.uid!=profile.uid)({vm.cheer(id,"You showed up. Nice work!",row.uid)})else null)}
   if(c.teamMode){item{Text("Team averages",style=MaterialTheme.typography.titleLarge);InfoBubble("Team scoring","All enrolled team members form the denominator, including members with no score. Teams need at least one eligible score.")};items(board.teams,key={it.uid}){row->RankingRow(row,c.metric,false)}}
   item{Text("Cheer consistency",style=MaterialTheme.typography.titleLarge)}
   if(board.joined)item{LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(state.summary?.phrases.orEmpty()){phrase->OutlinedButton(onClick={vm.cheer(id,phrase)},enabled=!state.busy){Text(phrase)}}}}
   items(board.messages,key={it.id}){m->Text("${m.alias}: ${m.phrase}",style=MaterialTheme.typography.bodyMedium)}
  }
 }
}
@Composable fun RankingRow(row:RankRow,metric:String,self:Boolean,onCheer:(()->Unit)?=null){
 Card(colors=CardDefaults.cardColors(containerColor=if(self)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)){
  Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){
   Text(row.rank?.let{"#$it"}?:"—",Modifier.width(48.dp),fontWeight=FontWeight.Bold)
   Column(Modifier.weight(1f)){Text(row.alias+if(self)" (you)" else "",fontWeight=FontWeight.Bold);if(row.team.isNotBlank())Text(row.team,style=MaterialTheme.typography.labelSmall);if(row.reason.isNotBlank())Text(row.reason,style=MaterialTheme.typography.bodySmall)}
   Column{Text(row.score?.let{"%.2f".format(Locale.getDefault(),it)}?:"Not ranked");if(onCheer!=null)IconButton(onClick=onCheer){Icon(Icons.Default.ThumbUp,"Cheer ${row.alias}")}}
  }
 }
}
@Composable fun PublicBoardScreen(kind:String,state:SocialState,vm:SocialViewModel,back:()->Unit){
 LaunchedEffect(kind){if(kind=="campus")vm.campus()else vm.league()}
 DisposableEffect(kind,state.campus?.weekStart,state.league?.id){vm.watchBoard(kind);onDispose{vm.stopBoardWatch()}}
 val rows=if(kind=="campus")state.campus?.rows.orEmpty()else state.league?.rows.orEmpty()
 LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{SocialHeading(if(kind=="campus")"Campus activity board" else "Weekly league",back);SocialNotice(state)}
  item{OutlinedButton(onClick={if(kind=="campus")vm.campus()else vm.league()},enabled=!state.busy){Text("Refresh")}}
  if(kind=="campus")item{Text(state.campus?.campus.orEmpty(),style=MaterialTheme.typography.titleLarge);InfoBubble("Campus scoring","This UTC week: self-reported workout minutes, capped at 60 minutes per day. Up to 100 opt-in aliases appear. No weights are public.")}
  else{
   item{Text("${listOf("Bronze","Silver","Gold","Diamond").getOrElse(state.league?.tier?:0){"Bronze"}} league",style=MaterialTheme.typography.titleLarge);InfoBubble("League rules","Up to 30 students per cohort. With at least 5 participants, the top 20% with activity promote and the bottom 20% relegate. Shared ranks protect ties. A 24-hour upload grace period precedes finalization.")}
   if(state.league?.optedIn!=true)item{Text("Enable weekly leagues in Social privacy, then refresh to join a cohort.")}
   if(state.league?.pendingFinalization==true)item{Text("Your previous league is awaiting the upload grace period. Your next cohort opens after finalization.")}
  }
  if(rows.isEmpty())item{Text("No standings yet. Keep building small habits; you can rest without competing.")}
  items(rows,key={it.uid}){row->RankingRow(row,"minutes",row.uid==state.summary?.me?.uid)}
 }
}
@Composable fun AchievementsScreen(state:SocialState,vm:SocialViewModel,back:()->Unit){
 val game=state.summary?.game
 val earned=game?.badges.orEmpty().toSet()
 var selected by remember{mutableStateOf<Pair<String,String>?>(null)}
 val catalog=listOf(Triple("first_move","First move","Log 2 minutes of activity."),Triple("ten_sessions","On a roll","10 sessions of at least 2 minutes."),Triple("hundred_minutes","100 club","Log 100 movement minutes over time."),Triple("early_bird","Early bird","Start a 2-minute session between 05:00 and 08:00 UTC."),Triple("seven_day_streak","Week of wins","A 7-day activity streak. A protected rest day can bridge once per UTC week."),Triple("stair_master","Stair master","Locked: stair-specific tracking isn’t available yet."))
 LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{SocialHeading("Your little wins.",back);SocialNotice(state)}
  item{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Level ${game?.level?:1}",style=MaterialTheme.typography.headlineLarge);SoftTag("${game?.xp?:0} XP",Icons.Default.Bolt)};BuddyArt(BuddyMood.TROPHY,Modifier.size(140.dp))}}
  item{InfoBubble("What earns XP?","2 XP per movement minute, plus 10 for a day with at least 2 minutes. Activity credit caps at 60 minutes per day. Levels require 100 XP. Weight loss earns no XP.")}
  items(catalog.chunked(2)){pair->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
   pair.forEach{(id,title,description)->Card(onClick={selected=title to description},modifier=Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=if(id in earned)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)){
    Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
     Icon(if(id in earned)Icons.Default.WorkspacePremium else Icons.Default.Lock,null,Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary)
     Text(title,style=MaterialTheme.typography.titleMedium);Text(if(id in earned)"Unlocked"else"Tap to explore",style=MaterialTheme.typography.labelMedium)
    }
   }}
  }}
  item{Text("Trophy shelf",style=MaterialTheme.typography.titleLarge)}
  if(state.summary?.awards.isNullOrEmpty())item{EmptyMoment("A little space for greatness.","Activity challenge wins land here.",BuddyMood.TROPHY)}
  items(state.summary?.awards.orEmpty(),key={it.id}){award->Card(onClick={selected=award.title to "Activity challenge trophy · ${dateUtc(award.at)}"},modifier=Modifier.fillMaxWidth()){ListItem(headlineContent={Text(award.title)},leadingContent={Icon(Icons.Default.EmojiEvents,null)})}}
  item{OutlinedButton(onClick=vm::refresh,enabled=!state.busy){Text("Refresh wins")}}
 }
 selected?.let{(title,description)->DetailPopup(title,{selected=null},BuddyMood.TROPHY){Text(description);Button(onClick={selected=null},modifier=Modifier.fillMaxWidth()){Text("Got it")}}}
}
