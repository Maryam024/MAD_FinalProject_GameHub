package com.example.gamehub.activities

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.gamehub.R
import com.example.gamehub.databinding.ActivityRapidTapBinding
import com.example.gamehub.utils.AuthHelper
import com.example.gamehub.utils.ScoreHelper
import com.example.gamehub.utils.SharedPrefsManager
import com.example.gamehub.utils.SoundManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.random.Random

class RapidTapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRapidTapBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var soundManager: SoundManager

    private var currentCircle: View? = null
    private var expiryRunnable: Runnable? = null   // per-circle timeout token
    private var score = 0
    private var isGameActive = true
    private var isPaused = false
    private var timer: CountDownTimer? = null
    private var timeLeft = 30
    private val nextCircleHandler = Handler(Looper.getMainLooper())

    private var currentDuration = 900L
    private var spawnDelay = 120L

    // Difficulty steps (elapsed seconds -> circle lifetime in ms)
    private val difficultySteps = listOf(
        5 to 750L,
        10 to 600L,
        15 to 480L,
        20 to 380L,
        25 to 300L
    )

    private var hitStreak = 0
    private var missCount = 0
    private var totalCirclesSpawned = 0
    private var circlesHit = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRapidTapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefsManager = SharedPrefsManager(this)
        soundManager = SoundManager(this)
        soundManager.startBgMusic()

        setupToolbar()
        startGame()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            showExitDialog()
        }
        binding.pauseButton.setOnClickListener {
            togglePause()
        }
    }

    private fun startGame() {
        score = 0
        hitStreak = 0
        missCount = 0
        totalCirclesSpawned = 0
        circlesHit = 0
        timeLeft = 30
        isGameActive = true
        isPaused = false
        currentDuration = 900L
        spawnDelay = 120L
        clearCircle()
        updateScoreUI()
        startTimer()
        scheduleNextCircle()
        binding.gameOverCard.visibility = View.GONE
        binding.pauseButton.isEnabled = true
        binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(timeLeft * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isPaused && isGameActive) {
                    timeLeft = (millisUntilFinished / 1000).toInt()
                    binding.timerText.text = timeLeft.toString()
                    updateDifficulty()
                }
            }

            override fun onFinish() {
                if (isGameActive && !isPaused) {
                    endGame()
                }
            }
        }.start()
    }

    private fun updateDifficulty() {
        val elapsed = 30 - timeLeft
        for ((threshold, duration) in difficultySteps) {
            if (elapsed >= threshold && currentDuration != duration) {
                currentDuration = duration
                if (duration <= 480) {
                    binding.timerText.setTextColor(ContextCompat.getColor(this, R.color.accent))
                }
                break
            }
        }
    }

    private fun scheduleNextCircle() {
        if (!isGameActive || isPaused) return
        nextCircleHandler.postDelayed({
            if (isGameActive && !isPaused) {
                showRandomCircle()
            }
        }, spawnDelay)
    }

    private fun showRandomCircle() {
        if (!isGameActive || isPaused) return

        // Remove any leftover view from the previous round WITHOUT counting a miss.
        // (A genuine miss is only ever recorded by the per-circle expiry runnable.)
        currentCircle?.let { binding.gameContainer.removeView(it) }
        currentCircle = null

        val circle = createCircleView()
        currentCircle = circle
        binding.gameContainer.addView(circle)

        circle.scaleX = 0f
        circle.scaleY = 0f
        circle.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

        totalCirclesSpawned++

        // This circle's own expiry. If it is still the active circle when it fires,
        // it was never tapped -> that is a real miss.
        val timeout = Runnable {
            if (isGameActive && !isPaused && currentCircle == circle) {
                currentCircle = null
                handleMiss()
                removeCircle(circle)
                scheduleNextCircle()
            }
        }
        expiryRunnable = timeout
        nextCircleHandler.postDelayed(timeout, currentDuration)
    }

    private fun handleMiss() {
        missCount++
        hitStreak = 0

        if (timeLeft > 1) timeLeft -= 1

        binding.gameContainer.animate()
            .translationX(10f)
            .setDuration(50)
            .withEndAction {
                binding.gameContainer.animate().translationX(0f).setDuration(50).start()
            }.start()

        soundManager.playWrong()
    }

    private fun createCircleView(): View {
        val circle = View(this).apply {
            val size = Random.nextInt(150, 210)
            val params = RelativeLayout.LayoutParams(size, size)
            val parentWidth = binding.gameContainer.width
            val parentHeight = binding.gameContainer.height

            // Keep circles below the top stats bar so they are always tappable.
            val topInset = (170 * resources.displayMetrics.density).toInt()
            if (parentWidth > 0 && parentHeight > 0) {
                val maxLeft = maxOf(10, parentWidth - size - 20)
                val verticalSpace = parentHeight - size - topInset - 40
                val marginTop = topInset + Random.nextInt(maxOf(1, verticalSpace))
                params.leftMargin = Random.nextInt(maxLeft)
                params.topMargin = marginTop
            }
            layoutParams = params

            background = ContextCompat.getDrawable(context, R.drawable.circle_rapid_tap)

            val type = when (Random.nextInt(100)) {
                in 0..59 -> CircleType.GREEN
                in 60..84 -> CircleType.GOLD
                else -> CircleType.RED
            }
            setTag(R.id.circle_type, type)

            isClickable = true
            isFocusable = true
            setOnClickListener { handleTap(this, type) }
        }

        val color = when (circle.getTag(R.id.circle_type) as CircleType) {
            CircleType.GREEN -> R.color.green_circle
            CircleType.GOLD -> R.color.gold_circle
            CircleType.RED -> R.color.red_circle
        }
        circle.background?.setTint(ContextCompat.getColor(this, color))
        return circle
    }

    private fun handleTap(circle: View, type: CircleType) {
        if (!isGameActive || isPaused) return
        // Only the current, live circle scores. Ignore stray/late taps.
        if (circle !== currentCircle) return

        // Cancel this circle's expiry so it cannot later be counted as a miss.
        expiryRunnable?.let { nextCircleHandler.removeCallbacks(it) }
        expiryRunnable = null
        currentCircle = null
        circle.isClickable = false

        val points = when (type) {
            CircleType.GREEN -> 15 + (hitStreak * 2)
            CircleType.GOLD -> 30 + (hitStreak * 3)
            CircleType.RED -> 50 + (hitStreak * 5)
        }

        score += points
        circlesHit++
        hitStreak++
        updateScoreUI()

        when (type) {
            CircleType.GREEN, CircleType.GOLD -> soundManager.playCorrect()
            CircleType.RED -> {
                soundManager.playPowerUp()
                timeLeft = minOf(35, timeLeft + 1)
            }
        }

        circle.animate()
            .alpha(0f)
            .scaleX(1.5f)
            .scaleY(1.5f)
            .setDuration(100)
            .withEndAction { removeCircle(circle) }
            .start()

        showScorePopup(points, circle)

        scheduleNextCircle()
    }

    private fun showScorePopup(points: Int, anchor: View) {
        val popup = TextView(this).apply {
            text = if (points > 0) "+$points" else "$points"
            textSize = 28f
            setTextColor(ContextCompat.getColor(context, if (points > 0) R.color.success else R.color.accent))
            setShadowLayer(6f, 3f, 3f, android.graphics.Color.BLACK)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val params = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val parentLocation = IntArray(2)
        binding.gameContainer.getLocationOnScreen(parentLocation)
        params.leftMargin = location[0] - parentLocation[0] + anchor.width / 2
        params.topMargin = location[1] - parentLocation[1]
        binding.popupContainer.addView(popup, params)

        popup.animate()
            .translationYBy(-120f)
            .alpha(0f)
            .setDuration(600)
            .withEndAction {
                binding.popupContainer.removeView(popup)
            }.start()
    }

    private fun removeCircle(circle: View) {
        if (circle.parent != null) {
            binding.gameContainer.removeView(circle)
        }
        if (currentCircle === circle) currentCircle = null
    }

    private fun clearCircle() {
        expiryRunnable?.let { nextCircleHandler.removeCallbacks(it) }
        expiryRunnable = null
        currentCircle?.let { if (it.parent != null) binding.gameContainer.removeView(it) }
        currentCircle = null
    }

    private fun updateScoreUI() {
        binding.scoreText.text = score.toString()

        if (hitStreak > 3) {
            binding.scoreText.setTextColor(ContextCompat.getColor(this, R.color.accent))
            binding.scoreText.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100)
                .withEndAction {
                    binding.scoreText.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
        } else {
            binding.scoreText.setTextColor(ContextCompat.getColor(this, R.color.success))
        }
    }

    private fun togglePause() {
        if (!isGameActive) return
        isPaused = !isPaused
        if (isPaused) {
            timer?.cancel()
            soundManager.pauseBgMusic()
            nextCircleHandler.removeCallbacksAndMessages(null)
            clearCircle()
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_play)
        } else {
            startTimer()
            soundManager.resumeBgMusic()
            scheduleNextCircle()
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun endGame() {
        isGameActive = false
        timer?.cancel()
        nextCircleHandler.removeCallbacksAndMessages(null)
        clearCircle()
        soundManager.playGameOver()
        soundManager.stopBgMusic()

        val accuracy = if (totalCirclesSpawned > 0) (circlesHit.toFloat() / totalCirclesSpawned * 100).toInt() else 0
        val accuracyBonus = (accuracy / 10) * 25
        val finalScore = score + accuracyBonus

        sharedPrefsManager.updateScore("rapidTap", finalScore)

        MainScope().launch {
            try {
                val authHelper = AuthHelper(this@RapidTapActivity)
                val scoreHelper = ScoreHelper()
                val uid = authHelper.getCurrentUid()
                val playerName = authHelper.getPlayerName(uid)
                scoreHelper.saveScore(uid, playerName, "Rapid Tap Challenge", finalScore)
            } catch (e: Exception) {
                android.util.Log.e("RapidTap", "Failed to save score: ${e.message}")
            }
        }

        binding.finalScoreText.text = finalScore.toString()
        binding.gameOverCard.visibility = View.VISIBLE
        binding.pauseButton.isEnabled = false

        binding.playAgainButton.setOnClickListener {
            resetAndRestart()
        }
        binding.exitButton.setOnClickListener {
            finish()
        }
    }

    private fun resetAndRestart() {
        binding.gameOverCard.visibility = View.GONE
        soundManager.startBgMusic()
        startGame()
    }

    private fun showExitDialog() {
        if (!isGameActive) {
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
        if (isGameActive && !isPaused) {
            togglePause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        nextCircleHandler.removeCallbacksAndMessages(null)
        soundManager.release()
    }

    enum class CircleType { GREEN, GOLD, RED }
}
