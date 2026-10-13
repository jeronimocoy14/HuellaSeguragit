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
import com.google.android.material.snackbar.Snackbar
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
    private var resolvedListener: ListenerRegistration? = null

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

        binding.tabInicio.setOnClickListener { binding.scrollCases.smoothScrollTo(0, 0) }

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

        binding.cardResueltos.setOnClickListener {
            startActivity(Intent(this, CasosResueltosActivity::class.java).apply {
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
            })
        }
        listenForResolved()

        binding.cardEstadisticas.setOnClickListener {
            startActivity(Intent(this, EstadisticasActivity::class.java))
        }

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

    private fun listenForResolved() {
        resolvedListener = Firebase.firestore.collection("reportes")
            .whereEqualTo("estado", "resuelto")
            .addSnapshotListener { snapshot, _ ->
                val count = snapshot?.size() ?: 0
                binding.tvResueltosCount.text = if (count == 0) getString(R.string.resolved_inbox_none)
                else resources.getQuantityString(R.plurals.resolved_inbox_count, count, count)
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
        CaseCard.bind(this, item, doc, "$prefix · $tipo", intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))

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
            item.btnCaseAction.setOnClickListener { confirmResolve(doc) }
        }

        binding.casesContainer.addView(item.root)
    }

    /** Resolver saca el caso de la lista, así que se pide confirmación antes. */
    private fun confirmResolve(doc: DocumentSnapshot) {
        val tipo = doc.getString("tipo").orEmpty().ifBlank { getString(R.string.report_type_other) }
        styledDialog()
            .setTitle(R.string.professional_resolve_confirm_title)
            .setMessage(getString(R.string.professional_resolve_confirm_message, tipo))
            .setNegativeButton(R.string.btn_cancelar, null)
            .setPositiveButton(R.string.professional_btn_resolve) { _, _ -> resolveCase(doc) }
            .show()
    }

    private fun resolveCase(doc: DocumentSnapshot) {
        OfflineWrite.run(
            this,
            doc.reference.update(
                mapOf(
                    "estado" to "resuelto",
                    "fechaResolucion" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ),
            onDone = { queued ->
                Snackbar.make(
                    binding.root,
                    if (queued) R.string.professional_case_resolved_queued else R.string.professional_case_resolved,
                    6000
                )
                    .setAction(R.string.btn_deshacer) { undoResolve(doc) }
                    .setActionTextColor(ContextCompat.getColor(this, R.color.surface_background))
                    .show()
            },
            onError = { Toast.makeText(this, R.string.write_error, Toast.LENGTH_SHORT).show() }
        )
    }

    /** Devuelve el caso a «en atención» (conserva quién lo atendía). */
    private fun undoResolve(doc: DocumentSnapshot) {
        OfflineWrite.notify(
            this,
            doc.reference.update(
                mapOf(
                    "estado" to "en_atencion",
                    "fechaResolucion" to com.google.firebase.firestore.FieldValue.delete()
                )
            ),
            R.string.professional_case_reopened_queued
        ) {
            Toast.makeText(this, R.string.professional_case_reopened, Toast.LENGTH_SHORT).show()
        }
    }

    private fun takeCase(doc: DocumentSnapshot) {
        OfflineWrite.notify(
            this,
            doc.reference.update(
                mapOf(
                    "estado" to "en_atencion",
                    "atendidoPor" to intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty(),
                    "fechaAtencion" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ),
            R.string.professional_case_taken_queued
        ) {
            Toast.makeText(this, R.string.professional_case_taken, Toast.LENGTH_SHORT).show()
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
        resolvedListener?.remove()
        super.onDestroy()
    }
}
