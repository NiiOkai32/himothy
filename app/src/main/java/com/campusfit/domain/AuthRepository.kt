package com.campusfit.domain
import kotlinx.coroutines.flow.StateFlow

data class Account(val uid: String, val email: String?, val verified: Boolean, val demo: Boolean = false)
interface AuthRepository {
    val account: StateFlow<Account?>
    val cloudConfigured: Boolean
    val syncStatus: StateFlow<String?>
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun google(idToken: String)
    suspend fun resetPassword(email: String)
    suspend fun resendVerification()
    suspend fun refreshVerification()
    suspend fun profile(): Profile?
    suspend fun saveProfile(profile: Profile)
    suspend fun deletionPending(): Boolean = false
    suspend fun clearLocalDataAndSignOut() { signOut() }
    suspend fun deleteAccount()
    suspend fun demo()
    fun signOut()
}
