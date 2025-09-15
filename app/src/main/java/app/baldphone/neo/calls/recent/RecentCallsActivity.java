package app.baldphone.neo.calls.recent;

import static app.baldphone.neo.utils.DateTimeFormatter.toRelativeDateString;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import app.baldphone.neo.calls.UnknownCallHandler;
import app.baldphone.neo.notifications.NotificationListenerService;
import app.baldphone.neo.contacts.ContactsUtils;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.activities.BaldActivity;
import com.bald.uriah.baldphone.activities.contacts.SingleContactActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecentCallsActivity extends BaldActivity {

    private static final int REQ_READ_CALL_LOG = 101;
    private static final String TAG = "RecentCallsActivity";
    private static final int SCROLL_THRESHOLD = 10;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<CallItem> data = new ArrayList<>();

    private RecentCallsAdapter adapter;
    private CallLogProvider callLogProvider;
    private RecyclerView recycler;
    private TextView noCallsText;
    private ProgressBar loadingSpinner;

    private boolean loading = false;
    private boolean reachedEnd = false;
    @Nullable private Long nextBeforeDate = null;
    @Nullable private String searchQuery = null;
    @Nullable private Integer typeFilter = null;

    private final ContentObserver observer =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    refreshFromScratch(); // call log changed → reload
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recent_calls);
        Log.v(TAG, "onCreate");

        initViews();
        ensurePermissionsThen(
                () -> {
                    getContentResolver()
                            .registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer);
                    refreshFromScratch();
                });
    }

    private void initViews() {
        callLogProvider = new CallLogProvider(getContentResolver());
        recycler = findViewById(R.id.recycler_view);
        noCallsText = findViewById(R.id.no_calls_text);
        loadingSpinner = findViewById(R.id.loading_spinner);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(true);

        adapter = new RecentCallsAdapter();
        adapter.setOnCallClickListener(this::onCallClicked);
        recycler.setAdapter(adapter);

        RecyclerView.ItemAnimator animator = recycler.getItemAnimator();
        if (animator instanceof SimpleItemAnimator simpleAnimator)
            simpleAnimator.setSupportsChangeAnimations(false);

        recycler.addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                        if (dy <= 0 || loading || reachedEnd) return;

                        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                        if (lm == null) return;

                        int lastVisible = lm.findLastVisibleItemPosition();
                        if (lastVisible >= adapter.getItemCount() - SCROLL_THRESHOLD) {
                            loadNextPage();
                        }
                    }
                });
    }

    private void onCallClicked(CallItem item) {
        String contactLookupKey =
                ContactsUtils.resolveLookupKey(
                        getContentResolver(),
                        item.cachedLookupUri(),
                        item.number(),
                        item.name());
        if (contactLookupKey != null) {
            final Intent intent =
                    new Intent(this, SingleContactActivity.class)
                            .putExtra(SingleContactActivity.CONTACT_LOOKUP_KEY, contactLookupKey);
            this.startActivityForResult(intent, SingleContactActivity.REQUEST_CHECK_CHANGE);
        } else {
            UnknownCallHandler.processCallAction(item, this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "Clearing missed call notifications");
        NotificationListenerService.clearMissedCalls();
    }

    @Override
    protected int requiredPermissions() {
        return PERMISSION_NONE;
    }

    private void ensurePermissionsThen(Runnable onGranted) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onGranted.run();
            return;
        }

        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.READ_CALL_LOG);

        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), REQ_READ_CALL_LOG);
        } else {
            onGranted.run();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(requestCode, perms, res);
        if (requestCode == REQ_READ_CALL_LOG) {
            boolean ok = true;
            for (int r : res) ok &= (r == PackageManager.PERMISSION_GRANTED);
            if (ok) refreshFromScratch();
            else Toast.makeText(this, "Call log permission denied", Toast.LENGTH_LONG).show();
        }
    }

    // === Visibility helpers ===
    private void showLoadingInitial() {
        // First-time load (empty list)
        recycler.setVisibility(View.GONE);
        noCallsText.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.VISIBLE);
    }

    private void showListLoadingMore() {
        // Keep list visible, show small spinner
        recycler.setVisibility(View.VISIBLE);
        noCallsText.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.VISIBLE);
    }

    private void showListLoaded() {
        recycler.setVisibility(View.VISIBLE);
        noCallsText.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.GONE);
    }

    private void showEmpty() {
        recycler.setVisibility(View.GONE);
        loadingSpinner.setVisibility(View.GONE);
        noCallsText.setVisibility(View.VISIBLE);
    }

    private void refreshFromScratch() {
        data.clear();
        adapter.submitList(groupWithHeaders(data), () -> {});
        nextBeforeDate = null;
        reachedEnd = false;
        showLoadingInitial();
        loadNextPage();
    }

    private void loadNextPage() {
        if (reachedEnd) return;
        loading = true;
        showListLoadingMore();

        Log.v(TAG, "Loading next page...");
        final Long key = nextBeforeDate;
        final String q = searchQuery;
        final Integer tf = typeFilter;

        io.execute(
                () -> {
                    try {
                        CallLogProvider.Page page =
                                callLogProvider.loadPage(
                                        key, CallLogProvider.DEFAULT_PAGE_SIZE, q, tf);

                        main.post(
                                () -> {
                                    loading = false;
                                    if (page.items().isEmpty()) {
                                        reachedEnd = true;
                                        if (data.isEmpty()) showEmpty();
                                        else showListLoaded();
                                        return;
                                    }

                                    data.addAll(page.items());
                                    nextBeforeDate = page.nextBeforeDate();
                                    reachedEnd = (nextBeforeDate == null);

                                    adapter.submitList(
                                            groupWithHeaders(data), this::showListLoaded);
                                });
                    } catch (Exception e) {
                        Log.e(TAG, "Error loading page", e);
                        main.post(
                                () -> {
                                    loading = false;
                                    showListLoaded();
                                });
                    }
                });
    }

    @NonNull
    private List<CallListEntry> groupWithHeaders(@NonNull List<CallItem> items) {
        List<CallListEntry> result = new ArrayList<>();
        String lastHeader = null;
        for (CallItem item : items) {
            String header = toRelativeDateString(item.date());
            if (!header.equals(lastHeader)) {
                result.add(new CallHeader(header));
                lastHeader = header;
            }
            result.add(item);
        }
        return result;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        try {
            getContentResolver().unregisterContentObserver(observer);
        } catch (Exception ignored) {
        }
    }
}
