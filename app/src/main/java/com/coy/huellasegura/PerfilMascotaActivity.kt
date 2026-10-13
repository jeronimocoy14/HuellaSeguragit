package com.coy.huellasegura

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import com.google.firebase.auth.auth
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import com.coy.huellasegura.databinding.ActivityPerfilMascotaBinding
import com.coy.huellasegura.databinding.DialogAnularRegistroBinding
import com.coy.huellasegura.databinding.DialogDetalleHistorialBinding
import com.coy.huellasegura.databinding.DialogRegistroMedicoBinding
import com.coy.huellasegura.databinding.ItemHistorialBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class RegistroEstado { PENDIENTE, ACEPTADO, ANULADO }

private class EstadoStyle(
    val iconRes: Int,
    val iconTintRes: Int,
    val badgeColorRes: Int,
    val pillBgRes: Int,
    val pillTextColorRes: Int,
    val labelRes: Int
)

private fun styleFor(estado: RegistroEstado) = when (estado) {
    RegistroEstado.ACEPTADO -> EstadoStyle(
        R.drawable.ic_check_circle, R.color.accent_green, R.color.accent_soft,
        R.drawable.bg_tag_pill, R.color.brand_dark, R.string.pets_status_record_accepted
    )
    RegistroEstado.PENDIENTE -> EstadoStyle(
        R.drawable.ic_warning, R.color.offline_notice_icon, R.color.offline_notice_bg,
        R.drawable.bg_pill_pending, R.color.offline_notice_text, R.string.pets_status_pending
    )
    RegistroEstado.ANULADO -> EstadoStyle(
        R.drawable.ic_octagon_cross, R.color.danger_red, R.color.danger_red_soft,
        R.drawable.bg_pill_danger, R.color.danger_red_dark_text, R.string.pets_status_annulled
    )
}

private fun DocumentSnapshot.registroEstado() = when (getString("estado")) {
    "aceptado" -> RegistroEstado.ACEPTADO
    "anulado" -> RegistroEstado.ANULADO
    else -> RegistroEstado.PENDIENTE
}

class PerfilMascotaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MASCOTA_ID = "extra_mascota_id"
        const val EXTRA_VET_MODE = "extra_vet_mode"
    }

    private lateinit var binding: ActivityPerfilMascotaBinding
    private lateinit var mascotaId: String
    private var vetMode = false
    private var petName = ""
    private var petDescription = ""
    private var mascotaDoc: DocumentSnapshot? = null
    private var mascotaListener: ListenerRegistration? = null
    private var historialListener: ListenerRegistration? = null
    private var historialAnimated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerfilMascotaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        mascotaId = intent.getStringExtra(EXTRA_MASCOTA_ID) ?: run { finish(); return }
        vetMode = intent.getBooleanExtra(EXTRA_VET_MODE, false)

        binding.ownerCard.visibility = if (vetMode) View.GONE else View.VISIBLE
        binding.vetStatusCard.visibility = if (vetMode) View.VISIBLE else View.GONE
        binding.btnAddMedical.visibility = if (vetMode) View.VISIBLE else View.GONE
        binding.btnReportLost.visibility = if (vetMode) View.GONE else View.VISIBLE
        binding.tvAddBehavior.visibility = if (vetMode) View.GONE else View.VISIBLE

        binding.tvNextVisit.setStartIcon(R.drawable.ic_calendar, 16)
        binding.tvNextVisit.compoundDrawablePadding = dp(8f)
        binding.btnAddMedical.enablePressScale()
        binding.btnReportLost.enablePressScale()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddMedical.setOnClickListener { showRegistroDialog(null) }
        binding.tvAddBehavior.setOnClickListener { showAddBehaviorDialog() }
        binding.btnReportLost.setOnClickListener {
            val email = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL)
                ?: Firebase.auth.currentUser?.email.orEmpty()
            startActivity(Intent(this, ReportarPerdidoActivity::class.java).apply {
                putExtra(ReportarPerdidoActivity.EXTRA_PREFILL_NAME, petName)
                putExtra(ReportarPerdidoActivity.EXTRA_PREFILL_DESC, petDescription)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, email)
                putExtra(LoginActivity.EXTRA_USER_NAME, intent.getStringExtra(LoginActivity.EXTRA_USER_NAME))
            })
        }

        val mascotaRef = Firebase.firestore.collection("mascotas").document(mascotaId)
        mascotaListener = mascotaRef.addSnapshotListener { doc, _ ->
            if (doc == null || !doc.exists()) {
                finish()
                return@addSnapshotListener
            }
            mascotaDoc = doc
            renderMascota(doc)
        }
        historialListener = mascotaRef.collection("historial")
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                renderHistorial(snapshot?.documents.orEmpty())
            }
    }

    private fun renderMascota(doc: DocumentSnapshot) {
        val nombre = doc.getString("nombre").orEmpty()
        val especie = doc.getString("especie").orEmpty()
        val raza = doc.getString("raza").orEmpty()
        val edad = doc.getString("edad").orEmpty().edadConUnidad()
        val estado = doc.getString("estado").orEmpty()
        val duenoCorreo = doc.getString("duenoCorreo").orEmpty()

        binding.tvNombre.text = nombre
        petName = nombre
        petDescription = listOf(especie, raza, edad).filter { it.isNotBlank() }.joinToString(", ")
        binding.ivHeaderIcon.setImageResource(
            when (especie) {
                getString(R.string.pets_species_cat) -> R.drawable.ic_cat
                getString(R.string.pets_species_dog) -> R.drawable.ic_dog
                else -> R.drawable.ic_paw
            }
        )

        binding.tagsContainer.removeAllViews()
        listOf(especie, raza, edad).filter { it.isNotBlank() }.forEach { binding.tagsContainer.addView(makeTag(it)) }

        if (vetMode) {
            binding.tvVetStatusPill.text = getString(
                if (estado == "aceptada") R.string.pets_status_accepted else R.string.pets_status_pending
            )
            binding.tvVetOwnerEmail.text = getString(R.string.pets_owner_prefix, duenoCorreo)
        } else {
            binding.tvOwnerName.text = duenoCorreo.ifBlank { intent.getStringExtra(LoginActivity.EXTRA_USER_NAME) ?: "Usuario" }
        }

        renderBehavior(doc)
    }

    private fun renderHistorial(docs: List<DocumentSnapshot>) {
        binding.historialContainer.removeAllViews()
        val pending = docs.filter { it.registroEstado() == RegistroEstado.PENDIENTE }
        val accepted = docs.filter { it.registroEstado() == RegistroEstado.ACEPTADO }
        val annulled = docs.filter { it.registroEstado() == RegistroEstado.ANULADO }

        updateNextVisitBanner(accepted)

        if (docs.isEmpty()) {
            binding.historialContainer.addView(makeEmptyText(R.string.pets_no_medical_history))
            return
        }

        val ordered = if (vetMode) accepted + pending + annulled else pending + accepted + annulled
        val cards = ordered.map { addHistorialCard(it) }

        if (!historialAnimated) {
            historialAnimated = true
            cards.forEachIndexed { i, card -> card.fadeSlideIn(i, dp(8f).toFloat()) }
        }
    }

    private fun startOfTodayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun updateNextVisitBanner(accepted: List<DocumentSnapshot>) {
        val now = System.currentTimeMillis()
        val next = accepted
            .mapNotNull { doc ->
                val millis = doc.getLong("proximaVisitaMillis") ?: return@mapNotNull null
                val texto = doc.getString("proximaVisita") ?: return@mapNotNull null
                if (millis < now) null else millis to texto
            }
            .minByOrNull { it.first }

        if (next == null) {
            binding.tvNextVisit.visibility = View.GONE
        } else {
            binding.tvNextVisit.visibility = View.VISIBLE
            binding.tvNextVisit.text = getString(R.string.pets_next_visit_banner, next.second)
        }
    }

    private fun addHistorialCard(doc: DocumentSnapshot): View {
        val item = ItemHistorialBinding.inflate(layoutInflater, binding.historialContainer, false)
        val estado = doc.registroEstado()
        val style = styleFor(estado)
        val annulled = estado == RegistroEstado.ANULADO

        val descripcion = doc.getString("descripcion").orEmpty()
        val fecha = doc.getString("fecha").orEmpty()
        val creadoPorVet = doc.getString("creadoPorVet").orEmpty()
        val editado = doc.getString("editadoPorVet")?.isNotBlank() == true
        val proximaVisita = doc.getString("proximaVisita").orEmpty()

        item.flBadge.background = GradientDrawable().apply {
            cornerRadius = dp(12f).toFloat()
            setColor(ContextCompat.getColor(this@PerfilMascotaActivity, style.badgeColorRes))
        }
        item.ivStatusIcon.setImageResource(style.iconRes)
        ImageViewCompat.setImageTintList(
            item.ivStatusIcon,
            ColorStateList.valueOf(ContextCompat.getColor(this, style.iconTintRes))
        )

        item.tvTitulo.text = SpannableStringBuilder(descripcion).apply {
            if (editado && !annulled) {
                val start = length + 1
                append(" ").append(getString(R.string.pets_edited_tag))
                setSpan(ForegroundColorSpan(ContextCompat.getColor(this@PerfilMascotaActivity, R.color.text_secondary)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(RelativeSizeSpan(0.85f), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(android.graphics.Typeface.NORMAL), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (annulled) {
            item.tvTitulo.paintFlags = item.tvTitulo.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            item.tvTitulo.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        item.tvSubtitulo.maxLines = 1
        item.tvSubtitulo.ellipsize = android.text.TextUtils.TruncateAt.END
        item.tvSubtitulo.text = listOf(fecha, creadoPorVet).filter { it.isNotBlank() }.joinToString(" · ")

        if (estado != RegistroEstado.ACEPTADO) {
            item.tvStatusPill.visibility = View.VISIBLE
            item.tvStatusPill.text = getString(style.labelRes)
            item.tvStatusPill.setBackgroundResource(style.pillBgRes)
            item.tvStatusPill.setTextColor(ContextCompat.getColor(this, style.pillTextColorRes))
        }

        if (!annulled && proximaVisita.isNotBlank()) {
            item.tvProximaVisita.visibility = View.VISIBLE
            val millis = doc.getLong("proximaVisitaMillis")
            val vencida = millis != null && millis < startOfTodayMillis()
            if (vencida) {
                item.tvProximaVisita.text = getString(R.string.pets_next_visit_overdue, proximaVisita)
                item.tvProximaVisita.setTextColor(ContextCompat.getColor(this, R.color.danger_red_dark_text))
                item.tvProximaVisita.setStartIcon(R.drawable.ic_calendar, 16, R.color.danger_red_dark_text)
            } else {
                item.tvProximaVisita.text = getString(R.string.pets_next_visit_row, proximaVisita)
                item.tvProximaVisita.setStartIcon(R.drawable.ic_calendar, 16)
            }
        }

        if (annulled) {
            val motivo = doc.getString("motivoAnulacion").orEmpty()
            if (motivo.isNotBlank()) {
                item.tvMotivo.visibility = View.VISIBLE
                item.tvMotivo.text = getString(R.string.pets_annulled_reason, motivo)
            }
        }

        if (vetMode && estado == RegistroEstado.ACEPTADO) {
            item.actions.visibility = View.VISIBLE
            item.btnEditar.setOnClickListener { showRegistroDialog(doc) }
            item.btnAnular.setOnClickListener { showAnnulDialog(doc) }
        }

        if (!vetMode && estado == RegistroEstado.PENDIENTE) {
            item.decision.visibility = View.VISIBLE
            item.btnRechazar.setOnClickListener { OfflineWrite.notify(this, doc.reference.delete()) }
            item.btnAceptar.setOnClickListener { OfflineWrite.notify(this, doc.reference.update("estado", "aceptado")) }
        }

        item.root.enablePressScale()
        item.root.setOnClickListener { showDetalleDialog(doc) }
        binding.historialContainer.addView(item.root)
        return item.root
    }

    private fun renderBehavior(doc: DocumentSnapshot) {
        binding.behaviorContainer.removeAllViews()
        val comportamiento = doc.get("comportamiento") as? List<*> ?: emptyList<String>()
        if (comportamiento.isEmpty()) {
            binding.behaviorContainer.addView(makeEmptyText(R.string.pets_no_behavior))
            return
        }
        comportamiento.forEach { binding.behaviorContainer.addView(makeTag(it.toString())) }
    }

    private fun makeTag(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(this@PerfilMascotaActivity, R.color.brand_dark))
        textSize = 12f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setBackgroundResource(R.drawable.bg_tag_pill)
        setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8f) }
    }

    private fun makeEmptyText(resId: Int): TextView = TextView(this).apply {
        text = getString(resId)
        setTextColor(ContextCompat.getColor(this@PerfilMascotaActivity, R.color.text_secondary))
        textSize = 13f
    }

    private fun showRegistroDialog(existing: DocumentSnapshot?) {
        val vetEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty()
        val form = DialogRegistroMedicoBinding.inflate(layoutInflater)
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("es-ES"))
        var proximaMillis: Long? = existing?.getLong("proximaVisitaMillis")
        var proximaTexto: String? = existing?.getString("proximaVisita")

        existing?.let {
            form.etDescripcion.setText(it.getString("descripcion").orEmpty())
            form.etObservaciones.setText(it.getString("observaciones").orEmpty())
        }

        fun refreshDate() {
            val hasDate = proximaTexto != null
            form.tvNextVisit.text = proximaTexto ?: getString(R.string.pets_dialog_next_visit_none)
            form.tvNextVisit.setTextColor(
                ContextCompat.getColor(this, if (hasDate) R.color.text_primary else R.color.text_secondary)
            )
            form.btnClearDate.visibility = if (hasDate) View.VISIBLE else View.GONE
        }
        refreshDate()

        form.etDescripcion.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!s.isNullOrBlank()) form.tilDescripcion.error = null
            }
        })

        form.rowNextVisit.setOnClickListener {
            val cal = Calendar.getInstance().apply { timeInMillis = proximaMillis ?: System.currentTimeMillis() }
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            DatePickerDialog(this, { _, y, m, d ->
                val picked = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                proximaMillis = picked.timeInMillis
                proximaTexto = dateFormat.format(picked.time)
                refreshDate()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = today.timeInMillis
            }.show()
        }
        form.btnClearDate.setOnClickListener {
            proximaMillis = null
            proximaTexto = null
            refreshDate()
        }

        val dialog = pawDialog()
            .setTitle(if (existing == null) R.string.pets_btn_add_medical else R.string.pets_dialog_edit_medical_title)
            .setView(form.root)
            .setPositiveButton(R.string.btn_guardar, null)
            .setNegativeButton(R.string.btn_cancelar, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val descripcion = form.etDescripcion.text.toString().trim()
                if (descripcion.isBlank()) {
                    form.tilDescripcion.error = getString(R.string.pets_error_medical_description)
                    return@setOnClickListener
                }
                val data = mutableMapOf<String, Any>(
                    "descripcion" to descripcion,
                    "observaciones" to form.etObservaciones.text.toString().trim()
                )
                if (existing == null) {
                    data["fecha"] = dateFormat.format(Date())
                    data["estado"] = "pendiente"
                    data["creadoPorVet"] = vetEmail
                    data["fechaCreacion"] = FieldValue.serverTimestamp()
                    val millis = proximaMillis
                    val texto = proximaTexto
                    if (millis != null && texto != null) {
                        data["proximaVisitaMillis"] = millis
                        data["proximaVisita"] = texto
                    }
                    OfflineWrite.notify(this, Firebase.firestore.collection("mascotas").document(mascotaId).collection("historial").document().set(data))
                } else {
                    data["editadoPorVet"] = vetEmail
                    data["fechaEdicion"] = FieldValue.serverTimestamp()
                    val millis = proximaMillis
                    val texto = proximaTexto
                    if (millis != null && texto != null) {
                        data["proximaVisitaMillis"] = millis
                        data["proximaVisita"] = texto
                    } else {
                        data["proximaVisitaMillis"] = FieldValue.delete()
                        data["proximaVisita"] = FieldValue.delete()
                    }
                    OfflineWrite.notify(this, existing.reference.update(data))
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showDetalleDialog(doc: DocumentSnapshot) {
        val estado = doc.registroEstado()
        val style = styleFor(estado)
        val view = DialogDetalleHistorialBinding.inflate(layoutInflater)

        val fecha = doc.getString("fecha").orEmpty()
        val creadoPorVet = doc.getString("creadoPorVet").orEmpty()
        val observaciones = doc.getString("observaciones").orEmpty()
        val proximaVisita = doc.getString("proximaVisita").orEmpty()

        view.tvStatusPill.text = getString(style.labelRes)
        view.tvStatusPill.setBackgroundResource(style.pillBgRes)
        view.tvStatusPill.setTextColor(ContextCompat.getColor(this, style.pillTextColorRes))
        view.tvStatusPill.setStartIcon(style.iconRes, 16, style.iconTintRes)

        if (fecha.isNotBlank()) {
            view.tvFecha.text = fecha
            view.tvFecha.setStartIcon(R.drawable.ic_calendar, 18)
        } else {
            view.tvFecha.visibility = View.GONE
        }

        if (creadoPorVet.isNotBlank()) {
            view.tvVet.text = getString(R.string.pets_detail_created_by, creadoPorVet)
            view.tvVet.setStartIcon(R.drawable.ic_profile, 18, R.color.text_secondary)
        } else {
            view.tvVet.visibility = View.GONE
        }

        if (estado != RegistroEstado.ANULADO && proximaVisita.isNotBlank()) {
            view.tvProximaVisita.visibility = View.VISIBLE
            view.tvProximaVisita.text = getString(R.string.pets_next_visit_row, proximaVisita)
            view.tvProximaVisita.setStartIcon(R.drawable.ic_calendar, 16)
            view.tvProximaVisita.compoundDrawablePadding = dp(8f)
        }

        if (observaciones.isBlank()) {
            view.tvObservaciones.text = getString(R.string.pets_detail_no_observations)
            view.tvObservaciones.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            view.tvObservaciones.text = observaciones
        }

        if (estado == RegistroEstado.ANULADO) {
            view.boxAnulado.visibility = View.VISIBLE
            view.tvMotivoAnulacion.text = doc.getString("motivoAnulacion").orEmpty()
            val anuladoPor = doc.getString("anuladoPor").orEmpty()
            if (anuladoPor.isNotBlank()) {
                view.tvAnuladoPor.text = getString(R.string.pets_annulled_by, anuladoPor)
            } else {
                view.tvAnuladoPor.visibility = View.GONE
            }
        }

        pawDialog()
            .setTitle(doc.getString("descripcion").orEmpty())
            .setView(view.root)
            .setPositiveButton(R.string.btn_cerrar, null)
            .show()
    }

    private fun showAnnulDialog(doc: DocumentSnapshot) {
        val vetEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty()
        val form = DialogAnularRegistroBinding.inflate(layoutInflater)

        form.etMotivo.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!s.isNullOrBlank()) form.tilMotivo.error = null
            }
        })

        val dialog = pawDialog()
            .setTitle(R.string.pets_dialog_annul_title)
            .setView(form.root)
            .setPositiveButton(R.string.pets_dialog_annul_confirm, null)
            .setNegativeButton(R.string.btn_cancelar, null)
            .create()

        dialog.setOnShowListener {
            val confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            confirm.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
            confirm.setOnClickListener {
                val motivo = form.etMotivo.text.toString().trim()
                if (motivo.isBlank()) {
                    form.tilMotivo.error = getString(R.string.pets_error_annul_reason)
                    return@setOnClickListener
                }
                OfflineWrite.notify(
                    this,
                    doc.reference.update(
                        mapOf(
                            "estado" to "anulado",
                            "motivoAnulacion" to motivo,
                            "anuladoPor" to vetEmail,
                            "fechaAnulacion" to FieldValue.serverTimestamp()
                        )
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showAddBehaviorDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.pets_dialog_behavior_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val container = android.widget.FrameLayout(this).apply {
            setPadding(dp(24f), dp(8f), dp(24f), 0)
            addView(input)
        }
        pawDialog()
            .setTitle(R.string.pets_btn_add_behavior)
            .setView(container)
            .setPositiveButton(R.string.btn_guardar) { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    OfflineWrite.notify(
                        this,
                        Firebase.firestore.collection("mascotas").document(mascotaId)
                            .update("comportamiento", FieldValue.arrayUnion(text))
                    )
                }
            }
            .setNegativeButton(R.string.btn_cancelar, null)
            .show()
    }

    private fun pawDialog() = MaterialAlertDialogBuilder(this)
        .setBackground(ContextCompat.getDrawable(this, R.drawable.bg_dialog))

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        mascotaListener?.remove()
        historialListener?.remove()
        super.onDestroy()
    }
}
