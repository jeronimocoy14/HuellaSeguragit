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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ReportarPeligroConfirmarActivity : BaseAccessibleActivity() {

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
        const val EXTRA_PLACE_TEXT = "extra_place_text"
        const val EXTRA_REPORT_TYPE = "extra_report_type"
        const val EXTRA_REFERENCE = "extra_reference"
        private const val USER_AGENT = "HuellaSegura-App/1.0"
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

        binding.btnEnviar.isEnabled = false

        val report = hashMapOf(
            "tipo" to reportType,
            "direccion" to resolvedAddress,
            "referencia" to reference,
            "telefono" to phone,
            "detalles" to details,
            "tieneFoto" to true,
            "flujo" to "normal",
            "estado" to "pendiente",
            "reportanteCorreo" to intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase(),
            "reportanteNombre" to intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty(),
            "fechaCreacion" to FieldValue.serverTimestamp()
        )
        if (!lat.isNaN() && !lon.isNaN()) {
            report["lat"] = lat
            report["lon"] = lon
        }

        Firebase.firestore.collection("reportes").add(report)
            .addOnSuccessListener {
                startActivity(Intent(this, ReportSentActivity::class.java).apply {
                    putExtra(LoginActivity.EXTRA_USER_NAME, intent.getStringExtra(LoginActivity.EXTRA_USER_NAME))
                    putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
                    putExtra(ReportSentActivity.EXTRA_SIMPLIFIED, false)
                })
                finish()
            }
            .addOnFailureListener {
                binding.btnEnviar.isEnabled = true
                Toast.makeText(this, R.string.report_send_error, Toast.LENGTH_SHORT).show()
            }
    }

    private fun reverseGeocode(lat: Double, lon: Double) {
        Thread {
            val address = runCatching {
                val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&addressdetails=0")
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("display_name").ifBlank { null }
            }.getOrNull()

            runOnUiThread {
                resolvedAddress = address ?: getString(R.string.report_location_obtained, lat, lon)
                binding.tvLocationTitle.text = resolvedAddress
            }
        }.start()
    }
}
