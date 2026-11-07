package com.coy.huellasegura

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object GeoUtil {
    private const val USER_AGENT = "HuellaSegura-App/1.0"

    /** Dirección legible de unas coordenadas (OpenStreetMap). Bloquea: llamar desde un hilo secundario. */
    fun reverseGeocode(lat: Double, lon: Double, timeoutMs: Int = 8000): String? = runCatching {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&addressdetails=0")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(response).optString("display_name").ifBlank { null }
    }.onFailure { Log.w("GeoUtil", "No se pudo obtener la dirección: $it") }.getOrNull()

    class Place(val lat: Double, val lon: Double, val name: String)

    /** Busca una dirección escrita y devuelve su punto (OpenStreetMap, en Colombia). Bloquea: usar un hilo secundario. */
    fun geocode(query: String, timeoutMs: Int = 8000): Place? = runCatching {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=co&q=$q")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val first = org.json.JSONArray(response).optJSONObject(0)
        first?.let { Place(it.getString("lat").toDouble(), it.getString("lon").toDouble(), it.optString("display_name")) }
    }.onFailure { Log.w("GeoUtil", "No se pudo buscar la dirección: $it") }.getOrNull()

    /** Enlace para abrir el punto en la app de mapas o en el navegador. */
    fun mapsUrl(lat: Double, lon: Double): String =
        String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", lat, lon)

    fun formatCoordinates(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon)
}
