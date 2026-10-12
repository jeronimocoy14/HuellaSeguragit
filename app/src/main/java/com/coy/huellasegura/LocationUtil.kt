package com.coy.huellasegura

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

object LocationUtil {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Pide una ubicación actual (GPS o red). Si no llega a tiempo usa la última conocida; si no hay
     * ninguna, entrega null. Siempre responde en el hilo principal.
     */
    @SuppressLint("MissingPermission")
    fun getCurrent(context: Context, timeoutMs: Long = 10_000, onResult: (Location?) -> Unit) {
        if (!hasPermission(context)) {
            onResult(null)
            return
        }
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val provider = providers.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
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
            onResult(location ?: lastKnown(manager, providers))
        }
        handler.postDelayed({
            cancel.cancel()
            finish(null)
        }, timeoutMs)
        runCatching {
            manager.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { finish(it) }
        }.onFailure { finish(null) }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
}
