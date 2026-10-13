package com.coy.huellasegura

import android.app.Activity
import android.view.View
import com.coy.huellasegura.databinding.ViewNewsBannerBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.firestore

/**
 * Aviso en el panel del ciudadano cuando un funcionario atiende sus reportes o revisa sus
 * solicitudes de animal perdido («Un funcionario tomó tu reporte…», «Tu solicitud fue publicada»).
 *
 * Se guarda en el teléfono el último estado que la persona ya vio de cada reporte; una novedad es
 * cualquier reporte cuyo estado actual es otro. La primera vez en una cuenta solo se toma nota del
 * estado actual, para no llenar el panel de avisos viejos.
 */
class StatusNews(
    private val activity: Activity,
    private val views: ViewNewsBannerBinding,
    private val email: String,
    private val onOpen: (Kind) -> Unit,
) {
    enum class Kind { REPORTS, LOST }

    private class Item(val key: String, val estado: String, val message: String, val kind: Kind)

    private val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
    private var listeners: List<ListenerRegistration> = emptyList()
    private val items = arrayOf<List<Item>>(emptyList(), emptyList())

    fun start() {
        if (email.isBlank()) return
        views.btnNewsView.setOnClickListener {
            val kind = if (items.any { list -> list.any { it.kind == Kind.REPORTS } }) Kind.REPORTS else Kind.LOST
            markSeen()
            onOpen(kind)
        }
        views.btnNewsClose.setOnClickListener { markSeen() }

        val db = Firebase.firestore
        listeners = listOf(
            db.collection("reportes").whereEqualTo("reportanteCorreo", email)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                    if (snapshot != null) update(0, "r", snapshot.documents, Kind.REPORTS)
                },
            db.collection("perdidos").whereEqualTo("reportanteCorreo", email)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                    if (snapshot != null) update(1, "p", snapshot.documents, Kind.LOST)
                }
        )
    }

    fun stop() {
        listeners.forEach { it.remove() }
        listeners = emptyList()
    }

    private fun seen(): MutableMap<String, String> =
        prefs.getStringSet(seenKey(), emptySet()).orEmpty()
            .mapNotNull { entry -> entry.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap().toMutableMap()

    private fun saveSeen(seen: Map<String, String>) {
        prefs.edit().putStringSet(seenKey(), seen.map { "${it.key}=${it.value}" }.toSet()).apply()
    }

    private fun seenKey() = "seen_$email"
    private fun baselineKey(prefix: String) = "baseline_${prefix}_$email"

    private fun update(index: Int, prefix: String, docs: List<DocumentSnapshot>, kind: Kind) {
        // Lo escrito en este teléfono y aún sin confirmar no es una novedad del servidor.
        val confirmed = docs.filter { !it.metadata.hasPendingWrites() }
        val seen = seen()

        if (!prefs.getBoolean(baselineKey(prefix), false)) {
            confirmed.forEach { seen["$prefix:${it.id}"] = it.getString("estado").orEmpty() }
            saveSeen(seen)
            prefs.edit().putBoolean(baselineKey(prefix), true).apply()
            items[index] = emptyList()
            render()
            return
        }

        val news = mutableListOf<Item>()
        var changed = false
        for (doc in confirmed) {
            val key = "$prefix:${doc.id}"
            val estado = doc.getString("estado").orEmpty()
            val before = seen[key]
            if (before == estado) continue
            val message = if (estado in NOTIFY) messageFor(doc, kind, estado) else null
            // Reportes nuevos (pendientes) y cambios que no merecen aviso se anotan sin avisar.
            if (message == null || (before == null && estado == "pendiente")) {
                seen[key] = estado
                changed = true
            } else {
                news.add(Item(key, estado, message, kind))
            }
        }
        if (changed) saveSeen(seen)
        items[index] = news
        render()
    }

    private fun messageFor(doc: DocumentSnapshot, kind: Kind, estado: String): String? {
        if (kind == Kind.LOST) {
            val nombre = doc.getString("nombre").orEmpty()
            return when (estado) {
                "publicado" -> activity.getString(R.string.news_lost_published, nombre)
                "rechazado" -> activity.getString(R.string.news_lost_rejected, nombre)
                else -> null
            }
        }
        val animal = doc.getString("perdidoNombre")
        if (!animal.isNullOrBlank()) {
            return when (estado) {
                "en_atencion" -> activity.getString(R.string.news_found_taken, animal)
                "resuelto" -> activity.getString(R.string.news_found_resolved, animal)
                else -> null
            }
        }
        val tipo = doc.getString("tipo").orEmpty().ifBlank { activity.getString(R.string.report_type_other) }
        return when (estado) {
            "en_atencion" -> activity.getString(R.string.news_report_taken, tipo)
            "resuelto" -> activity.getString(R.string.news_report_resolved, tipo)
            else -> null
        }
    }

    private fun markSeen() {
        val seen = seen()
        items.forEach { list -> list.forEach { seen[it.key] = it.estado } }
        saveSeen(seen)
        items.fill(emptyList())
        render()
    }

    private fun render() {
        val all = items.flatMap { it }
        if (all.isEmpty()) {
            views.root.visibility = View.GONE
            return
        }
        views.root.visibility = View.VISIBLE
        if (all.size == 1) {
            views.tvNewsTitle.text = all[0].message
            views.tvNewsSubtitle.visibility = View.GONE
        } else {
            views.tvNewsTitle.text = activity.resources.getQuantityString(R.plurals.news_title_many, all.size, all.size)
            views.tvNewsSubtitle.visibility = View.VISIBLE
        }
    }

    private companion object {
        const val PREFS = "status_news"
        val NOTIFY = setOf("en_atencion", "resuelto", "publicado", "rechazado")
    }
}
