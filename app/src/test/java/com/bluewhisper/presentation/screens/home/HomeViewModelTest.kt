package com.bluewhisper.presentation.screens.home

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.bluewhisper.bluetooth.NearbyConnectionsManager
import com.bluewhisper.data.local.UserProfileDataStore
import com.bluewhisper.domain.model.ConnectionState
import com.bluewhisper.domain.model.UserProfile
import com.bluewhisper.service.BluetoothForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * PRD W9 ViewModel tests for Home.
 * Verifies:
 *  - Permission gating across API 31+ vs pre-S (FR-09.x)
 *  - toggleDiscoverability dispatches the right service intent (Tier 3 #12)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private fun stubManager(): NearbyConnectionsManager {
        val mgr = mock<NearbyConnectionsManager>()
        whenever(mgr.connectionState).thenReturn(MutableStateFlow(ConnectionState.Idle))
        whenever(mgr.nearbyDevices).thenReturn(MutableStateFlow(emptyList()))
        whenever(mgr.events).thenReturn(MutableSharedFlow())
        return mgr
    }

    private fun stubProfileStore(profile: UserProfile = UserProfile(nickname = "Annoy")): UserProfileDataStore {
        val store = mock<UserProfileDataStore>()
        whenever(store.userProfile).thenReturn(flowOf(profile))
        return store
    }

    private fun newVm(
        mgr: NearbyConnectionsManager = stubManager(),
        store: UserProfileDataStore = stubProfileStore()
    ): HomeViewModel {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        return HomeViewModel(ctx, mgr, store)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `pre-S permission gate evaluates without crashing on API 33`() = runTest {
        val vm = newVm()
        // No permissions granted → checkPermissions = false. The point of the
        // test is the API-version branch executes and returns a Boolean rather
        // than throwing — Robolectric simulates the API level.
        val granted = vm.checkPermissions()
        assertFalse(granted)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O])
    fun `legacy permission gate executes on API 26`() = runTest {
        val vm = newVm()
        val granted = vm.checkPermissions()
        // No grants → false on API 26 too.
        assertFalse(granted)
    }

    @Test
    fun `toggleDiscoverability fires BECOME_INVISIBLE when turning off`() = runTest {
        // Profile starts with isDiscoverable = true.
        val vm = newVm(store = stubProfileStore(UserProfile(nickname = "X", isDiscoverable = true)))
        advanceUntilIdle() // allow profile flow to land

        vm.toggleDiscoverability()
        advanceUntilIdle()

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val shadow = Shadows.shadowOf(ctx as android.app.Application)
        val started = shadow.peekNextStartedService()
        assertNotNull("Expected a service intent to be dispatched", started)
        assertEquals(
            BluetoothForegroundService.ACTION_BECOME_INVISIBLE,
            started.action
        )
    }

    @Test
    fun `toggleDiscoverability fires BECOME_DISCOVERABLE when turning on`() = runTest {
        val vm = newVm(store = stubProfileStore(UserProfile(nickname = "X", isDiscoverable = false)))
        advanceUntilIdle()

        vm.toggleDiscoverability()
        advanceUntilIdle()

        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val shadow = Shadows.shadowOf(ctx as android.app.Application)
        val started = shadow.peekNextStartedService()
        assertNotNull(started)
        assertEquals(
            BluetoothForegroundService.ACTION_BECOME_DISCOVERABLE,
            started.action
        )
    }

    @Test
    fun `startScanning dispatches START_SCANNING service intent`() {
        val vm = newVm()
        vm.startScanning()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val shadow = Shadows.shadowOf(ctx as android.app.Application)
        val started = shadow.peekNextStartedService()
        assertNotNull(started)
        assertEquals(BluetoothForegroundService.ACTION_START_SCANNING, started.action)
        assertTrue(vm.uiState.value.isScanning)
    }
}
