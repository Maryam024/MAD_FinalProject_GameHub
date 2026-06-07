package com.example.gamehub.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.gamehub.R
import com.example.gamehub.databinding.ActivityQuizStartBinding

class QuizStartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizStartBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        binding.startGameButton.setOnClickListener {
            val intent = Intent(this, QuizActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
}