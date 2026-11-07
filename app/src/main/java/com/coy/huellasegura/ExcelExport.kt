package com.coy.huellasegura

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Exporta datos a un archivo de Excel (.xlsx) y lo comparte (guardar en Drive, mandar por correo, abrir...).
 * - Funcionario: resumen de los reportes (como la pantalla de estadísticas), cada reporte y los animales
 *   perdidos/encontrados, con su categoría.
 * - Veterinario: los animales registrados (pacientes), con su especie y dueño.
 * El período se elige al exportar: última semana, último mes o todo.
 */
object ExcelExport {

    enum class Period(val labelRes: Int, val days: Int?) {
        WEEK(R.string.export_period_week, 7),
        MONTH(R.string.export_period_month, 30),
        ALL(R.string.export_period_all, null),
    }

    private const val DAY = 24L * 60 * 60 * 1000
    private const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    /** Pregunta qué período exportar. */
    fun askPeriod(activity: Activity, onChosen: (Period) -> Unit) {
        val periods = Period.entries
        activity.styledDialog()
            .setTitle(R.string.export_dialog_title)
            .setItems(periods.map { activity.getString(it.labelRes) }.toTypedArray()) { _, index -> onChosen(periods[index]) }
            .setNegativeButton(R.string.btn_cancelar, null)
            .show()
    }

    private fun since(period: Period): Long? = period.days?.let { System.currentTimeMillis() - it * DAY }

    private fun inPeriod(doc: DocumentSnapshot, since: Long?): Boolean = since == null || doc.createdAtMillis() >= since

    private fun dateText(millis: Long?): String =
        if (millis == null || millis == Long.MAX_VALUE) "" else SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es")).format(Date(millis))

    private fun docDate(doc: DocumentSnapshot): String = dateText(doc.createdAtMillis().takeIf { it != Long.MAX_VALUE } ?: System.currentTimeMillis())

    private fun ts(doc: DocumentSnapshot, field: String): Long? = doc.getTimestamp(field)?.toDate()?.time

    // ---------------------------------------------------------------- Funcionario

    fun exportFuncionario(activity: Activity, period: Period) {
        Toast.makeText(activity, R.string.export_preparing, Toast.LENGTH_SHORT).show()
        val db = Firebase.firestore
        db.collection("reportes").get()
            .addOnSuccessListener { reports ->
                db.collection("perdidos").get()
                    .addOnSuccessListener { lost ->
                        db.collection("comunitarios").get()
                            .addOnSuccessListener { community ->
                                Thread {
                                    val result = runCatching {
                                        val since = since(period)
                                        val sheets = listOf(
                                            summarySheet(activity, period, reports.documents.filter { inPeriod(it, since) }, lost.documents.filter { inPeriod(it, since) }),
                                            reportsSheet(activity, reports.documents.filter { inPeriod(it, since) }),
                                            lostSheet(activity, lost.documents.filter { inPeriod(it, since) }),
                                            communitySheet(activity, community.documents.filter { inPeriod(it, since) }),
                                        )
                                        writeFile(activity, "resumen", sheets)
                                    }
                                    activity.runOnUiThread { finish(activity, result) }
                                }.start()
                            }
                            .addOnFailureListener { fail(activity) }
                    }
                    .addOnFailureListener { fail(activity) }
            }
            .addOnFailureListener { fail(activity) }
    }

    private fun summarySheet(
        activity: Activity,
        period: Period,
        reports: List<DocumentSnapshot>,
        lost: List<DocumentSnapshot>,
    ): XlsxWriter.Sheet {
        fun s(id: Int) = activity.getString(id)
        val rows = mutableListOf<List<Any?>>()
        fun title(text: String) = rows.add(listOf(XlsxWriter.Title(text)))
        fun blank() = rows.add(emptyList())

        title(s(R.string.export_summary_title))
        rows.add(listOf(s(R.string.export_period), s(period.labelRes)))
        rows.add(listOf(s(R.string.export_generated), dateText(System.currentTimeMillis())))
        blank()

        val total = reports.size
        val resolved = reports.count { it.getString("estado") == "resuelto" }
        val attending = reports.count { it.getString("estado") == "en_atencion" }
        val waiting = reports.count { it.getString("estado") == "pendiente" }
        val resolveTimes = reports.mapNotNull { doc ->
            val start = ReportStats.createdAt(doc)
            val end = ts(doc, "fechaResolucion")
            if (start != null && end != null && doc.getString("estado") == "resuelto" && end >= start) end - start else null
        }
        val takeTimes = reports.mapNotNull { doc ->
            val start = ReportStats.createdAt(doc)
            val taken = ts(doc, "fechaAtencion")
            if (start != null && taken != null && taken >= start) taken - start else null
        }
        val noData = s(R.string.stats_no_data)
        title(s(R.string.export_reports))
        rows.add(listOf(s(R.string.export_total), total))
        rows.add(listOf(s(R.string.export_resolved), resolved))
        rows.add(listOf(s(R.string.export_attending), attending))
        rows.add(listOf(s(R.string.export_waiting), waiting))
        rows.add(listOf(s(R.string.export_resolved_pct), if (total == 0) null else Math.round(resolved * 1000f / total) / 10.0))
        rows.add(listOf(s(R.string.export_avg_take), if (takeTimes.isEmpty()) noData else ReportStats.formatDuration(activity, takeTimes.average().toLong())))
        rows.add(listOf(s(R.string.export_avg_resolve), if (resolveTimes.isEmpty()) noData else ReportStats.formatDuration(activity, resolveTimes.average().toLong())))
        blank()

        title(s(R.string.stats_by_type))
        rows.add(listOf(XlsxWriter.Title(s(R.string.export_col_type)), XlsxWriter.Title(s(R.string.export_count))))
        reports.groupingBy { ReportStats.typeLabel(activity, it) }.eachCount().entries.sortedByDescending { it.value }
            .forEach { rows.add(listOf(it.key, it.value)) }
        blank()

        title(s(R.string.stats_by_species))
        rows.add(listOf(XlsxWriter.Title(s(R.string.export_col_category)), XlsxWriter.Title(s(R.string.export_count))))
        val bySpecies = reports.mapNotNull { speciesOf(activity, it) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
        bySpecies.forEach { rows.add(listOf(it.key, it.value)) }
        rows.add(listOf(s(R.string.export_no_category), total - bySpecies.sumOf { it.value }))
        blank()

        title(s(R.string.stats_by_week))
        rows.add(listOf(XlsxWriter.Title(s(R.string.export_col_week)), XlsxWriter.Title(s(R.string.export_count))))
        weekCounts(reports).forEach { rows.add(listOf(it.first, it.second)) }
        blank()

        title(s(R.string.export_lost_section))
        rows.add(listOf(s(R.string.export_total), lost.size))
        rows.add(listOf(s(R.string.export_lost_published), lost.count { it.getString("estado") == "publicado" }))
        rows.add(listOf(s(R.string.export_lost_review), lost.count { it.getString("estado") == "pendiente" }))
        rows.add(listOf(s(R.string.export_lost_rejected), lost.count { it.getString("estado") == "rechazado" }))
        return XlsxWriter.Sheet(s(R.string.export_sheet_summary), rows, table = false)
    }

    /** Categoría de animal de un documento, solo si es una de las de la app. */
    private fun speciesOf(activity: Activity, doc: DocumentSnapshot): String? {
        val name = doc.getString("especie").orEmpty()
        return Species.all.firstOrNull { activity.getString(it.nameRes).equals(name, ignoreCase = true) }?.let { activity.getString(it.nameRes) }
    }

    /** Reportes creados por semana (de lunes a domingo), solo las semanas dentro del período; hasta 52. */
    private fun weekCounts(reports: List<DocumentSnapshot>): List<Pair<String, Int>> {
        fun weekStart(millis: Long): Long = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            timeInMillis = millis
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val times = reports.mapNotNull { ReportStats.createdAt(it) }
        if (times.isEmpty()) return emptyList()
        val counts = times.groupingBy { weekStart(it) }.eachCount()
        val last = weekStart(System.currentTimeMillis())
        val first = weekStart(times.min())
        val format = SimpleDateFormat("dd/MM/yyyy", Locale("es"))
        val weeks = mutableListOf<Pair<String, Int>>()
        val cursor = Calendar.getInstance().apply { timeInMillis = first }
        while (cursor.timeInMillis <= last && weeks.size < 52) {
            weeks.add(format.format(cursor.time) to (counts[cursor.timeInMillis] ?: 0))
            cursor.add(Calendar.DAY_OF_YEAR, 7)
        }
        return weeks
    }

    private fun reportsSheet(activity: Activity, reports: List<DocumentSnapshot>): XlsxWriter.Sheet {
        fun s(id: Int) = activity.getString(id)
        val header = listOf(
            R.string.export_col_date, R.string.export_col_type, R.string.export_col_state, R.string.export_col_category,
            R.string.export_col_detected, R.string.export_col_confidence, R.string.export_col_manual, R.string.export_col_place,
            R.string.export_col_lat, R.string.export_col_lon, R.string.export_col_reporter, R.string.export_col_attended_by,
            R.string.export_col_taken_at, R.string.export_col_resolved_at, R.string.export_col_time_take, R.string.export_col_time_resolve,
            R.string.export_col_flow,
        ).map { s(it) }
        val rows = mutableListOf<List<Any?>>(header)
        reports.sortedByDescending { it.createdAtMillis() }.forEach { doc ->
            val created = ReportStats.createdAt(doc)
            val taken = ts(doc, "fechaAtencion")
            val resolved = ts(doc, "fechaResolucion")
            rows.add(
                listOf(
                    docDate(doc),
                    ReportStats.typeLabel(activity, doc),
                    when (doc.getString("estado")) {
                        "resuelto" -> s(R.string.export_state_resolved)
                        "en_atencion" -> s(R.string.export_state_attending)
                        else -> s(R.string.export_state_waiting)
                    },
                    speciesOf(activity, doc).orEmpty(),
                    doc.getString("especieDetalle").orEmpty(),
                    doc.getDouble("especieConfianza"),
                    if (doc.getBoolean("especieManual") == true) s(R.string.export_yes) else "",
                    doc.getString("direccion").orEmpty().ifBlank { doc.getString("referencia").orEmpty() },
                    doc.getDouble("lat"),
                    doc.getDouble("lon"),
                    doc.getString("reportanteNombre").orEmpty(),
                    doc.getString("atendidoPor").orEmpty(),
                    dateText(taken),
                    dateText(resolved),
                    if (created != null && taken != null && taken >= created) ReportStats.formatDuration(activity, taken - created) else "",
                    if (created != null && resolved != null && resolved >= created) ReportStats.formatDuration(activity, resolved - created) else "",
                    when (doc.getString("flujo")) {
                        "simplificado" -> s(R.string.export_flow_simple)
                        "normal" -> s(R.string.export_flow_normal)
                        else -> ""
                    },
                )
            )
        }
        return XlsxWriter.Sheet(s(R.string.export_sheet_reports), rows)
    }

    private fun lostSheet(activity: Activity, lost: List<DocumentSnapshot>): XlsxWriter.Sheet {
        fun s(id: Int) = activity.getString(id)
        val header = listOf(
            R.string.export_col_date, R.string.export_col_name, R.string.export_col_description, R.string.export_col_state,
            R.string.export_col_category, R.string.export_col_detected, R.string.export_col_manual, R.string.export_col_reporter,
            R.string.export_col_reviewed_by, R.string.export_col_published_at, R.string.export_col_reject_reason,
        ).map { s(it) }
        val rows = mutableListOf<List<Any?>>(header)
        lost.sortedByDescending { it.createdAtMillis() }.forEach { doc ->
            rows.add(
                listOf(
                    docDate(doc),
                    doc.getString("nombre").orEmpty(),
                    doc.getString("descripcion").orEmpty(),
                    when (doc.getString("estado")) {
                        "publicado" -> s(R.string.export_state_published)
                        "rechazado" -> s(R.string.export_state_rejected)
                        else -> s(R.string.export_state_review)
                    },
                    speciesOf(activity, doc).orEmpty(),
                    doc.getString("especieDetalle").orEmpty(),
                    if (doc.getBoolean("especieManual") == true) s(R.string.export_yes) else "",
                    doc.getString("reportanteNombre").orEmpty(),
                    doc.getString("revisadoPor").orEmpty(),
                    dateText(ts(doc, "fechaPublicacion")),
                    doc.getString("motivoRechazo").orEmpty(),
                )
            )
        }
        return XlsxWriter.Sheet(s(R.string.export_sheet_lost), rows)
    }

    private fun communitySheet(activity: Activity, animals: List<DocumentSnapshot>): XlsxWriter.Sheet {
        fun s(id: Int) = activity.getString(id)
        fun yesNo(value: Boolean?) = if (value == true) s(R.string.export_yes) else s(R.string.comunitarios_no)
        val header = listOf(
            R.string.export_col_date, R.string.export_col_name, R.string.export_col_category, R.string.export_col_reference,
            R.string.export_col_address, R.string.export_col_lat, R.string.export_col_lon, R.string.comunitarios_sterilized,
            R.string.comunitarios_vaccinated, R.string.export_col_state, R.string.export_col_care_note, R.string.export_col_registered_by_staff,
        ).map { s(it) }
        val rows = mutableListOf<List<Any?>>(header)
        animals.sortedByDescending { it.createdAtMillis() }.forEach { doc ->
            rows.add(
                listOf(
                    docDate(doc),
                    doc.getString("nombre").orEmpty(),
                    speciesOf(activity, doc).orEmpty(),
                    doc.getString("referencia").orEmpty(),
                    doc.getString("direccion").orEmpty(),
                    doc.getDouble("lat"),
                    doc.getDouble("lon"),
                    yesNo(doc.getBoolean("esterilizado")),
                    yesNo(doc.getBoolean("vacunado")),
                    if (doc.getBoolean("activo") == false) s(R.string.comunitarios_gone) else s(R.string.export_comunitario_active),
                    doc.getString("nota").orEmpty(),
                    doc.getString("creadoPor").orEmpty(),
                )
            )
        }
        return XlsxWriter.Sheet(s(R.string.export_sheet_community), rows)
    }

    // ---------------------------------------------------------------- Veterinario

    fun exportPatients(activity: Activity, period: Period) {
        Toast.makeText(activity, R.string.export_preparing, Toast.LENGTH_SHORT).show()
        Firebase.firestore.collection("mascotas").get()
            .addOnSuccessListener { pets ->
                Thread {
                    val result = runCatching {
                        val since = since(period)
                        val docs = pets.documents.filter { inPeriod(it, since) }
                        writeFile(activity, "pacientes", listOf(petsSummarySheet(activity, period, docs), petsSheet(activity, docs)))
                    }
                    activity.runOnUiThread { finish(activity, result) }
                }.start()
            }
            .addOnFailureListener { fail(activity) }
    }

    private fun petsSummarySheet(activity: Activity, period: Period, pets: List<DocumentSnapshot>): XlsxWriter.Sheet {
        fun s(id: Int) = activity.getString(id)
        val rows = mutableListOf<List<Any?>>()
        rows.add(listOf(XlsxWriter.Title(s(R.string.export_pets_summary_title))))
        rows.add(listOf(s(R.string.export_period), s(period.labelRes)))
        rows.add(listOf(s(R.string.export_generated), dateText(System.currentTimeMillis())))
        rows.add(emptyList())
        rows.add(listOf(s(R.string.export_total), pets.size))
        rows.add(listOf(s(R.string.export_pets_accepted), pets.count { it.getString("estado") == "aceptada" }))
        rows.add(listOf(s(R.string.export_pets_pending), pets.count { it.getString("estado") != "aceptada" }))
        rows.add(emptyList())
        rows.add(listOf(XlsxWriter.Title(s(R.string.export_pets_by_species))))
        rows.add(listOf(XlsxWriter.Title(s(R.string.pets_label_species)), XlsxWriter.Title(s(R.string.export_count))))
        val bySpecies = pets.map { Species.find(activity, it.getString("especie").orEmpty()) }
            .groupingBy { activity.getString(it.nameRes) }.eachCount().entries.sortedByDescending { it.value }
        bySpecies.forEach { rows.add(listOf(it.key, it.value)) }
        return XlsxWriter.Sheet(s(R.string.export_sheet_summary), rows, table = false)
    }

    private fun petsSheet(activity: Activity, pets: List<DocumentSnapshot>): XlsxWriter.Sheet {
        fun s(id: Int) = activity.getString(id)
        val header = listOf(
            R.string.export_col_name, R.string.pets_label_species, R.string.export_col_breed, R.string.export_col_age,
            R.string.export_col_state, R.string.export_col_owner, R.string.export_col_registered_by, R.string.export_col_registered_at,
        ).map { s(it) }
        val rows = mutableListOf<List<Any?>>(header)
        pets.sortedByDescending { it.createdAtMillis() }.forEach { doc ->
            rows.add(
                listOf(
                    doc.getString("nombre").orEmpty(),
                    activity.getString(Species.find(activity, doc.getString("especie").orEmpty()).nameRes),
                    doc.getString("raza").orEmpty(),
                    doc.getString("edad").orEmpty().edadConUnidad(),
                    if (doc.getString("estado") == "aceptada") s(R.string.pets_status_accepted) else s(R.string.pets_status_pending),
                    doc.getString("duenoCorreo").orEmpty(),
                    doc.getString("creadoPorVet").orEmpty(),
                    docDate(doc),
                )
            )
        }
        return XlsxWriter.Sheet(s(R.string.export_sheet_pets), rows)
    }

    // ---------------------------------------------------------------- Archivo y compartir

    private fun writeFile(activity: Activity, kind: String, sheets: List<XlsxWriter.Sheet>): File {
        val dir = File(activity.cacheDir, "exports")
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val file = File(dir, "HuellaSegura_${kind}_$stamp.xlsx")
        XlsxWriter.write(file, sheets)
        return file
    }

    private fun finish(activity: Activity, result: Result<File>) {
        if (activity.isDestroyed || activity.isFinishing) return
        val file = result.getOrNull()
        if (file == null) {
            fail(activity)
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activity.startActivity(Intent.createChooser(send, activity.getString(R.string.export_share_title))) }
            .onFailure { Toast.makeText(activity, R.string.export_error, Toast.LENGTH_LONG).show() }
    }

    private fun fail(activity: Activity) {
        if (activity.isDestroyed || activity.isFinishing) return
        Toast.makeText(activity, R.string.export_error, Toast.LENGTH_LONG).show()
    }
}
