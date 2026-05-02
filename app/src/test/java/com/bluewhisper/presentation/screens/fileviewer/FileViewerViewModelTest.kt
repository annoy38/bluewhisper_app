package com.bluewhisper.presentation.screens.fileviewer

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.bluewhisper.bluetooth.FileManager
import com.bluewhisper.data.local.SavedFileDao
import com.bluewhisper.data.local.SavedFileEntity
import com.bluewhisper.domain.model.FileState
import com.bluewhisper.domain.model.FileType
import com.bluewhisper.domain.model.ReceivedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * PRD W9: FileViewer save / vanish / decline paths (FR-07.4 / FR-07.6 / FR-07.7).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FileViewerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After  fun tearDown() { Dispatchers.resetMain() }

    private fun received(name: String = "photo.jpg") = ReceivedFile(
        payloadId = 1L,
        fileName = name,
        fileSizeBytes = 100L,
        fileType = FileType.IMAGE,
        tempPath = "/tmp/$name",
        senderNickname = "Alice",
        state = FileState.RECEIVED_UNVIEWED
    )

    private fun newVm(
        dao: SavedFileDao = mock(),
        fm: FileManager = mock()
    ): FileViewerViewModel {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        return FileViewerViewModel(ctx, dao, fm, SavedStateHandle())
    }

    @Test fun `save inserts SavedFileEntity with sender nickname`() = runTest {
        val dao = mock<SavedFileDao>()
        val fm = mock<FileManager>()
        whenever(fm.savePublicCopy(any(), any(), any())).thenReturn(
            FileManager.SavedLocation(
                absolutePath = "Downloads/BlueWhisper/Received/photo.jpg",
                contentUri = "content://media/external/downloads/1",
                mimeType = "image/jpeg"
            )
        )
        val vm = newVm(dao, fm)
        vm.loadFile(received())
        vm.saveFile()
        advanceUntilIdle()

        verify(dao).insertFile(org.mockito.kotlin.argThat<SavedFileEntity> {
            senderNickname == "Alice" && fileName == "photo.jpg"
        })
        assertTrue(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isCounting)
        assertEquals(FileState.SAVED, vm.uiState.value.file?.state)
    }

    @Test fun `vanishOnDisconnect triggers secure delete`() = runTest {
        val fm = mock<FileManager>()
        val vm = newVm(fm = fm)
        vm.loadFile(received())
        vm.vanishOnDisconnect()
        advanceUntilIdle()
        verify(fm).secureDelete("/tmp/photo.jpg")
        assertTrue(vm.uiState.value.isVanished)
    }

    @Test fun `vanishOnDisconnect after save is a no-op`() = runTest {
        val dao = mock<SavedFileDao>()
        val fm = mock<FileManager>()
        whenever(fm.savePublicCopy(any(), any(), any())).thenReturn(
            FileManager.SavedLocation("p", null, "image/jpeg")
        )
        val vm = newVm(dao, fm)
        vm.loadFile(received())
        vm.saveFile()
        advanceUntilIdle()

        vm.vanishOnDisconnect()
        advanceUntilIdle()
        // Once for save's path-prefix usage in secureDelete? No — save does NOT
        // delete (file is moved to public). vanishOnDisconnect is the no-op
        // after save: secureDelete must NOT be invoked again.
        verify(fm, org.mockito.kotlin.never()).secureDelete(any())
        assertTrue(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isVanished)
    }

    @Test fun `countdown reaches zero and vanishes the file`() = runTest {
        val fm = mock<FileManager>()
        val vm = newVm(fm = fm)
        vm.loadFile(received())
        vm.startCountdown()
        // Run the full 10 second countdown
        advanceTimeBy(10_000L)
        advanceUntilIdle()
        verify(fm).secureDelete("/tmp/photo.jpg")
        assertTrue(vm.uiState.value.isVanished)
        assertEquals(FileState.VANISHED, vm.uiState.value.file?.state)
    }

    @Test fun `save during countdown stops the timer`() = runTest {
        val dao = mock<SavedFileDao>()
        val fm = mock<FileManager>()
        whenever(fm.savePublicCopy(any(), any(), any())).thenReturn(
            FileManager.SavedLocation("p", null, "image/jpeg")
        )
        val vm = newVm(dao, fm)
        vm.loadFile(received())
        vm.startCountdown()
        advanceTimeBy(3_000L)

        vm.saveFile()
        advanceTimeBy(20_000L)
        advanceUntilIdle()

        // Vanish must NOT fire — secureDelete should never be called.
        verify(fm, org.mockito.kotlin.never()).secureDelete(any())
        assertTrue(vm.uiState.value.isSaved)
        assertFalse(vm.uiState.value.isVanished)
    }
}
