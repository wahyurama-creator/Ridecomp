package com.ridecomp.core.util

/** Numbers typed in forms: accepts both "70.5" and the Indonesian "70,5". */
public fun String.toDecimalOrNull(): Double? = trim().replace(DECIMAL_COMMA, DECIMAL_POINT).toDoubleOrNull()

/** A number for a form field: 70.0 → "70", 70.5 → "70.5". */
public fun Double.toPlainString(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

private const val DECIMAL_COMMA = ','
private const val DECIMAL_POINT = '.'
