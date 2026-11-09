package com.coy.huellasegura

import android.graphics.drawable.BitmapDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Categorías de animales y textos de distancia, con los recursos reales de la app. */
@RunWith(AndroidJUnit4::class)
class SpeciesAndDistanceTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun hayDoceCategoriasYLaUltimaEsOtro() {
        assertEquals(12, Species.all.size)
        assertEquals("Otro", context.getString(Species.all.last().nameRes))
    }

    @Test
    fun lasCategoriasSeBuscanPorSuNombreSinImportarMayusculas() {
        assertEquals(R.string.pets_species_cow, Species.find(context, "vaca").nameRes)
        assertEquals(R.string.pets_species_cow, Species.find(context, " VACA ").nameRes)
        assertEquals(R.string.pets_species_dog, Species.find(context, "Perro").nameRes)
    }

    @Test
    fun unaCategoriaDesconocidaCaeEnOtro() {
        assertEquals(R.string.pets_species_other, Species.find(context, "Dragón").nameRes)
        assertEquals(R.string.pets_species_other, Species.find(context, "").nameRes)
    }

    @Test
    fun todaCategoriaTieneUnIconoQueSePuedeDibujar() {
        Species.all.forEach { item ->
            val icon = Species.icon(context, item, 28)
            assertNotNull(context.getString(item.nameRes), icon)
        }
    }

    @Test
    fun lasCategoriasConEmojiSeDibujanComoImagen() {
        val icon = Species.icon(context, "Vaca", 28)
        assertTrue(icon is BitmapDrawable)
        assertTrue((icon as BitmapDrawable).bitmap.width > 0)
    }

    // ---- Texto de la distancia que ve el ciudadano

    @Test
    fun menosDeUnKilometroSeDiceEnMetrosRedondeadosDe50En50() {
        assertEquals("a 450 m", Comunitarios.formatDistance(context, 450.0))
        assertEquals("a 450 m", Comunitarios.formatDistance(context, 460.0))
        assertEquals("a 500 m", Comunitarios.formatDistance(context, 480.0))
    }

    @Test
    fun muyCercaNuncaDiceCeroMetros() {
        assertEquals("a 50 m", Comunitarios.formatDistance(context, 3.0))
    }

    @Test
    fun masDeUnKilometroSeDiceEnKilometrosConComa() {
        assertEquals("a 1,5 km", Comunitarios.formatDistance(context, 1500.0))
        assertEquals("a 2,0 km", Comunitarios.formatDistance(context, 2000.0))
    }
}
