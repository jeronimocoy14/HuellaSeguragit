package com.coy.huellasegura

import android.content.Context
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Animales comunitarios: los que vive en la calle pero cuida todo el barrio. Los registra el personal. */
object Comunitarios {

    /** Los ciudadanos ven los que están a menos de este radio. */
    const val RADIUS_METERS = 1000.0

    /** Distancia en metros entre dos puntos (fórmula de haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Distancia de la persona a un animal comunitario, o null si no hay ubicación de alguno de los dos. */
    fun distanceTo(doc: com.google.firebase.firestore.DocumentSnapshot, here: android.location.Location?): Double? {
        val me = here ?: return null
        val lat = doc.getDouble("lat") ?: return null
        val lon = doc.getDouble("lon") ?: return null
        return distanceMeters(me.latitude, me.longitude, lat, lon)
    }

    /** «a 450 m» o «a 1,2 km» (se redondea de 50 en 50 m: no hace falta más precisión). */
    fun formatDistance(context: Context, meters: Double): String =
        if (meters < 1000) context.getString(R.string.comunitarios_distance_m, (Math.round(meters / 50.0) * 50).toInt().coerceAtLeast(50))
        else context.getString(R.string.comunitarios_distance_km, String.format(java.util.Locale("es"), "%.1f", meters / 1000))
}
