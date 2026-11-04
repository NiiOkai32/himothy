package com.campusfit.feature.engagement

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.widget.RemoteViews
import com.campusfit.MainActivity
import com.campusfit.R
import com.campusfit.data.local.CampusDatabase
import com.campusfit.data.engagement.EngagementRuntime
import com.campusfit.domain.AuthRepository
import com.campusfit.domain.social.*
import com.google.gson.Gson
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import java.time.*
import java.time.format.DateTimeFormatter

@EntryPoint @InstallIn(SingletonComponent::class) interface WidgetDependencies {
 fun database():CampusDatabase
 fun auth():AuthRepository
 fun runtime():EngagementRuntime
}
class CampusWidgetProvider:AppWidgetProvider(){
 override fun onReceive(context:Context,intent:Intent){
  super.onReceive(context,intent)
  if(intent.action!=AppWidgetManager.ACTION_APPWIDGET_UPDATE)return
  val manager=AppWidgetManager.getInstance(context)
  val placeholder=RemoteViews(context.packageName,R.layout.campus_widget)
  for(id in manager.getAppWidgetIds(ComponentName(context,CampusWidgetProvider::class.java)))manager.updateAppWidget(id,placeholder)
  val pending=goAsync()
  CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{
   try{withTimeout(8000){render(context)}}catch(_:Exception){ /* The generic placeholder stays visible when encrypted data is unavailable. */ }finally{pending.finish()}
  }
 }
 private suspend fun render(context:Context){
  val deps=EntryPointAccessors.fromApplication(context,WidgetDependencies::class.java)
  val db=deps.database();val uid=deps.runtime().uid
  var text="Open CampusFit to enable widget sharing."
  if(uid!=null&&deps.auth().account.value?.uid==uid&&!db.extra().deleting(uid)&&db.profiles().get(uid)!=null&&db.engagement().preference(uid)?.widget==true){
   val now=System.currentTimeMillis();val rows=db.social().all(uid);val gson=Gson()
   val summary=rows.firstOrNull{it.resource=="summary"&&now-it.updatedAt in 0..86_400_000&&Math.floorDiv(it.updatedAt,86_400_000)==Math.floorDiv(now,86_400_000)}
   val streak=summary?.let{runCatching{gson.fromJson(it.payloadJson,SocialSummary::class.java).game.streak}.getOrNull()}
   val ranked=rows.filter{it.resource.startsWith("board:")&&now-it.updatedAt in 0..300_000}.mapNotNull{row->
    runCatching{val b=gson.fromJson(row.payloadJson,ChallengeBoard::class.java);if(b.challenge.period=="daily"&&b.challenge.metric in listOf("minutes","calories")&&now in b.challenge.start until b.challenge.end&&b.joined){b.rows.firstOrNull{it.uid==uid}?.rank?.let{it to row.updatedAt}}else null}.getOrNull()
   }.maxByOrNull{it.second}
   val stamp=DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
   text=(ranked?.let{"Daily activity rank #${it.first} · cached ${stamp.format(Instant.ofEpochMilli(it.second))}"}?:"Daily activity rank: open a daily challenge to refresh")+"\n"+(streak?.let{"Streak: $it days · cached ${stamp.format(Instant.ofEpochMilli(summary!!.updatedAt))}"}?:"Streak: open app to refresh")+"\nCached values may change. Tap to refresh."
  }
  synchronized(EngagementRuntime.lock){
   // Recheck at publication so an account change cannot expose the previous account's cache.
   if(deps.runtime().uid!=uid||!deps.runtime().widgetAllowed())text="Open CampusFit to enable widget sharing."
   val manager=AppWidgetManager.getInstance(context)
   for(id in manager.getAppWidgetIds(ComponentName(context,CampusWidgetProvider::class.java))){
    val views=RemoteViews(context.packageName,R.layout.campus_widget)
    views.setTextViewText(R.id.widget_values,text)
    val open=Intent(context,MainActivity::class.java).setAction("com.campusfit.OPEN_WIDGET").putExtra("open_screen","challenges")
    views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,91,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    manager.updateAppWidget(id,views)
   }
  }
 }
}
