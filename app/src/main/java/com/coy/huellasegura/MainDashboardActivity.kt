package com.coy.huellasegura

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import com.google.firebase.firestore.firestore
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityMainDashboardBinding
import java.util.Locale

class MainDashboardActivity : BaseAccessibleActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainDashboardBinding
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var reportsListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val userName = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME) ?: "Usuario"
        val userEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL)
        binding.tvUserName.text = userName

        if (isHighContrastEnabled()) applyHighContrast()

        binding.btnListen.visibility = if (isTtsEnabled()) View.VISIBLE else View.GONE
        if (isTtsEnabled()) tts = TextToSpeech(this, this)
        binding.btnListen.setOnClickListener { readScreenAloud() }

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(this, AccessibilityConfigActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
            })
        }

        val openReport = View.OnClickListener {
            startActivity(Intent(this, ReportarPeligroActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
            })
        }
        binding.cardReport.setOnClickListener(openReport)
        binding.btnFab.setOnClickListener(openReport)

        binding.tabProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_IS_PROFESSIONAL, false)
            })
        }

        binding.cardMyPets.setOnClickListener {
            startActivity(Intent(this, MisMascotasActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
            })
        }

        val openChat = View.OnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, userEmail)
                putExtra(ChatActivity.EXTRA_ROLE, ChatActivity.ROLE_CIUDADANO)
                putExtra(ChatActivity.EXTRA_MY_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
            })
        }
        binding.cardChatVet.setOnClickListener(openChat)
        binding.tabChat.setOnClickListener(openChat)

        binding.cardLostFound.setOnClickListener {
            startActivity(Intent(this, PerdidosActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
            })
        }

        val openMyReports = View.OnClickListener {
            startActivity(Intent(this, MisReportesActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
            })
        }
        binding.cardMyReports.setOnClickListener(openMyReports)
        binding.tabReports.setOnClickListener(openMyReports)
        listenMyReportsCount(userEmail)
    }

    private fun listenMyReportsCount(email: String?) {
        val clean = email.orEmpty().trim().lowercase()
        if (clean.isBlank()) {
            binding.tvMyReportsSubtitle.setText(R.string.reports_count_none)
            return
        }
        reportsListener = com.google.firebase.Firebase.firestore.collection("reportes")
            .whereEqualTo("reportanteCorreo", clean)
            .addSnapshotListener { snapshot, _ ->
                val active = snapshot?.documents.orEmpty().count { it.getString("estado") != "resuelto" }
                binding.tvMyReportsSubtitle.text =
                    if (active == 0) getString(R.string.reports_count_none) else getString(R.string.reports_count, active)
            }
    }

    private fun applyHighContrast() {
        val bg = ContextCompat.getColor(this, R.color.high_contrast_bg)
        val text = ContextCompat.getColor(this, R.color.high_contrast_text)
        val accent = ContextCompat.getColor(this, R.color.high_contrast_accent)

        binding.root.setBackgroundColor(bg)
        binding.tvUserName.setTextColor(text)

        listOf(binding.cardLostFound, binding.cardChatVet.getChildAt(0) as View, binding.cardMyReports, binding.cardMyPets)
            .forEach { card ->
                (card.background?.mutate() as? GradientDrawable)?.apply {
                    setColor(bg)
                    setStroke(dp(1.5f), accent)
                }
                forEachTextView(card) { it.setTextColor(text) }
            }
    }

    private fun forEachTextView(view: View, action: (android.widget.TextView) -> Unit) {
        if (view is android.widget.TextView) action(view)
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) forEachTextView(view.getChildAt(i), action)
        }
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("es", "ES")
    }

    private fun readScreenAloud() {
        if (!ttsReady || !isTtsEnabled()) return
        val text = "${getString(R.string.dashboard_hello)} ${binding.tvUserName.text}. " +
            "${getString(R.string.dashboard_report_title)}. ${getString(R.string.dashboard_lost_found_title)}. " +
            "${getString(R.string.dashboard_chat_vet_title)}. ${getString(R.string.dashboard_my_reports_title)}. " +
            getString(R.string.dashboard_my_pets_title)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        reportsListener?.remove()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
