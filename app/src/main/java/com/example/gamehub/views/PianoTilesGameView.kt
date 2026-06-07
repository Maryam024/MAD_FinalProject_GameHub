package com.example.gamehub.views

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * Piano Tiles ("white tiles") game.
 *
 * Four vertical lanes. Each row contains exactly ONE neon tile (the "black"/active
 * tile) and three empty (white) lanes. Tiles scroll downward. The player must tap the
 * lowest un-tapped tile, in order, lane by lane.
 *
 *  - Correct tap  -> tile is consumed, +1 score, note sound, green flash, "+1" popup.
 *  - Wrong tap    -> tapped an empty (white) lane -> that cell flashes RED -> GAME OVER.
 *  - Missed tile  -> a tile scrolls fully off the bottom un-tapped -> GAME OVER.
 *
 * Styled to match the GameHub cyber/neon dark theme.
 */
class PianoTilesGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    // ─── Public callbacks (always invoked on the main thread) ──────────────────
    var onScoreUpdate: ((Int) -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null
    var onCorrectTap: (() -> Unit)? = null
    var onWrongTap: (() -> Unit)? = null

    // ─── Public state flags ────────────────────────────────────────────────────
    @Volatile var isGameActive = true
    @Volatile var isPaused = false

    @Volatile var score = 0
        private set

    // ─── Internal state machine ─────────────────────────────────────────────────
    private val STATE_READY = 0
    private val STATE_RUNNING = 1
    private val STATE_OVER = 2
    @Volatile private var state = STATE_READY

    private val LANES = 4

    // Threading
    private var surfaceHolder: SurfaceHolder? = null
    private var gameThread: Thread? = null
    private var isRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Tiles (only ever mutated on the game thread)
    private val tiles = ArrayList<Tile>()
    private val popups = ArrayList<Popup>()

    // Taps recorded on the UI thread, drained on the game thread
    private val pendingTaps = ArrayDeque<Int>()

    // Geometry
    private val laneWidth get() = width / LANES.toFloat()
    private val tileHeight get() = height / 4f

    // Speed (pixels per second), ramps up with score
    private val baseSpeed get() = height * 0.55f
    private val currentSpeed: Float
        get() = (baseSpeed * (1f + score * 0.03f)).coerceAtMost(baseSpeed * 2.8f)

    // Wrong-tap flash data
    private var wrongLane = -1
    private var wrongTop = 0f
    private var hasWrong = false

    // Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Theme colours
    private val cBg = Color.parseColor("#070b14")
    private val cLaneA = Color.parseColor("#0a1120")
    private val cLaneB = Color.parseColor("#0d1526")
    private val cLaneLine = Color.parseColor("#1a2d4a")
    private val cTileTop = Color.parseColor("#00aaff")
    private val cTileBottom = Color.parseColor("#005aff")
    private val cTapped = Color.parseColor("#0d1526")
    private val cTappedBorder = Color.parseColor("#1a2d4a")
    private val cSuccess = Color.parseColor("#00dd88")
    private val cDanger = Color.parseColor("#ff4444")
    private val cText = Color.parseColor("#e0e8ff")
    private val cTextMuted = Color.parseColor("#3a5880")

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ─── Surface lifecycle ──────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceHolder = holder
        if (tiles.isEmpty()) buildInitialTiles()
        start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
        // Rebuild for the (now known) size if we have not really started yet.
        if (state == STATE_READY) buildInitialTiles()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stop()
        surfaceHolder = null
    }

    private fun start() {
        if (!isRunning) {
            isRunning = true
            gameThread = Thread(this)
            gameThread?.start()
        }
    }

    private fun stop() {
        isRunning = false
        gameThread?.interrupt()
        gameThread = null
    }

    // ─── Tile generation ─────────────────────────────────────────────────────────

    private fun buildInitialTiles() {
        if (width == 0 || height == 0) return
        tiles.clear()
        popups.clear()
        // Fill from the bottom row upward, one tile above the screen.
        var top = height - tileHeight
        var lastLane = -1
        while (top > -tileHeight) {
            var lane = Random.nextInt(LANES)
            if (lane == lastLane) lane = (lane + 1) % LANES  // avoid visual "double" tile
            tiles.add(Tile(lane, top))
            lastLane = lane
            top -= tileHeight
        }
        // ensure sorted top-to-bottom (small first)
        tiles.sortBy { it.top }
    }

    private fun spawnIfNeeded() {
        // Highest tile = smallest top. Keep filling above the top of the screen.
        val highest = tiles.minByOrNull { it.top } ?: return
        var top = highest.top
        var lastLane = highest.lane
        while (top > -tileHeight) {
            top -= tileHeight
            var lane = Random.nextInt(LANES)
            if (lane == lastLane) lane = (lane + 1) % LANES
            tiles.add(Tile(lane, top))
            lastLane = lane
        }
    }

    /** The lowest still-untapped tile (largest top value). */
    private fun lowestUntapped(): Tile? =
        tiles.filter { !it.tapped }.maxByOrNull { it.top }

    // ─── Game loop ────────────────────────────────────────────────────────────────

    override fun run() {
        var lastTime = System.nanoTime()
        while (isRunning) {
            val now = System.nanoTime()
            val delta = (now - lastTime) / 1_000_000_000f
            lastTime = now

            if (!isPaused && state != STATE_OVER && delta < 0.05f) {
                processTaps()
                if (state == STATE_RUNNING) updateGame(delta)
                updatePopups(delta)
            }
            drawFrame()

            try {
                Thread.sleep(16)   // ~60 FPS
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    private fun processTaps() {
        while (true) {
            val lane: Int = synchronized(pendingTaps) {
                if (pendingTaps.isEmpty()) return else pendingTaps.poll()
            } ?: return

            val target = lowestUntapped() ?: continue

            if (lane == target.lane) {
                // Correct tile
                target.tapped = true
                score += 1
                mainHandler.post {
                    onScoreUpdate?.invoke(score)
                    onCorrectTap?.invoke()
                }
                popups.add(
                    Popup(
                        x = target.lane * laneWidth + laneWidth / 2f,
                        y = target.top + tileHeight / 2f
                    )
                )
                if (state == STATE_READY) state = STATE_RUNNING
            } else {
                // Tapped an empty (white) lane -> wrong
                wrongLane = lane
                wrongTop = target.top
                hasWrong = true
                gameOver()
                return
            }
        }
    }

    private fun updateGame(delta: Float) {
        val dy = currentSpeed * delta
        for (t in tiles) t.top += dy

        // Miss: lowest untapped tile fully left the screen
        val target = lowestUntapped()
        if (target != null && target.top > height) {
            wrongLane = target.lane
            wrongTop = height - tileHeight
            hasWrong = true
            gameOver()
            return
        }

        // Recycle tiles that scrolled off the bottom, then top up the column
        tiles.removeAll { it.top > height + tileHeight }
        spawnIfNeeded()
    }

    private fun updatePopups(delta: Float) {
        val it = popups.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += delta
            p.y -= delta * height * 0.15f
            if (p.age > 0.7f) it.remove()
        }
    }

    private fun gameOver() {
        if (state == STATE_OVER) return
        state = STATE_OVER
        isGameActive = false
        mainHandler.post {
            onWrongTap?.invoke()
            onGameOver?.invoke(score)
        }
    }

    // ─── Drawing ────────────────────────────────────────────────────────────────

    private fun drawFrame() {
        val holder = surfaceHolder ?: return
        if (width == 0 || height == 0) return
        val canvas = holder.lockCanvas() ?: return
        try {
            canvas.drawColor(cBg)

            // Lane backgrounds (subtle alternating shade) + separators
            paint.style = Paint.Style.FILL
            for (i in 0 until LANES) {
                paint.color = if (i % 2 == 0) cLaneA else cLaneB
                canvas.drawRect(i * laneWidth, 0f, (i + 1) * laneWidth, height.toFloat(), paint)
            }
            paint.color = cLaneLine
            paint.strokeWidth = 2f
            for (i in 1 until LANES) {
                canvas.drawLine(i * laneWidth, 0f, i * laneWidth, height.toFloat(), paint)
            }

            // Tiles
            val pad = laneWidth * 0.06f
            val target = lowestUntapped()
            for (t in tiles) {
                val left = t.lane * laneWidth + pad
                val right = (t.lane + 1) * laneWidth - pad
                val top = t.top + pad
                val bottom = t.top + tileHeight - pad
                val rect = RectF(left, top, right, bottom)

                if (t.tapped) {
                    // Consumed tile — dim with a soft border
                    paint.style = Paint.Style.FILL
                    paint.color = cTapped
                    canvas.drawRoundRect(rect, 14f, 14f, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = cTappedBorder
                    canvas.drawRoundRect(rect, 14f, 14f, paint)
                    paint.style = Paint.Style.FILL
                } else {
                    // Active neon tile with vertical gradient + glow
                    val isTarget = t === target
                    paint.style = Paint.Style.FILL
                    paint.shader = LinearGradient(
                        0f, top, 0f, bottom,
                        cTileTop, cTileBottom, Shader.TileMode.CLAMP
                    )
                    canvas.drawRoundRect(rect, 14f, 14f, paint)
                    paint.shader = null

                    // Inner highlight
                    paint.color = Color.argb(60, 255, 255, 255)
                    canvas.drawRoundRect(
                        RectF(left + 6f, top + 6f, right - 6f, top + tileHeight * 0.32f),
                        10f, 10f, paint
                    )

                    // Outline (brighter for the current target)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = if (isTarget) 5f else 2f
                    paint.color = if (isTarget) cSuccess else Color.argb(120, 0, 170, 255)
                    canvas.drawRoundRect(rect, 14f, 14f, paint)
                    paint.style = Paint.Style.FILL
                }
            }

            // Wrong-tap red flash
            if (hasWrong && wrongLane in 0 until LANES) {
                val left = wrongLane * laneWidth + pad
                val right = (wrongLane + 1) * laneWidth - pad
                val rect = RectF(left, wrongTop + pad, right, wrongTop + tileHeight - pad)
                paint.style = Paint.Style.FILL
                paint.color = cDanger
                canvas.drawRoundRect(rect, 14f, 14f, paint)
                paint.color = Color.WHITE
                textPaint.textSize = tileHeight * 0.5f
                textPaint.color = Color.WHITE
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText("✕", rect.centerX(), rect.centerY() + textPaint.textSize / 3f, textPaint)
            }

            // Floating "+1" popups
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = tileHeight * 0.28f
            for (p in popups) {
                val alpha = (255 * (1f - p.age / 0.7f)).toInt().coerceIn(0, 255)
                textPaint.color = Color.argb(alpha, 0, 221, 136)
                canvas.drawText("+1", p.x, p.y, textPaint)
            }

            // READY overlay
            if (state == STATE_READY) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(150, 7, 11, 20)
                canvas.drawRect(0f, 0f, width.toFloat(), height * 0.5f, paint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = cText
                textPaint.textSize = width * 0.07f
                canvas.drawText("PIANO TILES", width / 2f, height * 0.20f, textPaint)
                textPaint.color = cTextMuted
                textPaint.textSize = width * 0.045f
                canvas.drawText("Tap the lit tile to start", width / 2f, height * 0.27f, textPaint)
                textPaint.color = cSuccess
                textPaint.textSize = width * 0.04f
                canvas.drawText("▼ tap the bottom tile ▼", width / 2f, height * 0.34f, textPaint)
            }

            // Pause overlay
            if (isPaused && state == STATE_RUNNING) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(190, 7, 11, 20)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = cText
                textPaint.textSize = width * 0.09f
                canvas.drawText("⏸ PAUSED", width / 2f, height / 2f, textPaint)
                textPaint.color = cTextMuted
                textPaint.textSize = width * 0.04f
                canvas.drawText("tap pause to resume", width / 2f, height / 2f + width * 0.08f, textPaint)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    // ─── Touch ────────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state == STATE_OVER || isPaused) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
        ) {
            val idx = event.actionIndex
            val x = event.getX(idx)
            if (laneWidth <= 0f) return true
            val lane = (x / laneWidth).toInt().coerceIn(0, LANES - 1)
            synchronized(pendingTaps) { pendingTaps.add(lane) }
            return true
        }
        return super.onTouchEvent(event)
    }

    // ─── Public API ────────────────────────────────────────────────────────────────

    fun resetGame() {
        score = 0
        hasWrong = false
        wrongLane = -1
        state = STATE_READY
        isGameActive = true
        isPaused = false
        buildInitialTiles()
        mainHandler.post { onScoreUpdate?.invoke(0) }
    }

    fun pauseGame() { isPaused = true }
    fun resumeGame() { isPaused = false }

    // ─── Data ───────────────────────────────────────────────────────────────────────

    data class Tile(val lane: Int, var top: Float, var tapped: Boolean = false)
    data class Popup(val x: Float, var y: Float, var age: Float = 0f)
}
