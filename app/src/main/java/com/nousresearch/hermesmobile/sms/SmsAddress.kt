package com.nousresearch.hermesmobile.sms

/** Strict E.164 recipient normalization; rejects ambiguous national numbers and extensions. */
object SmsAddress {
    private val allowedFormatting = Regex("\\+[0-9() .-]+")

    /** Returns a normalized E.164 address, or null when input is ambiguous or outside E.164 bounds. */
    fun normalize(input: String): String? {
        val value = input.trim()
        if (!allowedFormatting.matches(value)) return null
        val digits = value.filter(Char::isDigit)
        if (digits.length !in 1..15 || digits.firstOrNull() == '0') return null
        return "+$digits"
    }
}
