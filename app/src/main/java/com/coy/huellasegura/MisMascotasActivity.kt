package com.coy.huellasegura

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityMisMascotasBinding
import com.coy.huellasegura.databinding.ItemMascotaBinding
import com.coy.huellasegura.databinding.ItemSolicitudBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

class MisMascotasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMisMascotasBinding
    private var userName: String? = null
    private var userEmail: String = ""
    private var vetsListener: ListenerRegistration? = null
    private var petsListener: ListenerRegistration? = null
    private var pendingVets: List<DocumentSnapshot> = emptyList()
    private var pendingPets: List<DocumentSnapshot> = emptyList()
    private var acceptedPets: List<DocumentSnapshot> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMisMascotasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userName = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME)
        userEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase()

        binding.btnBack.setOnClickListener { finish() }

        vetsListener = Firebase.firestore.collection("veterinarios")
            .whereEqualTo("estado", "pendiente")
            .addSnapshotListener { snapshot, _ ->
                pendingVets = snapshot?.documents.orEmpty()
                render()
            }

        binding.petsContainer.showStatusMessage(getString(R.string.list_loading))
        petsListener = Firebase.firestore.collection("mascotas")
            .whereEqualTo("duenoCorreo", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null && snapshot == null) {
                    binding.petsContainer.showStatusMessage(getString(R.string.list_error))
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                pendingPets = docs.filter { it.getString("estado") == "pendiente" }
                acceptedPets = docs.filter { it.getString("estado") == "aceptada" }
                render()
            }
    }

    private fun render() {
        renderRequests()
        renderPets()
    }

    private fun renderRequests() {
        binding.requestsContainer.removeAllViews()
        val hasRequests = pendingVets.isNotEmpty() || pendingPets.isNotEmpty()
        binding.requestsSection.visibility = if (hasRequests) View.VISIBLE else View.GONE
        if (!hasRequests) return

        pendingVets.forEach { doc -> addVetRequestView(doc) }
        pendingPets.forEach { doc -> addPetRequestView(doc) }
    }

    private fun addVetRequestView(doc: DocumentSnapshot) {
        val item = ItemSolicitudBinding.inflate(layoutInflater, binding.requestsContainer, false)
        item.tvSolicitudTitle.text = getString(R.string.pets_request_vet_title)
        item.tvSolicitudSubtitle.text = getString(R.string.pets_request_vet_subtitle, doc.id)
        item.btnAceptar.setOnClickListener { OfflineWrite.notify(this, doc.reference.update("estado", "aceptado")) }
        item.btnRechazar.setOnClickListener { OfflineWrite.notify(this, doc.reference.delete()) }
        binding.requestsContainer.addView(item.root)
    }

    private fun addPetRequestView(doc: DocumentSnapshot) {
        val item = ItemSolicitudBinding.inflate(layoutInflater, binding.requestsContainer, false)
        val nombre = doc.getString("nombre").orEmpty()
        val vet = doc.getString("creadoPorVet").orEmpty()
        item.tvSolicitudTitle.text = getString(R.string.pets_request_pet_title, nombre)
        item.tvSolicitudSubtitle.text = getString(R.string.pets_request_pet_subtitle, vet)
        item.btnAceptar.setOnClickListener { OfflineWrite.notify(this, doc.reference.update("estado", "aceptada")) }
        item.btnRechazar.setOnClickListener { OfflineWrite.notify(this, doc.reference.delete()) }
        binding.requestsContainer.addView(item.root)
    }

    private fun renderPets() {
        binding.petsContainer.removeAllViews()
        if (acceptedPets.isEmpty()) {
            val title = TextView(this).apply {
                text = getString(R.string.pets_empty_title)
                setTextColor(ContextCompat.getColor(this@MisMascotasActivity, R.color.text_primary))
                textSize = 15f
            }
            val subtitle = TextView(this).apply {
                text = getString(R.string.pets_empty_subtitle)
                setTextColor(ContextCompat.getColor(this@MisMascotasActivity, R.color.text_secondary))
                textSize = 13f
                setPadding(0, dp(6f), 0, 0)
            }
            binding.petsContainer.addView(title)
            binding.petsContainer.addView(subtitle)
            return
        }
        acceptedPets.forEach { doc -> addPetView(doc) }
    }

    private fun addPetView(doc: DocumentSnapshot) {
        val item = ItemMascotaBinding.inflate(layoutInflater, binding.petsContainer, false)
        val especie = doc.getString("especie").orEmpty()
        item.tvMascotaNombre.text = doc.getString("nombre").orEmpty()
        item.tvMascotaSubtitulo.text = listOf(especie, doc.getString("raza").orEmpty(), doc.getString("edad").orEmpty().edadConUnidad())
            .filter { it.isNotBlank() }.joinToString(" · ")
        item.ivMascotaIcon.setImageResource(
            when (especie) {
                getString(R.string.pets_species_cat) -> R.drawable.ic_cat
                getString(R.string.pets_species_dog) -> R.drawable.ic_dog
                else -> R.drawable.ic_paw
            }
        )
        item.root.setOnClickListener {
            startActivity(Intent(this, PerfilMascotaActivity::class.java).apply {
                putExtra(PerfilMascotaActivity.EXTRA_MASCOTA_ID, doc.id)
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
            })
        }
        binding.petsContainer.addView(item.root)
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        vetsListener?.remove()
        petsListener?.remove()
        super.onDestroy()
    }
}
