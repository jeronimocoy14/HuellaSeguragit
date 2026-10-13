package com.coy.huellasegura

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityEstadisticasBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Estadísticas de los reportes para el funcionario: totales, tiempos medios de atención,
 * reportes por tipo y reportes por semana.
 */
class EstadisticasActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityEstadisticasBinding
    private var listener: ListenerRegistration? = null
    private var docs: List<DocumentSnapshot> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEstadisticasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, _ -> render() }

        listener = Firebase.firestore.collection("reportes").addSnapshotListener { snapshot, _ ->
            if (snapshot == null) return@addSnapshotListener
            docs = snapshot.documents
            render()
        }
    }

    private fun createdAt(doc: DocumentSnapshot): Long? = doc.getTimestamp("fechaCreacion")?.toDate()?.time

    private fun render() {
        val day = 24L * 60 * 60 * 1000
        val since = when (binding.chipGroupPeriod.checkedChipId) {
            R.id.chipPeriod7 -> System.currentTimeMillis() - 7 * day
            R.id.chipPeriod30 -> System.currentTimeMillis() - 30 * day
            else -> null
        }
        // Un reporte recién escrito aún no tiene fecha del servidor: se cuenta cuando la tenga.
        val inPeriod = docs.filter { doc -> createdAt(doc)?.let { since == null || it >= since } == true }

        renderTotals(inPeriod)
        renderTypes(inPeriod)
        renderWeeks()
    }

    private fun renderTotals(list: List<DocumentSnapshot>) {
        val total = list.size
        val resolved = list.count { it.getString("estado") == "resuelto" }
        val attending = list.count { it.getString("estado") == "en_atencion" }
        val waiting = list.count { it.getString("estado") == "pendiente" }

        binding.tvKpiTotal.text = total.toString()
        binding.tvKpiTotalSub.text = getString(R.string.stats_total_sub, waiting, attending)
        binding.tvKpiResolved.text = resolved.toString()
        binding.tvKpiResolvedSub.text =
            if (total == 0) "" else getString(R.string.stats_resolved_sub, Math.round(resolved * 100f / total))

        val resolveTimes = list.mapNotNull { doc ->
            val start = createdAt(doc)
            val end = doc.getTimestamp("fechaResolucion")?.toDate()?.time
            if (start != null && end != null && doc.getString("estado") == "resuelto" && end >= start) end - start else null
        }
        val takeTimes = list.mapNotNull { doc ->
            val start = createdAt(doc)
            val taken = doc.getTimestamp("fechaAtencion")?.toDate()?.time
            if (start != null && taken != null && taken >= start) taken - start else null
        }

        if (resolveTimes.isEmpty()) {
            binding.tvKpiResolveTime.text = getString(R.string.stats_no_data)
            binding.tvKpiResolveTimeSub.text = getString(R.string.stats_resolve_time_empty)
        } else {
            binding.tvKpiResolveTime.text = formatDuration(resolveTimes.average().toLong())
            binding.tvKpiResolveTimeSub.text = resources.getQuantityString(R.plurals.stats_based_on, resolveTimes.size, resolveTimes.size)
        }
        if (takeTimes.isEmpty()) {
            binding.tvKpiTake.text = getString(R.string.stats_no_data)
            binding.tvKpiTakeSub.text = getString(R.string.stats_take_time_empty)
        } else {
            binding.tvKpiTake.text = formatDuration(takeTimes.average().toLong())
            binding.tvKpiTakeSub.text = resources.getQuantityString(R.plurals.stats_based_on, takeTimes.size, takeTimes.size)
        }
    }

    private fun typeLabel(doc: DocumentSnapshot): String {
        if (!doc.getString("perdidoId").isNullOrBlank()) return getString(R.string.resolved_filter_found)
        val tipo = doc.getString("tipo").orEmpty().trim()
        val known = listOf(R.string.report_type_hurt, R.string.report_type_abuse, R.string.report_type_hit)
            .map { getString(it) }
            .firstOrNull { normalize(it) == normalize(tipo) }
        return known ?: getString(R.string.report_type_other)
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun renderTypes(list: List<DocumentSnapshot>) {
        binding.typeContainer.removeAllViews()
        if (list.isEmpty()) {
            binding.typeContainer.addView(TextView(this).apply {
                text = getString(R.string.stats_empty)
                setTextColor(ContextCompat.getColor(this@EstadisticasActivity, R.color.text_secondary))
                textSize = 14f
            })
            return
        }
        val counts = list.groupingBy { typeLabel(it) }.eachCount().entries.sortedByDescending { it.value }
        val max = counts.first().value
        counts.forEachIndexed { index, (label, count) ->
            binding.typeContainer.addView(barRow(label, count, max, topMargin = if (index == 0) 0 else 12))
        }
    }

    private fun barRow(label: String, count: Int, max: Int, topMargin: Int): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { this.topMargin = (topMargin * density).toInt() }
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 13.5f
            setTextColor(ContextCompat.getColor(this@EstadisticasActivity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams((118 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = max.toFloat()
            setBackgroundResource(R.drawable.bg_bar_track)
            layoutParams = LinearLayout.LayoutParams(0, (14 * density).toInt(), 1f)
        }
        track.addView(View(this).apply {
            setBackgroundResource(R.drawable.bg_bar_fill)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, count.toFloat())
        })
        row.addView(track)
        row.addView(TextView(this).apply {
            text = count.toString()
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.END
            setTextColor(ContextCompat.getColor(this@EstadisticasActivity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams((34 * density).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        return row
    }

    /** Reportes creados en cada una de las últimas 8 semanas (de lunes a domingo). */
    private fun renderWeeks() {
        fun weekStart(millis: Long): Long = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            timeInMillis = millis
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val current = weekStart(System.currentTimeMillis())
        val starts = (WEEKS - 1 downTo 0).map { back ->
            Calendar.getInstance().apply { timeInMillis = current; add(Calendar.DAY_OF_YEAR, -7 * back) }.timeInMillis
        }
        val counts = docs.mapNotNull { createdAt(it) }.groupingBy { weekStart(it) }.eachCount()
        val format = SimpleDateFormat("d MMM", Locale("es"))
        binding.weekChart.setData(starts.map { format.format(it).replace(".", "") to (counts[it] ?: 0) })
    }

    private fun formatDuration(millis: Long): String {
        val minutes = millis / 60000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1 -> getString(R.string.stats_duration_lt_min)
            hours < 1 -> getString(R.string.stats_duration_min, minutes.toInt())
            days < 1 -> if (minutes % 60 == 0L) getString(R.string.stats_duration_h, hours.toInt())
            else getString(R.string.stats_duration_h_min, hours.toInt(), (minutes % 60).toInt())
            else -> if (hours % 24 == 0L) getString(R.string.stats_duration_d, days.toInt())
            else getString(R.string.stats_duration_d_h, days.toInt(), (hours % 24).toInt())
        }
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }

    private companion object {
        const val WEEKS = 8
    }
}
