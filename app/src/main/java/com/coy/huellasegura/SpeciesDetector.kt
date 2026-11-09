package com.coy.huellasegura

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

/**
 * Reconoce qué animal sale en una foto y lo ubica en una de las categorías de [Species].
 * Todo corre dentro del celular (sin internet) con dos modelos que se combinan:
 *
 * 1. ML Kit Image Labeling (modelo general de Google): perro, gato, ave, caballo...
 * 2. EfficientNet/ImageNet (assets/animales_imagenet.tflite): unas 400 especies, que
 *    assets/imagenet_tipos.txt agrupa por tipo sumando las probabilidades de cada grupo.
 *
 * Es una ayuda, no una verdad: la categoría puede fallar (poca luz, animal herido, varios animales),
 * por eso se guarda también la confianza.
 */
object SpeciesDetector {

    /**
     * [name] es la categoría de la app (p. ej. "Ave"), [item] su tarjeta y [detail] el animal concreto que vio
     * el modelo (p. ej. "Loro / Guacamaya"). Si la persona la cambió a mano, [manual] es true y [autoName] guarda
     * lo que había sugerido el modelo (si sugirió algo), para poder medir cuánto acierta.
     */
    class Result(
        val item: Species.Item,
        val name: String,
        val detail: String,
        val confidence: Float,
        val manual: Boolean = false,
        val autoName: String? = null,
    ) {
        /** Campos que se guardan junto al reporte en Firestore. */
        fun fields(): Map<String, Any> = buildMap {
            put("especie", name)
            if (manual) {
                put("especieManual", true)
                autoName?.let { put("especieAuto", it) }
            } else {
                put("especieDetalle", detail)
                put("especieConfianza", Math.round(confidence * 100) / 100.0)
            }
        }
    }

    /** Texto para mostrar bajo la foto: "Categoría detectada: Ave (Loro / Guacamaya)". */
    fun describe(context: Context, result: Result?): String = when {
        result == null -> context.getString(R.string.species_not_detected)
        result.manual -> context.getString(R.string.species_manual, result.name)
        result.detail.equals(result.name, ignoreCase = true) -> context.getString(R.string.species_detected, result.name)
        else -> context.getString(R.string.species_detected_detail, result.name, result.detail)
    }

    /**
     * Analiza la foto y va mostrando el resultado en [label] ("Reconociendo…", luego la categoría). Tocar el texto
     * deja elegir otra categoría a mano (también si no se reconoció ningún animal).
     * [onResult] recibe el resultado, ya sea el del modelo o el elegido por la persona (null si no hay ninguno).
     */
    fun detectInto(activity: android.app.Activity, uri: Uri, label: android.widget.TextView, onResult: (Result?) -> Unit) {
        label.visibility = android.view.View.VISIBLE
        label.text = activity.getString(R.string.species_detecting)
        label.setOnClickListener(null)
        label.isClickable = false
        // Si se cambia la foto mientras la anterior aún se analiza, el resultado viejo no debe pisar al nuevo.
        val ticket = Any()
        label.tag = ticket

        fun show(result: Result?) {
            val hint = activity.getString(if (result == null) R.string.species_tap_to_pick else R.string.species_tap_to_change)
            label.text = describe(activity, result) + "\n" + hint
        }

        detect(activity, uri) { result ->
            if (activity.isDestroyed || activity.isFinishing || label.tag !== ticket) return@detect
            show(result)
            onResult(result)
            label.setOnClickListener {
                val names = Species.all.map { activity.getString(it.nameRes) }.toTypedArray()
                activity.styledDialog()
                    .setTitle(R.string.species_pick_title)
                    .setItems(names) { _, index ->
                        val item = Species.all[index]
                        val name = names[index]
                        val chosen = Result(item, name, name, 1f, manual = true, autoName = result?.name)
                        show(chosen)
                        onResult(chosen)
                    }
                    .show()
            }
        }
    }

    private const val TAG = "SpeciesDetector"
    private const val ANALYSIS_SIDE = 640
    private const val MODEL = "animales_imagenet.tflite"
    private const val TYPES_FILE = "imagenet_tipos.txt"
    private const val MIN_TYPE = 0.30f
    private const val SURE_SPECIES = 0.50f
    private const val MIN_SUBTYPE = 0.30f
    private const val MIN_GENERIC = 0.60f

    private var general: ImageLabeler? = null
    private var specific: ImageLabeler? = null
    private var typeByIndex: List<String> = emptyList()

    /** Resultado de cada foto por la ruta de su archivo, para que las pantallas siguientes del reporte lo reutilicen. */
    private val byPath = HashMap<String, Result>()

    fun remember(path: String?, result: Result?) {
        if (path != null && result != null) byPath[path] = result
    }

    fun recall(path: String?): Result? = path?.let { byPath[it] }

    /**
     * Analiza la foto en segundo plano y entrega el resultado en el hilo principal:
     * null si no se reconoce ningún animal o si algo falla.
     */
    fun detect(context: Context, uri: Uri, onResult: (Result?) -> Unit) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val started = SystemClock.elapsedRealtime()
            val bitmap = runCatching { PhotoUtil.loadForAnalysis(app, uri, ANALYSIS_SIDE) }.getOrNull()
            main.post {
                if (bitmap == null) {
                    onResult(null)
                    return@post
                }
                runCatching {
                    prepare(app)
                    val image = InputImage.fromBitmap(bitmap, 0)
                    general!!.process(image)
                        .addOnSuccessListener { generalLabels ->
                            specific!!.process(image)
                                .addOnSuccessListener { specificLabels ->
                                    val result = decide(app, generalLabels, specificLabels)
                                    Log.d(TAG, "analisis ${SystemClock.elapsedRealtime() - started} ms -> ${result?.detail} ${result?.confidence}")
                                    onResult(result)
                                }
                                .addOnFailureListener { fail(it, onResult) }
                        }
                        .addOnFailureListener { fail(it, onResult) }
                }.onFailure { fail(it, onResult) }
            }
        }.start()
    }

    private fun fail(error: Throwable, onResult: (Result?) -> Unit) {
        Log.w(TAG, "No se pudo reconocer el animal: $error")
        onResult(null)
    }

    private fun prepare(context: Context) {
        if (general != null) return
        general = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.3f).build())
        specific = ImageLabeling.getClient(
            CustomImageLabelerOptions.Builder(LocalModel.Builder().setAssetFilePath(MODEL).build())
                .setConfidenceThreshold(0.01f)
                .setMaxResultCount(40)
                .build()
        )
        typeByIndex = context.assets.open(TYPES_FILE).bufferedReader().use { it.readLines() }
    }

    private fun decide(context: Context, generalLabels: List<ImageLabel>, specificLabels: List<ImageLabel>): Result? {
        val generalScore = mutableMapOf<String, Float>()
        var bestGeneric: Pair<AnimalCatalog.AnimalInfo, Float>? = null
        for (label in generalLabels) {
            val animal = AnimalCatalog.buscar(label.text) ?: continue
            if (AnimalCatalog.esGenerica(animal)) {
                if (label.confidence > (bestGeneric?.second ?: 0f)) bestGeneric = animal to label.confidence
            } else {
                generalScore[animal.clave] = maxOf(generalScore[animal.clave] ?: 0f, label.confidence)
            }
        }

        val sumByType = mutableMapOf<String, Float>()
        for (label in specificLabels) {
            val key = typeByIndex.getOrNull(label.index) ?: continue
            if (key == "-") continue
            sumByType[key] = (sumByType[key] ?: 0f) + label.confidence
        }
        sumByType.keys.toList().forEach { sumByType[it] = sumByType.getValue(it).coerceAtMost(1f) }

        val winner = pickType(generalScore, sumByType)
        val (key, confidence) = when {
            winner != null -> winner
            bestGeneric != null && bestGeneric.second >= MIN_GENERIC -> bestGeneric.first.clave to bestGeneric.second
            else -> return null
        }
        val info = AnimalCatalog.buscar(key) ?: return null
        val item = categoryFor(key)
        return Result(item, context.getString(item.nameRes), info.nombre, confidence)
    }

    /** Mismo criterio del prototipo: si el modelo de especies está seguro decide él; si no, gana la mayor confianza. */
    private fun pickType(general: Map<String, Float>, species: Map<String, Float>): Pair<String, Float>? {
        fun support(key: String): Float =
            maxOf(general[key] ?: 0f, AnimalCatalog.grupo(key)?.let { general[it] } ?: 0f)

        val bestSpecies = species.maxByOrNull { it.value }
        if (bestSpecies != null && bestSpecies.value >= SURE_SPECIES) {
            return bestSpecies.key to maxOf(bestSpecies.value, support(bestSpecies.key))
        }
        val combined = general.toMutableMap()
        species.forEach { (k, v) -> combined[k] = maxOf(combined[k] ?: 0f, v) }
        val winner = combined.maxByOrNull { it.value } ?: return null
        if (winner.value < MIN_TYPE) return null
        val subtype = species
            .filter { AnimalCatalog.grupo(it.key) == winner.key && it.value >= MIN_SUBTYPE }
            .maxByOrNull { it.value }
        return (subtype?.key ?: winner.key) to winner.value
    }

    private val categories: Map<String, Int> = buildMap {
        put("dog", R.string.pets_species_dog)
        put("cat", R.string.pets_species_cat)
        put("rabbit", R.string.pets_species_rabbit)
        listOf("bird", "parrot", "owl", "eagle", "vulture", "penguin", "flamingo", "swan", "peacock", "ostrich",
            "hummingbird", "toucan", "pelican", "pigeon", "chicken", "duck", "goose", "turkey")
            .forEach { put(it, R.string.pets_species_bird) }
        listOf("horse", "donkey").forEach { put(it, R.string.pets_species_horse) }
        put("cattle", R.string.pets_species_cow)
        put("pig", R.string.pets_species_pig)
        put("sheep", R.string.pets_species_sheep)
        put("goat", R.string.pets_species_goat)
        listOf("rodent", "hamster", "guinea pig", "mouse", "squirrel", "beaver", "marmot", "porcupine")
            .forEach { put(it, R.string.pets_species_rodent) }
        listOf("reptile", "snake", "lizard", "iguana", "chameleon", "crocodile", "turtle")
            .forEach { put(it, R.string.pets_species_reptile) }
    }

    /** Categoría de la app para el tipo de animal reconocido; lo que no encaja (león, pez, insecto...) va a "Otro". */
    private fun categoryFor(key: String): Species.Item {
        val nameRes = nameResFor(key)
        return Species.all.first { it.nameRes == nameRes }
    }

    /** Texto de la categoría de la app para una clave del reconocimiento (p. ej. «parrot» -> Ave). */
    internal fun nameResFor(key: String): Int = categories[key] ?: R.string.pets_species_other
}
