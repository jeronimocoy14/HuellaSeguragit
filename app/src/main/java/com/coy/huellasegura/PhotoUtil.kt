package com.coy.huellasegura

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.InputStream
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

object PhotoUtil {

    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private const val TAG = "PhotoUtil"

    fun encode(context: Context, uri: Uri, maxSide: Int = 720, quality: Int = 72): String? = try {
        val started = SystemClock.elapsedRealtime()
        val bitmap = decodeSampled(context, uri, maxSide) ?: return null
        val decodedMs = SystemClock.elapsedRealtime() - started
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        // Tiempos para medir esta operación (la más pesada de la app) en dispositivos de gama baja.
        Log.d(
            TAG,
            "foto: leer y reducir ${decodedMs} ms, total ${SystemClock.elapsedRealtime() - started} ms, " +
                "${bitmap.width}x${bitmap.height} -> ${scaled.width}x${scaled.height}, ${encoded.length / 1024} KB en Base64"
        )
        encoded
    } catch (e: Exception) {
        null
    }

    /**
     * Vista previa de una foto de la cámara. `ImageView.setImageURI` la decodifica completa en el hilo
     * principal (una foto de 12 MP ocupa unos 48 MB y puede agotar la memoria de un celular antiguo); aquí
     * se lee reducida ([maxSide] píxeles) en un hilo secundario y se entrega en el principal.
     */
    fun loadPreview(context: Context, uri: Uri, maxSide: Int = 540, onResult: (Bitmap?) -> Unit) {
        val appContext = context.applicationContext
        Thread {
            val started = SystemClock.elapsedRealtime()
            val bitmap = runCatching { decodeSampled(appContext, uri, maxSide) }.getOrNull()
            Log.d(TAG, "vista previa: ${SystemClock.elapsedRealtime() - started} ms, ${bitmap?.width}x${bitmap?.height}")
            Handler(Looper.getMainLooper()).post { onResult(bitmap) }
        }.start()
    }

    /**
     * Copia una foto elegida de la galería a un archivo propio de la app (carpeta de reportes), para que siga el mismo
     * camino que las fotos de la cámara. Bloquea: usar un hilo secundario. Devuelve null si no se pudo leer.
     */
    fun copyToReports(context: Context, uri: Uri): java.io.File? = try {
        val dir = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "reports").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val file = java.io.File(dir, "galeria_$stamp.jpg")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (copied && file.length() > 0L) file else { file.delete(); null }
    } catch (e: Exception) {
        null
    }

    /** Foto reducida (lado largo de [maxSide] a 2×[maxSide] px) y bien girada, para analizarla. Bloquea: usar un hilo secundario. */
    fun loadForAnalysis(context: Context, uri: Uri, maxSide: Int): Bitmap? = decodeSampled(context, uri, maxSide)

    /** Foto guardada en un archivo local (p. ej. la que tomó la cámara). Bloquea: usar un hilo secundario. */
    fun encodeFile(context: Context, path: String?): String? {
        if (path.isNullOrBlank()) return null
        val file = java.io.File(path)
        if (!file.isFile || file.length() == 0L) return null
        return encode(context, Uri.fromFile(file))
    }

    fun decode(base64: String?, key: String): Bitmap? {
        if (base64.isNullOrBlank()) return null
        val cacheKey = "$key:${base64.length}"
        cache.get(cacheKey)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { cache.put(cacheKey, it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > maxSide * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { exifRotation(it) } ?: 0
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun exifRotation(stream: InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
}
