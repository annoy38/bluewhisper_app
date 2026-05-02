package com.bluewhisper.presentation.screens.onboarding

import androidx.test.core.app.ApplicationProvider
import com.bluewhisper.data.local.UserProfileDataStore
import com.bluewhisper.domain.model.AppLanguage
import com.bluewhisper.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * PRD W9 boundary tests for nickname acceptance.
 * Covers FR-01.1 (1–15 chars) and Bug B-06 (`|` rejection).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private fun newVm(): OnboardingViewModel {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        return OnboardingViewModel(ctx, mock(UserProfileDataStore::class.java))
    }

    @Test fun `empty nickname disables Start`() {
        val vm = newVm()
        vm.onNicknameChanged("")
        assertFalse(vm.uiState.value.isStartEnabled)
    }

    @Test fun `single char nickname enables Start`() {
        val vm = newVm()
        vm.onNicknameChanged("A")
        assertTrue(vm.uiState.value.isStartEnabled)
    }

    @Test fun `15 char nickname enables Start`() {
        val vm = newVm()
        vm.onNicknameChanged("x".repeat(15))
        assertTrue(vm.uiState.value.isStartEnabled)
        assertEquals(15, vm.uiState.value.nickname.length)
    }

    @Test fun `over-length input is clamped to 15`() {
        val vm = newVm()
        vm.onNicknameChanged("x".repeat(20))
        assertEquals(15, vm.uiState.value.nickname.length)
    }

    @Test fun `pipe character is stripped from nickname`() {
        val vm = newVm()
        vm.onNicknameChanged("A|B")
        assertEquals("AB", vm.uiState.value.nickname)
        assertTrue(vm.uiState.value.isStartEnabled)
    }

    @Test fun `whitespace only nickname disables Start`() {
        val vm = newVm()
        vm.onNicknameChanged("   ")
        assertFalse(vm.uiState.value.isStartEnabled)
    }

    @Test fun `saveProfileAndComplete writes profile on valid input`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = mock(UserProfileDataStore::class.java)
        val vm = OnboardingViewModel(ctx, store)

        vm.onNicknameChanged("Annoy")
        vm.onAvatarSelected(7)
        vm.onLanguageSelected(AppLanguage.BANGLA)

        var done = false
        vm.saveProfileAndComplete { done = true }
        advanceUntilIdle()

        assertTrue(done)
        verify(store).saveProfile(
            org.mockito.kotlin.argThat<UserProfile> {
                nickname == "Annoy" && avatarId == 7 && language == AppLanguage.BANGLA
            }
        )
        verify(store).setOnboardingComplete()
    }

    @Test fun `saveProfileAndComplete is a no-op when Start disabled`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = mock(UserProfileDataStore::class.java)
        val vm = OnboardingViewModel(ctx, store)
        // Empty input → isStartEnabled = false
        var done = false
        vm.saveProfileAndComplete { done = true }
        advanceUntilIdle()
        assertFalse(done)
    }
}
