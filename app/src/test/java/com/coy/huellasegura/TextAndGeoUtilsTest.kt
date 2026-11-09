package com.coy.huellasegura

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Utilidades pequeñas que se usan en muchas pantallas: normalizar texto, edades y enlaces de mapa. */
class TextAndGeoUtilsTest {

    // ---- Normalizar texto (filtros y estadísticas comparan tipos de reporte sin importar tildes ni mayúsculas)

    @Test
    fun `normalizar quita tildes y pasa a minusculas`() {
        assertEquals("atropellado", ReportStats.normalize("ATROPELLADO"))
        assertEquals("maltrato", ReportStats.normalize("Maltráto"))
        assertEquals("nino pequeno", ReportStats.normalize("Niño Pequeño"))
    }

    @Test
    fun `dos escrituras del mismo tipo de reporte se igualan`() {
        assertEquals(ReportStats.normalize("Está herido"), ReportStats.normalize("esta HERIDO"))
    }

    // ---- Edad de la mascota

    @Test
    fun `una edad en numero lleva su unidad`() {
        assertEquals("3 años", "3".edadConUnidad())
        assertEquals("13 años", " 13 ".edadConUnidad())
    }

    @Test
    fun `un año va en singular`() {
        assertEquals("1 año", "1".edadConUnidad())
    }

    @Test
    fun `si la edad ya trae texto se deja igual`() {
        assertEquals("6 meses", "6 meses".edadConUnidad())
        assertEquals("", "".edadConUnidad())
    }

    // ---- Enlaces y coordenadas

    @Test
    fun `el enlace al mapa lleva las coordenadas con punto decimal`() {
        val url = GeoUtil.mapsUrl(5.0221, -74.0058)
        assertTrue(url, url.startsWith("https://www.google.com/maps/search/?api=1&query="))
        assertTrue(url, url.endsWith("5.022100,-74.005800"))
    }

    @Test
    fun `las coordenadas se muestran con cinco decimales`() {
        assertEquals("5.02210, -74.00580", GeoUtil.formatCoordinates(5.0221, -74.0058))
    }
}
