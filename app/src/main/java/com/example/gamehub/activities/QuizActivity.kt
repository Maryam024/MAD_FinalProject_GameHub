package com.example.gamehub.activities

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.gamehub.R
import com.example.gamehub.databinding.ActivityQuizGameBinding
import com.example.gamehub.models.PowerUp
import com.example.gamehub.models.Question
import com.example.gamehub.utils.SharedPrefsManager
import com.example.gamehub.utils.SoundManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.example.gamehub.utils.AuthHelper
import com.example.gamehub.utils.ScoreHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class QuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizGameBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var soundManager: SoundManager

    // Game variables
    private var allQuestions = mutableListOf<Question>()
    private var remainingQuestions = mutableListOf<Question>()
    private var currentQuestion: Question? = null
    private var score = 0
    private var lives = 3
    private var combo = 0
    private var powerUpsUsed = 0
    private var timer: CountDownTimer? = null
    private var isAnswered = false
    private var timeForQuestion = 15000L

    // Timer tracking
    private var remainingTimeForQuestion = 15000L  // Track remaining time when paused

    // Game state
    private var isGameActive = true
    private var isPaused = false
    private var questionsAnswered = 0
    private var correctAnswers = 0
    private var totalQuestionsPlayed = 0

    // Power-ups availability
    private var canUse5050 = true
    private var canUseSkip = true
    private var canUseExtraTime = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefsManager = SharedPrefsManager(this)
        soundManager = SoundManager(this)
        soundManager.startBgMusic()

        setupToolbar()
        setupPauseButton()
        loadAllQuestions()
        startGame()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            showExitDialog()
        }
    }

    private fun setupPauseButton() {
        binding.pauseButton.setOnClickListener {
            if (!isGameActive) return@setOnClickListener

            isPaused = !isPaused
            if (isPaused) {
                // Save remaining time before canceling
                remainingTimeForQuestion = timer?.let {
                    remainingTimeForQuestion
                } ?: remainingTimeForQuestion
                timer?.cancel()
                soundManager.pauseBgMusic()
                disableOptions()
                binding.pauseButton.setImageResource(android.R.drawable.ic_media_play)
                Toast.makeText(this, "⏸️ Game Paused", Toast.LENGTH_SHORT).show()
            } else {
                startTimer()  // This will use remainingTimeForQuestion
                soundManager.resumeBgMusic()
                enableOptions()
                binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
                Toast.makeText(this, "▶️ Game Resumed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAllQuestions() {
        allQuestions = generateAllQuestions().shuffled().toMutableList()
        resetRemainingQuestions()
    }

    private fun generateAllQuestions(): MutableList<Question> {
        return mutableListOf(
            // Geography (10 questions)
            Question(1, "What is the capital of France?", listOf("London", "Berlin", "Paris", "Madrid"), 2, "Geography", "Easy", 100),
            Question(2, "What is the capital of Japan?", listOf("Seoul", "Beijing", "Tokyo", "Bangkok"), 2, "Geography", "Easy", 100),
            Question(3, "What is the capital of Brazil?", listOf("Rio de Janeiro", "Sao Paulo", "Brasilia", "Salvador"), 2, "Geography", "Medium", 150),
            Question(4, "What is the capital of India?", listOf("Mumbai", "Delhi", "Kolkata", "Chennai"), 1, "Geography", "Easy", 100),
            Question(5, "What is the capital of Australia?", listOf("Sydney", "Melbourne", "Canberra", "Perth"), 2, "Geography", "Medium", 150),
            Question(6, "What is the largest ocean on Earth?", listOf("Atlantic", "Indian", "Arctic", "Pacific"), 3, "Geography", "Easy", 100),
            Question(7, "What is the longest river in the world?", listOf("Amazon", "Nile", "Yangtze", "Mississippi"), 1, "Geography", "Hard", 200),
            Question(8, "What is the tallest mountain in the world?", listOf("K2", "Kangchenjunga", "Everest", "Makalu"), 2, "Geography", "Medium", 150),
            Question(9, "Which desert is the largest in the world?", listOf("Gobi", "Sahara", "Kalahari", "Atacama"), 1, "Geography", "Hard", 200),
            Question(10, "Which country has the most natural lakes?", listOf("USA", "Russia", "Canada", "Brazil"), 2, "Geography", "Hard", 200),

            // Science (10 questions)
            Question(11, "Which planet is known as the Red Planet?", listOf("Mars", "Jupiter", "Venus", "Saturn"), 0, "Science", "Easy", 100),
            Question(12, "What is the chemical symbol for Gold?", listOf("Go", "Gd", "Au", "Ag"), 2, "Science", "Medium", 150),
            Question(13, "Who developed the theory of relativity?", listOf("Newton", "Galileo", "Einstein", "Tesla"), 2, "Science", "Hard", 200),
            Question(14, "What is the hardest natural substance?", listOf("Iron", "Diamond", "Gold", "Platinum"), 1, "Science", "Easy", 100),
            Question(15, "What is the boiling point of water?", listOf("90°C", "100°C", "110°C", "120°C"), 1, "Science", "Easy", 100),
            Question(16, "What gas do plants absorb?", listOf("Oxygen", "Nitrogen", "Carbon Dioxide", "Hydrogen"), 2, "Science", "Easy", 100),
            Question(17, "What is the fastest animal on land?", listOf("Lion", "Cheetah", "Leopard", "Tiger"), 1, "Science", "Medium", 150),
            Question(18, "What is the largest organ in the human body?", listOf("Heart", "Brain", "Liver", "Skin"), 3, "Science", "Medium", 150),
            Question(19, "What is the chemical symbol for Oxygen?", listOf("Ox", "Om", "O", "Oy"), 2, "Science", "Easy", 100),
            Question(20, "Who invented the light bulb?", listOf("Nikola Tesla", "Thomas Edison", "Albert Einstein", "Alexander Bell"), 1, "Science", "Medium", 150),

            // Animals (10 questions)
            Question(21, "Which animal is known as the 'Ship of the Desert'?", listOf("Camel", "Horse", "Elephant", "Lion"), 0, "Animals", "Easy", 100),
            Question(22, "What is the largest animal on Earth?", listOf("Elephant", "Blue Whale", "Giraffe", "Great White Shark"), 1, "Animals", "Medium", 150),
            Question(23, "Which bird can fly backwards?", listOf("Eagle", "Hummingbird", "Sparrow", "Parrot"), 1, "Animals", "Hard", 200),
            Question(24, "What is the national animal of Australia?", listOf("Koala", "Kangaroo", "Wombat", "Platypus"), 1, "Animals", "Easy", 100),
            Question(25, "Which animal has the longest lifespan?", listOf("Elephant", "Galapagos Tortoise", "Bowhead Whale", "Greenland Shark"), 3, "Animals", "Hard", 200),
            Question(26, "What is the fastest fish in the ocean?", listOf("Marlin", "Tuna", "Sailfish", "Swordfish"), 2, "Animals", "Hard", 200),
            Question(27, "Which animal has three hearts?", listOf("Octopus", "Squid", "Cuttlefish", "All of above"), 3, "Animals", "Hard", 200),
            Question(28, "What is the smallest mammal?", listOf("Mouse", "Shrew", "Bumblebee Bat", "Pygmy Marmoset"), 2, "Animals", "Hard", 200),
            Question(29, "Which animal can regenerate its limbs?", listOf("Lizard", "Salamander", "Crab", "Starfish"), 3, "Animals", "Medium", 150),
            Question(30, "What is the only mammal capable of sustained flight?", listOf("Flying Squirrel", "Bat", "Flying Fox", "Sugar Glider"), 1, "Animals", "Easy", 100),

            // Entertainment (10 questions)
            Question(31, "Who painted the Mona Lisa?", listOf("Van Gogh", "Picasso", "Da Vinci", "Rembrandt"), 2, "Arts", "Medium", 150),
            Question(32, "Who wrote 'Romeo and Juliet'?", listOf("Charles Dickens", "Jane Austen", "William Shakespeare", "Mark Twain"), 2, "Literature", "Medium", 150),
            Question(33, "Which movie won the Oscar for Best Picture in 2020?", listOf("1917", "Joker", "Parasite", "Once Upon a Time in Hollywood"), 2, "Movies", "Hard", 200),
            Question(34, "Who played Jack Dawson in Titanic?", listOf("Brad Pitt", "Leonardo DiCaprio", "Johnny Depp", "Matt Damon"), 1, "Movies", "Easy", 100),
            Question(35, "Which band performed 'Bohemian Rhapsody'?", listOf("The Beatles", "Queen", "Led Zeppelin", "Pink Floyd"), 1, "Music", "Medium", 150),
            Question(36, "Who is known as the King of Pop?", listOf("Elvis Presley", "Michael Jackson", "Prince", "Freddie Mercury"), 1, "Music", "Easy", 100),
            Question(37, "Which game features characters like Pikachu and Charizard?", listOf("Digimon", "Pokemon", "Yu-Gi-Oh", "Dragon Quest"), 1, "Games", "Easy", 100),
            Question(38, "Who directed the movie 'Inception'?", listOf("James Cameron", "Steven Spielberg", "Christopher Nolan", "Quentin Tarantino"), 2, "Movies", "Medium", 150),
            Question(39, "Which TV series features the character 'Jon Snow'?", listOf("The Witcher", "Game of Thrones", "Vikings", "The Last Kingdom"), 1, "TV", "Easy", 100),
            Question(40, "Who wrote the Harry Potter books?", listOf("J.R.R. Tolkien", "George R.R. Martin", "J.K. Rowling", "Stephen King"), 2, "Literature", "Easy", 100),

            // Technology (10 questions)
            Question(41, "Who is known as the father of computers?", listOf("Alan Turing", "Charles Babbage", "Bill Gates", "Steve Jobs"), 1, "Technology", "Hard", 200),
            Question(42, "What does CPU stand for?", listOf("Computer Processing Unit", "Central Processing Unit", "Central Program Unit", "Computer Program Unit"), 1, "Technology", "Easy", 100),
            Question(43, "Which company created the Android operating system?", listOf("Apple", "Microsoft", "Google", "Samsung"), 2, "Technology", "Easy", 100),
            Question(44, "What does RAM stand for?", listOf("Random Access Memory", "Readily Available Memory", "Random Allocation Memory", "Rapid Access Memory"), 0, "Technology", "Easy", 100),
            Question(45, "Who is the CEO of Tesla and SpaceX?", listOf("Jeff Bezos", "Elon Musk", "Mark Zuckerberg", "Tim Cook"), 1, "Technology", "Easy", 100),
            Question(46, "What year was the first iPhone released?", listOf("2005", "2006", "2007", "2008"), 2, "Technology", "Medium", 150),
            Question(47, "What does AI stand for?", listOf("Artificial Intelligence", "Automated Intelligence", "Advanced Interface", "Algorithmic Intelligence"), 0, "Technology", "Easy", 100),
            Question(48, "Which company owns Instagram?", listOf("Google", "Microsoft", "Facebook", "Twitter"), 2, "Technology", "Easy", 100),
            Question(49, "What is the most popular programming language in 2024?", listOf("Java", "Python", "JavaScript", "C++"), 1, "Technology", "Medium", 150),
            Question(50, "What does VPN stand for?", listOf("Virtual Private Network", "Very Private Network", "Virtual Protected Network", "Verified Private Network"), 0, "Technology", "Medium", 150),

            // Sports (10 questions)
            Question(51, "Which country won the FIFA World Cup 2018?", listOf("France", "Croatia", "Belgium", "England"), 0, "Sports", "Medium", 150),
            Question(52, "Who has won the most Ballon d'Or awards?", listOf("Cristiano Ronaldo", "Lionel Messi", "Michel Platini", "Johan Cruyff"), 1, "Sports", "Hard", 200),
            Question(53, "Which sport is known as 'the beautiful game'?", listOf("Basketball", "Cricket", "Football", "Tennis"), 2, "Sports", "Easy", 100),
            Question(54, "Who is the fastest man in the world?", listOf("Usain Bolt", "Tyson Gay", "Yohan Blake", "Justin Gatlin"), 0, "Sports", "Easy", 100),
            Question(55, "How many players are on a basketball team on court?", listOf("5", "6", "7", "8"), 0, "Sports", "Easy", 100),
            Question(56, "Which tennis player has won the most Grand Slams?", listOf("Roger Federer", "Rafael Nadal", "Novak Djokovic", "Pete Sampras"), 2, "Sports", "Hard", 200),
            Question(57, "Which country invented cricket?", listOf("Australia", "India", "England", "South Africa"), 2, "Sports", "Medium", 150),
            Question(58, "Who is known as 'King James' in basketball?", listOf("James Harden", "LeBron James", "James Wiseman", "James Worthy"), 1, "Sports", "Easy", 100),
            Question(59, "Which boxer was known as 'The Greatest'?", listOf("Mike Tyson", "Muhammad Ali", "Joe Frazier", "George Foreman"), 1, "Sports", "Easy", 100),
            Question(60, "How many holes are in a round of golf?", listOf("16", "18", "20", "22"), 1, "Sports", "Easy", 100)
        )
    }

    private fun resetRemainingQuestions() {
        remainingQuestions = allQuestions.toMutableList()
        remainingQuestions.shuffle()
    }

    private fun getNextQuestion(): Question? {
        if (remainingQuestions.isEmpty()) {
            resetRemainingQuestions()
        }
        return remainingQuestions.removeFirstOrNull()
    }

    private fun startGame() {
        isGameActive = true
        isPaused = false
        score = 0
        lives = 3
        combo = 0
        questionsAnswered = 0
        correctAnswers = 0
        totalQuestionsPlayed = 0
        canUse5050 = true
        canUseSkip = true
        canUseExtraTime = true
        timeForQuestion = 15000L
        remainingTimeForQuestion = 15000L

        resetRemainingQuestions()
        loadNextQuestion()
        updateUI()
    }

    private fun loadNextQuestion() {
        if (!isGameActive) return

        currentQuestion = getNextQuestion()

        if (currentQuestion == null) {
            // All questions completed - WIN!
            onGameWin()
            return
        }

        totalQuestionsPlayed++
        isAnswered = false
        remainingTimeForQuestion = timeForQuestion  // Reset remaining time for new question
        val question = currentQuestion!!

        binding.questionText.text = question.text
        binding.questionText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce))

        val options = listOf(
            binding.option1, binding.option2, binding.option3, binding.option4
        )

        options.forEachIndexed { index, button ->
            button.text = "${(65 + index).toChar()}. ${question.options[index]}"
            button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.card_bg)
            button.isEnabled = true
            button.alpha = 1f
        }

        startTimer()
    }

    private fun startTimer() {
        timer?.cancel()

        // Use remaining time if available, otherwise use full time
        val timeToUse = if (remainingTimeForQuestion > 0 && remainingTimeForQuestion < timeForQuestion) {
            remainingTimeForQuestion
        } else {
            timeForQuestion
        }

        binding.timerProgress.progress = (timeToUse * 100 / timeForQuestion).toInt()
        binding.timeText.text = "${timeToUse / 1000}s"

        timer = object : CountDownTimer(timeToUse, 50) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isPaused && isGameActive) {
                    remainingTimeForQuestion = millisUntilFinished
                    val progress = (millisUntilFinished * 100 / timeForQuestion).toInt()
                    binding.timerProgress.progress = progress
                    binding.timeText.text = "${millisUntilFinished / 1000}s"

                    when {
                        millisUntilFinished < 3000 -> {
                            binding.timerProgress.progressTintList = ContextCompat.getColorStateList(this@QuizActivity, R.color.accent)
                            binding.timeText.setTextColor(ContextCompat.getColor(this@QuizActivity, R.color.accent))
                        }
                        millisUntilFinished < 7000 -> {
                            binding.timerProgress.progressTintList = ContextCompat.getColorStateList(this@QuizActivity, R.color.warning)
                            binding.timeText.setTextColor(ContextCompat.getColor(this@QuizActivity, R.color.warning))
                        }
                        else -> {
                            binding.timerProgress.progressTintList = ContextCompat.getColorStateList(this@QuizActivity, R.color.success)
                            binding.timeText.setTextColor(ContextCompat.getColor(this@QuizActivity, R.color.white))
                        }
                    }
                }
            }

            override fun onFinish() {
                if (!isAnswered && isGameActive && !isPaused) {
                    onTimeOut()
                }
            }
        }.start()
    }

    private fun checkAnswer(selectedIndex: Int) {
        if (isAnswered || !isGameActive || isPaused) return

        isAnswered = true
        timer?.cancel()

        val question = currentQuestion ?: return
        val isCorrect = selectedIndex == question.correctAnswer

        if (isCorrect) {
            handleCorrectAnswer(selectedIndex, question)
        } else {
            handleWrongAnswer(selectedIndex, question)
        }

        // Move to next question after delay
        Handler(Looper.getMainLooper()).postDelayed({
            loadNextQuestion()
        }, 1500)
    }

    private fun handleCorrectAnswer(selectedIndex: Int, question: Question) {
        soundManager.playCorrect()
        correctAnswers++
        questionsAnswered++

        val basePoints = question.points
        val comboBonus = (combo * 50).coerceAtMost(500)
        val pointsEarned = basePoints + comboBonus

        score += pointsEarned
        combo++

        val options = listOf(binding.option1, binding.option2, binding.option3, binding.option4)
        options[selectedIndex].backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)

        if (combo > 1) {
            showComboAnimation()
        }

        Toast.makeText(this, "+$pointsEarned points! Combo: ${combo}x", Toast.LENGTH_SHORT).show()

        updateUI()
        disableOptions()
    }

    private fun handleWrongAnswer(selectedIndex: Int, question: Question) {
        soundManager.playWrong()
        lives--
        combo = 0
        questionsAnswered++

        val options = listOf(binding.option1, binding.option2, binding.option3, binding.option4)
        options[selectedIndex].backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent)
        options[question.correctAnswer].backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)

        binding.questionText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))

        Toast.makeText(this, "Wrong! Correct: ${question.options[question.correctAnswer]}", Toast.LENGTH_SHORT).show()

        updateUI()
        disableOptions()

        if (lives <= 0) {
            onGameLose()
        }
    }

    private fun usePowerUp(powerUp: PowerUp) {
        if (!isGameActive || isPaused) return

        soundManager.playPowerUp()
        powerUpsUsed++

        when (powerUp) {
            PowerUp.FIFTY_FIFTY -> useFiftyFifty()
            PowerUp.SKIP -> useSkip()
            PowerUp.EXTRA_TIME -> useExtraTime()
        }

        updateUI()
    }

    private fun useFiftyFifty() {
        if (!canUse5050) return
        canUse5050 = false

        val question = currentQuestion ?: return
        val wrongOptions = mutableListOf<Int>()

        for (i in question.options.indices) {
            if (i != question.correctAnswer) {
                wrongOptions.add(i)
            }
        }

        wrongOptions.shuffle()
        val toRemove = wrongOptions.take(2)

        val options = listOf(binding.option1, binding.option2, binding.option3, binding.option4)
        toRemove.forEach { index ->
            options[index].text = "???"
            options[index].isEnabled = false
            options[index].alpha = 0.5f
        }

        Toast.makeText(this, "50/50 Used! Two options removed", Toast.LENGTH_SHORT).show()
    }

    private fun useSkip() {
        if (!canUseSkip) return
        canUseSkip = false

        timer?.cancel()
        loadNextQuestion()

        Toast.makeText(this, "Question Skipped!", Toast.LENGTH_SHORT).show()
    }

    private fun useExtraTime() {
        if (!canUseExtraTime) return
        canUseExtraTime = false

        // Add 10 seconds to remaining time
        remainingTimeForQuestion += 10000
        if (remainingTimeForQuestion > timeForQuestion * 2) {
            remainingTimeForQuestion = timeForQuestion * 2
        }
        timeForQuestion = remainingTimeForQuestion
        startTimer()

        Toast.makeText(this, "+10 Seconds Added!", Toast.LENGTH_SHORT).show()
    }

    private fun onTimeOut() {
        isAnswered = true
        timer?.cancel()
        lives--
        combo = 0

        Toast.makeText(this, "Time's Up! -1 Life", Toast.LENGTH_LONG).show()

        updateUI()

        if (lives <= 0) {
            onGameLose()
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                loadNextQuestion()
            }, 1500)
        }
    }

    private fun showComboAnimation() {
        binding.comboText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce))
        binding.comboText.setTextColor(ContextCompat.getColor(this, R.color.accent))
        Handler(Looper.getMainLooper()).postDelayed({
            binding.comboText.setTextColor(ContextCompat.getColor(this, R.color.warning))
        }, 500)
    }

    private fun updateUI() {
        binding.scoreText.text = score.toString()
        binding.comboText.text = "${combo}x"

        val livesDisplay = when (lives) {
            3 -> "❤️❤️❤️"
            2 -> "❤️❤️💔"
            1 -> "❤️💔💔"
            else -> "💔💔💔"
        }
        binding.livesText.text = livesDisplay

        binding.powerUp5050.isEnabled = canUse5050 && !isPaused
        binding.powerUpSkip.isEnabled = canUseSkip && !isPaused
        binding.powerUpTime.isEnabled = canUseExtraTime && !isPaused

        binding.powerUp5050.alpha = if (canUse5050 && !isPaused) 1f else 0.5f
        binding.powerUpSkip.alpha = if (canUseSkip && !isPaused) 1f else 0.5f
        binding.powerUpTime.alpha = if (canUseExtraTime && !isPaused) 1f else 0.5f
    }

    private fun disableOptions() {
        val options = listOf(binding.option1, binding.option2, binding.option3, binding.option4)
        options.forEach { it.isEnabled = false }
    }

    private fun enableOptions() {
        if (!isAnswered && isGameActive) {
            val options = listOf(binding.option1, binding.option2, binding.option3, binding.option4)
            options.forEach { it.isEnabled = true }
        }
    }
    private fun onGameWin() {

        isGameActive = false
        timer?.cancel()
        soundManager.playGameOver()

        val finalScore = score

        sharedPrefsManager.updateScore(
            "quiz",
            finalScore
        )

        saveScoreToLeaderboard(finalScore)

        val intent = Intent(this, QuizResultActivity::class.java).apply {
            putExtra("FINAL_SCORE", finalScore)
            putExtra("TOTAL_QUESTIONS", questionsAnswered)
            putExtra("CORRECT_ANSWERS", correctAnswers)
            putExtra("COMBO", combo)
            putExtra("POWER_UPS_USED", powerUpsUsed)
            putExtra("IS_WIN", true)
        }

        startActivity(intent)
        finish()
    }
    private fun saveScoreToLeaderboard(score: Int) {
        val db = FirebaseFirestore.getInstance()
        val playerName = sharedPrefsManager.getPlayerName()

        val scoreData = hashMapOf(
            "playerName" to playerName,
            "score" to score,
            "game" to "Quiz Activity",
            "timestamp" to System.currentTimeMillis()
        )
        android.util.Log.d("QUIZ_DEBUG", "Saving score = $score")
        db.collection("scores").add(scoreData)
            .addOnSuccessListener {
                android.util.Log.d("FIREBASE", "Score saved: $score for $playerName")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FIREBASE", "Failed to save: ${e.message}")
            }
    }

    private fun onGameLose() {

        isGameActive = false
        timer?.cancel()
        soundManager.playGameOver()

        saveScoreToLeaderboard(score)

        val intent = Intent(this, QuizResultActivity::class.java).apply {
            putExtra("FINAL_SCORE", score)
            putExtra("TOTAL_QUESTIONS", questionsAnswered)
            putExtra("CORRECT_ANSWERS", correctAnswers)
            putExtra("COMBO", combo)
            putExtra("POWER_UPS_USED", powerUpsUsed)
            putExtra("IS_WIN", false)
        }

        startActivity(intent)
        finish()
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

    private fun setupClickListeners() {
        binding.option1.setOnClickListener { checkAnswer(0) }
        binding.option2.setOnClickListener { checkAnswer(1) }
        binding.option3.setOnClickListener { checkAnswer(2) }
        binding.option4.setOnClickListener { checkAnswer(3) }

        binding.powerUp5050.setOnClickListener { usePowerUp(PowerUp.FIFTY_FIFTY) }
        binding.powerUpSkip.setOnClickListener { usePowerUp(PowerUp.SKIP) }
        binding.powerUpTime.setOnClickListener { usePowerUp(PowerUp.EXTRA_TIME) }
    }

    override fun onPause() {
        super.onPause()
        if (isGameActive && !isPaused) {
            isPaused = true
            remainingTimeForQuestion = timer?.let {
                remainingTimeForQuestion
            } ?: remainingTimeForQuestion
            timer?.cancel()
            soundManager.pauseBgMusic()
            disableOptions()
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isGameActive && isPaused && lives > 0 && currentQuestion != null) {
            isPaused = false
            startTimer()
            soundManager.resumeBgMusic()
            enableOptions()
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
        outState.putInt("score", score)
        outState.putInt("lives", lives)
        outState.putInt("combo", combo)
        outState.putInt("questionsAnswered", questionsAnswered)
        outState.putInt("correctAnswers", correctAnswers)
        outState.putBoolean("canUse5050", canUse5050)
        outState.putBoolean("canUseSkip", canUseSkip)
        outState.putBoolean("canUseExtraTime", canUseExtraTime)
        outState.putLong("timeForQuestion", timeForQuestion)
        outState.putLong("remainingTimeForQuestion", remainingTimeForQuestion)
        outState.putBoolean("isGameActive", isGameActive)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        score = savedInstanceState.getInt("score", 0)
        lives = savedInstanceState.getInt("lives", 3)
        combo = savedInstanceState.getInt("combo", 0)
        questionsAnswered = savedInstanceState.getInt("questionsAnswered", 0)
        correctAnswers = savedInstanceState.getInt("correctAnswers", 0)
        canUse5050 = savedInstanceState.getBoolean("canUse5050", true)
        canUseSkip = savedInstanceState.getBoolean("canUseSkip", true)
        canUseExtraTime = savedInstanceState.getBoolean("canUseExtraTime", true)
        timeForQuestion = savedInstanceState.getLong("timeForQuestion", 15000L)
        remainingTimeForQuestion = savedInstanceState.getLong("remainingTimeForQuestion", timeForQuestion)
        isGameActive = savedInstanceState.getBoolean("isGameActive", true)
        updateUI()
    }
}