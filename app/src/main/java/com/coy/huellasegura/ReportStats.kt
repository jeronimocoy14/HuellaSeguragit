package com.coy.huellasegura

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import java.text.Normalizer

/** Cálculos comunes de los reportes: los usan la pantalla de estadísticas y la exportación a Excel. */
object ReportStats {

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Tipo de un reporte para agrupar: Herido, Maltrato, Atropellado, Otro o aviso de perdido. */
    fun typeLabel(context: Context, doc: DocumentSnapshot): String {
        if (!doc.getString("perdidoId").isNullOrBlank()) return context.getString(R.string.resolved_filter_found)
        val tipo = doc.getString("tipo").orEmpty().trim()
        val known = listOf(R.string.report_type_hurt, R.string.report_type_abuse, R.string.report_type_hit)
            .map { context.getString(it) }
            .firstOrNull { normalize(it) == normalize(tipo) }
        return known ?: context.getString(R.string.report_type_other)
    }

    /** «1 d 4 h», «35 min»… */
    fun formatDuration(context: Context, millis: Long): String {
        val minutes = millis / 60000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> context.getString(R.string.stats_duration_lt_min)
            hours < 1 -> context.getString(R.string.stats_duration_min, minutes.toInt())
            days < 1 -> if (minutes % 60 == 0L) context.getString(R.string.stats_duration_h, hours.toInt())
            else context.getString(R.string.stats_duration_h_min, hours.toInt(), (minutes % 60).toInt())
            else -> if (hours % 24 == 0L) context.getString(R.string.stats_duration_d, days.toInt())
            else context.getString(R.string.stats_duration_d_h, days.toInt(), (hours % 24).toInt())
        }
    }

    fun createdAt(doc: DocumentSnapshot): Long? = doc.getTimestamp("fechaCreacion")?.toDate()?.time
}
