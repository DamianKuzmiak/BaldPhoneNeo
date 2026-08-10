package app.baldphone.neo.features.contacts

import android.content.Context

import kotlin.text.iterator

import app.baldphone.neo.utils.toNormalizedLowercase

import com.bald.uriah.baldphone.R

/**
 * Logic for searching contacts, including T9 matching and alphabetical grouping.
 *
 * Takes a pre-built char->digit T9 map to avoid holding a Context reference.
 */
class ContactSearcher(
    private val charToDigit: Map<Char, Char>
) {
    companion object {
        /**
         * Builds the inverted T9 map (char -> digit) from string resources.
         * Call once with Application context and pass the result to the constructor.
         */
        fun buildCharToDigitMap(context: Context): Map<Char, Char> {
            // TODO: Re-initialize the Map dynamically on Configuration Change
            val digitToLetters =
                mapOf(
                    '0' to " ",
                    '2' to context.getString(R.string.t9_key_2),
                    '3' to context.getString(R.string.t9_key_3),
                    '4' to context.getString(R.string.t9_key_4),
                    '5' to context.getString(R.string.t9_key_5),
                    '6' to context.getString(R.string.t9_key_6),
                    '7' to context.getString(R.string.t9_key_7),
                    '8' to context.getString(R.string.t9_key_8),
                    '9' to context.getString(R.string.t9_key_9)
                )
            return buildMap {
                digitToLetters.forEach { (digit, letters) ->
                    letters.forEach { ch -> put(ch, digit) }
                }
            }
        }
    }

    private val nonLetterRegex = Regex("[^a-z]")
    private val numericRegex = Regex("[0-9]+")

    /**
     * Performs a synchronous search and grouping operation.
     */
    fun searchContacts(
        allContacts: List<SimpleContact>,
        query: String = "",
        isFavorites: Boolean = false,
        enableT9: Boolean = false,
        showAllWhenEmpty: Boolean = false,
        itemLimit: Int = -1
    ): List<ContactItemType> {
        val filtered = filterContacts(allContacts, query.trim(), isFavorites, enableT9, showAllWhenEmpty)
        return groupContactsByInitial(filtered, itemLimit)
    }

    /**
     * Filters contacts based on query, starred status, and T9 input rules.
     */
    private fun filterContacts(
        contacts: List<SimpleContact>,
        query: String,
        starredOnly: Boolean,
        enableT9: Boolean,
        showAllWhenEmpty: Boolean
    ): List<SimpleContact> {
        val filteredByStarred = if (starredOnly) contacts.filter { it.isStarred } else contacts

        if (query.isEmpty() && !showAllWhenEmpty) return emptyList()
        if (query.isEmpty()) return filteredByStarred

        return filterByQuery(filteredByStarred, query, enableT9)
    }

    private fun filterByQuery(
        contacts: List<SimpleContact>,
        query: String,
        enableT9: Boolean
    ): List<SimpleContact> {
        val isNumeric = query.matches(numericRegex)
        val normalizedNameQuery = query.toNormalizedLowercase()

        return contacts.filter { contact ->
            if (isNumeric) {
                contact.normalizedNumber.contains(query) || (
                    enableT9 &&
                        matchesT9Query(
                            contact.normalizedName,
                            query
                        )
                ) || matchesNamePrefix(contact.normalizedName, normalizedNameQuery)
            } else {
                matchesNamePrefix(contact.normalizedName, normalizedNameQuery)
            }
        }
    }

    /**
     * Checks if the contact name or any word within it starts with the query.
     */
    private fun matchesNamePrefix(
        normalizedName: String,
        normalizedQuery: String
    ): Boolean {
        if (normalizedQuery.isEmpty()) return true
        if (normalizedName.startsWith(normalizedQuery)) return true

        // Check if any word starts with the query (e.g., "Doe" for query "d" in "John Doe")
        // We look for the query preceded by a space.
        return normalizedName.contains(" $normalizedQuery")
    }

    /**
     * T9 matching using the precomputed char->digit map for O(1) per-character lookup.
     */
    private fun matchesT9Query(
        normalizedName: String,
        query: String
    ): Boolean {
        if (query.isEmpty() || !query.matches(numericRegex)) return false
        val cleanName = normalizedName.replace(nonLetterRegex, " ")
        if (cleanName.trim().isEmpty()) return false

        // Generate T9 representation of the name, preserving spaces to allow word-start matching
        val nameT9 =
            buildString(cleanName.length) {
                for (ch in cleanName) {
                    if (ch == ' ') {
                        append(' ')
                    } else {
                        append(charToDigit[ch] ?: '?')
                    }
                }
            }

        return nameT9.startsWith(query) || nameT9.contains(" $query")
    }

    /**
     * Groups contacts by their initial letter for section headers.
     * Assumes the input list is already sorted from the provider.
     *
     * @param itemLimit If > 0, stops grouping after reaching this many items (headers+contacts).
     */
    private fun groupContactsByInitial(
        contacts: List<SimpleContact>,
        itemLimit: Int = -1
    ): List<ContactItemType> {
        if (contacts.isEmpty()) return emptyList()

        val sortedContacts = contacts.sortedBy { it.normalizedName }

        val result = mutableListOf<ContactItemType>()
        var currentLetter: String? = null

        for (contact in sortedContacts) {
            if (itemLimit > 0 && result.size >= itemLimit) break

            val firstChar = contact.normalizedName.firstOrNull()?.uppercaseChar()
            val letter =
                if (firstChar != null && firstChar in 'A'..'Z') {
                    firstChar.toString()
                } else {
                    "#"
                }

            if (letter != currentLetter) {
                currentLetter = letter
                result.add(ContactItemType.Header(letter))
                if (itemLimit > 0 && result.size >= itemLimit) break
            }
            result.add(ContactItemType.ContactItem(contact))
        }

        return result
    }
}
