package com.example.gamehub.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gamehub.databinding.ItemGameBinding
import com.example.gamehub.models.Game

class GameAdapter(
    private val games: List<Game>,
    private val onGameClick: (Game) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    inner class GameViewHolder(private val binding: ItemGameBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(game: Game) {
            binding.apply {
                gameTitle.text = game.title
                gameDescription.text = game.description
                // Set icons based on game type
                gameIcon.text = when (game.id) {
                    1 -> "⚡"  // Rapid Fire Trivia
                    2 -> "🃏"  // Memory Match
                    3 -> "🔤"  // Word Quest
                    4 -> "🖼️"  // Photo Puzzle
                    5 -> "⚡"      // Rapid Tap Challenge
                    6 -> "🎹"      // Piano Game (changed from Reaction Time)
                    7 -> "🏃"      // Neon Dash Runner
                    else -> "🎮"
                }

                root.setOnClickListener {
                    onGameClick(game)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val binding = ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GameViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(games[position])
    }

    override fun getItemCount() = games.size
}