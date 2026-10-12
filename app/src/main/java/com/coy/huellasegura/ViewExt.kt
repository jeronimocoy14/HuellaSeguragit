package com.coy.huellasegura

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat

private val easeOut = DecelerateInterpolator(2.2f)

@SuppressLint("ClickableViewAccessibility")
fun View.enablePressScale(pressedScale: Float = 0.98f) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(pressedScale).scaleY(pressedScale)
                    .setDuration(120).setInterpolator(easeOut).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f)
                    .setDuration(160).setInterpolator(easeOut).start()
        }
        false
    }
}

fun View.fadeSlideIn(index: Int, distancePx: Float) {
    alpha = 0f
    translationY = distancePx
    animate().alpha(1f).translationY(0f)
        .setStartDelay(index * 45L).setDuration(240).setInterpolator(easeOut).start()
}

fun TextView.setStartIcon(iconRes: Int, sizeDp: Int, tintRes: Int? = null) {
    val px = (sizeDp * resources.displayMetrics.density).toInt()
    val icon = ContextCompat.getDrawable(context, iconRes)?.mutate() ?: return
    icon.setBounds(0, 0, px, px)
    setCompoundDrawablesRelative(icon, null, null, null)
    TextViewCompat.setCompoundDrawableTintList(
        this,
        tintRes?.let { ColorStateList.valueOf(ContextCompat.getColor(context, it)) }
    )
}

fun android.content.Context.styledDialog() =
    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        .setBackground(ContextCompat.getDrawable(this, R.drawable.bg_dialog))

fun com.google.firebase.firestore.DocumentSnapshot.createdAtMillis(): Long =
    getTimestamp("fechaCreacion", com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?.toDate()?.time ?: Long.MAX_VALUE

/** «13» -> «13 años», «1» -> «1 año»; si ya trae texto (p. ej. «6 meses») se deja igual. */
fun String.edadConUnidad(): String {
    val limpio = trim()
    val n = limpio.toIntOrNull() ?: return limpio
    return if (n == 1) "1 año" else "$n años"
}

/** Mensaje de estado (cargando / error) dentro de una lista, para que nunca quede en blanco. */
fun android.widget.LinearLayout.showStatusMessage(text: String) {
    removeAllViews()
    addView(TextView(context).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        textSize = 14f
        val pad = (12 * resources.displayMetrics.density).toInt()
        setPadding(0, pad, 0, pad)
    })
}
