package com.campusfit.data.auth

import android.content.Context
import androidx.room.withTransaction
import com.campusfit.data.local.*
import com.campusfit.domain.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class AuthRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context, private val db: CampusDatabase, private val scheduler: com.campusfit.data.tracking.SyncScheduler, private val runtime: com.campusfit.data.engagement.EngagementRuntime,
) : AuthRepository {
    override val cloudConfigured = FirebaseApp.getApps(context).isNotEmpty()
    private val auth = if (cloudConfigured) FirebaseAuth.getInstance() else null
    private val store = if (cloudConfigured) FirebaseFirestore.getInstance().apply {
        firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(com.google.firebase.firestore.MemoryCacheSettings.newBuilder().build()).build()
    } else null
    private val mutable = MutableStateFlow(auth?.currentUser?.let { Account(it.uid, it.email, it.isEmailVerified) } ?: if(runtime.uid=="local-demo") Account("local-demo",null,true,true) else null)
    override val account = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sync = MutableStateFlow<String?>(null)
    override val syncStatus = sync.asStateFlow()
    init {
        runtime.account(mutable.value?.uid)
        auth?.addAuthStateListener { firebase ->
            if (mutable.value?.demo != true || firebase.currentUser != null) {
                mutable.value = firebase.currentUser?.let { Account(it.uid, it.email, it.isEmailVerified) }; runtime.account(mutable.value?.uid)
            }
        }
    }
    private fun requireAuth() = checkNotNull(auth) { "Add your Firebase configuration to enable account sign-in." }
    override suspend fun signIn(email: String, password: String) { requireAuth().signInWithEmailAndPassword(email.trim(), password).await() }
    override suspend fun signUp(email: String, password: String) {
        requireAuth().createUserWithEmailAndPassword(email.trim(), password).await()
        // A verification failure leaves a recoverable signed-in account; the UI offers resend.
        requireAuth().currentUser!!.sendEmailVerification().await()
    }
    override suspend fun google(idToken: String) { requireAuth().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }
    override suspend fun resetPassword(email: String) { requireAuth().sendPasswordResetEmail(email.trim()).await() }
    override suspend fun resendVerification() { requireAuth().currentUser!!.sendEmailVerification().await() }
    override suspend fun refreshVerification() {
        requireAuth().currentUser!!.reload().await()
        requireAuth().currentUser!!.getIdToken(true).await()
        val user = requireAuth().currentUser!!
        mutable.value = Account(user.uid, user.email, user.isEmailVerified)
    }
    override suspend fun profile(): Profile? {
        val user = account.value ?: return null
        db.profiles().get(user.uid)?.let {
            if (it.syncPending && !user.demo && user.verified) queueProfile(it.profile)
            return it.profile
        }
        if (user.demo || !user.verified) return null
        val doc = store!!.collection("users").document(user.uid).get().await()
        val json = doc.getString("profileJson") ?: return null
        val p = com.google.gson.Gson().fromJson(json, Profile::class.java)
        db.profiles().save(ProfileEntity(user.uid, p, false))
        return p
    }
    override suspend fun saveProfile(profile: Profile) {
        val user = checkNotNull(account.value)
        require(profile.uid == user.uid && user.verified && !db.extra().deleting(user.uid))
        require(HealthPolicy.validate(profile) == null)
        db.profiles().save(ProfileEntity(user.uid, profile, !user.demo))
        if (!user.demo) queueProfile(profile)
    }
    private fun queueProfile(profile: Profile) {
        scope.launch {
            if (db.extra().deleting(profile.uid)) return@launch
            queueProfileAllowed(profile)
        }
    }
    private fun queueProfileAllowed(profile: Profile) {
        sync.value = "Saved on device · cloud sync pending"
        store!!.collection("users").document(profile.uid).set(mapOf(
            "profileJson" to com.google.gson.Gson().toJson(profile), "schemaVersion" to 1,
        )).addOnSuccessListener {
            scope.launch {
                db.withTransaction {
                    val current = db.profiles().get(profile.uid)
                    if (current?.profile == profile) db.profiles().save(current.copy(syncPending = false))
                }
                if (account.value?.uid == profile.uid) sync.value = "Profile synced"
            }
        }.addOnFailureListener {
            if (account.value?.uid == profile.uid) sync.value = "Saved on device. Cloud sync failed; sign in again to retry."
        }
    }
    override suspend fun demo() { auth?.signOut(); mutable.value = Account("local-demo", null, true, true); runtime.account("local-demo") }
    override fun signOut() { runtime.account(null); if(cloudConfigured){ com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled=false; com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken() }; account.value?.uid?.let(scheduler::cancel); auth?.signOut(); mutable.value = null; sync.value = null }
    override suspend fun deletionPending(): Boolean = account.value?.uid?.let { db.extra().deleting(it) } ?: false
    override suspend fun clearLocalDataAndSignOut() {
        val uid = checkNotNull(account.value).uid
        scheduler.cancel(uid)
        runtime.account(null)
        db.withLocalDeletion(uid)
        signOut()
    }
    override suspend fun deleteAccount() {
        val user = checkNotNull(account.value)
        if (!user.demo) {
            val signedInAt = requireAuth().currentUser!!.metadata?.lastSignInTimestamp ?: 0L
            check(System.currentTimeMillis() - signedInAt < 5 * 60 * 1000) { "Sign out and sign in again before deleting your account." }
            db.extra().deleting(AccountDeletion(user.uid))
            scheduler.cancel(user.uid)
            runtime.account(null)
            // Server tombstones the UID, recursively erases all private collections and deletes Auth.
            val result = com.google.firebase.functions.FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("deleteCampusFitAccount").call(mapOf("uid" to user.uid)).await().data as? Map<*, *>
            check(result?.get("removed") == true) { "Cloud deletion has not been confirmed. Please retry." }
        }
        runtime.account(null)
        db.withLocalDeletion(user.uid)
        db.extra().clearDeletion(user.uid)
        signOut()
    }
}
private suspend fun CampusDatabase.withLocalDeletion(uid: String) {
    withTransaction { profiles().delete(uid); tracking().deleteWeights(uid); tracking().deleteSessions(uid); runs().delete(uid); extra().deleteRoutes(uid); extra().deletePreferences(uid); social().delete(uid); engagement().apply { clearBlocks(uid); deletePreferences(uid); deleteInbox(uid); deleteMinis(uid); deleteAwards(uid); deleteDays(uid) } }
}
