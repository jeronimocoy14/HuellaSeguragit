package com.coy.huellasegura

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.coy.huellasegura.databinding.ActivityAgregarComunitarioBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de un animal comunitario por el personal (funcionario o veterinario): foto (con la categoría detectada),
 * apodo, lugar (GPS o dirección escrita), referencia para los vecinos, nota de cuidado y salud.
 */
class AgregarComunitarioActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityAgregarComunitarioBinding
    private var photoFile: File? = null
    private var pendingUri: Uri? = null
    private var detected: SpeciesDetector.Result? = null
    private var lat: Double? = null
    private var lon: Double? = null
    /** Texto con el que se buscó el punto, si vino de «Buscar dirección» (si se cambia el texto, hay que buscar otra vez). */
    private var searchedText: String? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) showPhoto(uri) else photoFile?.delete()
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        Thread {
            val file = PhotoUtil.copyToReports(this, uri)
            runOnUiThread {
                if (file == null) {
                    Toast.makeText(this, R.string.perdidos_photo_error, Toast.LENGTH_SHORT).show()
                } else {
                    photoFile = file
                    showPhoto(Uri.fromFile(file))
                }
            }
        }.start()
    }

    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { useGps() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgregarComunitarioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.photoBox.setOnClickListener { chooseSource() }
        binding.btnGps.setOnClickListener { useGps() }
        binding.btnBuscar.setOnClickListener { searchAddress() }
        binding.btnGuardar.setOnClickListener { save() }
        binding.etNombre.doAfterTextChanged { binding.tilNombre.error = null }
        binding.etDireccion.doAfterTextChanged { binding.tilDireccion.error = null }
    }

    // ---------------------------------------------------------------- Foto

    private fun chooseSource() {
        val options = arrayOf(getString(R.string.perdidos_photo_take), getString(R.string.perdidos_photo_gallery))
        styledDialog()
            .setTitle(R.string.perdidos_photo_prompt)
            .setItems(options) { _, which -> if (which == 0) launchCamera() else pickImage.launch("image/*") }
            .show()
    }

    private fun launchCamera() {
        if (packageManager.resolveActivity(android.content.Intent(MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY) == null) {
            Toast.makeText(this, R.string.perdidos_photo_error, Toast.LENGTH_SHORT).show()
            return
        }
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "comunitario_$stamp.jpg")
        photoFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingUri = uri
        takePicture.launch(uri)
    }

    private fun showPhoto(uri: Uri) {
        PhotoUtil.loadPreview(this, uri) { bitmap -> bitmap?.let { binding.ivFoto.setImageBitmap(it) } }
        binding.ivFoto.visibility = View.VISIBLE
        binding.photoPrompt.visibility = View.GONE
        detected = null
        SpeciesDetector.detectInto(this, uri, binding.tvSpecies) { detected = it }
    }

    // ---------------------------------------------------------------- Ubicación

    private fun useGps() {
        if (!LocationUtil.hasPermission(this)) {
            requestLocation.launch(LocationUtil.PERMISSIONS)
            return
        }
        binding.tvUbicacion.text = getString(R.string.comunitarios_location_searching)
        LocationUtil.getCurrent(this) { location ->
            if (location == null) {
                binding.tvUbicacion.text = getString(R.string.comunitarios_location_none)
                Toast.makeText(this, R.string.comunitarios_location_error, Toast.LENGTH_LONG).show()
                return@getCurrent
            }
            lat = location.latitude
            lon = location.longitude
            searchedText = null
            binding.tvUbicacion.text = getString(R.string.comunitarios_location_ready, GeoUtil.formatCoordinates(location.latitude, location.longitude))
            if (binding.etDireccion.text.isNullOrBlank()) {
                Thread {
                    val address = GeoUtil.reverseGeocode(location.latitude, location.longitude)
                    runOnUiThread { if (address != null && binding.etDireccion.text.isNullOrBlank()) binding.etDireccion.setText(address) }
                }.start()
            }
        }
    }

    private fun searchAddress() {
        val text = binding.etDireccion.text.toString().trim()
        if (text.isBlank()) {
            binding.tilDireccion.error = getString(R.string.comunitarios_error_address)
            return
        }
        binding.tvUbicacion.text = getString(R.string.comunitarios_location_searching)
        val query = if (text.contains("zipaquir", ignoreCase = true)) text else "$text, Zipaquirá, Cundinamarca, Colombia"
        Thread {
            val place = GeoUtil.geocode(query)
            runOnUiThread {
                if (place == null) {
                    binding.tvUbicacion.text = getString(R.string.comunitarios_location_none)
                    Toast.makeText(this, R.string.comunitarios_search_error, Toast.LENGTH_LONG).show()
                } else {
                    lat = place.lat
                    lon = place.lon
                    searchedText = text
                    binding.tvUbicacion.text = getString(R.string.comunitarios_location_ready, GeoUtil.formatCoordinates(place.lat, place.lon))
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- Guardar

    private fun save() {
        val name = binding.etNombre.text.toString().trim()
        val email = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase()
        val file = photoFile
        var valid = true
        if (file == null || binding.ivFoto.visibility != View.VISIBLE) {
            Toast.makeText(this, R.string.perdidos_photo_required, Toast.LENGTH_SHORT).show()
            valid = false
        }
        if (name.isBlank()) {
            binding.tilNombre.error = getString(R.string.comunitarios_error_name)
            valid = false
        }
        if (valid && detected == null) {
            Toast.makeText(this, R.string.comunitarios_error_species, Toast.LENGTH_LONG).show()
            valid = false
        }
        if (valid && searchedText != null && searchedText != binding.etDireccion.text.toString().trim()) {
            binding.tilDireccion.error = getString(R.string.comunitarios_error_search_again)
            valid = false
        }
        if (valid && (lat == null || lon == null)) {
            Toast.makeText(this, R.string.comunitarios_error_location, Toast.LENGTH_LONG).show()
            valid = false
        }
        if (!valid) return
        if (email.isBlank()) {
            Toast.makeText(this, R.string.chat_error_no_account, Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnGuardar.isEnabled = false
        val species = detected
        val latitude = lat!!
        val longitude = lon!!
        val path = file!!.absolutePath
        Thread {
            val photo = PhotoUtil.encodeFile(this, path)
            runOnUiThread {
                if (photo == null) {
                    binding.btnGuardar.isEnabled = true
                    Toast.makeText(this, R.string.report_photo_prepare_error, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val data = mapOf(
                    "nombre" to name,
                    "referencia" to binding.etReferencia.text.toString().trim(),
                    "direccion" to binding.etDireccion.text.toString().trim(),
                    "nota" to binding.etNota.text.toString().trim(),
                    "lat" to latitude,
                    "lon" to longitude,
                    "esterilizado" to binding.swEsterilizado.isChecked,
                    "vacunado" to binding.swVacunado.isChecked,
                    "activo" to true,
                    "fotoBase64" to photo,
                    "creadoPor" to email,
                    "fechaCreacion" to FieldValue.serverTimestamp(),
                ) + (species?.fields() ?: emptyMap())
                OfflineWrite.run(
                    this,
                    Firebase.firestore.collection("comunitarios").document().set(data),
                    onDone = { queued ->
                        Toast.makeText(this, if (queued) R.string.comunitarios_queued else R.string.comunitarios_saved, Toast.LENGTH_LONG).show()
                        finish()
                    },
                    onError = {
                        binding.btnGuardar.isEnabled = true
                        Toast.makeText(this, R.string.write_error, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }.start()
    }
}
