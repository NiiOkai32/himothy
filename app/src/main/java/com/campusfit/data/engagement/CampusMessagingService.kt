package com.campusfit.data.engagement

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.campusfit.domain.engagement.NotificationKinds
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint class CampusMessagingService:FirebaseMessagingService(){
 @Inject lateinit var runtime:EngagementRuntime
 @Inject lateinit var scheduler:EngagementScheduler
 override fun onNewToken(token:String){runtime.uid?.let(scheduler::device)}
 override fun onMessageReceived(message:RemoteMessage){
  val uid=message.data["uid"]?:return;val id=message.data["id"]?:return;val kind=message.data["kind"]?:return
  if(runtime.uid!=uid||kind !in NotificationKinds.all||!id.matches(Regex("[a-zA-Z0-9_-]{1,100}")))return
  scheduler.delivery(uid,id)
 }
}
