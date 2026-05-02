package com.bluewhisper.di

import com.bluewhisper.bluetooth.FileManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point that allows Compose screens to access FileManager
 * without wrapping it in a ViewModel. This avoids the anti-pattern of
 * constructing FileManager(context) manually inside composable functions.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FileManagerEntryPoint {
    fun fileManager(): FileManager
}
