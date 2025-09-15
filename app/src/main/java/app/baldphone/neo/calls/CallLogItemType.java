package app.baldphone.neo.calls;

import android.provider.CallLog;
import android.provider.CallLog.Calls;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.bald.uriah.baldphone.R;

import kotlin.jvm.JvmStatic;

/**
 * Maps system call types to specific UI resources for display in the call log.
 *
 * <p>Each call log type (constants from {@link CallLog.Calls}) is associated with specific UI
 * resources (drawable, string, and color) to be displayed in the call log history.
 */
public enum CallLogItemType {
    INCOMING(R.drawable.call_received_on_button, R.string.received, R.color.received),
    MISSED(R.drawable.call_missed_on_button, R.string.missed, R.color.missed),
    OUTGOING(R.drawable.call_made_on_button, R.string.outgoing, R.color.outgoing),
    VOICEMAIL(R.drawable.voicemail_on_button, R.string.voice_mail, R.color.other),
    BLOCKED(R.drawable.blocked_on_button, R.string.blocked, R.color.other),
    UNKNOWN(R.drawable.error_on_background, R.string.empty, R.color.other);

    @DrawableRes public final int drawableRes;
    @StringRes public final int stringRes;
    @ColorRes public final int colorRes;

    CallLogItemType(@DrawableRes int d, @StringRes int s, @ColorRes int c) {
        this.drawableRes = d;
        this.stringRes = s;
        this.colorRes = c;
    }

    @JvmStatic
    public static CallLogItemType fromSystemType(int systemCallType) {
        return switch (systemCallType) {
            case Calls.INCOMING_TYPE, Calls.ANSWERED_EXTERNALLY_TYPE -> INCOMING;
            case Calls.MISSED_TYPE, Calls.REJECTED_TYPE -> MISSED;
            case Calls.OUTGOING_TYPE -> OUTGOING;
            case Calls.VOICEMAIL_TYPE -> VOICEMAIL;
            case Calls.BLOCKED_TYPE -> BLOCKED;
            default -> UNKNOWN;
        };
    }
}
