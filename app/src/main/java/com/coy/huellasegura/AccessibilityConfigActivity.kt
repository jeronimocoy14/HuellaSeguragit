package com.coy.huellasegura

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityAccessibilityConfigBinding
import java.util.Locale

class AccessibilityConfigActivity : BaseAccessibleActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val PREFS_NAME = "accessibility_prefs"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val KEY_COLORBLIND_MODE = "colorblind_mode"
        const val KEY_TTS = "tts_enabled"
        const val KEY_DRAWINGS = "drawings_mode"
        const val KEY_SIMPLIFIED = "simplified_mode"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        const val EXTRA_PROFESSIONAL_MODE = "extra_professional_mode"

        private const val SCALE_NORMAL = 1.0f
        private const val SCALE_LARGE = 1.15f
        private const val SCALE_XLARGE = 1.3f
    }

    private lateinit var binding: ActivityAccessibilityConfigBinding
    private lateinit var prefs: android.content.SharedPreferences
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var userName: String = "Usuario"
    private var userEmail: String? = null
    private var isProfessionalMode: Boolean = false

    private data class Card(val container: View, val title: TextView, val subtitle: TextView, val iconWrap: View)
    private lateinit var cards: List<Card>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccessibilityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AccessibilityPrefs.of(this)
        userName = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME) ?: "Usuario"
        userEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL)
        isProfessionalMode = intent.getBooleanExtra(EXTRA_PROFESSIONAL_MODE, false)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        cards = listOf(
            Card(binding.cardTextSize, binding.tvTitleTextSize, binding.tvSubTextSize, binding.iconTextSize),
            Card(binding.cardHighContrast, binding.tvTitleHighContrast, binding.tvSubHighContrast, binding.iconHighContrast),
            Card(binding.cardColorblind, binding.tvTitleColorblind, binding.tvSubColorblind, binding.iconColorblind),
            Card(binding.cardTts, binding.tvTitleTts, binding.tvSubTts, binding.iconTts),
            Card(binding.cardDrawings, binding.tvTitleDrawings, binding.tvSubDrawings, binding.iconDrawings),
            Card(binding.cardSimplified, binding.tvTitleSimplified, binding.tvSubSimplified, binding.iconSimplified)
        )

        if (isProfessionalMode) {
            binding.cardDrawings.visibility = View.GONE
            binding.cardSimplified.visibility = View.GONE
        }

        tts = TextToSpeech(this, this)

        setupTextSizeChips()
        setupHighContrast()
        setupThemeChips()
        setupColorblindChips()
        setupTtsSwitch()
        if (!isProfessionalMode) {
            setupDrawingsSwitch()
            setupSimplifiedSwitch()
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGuardarContinuar.setOnClickListener {
            Toast.makeText(this, "Preferencias guardadas", Toast.LENGTH_SHORT).show()
            goHome()
        }
        binding.tvOmitir.setOnClickListener { goHome() }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale("es", "ES")
            if (binding.switchTts.isChecked) {
                speak(getString(R.string.accessibility_description))
            }
        }
    }

    private fun speak(text: String) {
        if (ttsReady && binding.switchTts.isChecked) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun setupTextSizeChips() {
        val savedScale = prefs.getFloat(KEY_FONT_SCALE, SCALE_LARGE)
        when (savedScale) {
            SCALE_NORMAL -> binding.chipTextNormal.isChecked = true
            SCALE_XLARGE -> binding.chipTextXLarge.isChecked = true
            else -> binding.chipTextLarge.isChecked = true
        }

        binding.chipGroupTextSize.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val scale = when (id) {
                binding.chipTextNormal.id -> SCALE_NORMAL
                binding.chipTextXLarge.id -> SCALE_XLARGE
                else -> SCALE_LARGE
            }
            if (scale != prefs.getFloat(KEY_FONT_SCALE, SCALE_LARGE)) {
                prefs.edit().putFloat(KEY_FONT_SCALE, scale).commit()
                speak("Tamaño de texto actualizado")
                recreate()
            }
        }
    }

    private fun setupHighContrast() {
        binding.switchHighContrast.isChecked = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        binding.switchHighContrast.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_HIGH_CONTRAST, isChecked).apply()
            speak(if (isChecked) "Alto contraste activado" else "Alto contraste desactivado")
            // Cambia la paleta de toda la app: esta pantalla se vuelve a crear con los colores nuevos.
            recreate()
        }
    }

    private fun setupThemeChips() {
        when (prefs.getString(AccessibilityTheme.KEY_THEME, AccessibilityTheme.THEME_AUTO)) {
            AccessibilityTheme.THEME_LIGHT -> binding.chipThemeLight.isChecked = true
            AccessibilityTheme.THEME_DARK -> binding.chipThemeDark.isChecked = true
            else -> binding.chipThemeAuto.isChecked = true
        }
        binding.chipGroupTheme.setOnCheckedStateChangeListener { _, ids ->
            val choice = when (ids.firstOrNull()) {
                R.id.chipThemeLight -> AccessibilityTheme.THEME_LIGHT
                R.id.chipThemeDark -> AccessibilityTheme.THEME_DARK
                else -> AccessibilityTheme.THEME_AUTO
            }
            if (choice == prefs.getString(AccessibilityTheme.KEY_THEME, AccessibilityTheme.THEME_AUTO)) return@setOnCheckedStateChangeListener
            prefs.edit().putString(AccessibilityTheme.KEY_THEME, choice).apply()
            // AppCompat vuelve a crear la pantalla con el modo elegido.
            AccessibilityTheme.applyNightMode(this)
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupColorblindChips() {
        val savedMode = prefs.getString(KEY_COLORBLIND_MODE, "Ninguno")
        val chip = when (savedMode) {
            "Protanopia" -> binding.chipProtanopia
            "Deuteranopia" -> binding.chipDeuteranopia
            "Tritanopia" -> binding.chipTritanopia
            else -> binding.chipNone
        }
        chip.isChecked = true

        binding.chipGroupColorblind.setOnCheckedStateChangeListener { group, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val label = group.findViewById<com.google.android.material.chip.Chip>(id).text.toString()
            if (label != prefs.getString(KEY_COLORBLIND_MODE, "Ninguno")) {
                prefs.edit().putString(KEY_COLORBLIND_MODE, label).apply()
                Toast.makeText(this, "Modo para daltonismo: $label", Toast.LENGTH_SHORT).show()
                speak("Modo para daltonismo: $label")
            }
        }
    }

    private fun setupTtsSwitch() {
        val enabled = prefs.getBoolean(KEY_TTS, true)
        binding.switchTts.isChecked = enabled
        binding.switchTts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_TTS, isChecked).apply()
            if (isChecked) {
                speak("Lectura en voz alta activada")
            } else {
                tts?.stop()
            }
        }
    }

    private fun setupDrawingsSwitch() {
        val enabled = prefs.getBoolean(KEY_DRAWINGS, false)
        binding.switchDrawings.isChecked = enabled
        binding.switchDrawings.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DRAWINGS, isChecked).apply()
            if (isChecked) {
                Toast.makeText(this, "Usaremos más íconos y audio en toda la app", Toast.LENGTH_SHORT).show()
            }
            speak(if (isChecked) "Modo con dibujos y voz activado" else "Modo con dibujos y voz desactivado")
        }
    }

    private fun setupSimplifiedSwitch() {
        val hasSavedValue = prefs.contains(KEY_SIMPLIFIED)
        val suggested = intent.getBooleanExtra(LoginActivity.EXTRA_SUGGEST_SIMPLIFIED, false)
        val enabled = if (hasSavedValue) prefs.getBoolean(KEY_SIMPLIFIED, suggested) else suggested
        binding.switchSimplified.isChecked = enabled
        prefs.edit().putBoolean(KEY_SIMPLIFIED, enabled).apply()

        binding.switchSimplified.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SIMPLIFIED, isChecked).apply()
            speak(if (isChecked) "Modo simplificado activado" else "Modo simplificado desactivado")
        }
    }

    private fun goHome() {
        if (isProfessionalMode) {
            finish()
            return
        }
        // Abierta desde el login (sin sesión): solo se guardan las preferencias y se vuelve.
        if (userEmail.isNullOrBlank()) {
            finish()
            return
        }
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
        val simplified = prefs.getBoolean(KEY_SIMPLIFIED, false)
        val destination = if (simplified) HomeSeniorActivity::class.java else MainDashboardActivity::class.java
        startActivity(Intent(this, destination).apply {
            putExtra(LoginActivity.EXTRA_USER_NAME, userName)
            putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
        })
        finish()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
