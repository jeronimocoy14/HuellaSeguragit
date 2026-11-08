package com.coy.huellasegura

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivitySimpleListBinding
import com.coy.huellasegura.databinding.ItemSimpleCardBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.util.Locale

/**
 * Listas del modo simplificado: tarjetas grandes, pocas cosas por pantalla y un botón «Escuchar» que lee la
 * tarjeta en voz alta. Una sola pantalla con cuatro contenidos ([KIND_REPORTS], [KIND_PETS], [KIND_LOST],
 * [KIND_COMMUNITY]) para que todas se vean y se comporten igual.
 */
class SimpleListActivity : BaseAccessibleActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_KIND = "extra_kind"
        const val KIND_REPORTS = "reports"
        const val KIND_PETS = "pets"
        const val KIND_LOST = "lost"
        const val KIND_COMMUNITY = "community"
    }

    private class Card(
        val photo: Bitmap? = null,
        val icon: Drawable? = null,
        val title: String,
        val line2: String = "",
        val line3: String = "",
        val status: Pair<String, StatusStyle.Kind>? = null,
        val extra: String = "",
        val speak: String,
        val onClick: (() -> Unit)? = null,
        val yesLabel: String? = null,
        val onYes: (() -> Unit)? = null,
        val noLabel: String? = null,
        val onNo: (() -> Unit)? = null,
    )

    private lateinit var binding: ActivitySimpleListBinding
    private var kind = KIND_REPORTS
    private var userEmail = ""
    private var userName = ""
    private var listener: ListenerRegistration? = null
    private var docs: List<DocumentSnapshot> = emptyList()
    private var loaded = false
    private var here: Location? = null
    private var askedPermission = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var allSpeech = ""

    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { locate() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_REPORTS
        userEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase()
        userName = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()

        binding.btnBack.setOnClickListener { finish() }
        val voice = isTtsEnabled()
        binding.btnListenAll.visibility = if (voice) View.VISIBLE else View.GONE
        if (voice) tts = TextToSpeech(this, this)
        binding.btnListenAll.setOnClickListener { speak(allSpeech) }

        setupHeader()
        listen()
        if (kind == KIND_COMMUNITY) locate()
    }

    private fun setupHeader() {
        val (title, hint) = when (kind) {
            KIND_PETS -> R.string.simple_title_pets to R.string.simple_hint_pets
            KIND_LOST -> R.string.simple_title_lost to R.string.simple_hint_lost
            KIND_COMMUNITY -> R.string.simple_title_community to R.string.simple_hint_community
            else -> R.string.simple_title_reports to R.string.simple_hint_reports
        }
        binding.tvTitle.setText(title)
        binding.tvHint.setText(hint)

        when (kind) {
            KIND_REPORTS -> action(R.string.simple_action_report) {
                startActivity(Intent(this, ReportarPeligroSimpleActivity::class.java).putUser())
            }
            KIND_LOST -> action(R.string.simple_action_lost) {
                startActivity(Intent(this, ReportarPerdidoActivity::class.java).putUser())
            }
        }
    }

    private fun action(label: Int, onClick: () -> Unit) {
        binding.btnAction.visibility = View.VISIBLE
        binding.btnAction.setText(label)
        binding.btnAction.setOnClickListener { onClick() }
    }

    private fun Intent.putUser(): Intent = apply {
        putExtra(LoginActivity.EXTRA_USER_NAME, userName)
        putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
    }

    // ---------------------------------------------------------------- Datos

    private fun listen() {
        val db = Firebase.firestore
        val query = when (kind) {
            KIND_PETS -> db.collection("mascotas").whereEqualTo("duenoCorreo", userEmail)
            KIND_LOST -> db.collection("perdidos").whereEqualTo("estado", "publicado")
            KIND_COMMUNITY -> db.collection("comunitarios").whereEqualTo("activo", true)
            else -> db.collection("reportes").whereEqualTo("reportanteCorreo", userEmail)
        }
        binding.listContainer.showStatusMessage(getString(R.string.list_loading))
        listener = query.addSnapshotListener { snapshot, error ->
            if (error != null && snapshot == null) {
                binding.listContainer.showStatusMessage(getString(R.string.list_error))
                return@addSnapshotListener
            }
            docs = snapshot?.documents.orEmpty()
            loaded = true
            render()
        }
    }

    private fun locate() {
        if (!LocationUtil.hasPermission(this)) {
            if (!askedPermission) {
                askedPermission = true
                requestLocation.launch(LocationUtil.PERMISSIONS)
            }
            render()
            return
        }
        LocationUtil.getCurrent(this) { location ->
            here = location
            render()
        }
    }

    // ---------------------------------------------------------------- Tarjetas

    private fun render() {
        if (!loaded) return
        val cards = when (kind) {
            KIND_PETS -> petCards()
            KIND_LOST -> lostCards()
            KIND_COMMUNITY -> communityCards()
            else -> reportCards()
        }
        binding.listContainer.removeAllViews()
        if (cards.isEmpty()) {
            binding.listContainer.addView(TextView(this).apply {
                setText(emptyMessage())
                setTextColor(ContextCompat.getColor(this@SimpleListActivity, R.color.text_secondary))
                textSize = 19f
                setLineSpacing(0f, 1.2f)
                setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
            })
        }
        cards.forEach { addCard(it) }
        allSpeech = (listOf(binding.tvTitle.text.toString(), binding.tvHint.text.toString()) + cards.take(6).map { it.speak })
            .joinToString(". ")
    }

    private fun emptyMessage() = when (kind) {
        KIND_PETS -> R.string.pets_empty_title
        KIND_LOST -> R.string.perdidos_catalog_empty
        KIND_COMMUNITY -> if (here == null) R.string.comunitarios_empty else R.string.comunitarios_empty_near
        else -> R.string.reports_empty
    }

    private fun addCard(card: Card) {
        val item = ItemSimpleCardBinding.inflate(layoutInflater, binding.listContainer, false)
        when {
            card.photo != null -> item.ivFoto.setImageBitmap(card.photo)
            card.icon != null -> {
                val pad = (20 * resources.displayMetrics.density).toInt()
                item.ivFoto.setPadding(pad, pad, pad, pad)
                item.ivFoto.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                item.ivFoto.setImageDrawable(card.icon)
            }
            else -> {
                item.ivFoto.visibility = View.GONE
                // Sin foto ni icono, el texto empieza en el borde de la tarjeta.
                (item.tvTitulo.parent as View).let { column ->
                    (column.layoutParams as android.widget.LinearLayout.LayoutParams).marginStart = 0
                }
            }
        }
        item.tvTitulo.text = card.title
        fun fill(view: TextView, text: String) {
            view.text = text
            view.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        fill(item.tvLinea2, card.line2)
        fill(item.tvLinea3, card.line3)
        fill(item.tvExtra, card.extra)
        card.status?.let { (label, statusKind) ->
            item.tvEstado.visibility = View.VISIBLE
            item.tvEstado.text = label
            StatusStyle.apply(item.tvEstado, statusKind)
        }
        card.onClick?.let { click ->
            item.rowMain.isClickable = true
            item.rowMain.setOnClickListener { click() }
        }
        if (isTtsEnabled()) {
            item.btnEscuchar.setOnClickListener { speak(card.speak) }
        } else {
            item.btnEscuchar.visibility = View.GONE
        }
        if (card.yesLabel != null && card.onYes != null && card.noLabel != null && card.onNo != null) {
            item.rowActions.visibility = View.VISIBLE
            item.btnSi.text = card.yesLabel
            item.btnNo.text = card.noLabel
            item.btnSi.setOnClickListener { card.onYes.invoke() }
            item.btnNo.setOnClickListener { card.onNo.invoke() }
        }
        binding.listContainer.addView(item.root)
    }

    private fun reportCards(): List<Card> = docs.sortedByDescending { it.createdAtMillis() }.map { doc ->
        val estado = doc.getString("estado").orEmpty()
        val (labelRes, detailRes, statusKind) = when (estado) {
            "en_atencion" -> Triple(R.string.reports_status_attending, R.string.reports_detail_attending, StatusStyle.Kind.OK)
            "resuelto" -> Triple(R.string.reports_status_resolved, R.string.reports_detail_resolved, StatusStyle.Kind.OK)
            else -> Triple(R.string.reports_status_waiting, R.string.reports_detail_waiting, StatusStyle.Kind.WAITING)
        }
        val tipo = doc.getString("tipo").orEmpty().ifBlank { getString(R.string.report_type_other) }
        val place = doc.getString("direccion").orEmpty().ifBlank { doc.getString("referencia").orEmpty() }
        val time = ReportStats.createdAt(doc)?.let { CaseCard.relativeTime(this, it) }.orEmpty()
        Card(
            photo = PhotoUtil.decode(doc.getString("fotoBase64"), doc.id),
            icon = null,
            title = tipo,
            line2 = time,
            line3 = place,
            status = getString(labelRes) to statusKind,
            extra = getString(detailRes),
            speak = listOf(tipo, getString(labelRes), getString(detailRes)).joinToString(". "),
        )
    }

    private fun lostCards(): List<Card> = docs.sortedByDescending { it.createdAtMillis() }.map { doc ->
        val name = doc.getString("nombre").orEmpty()
        val species = doc.getString("especie").orEmpty()
        val description = doc.getString("descripcion").orEmpty()
        Card(
            photo = PhotoUtil.decode(doc.getString("fotoBase64"), doc.id),
            title = name,
            line2 = species,
            extra = description,
            speak = listOf(name, species, description).filter { it.isNotBlank() }.joinToString(". "),
            onClick = {
                startActivity(Intent(this, PerdidoDetalleActivity::class.java).putUser()
                    .putExtra(PerdidoDetalleActivity.EXTRA_PERDIDO_ID, doc.id))
            },
        )
    }

    private fun communityCards(): List<Card> {
        val me = here
        val withDistance = docs.map { it to Comunitarios.distanceTo(it, me) }
        val shown = if (me != null) {
            withDistance.filter { (it.second ?: Double.MAX_VALUE) <= Comunitarios.RADIUS_METERS }.sortedBy { it.second }
        } else {
            withDistance.sortedByDescending { it.first.createdAtMillis() }
        }
        return shown.map { (doc, distance) ->
            val name = doc.getString("nombre").orEmpty()
            val species = doc.getString("especie").orEmpty()
            val distanceText = distance?.let { Comunitarios.formatDistance(this, it) }.orEmpty()
            val reference = doc.getString("referencia").orEmpty()
            val health = getString(
                R.string.comunitarios_health,
                getString(if (doc.getBoolean("esterilizado") == true) R.string.export_yes else R.string.comunitarios_no),
                getString(if (doc.getBoolean("vacunado") == true) R.string.export_yes else R.string.comunitarios_no),
            )
            val note = doc.getString("nota").orEmpty()
            Card(
                photo = PhotoUtil.decode(doc.getString("fotoBase64"), doc.id),
                title = name,
                line2 = listOf(species, distanceText).filter { it.isNotBlank() }.joinToString(" · "),
                line3 = reference,
                extra = listOf(health, note).filter { it.isNotBlank() }.joinToString("\n"),
                speak = listOf(name, species, distanceText, reference, health, note).filter { it.isNotBlank() }.joinToString(". "),
            )
        }
    }

    private fun petCards(): List<Card> {
        val pending = docs.filter { it.getString("estado") == "pendiente" }
        val accepted = docs.filter { it.getString("estado") == "aceptada" }
        val requests = pending.map { doc ->
            val name = doc.getString("nombre").orEmpty()
            val species = doc.getString("especie").orEmpty()
            val vet = doc.getString("creadoPorVet").orEmpty()
            val question = getString(R.string.simple_pet_question, name)
            Card(
                icon = Species.icon(this, species, 40),
                title = name,
                line2 = species,
                extra = question,
                speak = listOf(question, getString(R.string.pets_request_pet_subtitle, vet)).joinToString(". "),
                yesLabel = getString(R.string.simple_pet_yes),
                onYes = { OfflineWrite.notify(this, doc.reference.update("estado", "aceptada")) },
                noLabel = getString(R.string.simple_pet_no),
                onNo = { confirmReject(doc, name) },
            )
        }
        val mine = accepted.map { doc ->
            val name = doc.getString("nombre").orEmpty()
            val species = doc.getString("especie").orEmpty()
            val breed = doc.getString("raza").orEmpty()
            val age = doc.getString("edad").orEmpty().edadConUnidad()
            Card(
                icon = Species.icon(this, species, 40),
                title = name,
                line2 = species,
                line3 = listOf(breed, age).filter { it.isNotBlank() }.joinToString(" · "),
                speak = listOf(name, species, breed, age).filter { it.isNotBlank() }.joinToString(". "),
                onClick = {
                    startActivity(Intent(this, PerfilMascotaActivity::class.java)
                        .putExtra(PerfilMascotaActivity.EXTRA_MASCOTA_ID, doc.id)
                        .putExtra(LoginActivity.EXTRA_USER_NAME, userName))
                },
            )
        }
        return requests + mine
    }

    private fun confirmReject(doc: DocumentSnapshot, name: String) {
        styledDialog()
            .setTitle(getString(R.string.simple_pet_reject_title, name))
            .setMessage(R.string.simple_pet_reject_message)
            .setNegativeButton(R.string.btn_cancelar, null)
            .setPositiveButton(R.string.simple_pet_no) { _, _ -> OfflineWrite.notify(this, doc.reference.delete()) }
            .show()
    }

    // ---------------------------------------------------------------- Voz

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale("es", "ES")
            pendingSpeech?.let { speak(it) }
            pendingSpeech = null
        }
    }

    private fun speak(text: String) {
        if (!isTtsEnabled() || text.isBlank()) return
        if (!ttsReady) {
            pendingSpeech = text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        listener?.remove()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
