package com.coy.huellasegura

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.MotionEvent
import android.view.View
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityReportarPeligroUbicacionBinding
import java.util.Locale

class ReportarPeligroUbicacionActivity : BaseAccessibleActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_REPORT_TYPE = "extra_report_type"
        const val EXTRA_PHOTO_PATH = "extra_photo_path"
    }

    private lateinit var binding: ActivityReportarPeligroUbicacionBinding
    private var lastLocation: Location? = null
    private var reportType: String = ""

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val locationListener = LocationListener { location -> onLocationObtained(location) }

    private val requestLocationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.any { it }) fetchLocation() else showLocationFailed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportarPeligroUbicacionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        reportType = intent.getStringExtra(EXTRA_REPORT_TYPE).orEmpty()

        binding.btnListen.visibility = if (isTtsEnabled()) View.VISIBLE else View.GONE
        if (isTtsEnabled()) tts = TextToSpeech(this, this)

        setupMapWebView()

        binding.btnHelpAccess.setOnClickListener {
            startActivity(android.content.Intent(this, AccessibilityConfigActivity::class.java))
        }

        binding.btnUseLocation.setOnClickListener { requestLocation() }

        binding.btnConfirmar.setOnClickListener { confirmPlace() }
        binding.btnListen.setOnClickListener { readScreenAloud() }
        binding.btnAtras.setOnClickListener { finish() }
    }

    private fun requestLocation() {
        if (LocationUtil.hasPermission(this)) {
            fetchLocation()
        } else {
            requestLocationPermission.launch(LocationUtil.PERMISSIONS)
        }
    }

    private fun fetchLocation() {
        // Ubicación actual; la última conocida solo se acepta si es reciente (menos de 2 minutos).
        LocationUtil.getCurrent(this) { location ->
            if (location != null) onLocationObtained(location) else showLocationFailed()
        }
    }

    private fun onLocationObtained(location: Location) {
        lastLocation = location
        showMapAt(location.latitude, location.longitude)
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupMapWebView() {
        // El atributo android:clipToOutline del XML solo lo respeta Android 12+; por código sirve desde Android 5.
        binding.mapWebView.clipToOutline = true
        binding.mapWebView.settings.javaScriptEnabled = true
        binding.mapWebView.settings.domStorageEnabled = true
        binding.mapWebView.webViewClient = WebViewClient()
        binding.mapWebView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent.requestDisallowInterceptTouchEvent(false)
            }
            v.onTouchEvent(event)
        }
    }

    private fun showMapAt(lat: Double, lon: Double) {
        val html = """
            <!doctype html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <style>html,body,#map{height:100%;margin:0;padding:0;}</style>
            </head>
            <body>
            <div id="map"></div>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <script>
              var map = L.map('map', {zoomControl:false}).setView([$lat, $lon], 16);
              L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '&copy; OpenStreetMap'
              }).addTo(map);
              L.circleMarker([$lat, $lon], {radius:10, color:'#B23B2E', weight:3, fillColor:'#B23B2E', fillOpacity:0.9}).addTo(map);
            </script>
            </body>
            </html>
        """.trimIndent()
        binding.mapWebView.loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "UTF-8", null)
        binding.mapWebView.visibility = View.VISIBLE
        binding.mapPlaceholder.visibility = View.GONE
    }

    private fun showLocationFailed() {
        Toast.makeText(this, R.string.report_location_denied, Toast.LENGTH_SHORT).show()
    }

    private fun confirmPlace() {
        val place = binding.etPlace.text.toString().trim()
        val location = lastLocation
        if (location == null && place.isBlank()) {
            Toast.makeText(this, R.string.report_missing_location, Toast.LENGTH_SHORT).show()
            return
        }
        val reference = binding.etReference.text.toString().trim()
        val intent = android.content.Intent(this, ReportarPeligroConfirmarActivity::class.java)
        intent.putExtra(ReportarPeligroConfirmarActivity.EXTRA_REPORT_TYPE, reportType)
        intent.putExtra(ReportarPeligroConfirmarActivity.EXTRA_REFERENCE, reference)
        intent.putExtra(ReportarPeligroConfirmarActivity.EXTRA_PHOTO_PATH, getIntent().getStringExtra(EXTRA_PHOTO_PATH))
        intent.putExtra(LoginActivity.EXTRA_USER_NAME, getIntent().getStringExtra(LoginActivity.EXTRA_USER_NAME))
        intent.putExtra(ProfileActivity.EXTRA_USER_EMAIL, getIntent().getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
        if (place.isNotBlank()) {
            intent.putExtra(ReportarPeligroConfirmarActivity.EXTRA_PLACE_TEXT, place)
        } else if (location != null) {
            intent.putExtra(ReportarPeligroConfirmarActivity.EXTRA_LAT, location.latitude)
            intent.putExtra(ReportarPeligroConfirmarActivity.EXTRA_LON, location.longitude)
        }
        startActivity(intent)
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) SpanishVoice.apply(tts)
    }

    private fun readScreenAloud() {
        if (!ttsReady || !isTtsEnabled()) return
        val text = "${getString(R.string.report_location_title)}. ${getString(R.string.report_use_location)}. " +
            "${getString(R.string.report_or_write_place)}. ${getString(R.string.report_reference_label)}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
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
