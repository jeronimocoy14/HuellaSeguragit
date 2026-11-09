package com.coy.huellasegura

import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Motor de los modos de ayuda en un teléfono de verdad: tema claro/oscuro, alto contraste, texto y daltonismo. */
@RunWith(AndroidJUnit4::class)
class AccessibilityEngineTest {

    @get:Rule
    val prefs = PrefsRule()

    private fun set(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.clear()
        AccessibilityPrefs.of(prefs.context).edit().apply(block).commit()
    }

    // ---- Tema

    @Test
    fun sinPreferenciasElTemaSigueAlTelefono() {
        prefs.clear()
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AccessibilityTheme.nightMode(prefs.context))
    }

    @Test
    fun elTemaOscuroYElClaroSeForzan() {
        set { putString(AccessibilityTheme.KEY_THEME, AccessibilityTheme.THEME_DARK) }
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AccessibilityTheme.nightMode(prefs.context))
        set { putString(AccessibilityTheme.KEY_THEME, AccessibilityTheme.THEME_LIGHT) }
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AccessibilityTheme.nightMode(prefs.context))
    }

    @Test
    fun elAltoContrasteSiempreUsaElTemaOscuroAunqueSePidaClaro() {
        set {
            putString(AccessibilityTheme.KEY_THEME, AccessibilityTheme.THEME_LIGHT)
            putBoolean(AccessibilityConfigActivity.KEY_HIGH_CONTRAST, true)
        }
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AccessibilityTheme.nightMode(prefs.context))
    }

    // ---- Texto y paleta

    @Test
    fun elTamanoDeTextoElegidoSeAplicaAlContexto() {
        set { putFloat(AccessibilityConfigActivity.KEY_FONT_SCALE, 1.3f) }
        val context = AccessibilityTheme.wrap(prefs.context)
        assertEquals(1.3f, context.resources.configuration.fontScale, 0.001f)
    }

    @Test
    fun sinPreferenciasElTextoEsUnPocoMasGrande() {
        prefs.clear()
        val context = AccessibilityTheme.wrap(prefs.context)
        assertEquals(1.15f, context.resources.configuration.fontScale, 0.001f)
    }

    @Test
    fun elAltoContrasteActivaSuPaletaYSinElNoQuedaNada() {
        set { putBoolean(AccessibilityConfigActivity.KEY_HIGH_CONTRAST, true) }
        val conContraste = AccessibilityTheme.wrap(prefs.context)
        assertEquals(Configuration.SCREENLAYOUT_ROUND_YES, conContraste.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK)
        // Con la paleta de alto contraste el fondo es negro puro y el texto blanco puro
        assertEquals(0xFF000000.toInt(), conContraste.getColor(R.color.surface_background))
        assertEquals(0xFFFFFFFF.toInt(), conContraste.getColor(R.color.text_primary))

        prefs.clear()
        val normal = AccessibilityTheme.wrap(prefs.context)
        assertEquals(Configuration.SCREENLAYOUT_ROUND_NO, normal.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK)
    }

    // ---- Daltonismo: los estados no dependen solo del color

    private fun pill(kind: StatusStyle.Kind): TextView {
        val view = TextView(prefs.context)
        StatusStyle.apply(view, kind)
        return view
    }

    @Test
    fun sinDaltonismoLaEtiquetaDeEstadoNoLlevaIcono() {
        set { }
        val view = pill(StatusStyle.Kind.OK)
        assertNull(view.compoundDrawablesRelative[0])
    }

    @Test
    fun conDaltonismoCadaEstadoLlevaSuIconoYSuColor() {
        set { putString(AccessibilityConfigActivity.KEY_COLORBLIND_MODE, "Deuteranopia") }
        val ok = pill(StatusStyle.Kind.OK)
        val waiting = pill(StatusStyle.Kind.WAITING)
        val danger = pill(StatusStyle.Kind.DANGER)
        listOf(ok, waiting, danger).forEach { assertNotNull(it.compoundDrawablesRelative[0]) }

        fun color(view: TextView) = (view.background as GradientDrawable).color!!.defaultColor
        assertEquals(0xFF0072B2.toInt(), color(ok))       // azul
        assertEquals(0xFFF0E442.toInt(), color(waiting))  // amarillo
        assertEquals(0xFFD55E00.toInt(), color(danger))   // bermellón
        assertTrue("los tres estados deben verse distintos", setOf(color(ok), color(waiting), color(danger)).size == 3)
    }

    @Test
    fun elModoConDibujosTambienPoneElIcono() {
        set { putBoolean(AccessibilityConfigActivity.KEY_DRAWINGS, true) }
        assertNotNull(pill(StatusStyle.Kind.WAITING).compoundDrawablesRelative[0])
    }
}
