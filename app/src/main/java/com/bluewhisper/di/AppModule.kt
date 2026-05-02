package com.bluewhisper.di

import android.content.Context
import androidx.room.Room
import com.bluewhisper.data.local.BlueWhisperDatabase
import com.bluewhisper.data.local.SavedFileDao
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .serializeNulls()
        .create()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BlueWhisperDatabase =
        Room.databaseBuilder(
            context,
            BlueWhisperDatabase::class.java,
            BlueWhisperDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideSavedFileDao(database: BlueWhisperDatabase): SavedFileDao =
        database.savedFileDao()

    // NOTE: UserProfileDataStore, EncryptionEngine, MessageSerializer,
    // KeyExchangeManager, FileManager, and NearbyConnectionsManager all use
    // @Singleton class @Inject constructor(...) — Hilt provides them automatically.
    // Do NOT add @Provides methods for them; that creates duplicate bindings.
}
