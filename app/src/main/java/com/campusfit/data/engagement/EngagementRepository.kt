package com.campusfit.data.engagement

import androidx.room.withTransaction
import com.campusfit.data.local.*
import com.campusfit.domain.*
import com.campusfit.domain.engagement.*
import com.google.firebase.firestore.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.time.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

fun EngagementPreference.options()=ReminderOptions(zone,startMinute,endMinute,quietStart,quietEnd,daysMask,minutes)
@Singleton class EngagementRepository @Inject constructor(
 private val db:CampusDatabase,private val auth:AuthRepository,private val scheduler:EngagementScheduler,private val runtime:EngagementRuntime,
){
 private val dao get()=db.engagement()
 fun preferences(uid:String)=dao.preferences(uid)
 fun blocks(uid:String)=dao.blocks(uid)
 fun inbox(uid:String)=dao.inbox(uid)
 fun minis(uid:String)=dao.minis(uid)
 suspend fun active(uid:String)=runtime.uid==uid&&auth.account.value?.uid==uid&&!db.extra().deleting(uid)&&db.profiles().get(uid)!=null
 private suspend fun owner(uid:String){check(active(uid)){"Sign in to an active account first."}}
 private fun cloud(uid:String)=auth.cloudConfigured&&auth.account.value?.let{it.uid==uid&&!it.demo&&it.verified}==true
 suspend fun initialize(profile:Profile){
  owner(profile.uid)
  if(dao.preference(profile.uid)==null)dao.preference(EngagementPreference(profile.uid,zone=ZoneId.systemDefault().id,minutes=profile.minutes))
  runtime.preferences(checkNotNull(dao.preference(profile.uid)))
  plan(profile.uid)
  if(cloud(profile.uid)){scheduler.periodic(profile.uid);scheduler.inbox(profile.uid);if(dao.preference(profile.uid)?.push==true)scheduler.device(profile.uid)}
  runtime.widgets()
 }
 suspend fun save(value:EngagementPreference){
  value.options().validate()
  require(!value.reminders&&!value.push||runtime.allowed()){ "Allow Android notifications before enabling reminders or push." }
  db.withTransaction{owner(value.uid);dao.preference(value)}
  runtime.preferences(value)
  if(cloud(value.uid))scheduler.device(value.uid)
  if(!value.push)runtime.cancelAll()
  plan(value.uid);runtime.widgets()
 }
 suspend fun replaceCalendar(uid:String,text:String):Int{
  val pref=checkNotNull(dao.preference(uid));val parsed=withContext(Dispatchers.Default){TimetableParser.parse(text,pref.zone,System.currentTimeMillis())}
  db.withTransaction{owner(uid);dao.clearBlocks(uid);dao.blocks(parsed.spans.map{TimetableBlock(uid,UUID.randomUUID().toString(),it.start,it.end)});dao.preference(pref.copy(calendarUntil=parsed.validUntil))}
  plan(uid);return parsed.spans.size
 }
 suspend fun manual(uid:String,weekday:Int,start:Int,end:Int){
  require(weekday in 1..7&&start in 0..1439&&end in 1..1440&&end>start){"Use a weekday and a same-day block with end after start."}
  val pref=checkNotNull(dao.preference(uid));val zone=ZoneId.of(pref.zone);val today=LocalDate.now(zone);val blocks=buildList{
   for(offset in 0L..89L){val day=today.plusDays(offset);if(day.dayOfWeek.value==weekday){val a=day.atTime(LocalTime.MIDNIGHT).plusMinutes(start.toLong()).atZone(zone).toInstant().toEpochMilli();val b=day.atTime(LocalTime.MIDNIGHT).plusMinutes(end.toLong()).atZone(zone).withLaterOffsetAtOverlap().toInstant().toEpochMilli();add(TimetableBlock(uid,UUID.randomUUID().toString(),a,b))}}
  }
  db.withTransaction{owner(uid);dao.blocks(blocks);dao.preference(pref.copy(calendarUntil=today.plusDays(90).atStartOfDay(zone).toInstant().toEpochMilli()))};plan(uid)
 }
 suspend fun clearCalendar(uid:String){db.withTransaction{owner(uid);dao.clearBlocks(uid);dao.preference(checkNotNull(dao.preference(uid)).copy(calendarUntil=0))};plan(uid)}
 suspend fun plan(uid:String,after:Long=System.currentTimeMillis(),fromWorker:Boolean=false):Long?{
  if(!active(uid))return null
  val pref=dao.preference(uid)?:return null
  if(!pref.reminders||!runtime.allowed()||pref.calendarUntil in 1..after){scheduler.cancelReminders(uid);return null}
  val day=Instant.ofEpochMilli(after).atZone(ZoneId.of(pref.zone)).toLocalDate().toEpochDay()
  val suppressed=dao.days(uid,day-1).filter{it.rested||it.notified}.map{it.epochDay}.toSet()
  val spans=dao.futureBlocks(uid,after-ReminderPolicy.BUFFER).map{BusySpan(it.start,it.end)}
  val next=ReminderPolicy.next(after,pref.options(),spans,suppressed)
  if(next!=null)scheduler.remind(uid,next,fromWorker)else scheduler.cancelReminders(uid)
  return next
 }
 suspend fun restToday(uid:String){val pref=checkNotNull(dao.preference(uid));val day=LocalDate.now(ZoneId.of(pref.zone)).toEpochDay();db.withTransaction{owner(uid);val old=dao.days(uid,day).firstOrNull{it.epochDay==day};dao.day((old?:ReminderDay(uid,day)).copy(rested=true))};plan(uid);runtime.cancelAll()}
 suspend fun deliverReminder(uid:String):Boolean{
  if(!active(uid))return false
  val now=System.currentTimeMillis();val pref=dao.preference(uid)?:return false
  if(!pref.reminders||!runtime.allowed()||pref.calendarUntil in 1..now)return false
  val zone=ZoneId.of(pref.zone);val day=LocalDate.now(zone).toEpochDay();val marked=dao.days(uid,day).firstOrNull{it.epochDay==day}
  if(marked?.let{it.rested||it.notified}==true)return false
  val start=LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli();val end=LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
  val sessions=db.tracking().allSessions(uid).filter{it.startedAt in start until end}
  if(sessions.sumOf{it.durationSeconds}>=pref.minutes*60){db.withTransaction{if(active(uid))dao.day(ReminderDay(uid,day,notified=true))};return false}
  val busy=dao.futureBlocks(uid,now-ReminderPolicy.BUFFER).map{BusySpan(it.start,it.end)}
  if(!ReminderPolicy.freeAt(now,pref.options(),busy))return false
  val id="reminder-$day";val entry=InboxEntry(uid,id,"reminder","quick-workout",now)
  db.withTransaction{owner(uid);dao.entry(entry)}
  val posted=runtime.post(uid,id,"A little room to move","You may have ${pref.minutes} minutes free. Move gently, or take a rest day.",EngagementRuntime.REMINDERS)
  if(posted)db.withTransaction{if(active(uid)){dao.entry(entry.copy(shown=true));dao.day(ReminderDay(uid,day,notified=true))}}
  return posted
 }
 suspend fun refreshInbox(uid:String){
  owner(uid);if(!cloud(uid))return
  val collection=FirebaseFirestore.getInstance().collection("users").document(uid).collection("notifications")
  for(row in dao.pendingReads(uid)){
   try{collection.document(row.id).update("read",true).await();db.withTransaction{if(active(uid))dao.readSynced(uid,row.id)}}catch(e:FirebaseFirestoreException){if(e.code==FirebaseFirestoreException.Code.NOT_FOUND)dao.readSynced(uid,row.id)else throw e}
  }
  val page=withTimeout(30_000){collection.orderBy("createdAt",Query.Direction.DESCENDING).limit(100).get(Source.SERVER).await()}
  db.withTransaction{owner(uid);for(doc in page.documents){val kind=doc.getString("kind")?:continue;if(kind !in NotificationKinds.all)continue;val old=dao.entry(uid,doc.id);dao.entry(InboxEntry(uid,doc.id,kind,doc.getString("target").orEmpty(),doc.getLong("createdAt")?:continue,doc.getBoolean("read")==true||old?.read==true,old?.readPending==true,old?.shown==true))}}
 }
 suspend fun read(uid:String,id:String){db.withTransaction{owner(uid);dao.read(uid,id)};runtime.cancel(uid,id);if(cloud(uid))scheduler.inbox(uid)}
 suspend fun push(uid:String,id:String):Long?{
  if(!active(uid)||!cloud(uid))return null
  val pref=dao.preference(uid)?:return null;if(!pref.push||!runtime.allowed())return null
  val doc=withTimeout(30_000){FirebaseFirestore.getInstance().document("users/$uid/notifications/$id").get(Source.SERVER).await()}
  if(!doc.exists())return null
  val kind=doc.getString("kind")?:return null;if(kind !in NotificationKinds.all)return null
  val now=System.currentTimeMillis();val created=doc.getLong("createdAt")?:return null
  if(now-created !in 0..86_400_000||doc.getBoolean("read")==true)return null
  val old=dao.entry(uid,id);if(old?.shown==true||old?.read==true)return null
  val spans=dao.futureBlocks(uid,now-ReminderPolicy.BUFFER)
  val busyEnd=spans.filter{now>=it.start-ReminderPolicy.BUFFER&&now<it.end+ReminderPolicy.BUFFER}.maxOfOrNull{it.end+ReminderPolicy.BUFFER}
  if(ReminderPolicy.quiet(now,pref.options()))return ReminderPolicy.quietEnd(now,pref.options())
  if(busyEnd!=null)return busyEnd
  val entry=InboxEntry(uid,id,kind,doc.getString("target").orEmpty(),created)
  db.withTransaction{owner(uid);dao.entry(entry)}
  if(runtime.post(uid,id,NotificationKinds.title(kind),"Open CampusFit for your private update.",EngagementRuntime.GAME))db.withTransaction{if(active(uid))dao.entry(entry.copy(shown=true))}
  return null
 }
 suspend fun registerDevice(uid:String){
  owner(uid);if(!cloud(uid))return
  val enabled=dao.preference(uid)?.push==true&&runtime.allowed()
  val messaging=FirebaseMessaging.getInstance()
  messaging.isAutoInitEnabled=enabled
  val pref=checkNotNull(dao.preference(uid))
  if(enabled){
   val token=withTimeout(30_000){messaging.token.await()};owner(uid)
   val result=FirebaseFunctions.getInstance("us-central1").getHttpsCallable("notificationAction").call(mapOf("uid" to uid,"action" to "register","token" to token)).await().data as Map<*,*>
   val key=result["key"] as? String ?: error("Device registration was not confirmed.")
   db.withTransaction{owner(uid);val current=checkNotNull(dao.preference(uid));dao.preference(current.copy(deviceKey=key))}
  }else{
   if(pref.deviceKey.isNotBlank()){owner(uid);FirebaseFunctions.getInstance("us-central1").getHttpsCallable("notificationAction").call(mapOf("uid" to uid,"action" to "unregister","key" to pref.deviceKey)).await();db.withTransaction{owner(uid);val current=checkNotNull(dao.preference(uid));dao.preference(current.copy(deviceKey=""))}}
   messaging.deleteToken().await()
  }
 }
 suspend fun completeMini(uid:String):Boolean{
  val day=Math.floorDiv(System.currentTimeMillis(),86_400_000L);val task=DailyMini.task(uid,day)
  val sessions=db.tracking().allSessions(uid).filter{Math.floorDiv(it.startedAt,86_400_000L)==day}
  require(DailyMini.eligible(sessions.sumOf{it.durationSeconds},sessions.sumOf{it.reps})){"Log a minute of movement or 30 estimated reps first, then confirm completion. A gentle alternative is always valid."}
  return db.withTransaction{owner(uid);dao.mini(MiniCompletion(uid,day,task.id,System.currentTimeMillis()))!=-1L}
 }
 suspend fun claimAwards(uid:String,ids:List<String>):Boolean=db.withTransaction{owner(uid);var fresh=false;for(id in ids)if(dao.award(SeenAward(uid,id))!=-1L)fresh=true;fresh}
 suspend fun preview(uid:String){owner(uid);require(runtime.allowed()){ "Allow Android notifications first." };val id="test-${System.currentTimeMillis()}";val row=InboxEntry(uid,id,"test","notifications",System.currentTimeMillis());dao.entry(row);runtime.post(uid,id,"CampusFit notification preview","Private details stay inside the app. This preview creates no recurring reminder.",EngagementRuntime.GAME,preview=true)}
}
