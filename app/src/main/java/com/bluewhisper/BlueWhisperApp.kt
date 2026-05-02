package com.bluewhisper

import android.app.Application
import android.content.Context
import com.bluewhisper.domain.model.AppLanguage
import com.bluewhisper.presentation.LocaleManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BlueWhisperApp : Application() {

    // FR-01.4 / FR-10.3: wrap context so all string resources use correct locale.
    // We read from SharedPreferences (synchronous, on-disk read of one string —
    // not from DataStore which is suspend-only) so attachBaseContext can stay
    // off the coroutines path entirely.
    override fun attachBaseContext(base: Context) {
        val prefs = base.getSharedPreferences(LANG_PREFS, Context.MODE_PRIVATE)
        val langCode = prefs.getString(LANG_KEY, AppLanguage.ENGLISH.code) ?: AppLanguage.ENGLISH.code
        val lang = AppLanguage.entries.find { it.code == langCode } ?: AppLanguage.ENGLISH
        super.attachBaseContext(LocaleManager.wrapContext(base, lang))
    }

    companion object {
        private const val LANG_PREFS = "bw_lang"
        private const val LANG_KEY = "lang"

        /** Called by SettingsViewModel when user changes language */
        fun persistLanguage(context: Context, language: AppLanguage) {
            context.applicationContext
                .getSharedPreferences(LANG_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(LANG_KEY, language.code)
                .apply()
        }
    }
}
