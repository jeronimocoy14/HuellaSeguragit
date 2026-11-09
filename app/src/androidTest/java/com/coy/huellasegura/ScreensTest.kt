package com.coy.huellasegura

import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import org.hamcrest.Matcher
import org.hamcrest.Matchers.instanceOf
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Toca el enlace (ClickableSpan) con ese texto dentro de un TextView: el resto del texto no es tocable. */
private fun clickLink(text: String): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = instanceOf(TextView::class.java)
    override fun getDescription() = "tocar el enlace «$text»"
    override fun perform(uiController: UiController, view: View) {
        val spanned = (view as TextView).text as Spanned
        val span = spanned.getSpans(0, spanned.length, ClickableSpan::class.java)
            .first { spanned.substring(spanned.getSpanStart(it), spanned.getSpanEnd(it)) == text }
        span.onClick(view)
    }
}

/** Pantallas de la app abiertas de verdad en el emulador: se toca y se comprueba lo que se ve. */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

    @get:Rule
    val prefs = PrefsRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // ---- Inicio de sesión

    @Test
    fun elLoginMuestraElTituloYLosCamposDeEntrada() {
        Firebase.auth.signOut()
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withText("Inicia sesión")).check(matches(isDisplayed()))
            onView(withId(R.id.etCorreo)).check(matches(isDisplayed()))
            onView(withId(R.id.etPassword)).check(matches(isDisplayed()))
            onView(withText("Ingresar")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun alElegirCrearCuentaAparecenNombreYApellido() {
        Firebase.auth.signOut()
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withId(R.id.etNombre)).check(matches(not(isDisplayed())))
            onView(withId(R.id.tvCrearCuenta)).perform(scrollTo(), clickLink("Crear cuenta"))
            onView(withId(R.id.etNombre)).check(matches(isDisplayed()))
            onView(withId(R.id.etApellido)).check(matches(isDisplayed()))
        }
    }

    // ---- Agregar mascota (veterinario)

    @Test
    fun elSelectorMuestraLasDoceEspecies() {
        ActivityScenario.launch(AgregarMascotaActivity::class.java).use {
            listOf("Perro", "Gato", "Conejo", "Ave", "Caballo", "Vaca", "Cerdo", "Oveja", "Cabra", "Roedor", "Reptil", "Otro")
                .forEach { onView(withText(it)).perform(scrollTo()).check(matches(isDisplayed())) }
        }
    }

    @Test
    fun unCorreoDeDuenoInvalidoMuestraSuErrorYNoGuarda() {
        ActivityScenario.launch(AgregarMascotaActivity::class.java).use {
            onView(withId(R.id.etCorreoDueno)).perform(typeText("esto-no-es-un-correo"), closeSoftKeyboard())
            onView(withText("Guardar mascota")).perform(scrollTo(), click())
            onView(withId(R.id.etCorreoDueno)).check(matches(hasErrorText(context.getString(R.string.pets_error_owner_email_invalid))))
        }
    }

    @Test
    fun sinCorreoDeDuenoPideEscribirlo() {
        ActivityScenario.launch(AgregarMascotaActivity::class.java).use {
            onView(withText("Guardar mascota")).perform(scrollTo(), click())
            onView(withId(R.id.etCorreoDueno)).check(matches(hasErrorText(context.getString(R.string.pets_error_owner_email))))
        }
    }

    // ---- Ajustes de accesibilidad

    @Test
    fun elegirElTemaOscuroLoGuardaEnLasPreferencias() {
        prefs.clear()
        ActivityScenario.launch(AccessibilityConfigActivity::class.java).use {
            onView(withId(R.id.chipThemeDark)).perform(scrollTo(), click())
        }
        assertEquals(AccessibilityTheme.THEME_DARK, AccessibilityPrefs.of(context).getString(AccessibilityTheme.KEY_THEME, ""))
    }

    @Test
    fun encenderElAltoContrasteLoGuardaYLaPantallaSigueFuncionando() {
        prefs.clear()
        ActivityScenario.launch(AccessibilityConfigActivity::class.java).use { scenario ->
            onView(withId(R.id.switchHighContrast)).perform(scrollTo(), click())
            // La pantalla se recrea con la paleta nueva y debe seguir mostrando sus opciones
            onView(withText("Alto contraste")).check(matches(isDisplayingAtLeast(1)))
            scenario.onActivity { assertTrue(AccessibilityTheme.isHighContrast(it)) }
        }
        assertTrue(AccessibilityPrefs.of(context).getBoolean(AccessibilityConfigActivity.KEY_HIGH_CONTRAST, false))
    }

    @Test
    fun elegirTextoMuyGrandeGuardaLaEscala() {
        prefs.clear()
        ActivityScenario.launch(AccessibilityConfigActivity::class.java).use {
            onView(withId(R.id.chipTextXLarge)).perform(scrollTo(), click())
        }
        assertEquals(1.3f, AccessibilityPrefs.of(context).getFloat(AccessibilityConfigActivity.KEY_FONT_SCALE, 0f), 0.001f)
    }

    @Test
    fun elModoParaDaltonismoGuardaElTipoElegido() {
        prefs.clear()
        ActivityScenario.launch(AccessibilityConfigActivity::class.java).use {
            onView(withId(R.id.chipDeuteranopia)).perform(scrollTo(), click())
        }
        assertEquals("Deuteranopia", AccessibilityPrefs.of(context).getString(AccessibilityConfigActivity.KEY_COLORBLIND_MODE, ""))
    }
}
