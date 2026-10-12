package com.coy.huellasegura

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.coy.huellasegura.databinding.ViewPendingBannerBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.firestore

object PendingSync {

    private const val WAIT_MS = 15_000L

    /**
     * Cuenta los reportes y solicitudes de perdidos de esta persona que siguen solo en el teléfono
     * (escritos sin internet y todavía sin confirmar por el servidor).
     */
    fun listen(email: String, onCount: (Int) -> Unit): List<ListenerRegistration> {
        val counts = intArrayOf(0, 0)
        val db = Firebase.firestore
        fun listener(index: Int, collection: String) =
            db.collection(collection).whereEqualTo("reportanteCorreo", email)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                    counts[index] = snapshot?.documents?.count { it.metadata.hasPendingWrites() } ?: 0
                    onCount(counts[0] + counts[1])
                }
        return listOf(listener(0, "reportes"), listener(1, "perdidos"))
    }

    /**
     * Reintenta ya el envío de lo pendiente: reinicia la conexión con Firestore (si no, esperaría su
     * propio plazo) y aguarda a que el servidor confirme todo. Devuelve false si no hay internet o
     * no se alcanzó a confirmar a tiempo (lo pendiente sigue guardado y se enviará solo).
     */
    fun sendNow(activity: Activity, onResult: (Boolean) -> Unit) {
        if (!OfflineWrite.hasInternet(activity)) {
            onResult(false)
            return
        }
        val db = Firebase.firestore
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        fun end(ok: Boolean) {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            onResult(ok)
        }
        handler.postDelayed({ end(false) }, WAIT_MS)
        db.disableNetwork()
            .continueWithTask { db.enableNetwork() }
            .continueWithTask { db.waitForPendingWrites() }
            .addOnSuccessListener { end(true) }
            .addOnFailureListener { end(false) }
    }
}

/** Aviso «Tienes N envíos pendientes» con el botón «Enviar ahora» (paneles del ciudadano). */
class PendingBanner(
    private val activity: Activity,
    private val views: ViewPendingBannerBinding,
    private val email: String,
) {
    private var listeners: List<ListenerRegistration> = emptyList()

    fun start() {
        if (email.isBlank()) return
        views.btnSendNow.setOnClickListener { sendNow() }
        listeners = PendingSync.listen(email) { count -> render(count) }
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners = emptyList()
    }

    private fun render(count: Int) {
        if (count <= 0) {
            views.root.visibility = View.GONE
            return
        }
        views.root.visibility = View.VISIBLE
        views.tvPendingTitle.text = activity.resources.getQuantityString(R.plurals.pending_banner_title, count, count)
    }

    private fun sendNow() {
        views.btnSendNow.isEnabled = false
        views.btnSendNow.setText(R.string.pending_banner_sending)
        PendingSync.sendNow(activity) { ok ->
            views.btnSendNow.isEnabled = true
            views.btnSendNow.setText(R.string.pending_banner_send_now)
            val message = when {
                ok -> R.string.pending_banner_sent
                !OfflineWrite.hasInternet(activity) -> R.string.pending_banner_no_internet
                else -> R.string.pending_banner_failed
            }
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }
    }
}
