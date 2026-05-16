package com.neomud.client.viewmodel

import androidx.compose.ui.graphics.Color

data class LogSpan(val text: String, val color: Color)

data class LogEntry(val id: Long = 0L, val spans: List<LogSpan>) {
    constructor(text: String, color: Color) : this(0L, listOf(LogSpan(text, color)))
    constructor(spans: List<LogSpan>) : this(0L, spans)
}
