package com.coy.huellasegura

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Preferencias de accesibilidad por cuenta. Sin sesión iniciada se usa el archivo «de invitado»
 * (lo que se configura desde la pantalla de login); al entrar por primera vez, la cuenta parte de
 * esos valores y desde ahí queda con los suyos.
 */
object AccessibilityPrefs {
    private const val GUEST_FILE = "accessibility_prefs"
    private const val KEY_SEEDED = "seeded_from_guest"

    fun of(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(currentFileName(), Context.MODE_PRIVATE)

    private fun guest(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(GUEST_FILE, Context.MODE_PRIVATE)

    private fun currentFileName(): String {
        val email = Firebase.auth.currentUser?.email?.trim()?.lowercase()
        return if (email.isNullOrBlank()) GUEST_FILE else GUEST_FILE + "_" + email.replace(Regex("[^a-z0-9]"), "_")
    }

    /** Primera vez de una cuenta en este dispositivo: copia lo configurado antes del login. */
    fun seedFromGuestIfNew(context: Context) {
        if (currentFileName() == GUEST_FILE) return
        val mine = of(context)
        if (mine.getBoolean(KEY_SEEDED, false)) return
        val edit = mine.edit()
        guest(context).all.forEach { (key, value) ->
            when (value) {
                is Boolean -> edit.putBoolean(key, value)
                is Float -> edit.putFloat(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is String -> edit.putString(key, value)
            }
        }
        edit.putBoolean(KEY_SEEDED, true).apply()
    }

    /** Al cerrar sesión, el login vuelve a los valores por defecto. */
    fun clearGuest(context: Context) {
        guest(context).edit().clear().apply()
    }
}
