package com.campusfit.domain.engagement

import java.time.*

data class BusySpan(val start:Long,val end:Long) { init { require(end>start) } }
data class ReminderOptions(
 val zone:String=ZoneId.systemDefault().id,val startMinute:Int=8*60,val endMinute:Int=20*60,
 val quietStart:Int=22*60,val quietEnd:Int=8*60,val daysMask:Int=31,val minutes:Int=10,
){
 fun validate(){ZoneId.of(zone);require(startMinute in 0..1439&&endMinute in 1..1440&&endMinute>startMinute){"Use a same-day availability window with its end after its start."};require(quietStart in 0..1439&&quietEnd in 0..1439);require(daysMask in 1..127);require(minutes in 2..20)}
}
object ReminderPolicy {
 const val BUFFER=5*60_000L
 private fun at(date:LocalDate,minute:Int,zone:ZoneId)=date.atTime(LocalTime.MIDNIGHT).plusMinutes(minute.toLong()).atZone(zone).toInstant().toEpochMilli()
 fun quiet(now:Long,options:ReminderOptions):Boolean {
  if(options.quietStart==options.quietEnd)return false
  val local=Instant.ofEpochMilli(now).atZone(ZoneId.of(options.zone)).toLocalTime();val minute=local.hour*60+local.minute
  return if(options.quietStart<options.quietEnd)minute in options.quietStart until options.quietEnd else minute>=options.quietStart||minute<options.quietEnd
 }
 fun quietEnd(now:Long,options:ReminderOptions):Long {
  val zone=ZoneId.of(options.zone);val local=Instant.ofEpochMilli(now).atZone(zone);var date=local.toLocalDate()
  if(options.quietStart>options.quietEnd&&local.hour*60+local.minute>=options.quietStart)date=date.plusDays(1)
  return at(date,options.quietEnd,zone)
 }
 fun freeAt(now:Long,options:ReminderOptions,busy:List<BusySpan>):Boolean {
  options.validate();val zone=ZoneId.of(options.zone);val local=Instant.ofEpochMilli(now).atZone(zone)
  if(options.daysMask and (1 shl(local.dayOfWeek.value-1))==0||quiet(now,options))return false
  val end=now+options.minutes*60_000L
  if(now<at(local.toLocalDate(),options.startMinute,zone)||end>at(local.toLocalDate(),options.endMinute,zone))return false
  if(options.quietStart!=options.quietEnd){
   for(offset in -1L..1L){val date=local.toLocalDate().plusDays(offset);val quietStart=at(date,options.quietStart,zone);val quietEnd=at(if(options.quietStart>options.quietEnd)date.plusDays(1)else date,options.quietEnd,zone);if(now<quietEnd&&end>quietStart)return false}
  }
  return busy.none{now<it.end+BUFFER&&end>it.start-BUFFER}
 }
 fun next(now:Long,options:ReminderOptions,busy:List<BusySpan>,blockedLocalDays:Set<Long> = emptySet()):Long? {
  options.validate()
  // Five-minute granularity, seven-day bounded search; delivery always revalidates.
  var candidate=((now+BUFFER+299_999)/300_000)*300_000
  val limit=now+7*86_400_000L
  while(candidate<limit){
   if(Instant.ofEpochMilli(candidate).atZone(ZoneId.of(options.zone)).toLocalDate().toEpochDay() !in blockedLocalDays&&freeAt(candidate,options,busy))return candidate
   candidate+=300_000
  }
  return null
 }
}
data class MiniTask(val id:String,val title:String,val exerciseId:String,val minutes:Int=2)
object DailyMini {
 fun task(uid:String,epochDay:Long):MiniTask {
  val choices=listOf("A one-minute movement break" to "easy-march","30 comfortable reps — or gentle movement instead" to "bodyweight-squat","A quiet reset between lectures" to "slow-standing-march","Loosen up with a gentle side reach" to "standing-side-reach","A small strength break, at your pace" to "wall-push-up","Make room for a relaxed breathing break" to "standing-breathing","Move gently and call it a win" to "quiet-balance-shift")
  val index=Math.floorMod(epochDay+uid.hashCode().toLong(),choices.size.toLong()).toInt();val value=choices[index]
  return MiniTask("v1-$epochDay-$index",value.first,value.second)
 }
 fun eligible(seconds:Int,reps:Int)=seconds>=60||reps>=30
}
object NotificationKinds {
 val all=setOf("friend_request","friend_accept","cheer","trophy","league","challenge_join")
 fun title(kind:String)=when(kind){"friend_request"->"A friend request is waiting";"friend_accept"->"Your campus circle grew";"cheer"->"You have encouragement";"trophy"->"An activity achievement is waiting";"league"->"Your league results are ready";"challenge_join"->"Your challenge has company";else->"CampusFit update"}
 fun route(kind:String,target:String):String=when(kind){"friend_request","friend_accept"->"friends";"trophy"->"badges";"league"->"league";"cheer","challenge_join"->if(target.matches(Regex("[a-zA-Z0-9_-]{1,100}")))"challenge-detail/$target" else "challenges";else->"notifications"}
}
