package com.ridecomp.core.util

/**
 * Masks a phone number for lists (13-UI-SCREENS #19, privacy): "+6281234564521" → "0812 •••• 4521".
 * The full number is never shown in the contact list.
 */
public object PhoneMask {
    private const val INDONESIA_PREFIX = "62"
    private const val INTERNATIONAL_PLUS = '+'
    private const val LOCAL_TRUNK = "0"
    private const val VISIBLE_DIGITS = 4
    private const val HIDDEN = "••••"
    private const val SPACE = " "

    public fun mask(phone: String): String {
        val digits = phone.filter(Char::isDigit)
        val local = if (phone.trimStart().firstOrNull() == INTERNATIONAL_PLUS && digits.startsWith(INDONESIA_PREFIX)) {
            LOCAL_TRUNK + digits.drop(INDONESIA_PREFIX.length)
        } else {
            digits
        }
        // Too short to show both ends without revealing most of it.
        if (local.length <= VISIBLE_DIGITS * 2) return HIDDEN + SPACE + local.takeLast(VISIBLE_DIGITS)
        return local.take(VISIBLE_DIGITS) + SPACE + HIDDEN + SPACE + local.takeLast(VISIBLE_DIGITS)
    }
}
