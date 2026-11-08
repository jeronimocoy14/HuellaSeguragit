package com.coy.huellasegura

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityReportarPeligroSimpleBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportarPeligroSimpleActivity : BaseAccessibleActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityReportarPeligroSimpleBinding
    private var pendingPhotoFile: File? = null
    private var pendingPhotoUri: Uri? = null
    private var hasPhoto = false
    private var detected: SpeciesDetector.Result? = null
    private var usingCurrentLocation = true
    private var lastLocation: Location? = null
    private var selectedType = ""

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private data class TypeOption(val container: LinearLayout, val icon: android.widget.ImageView, val label: TextView, val iconRes: Int)
    private lateinit var typeOptions: List<TypeOption>

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingPhotoUri != null) {
            showPhoto(pendingPhotoUri!!)
        } else {
            pendingPhotoFile?.delete()
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        // La foto de la galería se copia a un archivo propio para que siga el mismo camino que la de la cámara.
        Thread {
            val file = PhotoUtil.copyToReports(this, uri)
            runOnUiThread {
                if (file == null) {
                    Toast.makeText(this, R.string.report_photo_error, Toast.LENGTH_SHORT).show()
                } else {
                    pendingPhotoFile = file
                    pendingPhotoUri = Uri.fromFile(file)
                    showPhoto(pendingPhotoUri!!)
                }
            }
        }.start()
    }

    private fun showPhoto(uri: Uri) {
        PhotoUtil.loadPreview(this, uri) { bitmap -> bitmap?.let { binding.photoPreview.setImageBitmap(it) } }
        binding.photoPreview.visibility = View.VISIBLE
        binding.photoPrompt.visibility = View.GONE
        hasPhoto = true
        detected = null
        SpeciesDetector.detectInto(this, uri, binding.tvSpecies) { detected = it }
        updateSendButtonState()
        Toast.makeText(this, R.string.report_photo_saved, Toast.LENGTH_SHORT).show()
    }

    private fun chooseSource() {
        val options = arrayOf(getString(R.string.perdidos_photo_take), getString(R.string.perdidos_photo_gallery))
        styledDialog()
            .setTitle(R.string.report_photo_source_title)
            .setItems(options) { _, which -> if (which == 0) launchCamera() else pickImage.launch("image/*") }
            .show()
    }

    private val requestLocationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) fetchLocation() else showLocationFailed()
    }

    private val locationListener = LocationListener { location ->
        onLocationObtained(location)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportarPeligroSimpleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnListen.visibility = if (isTtsEnabled()) View.VISIBLE else View.GONE
        if (isTtsEnabled()) tts = TextToSpeech(this, this)

        typeOptions = listOf(
            TypeOption(binding.optHurt, binding.iconHurt, binding.labelHurt, R.drawable.ic_heart_outline),
            TypeOption(binding.optAbuse, binding.iconAbuse, binding.labelAbuse, R.drawable.ic_octagon_cross),
            TypeOption(binding.optHit, binding.iconHit, binding.labelHit, R.drawable.ic_car),
            TypeOption(binding.optOther, binding.iconOther, binding.labelOther, R.drawable.ic_question)
        )
        typeOptions.forEach { option ->
            option.container.setOnClickListener { selectType(option) }
        }
        selectType(typeOptions[0])

        binding.btnBack.setOnClickListener { finish() }

        binding.photoPrompt.setOnClickListener { chooseSource() }
        binding.photoPreview.setOnClickListener { chooseSource() }

        binding.locCurrent.setOnClickListener { selectCurrentLocation() }
        binding.locAddress.setOnClickListener { selectAddressLocation() }

        binding.btnSend.setOnClickListener {
            if (hasPhoto) sendReport()
        }

        binding.btnListen.setOnClickListener { readScreenAloud() }

        requestLocationPermission.launch(LocationUtil.PERMISSIONS)
        updateSendButtonState()
    }

    private fun selectType(selected: TypeOption) {
        typeOptions.forEach { option ->
            val active = option === selected
            option.container.setBackgroundResource(if (active) R.drawable.bg_option_card_active else R.drawable.bg_option_card)
            val color = ContextCompat.getColor(this, if (active) R.color.danger_red else R.color.text_primary)
            option.label.setTextColor(color)
            option.icon.setImageResource(option.iconRes)
            option.icon.setColorFilter(if (active) ContextCompat.getColor(this, R.color.danger_red) else ContextCompat.getColor(this, R.color.brand_dark))
        }
        selectedType = selected.label.text.toString()
    }

    private fun launchCamera() {
        if (packageManager.resolveActivity(android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY) == null) {
            Toast.makeText(this, R.string.report_photo_error, Toast.LENGTH_SHORT).show()
            return
        }
        val reportsDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "reports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(reportsDir, "reporte_$timestamp.jpg")
        pendingPhotoFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingPhotoUri = uri
        takePicture.launch(uri)
    }

    private fun updateSendButtonState() {
        binding.btnSend.setBackgroundResource(if (hasPhoto) R.drawable.bg_alert_card else R.drawable.bg_button_disabled)
        binding.tvSendLabel.setTextColor(ContextCompat.getColor(this, if (hasPhoto) R.color.on_fill else R.color.disabled_btn_text))
        binding.iconSendLock.visibility = if (hasPhoto) View.GONE else View.VISIBLE
        binding.tvSendWarning.visibility = if (hasPhoto) View.GONE else View.VISIBLE
    }

    private fun selectCurrentLocation() {
        usingCurrentLocation = true
        binding.locCurrent.setBackgroundResource(R.drawable.bg_location_option_active)
        binding.locAddress.setBackgroundResource(R.drawable.bg_location_option)
        binding.etAddress.visibility = View.GONE
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etAddress.windowToken, 0)
        if (lastLocation != null) showLocationSent() else fetchLocation()
    }

    private fun selectAddressLocation() {
        usingCurrentLocation = false
        binding.locAddress.setBackgroundResource(R.drawable.bg_location_option_active)
        binding.locCurrent.setBackgroundResource(R.drawable.bg_location_option)
        binding.iconLocationCheck.visibility = View.GONE
        binding.etAddress.visibility = View.VISIBLE

        binding.etAddress.post {
            binding.scrollContent.smoothScrollTo(0, binding.etAddress.bottom)
            binding.etAddress.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etAddress, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun fetchLocation() {
        if (!LocationUtil.hasPermission(this)) {
            showLocationFailed()
            return
        }
        binding.tvLocationStatus.text = getString(R.string.report_simple_location_getting)
        // Ubicación actual; la última conocida solo se acepta si es reciente (menos de 2 minutos).
        LocationUtil.getCurrent(this) { location ->
            if (location != null) onLocationObtained(location) else showLocationFailed()
        }
    }

    private fun onLocationObtained(location: Location) {
        lastLocation = location
        if (usingCurrentLocation) showLocationSent()
    }

    private fun showLocationSent() {
        binding.tvLocationStatus.text = getString(R.string.report_simple_location_sent)
        binding.tvLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.location_sent_text))
        binding.iconLocationCheck.visibility = View.VISIBLE
    }

    private fun showLocationFailed() {
        binding.tvLocationStatus.text = getString(R.string.report_simple_location_failed)
        binding.tvLocationStatus.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
        binding.iconLocationCheck.visibility = View.GONE
    }

    private fun sendReport() {
        binding.btnSend.isEnabled = false

        val photoPath = pendingPhotoFile?.absolutePath
        val location = lastLocation
        val typedAddress = if (!usingCurrentLocation) binding.etAddress.text.toString().trim() else ""

        // La foto y la dirección se resuelven fuera del hilo principal.
        Thread {
            val photo = PhotoUtil.encodeFile(this, photoPath)
            val address = when {
                typedAddress.isNotBlank() -> typedAddress
                usingCurrentLocation && location != null -> GeoUtil.reverseGeocode(location.latitude, location.longitude).orEmpty()
                else -> ""
            }
            runOnUiThread {
                if (photo == null) {
                    binding.btnSend.isEnabled = true
                    Toast.makeText(this, R.string.report_photo_prepare_error, Toast.LENGTH_LONG).show()
                } else {
                    submitReport(photo, address, location)
                }
            }
        }.start()
    }

    private fun submitReport(photo: String, address: String, location: Location?) {
        val report = hashMapOf<String, Any>(
            "tipo" to selectedType,
            "direccion" to address,
            "telefono" to "",
            "detalles" to "",
            "tieneFoto" to true,
            "fotoBase64" to photo,
            "flujo" to "simplificado",
            "estado" to "pendiente",
            "reportanteCorreo" to intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase(),
            "reportanteNombre" to intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty(),
            "fechaCreacion" to FieldValue.serverTimestamp()
        )
        detected?.let { report.putAll(it.fields()) }
        if (usingCurrentLocation && location != null) {
            report["lat"] = location.latitude
            report["lon"] = location.longitude
        }

        OfflineWrite.run(
            this,
            Firebase.firestore.collection("reportes").document().set(report),
            onDone = { queued ->
                speak(getString(if (queued) R.string.report_queued_title else R.string.report_simple_sent_confirmation))
                startActivity(Intent(this, ReportSentActivity::class.java).apply {
                    putExtra(LoginActivity.EXTRA_USER_NAME, intent.getStringExtra(LoginActivity.EXTRA_USER_NAME))
                    putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
                    putExtra(ReportSentActivity.EXTRA_SIMPLIFIED, true)
                    putExtra(ReportSentActivity.EXTRA_QUEUED, queued)
                })
                finish()
            },
            onError = {
                binding.btnSend.isEnabled = true
                Toast.makeText(this, R.string.report_send_error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) SpanishVoice.apply(tts)
    }

    private fun speak(text: String) {
        if (ttsReady && isTtsEnabled()) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun readScreenAloud() {
        val text = "${getString(R.string.report_simple_title).replace("\n", " ")}. " +
            "${getString(R.string.report_simple_attach_label)}, ${getString(R.string.report_simple_attach_required)}. " +
            getString(R.string.report_simple_location_label)
        speak(text)
    }

    override fun onDestroy() {
        runCatching {
            (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(locationListener)
        }
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
