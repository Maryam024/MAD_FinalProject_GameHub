package com.example.gamehub.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gamehub.R
import com.example.gamehub.adapters.GameAdapter
import com.example.gamehub.databinding.ActivityMainBinding
import com.example.gamehub.models.Game
import com.example.gamehub.utils.SharedPrefsManager
import com.example.gamehub.utils.AuthHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import com.example.gamehub.activities.LeaderboardActivity
import com.example.gamehub.activities.StatsActivity


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefsManager: SharedPrefsManager

    private val games = listOf(
        Game(1, "Rapid Fire Trivia", "Answer before time runs out",
            android.R.drawable.ic_menu_edit, R.color.purple_500),
        Game(2, "Memory Match", "Find matching pairs fast",
            android.R.drawable.ic_menu_help, R.color.teal_200),
        Game(3, "Word Quest", "Unscramble the mystery word",
            android.R.drawable.ic_menu_camera, R.color.accent),
        Game(4, "Photo Puzzle", "Slide & reconstruct the image",
            android.R.drawable.ic_menu_camera, R.color.accent),
        Game(5, "Rapid Tap Challenge", "Tap circles before they disappear!",
            android.R.drawable.ic_menu_camera, R.color.accent),
        Game(6, "Piano Tiles", "Tap the rhythm, don't miss a beat!",
            android.R.drawable.ic_menu_camera, R.color.accent),
        Game(7, "Neon Dash Runner", "Endless runner – tap to jump",
            android.R.drawable.ic_menu_camera, R.color.accent)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            sharedPrefsManager = SharedPrefsManager(this)

            setupToolbar()
            setupNavigationDrawer()
            setupRecyclerView()
            binding.btnStats.setOnClickListener {
                startActivity(Intent(this, StatsActivity::class.java))
            }

            binding.btnLeaderboard.setOnClickListener {
                startActivity(Intent(this, LeaderboardActivity::class.java))
            }
            updateWelcomeMessage()

            // Initialize user
            lifecycleScope.launch {
                try {
                    val authHelper = AuthHelper(this@MainActivity)

                    if (!authHelper.isAuthenticated()) {
                        val uid = authHelper.signInAnonymously()
                        val existingName = authHelper.getPlayerName(uid)

                        if (existingName.isEmpty()) {
                            showNameDialog(authHelper, uid)
                        } else {
                            sharedPrefsManager.savePlayerName(existingName)
                            updateWelcomeMessage()
                        }
                    } else {
                        val uid = authHelper.getCurrentUid()
                        val name = authHelper.getPlayerName(uid)
                        if (name.isNotEmpty()) {
                            sharedPrefsManager.savePlayerName(name)
                            updateWelcomeMessage()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showNameDialog(authHelper: AuthHelper, uid: String) {
        val input = android.widget.EditText(this)
        input.hint = "Enter your nickname"

        MaterialAlertDialogBuilder(this)
            .setTitle("Welcome to GameHub!")
            .setMessage("Choose your player name:")
            .setView(input)
            .setPositiveButton("Start") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && name.length >= 3) {
                    lifecycleScope.launch {
                        authHelper.createUser(uid, name)
                        sharedPrefsManager.savePlayerName(name)
                        updateWelcomeMessage()

                        val headerView = binding.navigationView.getHeaderView(0)
                        val playerNameView = headerView.findViewById<android.widget.TextView>(R.id.playerName)
                        playerNameView?.text = name
                    }
                } else {
                    Toast.makeText(this, "Name must be at least 3 characters", Toast.LENGTH_SHORT).show()
                    showNameDialog(authHelper, uid)
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun setupToolbar() {
        try {
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu_cyber)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupNavigationDrawer() {
        try {
            val headerView = binding.navigationView.getHeaderView(0)
            val playerNameView = headerView.findViewById<android.widget.TextView>(R.id.playerName)
            val playerScoreView = headerView.findViewById<android.widget.TextView>(R.id.playerScore)
            val playerAvatarView = headerView.findViewById<android.widget.ImageView>(R.id.playerAvatar)

            val playerName = sharedPrefsManager.getPlayerName()
            val playerScore = sharedPrefsManager.getTotalScore()

            playerNameView?.text = playerName
            playerScoreView?.text = "Score: $playerScore"

            try {
                playerAvatarView?.setImageResource(R.drawable.avatar_default)
            } catch (e: Exception) {
                playerAvatarView?.setBackgroundColor(resources.getColor(R.color.accent))
            }

            binding.navigationView.setNavigationItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_home -> {
                        binding.drawerLayout.closeDrawers()
                    }
                    R.id.nav_profile -> {
                        showEditProfileDialog()
                    }
                    R.id.nav_about -> {
                        showAboutDialog()
                    }
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateWelcomeMessage() {
        try {
            val playerName = sharedPrefsManager.getPlayerName()
            val welcomeTextView = binding.root.findViewById<android.widget.TextView>(R.id.welcomeNameText)
            welcomeTextView?.text = playerName.uppercase()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showEditProfileDialog() {
        try {
            val input = android.widget.EditText(this)
            input.setText(sharedPrefsManager.getPlayerName())
            input.hint = "Enter your name"

            MaterialAlertDialogBuilder(this)
                .setTitle("Edit Profile")
                .setMessage("Enter your new name")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        sharedPrefsManager.savePlayerName(name)
                        val uid = FirebaseAuth.getInstance().currentUser?.uid

                        if (uid != null) {
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .update("playerName", name)
                        }
                        updateWelcomeMessage()

                        val headerView = binding.navigationView.getHeaderView(0)
                        val playerNameView = headerView.findViewById<android.widget.TextView>(R.id.playerName)
                        playerNameView?.text = name

                        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("About GameHub")
            .setMessage("""
            GameHub v1.0
            
            A collection of memory and skill-based mini-games.
            
            FEATURES:
            • 🧠 Memory Match
            • ⚡ Rapid Fire Trivia
            • 🔤 Word Quest
            • 🖼️ Photo Puzzle
            • 👆 Rapid Tap Challenge
            • 🎹 Piano Tiles
            • 🏃 Neon Dash Runner
            
            All games save your scores to the cloud!
            Track your progress in Statistics & Leaderboards.
            
            Created with ❤️ for Android Development
        """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupRecyclerView() {
        try {
            val adapter = GameAdapter(games) { game ->
                when (game.id) {
                    1 -> startActivity(Intent(this, QuizStartActivity::class.java))
                    2 -> startActivity(Intent(this, MemoryMatchActivity::class.java))
                    3 -> startActivity(Intent(this, WordQuestActivity::class.java))
                    4 -> startActivity(Intent(this, PhotoPuzzleActivity::class.java))
                    5 -> startActivity(Intent(this, RapidTapActivity::class.java))
                    6 -> startActivity(Intent(this, PianoTilesActivity::class.java))
                    7 -> startActivity(Intent(this, NeonDashActivity::class.java))

                }
            }

            binding.gamesRecyclerView.layoutManager = LinearLayoutManager(this)
            binding.gamesRecyclerView.adapter = adapter
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error setting up games: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            binding.drawerLayout.openDrawer(GravityCompat.START)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        try {
            updateWelcomeMessage()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}