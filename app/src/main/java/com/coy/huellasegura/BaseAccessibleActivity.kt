package com.coy.huellasegura

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Base de todas las pantallas: aplica las preferencias de accesibilidad (texto, modo oscuro, alto contraste). */
open class BaseAccessibleActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AccessibilityTheme.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AccessibilityTheme.applyNightMode(this)
        super.onCreate(savedInstanceState)
    }

    protected fun isTtsEnabled(): Boolean =
        AccessibilityPrefs.of(this)
            .getBoolean(AccessibilityConfigActivity.KEY_TTS, true)
}
