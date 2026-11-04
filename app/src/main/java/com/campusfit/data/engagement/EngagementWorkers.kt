package com.campusfit.data.engagement

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker class ReminderWorker @AssistedInject constructor(@Assisted context:Context,@Assisted params:WorkerParameters,private val repository:EngagementRepository):CoroutineWorker(context,params){
 override suspend fun doWork():Result{
  val uid=inputData.getString("uid")?:return Result.failure()
  return try{if(repository.active(uid)){repository.deliverReminder(uid);repository.plan(uid,fromWorker=true)};Result.success()}catch(e:CancellationException){throw e}catch(_:Exception){Result.retry()}
 }
}
@HiltWorker class InboxWorker @AssistedInject constructor(@Assisted context:Context,@Assisted params:WorkerParameters,private val repository:EngagementRepository):CoroutineWorker(context,params){
 override suspend fun doWork():Result{
  val uid=inputData.getString("uid")?:return Result.failure()
  return try{if(repository.active(uid))repository.refreshInbox(uid);Result.success()}catch(e:CancellationException){throw e}catch(_:Exception){Result.retry()}
 }
}
@HiltWorker class DeviceRegistrationWorker @AssistedInject constructor(@Assisted context:Context,@Assisted params:WorkerParameters,private val repository:EngagementRepository):CoroutineWorker(context,params){
 override suspend fun doWork():Result{
  val uid=inputData.getString("uid")?:return Result.failure()
  return try{if(repository.active(uid))repository.registerDevice(uid);Result.success()}catch(e:CancellationException){throw e}catch(_:Exception){Result.retry()}
 }
}
@HiltWorker class PushDeliveryWorker @AssistedInject constructor(@Assisted context:Context,@Assisted params:WorkerParameters,private val repository:EngagementRepository,private val scheduler:EngagementScheduler):CoroutineWorker(context,params){
 override suspend fun doWork():Result{
  val uid=inputData.getString("uid")?:return Result.failure();val id=inputData.getString("id")?:return Result.failure()
  if(!id.matches(Regex("[a-zA-Z0-9_-]{1,100}")))return Result.failure()
  return try{val next=repository.push(uid,id);if(next!=null)scheduler.delivery(uid,id,next,true);Result.success()}catch(e:CancellationException){throw e}catch(_:Exception){Result.retry()}
 }
}
