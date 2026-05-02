package com.bluewhisper.presentation.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluewhisper.BlueWhisperApp
import com.bluewhisper.data.local.UserProfileDataStore
import com.bluewhisper.domain.model.AppLanguage
import com.bluewhisper.domain.model.NicknameRules
import com.bluewhisper.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val nickname: String = "",
    val selectedAvatarId: Int = 1,
    val selectedLanguage: AppLanguage = AppLanguage.ENGLISH,
    val isStartEnabled: Boolean = false,
    val isSaving: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val userProfileDataStore: UserProfileDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onNicknameChanged(value: String) {
        // Strip forbidden chars (e.g. `|` — the advertising-payload delimiter,
        // Bug B-06) and clamp length, then update state.
        val cleaned = NicknameRules.sanitize(value)
        _uiState.update { it.copy(
            nickname = cleaned,
            isStartEnabled = NicknameRules.isValid(cleaned)
        )}
    }

    fun onAvatarSelected(avatarId: Int) {
        _uiState.update { it.copy(selectedAvatarId = avatarId) }
    }

    fun onLanguageSelected(language: AppLanguage) {
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun saveProfileAndComplete(onComplete: () -> Unit) {
        if (!_uiState.value.isStartEnabled) return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val profile = UserProfile(
                nickname = _uiState.value.nickname.trim(),
                avatarId = _uiState.value.selectedAvatarId,
                language = _uiState.value.selectedLanguage
            )
            userProfileDataStore.saveProfile(profile)
            userProfileDataStore.setOnboardingComplete()
            // Persist language to SharedPrefs so BlueWhisperApp.attachBaseContext
            // picks up the choice on next process start.
            BlueWhisperApp.persistLanguage(appContext, _uiState.value.selectedLanguage)
            onComplete()
        }
    }
}
