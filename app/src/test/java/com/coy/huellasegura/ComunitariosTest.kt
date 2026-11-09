package com.coy.huellasegura

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Distancias y estado de salud de los animales comunitarios (la base de «a menos de 1 km de ti»). */
class ComunitariosTest {

    private val zipaquiraLat = 5.0221
    private val zipaquiraLon = -74.0058

    @Test
    fun `la distancia de un punto a si mismo es cero`() {
        assertEquals(0.0, Comunitarios.distanceMeters(zipaquiraLat, zipaquiraLon, zipaquiraLat, zipaquiraLon), 0.001)
    }

    @Test
    fun `un grado de latitud son unos 111 km`() {
        val meters = Comunitarios.distanceMeters(5.0, -74.0, 6.0, -74.0)
        assertEquals(111_195.0, meters, 300.0)
    }

    @Test
    fun `Zipaquira queda a unos 35 km de Bogota`() {
        val meters = Comunitarios.distanceMeters(zipaquiraLat, zipaquiraLon, 4.7110, -74.0721)
        assertTrue("distancia = $meters", meters in 34_000.0..37_000.0)
    }

    @Test
    fun `la distancia es la misma de ida y de vuelta`() {
        val ida = Comunitarios.distanceMeters(zipaquiraLat, zipaquiraLon, 5.0400, -73.9900)
        val vuelta = Comunitarios.distanceMeters(5.0400, -73.9900, zipaquiraLat, zipaquiraLon)
        assertEquals(ida, vuelta, 0.001)
    }

    @Test
    fun `un animal a 500 m entra en el radio y uno a 2 km no`() {
        // 0,0045 grados de latitud son unos 500 m; 0,018 grados, unos 2 km
        val cerca = Comunitarios.distanceMeters(zipaquiraLat, zipaquiraLon, zipaquiraLat + 0.0045, zipaquiraLon)
        val lejos = Comunitarios.distanceMeters(zipaquiraLat, zipaquiraLon, zipaquiraLat + 0.018, zipaquiraLon)
        assertTrue("cerca = $cerca", cerca <= Comunitarios.RADIUS_METERS)
        assertTrue("lejos = $lejos", lejos > Comunitarios.RADIUS_METERS)
    }

    @Test
    fun `el radio para los vecinos es de 1 km`() {
        assertEquals(1000.0, Comunitarios.RADIUS_METERS, 0.0)
    }

    @Test
    fun `salud completa si esta esterilizado y vacunado`() {
        assertEquals(Comunitarios.Health.COMPLETE, Comunitarios.healthOf(sterilized = true, vaccinated = true))
    }

    @Test
    fun `salud parcial si le falta una de las dos cosas`() {
        assertEquals(Comunitarios.Health.PARTIAL, Comunitarios.healthOf(sterilized = true, vaccinated = false))
        assertEquals(Comunitarios.Health.PARTIAL, Comunitarios.healthOf(sterilized = false, vaccinated = true))
    }

    @Test
    fun `salud pendiente si no tiene ninguna`() {
        assertEquals(Comunitarios.Health.NONE, Comunitarios.healthOf(sterilized = false, vaccinated = false))
    }
}
