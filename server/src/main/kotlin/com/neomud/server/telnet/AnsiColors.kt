package com.neomud.server.telnet

internal object Ansi {
    const val RESET       = "[0m"
    const val BOLD        = "[1m"
    const val RED         = "[31m"
    const val BOLD_RED    = "[1;31m"
    const val GREEN       = "[32m"
    const val BOLD_GREEN  = "[1;32m"
    const val YELLOW      = "[33m"
    const val BOLD_YELLOW = "[1;33m"
    const val CYAN        = "[36m"
    const val BOLD_CYAN   = "[1;36m"
    const val WHITE       = "[37m"
    const val BOLD_WHITE  = "[1;37m"
    const val GRAY        = "[90m"
    const val BLUE        = "[34m"
    const val MAGENTA     = "[35m"

    fun c(text: String, color: String, useColor: Boolean): String =
        if (useColor) "$color$text$RESET" else text

    fun hpBar(current: Int, max: Int, width: Int = 10, useColor: Boolean = false): String {
        val filled = if (max > 0) ((current.toFloat() / max) * width).toInt().coerceIn(0, width) else 0
        val ratio  = if (max > 0) current.toFloat() / max else 0f
        val color  = when {
            ratio > 0.5f  -> GREEN
            ratio > 0.25f -> YELLOW
            else          -> RED
        }
        val bar = "█".repeat(filled) + "░".repeat(width - filled)
        return if (useColor) "$color[$bar]$RESET" else "[$bar]"
    }
}
