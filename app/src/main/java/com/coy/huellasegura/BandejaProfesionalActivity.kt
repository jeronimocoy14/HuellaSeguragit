package com.coy.huellasegura

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityBandejaProfesionalBinding
import com.coy.huellasegura.databinding.ItemProfessionalCaseBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

class BandejaProfesionalActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityBandejaProfesionalBinding
    private var onShift = true
    private var casesListener: ListenerRegistration? = null
    private var perdidosListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBandejaProfesionalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnHelpAccess.setOnClickListener { openAccessibilitySettings() }
        binding.btnProfile.setOnClickListener { openProfile() }

        binding.tvToggleShift.setOnClickListener { toggleShift() }

        val comingSoon = listOf(binding.tabInicio, binding.tabMisReportes)
        comingSoon.forEach { view ->
            view.setOnClickListener { Toast.makeText(this, R.string.coming_soon_feature, Toast.LENGTH_SHORT).show() }
        }

        binding.tabChat.setOnClickListener {
            startActivity(Intent(this, ChatsFuncionarioActivity::class.java).apply {
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
            })
        }

        binding.cardPerdidos.setOnClickListener {
            startActivity(Intent(this, PerdidosModeracionActivity::class.java).apply {
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
            })
        }
        listenForPerdidos()

        binding.tabAjustes.setOnClickListener { openAccessibilitySettings() }

        listenForCases()
    }

    private fun listenForCases() {
        binding.casesContainer.showStatusMessage(getString(R.string.list_loading))
        casesListener = Firebase.firestore.collection("reportes")
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null && snapshot == null) {
                    binding.casesContainer.showStatusMessage(getString(R.string.list_error))
                    return@addSnapshotListener
                }
                renderCases(snapshot?.documents.orEmpty())
            }
    }

    private fun listenForPerdidos() {
        perdidosListener = Firebase.firestore.collection("perdidos")
            .whereEqualTo("estado", "pendiente")
            .addSnapshotListener { snapshot, _ ->
                val count = snapshot?.size() ?: 0
                binding.tvPerdidosCount.text = if (count == 0) getString(R.string.perdidos_inbox_none)
                else getString(R.string.perdidos_inbox_count, count)
                binding.dotPerdidos.visibility = if (count == 0) View.INVISIBLE else View.VISIBLE
            }
    }

    private fun renderCases(docs: List<DocumentSnapshot>) {
        val pending = docs.filter { it.getString("estado") == "pendiente" }
        val attending = docs.filter { it.getString("estado") == "en_atencion" }

        binding.tvCountPending.text = getString(R.string.professional_count_pending, pending.size)
        binding.tvCountAttending.text = getString(R.string.professional_count_attending, attending.size)

        binding.casesContainer.removeAllViews()

        if (pending.isEmpty() && attending.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.professional_no_cases)
                setTextColor(ContextCompat.getColor(this@BandejaProfesionalActivity, R.color.text_secondary))
                textSize = 13.5f
            }
            binding.casesContainer.addView(empty)
            return
        }

        pending.forEach { addCaseView(it, isPending = true) }
        attending.forEach { addCaseView(it, isPending = false) }
    }

    private fun addCaseView(doc: DocumentSnapshot, isPending: Boolean) {
        val item = ItemProfessionalCaseBinding.inflate(layoutInflater, binding.casesContainer, false)

        val tipo = doc.getString("tipo").orEmpty().ifBlank { getString(R.string.report_type_other) }
        val prefix = getString(if (isPending) R.string.professional_case_new_prefix else R.string.professional_case_active_prefix)
        item.tvCaseTitle.text = "$prefix · $tipo"

        val place = doc.getString("direccion").orEmpty().ifBlank { doc.getString("referencia").orEmpty() }
        val timeText = doc.getTimestamp("fechaCreacion")?.let { relativeTime(it.toDate().time) }.orEmpty()
        item.tvCaseSubtitle.text = listOf(place, timeText).filter { it.isNotBlank() }.joinToString(" · ")

        if (isPending) {
            item.btnCaseAction.text = getString(R.string.professional_btn_take_case)
            item.btnCaseAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_dark))
            item.btnCaseAction.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            item.btnCaseAction.strokeWidth = 0
            item.btnCaseAction.setOnClickListener { takeCase(doc) }
        } else {
            item.btnCaseAction.text = getString(R.string.professional_btn_resolve)
            item.btnCaseAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.white))
            item.btnCaseAction.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            item.btnCaseAction.strokeWidth = dp(1.5f)
            item.btnCaseAction.setStrokeColorResource(R.color.field_border)
            item.btnCaseAction.setOnClickListener { resolveCase(doc) }
        }

        val reporterEmail = doc.getString("reportanteCorreo").orEmpty()
        if (reporterEmail.isNotBlank()) {
            item.btnCaseChat.visibility = View.VISIBLE
            item.btnCaseChat.setOnClickListener {
                startActivity(Intent(this, ChatActivity::class.java).apply {
                    putExtra(ChatActivity.EXTRA_CHAT_ID, reporterEmail)
                    putExtra(ChatActivity.EXTRA_ROLE, ChatActivity.ROLE_FUNCIONARIO)
                    putExtra(ChatActivity.EXTRA_PEER_NAME, doc.getString("reportanteNombre").orEmpty())
                    putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
                })
            }
        }

        binding.casesContainer.addView(item.root)
    }

    private fun resolveCase(doc: DocumentSnapshot) {
        doc.reference.update(
            mapOf(
                "estado" to "resuelto",
                "fechaResolucion" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        )
            .addOnSuccessListener {
                Toast.makeText(this, R.string.professional_case_resolved, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.report_send_error, Toast.LENGTH_SHORT).show()
            }
    }

    private fun takeCase(doc: DocumentSnapshot) {
        doc.reference.update(
            mapOf(
                "estado" to "en_atencion",
                "atendidoPor" to intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty()
            )
        )
            .addOnSuccessListener {
                Toast.makeText(this, R.string.professional_case_taken, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.report_send_error, Toast.LENGTH_SHORT).show()
            }
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

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun openAccessibilitySettings() {
        startActivity(Intent(this, AccessibilityConfigActivity::class.java).apply {
            putExtra(AccessibilityConfigActivity.EXTRA_PROFESSIONAL_MODE, true)
        })
    }

    private fun openProfile() {
        startActivity(Intent(this, ProfileActivity::class.java).apply {
            putExtra(ProfileActivity.EXTRA_IS_PROFESSIONAL, true)
            putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
        })
    }

    private fun toggleShift() {
        onShift = !onShift
        if (onShift) {
            binding.tvStatus.text = getString(R.string.professional_status_available)
            binding.tvToggleShift.text = getString(R.string.professional_end_shift)
            binding.dotStatus.setBackgroundResource(R.drawable.shape_dot_active)
        } else {
            binding.tvStatus.text = getString(R.string.professional_status_unavailable)
            binding.tvToggleShift.text = getString(R.string.professional_start_shift)
            binding.dotStatus.setBackgroundResource(R.drawable.shape_dot_inactive)
        }
    }

    override fun onDestroy() {
        casesListener?.remove()
        perdidosListener?.remove()
        super.onDestroy()
    }
}
