package com.coy.huellasegura

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityAgregarMascotaBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore

class AgregarMascotaActivity : BaseAccessibleActivity() {

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

        buildSpeciesGrid()
        selectSpecies(speciesOptions[0])

        binding.btnGuardar.setOnClickListener { guardarMascota() }
    }

    /** Tarjetas de especie en filas de [PER_ROW]; la última fila se completa con huecos para que midan igual. */
    private fun buildSpeciesGrid() {
        val density = resources.displayMetrics.density
        val gap = (6 * density).toInt()
        val options = mutableListOf<SpeciesOption>()
        Species.all.chunked(PER_ROW).forEachIndexed { rowIndex, items ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = if (rowIndex == 0) 0 else gap * 2 }
            }
            items.forEachIndexed { col, item ->
                val icon = android.widget.ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt())
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setImageDrawable(Species.icon(this@AgregarMascotaActivity, item, 28))
                }
                val label = TextView(this).apply {
                    text = getString(item.nameRes)
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = (6 * density).toInt() }
                }
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    setPadding((8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        .apply { marginStart = if (col == 0) 0 else gap; marginEnd = if (col == PER_ROW - 1) 0 else gap }
                    addView(icon)
                    addView(label)
                }
                row.addView(card)
                options.add(SpeciesOption(card, icon, label))
            }
            repeat(PER_ROW - items.size) {
                row.addView(android.view.View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            binding.speciesGrid.addView(row)
        }
        speciesOptions = options
        speciesOptions.forEach { option -> option.container.setOnClickListener { selectSpecies(option) } }
    }

    private fun selectSpecies(selected: SpeciesOption) {
        speciesOptions.forEach { option ->
            val active = option === selected
            option.container.setBackgroundResource(if (active) R.drawable.bg_option_card_active else R.drawable.bg_option_card)
            val color = ContextCompat.getColor(this, if (active) R.color.brand_dark else R.color.text_primary)
            option.label.setTextColor(color)
            option.container.isSelected = active
        }
        selectedSpecies = selected.label.text.toString()
    }

    private fun guardarMascota() {
        val duenoCorreo = binding.etCorreoDueno.text.toString().trim()
        if (duenoCorreo.isBlank()) {
            binding.etCorreoDueno.error = getString(R.string.pets_error_owner_email)
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(duenoCorreo).matches()) {
            binding.etCorreoDueno.error = getString(R.string.pets_error_owner_email_invalid)
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
        OfflineWrite.run(
            this,
            Firebase.firestore.collection("mascotas").document().set(mascota),
            onDone = { queued ->
                if (queued) android.widget.Toast.makeText(this, R.string.pets_queued, android.widget.Toast.LENGTH_LONG).show()
                finish()
            },
            onError = {
                binding.btnGuardar.isEnabled = true
                android.widget.Toast.makeText(this, R.string.report_send_error, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    private companion object {
        const val PER_ROW = 3
    }
}
