package com.bluewhisper.presentation.screens.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tier 4 #20: countdown logic extracted out of ChatViewModel so the timer
 * lifecycle has one obvious owner. ChatViewModel still holds the canonical
 * `countdownFileId` / `countdownSeconds` UI state — the timer just drives the
 * tick callbacks and the terminal `onZero` action.
 *
 * Lives as long as the supplied scope lives. Cancel via `cancel()` on wipe.
 */
class VanishTimer(
    private val scope: CoroutineScope,
    private val tickMillis: Long = 1_000L,
    private val totalSeconds: Int = 10
) {
    private var job: Job? = null
    var activeFileId: String? = null
        private set

    fun start(
        fileId: String,
        onTick: (remaining: Int) -> Unit,
        onZero: suspend () -> Unit
    ) {
        if (activeFileId == fileId && job?.isActive == true) return
        job?.cancel()
        activeFileId = fileId
        onTick(totalSeconds)
        job = scope.launch {
            repeat(totalSeconds) { tick ->
                delay(tickMillis)
                val remaining = (totalSeconds - 1) - tick
                onTick(remaining)
                if (remaining == 0) {
                    onZero()
                    activeFileId = null
                }
            }
        }
    }

    fun cancelFor(fileId: String) {
        if (activeFileId == fileId) cancel()
    }

    fun cancel() {
        job?.cancel()
        job = null
        activeFileId = null
    }

    fun isRunning(): Boolean = job?.isActive == true
}
