package com.coy.huellasegura

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Las pruebas cambian preferencias de accesibilidad de la cuenta que esté abierta en el emulador. Esta regla guarda
 * las preferencias antes de cada prueba y las deja exactamente como estaban al terminar.
 */
class PrefsRule : TestWatcher() {

    private var backup: Map<String, Any?> = emptyMap()
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    override fun starting(description: Description) {
        backup = AccessibilityPrefs.of(context).all.toMap()
    }

    override fun finished(description: Description) {
        val edit = AccessibilityPrefs.of(context).edit().clear()
        backup.forEach { (key, value) ->
            when (value) {
                is Boolean -> edit.putBoolean(key, value)
                is Float -> edit.putFloat(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is String -> edit.putString(key, value)
            }
        }
        edit.commit()
    }

    /** Deja la cuenta sin ninguna preferencia (como una persona nueva). */
    fun clear() {
        AccessibilityPrefs.of(context).edit().clear().commit()
    }
}
