/*
 * Copyright 2019 Uriah Shaul Mandel
 * Copyright 2026 Zenolabs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bald.uriah.baldphone.activities.alarms;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.activities.TimedBaldActivity;
import com.bald.uriah.baldphone.databases.alarms.Alarm;
import com.bald.uriah.baldphone.databases.alarms.AlarmScheduler;
import com.bald.uriah.baldphone.databases.alarms.AlarmsDatabase;
import com.bald.uriah.baldphone.utils.Animations;
import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.BaldToast;
import com.bald.uriah.baldphone.utils.D;
import com.bald.uriah.baldphone.utils.S;

/**
 * Alarm screen, will be called from {@link com.bald.uriah.baldphone.broadcast_receivers.AlarmReceiver}
 */
public class AlarmScreenActivity extends TimedBaldActivity {
    private static final String TAG = AlarmScreenActivity.class.getSimpleName();
    private static final int TIME_DELAYED_SCHEDULE = 100;
    /**
     * How often the ringtone is checked on API levels that cannot loop it natively.
     */
    private static final int RINGTONE_POLL_INTERVAL = 500;
    private static final AudioAttributes alarmAttributes =
            new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

    private TextView tv_name, snooze;
    private ImageView cancel;
    private Ringtone ringtone;
    private final Handler ringtoneHandler = new Handler(Looper.getMainLooper());
    /**
     * Fallback for API < 28, where {@link Ringtone#setLooping(boolean)} does not exist:
     * restarts the ringtone as soon as it has finished playing.
     */
    private final Runnable ringtoneWatchdog = new Runnable() {
        @Override
        public void run() {
            if (ringtone != null && !ringtone.isPlaying())
                ringtone.play();
            ringtoneHandler.postDelayed(this, RINGTONE_POLL_INTERVAL);
        }
    };
    private Alarm alarm;

    public static Ringtone getRingtone(Context context) {
        Uri alert =
                RingtoneManager
                        .getActualDefaultRingtoneUri(context.getApplicationContext(), RingtoneManager.TYPE_ALARM);
        if (alert == null)
            alert = RingtoneManager
                    .getActualDefaultRingtoneUri(context.getApplicationContext(), RingtoneManager.TYPE_NOTIFICATION);
        if (alert == null)
            alert = RingtoneManager
                    .getActualDefaultRingtoneUri(context.getApplicationContext(), RingtoneManager.TYPE_RINGTONE);
        final Ringtone ringtone = RingtoneManager.getRingtone(context, alert);
        final AudioManager audioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {//who knows lol - btw don't delete user's may lower the alarm sounds by mistake
            final int alarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) * (BPrefs.get(context).getInt(BPrefs.ALARM_VOLUME_KEY, BPrefs.ALARM_VOLUME_DEFAULT_VALUE) + 6) / 10;
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmVolume, 0);
        }
        ringtone.setAudioAttributes(alarmAttributes);
        return ringtone;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        S.logImportant("alarmScreen was called!");
        setContentView(R.layout.alarm_screen);

        attachXml();

        final Intent intent = getIntent();
        if (intent == null) throw new AssertionError();
        int key = intent.getIntExtra(Alarm.ALARM_KEY_VIA_INTENTS, -1);
        if (key == -1) throw new AssertionError();
        alarm = AlarmsDatabase.getInstance(this).alarmsDatabaseDao().getByKey(key);
        if (alarm == null) {
            S.logImportant("alarm == null!, returning");
            return;
        }

        final String name = alarm.getName();
        if (name == null) tv_name.setVisibility(View.GONE);
        else tv_name.setText(name);

        cancel.setOnClickListener(v -> {
            if (vibrator != null)
                vibrator.vibrate(D.vibetime);
            if (alarm.getName().equals(getString(R.string.timer)))
                AlarmsDatabase.getInstance(this).alarmsDatabaseDao().delete(alarm);
            finish();
        });
        cancel.setOnLongClickListener(v -> {
            if (vibrator != null)
                vibrator.vibrate(D.vibetime);
            if (alarm.getName().equals(getString(R.string.timer)))
                AlarmsDatabase.getInstance(this).alarmsDatabaseDao().delete(alarm);
            finish();
            return true;
        });

        snooze.setOnClickListener((v) -> snooze());
        snooze.setOnLongClickListener((v) -> {
            snooze();
            return true;
        });

        ringtone = getRingtone(this);

        Animations.makeBiggerAndSmaller(this, cancel, () -> {
            if (vibrator != null) vibrator.vibrate(D.vibetime);
        });
        scheduleNextAlarm();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startRingtone();
    }

    @Override
    protected void onStop() {
        stopRingtone();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        stopRingtone();
        super.onDestroy();
    }

    /**
     * Starts the ringtone and keeps it going until the alarm is dismissed or snoozed.
     * <p>
     * A plain {@link Ringtone#play()} stops after a single pass, which used to leave the alarm
     * silent after a few seconds. From API 28 the ringtone can loop natively; below that a
     * watchdog restarts it whenever it stops.
     */
    private void startRingtone() {
        if (ringtone == null)
            return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone.setLooping(true);
                ringtone.play();
            } else {
                ringtone.play();
                ringtoneHandler.postDelayed(ringtoneWatchdog, RINGTONE_POLL_INTERVAL);
            }
        } catch (Exception e) {
            BaldToast.error(this);
            Log.e(TAG, "could not play the alarm ringtone", e);
        }
    }

    private void stopRingtone() {
        ringtoneHandler.removeCallbacks(ringtoneWatchdog);
        if (ringtone != null)
            ringtone.stop();
    }

    private void attachXml() {
        tv_name = findViewById(R.id.alarm_name);
        cancel = findViewById(R.id.alarm_cancel);
        snooze = findViewById(R.id.snooze);
    }

    private void snooze() {
        if (vibrator != null)
            vibrator.vibrate(D.vibetime);
        AlarmScheduler.scheduleSnooze(alarm, this);
        finish();
    }

    private void scheduleNextAlarm() {
        AlarmScheduler.cancelAlarm(alarm.getKey(), this);
        new Handler().postDelayed(() -> {
            if (alarm.isEnabled()) {
                if (alarm.getDays() == -1) {
                    AlarmsDatabase.getInstance(this)
                            .alarmsDatabaseDao().update(alarm.getKey(), false);
                } else {
                    AlarmScheduler.scheduleAlarm(alarm, this);
                }
            }
        }, TIME_DELAYED_SCHEDULE);
    }

    @Override
    public void onBackPressed() {
        snooze();
        super.onBackPressed();
    }

    @Override
    protected int screenTimeout() {
        return D.MINUTE * 5;
    }

    @Override
    protected int requiredPermissions() {
        return PERMISSION_SYSTEM_ALERT_WINDOW;
    }
}