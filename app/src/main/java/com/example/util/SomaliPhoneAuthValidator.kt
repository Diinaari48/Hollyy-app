package com.example.util

object SomaliPhoneAuthValidator {

    const val AUTH_EMAIL_DOMAIN = "holly.com"

    private val SOMALI_PHONE_REGEX = Regex("^(61|68)\\d{7}$")

    /**
     * Normalizes a Somali phone number to a standard 9-digit format:
     * - Strips spaces, dashes, '+', parentheses and non-digits
     * - Strips leading +252 or 252 prefix
     * - Strips leading 0
     * - Validates against /^(61|68)\d{7}$/ (exactly 9 digits starting with 61 or 68)
     *
     * Returns the 9-digit normalized string (e.g. "611234567") or null if invalid.
     */
    fun normalizePhoneNumber(input: String): String? {
        var digits = input.replace(Regex("[\\s\\-+()]"), "").filter { it.isDigit() }
        if (digits.startsWith("252")) {
            digits = digits.substring(3)
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1)
        }
        return if (SOMALI_PHONE_REGEX.matches(digits)) digits else null
    }

    /**
     * Converts a Somali phone number to synthetic email: "{normalizedPhone}@holly.com" (lowercase).
     * Used by BOTH signUp and signInWithPassword.
     */
    fun phoneToEmail(phone: String): String {
        val normalized = normalizePhoneNumber(phone) ?: phone.replace(Regex("[\\s\\-+()]"), "").filter { it.isDigit() }
        return "${normalized.lowercase()}@$AUTH_EMAIL_DOMAIN"
    }

    /**
     * Validates phone number and returns user-friendly Somali error message, or null if valid.
     */
    fun validatePhone(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return "Fadlan geli number-ka taleefanka"
        }
        val normalized = normalizePhoneNumber(trimmed)
        if (normalized == null) {
            return "Number-ku waa inuu ka bilaabmaa 61 ama 68 (9 lambar)"
        }
        return null
    }

    fun extractPhoneFromEmail(email: String): String {
        val raw = email.substringBefore("@")
        return normalizePhoneNumber(raw) ?: raw
    }

    fun validatePassword(password: String): String? {
        if (password.length < 6) {
            return "Erayga sirta ah waa inuu ka koobnaadaa ugu yaraan 6 xaraf"
        }
        return null
    }

    fun formatPhoneDisplay(phone: String): String {
        val normalized = normalizePhoneNumber(phone) ?: phone
        return if (normalized.length == 9) {
            "+252 ${normalized.substring(0, 2)} ${normalized.substring(2, 5)} ${normalized.substring(5)}"
        } else {
            phone
        }
    }
}

