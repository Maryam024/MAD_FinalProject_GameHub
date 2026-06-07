package com.example.gamehub.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gamehub.R
import com.example.gamehub.databinding.ItemLeaderboardBinding
import com.example.gamehub.utils.LeaderboardEntry

class LeaderboardAdapter : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    private var entries = listOf<LeaderboardEntry>()

    fun submitList(list: List<LeaderboardEntry>) {
        entries = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount() = entries.size

    class ViewHolder(private val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: LeaderboardEntry) {
            binding.rankText.text = "#${entry.rank}"
            binding.playerNameText.text = entry.playerName
            binding.scoreText.text = entry.score.toString()
        }
    }
}