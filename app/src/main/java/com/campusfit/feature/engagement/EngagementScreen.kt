package com.campusfit.feature.engagement

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.campusfit.ui.components.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campusfit.data.local.EngagementPreference
import com.campusfit.domain.engagement.NotificationKinds
import com.campusfit.feature.workouts.CompletionCelebration
import java.time.*

private fun minute(text:String):Int?=runCatching{val t=LocalTime.parse(text);t.hour*60+t.minute}.getOrNull()
private fun clock(n:Int)="%02d:%02d".format(n/60,n%60)
@Composable fun MiniChallengeCard(state:EngagementState,vm:EngagementViewModel,navigate:(String)->Unit){
 Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){Text("DAILY SIDE QUEST",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary);Text(if(state.completed)"Quest complete!" else state.task?.title?:"A little movement break",style=MaterialTheme.typography.titleLarge)}
    BuddyArt(if(state.completed)BuddyMood.TROPHY else BuddyMood.MOVE,Modifier.size(80.dp))
   }
   if(state.completed)SoftTag("Nicely done",Icons.Default.CheckCircle)else{
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
     Button(onClick={state.task?.let{navigate("workout-detail/${it.exerciseId}")}},enabled=state.task!=null){Text("Try it")}
     if(state.eligible)OutlinedButton(onClick=vm::complete,enabled=!state.busy){Text("I did it!")}
    }
    if(!state.eligible)Text("Log 1 minute or 30 estimated reps to check it off.",style=MaterialTheme.typography.labelMedium)
   }
   InfoBubble("Quest rules","A two-minute idea, at your pace. Gentle alternatives count. After a logged minute of movement or 30 estimated camera reps, confirm your completion. This is self-reported and adds no extra XP. A new quest arrives each UTC day.")
  }
 }
}
@Composable fun EngagementScreen(state:EngagementState,vm:EngagementViewModel,back:()->Unit,navigate:(String)->Unit){
 val context=LocalContext.current
 var pending by remember{mutableStateOf<EngagementPreference?>(null)}
 var preferences by rememberSaveable{mutableStateOf(false)}
 var timetable by rememberSaveable{mutableStateOf(false)}
 var schedule by rememberSaveable{mutableStateOf(false)}
 var manual by rememberSaveable{mutableStateOf(false)}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)pending?.let(vm::save);pending=null}
 val calendar=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(vm::importCalendar)}
 fun save(value:EngagementPreference){if((value.reminders||value.push)&&Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pending=value;permission.launch(Manifest.permission.POST_NOTIFICATIONS)}else vm.save(value)}
 @Composable fun feedback(){if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth());state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};state.message?.let{Text(it,color=MaterialTheme.colorScheme.primary)}}
 Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  AppHeader("Good little nudges.","Reminders & inbox",back,trailing={FilledTonalIconButton(onClick={preferences=true}){Icon(Icons.Default.Tune,"Notification preferences")}})
  Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
   ActionTile("My reminders",Icons.Default.NotificationsActive,{preferences=true},Modifier.weight(1f),if(state.preference?.reminders==true)"Switched on"else"Your call")
   ActionTile("My timetable",Icons.Default.CalendarMonth,{timetable=true},Modifier.weight(1f),if(state.preference?.calendarUntil?.let{it in 1..System.currentTimeMillis()}==true)"Refresh needed"else"${state.blocks} busy slots")
  }
  feedback()
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Your inbox",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconButton(onClick=vm::refresh,enabled=!state.busy){Icon(Icons.Default.Refresh,"Refresh inbox")}}
  if(state.inbox.isEmpty())EmptyMoment("All quiet here.","Your next nudge will land here.",BuddyMood.MAIL)
  state.inbox.forEach{entry->Card(onClick={vm.read(entry);navigate(if(entry.kind=="reminder")"quick-workout"else NotificationKinds.route(entry.kind,entry.target))},modifier=Modifier.fillMaxWidth()){
   ListItem(headlineContent={Text(when(entry.kind){"reminder"->"A little room to move";"test"->"Your notification preview";else->NotificationKinds.title(entry.kind)})},supportingContent={Text(Instant.ofEpochMilli(entry.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toString())},leadingContent={Icon(if(entry.read)Icons.Default.Drafts else Icons.Default.MarkEmailUnread,null)},trailingContent={if(!entry.read)Icon(Icons.Default.Circle,"Unread",Modifier.size(9.dp),tint=MaterialTheme.colorScheme.primary)})
  }}
 }
 if(preferences)DetailPopup("Nudges, your way.",{preferences=false},BuddyMood.MAIL){
  feedback()
  state.preference?.let{p->
   PreferenceSwitch("Movement reminders",p.reminders,!state.busy){save(p.copy(reminders=it))}
   PreferenceSwitch("Friends & wins",p.push,vm.cloud&&!state.busy){save(p.copy(push=it))}
   if(!vm.cloud)Text("Online updates need a signed-in account.",style=MaterialTheme.typography.bodySmall)
   Text("Widget sharing makes your cached rank and streak visible to anyone using this device.",style=MaterialTheme.typography.bodySmall)
   PreferenceSwitch("Share on my home widget",p.widget,!state.busy){save(p.copy(widget=it))}
   if(p.widget)OutlinedButton(onClick={val manager=AppWidgetManager.getInstance(context);if(manager.isRequestPinAppWidgetSupported)manager.requestPinAppWidget(ComponentName(context,CampusWidgetProvider::class.java),null,null)}){Icon(Icons.Default.Widgets,null);Text(" Add widget")}
   Button(onClick={schedule=true},modifier=Modifier.fillMaxWidth()){Text("Choose my free hours")}
   OutlinedButton(onClick=vm::rest,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Pause reminders today")}
   Row{TextButton(onClick=vm::preview,enabled=!state.busy){Text("Send a preview")};TextButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("Phone settings")}}
   InfoBubble("How nudges work","Everything is opt-in. Android schedules reminders approximately and may delay them. Delivery checks your free gap, classes, quiet hours, daily goal and rest choice. Push text stays generic. Widget values are labelled cached; open the app to refresh them. No weights or peer names appear there.")
  }
 }
 if(schedule)DetailPopup("Find my free gap.",{schedule=false},BuddyMood.REST){feedback();state.preference?.let{ReminderForm(it,vm)};Button(onClick={schedule=false},modifier=Modifier.fillMaxWidth()){Text("Done")}}
 if(timetable)DetailPopup("Between lectures.",{timetable=false}){
  feedback()
  SoftTag("${state.blocks} busy slots saved",Icons.Default.CalendarMonth)
  state.preference?.let{p->if(p.calendarUntil>0)Text("Coverage until ${Instant.ofEpochMilli(p.calendarUntil).atZone(ZoneId.of(p.zone)).toLocalDate()}",style=MaterialTheme.typography.bodySmall)}
  Button(onClick={calendar.launch(arrayOf("text/calendar","application/octet-stream","text/plain"))},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileUpload,null);Text(" Import timetable (.ics)")}
  OutlinedButton(onClick={manual=true},modifier=Modifier.fillMaxWidth()){Text("Add a weekly class")}
  InfoBubble("Private by design","Only busy times stay in your encrypted device database; calendar titles and the imported file are discarded. We leave five minutes around classes. Timetables cover 90 days; reminders pause when coverage expires. Import again when your classes change. Removing a timetable uses only your chosen availability window.",Icons.Default.Lock)
  TextButton(onClick=vm::clearCalendar,enabled=!state.busy){Text("Remove timetable")}
 }
 if(manual)DetailPopup("Add a busy block.",{manual=false}){feedback();ManualBlockForm(vm,state.busy);Button(onClick={manual=false},modifier=Modifier.fillMaxWidth()){Text("Done")}}
}
@Composable private fun PreferenceSwitch(title:String,value:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(title,Modifier.weight(1f));Switch(value,onChange,enabled=enabled)}}
@Composable private fun ReminderForm(p:EngagementPreference,vm:EngagementViewModel){
 var start by rememberSaveable(p.startMinute){mutableStateOf(clock(p.startMinute))};var end by rememberSaveable(p.endMinute){mutableStateOf(if(p.endMinute==1440)"23:59" else clock(p.endMinute))}
 var qs by rememberSaveable(p.quietStart){mutableStateOf(clock(p.quietStart))};var qe by rememberSaveable(p.quietEnd){mutableStateOf(clock(p.quietEnd))};var zone by rememberSaveable(p.zone){mutableStateOf(p.zone)};var days by rememberSaveable(p.daysMask){mutableIntStateOf(p.daysMask)};var duration by rememberSaveable(p.minutes){mutableFloatStateOf(p.minutes.toFloat())}
 Text("Availability and quiet hours",style=MaterialTheme.typography.titleLarge)
 OutlinedTextField(zone,{zone=it},label={Text("IANA time zone, e.g. Africa/Accra")},singleLine=true,modifier=Modifier.fillMaxWidth())
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(start,{start=it},label={Text("Free from · HH:mm")},modifier=Modifier.weight(1f));OutlinedTextField(end,{end=it},label={Text("Until · HH:mm")},modifier=Modifier.weight(1f))}
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(qs,{qs=it},label={Text("Quiet from · HH:mm")},modifier=Modifier.weight(1f));OutlinedTextField(qe,{qe=it},label={Text("Until · HH:mm")},modifier=Modifier.weight(1f))}
 InfoBubble("Quiet hours explained","Equal quiet times switch quiet hours off. Reminders need an uninterrupted gap inside your availability window. The selected time zone applies to reminder and rest days.")
 Row{listOf("M","T","W","T","F","S","S").forEachIndexed{i,label->FilterChip(selected=days and (1 shl i)!=0,onClick={days=days xor (1 shl i)},label={Text(label)},modifier=Modifier.weight(1f).semantics{contentDescription=DayOfWeek.of(i+1).name.lowercase().replaceFirstChar(Char::uppercase)})}}
 Text("Movement goal and required gap: ${duration.toInt()} minutes");Slider(duration,{duration=it},valueRange=2f..20f,steps=17)
 val a=minute(start);val b=minute(end);val c=minute(qs);val d=minute(qe)
 Button(onClick={vm.save(p.copy(zone=zone.trim(),startMinute=a!!,endMinute=b!!,quietStart=c!!,quietEnd=d!!,daysMask=days,minutes=duration.toInt()))},enabled=a!=null&&b!=null&&b>a&&c!=null&&d!=null&&days!=0&&runCatching{ZoneId.of(zone.trim())}.isSuccess){Text("Save schedule")}
}
@Composable private fun ManualBlockForm(vm:EngagementViewModel,busy:Boolean){
 var day by rememberSaveable{mutableFloatStateOf(1f)};var start by rememberSaveable{mutableStateOf("09:00")};var end by rememberSaveable{mutableStateOf("10:00")}
 Text("Weekly busy time · ${DayOfWeek.of(day.toInt())}");Slider(day,{day=it},valueRange=1f..7f,steps=5)
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(start,{start=it},label={Text("Class starts · HH:mm")},modifier=Modifier.weight(1f));OutlinedTextField(end,{end=it},label={Text("Ends · HH:mm")},modifier=Modifier.weight(1f))}
 val a=minute(start);val b=minute(end)
 OutlinedButton(onClick={vm.manual(day.toInt(),a!!,b!!)},enabled=!busy&&a!=null&&b!=null&&b>a){Text("Add weekly busy time")}
}
@Composable fun EngagementCelebration(state:EngagementState,vm:EngagementViewModel){
 if(state.celebrate)AlertDialog(onDismissRequest=vm::stopCelebration,title={Text("Little win. Big mood.")},text={Column(horizontalAlignment=Alignment.CenterHorizontally){BuddyArt(BuddyMood.TROPHY,Modifier.size(170.dp),animate=true);CompletionCelebration();Text("You showed up. That’s worth a moment.")}},confirmButton={TextButton(onClick=vm::stopCelebration){Text("Niiice!")}})
}
