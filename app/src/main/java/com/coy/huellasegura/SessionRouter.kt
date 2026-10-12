package com.coy.huellasegura

import android.app.Activity
import android.content.Intent
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import java.util.Calendar

object SessionRouter {

    const val SENIOR_AGE = 65
    private const val ROL_VETERINARIO = "veterinario"
    private const val ROL_FUNCIONARIO = "funcionario"

    /**
     * Decide a qué pantalla va la persona según su rol en `roles_profesionales`.
     * Sin rol asignado se trata como ciudadano. [direct] salta la pantalla de
     * accesibilidad (se usa al reabrir la app con la sesión ya iniciada).
     */
    fun route(activity: Activity, user: FirebaseUser, direct: Boolean, onError: () -> Unit) {
        val email = user.email.orEmpty().trim().lowercase()
        if (email.isBlank()) {
            onError()
            return
        }
        val db = Firebase.firestore
        db.collection("roles_profesionales").document(email).get()
            .addOnSuccessListener { roleDoc ->
                when (roleDoc.getString("rol")) {
                    ROL_VETERINARIO -> openVeterinario(activity, email)
                    ROL_FUNCIONARIO -> openFuncionario(activity, email)
                    else -> loadCitizen(activity, email, direct, onError)
                }
            }
            .addOnFailureListener { onError() }
    }

    private fun openVeterinario(activity: Activity, email: String) {
        val ref = Firebase.firestore.collection("veterinarios").document(email)
        ref.get().addOnSuccessListener { doc ->
            if (!doc.exists()) {
                ref.set(mapOf("estado" to "pendiente", "fechaSolicitud" to FieldValue.serverTimestamp()))
            }
        }
        activity.startActivity(Intent(activity, VeterinarioActivity::class.java).apply {
            putExtra(ProfileActivity.EXTRA_USER_EMAIL, email)
        })
        activity.finish()
    }

    private fun openFuncionario(activity: Activity, email: String) {
        activity.startActivity(Intent(activity, BandejaProfesionalActivity::class.java).apply {
            putExtra(ProfileActivity.EXTRA_USER_EMAIL, email)
        })
        activity.finish()
    }

    private fun loadCitizen(activity: Activity, email: String, direct: Boolean, onError: () -> Unit) {
        Firebase.firestore.collection("usuarios").document(email).get()
            .addOnSuccessListener { doc ->
                val nombre = doc.getString("nombre").orEmpty()
                val apellido = doc.getString("apellido").orEmpty()
                val fullName = "$nombre $apellido".trim().ifBlank { email.substringBefore("@") }
                val age = doc.getLong("fechaNacimientoMillis")?.let { ageFrom(it) }
                openCitizen(activity, email, fullName, age, direct)
            }
            .addOnFailureListener { onError() }
    }

    private fun openCitizen(activity: Activity, email: String, name: String, age: Int?, direct: Boolean) {
        val prefs = activity.getSharedPreferences(AccessibilityConfigActivity.PREFS_NAME, Activity.MODE_PRIVATE)
        val intent = if (direct) {
            val simplified = prefs.getBoolean(AccessibilityConfigActivity.KEY_SIMPLIFIED, false)
            Intent(activity, if (simplified) HomeSeniorActivity::class.java else MainDashboardActivity::class.java)
        } else {
            Intent(activity, AccessibilityConfigActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_SUGGEST_SIMPLIFIED, (age ?: 0) >= SENIOR_AGE)
            }
        }
        intent.putExtra(LoginActivity.EXTRA_USER_NAME, name)
        intent.putExtra(ProfileActivity.EXTRA_USER_EMAIL, email)
        activity.startActivity(intent)
        activity.finish()
    }

    fun ageFrom(birthMillis: Long): Int {
        val birth = Calendar.getInstance().apply { timeInMillis = birthMillis }
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) age--
        return age
    }
}
