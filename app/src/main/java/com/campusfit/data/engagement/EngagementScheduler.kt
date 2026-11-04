package com.campusfit.data.engagement

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class EngagementScheduler @Inject constructor(@ApplicationContext private val context:Context){
 private fun manager()=WorkManager.getInstance(context)
 fun remind(uid:String,at:Long,reschedule:Boolean=false){
  val work=OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("uid" to uid)).setInitialDelay((at-System.currentTimeMillis()).coerceAtLeast(0),TimeUnit.MILLISECONDS).addTag("engagement:$uid").build()
  manager().enqueueUniqueWork("reminder:$uid",if(reschedule)ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,work)
 }
 fun cancelReminders(uid:String){manager().cancelUniqueWork("reminder:$uid")}
 fun inbox(uid:String){
  val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
  val work=OneTimeWorkRequestBuilder<InboxWorker>().setInputData(workDataOf("uid" to uid)).setConstraints(constraints).addTag("engagement:$uid").build()
  manager().enqueueUniqueWork("inbox:$uid",ExistingWorkPolicy.APPEND_OR_REPLACE,work)
 }
 fun delivery(uid:String,id:String,at:Long=System.currentTimeMillis(),reschedule:Boolean=false){
  val work=OneTimeWorkRequestBuilder<PushDeliveryWorker>().setInputData(workDataOf("uid" to uid,"id" to id)).setInitialDelay((at-System.currentTimeMillis()).coerceAtLeast(0),TimeUnit.MILLISECONDS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag("engagement:$uid").build()
  manager().enqueueUniqueWork("push:$uid:$id",if(reschedule)ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,work)
 }
 fun device(uid:String){
  val work=OneTimeWorkRequestBuilder<DeviceRegistrationWorker>().setInputData(workDataOf("uid" to uid)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag("engagement:$uid").build()
  manager().enqueueUniqueWork("device:$uid",ExistingWorkPolicy.APPEND_OR_REPLACE,work)
 }
 fun periodic(uid:String){
  val work=PeriodicWorkRequestBuilder<InboxWorker>(30,TimeUnit.MINUTES).setInputData(workDataOf("uid" to uid)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag("engagement:$uid").build()
  manager().enqueueUniquePeriodicWork("inbox-periodic:$uid",ExistingPeriodicWorkPolicy.KEEP,work)
 }
}
