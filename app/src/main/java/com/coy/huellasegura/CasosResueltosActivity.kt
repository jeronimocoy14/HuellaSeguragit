package com.coy.huellasegura

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityCasosResueltosBinding
import com.coy.huellasegura.databinding.ItemProfessionalCaseBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

/** Historial de casos que ya se marcaron como resueltos, del más reciente al más antiguo. */
class CasosResueltosActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityCasosResueltosBinding
    private var listener: ListenerRegistration? = null
    private var funcionarioEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCasosResueltosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        funcionarioEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty()
        binding.btnBack.setOnClickListener { finish() }

        binding.listContainer.showStatusMessage(getString(R.string.list_loading))
        // Sin orderBy: ordenar por fecha en el servidor pediría un índice; se ordena aquí.
        listener = Firebase.firestore.collection("reportes")
            .whereEqualTo("estado", "resuelto")
            .addSnapshotListener { snapshot, error ->
                if (error != null && snapshot == null) {
                    binding.listContainer.showStatusMessage(getString(R.string.list_error))
                    return@addSnapshotListener
                }
                render(snapshot?.documents.orEmpty().sortedByDescending { resolvedAt(it) })
            }
    }

    private fun resolvedAt(doc: DocumentSnapshot): Long =
        doc.getTimestamp("fechaResolucion")?.toDate()?.time ?: doc.createdAtMillis()

    private fun render(docs: List<DocumentSnapshot>) {
        binding.listContainer.removeAllViews()
        if (docs.isEmpty()) {
            binding.tvCount.text = ""
            binding.listContainer.addView(TextView(this).apply {
                text = getString(R.string.resolved_empty)
                setTextColor(ContextCompat.getColor(this@CasosResueltosActivity, R.color.text_secondary))
                textSize = 14f
                setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            return
        }
        binding.tvCount.text = resources.getQuantityString(R.plurals.resolved_count, docs.size, docs.size)
        docs.take(MAX_SHOWN).forEach { addCard(it) }
    }

    private fun addCard(doc: DocumentSnapshot) {
        val item = ItemProfessionalCaseBinding.inflate(layoutInflater, binding.listContainer, false)
        val tipo = doc.getString("tipo").orEmpty().ifBlank { getString(R.string.report_type_other) }
        val by = doc.getString("atendidoPor").orEmpty()
        CaseCard.bind(
            activity = this,
            item = item,
            doc = doc,
            title = getString(R.string.resolved_prefix) + " · " + tipo,
            funcionarioEmail = funcionarioEmail,
            timeText = getString(R.string.resolved_when, CaseCard.relativeTime(this, resolvedAt(doc))),
            extra = if (by.isNotBlank()) getString(R.string.resolved_by, by) else ""
        )

        item.btnCaseAction.text = getString(R.string.resolved_btn_reopen)
        item.btnCaseAction.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.white))
        item.btnCaseAction.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        item.btnCaseAction.strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
        item.btnCaseAction.setStrokeColorResource(R.color.field_border)
        item.btnCaseAction.setOnClickListener { confirmReopen(doc, tipo) }

        binding.listContainer.addView(item.root)
    }

    private fun confirmReopen(doc: DocumentSnapshot, tipo: String) {
        styledDialog()
            .setTitle(R.string.resolved_reopen_title)
            .setMessage(getString(R.string.resolved_reopen_message, tipo))
            .setNegativeButton(R.string.btn_cancelar, null)
            .setPositiveButton(R.string.resolved_btn_reopen) { _, _ ->
                OfflineWrite.notify(
                    this,
                    doc.reference.update(
                        mapOf(
                            "estado" to "en_atencion",
                            "fechaResolucion" to FieldValue.delete()
                        )
                    ),
                    R.string.professional_case_reopened_queued
                ) {
                    Toast.makeText(this, R.string.professional_case_reopened, Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }

    private companion object {
        const val MAX_SHOWN = 50
    }
}
