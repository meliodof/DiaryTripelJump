package com.example.diarytripeljump.data

/** Своя расцветка: три цвета ARGB. Светлая и тёмная темы строятся из них автоматически. */
data class CustomPalette(val primary: Int, val secondary: Int, val tertiary: Int) {

    fun encode(): String = "$primary,$secondary,$tertiary"

    companion object {
        fun decode(text: String?): CustomPalette? {
            val parts = text?.split(",")?.map { it.toIntOrNull() ?: return null } ?: return null
            return if (parts.size == 3) CustomPalette(parts[0], parts[1], parts[2]) else null
        }
    }
}
