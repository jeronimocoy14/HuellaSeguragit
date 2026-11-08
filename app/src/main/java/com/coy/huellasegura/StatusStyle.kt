package com.coy.huellasegura

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

/**
 * Estilo de las etiquetas de estado («Resuelto», «En revisión», «Rechazado»...).
 *
 * Con el modo para daltonismo la etiqueta cambia de colores a unos que se distinguen aunque no se vea el rojo o el
 * verde, y siempre lleva un icono con forma distinta (visto, reloj, equis) para que el estado no dependa del color.
 * El icono también aparece con el «modo con dibujos y voz».
 */
object StatusStyle {

    enum class Kind { OK, WAITING, DANGER }

    private class Palette(val bg: Int, val fg: Int)

    fun colorblindMode(context: Context): String =
        AccessibilityPrefs.of(context).getString(AccessibilityConfigActivity.KEY_COLORBLIND_MODE, "Ninguno") ?: "Ninguno"

    fun drawingsMode(context: Context): Boolean =
        AccessibilityPrefs.of(context).getBoolean(AccessibilityConfigActivity.KEY_DRAWINGS, false)

    private fun showIcon(context: Context) = colorblindMode(context) != "Ninguno" || drawingsMode(context)

    /** Colores normales (siguen el tema claro/oscuro/alto contraste) o los del modo para daltonismo. */
    private fun palette(context: Context, kind: Kind): Palette {
        fun c(res: Int) = ContextCompat.getColor(context, res)
        return when (colorblindMode(context)) {
            // Rojo-verde: azul / amarillo / bermellón (se distinguen por tono y por claridad)
            "Protanopia", "Deuteranopia" -> when (kind) {
                Kind.OK -> Palette(Color.parseColor("#0072B2"), Color.WHITE)
                Kind.WAITING -> Palette(Color.parseColor("#F0E442"), Color.parseColor("#1F2A24"))
                Kind.DANGER -> Palette(Color.parseColor("#D55E00"), Color.WHITE)
            }
            // Azul-amarillo: verde azulado / rosa / bermellón
            "Tritanopia" -> when (kind) {
                Kind.OK -> Palette(Color.parseColor("#009E73"), Color.WHITE)
                Kind.WAITING -> Palette(Color.parseColor("#CC79A7"), Color.parseColor("#1F2A24"))
                Kind.DANGER -> Palette(Color.parseColor("#D55E00"), Color.WHITE)
            }
            else -> when (kind) {
                Kind.OK -> Palette(c(R.color.accent_soft), c(R.color.brand_dark))
                Kind.WAITING -> Palette(c(R.color.offline_notice_bg), c(R.color.offline_notice_text))
                Kind.DANGER -> Palette(c(R.color.danger_red_soft), c(R.color.danger_red_dark_text))
            }
        }
    }

    private fun iconFor(kind: Kind) = when (kind) {
        Kind.OK -> R.drawable.ic_status_ok
        Kind.WAITING -> R.drawable.ic_status_wait
        Kind.DANGER -> R.drawable.ic_status_danger
    }

    /** Pinta [view] como etiqueta de estado. */
    fun apply(view: TextView, kind: Kind) {
        val context = view.context
        val palette = palette(context, kind)
        view.background = GradientDrawable().apply {
            cornerRadius = 20 * context.resources.displayMetrics.density
            setColor(palette.bg)
        }
        view.setTextColor(palette.fg)
        if (showIcon(context)) {
            val icon = ContextCompat.getDrawable(context, iconFor(kind))!!.mutate()
            DrawableCompat.setTint(icon, palette.fg)
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            view.compoundDrawablePadding = (6 * context.resources.displayMetrics.density).toInt()
        } else {
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        }
    }
}
