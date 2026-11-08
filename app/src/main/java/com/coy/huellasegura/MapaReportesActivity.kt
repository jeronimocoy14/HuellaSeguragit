package com.coy.huellasegura

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityMapaReportesBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mapa de reportes para el funcionario: un punto por reporte (con ubicación), de color según su estado.
 * Se puede filtrar por estado, período y categoría del animal; al tocar un punto sale la tarjeta del caso con
 * «Tomar caso», el chat con quien lo reportó y cómo llegar. Los reportes sin ubicación se cuentan aparte.
 * El mapa (Leaflet + OpenStreetMap) necesita internet.
 */
class MapaReportesActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityMapaReportesBinding
    private var listener: ListenerRegistration? = null
    private var docs: List<DocumentSnapshot> = emptyList()
    private var shown: List<DocumentSnapshot> = emptyList()
    private var funcionarioEmail = ""
    private var speciesFilter: String? = null
    private var mapReady = false
    private var selectedId: String? = null
    private var fitNext = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaReportesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        funcionarioEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase()
        binding.btnBack.setOnClickListener { finish() }

        binding.chipGroupState.setOnCheckedStateChangeListener { _, _ -> filtersChanged() }
        binding.chipGroupPeriod.setOnCheckedStateChangeListener { _, _ -> filtersChanged() }
        SpeciesFilter.setup(this, binding.speciesFilter.chipGroupSpecies) { speciesFilter = it; filtersChanged() }

        if (OfflineWrite.hasInternet(this)) {
            setupMap()
        } else {
            binding.mapWebView.visibility = View.GONE
            binding.tvOffline.visibility = View.VISIBLE
        }

        listener = Firebase.firestore.collection("reportes").addSnapshotListener { snapshot, _ ->
            docs = snapshot?.documents.orEmpty()
            render()
        }
    }

    // ---------------------------------------------------------------- Mapa

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMap() {
        val web = binding.mapWebView
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(MapBridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                mapReady = true
                pushMarkers()
            }
        }
        val html = """
            <!doctype html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <style>html,body,#map{height:100%;margin:0;padding:0;}</style>
            </head>
            <body>
            <div id="map"></div>
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <script>
              var map = L.map('map', {zoomControl:true}).setView([5.0221, -74.0058], 14);
              L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 19, attribution: '&copy; OpenStreetMap'}).addTo(map);
              var layer = L.layerGroup().addTo(map);
              map.on('click', function() { Android.onMapClick(); });
              function setMarkers(list, fit) {
                layer.clearLayers();
                map.invalidateSize();
                list.forEach(function(m) {
                  var mk = L.circleMarker([m.lat, m.lon], {radius: m.sel ? 15 : 11, color: m.sel ? '#000' : '#fff', weight: m.sel ? 3 : 2, fillColor: m.color, fillOpacity: 0.95}).addTo(layer);
                  mk.on('click', function(e) { L.DomEvent.stopPropagation(e); Android.onMarker(m.id); });
                  if (m.sel) { map.panTo([m.lat, m.lon]); }
                });
                if (fit && list.length) {
                  map.fitBounds(L.latLngBounds(list.map(function(m) { return [m.lat, m.lon]; })), {padding: [48, 48], maxZoom: 16});
                }
              }
            </script>
            </body>
            </html>
        """.trimIndent()
        web.loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "UTF-8", null)
    }

    /** Puente entre el mapa y la app: solo avisa qué punto se tocó. */
    private inner class MapBridge {
        @JavascriptInterface
        fun onMarker(id: String) {
            runOnUiThread { selectedId = id; showCard(); pushMarkers() }
        }

        @JavascriptInterface
        fun onMapClick() {
            runOnUiThread { selectedId = null; showCard(); pushMarkers() }
        }
    }

    private fun pushMarkers() {
        if (!mapReady) return
        val array = JSONArray()
        shown.forEach { doc ->
            array.put(
                JSONObject()
                    .put("id", doc.id)
                    .put("lat", doc.getDouble("lat"))
                    .put("lon", doc.getDouble("lon"))
                    .put("color", String.format("#%06X", 0xFFFFFF and StatusStyle.markerColor(this, kindOf(doc))))
                    .put("sel", doc.id == selectedId)
            )
        }
        val fit = fitNext
        fitNext = false
        binding.mapWebView.evaluateJavascript("if (typeof setMarkers === 'function') { setMarkers($array, $fit); }", null)
    }

    // ---------------------------------------------------------------- Filtros y lista

    private fun kindOf(doc: DocumentSnapshot) = when (doc.getString("estado")) {
        "resuelto" -> StatusStyle.Kind.OK
        "en_atencion" -> StatusStyle.Kind.WAITING
        else -> StatusStyle.Kind.DANGER
    }

    private fun stateFilter(): String? = when (binding.chipGroupState.checkedChipId) {
        R.id.chipStateWaiting -> "pendiente"
        R.id.chipStateAttending -> "en_atencion"
        R.id.chipStateResolved -> "resuelto"
        else -> null
    }

    private fun filtersChanged() {
        fitNext = true
        render()
    }

    private fun render() {
        val day = 24L * 60 * 60 * 1000
        val since = when (binding.chipGroupPeriod.checkedChipId) {
            R.id.chipPeriod7 -> System.currentTimeMillis() - 7 * day
            R.id.chipPeriod30 -> System.currentTimeMillis() - 30 * day
            else -> null
        }
        val state = stateFilter()
        val filtered = docs.filter { doc ->
            (state == null || doc.getString("estado") == state) &&
                (since == null || doc.createdAtMillis() >= since) &&
                SpeciesFilter.matches(doc, speciesFilter)
        }
        shown = filtered.filter { it.getDouble("lat") != null && it.getDouble("lon") != null }
        val without = filtered.size - shown.size
        binding.tvInfo.text = if (without > 0) getString(R.string.map_info_with_missing, shown.size, without)
        else getString(R.string.map_info, shown.size)
        if (selectedId != null && shown.none { it.id == selectedId }) selectedId = null
        showCard()
        pushMarkers()
    }

    // ---------------------------------------------------------------- Tarjeta del caso

    private fun showCard() {
        val doc = shown.firstOrNull { it.id == selectedId }
        if (doc == null) {
            binding.cardReport.visibility = View.GONE
            return
        }
        binding.cardReport.visibility = View.VISIBLE
        val estado = doc.getString("estado").orEmpty()
        binding.tvCardTitle.text = ReportStats.typeLabel(this, doc)
        binding.tvCardState.text = getString(
            when (estado) {
                "resuelto" -> R.string.export_state_resolved
                "en_atencion" -> R.string.export_state_attending
                else -> R.string.export_state_waiting
            }
        )
        StatusStyle.apply(binding.tvCardState, kindOf(doc))

        val species = doc.getString("especie").orEmpty()
        val time = ReportStats.createdAt(doc)?.let { CaseCard.relativeTime(this, it) }.orEmpty()
        val reporter = doc.getString("reportanteNombre").orEmpty()
        binding.tvCardDetail.text = listOf(species, time, reporter).filter { it.isNotBlank() }.joinToString(" · ")
        binding.tvCardPlace.text = doc.getString("direccion").orEmpty().ifBlank { doc.getString("referencia").orEmpty() }

        binding.btnTake.visibility = if (estado == "pendiente") View.VISIBLE else View.GONE
        binding.btnTake.setOnClickListener { takeCase(doc) }

        val reporterEmail = doc.getString("reportanteCorreo").orEmpty()
        binding.btnChat.visibility = if (reporterEmail.isBlank()) View.GONE else View.VISIBLE
        binding.btnChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, reporterEmail)
                putExtra(ChatActivity.EXTRA_ROLE, ChatActivity.ROLE_FUNCIONARIO)
                putExtra(ChatActivity.EXTRA_PEER_NAME, reporter)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, funcionarioEmail)
            })
        }

        val lat = doc.getDouble("lat")!!
        val lon = doc.getDouble("lon")!!
        binding.btnRoute.setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GeoUtil.mapsUrl(lat, lon)))) }
                .onFailure { Toast.makeText(this, R.string.report_send_error, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun takeCase(doc: DocumentSnapshot) {
        OfflineWrite.notify(
            this,
            doc.reference.update(
                mapOf(
                    "estado" to "en_atencion",
                    "atendidoPor" to funcionarioEmail,
                    "fechaAtencion" to FieldValue.serverTimestamp(),
                )
            ),
            R.string.professional_case_taken_queued
        ) {
            Toast.makeText(this, R.string.professional_case_taken, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        listener?.remove()
        binding.mapWebView.destroy()
        super.onDestroy()
    }
}
