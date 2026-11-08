package com.coy.huellasegura

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * Apariencia según las preferencias de accesibilidad de la cuenta: tamaño del texto, modo oscuro y alto contraste.
 *
 * - Modo oscuro: «Automático» sigue al teléfono, o se fuerza «Claro» u «Oscuro». Los colores de cada modo están en
 *   values/colors.xml (claro) y values-night/colors.xml (oscuro).
 * - Alto contraste: siempre usa el tema oscuro y una paleta propia (negro, blanco y amarillo, en values-round).
 *   Ese calificador es solo un interruptor: la app lo activa aquí; ningún teléfono lo activa solo.
 */
object AccessibilityTheme {
    const val KEY_THEME = "theme_mode"
    const val THEME_AUTO = "auto"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    fun isHighContrast(context: Context): Boolean =
        AccessibilityPrefs.of(context).getBoolean(AccessibilityConfigActivity.KEY_HIGH_CONTRAST, false)

    fun themeChoice(context: Context): String =
        AccessibilityPrefs.of(context).getString(KEY_THEME, THEME_AUTO) ?: THEME_AUTO

    /** Modo claro/oscuro que corresponde a las preferencias (el alto contraste fuerza el oscuro). */
    fun nightMode(context: Context): Int = when {
        isHighContrast(context) -> AppCompatDelegate.MODE_NIGHT_YES
        themeChoice(context) == THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        themeChoice(context) == THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /** Debe llamarse antes de crear la pantalla; si el modo cambió, AppCompat la recrea sola. */
    fun applyNightMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(nightMode(context))
    }

    /** Contexto con el tamaño de texto de la cuenta y, si hace falta, la paleta de alto contraste. */
    fun wrap(base: Context): Context {
        val prefs = AccessibilityPrefs.of(base)
        val config = Configuration(base.resources.configuration)
        config.fontScale = prefs.getFloat(AccessibilityConfigActivity.KEY_FONT_SCALE, 1.15f)
        val roundBits = if (isHighContrast(base)) Configuration.SCREENLAYOUT_ROUND_YES else Configuration.SCREENLAYOUT_ROUND_NO
        config.screenLayout = (config.screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()) or roundBits
        return base.createConfigurationContext(config)
    }
}
