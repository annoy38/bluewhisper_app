package com.bluewhisper.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluewhisper.data.local.SavedFileDao
import com.bluewhisper.data.local.UserProfileDataStore
import com.bluewhisper.domain.model.AppLanguage
import com.bluewhisper.domain.model.NicknameRules
import com.bluewhisper.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SettingsUiState(
    val profile: UserProfile = UserProfile(),
    val isDiscoverable: Boolean = true,
    val fileCount: Int = 0,
    val totalStorageBytes: Long = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userProfileDataStore: UserProfileDataStore,
    private val savedFileDao: SavedFileDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userProfileDataStore.userProfile.collect { profile ->
                _uiState.update { it.copy(profile = profile, isDiscoverable = profile.isDiscoverable) }
            }
        }
        viewModelScope.launch {
            savedFileDao.getFileCount().collect { count ->
                _uiState.update { it.copy(fileCount = count) }
            }
        }
        viewModelScope.launch {
            savedFileDao.getTotalStorageUsedBytes().collect { bytes ->
                _uiState.update { it.copy(totalStorageBytes = bytes) }
            }
        }
    }

    fun updateNickname(nickname: String) {
        if (!NicknameRules.isValid(nickname)) return
        viewModelScope.launch { userProfileDataStore.updateNickname(nickname.trim()) }
    }

    fun updateAvatar(avatarId: Int) {
        viewModelScope.launch { userProfileDataStore.updateAvatar(avatarId) }
    }

    fun updateLanguage(language: AppLanguage, context: android.content.Context) {
        viewModelScope.launch {
            userProfileDataStore.updateLanguage(language)
            // FR-10.3: persist to SharedPrefs so attachBaseContext picks it up on next launch
            com.bluewhisper.BlueWhisperApp.persistLanguage(context, language)
            // Recreate the activity so all string resources reload in new locale
            withContext(Dispatchers.Main) {
                com.bluewhisper.presentation.LocaleManager.applyLanguage(
                    language, context as android.app.Activity
                )
            }
        }
    }

    fun toggleDiscoverability() {
        val new = !_uiState.value.isDiscoverable
        viewModelScope.launch { userProfileDataStore.updateDiscoverability(new) }
    }

    fun clearAllFiles() {
        viewModelScope.launch {
            val files = savedFileDao.getAllFiles().first()
            files.forEach { entity ->
                File(entity.localPath).delete()
                savedFileDao.softDeleteFile(entity.fileId)
            }
        }
    }

    fun formatStorageSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)}KB"
        else -> "${"%.1f".format(bytes / (1024f * 1024f))}MB"
    }
}
