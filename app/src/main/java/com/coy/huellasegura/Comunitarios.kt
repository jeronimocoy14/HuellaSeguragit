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

    enum class Health { COMPLETE, PARTIAL, NONE }

    /** Cuánto le falta a un animal: esterilizado y vacunado (completo), uno de los dos (parcial) o ninguno. */
    fun healthOf(doc: com.google.firebase.firestore.DocumentSnapshot): Health {
        val done = listOf(doc.getBoolean("esterilizado") == true, doc.getBoolean("vacunado") == true).count { it }
        return when (done) {
            2 -> Health.COMPLETE
            1 -> Health.PARTIAL
            else -> Health.NONE
        }
    }

    /** Diálogo para marcar si el animal está esterilizado y vacunado; guarda con la misma lógica sin conexión de la app. */
    fun editHealth(activity: android.app.Activity, doc: com.google.firebase.firestore.DocumentSnapshot) {
        val labels = arrayOf(activity.getString(R.string.comunitarios_sterilized), activity.getString(R.string.comunitarios_vaccinated))
        val checked = booleanArrayOf(doc.getBoolean("esterilizado") == true, doc.getBoolean("vacunado") == true)
        activity.styledDialog()
            .setTitle(activity.getString(R.string.comunitarios_health_title, doc.getString("nombre").orEmpty()))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton(R.string.btn_cancelar, null)
            .setPositiveButton(R.string.comunitarios_save) { _, _ ->
                OfflineWrite.notify(activity, doc.reference.update(mapOf("esterilizado" to checked[0], "vacunado" to checked[1])))
            }
            .show()
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
