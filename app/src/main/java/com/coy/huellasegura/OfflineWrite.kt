package com.coy.huellasegura

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.tasks.Task

object OfflineWrite {

    private const val WAIT_MS = 6_000L

    fun hasInternet(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Firestore guarda cada escritura en su caché local y la envía sola cuando vuelve la señal, pero
     * la confirmación del servidor no llega hasta entonces. Con internet esperamos esa confirmación;
     * sin internet, o si tarda más de [WAIT_MS], seguimos y avisamos que quedó en cola
     * ([onDone] recibe queued = true). Un error real del servidor llega a [onError].
     */
    fun run(
        context: Context,
        write: Task<*>,
        onDone: (queued: Boolean) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        fun done(queued: Boolean) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            onDone(queued)
        }
        fun fail(error: Exception) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            onError(error)
        }

        write.addOnSuccessListener { done(false) }.addOnFailureListener { fail(it) }
        if (!hasInternet(context)) done(true) else handler.postDelayed({ done(true) }, WAIT_MS)
    }

    /**
     * Para cambios sin pantalla de confirmación propia (aceptar, rechazar, editar…): sin internet
     * avisa que quedó guardado en el teléfono y se enviará solo; con un error real lo dice;
     * si todo sale bien llama a [onSuccess].
     */
    fun notify(
        context: Context,
        write: Task<*>,
        queuedMessage: Int = R.string.write_queued,
        onSuccess: () -> Unit = {},
    ) {
        run(
            context,
            write,
            onDone = { queued ->
                if (queued) Toast.makeText(context, queuedMessage, Toast.LENGTH_LONG).show() else onSuccess()
            },
            onError = { Toast.makeText(context, R.string.write_error, Toast.LENGTH_SHORT).show() }
        )
    }
}
