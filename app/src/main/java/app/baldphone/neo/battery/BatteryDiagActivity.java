package app.baldphone.neo.battery; // Adjust if your package is different

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.bald.uriah.baldphone.R;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class BatteryDiagActivity extends Activity {

    public static final String NOT_AVAILABLE = "N/A";
    private TextView textView;
    private BatteryManager bm;
    private Field[] bmFields;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battery_diag);

        textView = findViewById(R.id.batteryInfoTextView);
        Button button = findViewById(R.id.refreshButton);
        button.setOnClickListener(v -> refreshBatteryData());

        bm = ContextCompat.getSystemService(this, BatteryManager.class);
        bmFields = BatteryManager.class.getDeclaredFields();

        refreshBatteryData();
    }

    private void refreshBatteryData() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== ACTION_BATTERY_CHANGED extras ===\n");
        appendBatteryStatus(sb);

        sb.append("=== BatteryManager properties ===\n");
        appendBatteryProperties(sb);

        sb.append("=== BatteryManager extra methods ===\n");
        appendExtraMethods(sb);

        sb.append("=== BatteryManager static constants ===\n");
        appendStaticConstants(sb);

        textView.setText(sb.toString());
    }

    private void appendBatteryStatus(StringBuilder sb) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        if (batteryStatus == null || batteryStatus.getExtras() == null) {
            sb.append("Could not retrieve ACTION_BATTERY_CHANGED intent.\n\n");
            return;
        }

        for (String key : batteryStatus.getExtras().keySet()) {
            Object value = batteryStatus.getExtras().get(key);
            sb.append(key).append(" = ").append(value);

            if (BatteryManager.EXTRA_STATUS.equals(key)) {
                sb.append(" (")
                        .append(statusToString(batteryStatus.getIntExtra(key, -1)))
                        .append(")");
            } else if (BatteryManager.EXTRA_HEALTH.equals(key)) {
                sb.append(" (")
                        .append(healthToString(batteryStatus.getIntExtra(key, -1)))
                        .append(")");
            } else if (BatteryManager.EXTRA_PLUGGED.equals(key)) {
                sb.append(" (")
                        .append(pluggedToString(batteryStatus.getIntExtra(key, -1)))
                        .append(")");
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void appendBatteryProperties(StringBuilder sb) {
        if (bm == null) {
            sb.append("BatteryManager service not available.\n\n");
            return;
        }

        for (Field f : bmFields) {
            if (!f.getName().startsWith("BATTERY_PROPERTY_")) continue;

            try {
                int constant = f.getInt(null);
                long val = bm.getLongProperty(constant);

                sb.append(f.getName()).append(" = ").append(val);
                switch (constant) {
                    case BatteryManager.BATTERY_PROPERTY_CAPACITY -> sb.append(" (%)");
                    case BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER -> sb.append(" (µAh)");
                    case BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE,
                            BatteryManager.BATTERY_PROPERTY_CURRENT_NOW ->
                            sb.append(" (µA)");
                    case BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER -> sb.append(" (nWh)");
                    case BatteryManager.BATTERY_PROPERTY_STATUS ->
                            sb.append(" (").append(statusToString((int) val)).append(")");
                    default -> {}
                }
                sb.append("\n");

            } catch (SecurityException | IllegalAccessException e) {
                sb.append(f.getName()).append(" (Access Denied)\n");
            }
        }
        sb.append("\n");
    }

    private void appendExtraMethods(StringBuilder sb) {
        if (bm == null) {
            sb.append("BatteryManager service not available.\n\n");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sb.append("isCharging() = ").append(bm.isCharging()).append("\n");
        } else {
            sb.append("isCharging() not available on this API version\n");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            long timeMs = bm.computeChargeTimeRemaining();
            sb.append("computeChargeTimeRemaining() = ")
                    .append(timeMs > 0 ? timeMs + " ms" : timeMs)
                    .append("\n");
        } else {
            sb.append("computeChargeTimeRemaining() not available on this API version\n");
        }
        sb.append("\n");
    }

    private void appendStaticConstants(StringBuilder sb) {
        for (Field f : bmFields) {
            if (Modifier.isStatic(f.getModifiers())
                    && f.getType() == int.class
                    && f.getName().startsWith("BATTERY_")) {
                sb.append(f.getName()).append("\n");
            }
        }
        sb.append("\n");
    }

    private String statusToString(int status) {
        return switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING";
            case BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING";
            case BatteryManager.BATTERY_STATUS_FULL -> "FULL";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING";
            case BatteryManager.BATTERY_STATUS_UNKNOWN -> "UNKNOWN";
            default -> NOT_AVAILABLE;
        };
    }

    private String healthToString(int health) {
        return switch (health) {
            case BatteryManager.BATTERY_HEALTH_COLD -> "COLD";
            case BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD";
            case BatteryManager.BATTERY_HEALTH_GOOD -> "GOOD";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER_VOLTAGE";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE";
            case BatteryManager.BATTERY_HEALTH_UNKNOWN -> "UNKNOWN";
            default -> NOT_AVAILABLE;
        };
    }

    private String pluggedToString(int plugged) {
        return switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC -> "AC";
            case BatteryManager.BATTERY_PLUGGED_USB -> "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS";
            default -> NOT_AVAILABLE;
        };
    }
}
