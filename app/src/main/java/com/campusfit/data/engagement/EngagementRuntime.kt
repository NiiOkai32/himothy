package com.campusfit.data.engagement

import android.app.*
import android.appwidget.AppWidgetManager
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.WorkManager
import com.campusfit.MainActivity
import com.campusfit.R
import com.campusfit.feature.engagement.CampusWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes final notification posting against account changes. Stores only a session UID. */
@Singleton class EngagementRuntime @Inject constructor(@ApplicationContext private val context:Context){
 private val session=context.getSharedPreferences("engagement_session",Context.MODE_PRIVATE)
 companion object{val lock=Any();const val REMINDERS="campusfit_movement";const val GAME="campusfit_game"}
 val uid:String? get()=session.getString("uid",null)
 fun account(uid:String?){synchronized(lock){
  val previous=this.uid
  if(previous!=uid){if(previous!=null)WorkManager.getInstance(context).cancelAllWorkByTag("engagement:$previous");NotificationManagerCompat.from(context).cancelAll()}
  session.edit{if(uid==null)remove("uid")else putString("uid",uid);if(previous!=uid){putBoolean("reminders",false);putBoolean("push",false);putBoolean("widget",false)}}
  widgets()
 }}
 fun preferences(value:com.campusfit.data.local.EngagementPreference){synchronized(lock){if(uid==value.uid)session.edit{putBoolean("reminders",value.reminders);putBoolean("push",value.push);putBoolean("widget",value.widget)}}}
 fun widgetAllowed()=session.getBoolean("widget",false)
 fun channels(){
  val manager=context.getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel(REMINDERS,"Movement reminders",NotificationManager.IMPORTANCE_DEFAULT).apply{description="Optional reminders during your available gaps"})
  manager.createNotificationChannel(NotificationChannel(GAME,"Friends and activity achievements",NotificationManager.IMPORTANCE_DEFAULT).apply{description="Optional generic game updates; private details remain inside CampusFit"})
 }
 fun allowed()=NotificationManagerCompat.from(context).areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
 fun post(uid:String,id:String,title:String,body:String,channel:String,preview:Boolean=false):Boolean=synchronized(lock){
  if(this.uid!=uid||!allowed()||(!preview&&!session.getBoolean(if(channel==REMINDERS)"reminders"else "push",false)))return@synchronized false
  channels()
  val intent=Intent(context,MainActivity::class.java).setAction("com.campusfit.OPEN_INBOX").putExtra("inbox_uid",uid).putExtra("inbox_id",id)
   .setData(android.net.Uri.Builder().scheme("campusfit-internal").authority("inbox").appendPath(uid).appendPath(id).build())
  val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_campusfit).setContentTitle(title).setContentText(body)
   .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
   .setPublicVersion(NotificationCompat.Builder(context,channel).setSmallIcon(R.drawable.ic_campusfit).setContentTitle("CampusFit").setContentText("Open the app for your private update").build()).build()
  try{NotificationManagerCompat.from(context).notify("$uid:$id",0,notification);true}catch(_:SecurityException){false}
 }
 fun cancel(uid:String,id:String){NotificationManagerCompat.from(context).cancel("$uid:$id",0)}
 fun cancelAll(){NotificationManagerCompat.from(context).cancelAll()}
 fun widgets(){val ids=AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,CampusWidgetProvider::class.java));if(ids.isNotEmpty())context.sendBroadcast(Intent(context,CampusWidgetProvider::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,ids))}
}
