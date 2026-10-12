package com.coy.huellasegura

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val goToLogin = Runnable { navigateToLogin() }
    private var navigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val root = findViewById<android.view.View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        root.setOnClickListener {
            handler.removeCallbacks(goToLogin)
            navigateToLogin()
        }

        handler.postDelayed(goToLogin, 1800)
    }

    private fun navigateToLogin() {
        if (navigated) return
        navigated = true
        val user = Firebase.auth.currentUser
        if (user == null) {
            openLogin()
            return
        }
        SessionRouter.route(this, user, direct = true) { openLogin() }
    }

    private fun openLogin() {
        if (isFinishing) return
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(goToLogin)
        super.onDestroy()
    }
}


