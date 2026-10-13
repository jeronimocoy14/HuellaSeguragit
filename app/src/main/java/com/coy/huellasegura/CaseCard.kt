package com.coy.huellasegura

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import com.coy.huellasegura.databinding.ItemProfessionalCaseBinding
import com.google.firebase.firestore.DocumentSnapshot

/** Parte común de la tarjeta de un caso (bandeja del funcionario y casos resueltos). */
object CaseCard {

    /**
     * Rellena título, lugar, foto, mapa y chat. [timeMillis] es la hora que se muestra junto al lugar
     * (por defecto cuándo se creó el reporte) y [extra] una línea más al final.
     */
    fun bind(
        activity: Activity,
        item: ItemProfessionalCaseBinding,
        doc: DocumentSnapshot,
        title: String,
        funcionarioEmail: String?,
        timeText: String? = null,
        extra: String = "",
    ) {
        item.tvCaseTitle.text = title

        val lat = doc.getDouble("lat")
        val lon = doc.getDouble("lon")
        val hasCoordinates = lat != null && lon != null
        val reference = doc.getString("referencia").orEmpty()
        val address = doc.getString("direccion").orEmpty()
        val place = when {
            address.isNotBlank() -> address
            reference.isNotBlank() -> reference
            hasCoordinates -> activity.getString(R.string.professional_location_gps, GeoUtil.formatCoordinates(lat!!, lon!!))
            else -> activity.getString(R.string.professional_no_location)
        }
        val time = timeText ?: doc.getTimestamp("fechaCreacion")?.let { relativeTime(activity, it.toDate().time) }.orEmpty()
        item.tvCaseSubtitle.text = listOf(place, time, extra).filter { it.isNotBlank() }.joinToString(" · ")

        if (hasCoordinates) {
            item.btnCaseMap.visibility = View.VISIBLE
            item.btnCaseMap.setOnClickListener {
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GeoUtil.mapsUrl(lat!!, lon!!))))
                }.onFailure { Toast.makeText(activity, R.string.report_send_error, Toast.LENGTH_SHORT).show() }
            }
        }

        val photo = PhotoUtil.decode(doc.getString("fotoBase64"), doc.id)
        if (photo != null) {
            item.ivCasePhoto.visibility = View.VISIBLE
            item.ivCasePhoto.setImageBitmap(photo)
            item.ivCasePhoto.setOnClickListener {
                val view = ImageView(activity).apply {
                    setImageBitmap(photo)
                    adjustViewBounds = true
                    val pad = (8 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                }
                activity.styledDialog()
                    .setTitle(R.string.professional_evidence_title)
                    .setView(view)
                    .setPositiveButton(R.string.btn_close, null)
                    .show()
            }
        }

        val reporterEmail = doc.getString("reportanteCorreo").orEmpty()
        if (reporterEmail.isNotBlank()) {
            item.btnCaseChat.visibility = View.VISIBLE
            item.btnCaseChat.setOnClickListener {
                activity.startActivity(Intent(activity, ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_CHAT_ID, reporterEmail)
                    putExtra(ChatActivity.EXTRA_ROLE, ChatActivity.ROLE_FUNCIONARIO)
                    putExtra(ChatActivity.EXTRA_PEER_NAME, doc.getString("reportanteNombre").orEmpty())
                    putExtra(ProfileActivity.EXTRA_USER_EMAIL, funcionarioEmail)
                })
            }
        }
    }

    fun relativeTime(activity: Activity, epochMillis: Long): String {
        val minutes = (System.currentTimeMillis() - epochMillis) / 60000
        return when {
            minutes < 1 -> activity.getString(R.string.time_just_now)
            minutes < 60 -> activity.getString(R.string.time_minutes_ago, minutes.toInt())
            minutes < 60 * 24 -> activity.getString(R.string.time_hours_ago, (minutes / 60).toInt())
            else -> activity.getString(R.string.time_days_ago, (minutes / (60 * 24)).toInt())
        }
    }
}
