package com.example.gamehub.activities

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.gamehub.R
import com.example.gamehub.adapters.MemoryCardAdapter
import com.example.gamehub.databinding.ActivityMemoryMatchBinding
import com.example.gamehub.models.MemoryCard
import com.example.gamehub.utils.SharedPrefsManager
import com.example.gamehub.utils.SoundManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.example.gamehub.utils.FirebaseManager
import com.example.gamehub.utils.AuthHelper
import com.example.gamehub.utils.ScoreHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MemoryMatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryMatchBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var soundManager: SoundManager
    private lateinit var adapter: MemoryCardAdapter

    // Game variables
    private var cards = mutableListOf<MemoryCard>()
    private var firstSelectedIndex = -1
    private var secondSelectedIndex = -1
    private var isWaiting = false
    private var moves = 0
    private var matches = 0
    private val totalPairs = 10
    private var timer: CountDownTimer? = null
    private var timeLeft = 180000L  // Initialize with full game time (3 minutes)
    private val gameTime = 180000L

    private var isPaused = false
    private var isGameActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryMatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefsManager = SharedPrefsManager(this)
        soundManager = SoundManager(this)
        soundManager.startBgMusic()

        setupToolbar()
        setupGame()
        setupClickListeners()
        setupPauseButton()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            showExitDialog()
        }
    }

    private fun setupGame() {
        generateCards()
        setupRecyclerView()
        timeLeft = gameTime  // Reset time
        startTimer()
        updateUI()
        isGameActive = true
        isPaused = false

        binding.gameStats.visibility = View.VISIBLE
        Toast.makeText(this, "Match pairs of cards! Tap two cards that are the same.", Toast.LENGTH_LONG).show()
    }

    private fun generateCards() {
        cards.clear()

        val emojis = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰",
            "🦊", "🐻", "🐼", "🐨", "🐯"
        )

        for (i in 0 until totalPairs) {
            val emoji = emojis[i]
            cards.add(MemoryCard(
                id = cards.size,
                cardId = i,
                imageRes = 0,
                cardValue = emoji
            ))
            cards.add(MemoryCard(
                id = cards.size,
                cardId = i,
                imageRes = 0,
                cardValue = emoji
            ))
        }

        cards.shuffle()

        for (i in cards.indices) {
            cards[i].id = i
            cards[i].isFlipped = false
            cards[i].isMatched = false
        }
    }

    private fun setupRecyclerView() {
        binding.cardsRecyclerView.layoutManager = GridLayoutManager(this, 4)
        adapter = MemoryCardAdapter(cards, soundManager) { position ->
            if (isGameActive && !isPaused && !isWaiting && !cards[position].isMatched && !cards[position].isFlipped) {
                onCardClick(position)
            }
        }
        binding.cardsRecyclerView.adapter = adapter
    }

    private fun onCardClick(position: Int) {
        if (firstSelectedIndex == -1) {
            firstSelectedIndex = position
            flipCard(position, true)
            soundManager.playCorrect()
        } else if (secondSelectedIndex == -1 && firstSelectedIndex != position) {
            secondSelectedIndex = position
            flipCard(position, true)
            moves++
            updateUI()

            val firstCard = cards[firstSelectedIndex]
            val secondCard = cards[position]

            if (firstCard.cardId == secondCard.cardId) {
                handleMatch()
            } else {
                handleMismatch()
            }
        }
    }

    private fun flipCard(position: Int, flipped: Boolean) {
        cards[position].isFlipped = flipped
        adapter.notifyItemChanged(position)
    }

    private fun handleMatch() {
        soundManager.playPowerUp()

        cards[firstSelectedIndex].isMatched = true
        cards[secondSelectedIndex].isMatched = true
        cards[firstSelectedIndex].isFlipped = true
        cards[secondSelectedIndex].isFlipped = true

        adapter.notifyItemChanged(firstSelectedIndex)
        adapter.notifyItemChanged(secondSelectedIndex)

        matches++
        updateUI()

        firstSelectedIndex = -1
        secondSelectedIndex = -1

        if (matches == totalPairs) {
            onGameWin()
        }
    }

    private fun handleMismatch() {
        soundManager.playWrong()
        isWaiting = true

        Handler(Looper.getMainLooper()).postDelayed({
            flipCard(firstSelectedIndex, false)
            flipCard(secondSelectedIndex, false)
            firstSelectedIndex = -1
            secondSelectedIndex = -1
            isWaiting = false
        }, 1000)
    }

    private fun startTimer() {
        timer?.cancel()

        if (timeLeft <= 0) {
            timeLeft = gameTime
        }

        timer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isPaused && isGameActive) {
                    timeLeft = millisUntilFinished
                    val seconds = (millisUntilFinished / 1000).toInt()
                    val minutes = seconds / 60
                    val remainingSeconds = seconds % 60
                    binding.timerText.text = String.format("%02d:%02d", minutes, remainingSeconds)

                    if (seconds < 10) {
                        binding.timerText.setTextColor(resources.getColor(R.color.accent))
                    }
                }
            }

            override fun onFinish() {
                if (isGameActive && !isPaused && matches < totalPairs) {
                    onGameLose()
                }
            }
        }.start()
    }

    private fun updateUI() {
        binding.movesText.text = moves.toString()
        binding.matchesText.text = "$matches / $totalPairs"

        val progress = (matches.toFloat() / totalPairs * 100).toInt()
        binding.progressBar.progress = progress

        val anim = ObjectAnimator.ofFloat(binding.movesText, "scaleX", 1f, 1.2f, 1f)
        anim.duration = 200
        anim.start()
    }

    private fun setupPauseButton() {
        binding.pauseButton.setOnClickListener {
            if (!isGameActive) return@setOnClickListener

            isPaused = !isPaused
            if (isPaused) {
                timer?.cancel()
                soundManager.pauseBgMusic()
                binding.pauseButton.setImageResource(android.R.drawable.ic_media_play)
                Toast.makeText(this, "⏸️ Game Paused", Toast.LENGTH_SHORT).show()
            } else {
                startTimer()
                soundManager.resumeBgMusic()
                binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
                Toast.makeText(this, "▶️ Game Resumed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.newGameButton.setOnClickListener {
            resetGame()
        }

        binding.exitButton.setOnClickListener {
            showExitDialog()
        }
    }

    private fun resetGame() {
        isGameActive = true
        isPaused = false
        timeLeft = gameTime
        generateCards()
        adapter.updateCards(cards)
        moves = 0
        matches = 0
        firstSelectedIndex = -1
        secondSelectedIndex = -1
        isWaiting = false
        updateUI()
        timer?.cancel()
        startTimer()
        binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun calculateScore(): Int {
        val baseScore = 1000
        val movePenalty = moves * 5
        val timeBonus = ((gameTime - timeLeft) / 1000).toInt() * 10
        val matchBonus = matches * 100
        return maxOf(0, baseScore - movePenalty + timeBonus + matchBonus)
    }


    private fun saveScoreToLeaderboard(score: Int) {
        val db = FirebaseFirestore.getInstance()
        val playerName = sharedPrefsManager.getPlayerName()

        val scoreData = hashMapOf(
            "playerName" to playerName,
            "score" to score,
            "game" to "Memory Match",
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

    private fun onGameWin() {
        isGameActive = false
        timer?.cancel()
        soundManager.playGameOver()

        val score = calculateScore()
        sharedPrefsManager.updateScore("memory", score)

        // Save to Firebase
        lifecycleScope.launch {
            try {
                val authHelper = AuthHelper(this@MemoryMatchActivity)
                val scoreHelper = ScoreHelper()
                val uid = authHelper.getCurrentUid()
                val playerName = authHelper.getPlayerName(uid)
                scoreHelper.saveScore(uid, playerName, "Memory Match", score)
            } catch (e: Exception) {
                android.util.Log.e("MemoryMatch", "Failed to save: ${e.message}")
            }
        }


        // Rest of your dialog code...
        MaterialAlertDialogBuilder(this)
            .setTitle("🎉 YOU WIN! 🎉")
            .setMessage("""
            Congratulations!
            ⭐ Moves: $moves
            🎯 Matches: $matches/$totalPairs
            ⏱️ Time: ${(gameTime - timeLeft) / 1000} seconds
            🏆 Score: $score
        """.trimIndent())
            .setPositiveButton("Play Again") { _, _ -> resetGame() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    private fun onGameLose() {
        isGameActive = false
        timer?.cancel()
        soundManager.playGameOver()

        MaterialAlertDialogBuilder(this)
            .setTitle("⏰ TIME'S UP!")
            .setMessage("""
                Don't worry! Try again?
                
                📊 Your Stats:
                • Matches: $matches/$totalPairs
                • Moves: $moves
            """.trimIndent())
            .setPositiveButton("Try Again") { _, _ ->
                resetGame()
            }
            .setNegativeButton("Exit") { _, _ ->
                finish()
            }
            .show()
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
        if (isGameActive && !isPaused) {
            isPaused = true
            timer?.cancel()
            soundManager.pauseBgMusic()
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isGameActive && isPaused && timeLeft > 0 && matches < totalPairs) {
            isPaused = false
            startTimer()
            soundManager.resumeBgMusic()
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
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
        outState.putInt("moves", moves)
        outState.putInt("matches", matches)
        outState.putBoolean("isGameActive", isGameActive)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        timeLeft = savedInstanceState.getLong("timeLeft", gameTime)
        moves = savedInstanceState.getInt("moves", 0)
        matches = savedInstanceState.getInt("matches", 0)
        isGameActive = savedInstanceState.getBoolean("isGameActive", true)
        updateUI()
    }

}