package com.campusfit.feature.auth

import com.campusfit.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun signedOutStartupFinishesLoading() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeAuth())
        runCurrent()
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.account)
    }
    @Test fun demoLoadsItsOwnLocalProfile() = runTest(dispatcher) {
        val repo = FakeAuth()
        val vm = AuthViewModel(repo)
        runCurrent()
        vm.demo()
        runCurrent()
        assertTrue(vm.state.value.account!!.demo)
        assertEquals("Ama", vm.state.value.profile!!.name)
    }
    @Test fun unsafeGoalNeverReachesRepository() = runTest(dispatcher) {
        val repo = FakeAuth()
        val vm = AuthViewModel(repo)
        runCurrent(); vm.demo(); runCurrent()
        vm.save(repo.saved.copy(goalKg = 40.0))
        runCurrent()
        assertNotNull(vm.state.value.error)
        assertEquals(0, repo.saveCount)
    }
    @Test fun profileLoadFailureIsRecoverable() = runTest(dispatcher) {
        val repo = FakeAuth().apply { failRead = true }
        val vm = AuthViewModel(repo)
        runCurrent(); vm.demo(); runCurrent()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.loading)
        repo.failRead = false
        vm.retry(); runCurrent()
        assertNotNull(vm.state.value.profile)
        assertNull(vm.state.value.error)
    }
    @Test fun signOutClearsDisplayedHealthData() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeAuth())
        runCurrent(); vm.demo(); runCurrent()
        vm.signOut(); runCurrent()
        assertNull(vm.state.value.profile)
        assertNull(vm.state.value.account)
    }
}
private class FakeAuth : AuthRepository {
    override val account = MutableStateFlow<Account?>(null)
    override val syncStatus = MutableStateFlow<String?>(null)
    override val cloudConfigured = false
    var failRead = false
    var saveCount = 0
    var saved = Profile("local-demo", "Ama", 21, 170.0, 75.0, 68.0, "Beginner", 10, "UG", "", true, true, true)
    override suspend fun profile(): Profile? {
        if (account.value == null) return null
        if (failRead) error("Unavailable")
        return saved
    }
    override suspend fun demo() { account.value = Account("local-demo", null, true, true) }
    override fun signOut() { account.value = null }
    override suspend fun saveProfile(profile: Profile) { saved = profile; saveCount++ }
    override suspend fun deleteAccount() { signOut() }
    override suspend fun signIn(email: String, password: String) = error("Unused")
    override suspend fun signUp(email: String, password: String) = error("Unused")
    override suspend fun google(idToken: String) = error("Unused")
    override suspend fun resetPassword(email: String) = error("Unused")
    override suspend fun resendVerification() = error("Unused")
    override suspend fun refreshVerification() = error("Unused")
}
