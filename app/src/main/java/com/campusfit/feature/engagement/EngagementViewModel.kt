package com.campusfit.feature.engagement

import android.content.Context
import android.net.Uri
import androidx.lifecycle.*
import com.campusfit.data.engagement.*
import com.campusfit.data.local.*
import com.campusfit.domain.*
import com.campusfit.domain.engagement.*
import com.google.firebase.firestore.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class EngagementState(val uid:String="",val preference:EngagementPreference?=null,val inbox:List<InboxEntry> = emptyList(),val blocks:Int=0,val task:MiniTask?=null,val completed:Boolean=false,val eligible:Boolean=false,val busy:Boolean=false,val message:String?=null,val error:String?=null,val celebrate:Boolean=false)
@HiltViewModel class EngagementViewModel @Inject constructor(private val repository:EngagementRepository,private val db:CampusDatabase,private val auth:AuthRepository,@ApplicationContext private val context:Context):ViewModel(){
 private val mutable=MutableStateFlow(EngagementState());val state=mutable.asStateFlow()
 val cloud get()=auth.cloudConfigured&&auth.account.value?.let{it.verified&&!it.demo}==true
 private var started=false
 private val observers=mutableListOf<Job>()
 private var operation:Job?=null
 private val day=MutableStateFlow(Math.floorDiv(System.currentTimeMillis(),86_400_000L))
 private var watch:ListenerRegistration?=null
 private var refreshJob:Job?=null
 private fun action(block:suspend()->Unit){if(mutable.value.busy)return;operation=viewModelScope.launch{mutable.update{it.copy(busy=true,error=null,message=null)};try{block()}catch(e:CancellationException){throw e}catch(e:Exception){mutable.update{it.copy(error=if(e is IllegalArgumentException||e is IllegalStateException)e.message else "Couldn’t finish that. Check your connection and try again.")}}finally{mutable.update{it.copy(busy=false)}}}}
 fun load(profile:Profile){if(started)return;started=true;val uid=profile.uid;mutable.update{it.copy(uid=uid)}
  observers+=viewModelScope.launch{repository.preferences(uid).collect{p->mutable.update{it.copy(preference=p)}}}
  observers+=viewModelScope.launch{repository.inbox(uid).collect{rows->mutable.update{it.copy(inbox=rows)}}}
  observers+=viewModelScope.launch{repository.blocks(uid).collect{rows->mutable.update{it.copy(blocks=rows.size)}}}
  observers+=viewModelScope.launch{combine(repository.minis(uid),db.tracking().sessions(uid),day){minis,sessions,date->Triple(minis,sessions,date)}.collect{(minis,sessions,day)->val today=sessions.filter{Math.floorDiv(it.startedAt,86_400_000L)==day};mutable.update{it.copy(task=DailyMini.task(uid,day),completed=minis.any{m->m.epochDay==day},eligible=DailyMini.eligible(today.sumOf{row->row.durationSeconds},today.sumOf{row->row.reps}))}}}
  // Refresh the UTC mini-task at midnight even while this screen remains open.
  observers+=viewModelScope.launch{while(isActive){day.value=Math.floorDiv(System.currentTimeMillis(),86_400_000L);delay(30_000)}}
  action{repository.initialize(profile);if(cloud)repository.refreshInbox(uid)}
  if(cloud)watch=FirebaseFirestore.getInstance().collection("users/$uid/notifications").orderBy("createdAt",Query.Direction.DESCENDING).limit(100).addSnapshotListener{_,error->if(error==null){refreshJob?.cancel();refreshJob=viewModelScope.launch{delay(1000);try{repository.refreshInbox(uid)}catch(e:CancellationException){throw e}catch(_:Exception){mutable.update{it.copy(message="Inbox refresh pending. Reconnect and refresh.")}}}}}
 }
 fun save(pref:EngagementPreference)=action{repository.save(pref);mutable.update{it.copy(message="Preferences saved on this device.")};if(cloud){repository.registerDevice(pref.uid);mutable.update{it.copy(message="Preferences saved; push registration updated.")}}}
 fun importCalendar(uri:Uri)=action{val text=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri).use{stream->checkNotNull(stream);val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(output.size()<=1_048_576){val count=stream.read(buffer,0,minOf(buffer.size,1_048_577-output.size()));if(count<0)break;output.write(buffer,0,count)};val bytes=output.toByteArray();require(bytes.size<=1_048_576){"Choose an iCalendar file under 1 MiB."};bytes.toString(Charsets.UTF_8)}};val count=repository.replaceCalendar(mutable.value.uid,text);mutable.update{it.copy(message="Imported $count busy intervals for the next 90 days. Event titles were discarded.")}}
 fun manual(day:Int,start:Int,end:Int)=action{repository.manual(mutable.value.uid,day,start,end);mutable.update{it.copy(message="Weekly busy block saved for 90 days.")}}
 fun clearCalendar()=action{repository.clearCalendar(mutable.value.uid);mutable.update{it.copy(message="Timetable removed. Reminders use your availability window only.")}}
 fun rest()=action{repository.restToday(mutable.value.uid);mutable.update{it.copy(message="Movement reminders paused for today in your selected time zone.")}}
 fun preview()=action{repository.preview(mutable.value.uid)}
 fun refresh()=action{repository.refreshInbox(mutable.value.uid);repository.plan(mutable.value.uid);mutable.update{it.copy(message="Inbox refreshed. Android may delay background reminders.")}}
 fun complete()=action{val fresh=repository.completeMini(mutable.value.uid);mutable.update{it.copy(celebrate=fresh,message=if(fresh)"Small step, well done. Your existing workout keeps its original XP." else "Today's mini-challenge is already complete.")}}
 fun awards(ids:List<String>)=action{if(repository.claimAwards(mutable.value.uid,ids))mutable.update{it.copy(celebrate=true)}}
 fun stopCelebration(){mutable.update{it.copy(celebrate=false)}}
 fun read(entry:InboxEntry)=action{repository.read(mutable.value.uid,entry.id)}
 fun stopWatching(){watch?.remove();watch=null;refreshJob?.cancel();observers.forEach{it.cancel()};observers.clear();operation?.cancel();started=false;mutable.value=EngagementState()}
 override fun onCleared(){stopWatching();super.onCleared()}
}
