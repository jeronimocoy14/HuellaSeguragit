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
    private const val ROL_CIUDADANO = "ciudadano"
    private const val CACHE_FILE = "session_cache"

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
                    ROL_VETERINARIO -> {
                        remember(activity, email, ROL_VETERINARIO)
                        openVeterinario(activity, email)
                    }
                    ROL_FUNCIONARIO -> {
                        remember(activity, email, ROL_FUNCIONARIO)
                        openFuncionario(activity, email)
                    }
                    else -> loadCitizen(activity, email, direct, onError)
                }
            }
            .addOnFailureListener { routeFromCache(activity, email, direct, onError) }
    }

    /** Guarda en el teléfono a dónde va esta cuenta, para poder abrir la app sin internet. */
    private fun remember(activity: Activity, email: String, rol: String, nombre: String = "", edad: Int? = null) {
        activity.getSharedPreferences(CACHE_FILE, Activity.MODE_PRIVATE).edit()
            .putString("rol_$email", rol)
            .putString("nombre_$email", nombre)
            .putInt("edad_$email", edad ?: -1)
            .apply()
    }

    /**
     * Sin internet no se puede leer el rol en Firestore. Si esta cuenta ya entró antes en este
     * teléfono se usa lo guardado; si no, se pide iniciar sesión.
     */
    private fun routeFromCache(activity: Activity, email: String, direct: Boolean, onError: () -> Unit) {
        val cache = activity.getSharedPreferences(CACHE_FILE, Activity.MODE_PRIVATE)
        when (cache.getString("rol_$email", null)) {
            ROL_VETERINARIO -> openVeterinario(activity, email)
            ROL_FUNCIONARIO -> openFuncionario(activity, email)
            ROL_CIUDADANO -> openCitizen(
                activity,
                email,
                cache.getString("nombre_$email", "").orEmpty().ifBlank { email.substringBefore("@") },
                cache.getInt("edad_$email", -1).takeIf { it >= 0 },
                direct
            )
            else -> onError()
        }
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
                remember(activity, email, ROL_CIUDADANO, fullName, age)
                openCitizen(activity, email, fullName, age, direct)
            }
            .addOnFailureListener { routeFromCache(activity, email, direct, onError) }
    }

    private fun openCitizen(activity: Activity, email: String, name: String, age: Int?, direct: Boolean) {
        AccessibilityPrefs.seedFromGuestIfNew(activity)
        val prefs = AccessibilityPrefs.of(activity)
        // La pantalla «Personaliza tu experiencia» se muestra solo la primera vez de cada cuenta.
        val showConfig = !direct && !prefs.getBoolean(AccessibilityConfigActivity.KEY_ONBOARDING_DONE, false)
        val intent = if (!showConfig) {
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
