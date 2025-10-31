package app.baldphone.neo.crashes;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import androidx.annotation.NonNull;

public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static CrashHandler instance;

    private final Context appContext;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    private CrashHandler(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    /**
     * Initializes and registers the crash handler for the application.
     * Should be called from your Application's onCreate() method.
     */
    public static synchronized void init(@NonNull Context context) {
        if (instance == null) {
            instance = new CrashHandler(context);
            Thread.setDefaultUncaughtExceptionHandler(instance);
        }
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        try {
            CrashReporter.persistCrash(appContext, t, e);
        } catch (Exception ignored) {
            // Best effort
        }

        try {
            Intent intent = new Intent(appContext, CrashActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK |
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            );
            appContext.startActivity(intent);
        } catch (Exception exception) {
            // Failsafe
        }

        // Delegate to the default handler to allow the system to perform its default actions
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        } else {
            // Fallback if there was no default handler
            Process.killProcess(Process.myPid());
            System.exit(10);
        }
    }
}
