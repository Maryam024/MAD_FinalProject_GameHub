package com.example.gamehub.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.gamehub.R
import com.example.gamehub.databinding.ActivityStatsBinding
import com.example.gamehub.utils.GameStatsHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadStatistics()
    }

    private fun loadStatistics() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        lifecycleScope.launch {
            // Memory Match
            val memoryLast = GameStatsHelper.getLastScore(uid, "Memory Match")
            val memoryBest = GameStatsHelper.getBestScore(uid, "Memory Match")
            binding.memoryLastScore.text = memoryLast.toString()
            binding.memoryBestScore.text = memoryBest.toString()

            // Quiz Activity
            val quizLast = GameStatsHelper.getLastScore(uid, "Quiz Activity")
            val quizBest = GameStatsHelper.getBestScore(uid, "Quiz Activity")
            binding.quizLastScore.text = quizLast.toString()
            binding.quizBestScore.text = quizBest.toString()

            // Word Quest
            val wordLast = GameStatsHelper.getLastScore(uid, "Word Quest")
            val wordBest = GameStatsHelper.getBestScore(uid, "Word Quest")
            binding.wordLastScore.text = wordLast.toString()
            binding.wordBestScore.text = wordBest.toString()

            // Photo Puzzle
            val puzzleLast = GameStatsHelper.getLastScore(uid, "Photo Puzzle")
            val puzzleBest = GameStatsHelper.getBestScore(uid, "Photo Puzzle")
            binding.puzzleLastScore.text = puzzleLast.toString()
            binding.puzzleBestScore.text = puzzleBest.toString()

            // Rapid Tap Challenge
            val rapidTapLast = GameStatsHelper.getLastScore(uid, "Rapid Tap Challenge")
            val rapidTapBest = GameStatsHelper.getBestScore(uid, "Rapid Tap Challenge")
            binding.rapidTapLastScore.text = rapidTapLast.toString()
            binding.rapidTapBestScore.text = rapidTapBest.toString()

            // Piano Game
            val pianoLast = GameStatsHelper.getLastScore(uid, "Piano Tiles")  // Changed from "Piano"
            val pianoBest = GameStatsHelper.getBestScore(uid, "Piano Tiles")  // Changed from "Piano"
            binding.pianoLastScore.text = pianoLast.toString()
            binding.pianoBestScore.text = pianoBest.toString()

            // Neon Dash Runner
            val neonLast = GameStatsHelper.getLastScore(uid, "Neon Dash Runner")
            val neonBest = GameStatsHelper.getBestScore(uid, "Neon Dash Runner")
            binding.neonLastScore.text = neonLast.toString()
            binding.neonBestScore.text = neonBest.toString()

        }
    }
}