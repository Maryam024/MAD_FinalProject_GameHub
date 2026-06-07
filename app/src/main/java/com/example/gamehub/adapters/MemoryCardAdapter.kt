package com.example.gamehub.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.gamehub.R
import com.example.gamehub.databinding.ItemMemoryCardBinding
import com.example.gamehub.models.MemoryCard
import com.example.gamehub.utils.SoundManager

class MemoryCardAdapter(
    private var cards: List<MemoryCard>,
    private val soundManager: SoundManager,
    private val onCardClick: (Int) -> Unit
) : RecyclerView.Adapter<MemoryCardAdapter.CardViewHolder>() {

    fun updateCards(newCards: List<MemoryCard>) {
        cards = newCards
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemMemoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(cards[position], position)
    }

    override fun getItemCount() = cards.size

    inner class CardViewHolder(private val binding: ItemMemoryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: MemoryCard, position: Int) {
            binding.apply {
                if (card.isMatched) {
                    // Matched card - show emoji with green glow
                    cardFront.visibility = View.VISIBLE
                    cardBack.visibility = View.GONE
                    cardFrontText.text = card.cardValue
                    root.setBackgroundResource(R.drawable.memory_card_matched)
                    root.isEnabled = false
                } else if (card.isFlipped) {
                    // Flipped card - show emoji
                    cardFront.visibility = View.VISIBLE
                    cardBack.visibility = View.GONE
                    cardFrontText.text = card.cardValue
                    root.setBackgroundResource(R.drawable.memory_card_front)
                    root.isEnabled = false
                } else {
                    // Face down card - show back
                    cardFront.visibility = View.GONE
                    cardBack.visibility = View.VISIBLE
                    root.setBackgroundResource(R.drawable.memory_card_back)
                    root.isEnabled = true
                }

                root.setOnClickListener {
                    if (!card.isMatched && !card.isFlipped) {
                        onCardClick(position)
                    }
                }
            }
        }
    }
}