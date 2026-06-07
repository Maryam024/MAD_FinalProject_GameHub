package com.example.gamehub.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Vibrator
import com.example.gamehub.R

class SoundManager(private val context: Context) {
    private var soundPool: SoundPool
    private var correctSound: Int = 0
    private var wrongSound: Int = 0
    private var powerUpSound: Int = 0
    private var gameOverSound: Int = 0
    private var bgMusic: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isSoundLoaded = false

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        soundPool.setOnLoadCompleteListener { _, _, _ ->
            isSoundLoaded = true
        }

        loadSounds()
    }

    private fun loadSounds() {
        try {
            correctSound = soundPool.load(context, R.raw.correct, 1)
            wrongSound = soundPool.load(context, R.raw.wrong, 1)
            powerUpSound = soundPool.load(context, R.raw.powerup, 1)
            gameOverSound = soundPool.load(context, R.raw.gameover, 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playCorrect() {
        try {
            if (isSoundLoaded && correctSound != 0) {
                soundPool.play(correctSound, 0.8f, 0.8f, 1, 0, 1f)
            }
            vibrate(50)
        } catch (e: Exception) {
            vibrate(50) // Fallback to vibration
        }
    }

    fun playWrong() {
        try {
            if (isSoundLoaded && wrongSound != 0) {
                soundPool.play(wrongSound, 0.8f, 0.8f, 1, 0, 1f)
            }
            vibrate(100)
        } catch (e: Exception) {
            vibrate(100)
        }
    }

    fun playPowerUp() {
        try {
            if (isSoundLoaded && powerUpSound != 0) {
                soundPool.play(powerUpSound, 0.9f, 0.9f, 1, 0, 1f)
            }
            vibrate(30)
        } catch (e: Exception) {
            vibrate(30)
        }
    }

    fun playGameOver() {
        try {
            if (isSoundLoaded && gameOverSound != 0) {
                soundPool.play(gameOverSound, 1f, 1f, 1, 0, 1f)
            }
            vibrate(200)
        } catch (e: Exception) {
            vibrate(200)
        }
    }

    fun startBgMusic() {
        try {
            bgMusic?.release()
            bgMusic = MediaPlayer.create(context, R.raw.background)
            bgMusic?.isLooping = true
            bgMusic?.setVolume(0.4f, 0.4f)
            bgMusic?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopBgMusic() {
        try {
            bgMusic?.stop()
            bgMusic?.release()
            bgMusic = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pauseBgMusic() {
        try {
            bgMusic?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resumeBgMusic() {
        try {
            bgMusic?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun vibrate(duration: Long) {
        try {
            vibrator?.vibrate(duration)
        } catch (e: Exception) {
            // Silently fail
        }
    }

    fun release() {
        try {
            soundPool.release()
            stopBgMusic()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}