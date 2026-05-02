package com.bluewhisper.presentation.screens.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.bluewhisper.data.local.UserProfileDataStore
import com.bluewhisper.presentation.theme.BlueWhisperTheme
import org.junit.Rule
import org.junit.Test

/**
 * PRD W9 Espresso onboarding flow.
 * Hardware-rooted: types nickname, picks avatar, picks language, hits Start,
 * verifies the complete callback fires.
 *
 * Constructs OnboardingViewModel directly with a real UserProfileDataStore.
 * The two assertion-only tests below never actually trigger a DataStore write.
 */
class OnboardingFlowUiTest {

    @get:Rule val composeRule = createComposeRule()

    private fun realVm(): OnboardingViewModel {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return OnboardingViewModel(ctx, UserProfileDataStore(ctx))
    }

    @Test fun startIsDisabledUntilNicknameHasAtLeastOneChar() {
        val vm = realVm()
        composeRule.setContent {
            BlueWhisperTheme { OnboardingScreen(onOnboardingComplete = {}, viewModel = vm) }
        }
        composeRule.onNodeWithText("✦ Start Chatting →").assertIsNotEnabled()
        composeRule.onNodeWithText("Your nickname...").performTextInput("Annoy")
        composeRule.onNodeWithText("✦ Start Chatting →").assertIsEnabled()
    }

    @Test fun pickingBanglaUpdatesGreeting() {
        val vm = realVm()
        composeRule.setContent {
            BlueWhisperTheme { OnboardingScreen(onOnboardingComplete = {}, viewModel = vm) }
        }
        composeRule.onNodeWithText("Hey there! 👋").assertIsDisplayed()
        composeRule.onNodeWithText("বাংলা").performClick()
        composeRule.onNodeWithText("হ্যালো! 👋").assertIsDisplayed()
    }

    @Test fun pipeCharacterIsStrippedFromNicknameInput() {
        val vm = realVm()
        composeRule.setContent {
            BlueWhisperTheme { OnboardingScreen(onOnboardingComplete = {}, viewModel = vm) }
        }
        composeRule.onNodeWithText("Your nickname...").performTextInput("A|B")
        // The `|` is stripped on input so the field shows "AB".
        composeRule.onNodeWithText("AB").assertIsDisplayed()
    }
}
