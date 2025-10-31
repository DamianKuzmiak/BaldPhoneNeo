package app.baldphone.neo.crashes;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bald.uriah.baldphone.R;

public class CrashActivity extends AppCompatActivity {

    private final Handler closeActivityHandler = new Handler(Looper.getMainLooper());
    private final Runnable closeActivityRunnable = this::closeCrashActivity;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

        long autoCloseDelay = 5000L;
        closeActivityHandler.postDelayed(closeActivityRunnable, autoCloseDelay);

        // Prevent the user from going back to the crashed state
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                // Do nothing
                            }
                        });
    }

    @Override
    protected void onDestroy() {
        closeActivityHandler.removeCallbacks(closeActivityRunnable);
        super.onDestroy();
    }

    private void closeCrashActivity() {
        boolean isCrashLoop = false;
        if (!isCrashLoop) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        }
        // Closes this activity
        finishAffinity();

        // Forcefully kill :crash_handler process.
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
