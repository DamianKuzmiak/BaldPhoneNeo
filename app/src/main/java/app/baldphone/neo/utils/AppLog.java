package app.baldphone.neo.utils;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * AppLog – a tiny ring-buffer logger you can use instead of android.util.Log so that recent lines
 * can be added into crash reports.
 *
 * <p>Usage: AppLog.i("Main", "Started"); AppLog.e("Network", "Request failed: " + error);
 *
 * <p>Retrieve recent lines in CrashReporter via AppLog.dumpRecent().
 */
public final class AppLog {
    private static final int MAX_LINES = 100; // keep it small for OOM resilience
    private static final ArrayDeque<String> RING = new ArrayDeque<>(MAX_LINES);

    private AppLog() {}

    public static synchronized void i(String tag, String msg) {
        add("I", tag, msg);
    }

    public static synchronized void w(String tag, String msg) {
        add("W", tag, msg);
    }

    public static synchronized void e(String tag, String msg) {
        add("E", tag, msg);
    }

    public static synchronized void d(String tag, String msg) {
        add("D", tag, msg);
    }

    private static void add(String level, String tag, String msg) {
        long now = System.currentTimeMillis();
        String line =
                String.format(Locale.US, "%tF %tT.%tL %s/%s: %s", now, now, now, level, tag, msg);
        if (RING.size() >= MAX_LINES) RING.removeFirst();
        RING.addLast(line);

        // Also emit to Logcat for developer convenience.
        int logPriority =
                switch (level) {
                    case "E" -> android.util.Log.ERROR;
                    case "W" -> android.util.Log.WARN;
                    case "D" -> android.util.Log.DEBUG;
                    default -> android.util.Log.INFO;
                };
        android.util.Log.println(logPriority, tag, msg);
    }

    @NonNull
    public static synchronized String dumpRecent() {
        StringBuilder sb = new StringBuilder();
        for (String s : RING) sb.append(s).append('\n');
        return sb.toString();
    }
}
