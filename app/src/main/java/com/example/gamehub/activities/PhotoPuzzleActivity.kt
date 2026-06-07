package com.example.gamehub.activities

import android.Manifest
import androidx.gridlayout.widget.GridLayout
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.gamehub.R
import com.example.gamehub.databinding.ActivityPhotoPuzzleBinding
import com.example.gamehub.utils.SharedPrefsManager
import com.example.gamehub.utils.SoundManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.example.gamehub.utils.AuthHelper
import com.example.gamehub.utils.ScoreHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PhotoPuzzleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoPuzzleBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager
    private lateinit var soundManager: SoundManager

    // Puzzle data — gridSize is now selectable (3 = 3x3, 4 = 4x4)
    private var gridSize = 3
    private val tileCount: Int get() = gridSize * gridSize
    private val emptyTile: Int get() = tileCount - 1

    private var tileBitmaps: Array<Bitmap?> = arrayOfNulls(0)
    private lateinit var currentOrder: IntArray
    private var emptyPosition = 8
    private var moves = 0
    private var isGameActive = false
    private var timer: CountDownTimer? = null
    private var timeLeft = 180000L
    private val gameTime = 180000L
    private var isPaused = false

    private var currentImageBitmap: Bitmap? = null

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1002
        private const val REQUEST_CAMERA_PERMISSION = 1001
        private const val REQUEST_GALLERY_IMAGE = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoPuzzleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefsManager = SharedPrefsManager(this)
        soundManager = SoundManager(this)
        soundManager.startBgMusic()

        // Safe default until a real puzzle is built
        currentOrder = IntArray(tileCount) { it }
        emptyPosition = emptyTile

        setupToolbar()
        setupClickListeners()
        setupPauseButton()
        showGridSizeDialog()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            showExitDialog()
        }
    }

    private fun setupClickListeners() {
        binding.newGameButton.setOnClickListener {
            showGridSizeDialog()
        }

        binding.shuffleButton.setOnClickListener {
            if (isGameActive && !isPaused) {
                shuffleTiles()
                soundManager.playPowerUp()
                Toast.makeText(this, "Puzzle shuffled!", Toast.LENGTH_SHORT).show()
            }
        }
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

    /** Step 1: choose difficulty (grid size). */
    private fun showGridSizeDialog() {
        val options = arrayOf("🟢 3 × 3  (Easy)", "🔴 4 × 4  (Hard)")
        MaterialAlertDialogBuilder(this)
            .setTitle("Choose Difficulty")
            .setItems(options) { _, which ->
                gridSize = if (which == 1) 4 else 3
                showPuzzleSelectionDialog()
            }
            .setNegativeButton("Cancel") { _, _ ->
                if (!isGameActive) finish()
            }
            .show()
    }

    /** Step 2: choose the image. */
    private fun showPuzzleSelectionDialog() {
        val options = arrayOf(
            "📷 Take Photo (Camera)",
            "🖼️ Choose from Gallery",
            "🎨 Colorful Number Puzzle",
            "🐱 Cute Cat Puzzle",
            "🌄 Nature Scenery",
            "🚀 Space Adventure"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Puzzle Image (${gridSize}×$gridSize)")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermission()
                    1 -> openGallery()
                    2 -> createNumberPuzzle()
                    3 -> loadPreMadePuzzle(R.drawable.cat_puzzle, "Cat")
                    4 -> loadPreMadePuzzle(R.drawable.nature_puzzle, "Nature")
                    5 -> loadPreMadePuzzle(R.drawable.space_puzzle, "Space")
                }
            }
            .setNegativeButton("Back") { _, _ ->
                showGridSizeDialog()
            }
            .show()
    }

    private fun loadPreMadePuzzle(resId: Int, name: String) {
        try {
            val bitmap = BitmapFactory.decodeResource(resources, resId)
            if (bitmap != null) {
                currentImageBitmap = bitmap
                initializePuzzleWithImage(bitmap)
                Toast.makeText(this, "$name puzzle loaded!", Toast.LENGTH_SHORT).show()
            } else {
                createNumberPuzzle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            createNumberPuzzle()
            Toast.makeText(this, "Using number puzzle instead", Toast.LENGTH_SHORT).show()
        }
    }

    /** Builds a colourful numbered image for any grid size. */
    private fun createNumberPuzzle() {
        val size = 900
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cellSize = size / gridSize
        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                val index = i * gridSize + j
                // Generate a distinct vivid colour per tile via HSV.
                val hue = (index * (360f / tileCount)) % 360f
                val cellColor = Color.HSVToColor(floatArrayOf(hue, 0.65f, 0.95f))
                val paint = Paint().apply {
                    color = cellColor
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawRect(
                    (j * cellSize).toFloat(), (i * cellSize).toFloat(),
                    ((j + 1) * cellSize).toFloat(), ((i + 1) * cellSize).toFloat(),
                    paint
                )
                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = cellSize * 0.35f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }
                canvas.drawText(
                    "${index + 1}",
                    (j * cellSize + cellSize / 2).toFloat(),
                    (i * cellSize + cellSize / 2 + textPaint.textSize / 3f),
                    textPaint
                )
            }
        }

        currentImageBitmap = bitmap
        initializePuzzleWithImage(bitmap)
    }

    private fun initializePuzzleWithImage(bitmap: Bitmap) {
        binding.progressBarLoading.visibility = View.VISIBLE

        // Use a square that divides evenly by the grid to avoid off-by-one slicing.
        val base = 960
        val side = base - (base % gridSize)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, side, side, true)

        val tileWidth = scaledBitmap.width / gridSize
        val tileHeight = scaledBitmap.height / gridSize
        tileBitmaps = arrayOfNulls(tileCount)

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val position = row * gridSize + col
                tileBitmaps[position] = Bitmap.createBitmap(
                    scaledBitmap,
                    col * tileWidth, row * tileHeight,
                    tileWidth, tileHeight
                )
            }
        }

        currentOrder = IntArray(tileCount) { it }
        emptyPosition = emptyTile

        createPuzzleGrid()

        Handler(Looper.getMainLooper()).postDelayed({
            shuffleTiles()
            timeLeft = gameTime
            startTimer()
            isGameActive = true
            isPaused = false
            moves = 0
            updateUI()
            binding.progressBarLoading.visibility = View.GONE
            binding.gameStats.visibility = View.VISIBLE
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
            Toast.makeText(this, "Tap tiles next to the empty space to move them!", Toast.LENGTH_LONG).show()
        }, 100)
    }

    private fun createPuzzleGrid() {
        binding.puzzleGrid.removeAllViews()
        binding.puzzleGrid.columnCount = gridSize
        binding.puzzleGrid.rowCount = gridSize

        val screenWidth = resources.displayMetrics.widthPixels
        val tileSize = (screenWidth - 64) / gridSize

        for (i in 0 until tileCount) {
            val imageView = ImageView(this)
            val layoutParams = GridLayout.LayoutParams()
            layoutParams.width = tileSize
            layoutParams.height = tileSize
            layoutParams.setMargins(2, 2, 2, 2)
            imageView.layoutParams = layoutParams
            imageView.scaleType = ImageView.ScaleType.FIT_XY

            val value = currentOrder[i]
            if (value == emptyTile) {
                imageView.setImageDrawable(null)
                imageView.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_bg))
            } else {
                imageView.setImageBitmap(tileBitmaps.getOrNull(value))
                imageView.setBackgroundColor(0)
            }

            val position = i
            imageView.setOnClickListener {
                if (isGameActive && !isPaused) {
                    onTileClick(position)
                }
            }

            binding.puzzleGrid.addView(imageView)
        }
    }

    private fun onTileClick(clickedPos: Int) {
        if (isAdjacent(clickedPos, emptyPosition)) {
            val temp = currentOrder[clickedPos]
            currentOrder[clickedPos] = currentOrder[emptyPosition]
            currentOrder[emptyPosition] = temp
            emptyPosition = clickedPos
            moves++
            soundManager.playCorrect()

            createPuzzleGrid()
            updateUI()

            if (checkWin()) {
                onGameWin()
            }
        } else {
            soundManager.playWrong()
            Toast.makeText(this, "Tap a tile next to the empty space!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAdjacent(pos1: Int, pos2: Int): Boolean {
        val row1 = pos1 / gridSize
        val col1 = pos1 % gridSize
        val row2 = pos2 / gridSize
        val col2 = pos2 % gridSize
        return (Math.abs(row1 - row2) + Math.abs(col1 - col2)) == 1
    }

    private fun shuffleTiles() {
        // Shuffling by valid adjacent moves guarantees the puzzle stays solvable.
        val iterations = 200 * gridSize
        for (i in 0 until iterations) {
            val adjacents = getAdjacentTiles(emptyPosition)
            if (adjacents.isNotEmpty()) {
                val randomPos = adjacents.random()
                val temp = currentOrder[randomPos]
                currentOrder[randomPos] = currentOrder[emptyPosition]
                currentOrder[emptyPosition] = temp
                emptyPosition = randomPos
            }
        }
        // Make sure we did not land on the solved state.
        if (checkWin()) {
            val adjacents = getAdjacentTiles(emptyPosition)
            if (adjacents.isNotEmpty()) {
                val p = adjacents.random()
                val temp = currentOrder[p]
                currentOrder[p] = currentOrder[emptyPosition]
                currentOrder[emptyPosition] = temp
                emptyPosition = p
            }
        }
        moves = 0
        createPuzzleGrid()
        updateUI()
    }

    private fun getAdjacentTiles(pos: Int): List<Int> {
        val row = pos / gridSize
        val col = pos % gridSize
        val adjacent = mutableListOf<Int>()
        if (row > 0) adjacent.add(pos - gridSize)
        if (row < gridSize - 1) adjacent.add(pos + gridSize)
        if (col > 0) adjacent.add(pos - 1)
        if (col < gridSize - 1) adjacent.add(pos + 1)
        return adjacent
    }

    private fun checkWin(): Boolean {
        for (i in 0 until tileCount) {
            if (currentOrder[i] != i) return false
        }
        return true
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
                }
            }

            override fun onFinish() {
                if (isGameActive && !isPaused && !checkWin()) {
                    onGameLose()
                }
            }
        }.start()
    }

    private fun updateUI() {
        binding.movesText.text = moves.toString()
        val solved = tileCount - getIncorrectCount()
        val progress = solved * 100 / tileCount
        binding.progressBar.progress = progress
    }

    private fun getIncorrectCount(): Int {
        var incorrect = 0
        for (i in 0 until tileCount) {
            if (currentOrder[i] != i) incorrect++
        }
        return incorrect
    }

    private fun calculateScore(): Int {
        // Harder grids are worth more.
        val baseScore = if (gridSize == 4) 2000 else 1000
        val movePenalty = moves * 2
        val timeBonus = ((gameTime - timeLeft) / 1000).toInt() * 10
        return maxOf(100, baseScore - movePenalty + timeBonus)
    }

    private fun onGameWin() {
        isGameActive = false
        timer?.cancel()
        soundManager.playGameOver()

        val score = calculateScore()
        sharedPrefsManager.updateScore("puzzle", score)

        lifecycleScope.launch {
            try {
                val authHelper = AuthHelper(this@PhotoPuzzleActivity)
                val scoreHelper = ScoreHelper()
                val uid = authHelper.getCurrentUid()
                val playerName = authHelper.getPlayerName(uid)
                scoreHelper.saveScore(uid, playerName, "Photo Puzzle", score)
            } catch (e: Exception) {
                android.util.Log.e("PhotoPuzzle", "Failed to save: ${e.message}")
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("🎉 YOU WIN! 🎉")
            .setMessage(
                """
            Congratulations! You solved the ${gridSize}×$gridSize puzzle!
            ⭐ Moves: $moves
            ⏱️ Time: ${(gameTime - timeLeft) / 1000} seconds
            🏆 Score: $score
        """.trimIndent()
            )
            .setPositiveButton("Play Again") { _, _ -> showGridSizeDialog() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun onGameLose() {
        isGameActive = false
        timer?.cancel()
        soundManager.playGameOver()

        MaterialAlertDialogBuilder(this)
            .setTitle("⏰ Time's Up!")
            .setMessage("Want to try again?")
            .setPositiveButton("Try Again") { _, _ ->
                showGridSizeDialog()
            }
            .setNegativeButton("Exit") { _, _ ->
                finish()
            }
            .setCancelable(false)
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

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        } else {
            dispatchTakePictureIntent()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                showPuzzleSelectionDialog()
            }
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_GALLERY_IMAGE)
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    val lastImage = getLatestImageFile()
                    lastImage?.let {
                        val bitmap = BitmapFactory.decodeFile(it.absolutePath)
                        if (bitmap != null) {
                            currentImageBitmap = bitmap
                            initializePuzzleWithImage(bitmap)
                        } else {
                            createNumberPuzzle()
                        }
                    } ?: createNumberPuzzle()
                }
                REQUEST_GALLERY_IMAGE -> {
                    data?.data?.let { uri ->
                        try {
                            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            if (bitmap != null) {
                                currentImageBitmap = bitmap
                                initializePuzzleWithImage(bitmap)
                            } else {
                                createNumberPuzzle()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                            createNumberPuzzle()
                        }
                    }
                }
            }
        }
    }

    private fun getLatestImageFile(): File? {
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return storageDir?.listFiles()?.maxByOrNull { it.lastModified() }
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
        if (isGameActive && isPaused && !checkWin()) {
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
        outState.putInt("gridSize", gridSize)
        outState.putLong("timeLeft", timeLeft)
        outState.putInt("moves", moves)
        outState.putInt("emptyPosition", emptyPosition)
        outState.putBoolean("isGameActive", isGameActive)
        outState.putIntArray("currentOrder", currentOrder)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        gridSize = savedInstanceState.getInt("gridSize", gridSize)
        timeLeft = savedInstanceState.getLong("timeLeft", gameTime)
        moves = savedInstanceState.getInt("moves", 0)
        emptyPosition = savedInstanceState.getInt("emptyPosition", emptyTile)
        isGameActive = savedInstanceState.getBoolean("isGameActive", false)
        savedInstanceState.getIntArray("currentOrder")?.let {
            currentOrder = it
        }
        // Rebuild tiles from the current image if we have one; otherwise wait for selection.
        currentImageBitmap?.let { initializePuzzleWithImage(it) }
    }
}
