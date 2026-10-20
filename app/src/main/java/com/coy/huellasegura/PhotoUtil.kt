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
