package com.example.gamehub.activities

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.gamehub.R
import com.example.gamehub.databinding.ActivitySplashBinding
import com.example.gamehub.utils.FirebaseManager

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val splashDuration = 2500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        val firebaseManager = FirebaseManager.getInstance(this)
        firebaseManager.initializeUser { success: Boolean, userId: String ->
            if (success) {
                android.util.Log.d("Splash", "User initialized: $userId")
            } else {
                android.util.Log.e("Splash", "Firebase init failed: $userId")
            }
        }

        startAnimations()

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, splashDuration)
    }

    private fun startAnimations() {
        val titleFadeIn = ObjectAnimator.ofFloat(binding.appNameText, "alpha", 0f, 1f)
        titleFadeIn.duration = 1000
        titleFadeIn.start()

        val titleSlideUp = ObjectAnimator.ofFloat(binding.appNameText, "translationY", 30f, 0f)
        titleSlideUp.duration = 1000
        titleSlideUp.start()

        Handler(Looper.getMainLooper()).postDelayed({
            val taglineFadeIn = ObjectAnimator.ofFloat(binding.taglineText, "alpha", 0f, 1f)
            taglineFadeIn.duration = 800
            taglineFadeIn.start()
        }, 300)

        Handler(Looper.getMainLooper()).postDelayed({
            val versionFadeIn = ObjectAnimator.ofFloat(binding.versionText, "alpha", 0f, 1f)
            versionFadeIn.duration = 600
            versionFadeIn.start()
        }, 600)
    }
}