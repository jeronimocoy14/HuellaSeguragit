package com.coy.huellasegura

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityAgregarMascotaBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore

class AgregarMascotaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgregarMascotaBinding
    private var selectedSpecies = ""

    private data class SpeciesOption(val container: LinearLayout, val icon: android.widget.ImageView, val label: TextView)
    private lateinit var speciesOptions: List<SpeciesOption>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgregarMascotaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        speciesOptions = listOf(
            SpeciesOption(binding.optDog, binding.iconDog, binding.labelDog),
            SpeciesOption(binding.optCat, binding.iconCat, binding.labelCat),
            SpeciesOption(binding.optOther, binding.iconOther, binding.labelOther)
        )
        speciesOptions.forEach { option -> option.container.setOnClickListener { selectSpecies(option) } }
        selectSpecies(speciesOptions[0])

        binding.btnGuardar.setOnClickListener { guardarMascota() }
    }

    private fun selectSpecies(selected: SpeciesOption) {
        speciesOptions.forEach { option ->
            val active = option === selected
            option.container.setBackgroundResource(if (active) R.drawable.bg_option_card_active else R.drawable.bg_option_card)
            val color = ContextCompat.getColor(this, if (active) R.color.brand_dark else R.color.text_primary)
            option.label.setTextColor(color)
        }
        selectedSpecies = selected.label.text.toString()
    }

    private fun guardarMascota() {
        val duenoCorreo = binding.etCorreoDueno.text.toString().trim()
        if (duenoCorreo.isBlank()) {
            binding.etCorreoDueno.error = getString(R.string.pets_error_owner_email)
            return
        }
        val nombre = binding.etNombre.text.toString().trim()
        if (nombre.isBlank()) {
            binding.etNombre.error = getString(R.string.pets_error_name)
            return
        }
        val vetEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty()
        binding.btnGuardar.isEnabled = false

        val mascota = hashMapOf(
            "nombre" to nombre,
            "especie" to selectedSpecies,
            "raza" to binding.etRaza.text.toString().trim(),
            "edad" to binding.etEdad.text.toString().trim(),
            "estado" to "pendiente",
            "creadoPorVet" to vetEmail,
            "duenoCorreo" to duenoCorreo.lowercase(),
            "comportamiento" to emptyList<String>(),
            "fechaCreacion" to FieldValue.serverTimestamp()
        )
        Firebase.firestore.collection("mascotas").add(mascota)
            .addOnSuccessListener { finish() }
            .addOnFailureListener {
                binding.btnGuardar.isEnabled = true
                android.widget.Toast.makeText(this, R.string.report_send_error, android.widget.Toast.LENGTH_SHORT).show()
            }
    }
}
