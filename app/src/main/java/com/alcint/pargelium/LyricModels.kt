package com.alcint.pargelium

import androidx.compose.runtime.Immutable

@Immutable
data class SyllableWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

@Immutable
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val words: List<SyllableWord> = emptyList()
)