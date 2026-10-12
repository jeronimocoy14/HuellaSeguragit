package com.coy.huellasegura

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

open class BaseAccessibleActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = AccessibilityPrefs.of(newBase)
        val scale = prefs.getFloat(AccessibilityConfigActivity.KEY_FONT_SCALE, 1.15f)
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = scale
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    protected fun isHighContrastEnabled(): Boolean =
        AccessibilityPrefs.of(this)
            .getBoolean(AccessibilityConfigActivity.KEY_HIGH_CONTRAST, false)

    protected fun isTtsEnabled(): Boolean =
        AccessibilityPrefs.of(this)
            .getBoolean(AccessibilityConfigActivity.KEY_TTS, true)
}
