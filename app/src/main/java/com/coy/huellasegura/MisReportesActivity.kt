package com.coy.huellasegura

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import com.coy.huellasegura.databinding.ActivityMisReportesBinding
import com.coy.huellasegura.databinding.ItemMiReporteBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

class MisReportesActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityMisReportesBinding
    private var listener: ListenerRegistration? = null
    private var userEmail = ""
    private var userName = ""
    private var animated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMisReportesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        userEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase()
        userName = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()
        binding.btnBack.setOnClickListener { finish() }

        if (userEmail.isBlank()) {
            render(emptyList())
            return
        }
        binding.listContainer.showStatusMessage(getString(R.string.list_loading))
        listener = Firebase.firestore.collection("reportes")
            .whereEqualTo("reportanteCorreo", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null && snapshot == null) {
                    binding.listContainer.showStatusMessage(getString(R.string.list_error))
                    return@addSnapshotListener
                }
                render(snapshot?.documents.orEmpty())
            }
    }

    private fun render(docs: List<DocumentSnapshot>) {
        binding.listContainer.removeAllViews()
        if (docs.isEmpty()) {
            binding.listContainer.addView(TextView(this).apply {
                text = getString(R.string.reports_empty)
                setTextColor(ContextCompat.getColor(this@MisReportesActivity, R.color.text_secondary))
                textSize = 14f
                setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            return
        }

        val (active, done) = docs
            .sortedByDescending { it.createdAtMillis() }
            .partition { it.getString("estado") != "resuelto" }
        val cards = (active + done).map { addCard(it) }

        if (!animated) {
            animated = true
            val distance = 8 * resources.displayMetrics.density
            cards.forEachIndexed { i, card -> card.fadeSlideIn(i, distance) }
        }
    }

    private fun addCard(doc: DocumentSnapshot): View {
        val item = ItemMiReporteBinding.inflate(layoutInflater, binding.listContainer, false)
        val estado = doc.getString("estado").orEmpty()

        val iconRes: Int
        val iconTint: Int
        val badgeBg: Int
        val pillBg: Int
        val pillText: Int
        val label: Int
        val detail: Int
        when (estado) {
            "en_atencion" -> {
                iconRes = R.drawable.ic_profile; iconTint = R.color.accent_green; badgeBg = R.color.accent_soft
                pillBg = R.drawable.bg_tag_pill; pillText = R.color.brand_dark
                label = R.string.reports_status_attending; detail = R.string.reports_detail_attending
            }
            "resuelto" -> {
                iconRes = R.drawable.ic_check_circle; iconTint = R.color.accent_green; badgeBg = R.color.accent_soft
                pillBg = R.drawable.bg_tag_pill; pillText = R.color.brand_dark
                label = R.string.reports_status_resolved; detail = R.string.reports_detail_resolved
            }
            else -> {
                iconRes = R.drawable.ic_warning; iconTint = R.color.offline_notice_icon; badgeBg = R.color.offline_notice_bg
                pillBg = R.drawable.bg_pill_pending; pillText = R.color.offline_notice_text
                label = R.string.reports_status_waiting; detail = R.string.reports_detail_waiting
            }
        }

        item.flBadge.background = GradientDrawable().apply {
            cornerRadius = 12 * resources.displayMetrics.density
            setColor(ContextCompat.getColor(this@MisReportesActivity, badgeBg))
        }
        item.ivIcon.setImageResource(iconRes)
        ImageViewCompat.setImageTintList(item.ivIcon, ColorStateList.valueOf(ContextCompat.getColor(this, iconTint)))

        item.tvTitulo.text = doc.getString("tipo").orEmpty().ifBlank { getString(R.string.report_type_other) }
        val place = doc.getString("direccion").orEmpty().ifBlank { doc.getString("referencia").orEmpty() }
        val time = doc.getTimestamp("fechaCreacion")?.let { relativeTime(it.toDate().time) }.orEmpty()
        item.tvSubtitulo.maxLines = 2
        item.tvSubtitulo.ellipsize = android.text.TextUtils.TruncateAt.END
        item.tvSubtitulo.text = listOf(place, time).filter { it.isNotBlank() }.joinToString(" · ")

        item.tvPill.setText(label)
        item.tvPill.setBackgroundResource(pillBg)
        item.tvPill.setTextColor(ContextCompat.getColor(this, pillText))
        item.tvDetalle.visibility = View.VISIBLE
        item.tvDetalle.setText(detail)

        if (estado == "en_atencion" || estado == "resuelto") {
            item.btnChat.visibility = View.VISIBLE
            item.btnChat.setOnClickListener {
                startActivity(Intent(this, ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_CHAT_ID, userEmail)
                    putExtra(ChatActivity.EXTRA_ROLE, ChatActivity.ROLE_CIUDADANO)
                    putExtra(ChatActivity.EXTRA_MY_NAME, userName)
                    putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
                })
            }
        }

        binding.listContainer.addView(item.root)
        return item.root
    }

    private fun relativeTime(epochMillis: Long): String {
        val minutes = (System.currentTimeMillis() - epochMillis) / 60000
        return when {
            minutes < 1 -> getString(R.string.time_just_now)
            minutes < 60 -> getString(R.string.time_minutes_ago, minutes.toInt())
            minutes < 60 * 24 -> getString(R.string.time_hours_ago, (minutes / 60).toInt())
            else -> getString(R.string.time_days_ago, (minutes / (60 * 24)).toInt())
        }
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}
