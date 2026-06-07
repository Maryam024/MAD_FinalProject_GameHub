package com.example.gamehub.activities

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.gamehub.R
import com.example.gamehub.databinding.ActivityQuizResultBinding

class QuizResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val finalScore = intent.getIntExtra("FINAL_SCORE", 0)
        val totalQuestions = intent.getIntExtra("TOTAL_QUESTIONS", 0)
        val correctAnswers = intent.getIntExtra("CORRECT_ANSWERS", 0)
        val maxCombo = intent.getIntExtra("COMBO", 0)
        val powerUpsUsed = intent.getIntExtra("POWER_UPS_USED", 0)

        val percentage = if (totalQuestions > 0) (correctAnswers * 100 / totalQuestions) else 0

        binding.finalScoreText.text = finalScore.toString()
        binding.statsText.text = buildString {
            append("Questions Answered: $totalQuestions/$totalQuestions\n")
            append("Correct Answers: $correctAnswers\n")
            append("Accuracy: ${percentage}%\n")
            append("Max Combo: ${maxCombo}x\n")
            append("Power-ups Used: $powerUpsUsed\n")
        }

        // Animation
        binding.finalScoreText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce))

        binding.playAgainButton.setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java))
            finish()
        }

        binding.exitButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}