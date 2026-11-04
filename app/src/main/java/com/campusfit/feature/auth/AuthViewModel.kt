package com.campusfit.feature.auth
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusfit.domain.*
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AuthUiState(val loading: Boolean = true, val account: Account? = null, val profile: Profile? = null, val error: String? = null, val message: String? = null, val deletionPending: Boolean = false)
@HiltViewModel class AuthViewModel @Inject constructor(private val repository: AuthRepository): ViewModel() {
    private val mutable = MutableStateFlow(AuthUiState())
    val state = mutable.asStateFlow()
    val syncStatus = repository.syncStatus
    val cloudConfigured get() = repository.cloudConfigured
    init {
        viewModelScope.launch {
            repository.account.collectLatest { user ->
                mutable.value = AuthUiState(loading = user != null, account = user)
                try { val pending = repository.deletionPending(); mutable.update { it.copy(deletionPending = pending) }; val profile = if (pending) null else repository.profile(); mutable.update { it.copy(loading = false, profile = profile, deletionPending = pending) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutable.update { it.copy(loading = false, error = "Could not load your profile. Check your connection and retry.") } }
            }
        }
    }
    private fun run(action: suspend () -> Unit) {
        if (mutable.value.loading) return
        viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null, message = null) }
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val error = when (e) {
                    is FirebaseNetworkException -> "Connection unavailable. Please try again when online."
                    is FirebaseAuthException -> when (e.errorCode) {
                        "ERROR_WEAK_PASSWORD" -> "Choose a stronger password with at least 8 characters."
                        "ERROR_REQUIRES_RECENT_LOGIN" -> "Sign out and sign in again before deleting your account."
                        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please wait and try again."
                        else -> "Account request failed. Check your details or reset your password."
                    }
                    is IllegalArgumentException, is IllegalStateException -> e.message ?: "Please check your details."
                    else -> "Something went wrong. Please try again."
                }
                mutable.update { it.copy(error = error) }
            } finally { mutable.update { it.copy(loading = false) } }
        }
    }
    fun submit(email: String, password: String, signup: Boolean) = run {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address." }
        require(if (signup) password.length >= 8 else password.isNotEmpty()) {
            if (signup) "Use at least 8 characters for your password." else "Enter your password."
        }
        if (signup) repository.signUp(email, password) else repository.signIn(email, password)
    }
    fun google(token: String) = run { repository.google(token) }
    fun googleError(message: String = "Google sign-in did not complete. Please try again.") { mutable.update { it.copy(error = message) } }
    fun demo() = run { repository.demo() }
    fun reset(email: String) = run {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter your email address first." }
        repository.resetPassword(email)
        mutable.update { it.copy(message = "If an account exists, you will receive password reset instructions.") }
    }
    fun resend() = run { repository.resendVerification(); mutable.update { it.copy(message = "Verification email sent. Check your inbox.") } }
    fun refresh() = run { repository.refreshVerification() }
    fun save(profile: Profile) = run {
        HealthPolicy.validate(profile)?.let { throw IllegalArgumentException(it) }
        repository.saveProfile(profile)
        mutable.update { it.copy(profile = profile, message = "Profile saved on this device.") }
    }
    fun retry() = run { val profile = repository.profile(); mutable.update { it.copy(profile = profile) } }
    fun signOut() = repository.signOut()
    fun delete() = run {
        try { repository.deleteAccount() }
        finally { val pending = repository.deletionPending(); mutable.update { it.copy(deletionPending = pending) } }
    }
    fun clearDeviceData() = run { repository.clearLocalDataAndSignOut() }
}
