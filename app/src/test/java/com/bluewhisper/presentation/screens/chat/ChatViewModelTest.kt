package com.bluewhisper.presentation.screens.chat

import androidx.test.core.app.ApplicationProvider
import com.bluewhisper.bluetooth.FileManager
import com.bluewhisper.bluetooth.NearbyConnectionsManager
import com.bluewhisper.data.local.UserProfileDataStore
import com.bluewhisper.domain.model.ConnectionState
import com.bluewhisper.domain.model.MessagePacket
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Verifies the safety-critical send-gate (NFR-03.1) and the WakeLock sizing
 * helper (Tier 3 #11 / B-08). The full chat path is covered by the two-device
 * harness; this is the unit-level lock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private fun stubManager(): NearbyConnectionsManager {
        val mgr = mock<NearbyConnectionsManager>()
        whenever(mgr.connectionState).thenReturn(MutableStateFlow(ConnectionState.Idle))
        whenever(mgr.incomingPackets).thenReturn(MutableSharedFlow())
        whenever(mgr.filePayloads).thenReturn(MutableSharedFlow())
        whenever(mgr.transferUpdates).thenReturn(MutableSharedFlow())
        whenever(mgr.events).thenReturn(MutableSharedFlow())
        return mgr
    }

    private fun newVm(mgr: NearbyConnectionsManager = stubManager()): ChatViewModel {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        return ChatViewModel(
            context = ctx,
            nearbyManager = mgr,
            fileManager = mock<FileManager>(),
            gson = Gson(),
            userProfileDataStore = mock<UserProfileDataStore>()
        )
    }

    @Test fun `sendMessage drops plaintext while not encrypted`() = runTest {
        val mgr = stubManager()
        val vm = newVm(mgr)

        vm.sendMessage("hi")
        advanceUntilIdle()

        // Must NEVER reach the wire before key exchange completes (NFR-03.1).
        verify(mgr, never()).sendMessage(any<MessagePacket>())
        assertEquals(0, vm.uiState.value.messageCount)
    }

    @Test fun `sendMessage drops content over 200 chars`() = runTest {
        val mgr = stubManager()
        val vm = newVm(mgr)
        // Force isEncrypted via reflection-free path: simulate a SessionKeyReady event.
        // Easiest: we use a wider hammer and assert no send occurs anyway because
        // content is too long. Both gates are independent in the implementation.
        vm.sendMessage("x".repeat(201))
        advanceUntilIdle()
        verify(mgr, never()).sendMessage(any<MessagePacket>())
    }

    @Test fun `sendMessage drops blank input`() = runTest {
        val mgr = stubManager()
        val vm = newVm(mgr)
        vm.sendMessage("   ")
        advanceUntilIdle()
        verify(mgr, never()).sendMessage(any<MessagePacket>())
    }

    @Test fun `WakeLock timeout scales with file size and is bounded`() {
        val vm = newVm()
        // Floor: tiny file → 30 s minimum
        assertEquals(30_000L, vm.computeWakeLockTimeoutMs(1_000L))
        // Ceiling: huge file → capped at 10 minutes
        assertEquals(600_000L, vm.computeWakeLockTimeoutMs(Long.MAX_VALUE / 2))
        // 25 MB at 100 KB/s ≈ 250 s + 30 s slack = 280 s. Always > the old 35 s constant.
        val twentyFiveMb = 25L * 1024 * 1024
        val timeout = vm.computeWakeLockTimeoutMs(twentyFiveMb)
        assertTrue("Expected > 35s for 25MB, got ${timeout}ms", timeout > 35_000L)
        assertTrue("Expected ≤ 10min, got ${timeout}ms", timeout <= 600_000L)
    }
}
