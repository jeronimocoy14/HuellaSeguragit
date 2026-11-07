package com.coy.huellasegura

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/**
 * Especies que se pueden registrar. En Firestore se guarda el nombre en texto ("Perro", "Vaca"...),
 * así que las mascotas viejas ("Perro", "Gato", "Otro") siguen funcionando igual.
 * Perro y gato usan su dibujo; las demás usan un emoji (todos de Unicode 6, que Android 7 ya trae).
 */
object Species {

    class Item(@StringRes val nameRes: Int, @DrawableRes val drawableRes: Int? = null, val emoji: String? = null)

    val all: List<Item> = listOf(
        Item(R.string.pets_species_dog, drawableRes = R.drawable.ic_dog),
        Item(R.string.pets_species_cat, drawableRes = R.drawable.ic_cat),
        Item(R.string.pets_species_rabbit, emoji = "🐇"),
        Item(R.string.pets_species_bird, emoji = "🐦"),
        Item(R.string.pets_species_horse, emoji = "🐴"),
        Item(R.string.pets_species_cow, emoji = "🐄"),
        Item(R.string.pets_species_pig, emoji = "🐖"),
        Item(R.string.pets_species_sheep, emoji = "🐑"),
        Item(R.string.pets_species_goat, emoji = "🐐"),
        Item(R.string.pets_species_rodent, emoji = "🐹"),
        Item(R.string.pets_species_reptile, emoji = "🐢"),
        Item(R.string.pets_species_other, drawableRes = R.drawable.ic_paw),
    )

    /** Busca la especie por su nombre guardado; si no existe (dato raro o viejo) devuelve "Otro". */
    fun find(context: Context, name: String): Item {
        val clean = name.trim()
        return all.firstOrNull { context.getString(it.nameRes).equals(clean, ignoreCase = true) } ?: all.last()
    }

    /** Dibujo de la especie para una lista o tarjeta, de [sizeDp] de lado. */
    fun icon(context: Context, name: String, sizeDp: Int = 26): Drawable = icon(context, find(context, name), sizeDp)

    fun icon(context: Context, item: Item, sizeDp: Int = 26): Drawable {
        item.drawableRes?.let { return ContextCompat.getDrawable(context, it)!! }
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = px * 0.82f
            textAlign = Paint.Align.CENTER
        }
        val baseline = px / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(item.emoji.orEmpty(), px / 2f, baseline, paint)
        return BitmapDrawable(context.resources, bitmap)
    }
}
