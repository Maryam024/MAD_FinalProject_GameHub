package com.example.gamehub.activities

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gamehub.R
import com.example.gamehub.adapters.LeaderboardAdapter
import com.example.gamehub.databinding.ActivityLeaderboardBinding
import com.example.gamehub.utils.GameStatsHelper
import com.example.gamehub.utils.LeaderboardEntry
import kotlinx.coroutines.launch

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private lateinit var adapter: LeaderboardAdapter
    private val games = listOf("Memory Match", "Quiz Activity", "Word Quest", "Photo Puzzle", "Rapid Tap Challenge", "Piano Tiles", "Neon Dash Runner")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupSpinner()
        setupRecyclerView()
    }

    private fun setupSpinner() {
        val spinner: Spinner = binding.gameSpinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, games)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                loadLeaderboard(games[position])
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = LeaderboardAdapter()
        binding.leaderboardRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.leaderboardRecyclerView.adapter = adapter
    }

    private fun loadLeaderboard(gameName: String) {
        lifecycleScope.launch {
            val entries = GameStatsHelper.getGlobalLeaderboard(gameName, 20)
            adapter.submitList(entries)
        }
    }
}