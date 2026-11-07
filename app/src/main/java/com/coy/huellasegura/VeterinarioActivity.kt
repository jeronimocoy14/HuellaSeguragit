package com.coy.huellasegura

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityVeterinarioBinding
import com.coy.huellasegura.databinding.ItemMascotaBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

class VeterinarioActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityVeterinarioBinding
    private lateinit var vetEmail: String
    private var petsListener: ListenerRegistration? = null
    private var petDocs: List<DocumentSnapshot> = emptyList()
    private var speciesFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVeterinarioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        vetEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty()

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java).apply {
                putExtra(ProfileActivity.EXTRA_IS_PROFESSIONAL, true)
                putExtra(ProfileActivity.EXTRA_IS_VETERINARIO, true)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, vetEmail)
            })
        }

        binding.btnAddPet.setOnClickListener {
            startActivity(Intent(this, AgregarMascotaActivity::class.java).apply {
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, vetEmail)
            })
        }

        SpeciesFilter.setup(this, binding.speciesFilter.chipGroupSpecies) { speciesFilter = it; renderPets(petDocs) }

        checkOwnStatus()
        listenForPets()
    }

    private fun checkOwnStatus() {
        Firebase.firestore.collection("veterinarios").document(vetEmail).get()
            .addOnSuccessListener { doc ->
                binding.tvPendingBanner.visibility = if (doc.getString("estado") != "aceptado") View.VISIBLE else View.GONE
            }
    }

    private fun listenForPets() {
        binding.petsContainer.showStatusMessage(getString(R.string.list_loading))
        petsListener = Firebase.firestore.collection("mascotas")
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null && snapshot == null) {
                    binding.petsContainer.showStatusMessage(getString(R.string.list_error))
                    return@addSnapshotListener
                }
                renderPets(snapshot?.documents.orEmpty())
            }
    }

    private fun renderPets(docs: List<DocumentSnapshot>) {
        petDocs = docs
        binding.petsContainer.removeAllViews()
        if (docs.isEmpty()) {
            addEmptyState()
            return
        }
        val shown = docs.filter { SpeciesFilter.matches(it, speciesFilter) }
        if (shown.isEmpty()) {
            binding.petsContainer.addView(android.widget.TextView(this).apply {
                text = getString(R.string.species_filter_empty)
                setTextColor(androidx.core.content.ContextCompat.getColor(this@VeterinarioActivity, R.color.text_secondary))
                textSize = 14f
            })
            return
        }
        shown.forEach { addPetView(it) }
    }

    private fun addEmptyState() {
        val title = android.widget.TextView(this).apply {
            text = getString(R.string.vet_empty_title)
            setTextColor(androidx.core.content.ContextCompat.getColor(this@VeterinarioActivity, R.color.text_primary))
            textSize = 15f
        }
        val subtitle = android.widget.TextView(this).apply {
            text = getString(R.string.vet_empty_subtitle)
            setTextColor(androidx.core.content.ContextCompat.getColor(this@VeterinarioActivity, R.color.text_secondary))
            textSize = 13f
            setPadding(0, dp(6f), 0, 0)
        }
        binding.petsContainer.addView(title)
        binding.petsContainer.addView(subtitle)
    }

    private fun addPetView(doc: DocumentSnapshot) {
        val item = ItemMascotaBinding.inflate(layoutInflater, binding.petsContainer, false)
        val nombre = doc.getString("nombre").orEmpty()
        val especie = doc.getString("especie").orEmpty()
        val raza = doc.getString("raza").orEmpty()
        val edad = doc.getString("edad").orEmpty().edadConUnidad()
        val estado = doc.getString("estado").orEmpty()
        val duenoCorreo = doc.getString("duenoCorreo").orEmpty()

        item.tvMascotaNombre.text = nombre
        val subtitleParts = listOf(especie, raza, edad).filter { it.isNotBlank() }.toMutableList()
        if (duenoCorreo.isNotBlank()) subtitleParts.add(getString(R.string.pets_owner_prefix, duenoCorreo))
        item.tvMascotaSubtitulo.text = subtitleParts.joinToString(" · ")
        item.ivMascotaIcon.setImageDrawable(Species.icon(this, especie))
        item.tvMascotaEstado.visibility = View.VISIBLE
        item.tvMascotaEstado.text = getString(
            if (estado == "aceptada") R.string.pets_status_accepted else R.string.pets_status_pending
        )

        item.root.setOnClickListener {
            startActivity(Intent(this, PerfilMascotaActivity::class.java).apply {
                putExtra(PerfilMascotaActivity.EXTRA_MASCOTA_ID, doc.id)
                putExtra(PerfilMascotaActivity.EXTRA_VET_MODE, true)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, vetEmail)
            })
        }
        binding.petsContainer.addView(item.root)
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        petsListener?.remove()
        super.onDestroy()
    }
}
