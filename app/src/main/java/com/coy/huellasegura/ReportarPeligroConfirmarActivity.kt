package com.coy.huellasegura

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityReportarPeligroConfirmarBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.FieldValue

class ReportarPeligroConfirmarActivity : BaseAccessibleActivity() {

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
        const val EXTRA_PLACE_TEXT = "extra_place_text"
        const val EXTRA_REPORT_TYPE = "extra_report_type"
        const val EXTRA_REFERENCE = "extra_reference"
        const val EXTRA_PHOTO_PATH = "extra_photo_path"
    }

    private lateinit var binding: ActivityReportarPeligroConfirmarBinding
    private var lat: Double = Double.NaN
    private var lon: Double = Double.NaN
    private var resolvedAddress: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportarPeligroConfirmarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val placeText = intent.getStringExtra(EXTRA_PLACE_TEXT)
        lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        lon = intent.getDoubleExtra(EXTRA_LON, Double.NaN)

        when {
            !placeText.isNullOrBlank() -> {
                resolvedAddress = placeText
                binding.tvLocationTitle.text = placeText
            }
            !lat.isNaN() && !lon.isNaN() -> {
                binding.tvLocationTitle.text = getString(R.string.report_address_loading)
                reverseGeocode(lat, lon)
            }
            else -> binding.tvLocationTitle.text = placeText.orEmpty()
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.tvEdit.setOnClickListener { finish() }

        binding.btnEnviar.setOnClickListener { sendReport() }
    }

    private fun sendReport() {
        val reportType = intent.getStringExtra(EXTRA_REPORT_TYPE).orEmpty()
        val reference = intent.getStringExtra(EXTRA_REFERENCE).orEmpty()
        val phone = binding.etPhone.text.toString().trim()
        val details = binding.etDetails.text.toString().trim()
        val photoPath = intent.getStringExtra(EXTRA_PHOTO_PATH)

        binding.btnEnviar.isEnabled = false

        // La foto se comprime y codifica fuera del hilo principal. Es opcional en este flujo.
        Thread {
            val photo = PhotoUtil.encodeFile(this, photoPath)
            runOnUiThread {
                if (!photoPath.isNullOrBlank() && photo == null) {
                    binding.btnEnviar.isEnabled = true
                    Toast.makeText(this, R.string.report_photo_prepare_error, Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                submitReport(reportType, reference, phone, details, photo)
            }
        }.start()
    }

    private fun submitReport(reportType: String, reference: String, phone: String, details: String, photo: String?) {
        val report = hashMapOf<String, Any>(
            "tipo" to reportType,
            "direccion" to resolvedAddress,
            "referencia" to reference,
            "telefono" to phone,
            "detalles" to details,
            "tieneFoto" to (photo != null),
            "flujo" to "normal",
            "estado" to "pendiente",
            "reportanteCorreo" to intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase(),
            "reportanteNombre" to intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty(),
            "fechaCreacion" to FieldValue.serverTimestamp()
        )
        if (photo != null) report["fotoBase64"] = photo
        SpeciesDetector.recall(intent.getStringExtra(EXTRA_PHOTO_PATH))?.let { report.putAll(it.fields()) }
        if (!lat.isNaN() && !lon.isNaN()) {
            report["lat"] = lat
            report["lon"] = lon
        }

        OfflineWrite.run(
            this,
            Firebase.firestore.collection("reportes").document().set(report),
            onDone = { queued ->
                startActivity(Intent(this, ReportSentActivity::class.java).apply {
                    putExtra(LoginActivity.EXTRA_USER_NAME, intent.getStringExtra(LoginActivity.EXTRA_USER_NAME))
                    putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
                    putExtra(ReportSentActivity.EXTRA_SIMPLIFIED, false)
                    putExtra(ReportSentActivity.EXTRA_QUEUED, queued)
                })
                finish()
            },
            onError = {
                binding.btnEnviar.isEnabled = true
                Toast.makeText(this, R.string.report_send_error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun reverseGeocode(lat: Double, lon: Double) {
        Thread {
            val address = GeoUtil.reverseGeocode(lat, lon)
            runOnUiThread {
                resolvedAddress = address ?: getString(R.string.report_location_obtained, lat, lon)
                binding.tvLocationTitle.text = resolvedAddress
            }
        }.start()
    }
}
