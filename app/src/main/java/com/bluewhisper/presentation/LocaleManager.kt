package com.bluewhisper.presentation

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.bluewhisper.domain.model.AppLanguage
import java.util.Locale

/**
 * FR-01.4 / FR-10.3: Applies the user's language choice so that ALL
 * Android string resources (R.string.*) are served in the right locale.
 *
 * On API 33+ we use the per-app language API (AppCompatDelegate).
 * On older APIs we wrap the context with the correct locale.
 */
object LocaleManager {

    fun applyLanguage(language: AppLanguage, activity: Activity) {
        val locale = localeFor(language)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+: use official per-app locale API
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.create(locale)
            )
        } else {
            // API 26-32: update config and recreate activity
            val config = Configuration(activity.resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
            activity.recreate()
        }
    }

    fun wrapContext(context: Context, language: AppLanguage): Context {
        val locale = localeFor(language)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun localeFor(language: AppLanguage): Locale = when (language) {
        AppLanguage.BANGLA  -> Locale("bn", "BD")
        AppLanguage.ENGLISH -> Locale.ENGLISH
    }
}
