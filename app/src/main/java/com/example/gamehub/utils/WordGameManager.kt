package com.example.gamehub.utils

import com.example.gamehub.models.WordCategory
import com.example.gamehub.models.GameWord

class
WordGameManager {

    private var wordsPlayed = 0

    private val wordsDatabase = mapOf(
        WordCategory.ANIMALS to listOf(
            GameWord("ELEPHANT", WordCategory.ANIMALS, "Easy", "Largest land animal", 100),
            GameWord("GIRAFFE", WordCategory.ANIMALS, "Medium", "Has a very long neck", 150),
            GameWord("KANGAROO", WordCategory.ANIMALS, "Hard", "Jumps and has a pouch", 200),
            GameWord("DOLPHIN", WordCategory.ANIMALS, "Medium", "Intelligent sea mammal", 150),
            GameWord("PENGUIN", WordCategory.ANIMALS, "Easy", "Flightless bird from Antarctica", 100)
        ),
        WordCategory.COUNTRIES to listOf(
            GameWord("AUSTRALIA", WordCategory.COUNTRIES, "Hard", "Land down under", 200),
            GameWord("CANADA", WordCategory.COUNTRIES, "Easy", "Known for maple syrup", 100),
            GameWord("GERMANY", WordCategory.COUNTRIES, "Medium", "Famous for beer and cars", 150),
            GameWord("JAPAN", WordCategory.COUNTRIES, "Easy", "Land of the rising sun", 100),
            GameWord("BRAZIL", WordCategory.COUNTRIES, "Medium", "Famous for soccer and carnival", 150)
        ),
        WordCategory.FRUITS to listOf(
            GameWord("PINEAPPLE", WordCategory.FRUITS, "Hard", "Tropical fruit with spikes", 200),
            GameWord("WATERMELON", WordCategory.FRUITS, "Medium", "Green outside, red inside", 150),
            GameWord("BLUEBERRY", WordCategory.FRUITS, "Medium", "Small blue fruit", 150),
            GameWord("STRAWBERRY", WordCategory.FRUITS, "Easy", "Red fruit with seeds outside", 100),
            GameWord("POMEGRANATE", WordCategory.FRUITS, "Hard", "Red fruit with many seeds", 200)
        ),
        WordCategory.MOVIES to listOf(
            GameWord("TITANIC", WordCategory.MOVIES, "Medium", "Famous ship disaster movie", 150),
            GameWord("AVATAR", WordCategory.MOVIES, "Easy", "Blue aliens movie", 100),
            GameWord("INCEPTION", WordCategory.MOVIES, "Hard", "Dream within a dream", 200),
            GameWord("GLADIATOR", WordCategory.MOVIES, "Medium", "Roman empire film", 150),
            GameWord("FROZEN", WordCategory.MOVIES, "Easy", "Disney snow queen movie", 100)
        ),
        WordCategory.SPORTS to listOf(
            GameWord("BASKETBALL", WordCategory.SPORTS, "Medium", "Sport with a hoop", 150),
            GameWord("FOOTBALL", WordCategory.SPORTS, "Easy", "Most popular sport worldwide", 100),
            GameWord("BADMINTON", WordCategory.SPORTS, "Hard", "Sport with shuttlecock", 200),
            GameWord("CRICKET", WordCategory.SPORTS, "Medium", "Popular in India and England", 150),
            GameWord("TENNIS", WordCategory.SPORTS, "Easy", "Roger Federer's sport", 100)
        ),
        WordCategory.TECHNOLOGY to listOf(
            GameWord("COMPUTER", WordCategory.TECHNOLOGY, "Easy", "Electronic device for computing", 100),
            GameWord("SMARTPHONE", WordCategory.TECHNOLOGY, "Medium", "Mobile device", 150),
            GameWord("ARTIFICIAL", WordCategory.TECHNOLOGY, "Hard", "AI full form starts with this", 200),
            GameWord("INTERNET", WordCategory.TECHNOLOGY, "Medium", "Global network", 150),
            GameWord("ROBOTICS", WordCategory.TECHNOLOGY, "Hard", "Study of robots", 200)
        )
    )

    fun getRandomWord(category: WordCategory, difficulty: String): String {
        val words = wordsDatabase[category]?.filter { it.difficulty == difficulty }
        val selectedWord = words?.random() ?: wordsDatabase[category]?.first()!!
        wordsPlayed++
        return selectedWord.word
    }

    fun getWordsPlayed(): Int = wordsPlayed
}