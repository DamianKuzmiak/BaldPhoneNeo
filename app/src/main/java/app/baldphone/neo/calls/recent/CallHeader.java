package app.baldphone.neo.calls.recent;

/**
 * Represents a header in a list of call entries, used to display a title or separator.
 *
 * <p>This class implements the {@link CallListEntry} interface, enabling its inclusion
 * in heterogeneous lists of call-related items.
 *
 * @param text The text to display in the header.
 */
public record CallHeader(String text) implements CallListEntry {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        return text.equals(((CallHeader) o).text);
    }
}
