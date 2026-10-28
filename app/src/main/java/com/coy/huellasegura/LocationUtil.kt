package com.coy.huellasegura

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal

object LocationUtil {

    private const val MAX_AGE_MS = 120_000L
    private const val TAG = "LocationUtil"

    /**
     * Los dos permisos de ubicación. Desde Android 12 la persona puede dar solo la ubicación
     * aproximada, así que se piden juntos y basta con que se conceda uno.
     */
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasFinePermission(context: Context): Boolean = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermission(context: Context): Boolean =
        hasFinePermission(context) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * Pide una ubicación actual (GPS o red). Si no llega a tiempo usa la última conocida solo si es
     * reciente (menos de [MAX_AGE_MS]); una ubicación vieja podría mandar a alguien al lugar equivocado.
     * Si no hay ninguna válida, entrega null. Siempre responde en el hilo principal.
     */
    @SuppressLint("MissingPermission")
    fun getCurrent(context: Context, timeoutMs: Long = 10_000, onResult: (Location?) -> Unit) {
        if (!hasPermission(context)) {
            onResult(null)
            return
        }
        val manager = context.getSystemService(LocationManager::class.java)
        // Con solo la ubicación aproximada Android no deja usar el GPS: se usa el proveedor combinado
        // (Android 12 o más) y la red.
        val providers = when {
            hasFinePermission(context) -> listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
            else -> listOf(LocationManager.NETWORK_PROVIDER)
        }
        val provider = providers.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        Log.d(TAG, "permiso preciso=${hasFinePermission(context)} proveedores=$providers elegido=$provider")
        if (provider == null) {
            onResult(lastKnown(manager, providers))
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val cancel = CancellationSignal()
        var done = false
        fun finish(location: Location?) {
            if (done) return
            done = true
            handler.removeCallbacksAndMessages(null)
            val result = location ?: lastKnown(manager, providers)
            Log.d(TAG, "actual=${location != null} resultado=${result != null}")
            onResult(result)
        }
        handler.postDelayed({
            cancel.cancel()
            finish(null)
        }, timeoutMs)
        runCatching {
            // LocationManager.getCurrentLocation existe desde Android 11; la versión de AndroidX hace lo mismo
            // en cualquier versión (en las antiguas usa una sola actualización de ubicación).
            LocationManagerCompat.getCurrentLocation(manager, provider, cancel, ContextCompat.getMainExecutor(context)) { finish(it) }
        }.onFailure {
            Log.w(TAG, "No se pudo pedir la ubicación: $it")
            finish(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { SystemClock.elapsedRealtime() - it.elapsedRealtimeNanos / 1_000_000 <= MAX_AGE_MS }
            .maxByOrNull { it.time }
}
