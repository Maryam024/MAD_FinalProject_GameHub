package com.example.gamehub.activities

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.gamehub.R
import com.example.gamehub.databinding.ActivityWordQuestResultBinding

class WordQuestResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWordQuestResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWordQuestResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val finalScore = intent.getIntExtra("FINAL_SCORE", 0)
        val powerUpsUsed = intent.getIntExtra("POWER_UPS_USED", 0)
        val wordCount = intent.getIntExtra("WORD_COUNT", 0)

        binding.finalScoreText.text = finalScore.toString()

        val message = when {
            finalScore >= 1000 -> "🏆 LEGENDARY! You're a Word Master!"
            finalScore >= 500 -> "🌟 AMAZING! Excellent vocabulary!"
            finalScore >= 200 -> "👍 GOOD JOB! Keep practicing!"
            else -> "💪 GOOD TRY! Play again to improve!"
        }
        binding.resultMessage.text = message

        binding.statsText.text = """
            Words Solved: $wordCount
            Power-ups Used: $powerUpsUsed
            Bonus: ${if (powerUpsUsed == 0) "+200 points!" else "Keep practicing!"}
        """.trimIndent()

        // Animations
        val bounceAnim = AnimationUtils.loadAnimation(this, R.anim.bounce)
        binding.finalScoreText.startAnimation(bounceAnim)

        val fadeIn = ObjectAnimator.ofFloat(binding.resultMessage, "alpha", 0f, 1f)
        fadeIn.duration = 1000
        fadeIn.start()

        binding.playAgainButton.setOnClickListener {
            startActivity(Intent(this, WordQuestActivity::class.java))
            finish()
        }

        binding.exitButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}