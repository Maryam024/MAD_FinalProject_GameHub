package com.example.gamehub.views

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

/**
 * Neon Enemy – grid-based game where the player moves freely on a grid,
 * collects coins (+10), dodges enemies (+20 for successfully passing one),
 * and loses a life when hit.
 */
class NeonDashGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    private var surfaceHolder: SurfaceHolder? = null
    private var gameThread: Thread? = null
    private var isRunning = false

    var isGameActive = true
    var isPaused = false

    @Volatile var score = 0
        private set

    var onScoreUpdate: ((Int) -> Unit)? = null
    var onTimeUpdate: ((Int) -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null
    var onCollectCoin: (() -> Unit)? = null
    var onHitObstacle: (() -> Unit)? = null

    // ─── Grid setup ───────────────────────────────────────────────────────────
    private val COLS = 7
    private val ROWS = 9

    private var cellW = 0f
    private var cellH = 0f

    // Player grid position
    private var playerCol = COLS / 2
    private var playerRow = ROWS - 1

    // Enemies: each has a grid col, a continuous Y position, and speed
    private data class Enemy(
        val col: Int,
        var yPx: Float,          // continuous pixel Y (top of cell)
        val speedPx: Float,      // pixels per second downward
        var passed: Boolean = false  // true once it scrolled past player row
    )

    private val enemies = mutableListOf<Enemy>()

    // Coins: grid col + continuous Y
    private data class Coin(val col: Int, var yPx: Float, val speedPx: Float)
    private val coins = mutableListOf<Coin>()

    private val popups = mutableListOf<PopupData>()

    // Game timing
    private var gameStartTime = 0L
    private var lastEnemySpawn = 0L
    private var lastCoinSpawn = 0L
    private var currentSpeed = 220f   // px/sec, ramps up over time

    // Lives
    private var lives = 3
    private var invincibleUntil = 0L  // brief invincibility after hit

    // Swipe tracking
    private var touchStartX = 0f
    private var touchStartY = 0f

    // Parallax background
    private val farLayer = mutableListOf<RectF>()
    private val starLayer = mutableListOf<FloatArray>()

    // Paint + colours
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val darkBg    = Color.parseColor("#070b14")
    private val bgFar     = Color.parseColor("#0d1526")
    private val neonGreen  = Color.parseColor("#00dd88")
    private val neonPink   = Color.parseColor("#ff3366")
    private val neonYellow = Color.parseColor("#ffcc00")
    private val neonBlue   = Color.parseColor("#00aaff")
    private val neonPurple = Color.parseColor("#a855f7")

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ─── Surface lifecycle ────────────────────────────────────────────────────

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceHolder = holder
        startGame()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        recalcCells()
        if (farLayer.isEmpty()) buildBackground()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopGame()
        surfaceHolder = null
    }

    private fun startGame() {
        if (!isRunning) {
            isRunning = true
            gameThread = Thread(this)
            gameThread?.start()
        }
    }

    private fun stopGame() {
        isRunning = false
        gameThread?.interrupt()
        gameThread = null
    }

    private fun recalcCells() {
        if (width == 0 || height == 0) return
        cellW = width.toFloat() / COLS
        cellH = height.toFloat() / ROWS
    }

    private fun buildBackground() {
        if (width == 0 || height == 0) return
        farLayer.clear(); starLayer.clear()
        var x = 0f
        while (x < width * 2f) {
            val w = Random.nextInt(40, 110).toFloat()
            val h = Random.nextInt(60, 220).toFloat()
            farLayer.add(RectF(x, height - 130f - h, x + w, height - 130f))
            x += w + Random.nextInt(30, 90)
        }
        repeat(40) {
            starLayer.add(floatArrayOf(
                Random.nextInt(0, width * 2).toFloat(),
                Random.nextInt(0, (height * 0.6f).toInt()).toFloat(),
                Random.nextInt(1, 4).toFloat()
            ))
        }
    }

    // ─── Game loop ────────────────────────────────────────────────────────────

    override fun run() {
        var lastTime = System.nanoTime()
        while (isRunning) {
            val now = System.nanoTime()
            val delta = (now - lastTime) / 1_000_000_000f
            lastTime = now
            if (!isPaused && isGameActive && delta < 0.05f) updateGame(delta)
            drawFrame()
            try { Thread.sleep(16) } catch (e: InterruptedException) { break }
        }
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    private fun updateGame(delta: Float) {
        if (width == 0 || height == 0) return
        val now = System.currentTimeMillis()

        if (cellW == 0f) recalcCells()
        if (farLayer.isEmpty()) buildBackground()
        if (gameStartTime == 0L) gameStartTime = now

        val elapsed = now - gameStartTime
        mainHandler.post { onTimeUpdate?.invoke((elapsed / 1000).toInt()) }

        // Speed ramp
        currentSpeed = (220f + elapsed / 8000f).coerceAtMost(480f)

        // Scroll background
        val pxStep = currentSpeed * delta * 0.15f
        for (r in farLayer) { r.offset(-pxStep, 0f); if (r.right < 0) r.offset(width * 2f, 0f) }
        for (s in starLayer) { s[0] -= pxStep * 0.6f; if (s[0] < 0) s[0] = width * 2f }

        // ── Spawn enemies ──
        val spawnGap = (1400L - (elapsed / 40).toLong()).coerceAtLeast(600L)
        if (now - lastEnemySpawn > spawnGap) {
            val col = Random.nextInt(COLS)
            enemies.add(Enemy(col, -cellH, currentSpeed))
            lastEnemySpawn = now
        }

        // ── Spawn coins ──
        if (now - lastCoinSpawn > Random.nextLong(900, 1600)) {
            val col = Random.nextInt(COLS)
            coins.add(Coin(col, -cellH, currentSpeed * 0.8f))
            lastCoinSpawn = now
        }

        val invincible = now < invincibleUntil

        // ── Move enemies ──
        val playerTopPx = playerRow * cellH
        val playerBotPx = playerTopPx + cellH

        val eIt = enemies.iterator()
        while (eIt.hasNext()) {
            val e = eIt.next()
            e.yPx += currentSpeed * delta

            // Award dodge bonus when enemy scrolls past player row without hitting
            if (!e.passed && e.yPx > playerBotPx) {
                e.passed = true
                if (e.col != playerCol) {
                    // Enemy was in a different column — player successfully dodged
                    score += 20
                    mainHandler.post { onScoreUpdate?.invoke(score) }
                    popups.add(PopupData(e.col * cellW + cellW / 2, playerTopPx, "+20"))
                }
            }

            if (e.yPx > height + cellH) { eIt.remove(); continue }

            // Collision: same column, overlapping Y, not invincible
            if (!invincible && e.col == playerCol) {
                val eTop = e.yPx
                val eBot = e.yPx + cellH * 0.8f
                if (eTop < playerBotPx && eBot > playerTopPx) {
                    mainHandler.post { onHitObstacle?.invoke() }
                    lives--
                    invincibleUntil = now + 1200L
                    eIt.remove()
                    if (lives <= 0) { gameOver(); return }
                    continue
                }
            }
        }

        // ── Move coins ──
        val cIt = coins.iterator()
        while (cIt.hasNext()) {
            val c = cIt.next()
            c.yPx += c.speedPx * delta
            if (c.yPx > height + cellH) { cIt.remove(); continue }

            if (c.col == playerCol) {
                val cTop = c.yPx
                val cBot = c.yPx + cellH * 0.8f
                val pTop = playerRow * cellH
                val pBot = pTop + cellH
                if (cTop < pBot && cBot > pTop) {
                    mainHandler.post { onCollectCoin?.invoke() }
                    score += 10
                    mainHandler.post { onScoreUpdate?.invoke(score) }
                    popups.add(PopupData(c.col * cellW + cellW / 2, pTop, "+10"))
                    cIt.remove()
                }
            }
        }

        // Popups
        val pIt = popups.iterator()
        while (pIt.hasNext()) {
            val p = pIt.next()
            p.age += delta
            p.y -= delta * 90f
            if (p.age > 0.9f) pIt.remove()
        }
    }

    private fun gameOver() {
        isGameActive = false
        mainHandler.post { onGameOver?.invoke(score) }
    }

    // ─── Drawing ──────────────────────────────────────────────────────────────

    private fun drawFrame() {
        val h = surfaceHolder ?: return
        if (width == 0 || height == 0) return
        val canvas = h.lockCanvas() ?: return
        try {
            canvas.drawColor(darkBg)

            // Stars
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(160, 0, 170, 255)
            for (s in starLayer) canvas.drawCircle(s[0], s[1], s[2], paint)

            // Far skyline
            paint.color = bgFar
            for (r in farLayer) canvas.drawRect(r, paint)

            // Grid lines
            if (cellW > 0f && cellH > 0f) {
                paint.color = Color.argb(25, 0, 221, 136)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                for (c in 0..COLS) canvas.drawLine(c * cellW, 0f, c * cellW, height.toFloat(), paint)
                for (r in 0..ROWS) canvas.drawLine(0f, r * cellH, width.toFloat(), r * cellH, paint)
                paint.style = Paint.Style.FILL
            }

            // Coins
            for (coin in coins) {
                val cx = coin.col * cellW + cellW / 2
                val cy = coin.yPx + cellH * 0.4f
                val r  = cellW * 0.28f
                paint.color = neonYellow
                canvas.drawCircle(cx, cy, r, paint)
                paint.color = Color.parseColor("#cc9900")
                canvas.drawCircle(cx, cy, r - 5f, paint)
                paint.color = Color.WHITE
                paint.textSize = (r * 0.9f).coerceAtLeast(12f)
                val tw = paint.measureText("$")
                canvas.drawText("$", cx - tw / 2, cy + paint.textSize * 0.35f, paint)
            }

            // Enemies
            for (e in enemies) drawEnemy(canvas, e)

            // Player
            if (cellW > 0f) drawPlayer(canvas)

            // Popups
            for (p in popups) {
                val alpha = (255 * (1f - p.age / 0.9f)).toInt().coerceIn(0, 255)
                paint.color = Color.argb(alpha, 255, 204, 0)
                paint.textSize = 34f
                canvas.drawText(p.text, p.x - paint.measureText(p.text) / 2, p.y, paint)
            }

            // Lives HUD
            paint.color = neonPink
            paint.textSize = 18f
            val livesText = "♥ ".repeat(lives)
            canvas.drawText(livesText, 20f, 36f, paint)

            // Pause overlay
            if (isPaused) {
                paint.color = Color.BLACK; paint.alpha = 180
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.color = Color.WHITE; paint.textSize = 52f; paint.alpha = 255
                val text = "⏸  PAUSED"
                canvas.drawText(text, width / 2f - paint.measureText(text) / 2, height / 2f, paint)
                paint.textSize = 22f; paint.alpha = 180
                val sub = "tap pause button to resume"
                canvas.drawText(sub, width / 2f - paint.measureText(sub) / 2, height / 2f + 50f, paint)
                paint.alpha = 255
            }

            // Hint at bottom
            paint.color = Color.WHITE; paint.textSize = 13f; paint.alpha = 120
            canvas.drawText("SWIPE LEFT/RIGHT/UP/DOWN to move", 20f, height - 14f, paint)
            paint.alpha = 255

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            h.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawEnemy(canvas: Canvas, e: Enemy) {
        if (cellW == 0f) return
        val now = System.currentTimeMillis()
        val invincible = now < invincibleUntil

        val margin = cellW * 0.08f
        val left  = e.col * cellW + margin
        val right = (e.col + 1) * cellW - margin
        val top   = e.yPx + margin
        val bot   = e.yPx + cellH * 0.85f - margin
        val rect  = RectF(left, top, right, bot)
        val cx = (left + right) / 2f
        val cy = (top + bot) / 2f

        // Glow
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(if (invincible) 30 else 70, 255, 51, 102)
        canvas.drawRoundRect(left - 6f, top - 6f, right + 6f, bot + 6f, 14f, 14f, paint)
        // Body
        paint.color = if (invincible) Color.argb(140, 255, 51, 102) else neonPink
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        // Inner
        paint.color = Color.parseColor("#cc0033")
        canvas.drawRoundRect(left + 8f, top + 8f, right - 8f, bot - 8f, 8f, 8f, paint)
        // Eyes
        paint.color = Color.WHITE
        val eyeY = cy - cellH * 0.1f
        val eyeR = cellW * 0.1f
        canvas.drawCircle(cx - cellW * 0.18f, eyeY, eyeR, paint)
        canvas.drawCircle(cx + cellW * 0.18f, eyeY, eyeR, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(cx - cellW * 0.16f, eyeY, eyeR * 0.55f, paint)
        canvas.drawCircle(cx + cellW * 0.2f, eyeY, eyeR * 0.55f, paint)
    }

    private fun drawPlayer(canvas: Canvas) {
        if (cellW == 0f) return
        val now = System.currentTimeMillis()
        val invincible = now < invincibleUntil
        val blink = invincible && (now / 150) % 2 == 0L  // blink when invincible

        val margin = cellW * 0.1f
        val left  = playerCol * cellW + margin
        val right = (playerCol + 1) * cellW - margin
        val top   = playerRow * cellH + margin
        val bot   = (playerRow + 1) * cellH - margin
        val rect  = RectF(left, top, right, bot)
        val cx = (left + right) / 2f
        val cy = (top + bot) / 2f

        if (!blink) {
            // Glow
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(80, 0, 221, 136)
            canvas.drawRoundRect(left - 8f, top - 8f, right + 8f, bot + 8f, 20f, 20f, paint)
            // Body
            paint.color = neonGreen
            canvas.drawRoundRect(rect, 16f, 16f, paint)
            // Eyes
            paint.color = Color.WHITE
            val eyeR = cellW * 0.1f
            val eyeY = cy - cellH * 0.08f
            canvas.drawCircle(cx - cellW * 0.17f, eyeY, eyeR, paint)
            canvas.drawCircle(cx + cellW * 0.17f, eyeY, eyeR, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(cx - cellW * 0.15f, eyeY, eyeR * 0.55f, paint)
            canvas.drawCircle(cx + cellW * 0.19f, eyeY, eyeR * 0.55f, paint)
        } else {
            // Transparent blink during invincibility
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(80, 0, 221, 136)
            canvas.drawRoundRect(rect, 16f, 16f, paint)
        }
    }

    // ─── Touch input (swipe to move on grid) ─────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isGameActive || isPaused) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                val minSwipe = 40f
                if (Math.abs(dx) < minSwipe && Math.abs(dy) < minSwipe) return true // tap - ignore
                if (Math.abs(dx) > Math.abs(dy)) {
                    // Horizontal swipe
                    if (dx > 0) playerCol = (playerCol + 1).coerceAtMost(COLS - 1)
                    else        playerCol = (playerCol - 1).coerceAtLeast(0)
                } else {
                    // Vertical swipe
                    if (dy > 0) playerRow = (playerRow + 1).coerceAtMost(ROWS - 1)
                    else        playerRow = (playerRow - 1).coerceAtLeast(0)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun resetGame() {
        isGameActive = true
        isPaused = false
        score = 0
        lives = 3
        currentSpeed = 220f
        enemies.clear()
        coins.clear()
        popups.clear()
        playerCol = COLS / 2
        playerRow = ROWS - 1
        invincibleUntil = 0L
        gameStartTime = 0L
        lastEnemySpawn = 0L
        lastCoinSpawn = 0L
        recalcCells()
        buildBackground()
        mainHandler.post { onScoreUpdate?.invoke(0); onTimeUpdate?.invoke(0) }
    }

    fun pauseGame()  { isPaused = true }
    fun resumeGame() { isPaused = false }

    // ─── Data ─────────────────────────────────────────────────────────────────
    data class PopupData(val x: Float, var y: Float, val text: String, var age: Float = 0f)
}
