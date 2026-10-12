package com.coy.huellasegura

import android.app.DatePickerDialog
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.StyleSpan
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityLoginBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import java.util.Calendar

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_SUGGEST_SIMPLIFIED = "extra_suggest_simplified"
    }

    private lateinit var binding: ActivityLoginBinding
    private var passwordVisible = false
    private var computedAge: Int? = null
    private var registerMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupPasswordToggle()
        setupBirthdate()
        setupTerms()
        applyMode()

        binding.btnIngresar.setOnClickListener { if (registerMode) attemptRegister() else attemptLogin() }
        binding.btnConfigAccesibilidad.setOnClickListener {
            startActivity(Intent(this, AccessibilityConfigActivity::class.java).apply {
                putExtra(EXTRA_USER_NAME, fullName())
            })
        }
    }

    private fun applyMode() {
        val register = registerMode
        binding.tvTitulo.setText(if (register) R.string.register_title else R.string.login_title)
        binding.tvSubtitulo.setText(if (register) R.string.register_subtitle else R.string.login_subtitle_login)
        binding.btnIngresar.setText(if (register) R.string.btn_crear_cuenta else R.string.btn_ingresar)
        binding.rowNombre.visibility = if (register) View.VISIBLE else View.GONE
        binding.blockNacimiento.visibility = if (register) View.VISIBLE else View.GONE
        binding.rowTerms.visibility = if (register) View.VISIBLE else View.GONE
        binding.etCorreo.setHint(R.string.hint_correo)
        setupModeLink()
    }

    private fun setupModeLink() {
        val full = getString(if (registerMode) R.string.has_account else R.string.no_account)
        val target = if (registerMode) "Iniciar sesión" else "Crear cuenta"
        val start = full.indexOf(target)
        val spannable = SpannableString(full)
        if (start >= 0) {
            spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, start + target.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    registerMode = !registerMode
                    applyMode()
                }
            }, start, start + target.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvCrearCuenta.text = spannable
        binding.tvCrearCuenta.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun fullName(): String {
        val nombre = binding.etNombre.text.toString().trim()
        val apellido = binding.etApellido.text.toString().trim()
        return "$nombre $apellido".trim().ifBlank { "Usuario" }
    }

    private fun setupPasswordToggle() {
        binding.btnTogglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            val cursorPosition = binding.etPassword.selectionStart
            if (passwordVisible) {
                binding.etPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnTogglePassword.setImageResource(R.drawable.ic_eye_off)
            } else {
                binding.etPassword.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnTogglePassword.setImageResource(R.drawable.ic_eye)
            }
            binding.etPassword.setSelection(cursorPosition.coerceIn(0, binding.etPassword.text.length))
        }
    }

    private fun setupBirthdate() {
        val watcher = { recomputeAge() }
        binding.etDay.addAutoAdvanceWatcher(2, binding.etMonth, watcher)
        binding.etMonth.addAutoAdvanceWatcher(2, binding.etYear, watcher)
        binding.etYear.addAutoAdvanceWatcher(4, null, watcher)

        binding.btnDatePicker.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = binding.etYear.text.toString().toIntOrNull() ?: (calendar.get(Calendar.YEAR) - 30)
            val month = (binding.etMonth.text.toString().toIntOrNull() ?: 1) - 1
            val day = binding.etDay.text.toString().toIntOrNull() ?: 1
            DatePickerDialog(this, { _, pickedYear, pickedMonth, pickedDay ->
                binding.etDay.setText(String.format("%02d", pickedDay))
                binding.etMonth.setText(String.format("%02d", pickedMonth + 1))
                binding.etYear.setText(pickedYear.toString())
                recomputeAge()
            }, year, month.coerceIn(0, 11), day.coerceIn(1, 31)).show()
        }
    }

    private fun android.widget.EditText.addAutoAdvanceWatcher(
        maxLen: Int,
        next: android.widget.EditText?,
        onChanged: () -> Unit
    ) {
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                onChanged()
                if (s?.length == maxLen) next?.requestFocus()
            }
        })
    }

    private fun recomputeAge() {
        val millis = parseBirthdateMillis()
        if (millis == null) {
            computedAge = null
            binding.seniorBanner.visibility = View.GONE
            return
        }
        val age = SessionRouter.ageFrom(millis)
        computedAge = age
        if (age >= SessionRouter.SENIOR_AGE) {
            binding.tvSeniorBanner.text = getString(R.string.senior_banner, age)
            binding.seniorBanner.visibility = View.VISIBLE
        } else {
            binding.seniorBanner.visibility = View.GONE
        }
    }

    private fun parseBirthdateMillis(): Long? {
        val day = binding.etDay.text.toString().toIntOrNull()
        val month = binding.etMonth.text.toString().toIntOrNull()
        val year = binding.etYear.text.toString().toIntOrNull()
        if (day == null || month == null || year == null || binding.etYear.text.length != 4) return null
        if (year < 1900) return null
        return try {
            val cal = Calendar.getInstance().apply {
                isLenient = false
                clear()
                set(year, month - 1, day)
            }
            val millis = cal.timeInMillis
            if (millis > System.currentTimeMillis()) null else millis
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun setupTerms() {
        val full = getString(R.string.terms_text)
        val target = "Términos y condiciones"
        val start = full.indexOf(target)
        val spannable = SpannableString(full)
        if (start >= 0) {
            spannable.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, start + target.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    Toast.makeText(this@LoginActivity, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
                }
            }, start, start + target.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvTerms.text = spannable
        binding.tvTerms.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun attemptLogin() {
        val correo = binding.etCorreo.text.toString().trim().lowercase()
        val password = binding.etPassword.text.toString()
        if (correo.isBlank() || password.isBlank()) {
            toast(R.string.error_fill_email_password)
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            toast(R.string.error_email_invalid)
            return
        }

        setBusy(true)
        Firebase.auth.signInWithEmailAndPassword(correo, password)
            .addOnSuccessListener { result -> routeAfterAuth(result.user) }
            .addOnFailureListener { error ->
                setBusy(false)
                toast(if (error is FirebaseNetworkException) R.string.error_network else R.string.error_credentials)
            }
    }

    private fun attemptRegister() {
        val nombre = binding.etNombre.text.toString().trim()
        val apellido = binding.etApellido.text.toString().trim()
        val correo = binding.etCorreo.text.toString().trim().lowercase()
        val password = binding.etPassword.text.toString()
        val birthMillis = parseBirthdateMillis()

        when {
            nombre.isBlank() || apellido.isBlank() -> return toast(R.string.error_name_required)
            !Patterns.EMAIL_ADDRESS.matcher(correo).matches() -> return toast(R.string.error_email_invalid)
            password.length < 6 -> return toast(R.string.error_password_short)
            birthMillis == null -> return toast(R.string.error_birthdate_invalid)
            !binding.cbTerms.isChecked -> return toast(R.string.error_terms)
        }

        setBusy(true)
        Firebase.auth.createUserWithEmailAndPassword(correo, password)
            .addOnSuccessListener { result ->
                val user = result.user
                val day = binding.etDay.text.toString().padStart(2, '0')
                val month = binding.etMonth.text.toString().padStart(2, '0')
                val profile = mapOf(
                    "nombre" to nombre,
                    "apellido" to apellido,
                    "correo" to correo,
                    "fechaNacimiento" to "$day/$month/${binding.etYear.text}",
                    "fechaNacimientoMillis" to birthMillis!!,
                    "fechaCreacion" to FieldValue.serverTimestamp()
                )
                Firebase.firestore.collection("usuarios").document(correo).set(profile)
                    .addOnCompleteListener { routeAfterAuth(user) }
            }
            .addOnFailureListener { error ->
                setBusy(false)
                toast(
                    when (error) {
                        is FirebaseAuthUserCollisionException -> R.string.error_account_exists
                        is FirebaseAuthWeakPasswordException -> R.string.error_password_short
                        is FirebaseNetworkException -> R.string.error_network
                        else -> R.string.error_generic_auth
                    }
                )
            }
    }

    private fun routeAfterAuth(user: FirebaseUser?) {
        if (user == null) {
            setBusy(false)
            toast(R.string.error_generic_auth)
            return
        }
        SessionRouter.route(this, user, direct = false) {
            setBusy(false)
            toast(R.string.error_account_load)
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.btnIngresar.isEnabled = !busy
        if (busy) {
            binding.btnIngresar.setText(R.string.login_checking)
        } else {
            binding.btnIngresar.setText(if (registerMode) R.string.btn_crear_cuenta else R.string.btn_ingresar)
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStart() {
        super.onStart()
        watchConnection()
    }

    override fun onStop() {
        networkCallback?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        networkCallback = null
        super.onStop()
    }

    /** El aviso «sin internet» solo se muestra cuando de verdad no hay conexión. */
    private fun watchConnection() {
        val cm = getSystemService(ConnectivityManager::class.java)
        fun hasInternet(c: NetworkCapabilities?) = c != null &&
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        fun show(offline: Boolean) = runOnUiThread {
            binding.offlineBanner.visibility = if (offline) View.VISIBLE else View.GONE
        }
        show(!hasInternet(cm.getNetworkCapabilities(cm.activeNetwork)))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                show(!hasInternet(capabilities))
            }

            override fun onLost(network: Network) {
                show(true)
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        networkCallback = callback
    }
}
