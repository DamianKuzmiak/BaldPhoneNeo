package app.baldphone.neo.utils

import java.text.Normalizer

private val diacriticsRegex = Regex("\\p{Mn}+")

/**
 * Normalizes text for searching and sorting:
 * - Decomposes characters (NFD)
 * - Removes diacritics (accents)
 * - Converts to lowercase
 *
 * This is useful for consistent contact searching regardless of accents or case.
 */
fun String.toNormalizedLowercase(): String =
    Normalizer
        .normalize(this, Normalizer.Form.NFD)
        .replace(diacriticsRegex, "")
        .lowercase()
