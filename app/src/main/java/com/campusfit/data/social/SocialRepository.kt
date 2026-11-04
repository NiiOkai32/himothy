package com.campusfit.data.social

import androidx.room.withTransaction
import com.campusfit.data.local.*
import com.campusfit.domain.*
import com.campusfit.domain.social.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class SocialRepository @Inject constructor(private val db:CampusDatabase,private val auth:AuthRepository,private val runtime:com.campusfit.data.engagement.EngagementRuntime) {
    private val gson=Gson()
    val cloud get()=auth.cloudConfigured&&auth.account.value?.let { !it.demo && it.verified }==true
    private suspend fun owner(uid:String){check(auth.account.value?.uid==uid&&!db.extra().deleting(uid)&&db.profiles().get(uid)!=null){"Sign in to an active account first."}}
    suspend fun <T> cached(uid:String,key:String,type:Class<T>):T? {
        owner(uid);val row=db.social().get(uid,key)?:return null
        if(row.sensitive&&System.currentTimeMillis()-row.updatedAt !in 0..300_000)return null
        return gson.fromJson(row.payloadJson,type)
    }
    suspend fun cachedAt(uid:String,key:String):Long? { owner(uid);return db.social().get(uid,key)?.updatedAt }
    suspend fun evict(uid:String,key:String) { db.withTransaction { owner(uid);db.social().evict(uid,key) } }
    private suspend fun cache(uid:String,key:String,value:Any,sensitive:Boolean=false) {
        db.withTransaction {owner(uid);db.social().put(SocialCache(uid,key,gson.toJson(value),System.currentTimeMillis(),sensitive))};runtime.widgets()
    }
    suspend fun <T> call(uid:String,action:String,data:Map<String,Any?>,type:Class<T>,key:String?=null,sensitive:Boolean=false):T {
        owner(uid);check(cloud){"Friends, groups and shared challenges need a verified Firebase account. Local workouts remain available."}
        val result=withTimeout(90_000){FirebaseFunctions.getInstance("us-central1").getHttpsCallable("socialAction").call(data+mapOf("uid" to uid,"action" to action)).awaitData()}
        owner(uid);val decoded=gson.fromJson(gson.toJson(result),type)
        if(key!=null)cache(uid,key,decoded as Any,sensitive)
        return decoded
    }
    suspend fun summary(profile:Profile):SocialSummary {
        if(cloud)return call(profile.uid,"summary",emptyMap(),SocialSummary::class.java,"summary")
        val old=cached(profile.uid,"summary",SocialSummary::class.java)
        val sessions=db.tracking().sessions(profile.uid).first()
        val game=DemoGame.stats(sessions.map{SocialActivity(it.id,it.startedAt,it.durationSeconds,it.calories)},System.currentTimeMillis())
        val result=(old?:SocialSummary(me=PublicPerson(profile.uid,"local_demo",profile.name,profile.university))).copy(game=game)
        cache(profile.uid,"summary",result);return result
    }
    suspend fun register(profile:Profile,person:PublicPerson):SocialSummary {
        if(cloud)return call(profile.uid,"register",mapOf("directoryConsent" to true,"handle" to person.handle,"alias" to person.alias,"campusOptIn" to person.campusOptIn,"leagueOptIn" to person.leagueOptIn,"discoverByPhone" to person.discoverByPhone),SocialSummary::class.java,"summary")
        val result=summary(profile).copy(me=person.copy(uid=profile.uid,handle=SocialPolicy.handle(person.handle),campusLabel=profile.university,campusOptIn=false,leagueOptIn=false,discoverByPhone=false))
        cache(profile.uid,"summary",result);return result
    }
    fun watchCampus(key:String,week:Long,changed:()->Unit):ListenerRegistration? {
        if(!cloud)return null
        var initial=true
        return FirebaseFirestore.getInstance().collection("campusBoards/$key/members").whereEqualTo("weekStart",week).orderBy("score",com.google.firebase.firestore.Query.Direction.DESCENDING).limit(100).addSnapshotListener{_,e->if(e==null){if(initial)initial=false else changed()}}
    }
    fun watchLeague(id:String,changed:()->Unit):ListenerRegistration? {
        if(!cloud)return null
        var initial=true
        return FirebaseFirestore.getInstance().document("leagues/$id").addSnapshotListener{_,e->if(e==null){if(initial)initial=false else changed()}}
    }
    fun watch(uid:String,challenge:String?,changed:()->Unit,failed:(Exception)->Unit):List<ListenerRegistration> {
        if(!cloud)return emptyList();val store=FirebaseFirestore.getInstance()
        val registrations=mutableListOf<ListenerRegistration>()
        var firstSignal=true
        registrations+=store.document("socialSignals/$uid").addSnapshotListener { _,e->if(e!=null)failed(e)else if(firstSignal)firstSignal=false else changed() }
        var firstFriends=true
        registrations+=store.collection("friendships").whereArrayContains("memberUids",uid).addSnapshotListener { _,e->if(e!=null)failed(e)else if(firstFriends)firstFriends=false else changed() }
        if(challenge!=null){var firstChallenge=true;registrations+=store.document("challenges/$challenge").addSnapshotListener { _,e->if(e!=null)failed(e)else if(firstChallenge)firstChallenge=false else changed() }}
        return registrations
    }
}
private suspend fun com.google.android.gms.tasks.Task<com.google.firebase.functions.HttpsCallableResult>.awaitData():Any? = this.await().data
