package app.baldphone.neo.calls.recent;

/**
 * Represents a single entry in the recent calls list.
 * This interface is used to unify different types of call log entries
 * (e.g., actual calls, headers) so they can be displayed in a single RecyclerView.
 */
public interface CallListEntry {
    boolean equals(Object o);
}
