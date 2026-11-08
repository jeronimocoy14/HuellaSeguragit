package com.coy.huellasegura

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityMapaComunitariosBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mapa de los animales comunitarios para el personal: cada punto es un animal y su color dice cuánto le falta
 * (esterilizar y vacunar). Sirve para planear campañas por zona. Se puede filtrar por lo que falta y por categoría.
 * Desde la tarjeta se actualiza la salud o se abre el lugar en la app de mapas. El mapa necesita internet.
 */
class MapaComunitariosActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityMapaComunitariosBinding
    private var listener: ListenerRegistration? = null
    private var docs: List<DocumentSnapshot> = emptyList()
    private var shown: List<DocumentSnapshot> = emptyList()
    private var speciesFilter: String? = null
    private var mapReady = false
    private var selectedId: String? = null
    private var fitNext = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapaComunitariosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.chipGroupHealth.setOnCheckedStateChangeListener { _, _ -> filtersChanged() }
        SpeciesFilter.setup(this, binding.speciesFilter.chipGroupSpecies) { speciesFilter = it; filtersChanged() }
        binding.tvLegend.text = legend()

        if (OfflineWrite.hasInternet(this)) {
            setupMap()
        } else {
            binding.mapWebView.visibility = View.GONE
            binding.tvOffline.visibility = View.VISIBLE
        }

        listener = Firebase.firestore.collection("comunitarios").addSnapshotListener { snapshot, _ ->
            docs = snapshot?.documents.orEmpty().filter { it.getBoolean("activo") != false }
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
        web.loadDataWithBaseURL(LeafletPage.BASE_URL, LeafletPage.HTML, "text/html", "UTF-8", null)
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

    private fun kindOf(doc: DocumentSnapshot) = when (Comunitarios.healthOf(doc)) {
        Comunitarios.Health.COMPLETE -> StatusStyle.Kind.OK
        Comunitarios.Health.PARTIAL -> StatusStyle.Kind.WAITING
        Comunitarios.Health.NONE -> StatusStyle.Kind.DANGER
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

    /** Leyenda con un punto de cada color (los mismos del mapa, también en el modo para daltonismo). */
    private fun legend(): CharSequence {
        val text = SpannableStringBuilder()
        fun entry(kind: StatusStyle.Kind, label: Int) {
            if (text.isNotEmpty()) text.append("    ")
            val start = text.length
            text.append("●")
            text.setSpan(ForegroundColorSpan(StatusStyle.markerColor(this, kind) or Color.BLACK), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.append(" ").append(getString(label))
        }
        entry(StatusStyle.Kind.OK, R.string.mapcom_state_complete)
        entry(StatusStyle.Kind.WAITING, R.string.mapcom_state_partial)
        entry(StatusStyle.Kind.DANGER, R.string.mapcom_state_pending)
        return text
    }

    // ---------------------------------------------------------------- Filtros

    private fun filtersChanged() {
        fitNext = true
        render()
    }

    private fun matchesHealth(doc: DocumentSnapshot): Boolean = when (binding.chipGroupHealth.checkedChipId) {
        R.id.chipHealthNoSterilized -> doc.getBoolean("esterilizado") != true
        R.id.chipHealthNoVaccinated -> doc.getBoolean("vacunado") != true
        R.id.chipHealthComplete -> Comunitarios.healthOf(doc) == Comunitarios.Health.COMPLETE
        else -> true
    }

    private fun render() {
        val filtered = docs.filter { matchesHealth(it) && SpeciesFilter.matches(it, speciesFilter) }
        shown = filtered.filter { it.getDouble("lat") != null && it.getDouble("lon") != null }
        val without = filtered.size - shown.size
        binding.tvInfo.text = if (without > 0) getString(R.string.map_info_with_missing, shown.size, without)
        else getString(R.string.map_info, shown.size)
        if (selectedId != null && shown.none { it.id == selectedId }) selectedId = null
        showCard()
        pushMarkers()
    }

    // ---------------------------------------------------------------- Tarjeta del animal

    private fun showCard() {
        val doc = shown.firstOrNull { it.id == selectedId }
        if (doc == null) {
            binding.cardAnimal.visibility = View.GONE
            return
        }
        binding.cardAnimal.visibility = View.VISIBLE
        binding.tvCardTitle.text = doc.getString("nombre").orEmpty()
        binding.tvCardState.text = getString(
            when (Comunitarios.healthOf(doc)) {
                Comunitarios.Health.COMPLETE -> R.string.mapcom_state_complete
                Comunitarios.Health.PARTIAL -> R.string.mapcom_state_partial
                Comunitarios.Health.NONE -> R.string.mapcom_state_pending
            }
        )
        StatusStyle.apply(binding.tvCardState, kindOf(doc))

        val yes = getString(R.string.export_yes)
        val no = getString(R.string.comunitarios_no)
        val health = getString(
            R.string.comunitarios_health,
            if (doc.getBoolean("esterilizado") == true) yes else no,
            if (doc.getBoolean("vacunado") == true) yes else no,
        )
        binding.tvCardDetail.text = listOf(doc.getString("especie").orEmpty(), health).filter { it.isNotBlank() }.joinToString(" · ")
        binding.tvCardPlace.text = listOf(doc.getString("referencia").orEmpty(), doc.getString("direccion").orEmpty())
            .filter { it.isNotBlank() }.joinToString(" · ")

        binding.btnHealth.setOnClickListener { Comunitarios.editHealth(this, doc) }
        val lat = doc.getDouble("lat")!!
        val lon = doc.getDouble("lon")!!
        binding.btnRoute.setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GeoUtil.mapsUrl(lat, lon)))) }
                .onFailure { Toast.makeText(this, R.string.report_send_error, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onDestroy() {
        listener?.remove()
        binding.mapWebView.destroy()
        super.onDestroy()
    }
}
