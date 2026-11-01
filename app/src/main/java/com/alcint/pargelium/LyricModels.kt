package com.alcint.pargelium

data class SyllableWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val words: List<SyllableWord> = emptyList()
)