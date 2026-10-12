package com.coy.huellasegura

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

open class BaseAccessibleActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(AccessibilityConfigActivity.PREFS_NAME, MODE_PRIVATE)
        val scale = prefs.getFloat(AccessibilityConfigActivity.KEY_FONT_SCALE, 1.15f)
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = scale
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    protected fun isHighContrastEnabled(): Boolean =
        getSharedPreferences(AccessibilityConfigActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(AccessibilityConfigActivity.KEY_HIGH_CONTRAST, false)

    protected fun isTtsEnabled(): Boolean =
        getSharedPreferences(AccessibilityConfigActivity.PREFS_NAME, MODE_PRIVATE)
            .getBoolean(AccessibilityConfigActivity.KEY_TTS, true)
}
