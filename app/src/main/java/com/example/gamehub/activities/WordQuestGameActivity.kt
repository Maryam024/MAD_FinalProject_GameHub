package com.example.gamehub.activities

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.gamehub.R
import com.example.gamehub.databinding.ActivityWordQuestGameBinding
import com.example.gamehub.models.WordCategory
import com.example.gamehub.utils.SharedPrefsManager
import com.example.gamehub.utils.SoundManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.EditText
import com.google.firebase.firestore.FirebaseFirestore
import com.example.gamehub.utils.AuthHelper
import com.example.gamehub.utils.ScoreHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class WordQuestGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWordQuestGameBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var soundManager: SoundManager

    // Game variables
    private var currentWord = ""
    private var guessedWord = mutableListOf<Char>()
    private val usedLetters = mutableSetOf<String>()
    private var lives = 6
    private var score = 0
    private var combo = 0
    private var powerUpsUsed = 0
    private var currentCategory = WordCategory.ANIMALS
    private var currentDifficulty = "Medium"
    private var wordsPlayed = 0

    // Power-up limits
    private var hintsRemaining = 2  // Max 2 hints per game
    private var skipsRemaining = 1   // Max 1 skip per game
    private var extraLivesRemaining = 1  // Max 1 extra life per game

    // Game state
    private var isGameActive = true
    private var isPaused = false
    private var isWaitingForNextWord = false

    // Word list
    private val wordsDatabase = mapOf(
        WordCategory.ANIMALS to listOf("ELEPHANT", "GIRAFFE", "KANGAROO", "DOLPHIN", "PENGUIN", "ZEBRA", "LION", "TIGER", "MONKEY", "PANDA"),
        WordCategory.COUNTRIES to listOf("AUSTRALIA", "CANADA", "GERMANY", "JAPAN", "BRAZIL", "INDIA", "FRANCE", "ITALY", "MEXICO", "EGYPT"),
        WordCategory.FRUITS to listOf("PINEAPPLE", "WATERMELON", "BLUEBERRY", "STRAWBERRY", "POMEGRANATE", "MANGO", "BANANA", "ORANGE", "GRAPE", "APPLE"),
        WordCategory.MOVIES to listOf("TITANIC", "AVATAR", "INCEPTION", "GLADIATOR", "FROZEN", "MATRIX", "JOKER", "INTERSTELLAR", "COCO", "UP"),
        WordCategory.SPORTS to listOf("BASKETBALL", "FOOTBALL", "BADMINTON", "CRICKET", "TENNIS", "SOCCER", "BASEBALL", "HOCKEY", "RUGBY", "GOLF"),
        WordCategory.TECHNOLOGY to listOf("COMPUTER", "SMARTPHONE", "ARTIFICIAL", "INTERNET", "ROBOTICS", "SOFTWARE", "HARDWARE", "DATABASE", "NETWORK", "ALGORITHM")
    )

    // Timer
    private var timer: CountDownTimer? = null
    private var timeLeft = 60000L
    private val timeLimit = 60000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWordQuestGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefsManager = SharedPrefsManager(this)
        soundManager = SoundManager(this)
        soundManager.startBgMusic()

        currentCategory = WordCategory.valueOf(intent.getStringExtra("CATEGORY") ?: "ANIMALS")
        currentDifficulty = intent.getStringExtra("DIFFICULTY") ?: "Medium"

        setupToolbar()
        setupGame()
        setupLetterInput()
        setupPowerUps()
        setupPauseButton()
        startTimer()

        isGameActive = true
        isPaused = false
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            showExitDialog()
        }
    }

    private fun setupGame() {
        currentWord = getRandomWord().uppercase()
        guessedWord = MutableList(currentWord.length) { '_' }
        usedLetters.clear()

        binding.categoryBadge.text = "${currentCategory.icon} ${currentCategory.displayName}"
        binding.difficultyBadge.text = currentDifficulty
        binding.wordLength.text = "Length: ${currentWord.length}"

        displayWord()
        updateUI()

        binding.letterInput.text?.clear()
        binding.letterInput.isEnabled = true
        binding.letterInput.requestFocus()
    }

    private fun getRandomWord(): String {
        val words = wordsDatabase[currentCategory] ?: wordsDatabase[WordCategory.ANIMALS]!!
        val selectedWord = words.random()
        wordsPlayed++
        return selectedWord
    }

    private fun setupLetterInput() {
        binding.letterInput.setOnEditorActionListener { _, actionId, event ->
            if (!isGameActive || isPaused || isWaitingForNextWord) return@setOnEditorActionListener false

            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {

                val input = binding.letterInput.text.toString().trim().uppercase()
                if (input.isNotEmpty()) {
                    val letter = input.substring(0, 1)
                    onLetterGuessed(letter)
                    binding.letterInput.text?.clear()
                }
                return@setOnEditorActionListener true
            }
            false
        }

        binding.letterInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isGameActive || isPaused || isWaitingForNextWord) return
                if (count == 1) {
                    val letter = s.toString().uppercase()
                    onLetterGuessed(letter)
                    binding.letterInput.text?.clear()
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun onLetterGuessed(letter: String) {
        if (!isGameActive || isPaused) return

        if (usedLetters.contains(letter)) {
            Toast.makeText(this, "You already tried '$letter'!", Toast.LENGTH_SHORT).show()
            return
        }

        usedLetters.add(letter)

        if (currentWord.contains(letter)) {
            soundManager.playCorrect()
            revealLetter(letter)

            val pointsEarned = 100 + (combo * 20)
            score += pointsEarned
            combo++

            Toast.makeText(this, "+$pointsEarned points! Combo: ${combo}x", Toast.LENGTH_SHORT).show()
            animateCorrectGuess()

            if (!guessedWord.contains('_')) {
                onWordComplete()
            }
        } else {
            soundManager.playWrong()
            lives--
            combo = 0
            updateUI()

            animateWrongGuess()
            Toast.makeText(this, "Wrong! '$letter' is not in the word. -1 life", Toast.LENGTH_SHORT).show()

            if (lives <= 0) {
                onGameOver()
            }
        }

        updateUI()
    }

    private fun revealLetter(letter: String) {
        for (i in currentWord.indices) {
            if (currentWord[i].toString() == letter) {
                guessedWord[i] = letter[0]
            }
        }
        displayWord()

        val anim = AnimationUtils.loadAnimation(this, R.anim.bounce)
        binding.wordContainer.startAnimation(anim)
    }

    private fun displayWord() {
        val displayText = guessedWord.joinToString(" ")
        binding.wordText.text = displayText
    }

    private fun setupPowerUps() {
        binding.powerUpHint.setOnClickListener {
            if (!isGameActive || isPaused) return@setOnClickListener
            useHint()
        }
        binding.powerUpSkip.setOnClickListener {
            if (!isGameActive || isPaused) return@setOnClickListener
            useSkip()
        }
        binding.powerUpLife.setOnClickListener {
            if (!isGameActive || isPaused) return@setOnClickListener
            useExtraLife()
        }
    }

    private fun setupPauseButton() {
        binding.pauseButton.setOnClickListener {
            if (!isGameActive) return@setOnClickListener

            isPaused = !isPaused
            if (isPaused) {
                timer?.cancel()
                soundManager.pauseBgMusic()
                binding.letterInput.isEnabled = false
                binding.pauseButton.setImageResource(android.R.drawable.ic_media_play)
                Toast.makeText(this, "⏸️ Game Paused", Toast.LENGTH_SHORT).show()
            } else {
                startTimer()
                soundManager.resumeBgMusic()
                binding.letterInput.isEnabled = true
                binding.letterInput.requestFocus()
                binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
                Toast.makeText(this, "▶️ Game Resumed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun useHint() {
        // Check if hints are available
        if (hintsRemaining <= 0) {
            Toast.makeText(this, "No hints remaining! You've used all ${hintsRemaining} hints.", Toast.LENGTH_SHORT).show()
            return
        }

        val unrevealedIndices = mutableListOf<Int>()
        for (i in guessedWord.indices) {
            if (guessedWord[i] == '_') {
                unrevealedIndices.add(i)
            }
        }

        if (unrevealedIndices.isEmpty()) {
            Toast.makeText(this, "No letters to reveal! The word is already complete.", Toast.LENGTH_SHORT).show()
            return
        }

        if (unrevealedIndices.isNotEmpty()) {
            val randomIndex = unrevealedIndices.random()
            val correctLetter = currentWord[randomIndex].toString()

            if (!usedLetters.contains(correctLetter)) {
                usedLetters.add(correctLetter)
                revealLetter(correctLetter)
                hintsRemaining--  // Decrease hint count
                powerUpsUsed++
                soundManager.playPowerUp()

                val remainingHintsText = if (hintsRemaining > 0) "You have $hintsRemaining hint(s) left." else "No hints remaining!"
                Toast.makeText(this, "💡 Hint: Letter '${correctLetter}' revealed! $remainingHintsText", Toast.LENGTH_LONG).show()
                updateUI()
            } else {
                // If the randomly selected letter was already guessed, try again recursively
                useHint()
            }
        }
    }

    private fun useSkip() {
        if (skipsRemaining <= 0) {
            Toast.makeText(this, "No skips remaining! You've already used your skip.", Toast.LENGTH_SHORT).show()
            return
        }

        lives = maxOf(1, lives - 1)
        usedLetters.clear()
        skipsRemaining--  // Decrease skip count
        setupGame()
        powerUpsUsed++
        soundManager.playPowerUp()
        Toast.makeText(this, "⏭️ Word skipped! -1 life. You have $skipsRemaining skip(s) left.", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun useExtraLife() {
        if (extraLivesRemaining <= 0) {
            Toast.makeText(this, "No extra lives remaining! You've already used your extra life.", Toast.LENGTH_SHORT).show()
            return
        }

        if (lives >= 6) {
            Toast.makeText(this, "You already have maximum lives!", Toast.LENGTH_SHORT).show()
            return
        }

        lives = minOf(6, lives + 1)
        extraLivesRemaining--  // Decrease extra life count
        powerUpsUsed++
        soundManager.playPowerUp()
        Toast.makeText(this, "❤️ +1 Life added! You have $extraLivesRemaining extra life(s) left.", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun startTimer() {
        timer?.cancel()

        if (timeLeft <= 0) {
            timeLeft = timeLimit
        }

        timer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isPaused && isGameActive) {
                    timeLeft = millisUntilFinished
                    val seconds = (millisUntilFinished / 1000).toInt()
                    binding.timerText.text = "${seconds}s"
                    binding.timerProgress.progress = (millisUntilFinished * 100 / timeLimit).toInt()

                    if (seconds < 10) {
                        binding.timerText.setTextColor(this@WordQuestGameActivity.getColor(R.color.accent))
                    }
                }
            }

            override fun onFinish() {
                if (isGameActive && !isPaused) {
                    onGameOver()
                }
            }
        }.start()
    }

    private fun updateUI() {
        binding.scoreText.text = score.toString()
        binding.comboText.text = "${combo}x"

        val livesDisplay = when (lives) {
            6 -> "❤️❤️❤️❤️❤️❤️"
            5 -> "❤️❤️❤️❤️❤️💔"
            4 -> "❤️❤️❤️❤️💔💔"
            3 -> "❤️❤️❤️💔💔💔"
            2 -> "❤️❤️💔💔💔💔"
            1 -> "❤️💔💔💔💔💔"
            else -> "💔💔💔💔💔💔"
        }
        binding.livesText.text = livesDisplay

        // Update power-up button states
        binding.powerUpHint.isEnabled = !isPaused && isGameActive && hintsRemaining > 0
        binding.powerUpSkip.isEnabled = !isPaused && isGameActive && skipsRemaining > 0 && lives > 1
        binding.powerUpLife.isEnabled = !isPaused && isGameActive && extraLivesRemaining > 0 && lives < 6

        // Update button text - CHANGE THIS LINE
        binding.powerUpHint.text = "💡 Hint ($hintsRemaining)"
        binding.powerUpSkip.text = "⏭️ Skip ($skipsRemaining)"
        binding.powerUpLife.text = "❤️ Life ($extraLivesRemaining)"

        binding.powerUpHint.alpha = if (binding.powerUpHint.isEnabled) 1f else 0.5f
        binding.powerUpSkip.alpha = if (binding.powerUpSkip.isEnabled) 1f else 0.5f
        binding.powerUpLife.alpha = if (binding.powerUpLife.isEnabled) 1f else 0.5f
    }

    private fun onWordComplete() {
        isGameActive = false
        isWaitingForNextWord = true
        timer?.cancel()
        soundManager.playGameOver()

        val timeBonus = (timeLeft / 1000).toInt() * 10
        val finalScore = score + timeBonus

        val anim = ObjectAnimator.ofFloat(binding.wordContainer, "scaleX", 1f, 1.2f, 1f)
        anim.duration = 300
        anim.repeatCount = 2
        anim.start()

        binding.letterInput.isEnabled = false

        MaterialAlertDialogBuilder(this)
            .setTitle("🎉 WORD COMPLETE! 🎉")
            .setMessage("""
                Correct! You guessed the word: $currentWord
                
                Score: +$finalScore
                Time Bonus: +$timeBonus
                Combo: ${combo}x
            """.trimIndent())
            .setPositiveButton("Next Word") { _, _ ->
                loadNextWord()
            }
            .setNegativeButton("End Game") { _, _ ->
                endGame()
            }
            .show()
    }

    private fun loadNextWord() {
        isGameActive = true
        isPaused = false
        isWaitingForNextWord = false
        timeLeft = timeLimit
        hintsRemaining = 1
        setupGame()
        startTimer()
        updateUI()
        binding.letterInput.isEnabled = true
        binding.letterInput.requestFocus()
        binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun onGameOver() {
        isGameActive = false
        timer?.cancel()
        soundManager.playGameOver()

        saveScoreToLeaderboard(score)
        endGame()
    }

    private fun saveScoreToLeaderboard(score: Int) {
        val db = FirebaseFirestore.getInstance()
        val playerName = sharedPrefsManager.getPlayerName()

        val scoreData = hashMapOf(
            "playerName" to playerName,
            "score" to score,
            "game" to "Word Quest",
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("scores").add(scoreData)
            .addOnSuccessListener {
                android.util.Log.d("FIREBASE", "Score saved: $score for $playerName")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FIREBASE", "Failed to save: ${e.message}")
            }
    }
    private fun endGame() {
        sharedPrefsManager.updateScore("wordquest", score)

        lifecycleScope.launch {
            try {
                val authHelper = AuthHelper(this@WordQuestGameActivity)
                val scoreHelper = ScoreHelper()
                val uid = authHelper.getCurrentUid()
                val playerName = authHelper.getPlayerName(uid)
                scoreHelper.saveScore(uid, playerName, "Word Quest", score)
            } catch (e: Exception) {
                android.util.Log.e("WordQuest", "Failed to save: ${e.message}")
            }
        }

        val intent = Intent(this, WordQuestResultActivity::class.java).apply {
            putExtra("FINAL_SCORE", score)
            putExtra("POWER_UPS_USED", powerUpsUsed)
            putExtra("WORD_COUNT", wordsPlayed)
        }
        startActivity(intent)
        finish()
    }

    private fun animateCorrectGuess() {
        val colorAnim = ObjectAnimator.ofObject(
            binding.wordText, "textColor",
            ArgbEvaluator(),
            getColor(R.color.white),
            getColor(R.color.success),
            getColor(R.color.white)
        )
        colorAnim.duration = 500
        colorAnim.start()
    }

    private fun animateWrongGuess() {
        val shakeAnim = AnimationUtils.loadAnimation(this, R.anim.shake)
        binding.wordContainer.startAnimation(shakeAnim)

        val colorAnim = ObjectAnimator.ofObject(
            binding.wordText, "textColor",
            ArgbEvaluator(),
            getColor(R.color.white),
            getColor(R.color.accent),
            getColor(R.color.white)
        )
        colorAnim.duration = 500
        colorAnim.start()
    }

    private fun showExitDialog() {
        if (!isGameActive) {
            finish()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Exit Game?")
            .setMessage("Are you sure you want to exit? Your progress will be lost.")
            .setPositiveButton("Exit") { _, _ ->
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (isGameActive && !isPaused && !isWaitingForNextWord) {
            isPaused = true
            timer?.cancel()
            soundManager.pauseBgMusic()
            binding.letterInput.isEnabled = false
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isGameActive && isPaused && !isWaitingForNextWord && lives > 0 && guessedWord.contains('_')) {
            isPaused = false
            startTimer()
            soundManager.resumeBgMusic()
            binding.letterInput.isEnabled = true
            binding.letterInput.requestFocus()
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
        } else if (isGameActive && !isPaused && !isWaitingForNextWord && lives > 0 && guessedWord.contains('_')) {
            startTimer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        soundManager.release()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("timeLeft", timeLeft)
        outState.putInt("score", score)
        outState.putInt("lives", lives)
        outState.putInt("combo", combo)
        outState.putInt("wordsPlayed", wordsPlayed)
        outState.putInt("hintsRemaining", hintsRemaining)
        outState.putInt("skipsRemaining", skipsRemaining)
        outState.putInt("extraLivesRemaining", extraLivesRemaining)
        outState.putBoolean("isGameActive", isGameActive)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        timeLeft = savedInstanceState.getLong("timeLeft", timeLimit)
        score = savedInstanceState.getInt("score", 0)
        lives = savedInstanceState.getInt("lives", 6)
        combo = savedInstanceState.getInt("combo", 0)
        wordsPlayed = savedInstanceState.getInt("wordsPlayed", 0)
        hintsRemaining = savedInstanceState.getInt("hintsRemaining", 2)
        skipsRemaining = savedInstanceState.getInt("skipsRemaining", 1)
        extraLivesRemaining = savedInstanceState.getInt("extraLivesRemaining", 1)
        isGameActive = savedInstanceState.getBoolean("isGameActive", true)
        updateUI()
    }
}