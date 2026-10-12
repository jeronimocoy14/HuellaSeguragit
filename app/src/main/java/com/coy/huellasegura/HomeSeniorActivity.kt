package com.coy.huellasegura

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityHomeSeniorBinding
import java.util.Locale

class HomeSeniorActivity : BaseAccessibleActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityHomeSeniorBinding
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingReadAloud = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeSeniorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val userName = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME) ?: "Usuario"
        val userEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL)
        binding.tvHello.text = getString(R.string.senior_hello, userName)

        binding.btnReadAloud.visibility = if (isTtsEnabled()) android.view.View.VISIBLE else android.view.View.GONE
        if (isTtsEnabled()) tts = TextToSpeech(this, this)

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(this, AccessibilityConfigActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, intent.getStringExtra(LoginActivity.EXTRA_USER_NAME))
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
            })
        }

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_IS_PROFESSIONAL, false)
            })
        }

        binding.btnReadAloud.setOnClickListener { readScreenAloud() }

        binding.btnReport.setOnClickListener {
            startActivity(Intent(this, ReportarPeligroSimpleActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
            })
        }

        binding.btnMyPets.setOnClickListener {
            startActivity(Intent(this, MisMascotasActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
            })
        }

        binding.btnChatVet.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, userEmail)
                putExtra(ChatActivity.EXTRA_ROLE, ChatActivity.ROLE_CIUDADANO)
                putExtra(ChatActivity.EXTRA_MY_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
            })
        }

        binding.btnMyReports.setOnClickListener {
            startActivity(Intent(this, MisReportesActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
            })
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale("es", "ES")
            if (pendingReadAloud) {
                pendingReadAloud = false
                readScreenAloud()
            }
        }
    }

    private fun readScreenAloud() {
        if (!isTtsEnabled()) return
        if (!ttsReady) {
            pendingReadAloud = true
            return
        }
        val text = "${binding.tvHello.text}. ${getString(R.string.senior_mode_active)}. " +
            "${getString(R.string.senior_report_title).replace("\n", " ")}. " +
            "${getString(R.string.senior_chat_vet_title)}. ${getString(R.string.senior_my_reports_title)}. " +
            getString(R.string.senior_my_pets_title)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
