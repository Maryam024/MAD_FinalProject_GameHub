package com.example.gamehub.activities

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gamehub.databinding.ActivityNeonDashBinding
import com.example.gamehub.utils.AuthHelper
import com.example.gamehub.utils.ScoreHelper
import com.example.gamehub.utils.SharedPrefsManager
import com.example.gamehub.utils.SoundManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class NeonDashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNeonDashBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var soundManager: SoundManager
    private var bestScore = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNeonDashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefsManager = SharedPrefsManager(this)
        soundManager = SoundManager(this)
        soundManager.startBgMusic()

        setupToolbar()
        setupGameView()
        setupButtons()
        loadBestScore()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            showExitDialog()
        }
    }

    private fun setupGameView() {
        val gameView = binding.gameView
        gameView.onScoreUpdate = { score ->
            binding.scoreText.text = score.toString()
        }
        gameView.onTimeUpdate = { seconds ->
            binding.timeText.text = "${seconds}s"
        }
        gameView.onGameOver = { finalScore ->
            soundManager.playGameOver()
            soundManager.stopBgMusic()
            showGameOverDialog(finalScore)
        }

        // Connect sound callbacks
        gameView.onCollectCoin = {
            soundManager.playCorrect()
        }

        gameView.onHitObstacle = {
            soundManager.playWrong()
        }
    }

    private fun setupButtons() {
        binding.pauseButton.setOnClickListener {
            if (binding.gameView.isGameActive) {
                if (binding.gameView.isPaused) {
                    binding.gameView.resumeGame()
                    soundManager.resumeBgMusic()
                    binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    binding.gameView.pauseGame()
                    soundManager.pauseBgMusic()
                    binding.pauseButton.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }
    }

    private fun loadBestScore() {
        bestScore = sharedPrefsManager.getGameScore("neonDash")
        binding.bestScoreText.text = bestScore.toString()
    }

    private fun saveBestScore(score: Int) {
        if (score > bestScore) {
            bestScore = score
            sharedPrefsManager.updateScore("neonDash", score)
            binding.bestScoreText.text = bestScore.toString()
        }
    }

    private fun showGameOverDialog(finalScore: Int) {
        saveBestScore(finalScore)
        binding.finalScoreText.text = finalScore.toString()
        binding.gameOverCard.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val authHelper = AuthHelper(this@NeonDashActivity)
                val scoreHelper = ScoreHelper()
                val uid = authHelper.getCurrentUid()
                val playerName = authHelper.getPlayerName(uid)
                scoreHelper.saveScore(uid, playerName, "Neon Dash Runner", finalScore)
            } catch (e: Exception) {
                android.util.Log.e("NeonDash", "Failed to save score: ${e.message}")
            }
        }

        binding.playAgainButton.setOnClickListener {
            binding.gameOverCard.visibility = View.GONE
            binding.gameView.resetGame()
            binding.gameView.isGameActive = true
            soundManager.startBgMusic()
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
        }

        binding.exitButton.setOnClickListener {
            finish()
        }
    }

    private fun showExitDialog() {
        if (!binding.gameView.isGameActive) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Exit Game?")
            .setMessage("Your progress will be lost.")
            .setPositiveButton("Exit") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (binding.gameView.isGameActive && !binding.gameView.isPaused) {
            binding.gameView.pauseGame()
            soundManager.pauseBgMusic()
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.gameView.isGameActive && binding.gameView.isPaused) {
            binding.gameView.resumeGame()
            soundManager.resumeBgMusic()
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}