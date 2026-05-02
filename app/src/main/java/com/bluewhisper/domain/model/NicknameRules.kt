package com.bluewhisper.domain.model

object NicknameRules {
    const val MAX_LENGTH = 15
    const val MIN_LENGTH = 1

    // `|` is the delimiter in the advertising payload "nickname|avatarId".
    // Allowing it would silently corrupt discovery and avatar parsing for the
    // affected user (Bug B-06).
    val FORBIDDEN_CHARS = charArrayOf('|')

    fun sanitize(input: String): String {
        var s = input
        for (c in FORBIDDEN_CHARS) s = s.replace(c.toString(), "")
        if (s.length > MAX_LENGTH) s = s.substring(0, MAX_LENGTH)
        return s
    }

    fun isValid(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.length !in MIN_LENGTH..MAX_LENGTH) return false
        if (FORBIDDEN_CHARS.any { trimmed.contains(it) }) return false
        return true
    }
}
