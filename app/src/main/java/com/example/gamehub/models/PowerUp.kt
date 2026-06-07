package com.example.gamehub.models

enum class PowerUp(val displayName: String, val icon: Int) {
    FIFTY_FIFTY("50/50", android.R.drawable.ic_menu_edit),
    SKIP("Skip Question", android.R.drawable.ic_menu_share),
    EXTRA_TIME("+10 Seconds", android.R.drawable.ic_menu_save)
}