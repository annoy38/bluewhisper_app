package com.bluewhisper.presentation.screens.connection

import app.cash.turbine.test
import com.bluewhisper.bluetooth.NearbyConnectionsManager
import com.bluewhisper.domain.model.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * PRD W9: ConnectionViewModel 30-second timeout (FR-03.4).
 * Both sender and receiver paths must auto-reject and emit Timeout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private fun stubManager(): NearbyConnectionsManager {
        val mgr = mock<NearbyConnectionsManager>()
        whenever(mgr.connectionState).thenReturn(MutableStateFlow(ConnectionState.Idle))
        whenever(mgr.events).thenReturn(MutableSharedFlow())
        return mgr
    }

    @Test fun `sender timeout fires Timeout event after 30 seconds`() = runTest {
        val mgr = stubManager()
        val state = MutableStateFlow<ConnectionState>(ConnectionState.Requesting("peer-A"))
        whenever(mgr.connectionState).thenReturn(state)

        val vm = ConnectionViewModel(mgr)
        vm.startSenderTimeout()

        vm.events.test {
            advanceTimeBy(29_999L)
            expectNoEvents()
            advanceTimeBy(1L)
            advanceUntilIdle()
            assertEquals(ConnectionUiEvent.Timeout, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // Auto-reject path
        verify(mgr).rejectConnection("peer-A")
    }

    @Test fun `receiver timeout auto-declines after 30 seconds`() = runTest {
        val mgr = stubManager()
        val vm = ConnectionViewModel(mgr)
        val endpointId = "peer-B"

        vm.startReceiverTimeout(endpointId)

        vm.events.test {
            advanceTimeBy(30_000L)
            advanceUntilIdle()
            assertEquals(ConnectionUiEvent.Timeout, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(mgr).rejectConnection(endpointId)
    }

    @Test fun `timeoutSecondsLeft counts down from 30`() = runTest {
        val mgr = stubManager()
        val vm = ConnectionViewModel(mgr)
        vm.startSenderTimeout()

        assertEquals(30, vm.timeoutSecondsLeft.value)
        advanceTimeBy(5_000L)
        // After 5 ticks, value should be 25.
        assertEquals(25, vm.timeoutSecondsLeft.value)
        advanceTimeBy(25_000L)
        assertTrue(vm.timeoutSecondsLeft.value <= 0)
    }

    @Test fun `accept cancels timer before it fires`() = runTest {
        val mgr = stubManager()
        val vm = ConnectionViewModel(mgr)

        vm.startReceiverTimeout("peer-X")
        advanceTimeBy(10_000L)
        vm.acceptConnection("peer-X")
        advanceTimeBy(40_000L)
        advanceUntilIdle()

        verify(mgr).acceptConnection("peer-X")
        // No reject should ever fire.
        verify(mgr, org.mockito.kotlin.never()).rejectConnection("peer-X")
    }
}
