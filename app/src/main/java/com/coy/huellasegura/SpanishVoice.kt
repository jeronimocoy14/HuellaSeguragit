package com.coy.huellasegura

import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Elige el español que tenga instalado el teléfono para la lectura en voz alta. Antes se forzaba español de España,
 * que muchos celulares de Latinoamérica no traen: el botón «Escuchar» quedaba mudo. Se prueba primero el de Colombia
 * y después los otros; si ninguno está, se deja la voz que traiga el teléfono.
 */
object SpanishVoice {

    private val PREFERRED = listOf(Locale("es", "CO"), Locale("es", "MX"), Locale("es", "US"), Locale("es", "ES"), Locale("es"))

    /** Devuelve true si se pudo poner una voz en español. Se llama cuando el motor de voz ya está listo. */
    fun apply(tts: TextToSpeech?): Boolean {
        if (tts == null) return false
        for (locale in PREFERRED) {
            val result = runCatching { tts.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (result >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = locale
                return true
            }
        }
        Log.w("SpanishVoice", "El teléfono no tiene una voz en español instalada")
        return false
    }
}
