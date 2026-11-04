package com.campusfit.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusfit.data.social.SocialRepository
import com.campusfit.domain.*
import com.campusfit.domain.social.*
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SocialState(
 val loading:Boolean=true,val busy:Boolean=false,val cached:Boolean=false,val summary:SocialSummary?=null,
 val board:ChallengeBoard?=null,val campus:CampusBoard?=null,val league:LeagueBoard?=null,
 val error:String?=null,val message:String?=null,val openedId:String?=null,
)
@HiltViewModel class SocialViewModel @Inject constructor(private val repository:SocialRepository):ViewModel(){
 private val mutable=MutableStateFlow(SocialState());val state=mutable.asStateFlow()
 val cloud get()=repository.cloud
 private var owner:Profile?=null
 private var watchers=emptyList<ListenerRegistration>()
 private var debounce:Job?=null
 private var expiry:Job?=null
 private var boardWatch:ListenerRegistration?=null
 private var currentChallenge:String?=null
 private var started=false
 private suspend fun <T> request(action:String,data:Map<String,Any?> = emptyMap(),type:Class<T>,key:String?=null,sensitive:Boolean=false):T = repository.call(checkNotNull(owner).uid,action,data,type,key,sensitive)
 private fun action(block:suspend()->Unit){
  if(mutable.value.busy)return
  viewModelScope.launch{
   mutable.update{it.copy(busy=true,error=null,message=null)}
   try{block()}
   catch(e:CancellationException){throw e}
   catch(e:Exception){
    val message=when(e){
     is FirebaseFunctionsException -> when(e.code){
      FirebaseFunctionsException.Code.NOT_FOUND -> "Couldn’t find that account or challenge. Online play may still be unavailable."
      FirebaseFunctionsException.Code.UNAVAILABLE -> "Connection unavailable. Saved workouts still work offline."
      FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "Please wait before trying again; this list may also have reached its limit."
      FirebaseFunctionsException.Code.INTERNAL -> "The social service could not complete this request. Please retry."
      else -> e.message?:"Social request failed."
     }
     is IllegalArgumentException,is IllegalStateException -> e.message?:"Check your details."
     else -> "Could not refresh social data. Reconnect and retry."
    }
    mutable.update{it.copy(loading=false,error=message,cached=it.summary!=null||it.board!=null)}
   }finally{mutable.update{it.copy(busy=false,loading=false)}}
  }
 }
 fun load(profile:Profile){
  owner=profile;if(started)return;started=true
  action{
   val cached=repository.cached(profile.uid,"summary",SocialSummary::class.java)
   if(cached!=null)mutable.update{it.copy(summary=cached,cached=true,loading=false)}
   val summary=repository.summary(profile);mutable.update{it.copy(summary=summary,cached=false)}
  }
 }
 fun refresh()=action{val summary=repository.summary(checkNotNull(owner));mutable.update{it.copy(summary=summary,cached=false)}}
 fun watch(challenge:String?=null){
  watchers.forEach{it.remove()};currentChallenge=challenge
  val uid=owner?.uid?:return
  watchers=repository.watch(uid,challenge,{
   debounce?.cancel();debounce=viewModelScope.launch{delay(1800);if(currentChallenge!=null)openBoard(currentChallenge!!)else refresh()}
  },{mutable.update{it.copy(cached=true,message="Live updates are unavailable. Reconnect and refresh.")}})
 }
 fun watchBoard(kind:String){
  boardWatch?.remove()
  val refresh:()->Unit={debounce?.cancel();debounce=viewModelScope.launch{delay(1500);if(kind=="campus")campus()else league()}}
  if(kind=="campus"){val me=mutable.value.summary?.me;val board=mutable.value.campus;if(me!=null&&board!=null)boardWatch=repository.watchCampus(me.campusKey,board.weekStart,refresh)}
  else mutable.value.league?.id?.takeIf{it.isNotBlank()}?.let{boardWatch=repository.watchLeague(it,refresh)}
 }
 fun stopBoardWatch(){boardWatch?.remove();boardWatch=null}
 fun stopWatching(){watchers.forEach{it.remove()};watchers=emptyList();debounce?.cancel()}
 fun register(person:PublicPerson)=action{val result=repository.register(checkNotNull(owner),person);mutable.update{it.copy(summary=result,cached=false,message="Your sharing preferences were saved.")}}
 fun create(title:String,period:String,metric:String,visibility:String,groupId:String,teams:String,next:Boolean)=action{
  val response=request("createChallenge",mapOf("title" to title,"period" to period,"metric" to metric,"visibility" to visibility,"groupId" to groupId,"teams" to teams.split(',').map{it.trim()}.filter{it.isNotEmpty()},"next" to next),Map::class.java)
  mutable.update{it.copy(openedId=response["id"] as String)}
 }
 fun clearOpened(){mutable.update{it.copy(openedId=null)}}
 private fun expire(board:ChallengeBoard,at:Long){
  expiry?.cancel()
  if(board.challenge.metric in listOf("percent","kilograms"))expiry=viewModelScope.launch{
   delay((at+300_000-System.currentTimeMillis()).coerceAtLeast(0))
   if(mutable.value.board?.challenge?.id==board.challenge.id)mutable.update{it.copy(board=null,cached=true,message="Weight sharing snapshots expire after five minutes. Refresh to check current consent and scores.")}
  }
 }
 fun openBoard(id:String)=action{
  currentChallenge=id
  val uid=checkNotNull(owner).uid
  val cached=repository.cached(uid,"board:$id",ChallengeBoard::class.java)
  if(cached!=null){mutable.update{it.copy(board=cached,cached=true)};expire(cached,repository.cachedAt(uid,"board:$id")?:0)}
  val board=request("board",mapOf("id" to id),ChallengeBoard::class.java,"board:$id",true)
  mutable.update{it.copy(board=board,cached=false)}
  expire(board,System.currentTimeMillis())
 }
 fun join(id:String,team:String,consent:Boolean)=action{val board=request("joinChallenge",mapOf("id" to id,"team" to team,"weightConsent" to consent),ChallengeBoard::class.java,"board:$id",true);mutable.update{it.copy(board=board,cached=false)}}
 fun leave(id:String)=action{
  request("leaveChallenge",mapOf("id" to id),Map::class.java)
  repository.evict(checkNotNull(owner).uid,"board:$id")
  mutable.update{state->val board=state.board;state.copy(board=board?.copy(joined=false,challenge=board.challenge.copy(memberUids=board.challenge.memberUids.filter{it!=owner?.uid}),rows=if(board.challenge.metric in listOf("percent","kilograms"))emptyList()else board.rows,teams=if(board.challenge.metric in listOf("percent","kilograms"))emptyList()else board.teams,messages=if(board.challenge.metric in listOf("percent","kilograms"))emptyList()else board.messages),message="You left the challenge.")}
  val s=repository.summary(checkNotNull(owner));mutable.update{it.copy(summary=s)}
 }

 fun friend(handle:String,operation:String)=action{val s=request("friend",mapOf("handle" to SocialPolicy.handle(handle),"operation" to operation),SocialSummary::class.java,"summary");mutable.update{it.copy(summary=s,message="Friend list updated.")}}
 fun friendCode(code:String){try{friend(SocialPolicy.friendCode(code),"request")}catch(_:Exception){mutable.update{it.copy(error="That is not a valid CampusFit friend QR code.")}}}
 fun unblock(uid:String)=action{val s=request("unblock",mapOf("target" to uid),SocialSummary::class.java,"summary");mutable.update{it.copy(summary=s,message="Account unblocked.")}}
 fun phoneLookup(phone:String)=action{val result=request("phoneLookup",mapOf("phone" to phone),Map::class.java);mutable.update{it.copy(message="Found @${result["handle"]}. Enter that username below to send a request.")}}
 fun group(operation:String,name:String="",kind:String="friends",code:String="",id:String="")=action{val s=request("group",mapOf("operation" to operation,"name" to name,"kind" to kind,"code" to code,"id" to id),SocialSummary::class.java,"summary");mutable.update{it.copy(summary=s,message="Group updated.")}}
 fun cheer(id:String,phrase:String,target:String="")=action{val b=request("cheer",mapOf("id" to id,"phrase" to phrase,"target" to target),ChallengeBoard::class.java);mutable.update{it.copy(board=b)}}
 fun campus()=action{val b=request("campus",type=CampusBoard::class.java,key="campus");mutable.update{it.copy(campus=b,cached=false)}}
 fun league()=action{val b=request("league",type=LeagueBoard::class.java,key="league");mutable.update{it.copy(league=b,cached=false)}}
 fun rest()=action{val s=request("rest",type=SocialSummary::class.java,key="summary");mutable.update{it.copy(summary=s,message="A rest day is protected this UTC week. It earns no XP or rank credit.")}}
 fun info(message:String){mutable.update{it.copy(message=message)}}
 override fun onCleared(){stopWatching();stopBoardWatch();expiry?.cancel();super.onCleared()}
}
