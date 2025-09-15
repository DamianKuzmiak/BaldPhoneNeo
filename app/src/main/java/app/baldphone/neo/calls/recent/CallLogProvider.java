package app.baldphone.neo.calls.recent;

import static android.provider.BaseColumns._ID;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.jetbrains.annotations.Contract;

import java.util.ArrayList;
import java.util.List;

public final class CallLogProvider {
    public static final int DEFAULT_PAGE_SIZE = 40;
    private static final String TAG = CallLogProvider.class.getSimpleName();
    private static final String[] PROJECTION;

    static {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PROJECTION =
                    new String[] {
                        _ID,
                        CallLog.Calls.CACHED_LOOKUP_URI,
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.CACHED_FORMATTED_NUMBER,
                        CallLog.Calls.CACHED_PHOTO_URI,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DATE,
                        CallLog.Calls.NEW
                    };
        } else {
            PROJECTION =
                    new String[] {
                        _ID,
                        CallLog.Calls.CACHED_LOOKUP_URI,
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.CACHED_FORMATTED_NUMBER,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DATE,
                        CallLog.Calls.NEW
                    };
        }
    }

    private final ContentResolver resolver;

    public CallLogProvider(ContentResolver resolver) {
        this.resolver = resolver;
    }

    @NonNull
    @Contract("_, _, _, _ -> new")
    @WorkerThread
    public Page loadPage(
            @Nullable Long beforeDate,
            int limit,
            @Nullable String query,
            @Nullable Integer typeFilter) {

        List<String> selParts = new ArrayList<>();
        List<String> args = new ArrayList<>();

        if (beforeDate != null) {
            selParts.add(CallLog.Calls.DATE + " < ?");
            args.add(Long.toString(beforeDate));
        }

        if (query != null && !query.isEmpty()) {
            String like = "%" + query.replace("%", "\\%") + "%";
            selParts.add(
                    "("
                            + CallLog.Calls.CACHED_NAME
                            + " LIKE ? ESCAPE '\\' OR "
                            + CallLog.Calls.NUMBER
                            + " LIKE ? ESCAPE '\\')");
            args.add(like);
            args.add(like);
        }

        if (typeFilter != null) {
            selParts.add(CallLog.Calls.TYPE + " = ?");
            args.add(Integer.toString(typeFilter));
        }

        String selection = selParts.isEmpty() ? null : String.join(" AND ", selParts);
        String[] selectionArgs = args.isEmpty() ? null : args.toArray(new String[0]);

        // Stable ordering to avoid reshuffling rows with identical DATE.
        final String sortOrder = CallLog.Calls.DATE + " DESC, " + _ID + " DESC";

        Cursor c = null;
        ArrayList<CallItem> list = new ArrayList<>(limit);
        Long minDateSeen = null;

        // Note: Some devices/providers ignore Bundle (ContentResolver.QUERY_ARG_LIMIT has no
        // effect). Hence using legacy signature.
        Uri withLimit =
                CallLog.Calls.CONTENT_URI
                        .buildUpon()
                        .appendQueryParameter(
                                ContactsContract.LIMIT_PARAM_KEY, Integer.toString(limit))
                        .build();

        try {
            c = resolver.query(withLimit, PROJECTION, selection, selectionArgs, sortOrder);
            if (c == null) {
                Log.v(TAG, "Query returned null. Falling back to legacy path.");
                c =
                        resolver.query(
                                CallLog.Calls.CONTENT_URI,
                                PROJECTION,
                                selection,
                                selectionArgs,
                                sortOrder);
            }

            Log.d(TAG, "Query returned a cursor. Count: " + (c != null ? c.getCount() : "null"));

            if (c != null) {
                int idxId = c.getColumnIndexOrThrow(_ID);
                int idxName = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME);
                int idxNumber = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
                int idxType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE);
                int idxDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE);
                int idxNew = c.getColumnIndexOrThrow(CallLog.Calls.NEW);
                int idxLookup = c.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI);
                int idxFormattedNumber = c.getColumnIndex(CallLog.Calls.CACHED_FORMATTED_NUMBER);
                int idxPhoto = -1;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    idxPhoto = c.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI);
                }

                while (c.moveToNext()) {
                    if (list.size() >= limit) break; // hard cap if provider ignored limit

                    long id = c.getLong(idxId);
                    String name = c.isNull(idxName) ? null : c.getString(idxName);
                    String number = c.isNull(idxNumber) ? null : c.getString(idxNumber);
                    int type = c.getInt(idxType);
                    long date = c.getLong(idxDate);

                    // Compute "new/unread": if NEW is missing, fall back to IS_READ==0 (TODO)
                    boolean isNew = false;
                    if (type == CallLog.Calls.MISSED_TYPE && idxNew >= 0 && !c.isNull(idxNew)) {
                        isNew = (c.getInt(idxNew) == 1);
                    }

                    String formattedNumber =
                            (idxFormattedNumber >= 0 && !c.isNull(idxFormattedNumber))
                                    ? c.getString(idxFormattedNumber)
                                    : null;

                    String cachedLookupUriStr =
                            (idxLookup >= 0 && !c.isNull(idxLookup))
                                    ? c.getString(idxLookup)
                                    : null;

                    String photoUri =
                            (idxPhoto >= 0 && !c.isNull(idxPhoto)) ? c.getString(idxPhoto) : null;

                    list.add(
                            new CallItem(
                                    id,
                                    name,
                                    number,
                                    type,
                                    date,
                                    photoUri,
                                    cachedLookupUriStr,
                                    isNew,
                                    formattedNumber));

                    if (minDateSeen == null || date < minDateSeen) minDateSeen = date;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "loadPage", e);
        } finally {
            if (c != null) c.close();
        }

        // If we fetched fewer than limit, we reached the end.
        Long nextKey = (list.size() < limit) ? null : minDateSeen;
        return new Page(list, nextKey);
    }

    /**
     * @param nextBeforeDate Pass this as beforeDate to load the next page; null means no more data.
     */
    public record Page(List<CallItem> items, @Nullable Long nextBeforeDate) {}
}
