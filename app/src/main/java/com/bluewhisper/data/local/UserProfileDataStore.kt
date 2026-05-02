package com.bluewhisper.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bluewhisper.domain.model.AppLanguage
import com.bluewhisper.domain.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
val Context.userProfileDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "user_profile")

@Singleton
class UserProfileDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val NICKNAME = stringPreferencesKey("nickname")
        val AVATAR_ID = intPreferencesKey("avatar_id")
        val LANGUAGE = stringPreferencesKey("language")
        val IS_DISCOVERABLE = booleanPreferencesKey("is_discoverable")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CREATED_AT = longPreferencesKey("created_at")
    }

    // ── Read profile ─────────────────────────────────────────────
    val userProfile: Flow<UserProfile> = context.userProfileDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            UserProfile(
                // We cannot call a suspend fun from inside a non-suspending .map { } lambda.
                // Generate a fresh UUID transiently if none stored; the first explicit
                // saveProfile() (from onboarding) writes the real persisted deviceId.
                deviceId = prefs[Keys.DEVICE_ID] ?: UUID.randomUUID().toString(),
                nickname = prefs[Keys.NICKNAME] ?: "",
                avatarId = prefs[Keys.AVATAR_ID] ?: 1,
                language = AppLanguage.entries.find {
                    it.code == prefs[Keys.LANGUAGE]
                } ?: AppLanguage.ENGLISH,
                isDiscoverable = prefs[Keys.IS_DISCOVERABLE] ?: true,
                createdAtEpoch = prefs[Keys.CREATED_AT] ?: System.currentTimeMillis()
            )
        }

    val isOnboardingComplete: Flow<Boolean> = context.userProfileDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.ONBOARDING_DONE] ?: false }

    // ── Write operations ─────────────────────────────────────────
    suspend fun saveProfile(profile: UserProfile) {
        context.userProfileDataStore.edit { prefs ->
            prefs[Keys.DEVICE_ID] = profile.deviceId
            prefs[Keys.NICKNAME] = profile.nickname
            prefs[Keys.AVATAR_ID] = profile.avatarId
            prefs[Keys.LANGUAGE] = profile.language.code
            prefs[Keys.IS_DISCOVERABLE] = profile.isDiscoverable
            if (prefs[Keys.CREATED_AT] == null) {
                prefs[Keys.CREATED_AT] = profile.createdAtEpoch
            }
        }
    }

    suspend fun setOnboardingComplete() {
        context.userProfileDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_DONE] = true
        }
    }

    suspend fun updateNickname(nickname: String) {
        context.userProfileDataStore.edit { prefs ->
            prefs[Keys.NICKNAME] = nickname
        }
    }

    suspend fun updateAvatar(avatarId: Int) {
        context.userProfileDataStore.edit { prefs ->
            prefs[Keys.AVATAR_ID] = avatarId
        }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.userProfileDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun updateDiscoverability(isDiscoverable: Boolean) {
        context.userProfileDataStore.edit { prefs ->
            prefs[Keys.IS_DISCOVERABLE] = isDiscoverable
        }
    }
}
