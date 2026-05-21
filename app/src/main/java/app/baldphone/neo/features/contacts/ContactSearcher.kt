package app.baldphone.neo.features.contacts

import android.content.Context

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

import java.text.Normalizer

import com.bald.uriah.baldphone.R

/**
 * Logic for searching contacts, including T9 matching and alphabetical grouping.
 */
class ContactSearcher(private val context: Context) {

    companion object {
        private const val DEFAULT_DEBOUNCE_MS = 250L
    }

    private val t9Map: Map<Char, String> by lazy {
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
    }

    private val nonDigitRegex = Regex("[^0-9]")
    private val nonLetterRegex = Regex("[^a-z]")
    private val numericRegex = Regex("[0-9]+")

    /**
     * Search contacts as-you-type with auto-detection.
     *
     * Automatically detects what to search based on input:
     * - Numeric input (e.g., "555", "2542") -> Searches phone numbers + optional T9 name matching
     * - Letter input (e.g., "ali", "bob") -> Searches contact names
     * - Mixed input -> Searches both
     */
    @OptIn(FlowPreview::class)
    fun searchContactsFlow(
        allContacts: List<SimpleContact>,
        searchQueryFlow: Flow<String>,
        starredOnlyFlow: Flow<Boolean> = flowOf(false),
        enableT9: Boolean = false,
        showAllWhenEmpty: Boolean = false,
        debounceMs: Long = DEFAULT_DEBOUNCE_MS
    ): Flow<List<ContactItemType>> {
        return combine(
            searchQueryFlow.map { it.trim() }.debounce(debounceMs).distinctUntilChanged(),
            starredOnlyFlow
        ) { query, isFavorites ->
            val filtered = filterContacts(
                contacts = allContacts,
                query = query,
                starredOnly = isFavorites,
                enableT9 = enableT9,
                showAllWhenEmpty = showAllWhenEmpty
            )
            groupContactsByInitial(filtered)
        }
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
        val normalizedQuery = query.replace(nonDigitRegex, "")

        return contacts.filter { contact ->
            if (isNumeric) {
                val normalizedNumber = contact.phoneNumber.replace(nonDigitRegex, "")
                normalizedNumber.contains(normalizedQuery) ||
                        (enableT9 && matchesT9Query(contact.name, query)) ||
                        matchesNameQuery(contact.name, query)
            } else {
                matchesNameQuery(contact.name, query)
            }
        }
    }

    private fun matchesNameQuery(name: String, query: String): Boolean {
        val nName = normalize(name)
        val nQuery = normalize(query)
        return nName.contains(nQuery)
    }

    private fun matchesT9Query(name: String, query: String): Boolean {
        if (query.isEmpty() || !query.matches(numericRegex)) return false
        val normalizedName = name.lowercase().replace(nonLetterRegex, "")
        if (normalizedName.isEmpty()) return false
        val nameT9 = normalizedName.map { char ->
            t9Map.entries.find { it.value.contains(char) }?.key ?: '?'
        }.joinToString("")
        return nameT9.contains(query)
    }

    private fun normalize(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
    }

    private fun groupContactsByInitial(contacts: List<SimpleContact>): List<ContactItemType> {
        if (contacts.isEmpty()) return emptyList()

        val result = mutableListOf<ContactItemType>()
        val grouped = contacts.groupBy {
            normalize(it.name).firstOrNull()?.uppercaseChar()?.toString() ?: "#"
        }

        grouped.toSortedMap().forEach { (letter, list) ->
            result.add(ContactItemType.Header(letter))
            list.sortedBy { it.name.lowercase() }
                .forEach { result.add(ContactItemType.ContactItem(it)) }
        }

        return result
    }
}
