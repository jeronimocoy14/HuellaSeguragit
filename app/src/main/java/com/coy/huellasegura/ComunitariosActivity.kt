package com.coy.huellasegura

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityComunitariosBinding
import com.coy.huellasegura.databinding.ItemComunitarioBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

/**
 * Animales comunitarios (los que viven en la calle y cuida todo el barrio).
 * - Ciudadano: los que están a menos de 1 km de donde está (o todos), con la distancia y la referencia del lugar,
 *   pero sin el punto exacto en el mapa.
 * - Personal (funcionario o veterinario): todos, con la dirección y el mapa; puede registrar, actualizar la salud
 *   y marcar «ya no está».
 */
class ComunitariosActivity : BaseAccessibleActivity() {

    companion object {
        const val EXTRA_STAFF = "extra_staff"
    }

    private lateinit var binding: ActivityComunitariosBinding
    private var staff = false
    private var userEmail = ""
    private var listener: ListenerRegistration? = null
    private var docs: List<DocumentSnapshot> = emptyList()
    private var loaded = false
    private var here: Location? = null
    private var showAll = false
    private var askedPermission = false
    private var speciesFilter: String? = null

    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { locate() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComunitariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        staff = intent.getBooleanExtra(EXTRA_STAFF, false)
        userEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase()

        binding.btnBack.setOnClickListener { finish() }
        SpeciesFilter.setup(this, binding.speciesFilter.chipGroupSpecies) { speciesFilter = it; render() }

        if (staff) {
            binding.btnAdd.visibility = View.VISIBLE
            binding.btnAdd.setOnClickListener {
                startActivity(Intent(this, AgregarComunitarioActivity::class.java).apply {
                    putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
                })
            }
        } else {
            binding.tvInfo.setOnClickListener { onInfoTapped() }
        }

        // Los ciudadanos solo pueden leer los que siguen en su zona; el personal ve todos.
        val collection = Firebase.firestore.collection("comunitarios")
        val source = if (staff) collection else collection.whereEqualTo("activo", true)
        binding.listContainer.showStatusMessage(getString(R.string.list_loading))
        listener = source.addSnapshotListener { snapshot, error ->
            if (error != null && snapshot == null) {
                binding.listContainer.showStatusMessage(getString(R.string.list_error))
                return@addSnapshotListener
            }
            docs = snapshot?.documents.orEmpty()
            loaded = true
            render()
        }

        if (!staff) locate()
    }

    /** Busca dónde está la persona; si no ha dado permiso lo pide una vez. */
    private fun locate() {
        if (staff) return
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

    private fun onInfoTapped() {
        when {
            here != null -> { showAll = !showAll; render() }
            else -> { askedPermission = false; locate() }
        }
    }

    private fun distanceTo(doc: DocumentSnapshot): Double? {
        val me = here ?: return null
        val lat = doc.getDouble("lat") ?: return null
        val lon = doc.getDouble("lon") ?: return null
        return Comunitarios.distanceMeters(me.latitude, me.longitude, lat, lon)
    }

    private fun render() {
        if (!loaded) return
        val withDistance = docs
            .filter { SpeciesFilter.matches(it, speciesFilter) }
            .map { it to distanceTo(it) }

        val shown = when {
            staff -> withDistance.sortedByDescending { it.first.createdAtMillis() }.sortedBy { it.first.getBoolean("activo") == false }
            here != null && !showAll -> withDistance.filter { (it.second ?: Double.MAX_VALUE) <= Comunitarios.RADIUS_METERS }.sortedBy { it.second }
            here != null -> withDistance.sortedBy { it.second ?: Double.MAX_VALUE }
            else -> withDistance.sortedByDescending { it.first.createdAtMillis() }
        }

        binding.tvInfo.text = when {
            staff -> getString(R.string.comunitarios_info_staff, docs.count { it.getBoolean("activo") != false })
            here != null && !showAll -> getString(R.string.comunitarios_info_near)
            here != null -> getString(R.string.comunitarios_info_all)
            else -> getString(R.string.comunitarios_info_no_location)
        }
        binding.tvInfo.isClickable = !staff

        binding.listContainer.removeAllViews()
        if (shown.isEmpty()) {
            val message = when {
                docs.isEmpty() -> R.string.comunitarios_empty
                speciesFilter != null -> R.string.species_filter_empty
                else -> R.string.comunitarios_empty_near
            }
            binding.listContainer.addView(TextView(this).apply {
                text = getString(message)
                setTextColor(ContextCompat.getColor(this@ComunitariosActivity, R.color.text_secondary))
                textSize = 14f
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            return
        }
        shown.forEach { (doc, distance) -> addItem(doc, distance) }
    }

    private fun addItem(doc: DocumentSnapshot, distance: Double?) {
        val item = ItemComunitarioBinding.inflate(layoutInflater, binding.listContainer, false)
        val active = doc.getBoolean("activo") != false
        val especie = doc.getString("especie").orEmpty()

        item.ivFoto.setImageBitmap(PhotoUtil.decode(doc.getString("fotoBase64"), doc.id))
        item.tvNombre.text = doc.getString("nombre").orEmpty()
        item.tvMeta.text = listOfNotNull(
            especie.ifBlank { null },
            distance?.let { Comunitarios.formatDistance(this, it) },
            if (active) null else getString(R.string.comunitarios_gone),
        ).joinToString(" · ")

        val reference = doc.getString("referencia").orEmpty()
        val address = doc.getString("direccion").orEmpty()
        // El ciudadano ve la referencia del lugar; la dirección exacta es para el personal.
        item.tvReferencia.text = if (staff) listOf(reference, address).filter { it.isNotBlank() }.joinToString("\n") else reference
        item.tvReferencia.visibility = if (item.tvReferencia.text.isNullOrBlank()) View.GONE else View.VISIBLE

        item.tvSalud.text = getString(
            R.string.comunitarios_health,
            getString(if (doc.getBoolean("esterilizado") == true) R.string.export_yes else R.string.comunitarios_no),
            getString(if (doc.getBoolean("vacunado") == true) R.string.export_yes else R.string.comunitarios_no),
        )

        val note = doc.getString("nota").orEmpty()
        if (note.isNotBlank()) {
            item.tvNota.visibility = View.VISIBLE
            item.tvNota.text = note
        }
        if (!active) item.root.alpha = 0.6f

        if (staff) {
            item.rowStaff.visibility = View.VISIBLE
            val lat = doc.getDouble("lat")
            val lon = doc.getDouble("lon")
            item.btnMapa.isEnabled = lat != null && lon != null
            item.btnMapa.setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GeoUtil.mapsUrl(lat!!, lon!!)))) }
                    .onFailure { Toast.makeText(this, R.string.report_send_error, Toast.LENGTH_SHORT).show() }
            }
            item.btnSalud.setOnClickListener { editHealth(doc) }
            item.btnEstado.setText(if (active) R.string.comunitarios_btn_gone else R.string.comunitarios_btn_back)
            item.btnEstado.setOnClickListener { if (active) confirmGone(doc) else setActive(doc, true) }
        }
        binding.listContainer.addView(item.root)
    }

    private fun editHealth(doc: DocumentSnapshot) {
        val labels = arrayOf(getString(R.string.comunitarios_sterilized), getString(R.string.comunitarios_vaccinated))
        val checked = booleanArrayOf(doc.getBoolean("esterilizado") == true, doc.getBoolean("vacunado") == true)
        styledDialog()
            .setTitle(getString(R.string.comunitarios_health_title, doc.getString("nombre").orEmpty()))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton(R.string.btn_cancelar, null)
            .setPositiveButton(R.string.comunitarios_save) { _, _ ->
                OfflineWrite.notify(this, doc.reference.update(mapOf("esterilizado" to checked[0], "vacunado" to checked[1])))
            }
            .show()
    }

    private fun confirmGone(doc: DocumentSnapshot) {
        styledDialog()
            .setTitle(getString(R.string.comunitarios_gone_title, doc.getString("nombre").orEmpty()))
            .setMessage(R.string.comunitarios_gone_message)
            .setNegativeButton(R.string.btn_cancelar, null)
            .setPositiveButton(R.string.comunitarios_btn_gone) { _, _ -> setActive(doc, false) }
            .show()
    }

    private fun setActive(doc: DocumentSnapshot, active: Boolean) {
        OfflineWrite.notify(this, doc.reference.update("activo", active))
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}
