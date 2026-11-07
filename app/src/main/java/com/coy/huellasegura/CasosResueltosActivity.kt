package com.coy.huellasegura

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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
import java.text.Normalizer
import java.util.Calendar

/**
 * Historial de casos que ya se marcaron como resueltos, del más reciente al más antiguo, con
 * buscador (por persona que reportó o que atendió, tipo o lugar) y filtros por tipo, fecha y
 * «atendidos por mí».
 */
class CasosResueltosActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityCasosResueltosBinding
    private var listener: ListenerRegistration? = null
    private var funcionarioEmail = ""
    private var all: List<DocumentSnapshot> = emptyList()
    private var loaded = false
    private var speciesFilter: String? = null

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

        funcionarioEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase()
        binding.btnBack.setOnClickListener { finish() }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = render()
        })
        binding.chipGroupType.setOnCheckedStateChangeListener { _, _ -> render() }
        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, _ -> render() }
        binding.chipMine.setOnCheckedChangeListener { _, _ -> render() }
        SpeciesFilter.setup(this, binding.speciesFilter.chipGroupSpecies) { speciesFilter = it; render() }

        binding.listContainer.showStatusMessage(getString(R.string.list_loading))
        // Sin orderBy: ordenar por fecha en el servidor pediría un índice; se ordena aquí.
        listener = Firebase.firestore.collection("reportes")
            .whereEqualTo("estado", "resuelto")
            .addSnapshotListener { snapshot, error ->
                if (error != null && snapshot == null) {
                    binding.listContainer.showStatusMessage(getString(R.string.list_error))
                    return@addSnapshotListener
                }
                all = snapshot?.documents.orEmpty().sortedByDescending { resolvedAt(it) }
                loaded = true
                render()
            }
    }

    private fun resolvedAt(doc: DocumentSnapshot): Long =
        doc.getTimestamp("fechaResolucion")?.toDate()?.time ?: doc.createdAtMillis()

    // ---------- Filtros ----------

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun isFound(doc: DocumentSnapshot) = !doc.getString("perdidoId").isNullOrBlank()

    private fun matchesType(doc: DocumentSnapshot): Boolean {
        val tipo = normalize(doc.getString("tipo").orEmpty().ifBlank { getString(R.string.report_type_other) })
        return when (binding.chipGroupType.checkedChipId) {
            R.id.chipTypeHurt -> !isFound(doc) && tipo == normalize(getString(R.string.report_type_hurt))
            R.id.chipTypeAbuse -> !isFound(doc) && tipo == normalize(getString(R.string.report_type_abuse))
            R.id.chipTypeHit -> !isFound(doc) && tipo == normalize(getString(R.string.report_type_hit))
            R.id.chipTypeOther -> !isFound(doc) && tipo == normalize(getString(R.string.report_type_other))
            R.id.chipTypeFound -> isFound(doc)
            else -> true
        }
    }

    private fun periodStart(): Long? {
        val day = 24L * 60 * 60 * 1000
        return when (binding.chipGroupPeriod.checkedChipId) {
            R.id.chipPeriodToday -> Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            R.id.chipPeriod7 -> System.currentTimeMillis() - 7 * day
            R.id.chipPeriod30 -> System.currentTimeMillis() - 30 * day
            else -> null
        }
    }

    private fun matchesSearch(doc: DocumentSnapshot, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        val haystack = normalize(
            listOf("reportanteNombre", "reportanteCorreo", "atendidoPor", "tipo", "direccion", "referencia", "perdidoNombre")
                .joinToString(" ") { doc.getString(it).orEmpty() }
        )
        return tokens.all { haystack.contains(it) }
    }

    private fun filtersActive(): Boolean =
        !binding.etSearch.text.isNullOrBlank() ||
            binding.chipGroupType.checkedChipId != R.id.chipTypeAll ||
            binding.chipGroupPeriod.checkedChipId != R.id.chipPeriodAll ||
            binding.chipMine.isChecked ||
            speciesFilter != null

    private fun clearFilters() {
        binding.etSearch.setText("")
        binding.chipTypeAll.isChecked = true
        binding.chipPeriodAll.isChecked = true
        binding.chipMine.isChecked = false
        SpeciesFilter.reset(binding.speciesFilter.chipGroupSpecies)
    }

    // ---------- Lista ----------

    private fun render() {
        if (!loaded) return
        val tokens = normalize(binding.etSearch.text?.toString().orEmpty().trim()).split(Regex("\\s+")).filter { it.isNotBlank() }
        val start = periodStart()
        val mine = binding.chipMine.isChecked
        val shown = all.filter { doc ->
            matchesType(doc) &&
                SpeciesFilter.matches(doc, speciesFilter) &&
                (start == null || resolvedAt(doc) >= start) &&
                (!mine || doc.getString("atendidoPor").orEmpty().trim().lowercase() == funcionarioEmail) &&
                matchesSearch(doc, tokens)
        }

        binding.listContainer.removeAllViews()
        binding.tvCount.text = when {
            all.isEmpty() -> ""
            filtersActive() -> getString(R.string.resolved_count_filtered, shown.size, all.size)
            else -> resources.getQuantityString(R.plurals.resolved_count, all.size, all.size)
        }

        if (all.isEmpty()) {
            addMessage(getString(R.string.resolved_empty), withClear = false)
            return
        }
        if (shown.isEmpty()) {
            addMessage(getString(R.string.resolved_no_match), withClear = true)
            return
        }
        shown.take(MAX_SHOWN).forEach { addCard(it) }
        if (shown.size > MAX_SHOWN) addMessage(getString(R.string.resolved_more_hint, MAX_SHOWN), withClear = false)
    }

    private fun addMessage(text: String, withClear: Boolean) {
        val pad = (24 * resources.displayMetrics.density).toInt()
        binding.listContainer.addView(TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(this@CasosResueltosActivity, R.color.text_secondary))
            textSize = 14f
            setPadding(0, pad, 0, 0)
        })
        if (withClear) {
            binding.listContainer.addView(TextView(this).apply {
                this.text = getString(R.string.resolved_clear_filters)
                setTextColor(ContextCompat.getColor(this@CasosResueltosActivity, R.color.link_color))
                textSize = 14f
                setPadding(0, pad / 2, 0, pad / 2)
                setOnClickListener { clearFilters() }
            })
        }
    }

    private fun addCard(doc: DocumentSnapshot) {
        val item = ItemProfessionalCaseBinding.inflate(layoutInflater, binding.listContainer, false)
        val tipo = doc.getString("tipo").orEmpty().ifBlank { getString(R.string.report_type_other) }
        val by = doc.getString("atendidoPor").orEmpty()
        val reporter = doc.getString("reportanteNombre").orEmpty().ifBlank { doc.getString("reportanteCorreo").orEmpty() }
        CaseCard.bind(
            activity = this,
            item = item,
            doc = doc,
            title = getString(R.string.resolved_prefix) + " · " + tipo,
            funcionarioEmail = funcionarioEmail,
            timeText = getString(R.string.resolved_when, CaseCard.relativeTime(this, resolvedAt(doc))),
            extra = listOfNotNull(
                reporter.takeIf { it.isNotBlank() }?.let { getString(R.string.resolved_reported_by, it) },
                by.takeIf { it.isNotBlank() }?.let { getString(R.string.resolved_by, it) }
            ).joinToString(" · ")
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
