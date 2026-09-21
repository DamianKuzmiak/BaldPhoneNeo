package app.baldphone.neo.features.contacts.data.source

import android.provider.ContactsContract

import app.baldphone.neo.features.contacts.model.Phone

/**
 * A phone number read from the Data table, together with the account it came from.
 */
internal data class PhoneCandidate(val phone: Phone, val accountType: String?) {
    val isSim: Boolean
        get() = accountType?.contains(PhoneDeduplicator.SIM_ACCOUNT_TYPE_SUFFIX, ignoreCase = true) == true

    val sourceRank: Int
        get() = if (isSim) PhoneDeduplicator.SOURCE_RANK_SIM else PhoneDeduplicator.SOURCE_RANK_ACCOUNT

    fun normalizedNumberKey(): String = phone.normalizedNumber?.takeIf { it.isNotEmpty() } ?: phone.value
}

/**
 * Collapses phone numbers that appear more than once across a contact's accounts down to a single
 * entry per number.
 *
 * The same number can appear multiple times, e.g. once from the SIM card and once from a synced
 * account, or across two different accounts. When that happens we keep the "richest" source rather
 * than a raw SIM entry.
 */
internal object PhoneDeduplicator {
    // SIM account types are vendor-specific (e.g. "vnd.sec.contact.sim", "com.android.contacts.sim")...
    const val SIM_ACCOUNT_TYPE_SUFFIX = ".sim"

    // Source ranking used when deduplicating a number across sources. Higher wins.
    const val SOURCE_RANK_SIM = 0
    const val SOURCE_RANK_ACCOUNT = 1

    fun deduplicate(candidates: List<PhoneCandidate>): List<Phone> {
        if (candidates.isEmpty()) return emptyList()

        val bestCandidates = LinkedHashMap<String, PhoneCandidate>()

        for (candidate in candidates) {
            val key = candidate.normalizedNumberKey()
            val existing = bestCandidates[key]

            if (existing == null || isBetterAccount(candidate, existing)) {
                bestCandidates[key] = candidate
            }
        }

        return bestCandidates.values.map { it.phone }
    }

    private fun isBetterAccount(
        candidate: PhoneCandidate,
        existing: PhoneCandidate
    ): Boolean =
        if (candidate.sourceRank != existing.sourceRank) {
            candidate.sourceRank > existing.sourceRank
        } else {
            candidate.phone.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE &&
                existing.phone.type != ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        }
}
