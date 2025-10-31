package app.baldphone.neo.crashes;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.core.util.AtomicFile;

import app.baldphone.neo.utils.AppLog;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A simple crash reporter that persists crash data to be viewed or shared.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Persists detailed crash reports in a text format.
 *   <li>Launches a viewer activity on next app start if crash reports exist.
 *   <li>Provides methods to manage (list, read, delete, share) stored reports.
 * </ul>
 */
public final class CrashReporter {

    static final String REPORTS_DIR = "crashreports";
    static final String FILE_PROVIDER_SUFFIX = ".crashfileprovider";
    private static final int MAX_REPORTS = 10;

    private CrashReporter() {}

    /** Write a crash report file atomically. */
    static void persistCrash(
            @NonNull Context context, @NonNull Thread thread, @NonNull Throwable ex) {
        long now = System.currentTimeMillis();

        StringBuilder report = new StringBuilder();
        report.append("Timestamp: ").append(now).append("\n");
        report.append("Thread: ").append(thread.getName()).append("\n");
        report.append("Exception: ").append(ex.getClass().getName()).append("\n");
        report.append("Message: ").append(ex.getMessage()).append("\n");
        report.append("App Version: ").append(getAppVersion(context)).append("\n");
        report.append("OS Version: ")
                .append(Build.VERSION.RELEASE)
                .append(" (SDK ")
                .append(Build.VERSION.SDK_INT)
                .append(")\n");
        report.append("Device: ")
                .append(Build.MANUFACTURER)
                .append(" ")
                .append(Build.MODEL)
                .append("\n");
        report.append("Recent Logs:\n").append(AppLog.dumpRecent()).append("\n");
        report.append("Stacktrace:\n").append(getStackTrace(ex));

        try {
            writeAtomic(context, report.toString(), now);
            enforceRetention(context);
        } catch (IOException ignore) {
            // Best effort
        }
    }

    /** Call early on next launch to show the viewer if there are pending reports. */
    public static void openViewerIfPending(@NonNull Context context) {
        List<File> reports = listReports(context);
        if (!reports.isEmpty()) {
            Intent i = new Intent(context, CrashViewerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    }

    /* Returns list of report files sorted by newest first. */
    @NonNull
    public static List<File> listReports(@NonNull Context context) {
        File dir = new File(context.getFilesDir(), REPORTS_DIR);
        if (!dir.exists()) return Collections.emptyList();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
        if (files == null || files.length == 0) return Collections.emptyList();
        List<File> list = Arrays.asList(files);
        list.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return list;
    }

    /* Reads a report file content as String. */
    @NonNull
    public static String readReport(@NonNull File file) {
        try (FileInputStream fis = new FileInputStream(file);
                InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int r;
            while ((r = br.read(buf)) != -1) sb.append(buf, 0, r);
            return sb.toString();
        } catch (IOException e) {
            return "Error: Failed to read report: " + e.getMessage();
        }
    }

    /** Deletes a list of report files. */
    public static void deleteReports(@NonNull List<File> files) {
        for (File f : files) {
            if (f != null) {
                f.delete();
            }
        }
    }

    /** Deletes a single report file. */
    public static void deleteReport(@NonNull File file) {
        file.delete();
    }

    /** Build an email Intent to share one or more crash reports as attachments. */
    public static Intent createEmailIntent(
            @NonNull Context context, @Nullable String toEmail, @NonNull List<File> reports) {
        ArrayList<Uri> uris = new ArrayList<>(reports.size());
        String authority = context.getPackageName() + FILE_PROVIDER_SUFFIX;

        for (File f : reports) {
            Uri uri = FileProvider.getUriForFile(context, authority, f);
            uris.add(uri);
        }

        Intent intent;
        if (uris.size() == 1) {
            intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }

        intent.setType("text/plain");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (toEmail != null && !toEmail.trim().isEmpty()) {
            intent.putExtra(Intent.EXTRA_EMAIL, new String[] {toEmail});
        }

        intent.putExtra(Intent.EXTRA_SUBJECT, "Android Crash Report(s)");
        intent.putExtra(
                Intent.EXTRA_TEXT,
                "Attached crash report(s). Please include steps to reproduce if possible.");

        return Intent.createChooser(intent, "Send crash report");
    }

    // ------------------------- internals -------------------------

    private static void writeAtomic(@NonNull Context context, @NonNull String content, long now)
            throws IOException {
        File dir = new File(context.getFilesDir(), REPORTS_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create crashreports dir");
        }

        File reportFile = new File(dir, now + ".txt");
        AtomicFile atomicFile = new AtomicFile(reportFile);
        FileOutputStream fos = null;
        try {
            fos = atomicFile.startWrite();
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            atomicFile.finishWrite(fos);
        } catch (IOException e) {
            // If something goes wrong, abort the write.
            if (fos != null) {
                atomicFile.failWrite(fos);
            }
            throw new IOException("Failed to write crash report atomically", e);
        }
    }

    private static void enforceRetention(@NonNull Context context) {
        List<File> all = listReports(context);
        if (all.size() <= MAX_REPORTS) return;
        for (int i = MAX_REPORTS; i < all.size(); i++) {
            //noinspection ResultOfMethodCallIgnored
            all.get(i).delete();
        }
    }

    private static String getStackTrace(@NonNull Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String getAppVersion(@NonNull Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo p;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                p = pm.getPackageInfo(
                        context.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                p = pm.getPackageInfo(context.getPackageName(), 0);
            }
            String vn = p.versionName != null ? p.versionName : "0";
            long vc =
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                            ? p.getLongVersionCode()
                            : p.versionCode;
            return vn + " (" + vc + ")";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /** Convenience formatter for filenames/dates in UI. */
    @NonNull
    static String formatTimestamp(long ts) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(ts));
    }
}
