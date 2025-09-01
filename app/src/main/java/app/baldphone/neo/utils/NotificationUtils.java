package app.baldphone.neo.utils;

import android.app.Notification;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NotificationUtils {

    private static final String INDENT = "  "; // Two spaces for indentation

    /**
     * Converts a StatusBarNotification object into a detailed human-readable string.
     *
     * @param sbn The StatusBarNotification object to convert.
     * @return A string representation of the StatusBarNotification, or "StatusBarNotification is null."
     */
    public static String statusBarNotificationToString(StatusBarNotification sbn) {
        if (sbn == null) {
            return "StatusBarNotification is null.";
        }

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

        sb.append("StatusBarNotification Details:\n");
        append(sb, 1, "Package Name: " + sbn.getPackageName());
        append(sb, 1, "ID: " + sbn.getId());
        append(sb, 1, "Tag: " + (sbn.getTag() == null ? "null" : sbn.getTag()));
        append(sb, 1, "Key: " + sbn.getKey());
        append(sb, 1, "Post Time: " + sdf.format(new Date(sbn.getPostTime())) + " (raw: " + sbn.getPostTime() + ")");
        append(sb, 1, "Is Clearable: " + sbn.isClearable());
        append(sb, 1, "Is Ongoing: " + sbn.isOngoing());

        append(sb, 1, "User: " + sbn.getUser());
        append(sb, 1, "Group Key: " + sbn.getGroupKey());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            append(sb, 1, "Is Group: " + sbn.isGroup());
            append(sb, 1, "Override Group Key: " + sbn.getOverrideGroupKey());
        }
        append(sb, 1, "Notification Channel ID (from SBN): " + (sbn.getNotification() != null ? sbn.getNotification().getChannelId() : "N/A"));
//        append(sb, 1, "Instance ID: " + sbn.getInstanceId());


        Notification notification = sbn.getNotification();
        if (notification == null) {
            append(sb, 1, "Notification: null");
            return sb.toString();
        }

        sb.append("\n").append(INDENT).append("Notification Details:\n");
        append(sb, 2, "When: " + sdf.format(new Date(notification.when)) + " (raw: " + notification.when + ")");
        append(sb, 2, "Flags: " + decodeNotificationFlags(notification.flags) + " (Raw: " + notification.flags + ")");
        append(sb, 2, "Defaults: " + decodeNotificationDefaults(notification.defaults) + " (Raw: " + notification.defaults + ")");

        if (notification.tickerText != null) {
            append(sb, 2, "Ticker Text: \"" + notification.tickerText + "\"");
        }
        append(sb, 2, "Icon Resource ID: " + notification.icon);
        append(sb, 2, "Icon Level: " + notification.iconLevel);
        append(sb, 2, "Number: " + notification.number);
        append(sb, 2, "Priority: " + priorityToString(notification.priority));

        if (notification.sound != null) {
            append(sb, 2, "Sound URI: " + notification.sound.toString());
        }
        if (notification.vibrate != null) {
            append(sb, 2, "Vibrate Pattern: " + Arrays.toString(notification.vibrate));
        }
        append(sb, 2, "LED ARGB: " + String.format("0x%08X", notification.ledARGB));
        append(sb, 2, "LED On MS: " + notification.ledOnMS);
        append(sb, 2, "LED Off MS: " + notification.ledOffMS);

        if (notification.contentIntent != null) {
            append(sb, 2, "Content Intent: " + notification.contentIntent.toString());
        }
        if (notification.deleteIntent != null) {
            append(sb, 2, "Delete Intent: " + notification.deleteIntent.toString());
        }
        if (notification.fullScreenIntent != null) {
            append(sb, 2, "FullScreen Intent: " + notification.fullScreenIntent.toString());
        }

        append(sb, 2, "Category: " + notification.category);
        append(sb, 2, "Visibility: " + visibilityToString(notification.visibility));
        append(sb, 2, "Public Version: " + (notification.publicVersion != null ? "Present" : "null")); // Could recurse if needed
        append(sb, 2, "Color: " + String.format("0x%08X", notification.color));
        append(sb, 2, "Group: " + notification.getGroup());
        append(sb, 2, "Sort Key: " + notification.getSortKey());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // API 23
            if (notification.getSmallIcon() != null) {
                append(sb, 2, "Small Icon (Icon Object): " + notification.getSmallIcon().toString());
            }
            if (notification.getLargeIcon() != null) { // This is an Icon object
                append(sb, 2, "Large Icon (Icon Object): " + notification.getLargeIcon().toString());
            }
        } else {
            if (notification.largeIcon != null) { // This is a Bitmap before API 23
                append(sb, 2, "Large Icon (Bitmap): Present (Dimensions: " + notification.largeIcon.getWidth() + "x" + notification.largeIcon.getHeight() + ")");
            }
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26
            append(sb, 2, "Channel ID: " + notification.getChannelId());
            append(sb, 2, "Timeout After (ms): " + notification.getTimeoutAfter());
            append(sb, 2, "Badge Icon Type: " + badgeIconTypeToString(notification.getBadgeIconType()));
            append(sb, 2, "Shortcut ID: " + notification.getShortcutId());
            append(sb, 2, "Settings Text: " + notification.getSettingsText());
            append(sb, 2, "Group Alert Behavior: " + groupAlertBehaviorToString(notification.getGroupAlertBehavior()));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29
            append(sb, 2, "Locus ID: " + (notification.getLocusId() != null ? notification.getLocusId().toString() : "null"));
            append(sb, 2, "Bubble Metadata: " + (notification.getBubbleMetadata() != null ? "Present" : "null"));
        }


        // Extras
        Bundle extras = notification.extras;
        if (extras != null && !extras.isEmpty()) {
            sb.append("\n").append(INDENT).append(INDENT).append("Extras (Bundle):\n");
            Set<String> keys = extras.keySet();
            for (String key : keys) {
                Object value = extras.get(key);
                String valueString;
                if (value instanceof CharSequence[]) {
                    valueString = Arrays.toString((CharSequence[]) value);
                } else if (value instanceof String[]) {
                    valueString = Arrays.toString((String[]) value);
                } else if (value instanceof byte[]) {
                    valueString = "byte[" + ((byte[]) value).length + "]";
                } else if (value instanceof int[]) {
                    valueString = Arrays.toString((int[]) value);
                } else if (value instanceof long[]) {
                    valueString = Arrays.toString((long[]) value);
                } else if (value instanceof boolean[]) {
                    valueString = Arrays.toString((boolean[]) value);
                } else if (value instanceof Bundle) {
                    valueString = "Bundle[...present...]"; // Avoid deep recursion
                }
                else {
                    valueString = (value != null) ? value.toString() : "null";
                }
                append(sb, 3, key + ": (" + (value != null ? value.getClass().getSimpleName() : "N/A") + ") = " + valueString);
            }
        } else {
            append(sb, 2, "Extras: null or empty");
        }

        // Actions
        if (notification.actions != null && notification.actions.length > 0) {
            sb.append("\n").append(INDENT).append(INDENT).append("Actions (Count: " + notification.actions.length + "):\n");
            for (int i = 0; i < notification.actions.length; i++) {
                Notification.Action action = notification.actions[i];
                if (action == null) continue;
                append(sb, 3, "Action " + i + ":");
                append(sb, 4, "Title: \"" + action.title + "\"");
                append(sb, 4, "Intent: " + (action.actionIntent != null ? action.actionIntent.toString() : "null"));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // API 23 for getIcon()
                    append(sb, 4, "Icon (Icon Object): " + (action.getIcon() != null ? action.getIcon().toString() : "null (resId: " + action.icon + ")"));
                } else {
                    append(sb, 4, "Icon Resource ID: " + action.icon);
                }
                Bundle actionExtras = action.getExtras();
                if (actionExtras != null && !actionExtras.isEmpty()) {
                    append(sb, 4, "Action Extras:");
                    for (String key : actionExtras.keySet()) {
                        append(sb, 5, key + ": " + actionExtras.get(key));
                    }
                }
                if (action.getRemoteInputs() != null) {
                    append(sb, 4, "Remote Inputs Count: " + action.getRemoteInputs().length);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24
                    append(sb, 4, "Allow Generated Replies: " + action.getAllowGeneratedReplies());
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28
                    append(sb, 4, "Semantic Action: " + action.getSemanticAction());
                }
            }
        } else {
            append(sb, 2, "Actions: null or empty");
        }

        return sb.toString();
    }

    private static void append(StringBuilder sb, int indentLevel, String text) {
        for (int i = 0; i < indentLevel; i++) {
            sb.append(INDENT);
        }
        sb.append(text).append("\n");
    }

    private static String decodeNotificationFlags(int flags) {
        List<String> flagStrings = new ArrayList<>();
        if ((flags & Notification.FLAG_AUTO_CANCEL) != 0) flagStrings.add("AUTO_CANCEL");
        if ((flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) flagStrings.add("FOREGROUND_SERVICE");
        if ((flags & Notification.FLAG_GROUP_SUMMARY) != 0) flagStrings.add("GROUP_SUMMARY");
        if ((flags & Notification.FLAG_INSISTENT) != 0) flagStrings.add("INSISTENT");
        if ((flags & Notification.FLAG_LOCAL_ONLY) != 0) flagStrings.add("LOCAL_ONLY");
        if ((flags & Notification.FLAG_NO_CLEAR) != 0) flagStrings.add("NO_CLEAR");
        if ((flags & Notification.FLAG_ONGOING_EVENT) != 0) flagStrings.add("ONGOING_EVENT");
        if ((flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0) flagStrings.add("ONLY_ALERT_ONCE");
        if ((flags & Notification.FLAG_SHOW_LIGHTS) != 0) flagStrings.add("SHOW_LIGHTS");
        if ((flags & Notification.FLAG_HIGH_PRIORITY) != 0) flagStrings.add("HIGH_PRIORITY (deprecated)");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if ((flags & Notification.FLAG_BUBBLE) != 0) flagStrings.add("BUBBLE");
        }
        return flagStrings.isEmpty() ? "NONE" : TextUtils.join(", ", flagStrings);
    }

    private static String decodeNotificationDefaults(int defaults) {
        List<String> defaultStrings = new ArrayList<>();
        if ((defaults & Notification.DEFAULT_ALL) == Notification.DEFAULT_ALL) return "ALL";
        if ((defaults & Notification.DEFAULT_SOUND) != 0) defaultStrings.add("SOUND");
        if ((defaults & Notification.DEFAULT_VIBRATE) != 0) defaultStrings.add("VIBRATE");
        if ((defaults & Notification.DEFAULT_LIGHTS) != 0) defaultStrings.add("LIGHTS");
        return defaultStrings.isEmpty() ? "NONE" : TextUtils.join(", ", defaultStrings);
    }

    private static String priorityToString(int priority) {
        switch (priority) {
            case Notification.PRIORITY_DEFAULT: return "DEFAULT";
            case Notification.PRIORITY_LOW: return "LOW";
            case Notification.PRIORITY_MIN: return "MIN";
            case Notification.PRIORITY_HIGH: return "HIGH";
            case Notification.PRIORITY_MAX: return "MAX";
            default: return "UNKNOWN (" + priority + ")";
        }
    }

    private static String visibilityToString(int visibility) {
        switch (visibility) {
            case Notification.VISIBILITY_PUBLIC: return "PUBLIC";
            case Notification.VISIBILITY_PRIVATE: return "PRIVATE";
            case Notification.VISIBILITY_SECRET: return "SECRET";
            default: return "UNKNOWN (" + visibility + ")";
        }
    }

    private static String badgeIconTypeToString(int badgeIconType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            switch (badgeIconType) {
                case Notification.BADGE_ICON_NONE: return "NONE";
                case Notification.BADGE_ICON_SMALL: return "SMALL";
                case Notification.BADGE_ICON_LARGE: return "LARGE";
                default: return "UNKNOWN (" + badgeIconType + ")";
            }
        }
        return "N/A (API < 26)";
    }

    private static String groupAlertBehaviorToString(int groupAlertBehavior) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            switch (groupAlertBehavior) {
                case Notification.GROUP_ALERT_ALL: return "ALL";
                case Notification.GROUP_ALERT_SUMMARY: return "SUMMARY";
                case Notification.GROUP_ALERT_CHILDREN: return "CHILDREN";
                default: return "UNKNOWN (" + groupAlertBehavior + ")";
            }
        }
        return "N/A (API < 26)";
    }
}

