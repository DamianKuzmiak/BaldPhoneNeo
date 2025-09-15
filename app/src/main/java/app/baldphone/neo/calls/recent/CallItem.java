package app.baldphone.neo.calls.recent;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * Represents a call log entry for Recent Calls.
 *
 * <p>It implements the {@link CallListEntry} interface, allowing it to be part of a heterogeneous
 * list of call-related items.
 *
 * @param id the unique identifier for the call.
 * @param name the cached display name of the contact, may be {@code null}.
 * @param number the raw phone number. May be {@code null} or empty if private number,
 *     unknown or unavailable.
 * @param type the type of the call (e.g., incoming, outgoing, missed).
 * @param date the date and time of the call, in epoch milliseconds.
 * @param cachedPhotoUri the URI for the cached photo of the contact, may be {@code null}.
 * @param cachedLookupUri the cached lookup URI for the contact, may be {@code null}.
 * @param isNew {@code true} if the call is new, {@code false} otherwise.
 * @param cachedFormattedNumber the cached formatted phone number, may be {@code null}.
 */
public record CallItem(
        long id,
        @Nullable String name,
        @Nullable String number,
        int type,
        long date,
        @Nullable String cachedPhotoUri,
        @Nullable String cachedLookupUri,
        boolean isNew,
        @Nullable String cachedFormattedNumber)
        implements CallListEntry {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallItem callItem = (CallItem) o;
        return id == callItem.id
                && type == callItem.type
                && date == callItem.date
                && Objects.equals(name, callItem.name)
                && Objects.equals(number, callItem.number)
                && Objects.equals(cachedPhotoUri, callItem.cachedPhotoUri)
                && Objects.equals(cachedLookupUri, callItem.cachedLookupUri)
                && isNew == callItem.isNew
                && Objects.equals(cachedFormattedNumber, callItem.cachedFormattedNumber);
    }
}
