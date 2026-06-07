package com.example.gamehub.activities

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.gamehub.R
import com.example.gamehub.databinding.ActivityWordQuestBinding
import com.example.gamehub.models.WordCategory
import com.example.gamehub.utils.SharedPrefsManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WordQuestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWordQuestBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private var selectedCategory: WordCategory = WordCategory.ANIMALS
    private var selectedDifficulty = "Medium"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWordQuestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefsManager = SharedPrefsManager(this)

        setupToolbar()
        setupClickListeners()
        loadHighScore()
        setupAnimations()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        binding.startGameButton.setOnClickListener {
            startGame()
        }

        binding.categorySelector.setOnClickListener {
            showCategoryDialog()
        }

        binding.difficultySelector.setOnClickListener {
            showDifficultyDialog()
        }

        binding.highScoreCard.setOnClickListener {
            showHighScoreDialog()
        }
    }

    private fun showCategoryDialog() {
        val categories = WordCategory.values().map { "${it.icon} ${it.displayName}" }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Category")
            .setItems(categories) { _, which ->
                selectedCategory = WordCategory.values()[which]
                binding.categoryText.text = "${selectedCategory.icon} ${selectedCategory.displayName}"
            }
            .show()
    }

    private fun showDifficultyDialog() {
        val difficulties = arrayOf("Easy", "Medium", "Hard")

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Difficulty")
            .setItems(difficulties) { _, which ->
                selectedDifficulty = difficulties[which]
                binding.difficultyText.text = selectedDifficulty

                when (selectedDifficulty) {
                    "Easy" -> binding.difficultyText.setTextColor(getColor(R.color.success))
                    "Medium" -> binding.difficultyText.setTextColor(getColor(R.color.warning))
                    "Hard" -> binding.difficultyText.setTextColor(getColor(R.color.accent))
                }
            }
            .show()
    }

    private fun startGame() {
        val intent = Intent(this, WordQuestGameActivity::class.java).apply {
            putExtra("CATEGORY", selectedCategory.name)
            putExtra("DIFFICULTY", selectedDifficulty)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun loadHighScore() {
        val highScore = sharedPrefsManager.getGameScore("wordquest")
        binding.highScoreText.text = highScore.toString()
    }

    private fun showHighScoreDialog() {
        val highScore = sharedPrefsManager.getGameScore("wordquest")
        MaterialAlertDialogBuilder(this)
            .setTitle("🏆 High Score")
            .setMessage("Your best score in Word Quest is: $highScore")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupAnimations() {
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        binding.startGameButton.startAnimation(fadeIn)
    }
}