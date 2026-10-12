package com.coy.huellasegura

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityProfileBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class ProfileActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IS_PROFESSIONAL = "extra_is_professional"
        const val EXTRA_USER_EMAIL = "extra_user_email"
        const val EXTRA_IS_VETERINARIO = "extra_is_veterinario"
    }

    private lateinit var binding: ActivityProfileBinding
    private var isProfessional: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        isProfessional = intent.getBooleanExtra(EXTRA_IS_PROFESSIONAL, false)
        val isVeterinario = intent.getBooleanExtra(EXTRA_IS_VETERINARIO, false)
        val userName = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME)
        val userEmail = intent.getStringExtra(EXTRA_USER_EMAIL)

        binding.tvProfileName.text = when {
            isProfessional -> userEmail ?: "Profesional"
            else -> userName ?: "Usuario"
        }
        binding.tvProfileRole.text = getString(
            when {
                isVeterinario -> R.string.profile_role_veterinario
                isProfessional -> R.string.profile_role_professional
                else -> R.string.profile_role_citizen
            }
        )

        binding.btnBack.setOnClickListener { finish() }

        binding.rowAccessibility.setOnClickListener {
            startActivity(Intent(this, AccessibilityConfigActivity::class.java).apply {
                putExtra(AccessibilityConfigActivity.EXTRA_PROFESSIONAL_MODE, isProfessional)
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
            })
        }

        binding.rowLogout.setOnClickListener { logout() }
    }

    private fun logout() {
        Firebase.auth.signOut()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
