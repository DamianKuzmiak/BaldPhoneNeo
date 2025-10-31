package app.baldphone.neo.crashes;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.activities.BaldActivity;
import com.bald.uriah.baldphone.views.BaldButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A simple Activity that lists crash reports, lets you view, share (email), and delete them. */
public final class CrashViewerActivity extends BaldActivity {

    private List<File> reports = Collections.emptyList();
    private ArrayAdapter<String> adapter;
    private ListView listView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_viewer);

        listView = findViewById(R.id.list_view);
        BaldButton shareAllBtn = findViewById(R.id.share_all_btn);
        BaldButton deleteAllBtn = findViewById(R.id.delete_all_btn);

        refreshList();

        listView.setOnItemClickListener(
                (parent, view, position, id) -> {
                    if (position < 0 || position >= reports.size()) return;
                    File f = reports.get(position);
                    showDetailsDialog(f);
                });

        shareAllBtn.setOnClickListener(
                v -> {
                    if (reports.isEmpty()) {
                        toast("No reports to share.");
                        return;
                    }
                    shareReports(reports);
                });

        deleteAllBtn.setOnClickListener(
                v -> {
                    if (reports.isEmpty()) {
                        toast("No reports to delete.");
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Delete all reports?")
                            .setMessage("This will permanently delete all stored crash reports.")
                            .setPositiveButton(
                                    "Delete",
                                    (d, w) -> {
                                        CrashReporter.deleteReports(reports);
                                        refreshList();
                                        toast("Deleted.");
                                    })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
    }

    private void refreshList() {
        reports = CrashReporter.listReports(this);
        List<String> labels = new ArrayList<>(reports.size());
        for (File f : reports) {
            String label = CrashReporter.formatTimestamp(f.lastModified());
            labels.add(label);
        }

        if (adapter == null) {
            adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
//            adapter = new ArrayAdapter<>(this, R.layout.list_item_crash, labels);
            listView.setAdapter(adapter);
        } else {
            adapter.clear();
            adapter.addAll(labels);
            adapter.notifyDataSetChanged();
        }
    }

    private void showDetailsDialog(@NonNull File file) {
        String content = CrashReporter.readReport(file);

        ScrollView scroll = new ScrollView(this);
        TextView tv = new TextView(this);
        int pad = dp(12);
        tv.setPadding(pad, pad, pad, pad);
        tv.setTextIsSelectable(true);
        tv.setText(content);
        tv.setTextSize(12);
        tv.setHorizontallyScrolling(true);
        tv.setMovementMethod(new ScrollingMovementMethod());
        scroll.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("Details: " + file.getName())
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(
                        android.R.string.copy,
                        (d, w) -> {
                            ClipboardManager cm =
                                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("Crash Report", content);
                            cm.setPrimaryClip(clip);
                            toast("Copied to clipboard.");
                        })
                .setPositiveButton(
                        R.string.share, (d, w) -> shareReports(Collections.singletonList(file)))
                .setOnDismissListener(
                        dialog -> {
                            // Optionally delete reports after sharing; here we leave as-is.
                        })
                .setOnCancelListener(dialog -> {})
                .setCancelable(true)
                .show();
    }

    private void shareReports(@NonNull List<File> files) {
        Intent intent =
                CrashReporter.createEmailIntent(this, getString(R.string.app_contact_email), files);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast("No email app available.");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    @Override
    protected int requiredPermissions() {
        return PERMISSION_NONE;
    }
}
