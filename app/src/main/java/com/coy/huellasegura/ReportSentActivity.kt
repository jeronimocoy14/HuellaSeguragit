package com.coy.huellasegura

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityReportSentBinding

class ReportSentActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SIMPLIFIED = "extra_simplified"
    }

    private lateinit var binding: ActivityReportSentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportSentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val simplified = intent.getBooleanExtra(EXTRA_SIMPLIFIED, false)
        val userName = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME)
        val userEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL)
        val destination = if (simplified) HomeSeniorActivity::class.java else MainDashboardActivity::class.java

        val goHome = {
            startActivity(Intent(this, destination).apply {
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }

        binding.btnAceptar.setOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(this) { goHome() }
    }
}
