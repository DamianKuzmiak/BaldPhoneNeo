package app.baldphone.neo.battery;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class BatteryState {

    public static final int UNKNOWN_PERCENTAGE = -1;

    @IntRange(from = -1, to = 100)
    public final int percentage; // 0..100, or -1 for unknown

    public final boolean isCharging; // actively charging
    public final boolean isFull; // battery is full, plugged in
    public final boolean isPlugged; // plugged in
    public final boolean isLow; // True if the battery intent reported low battery

    private final long msUntilCharged;

    public BatteryState(
            int percent,
            boolean charging,
            boolean isFull,
            boolean isPlugged,
            boolean isLow,
            long chargeTimeMs) {
        this.percentage = percent;
        this.isCharging = charging;
        this.isFull = isFull;
        this.isPlugged = isPlugged;
        this.isLow = isLow;
        this.msUntilCharged = chargeTimeMs;
    }

    @IntRange(from = -1)
    public long getMinutesToFullCharge() {
        if (msUntilCharged < 0) {
            return -1L; // Typically -1 if unknown
        } else {
            // Round up to the nearest minute. E.g., 1000 ms to 1 minute, 60001 ms to 2 minutes
            long minutes = TimeUnit.MILLISECONDS.toMinutes(msUntilCharged);
            if (msUntilCharged > 0 && msUntilCharged % 60000 != 0) {
                minutes++;
            }
            return minutes;
        }
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(
                Locale.getDefault(),
                "BatteryState{percentage=%d, isCharging=%b, isPlugged=%b, isFull=%b, isLow=%b, msUntilCharged=%d (%d minutes)}",
                percentage,
                isCharging,
                isPlugged,
                isFull,
                isLow,
                msUntilCharged,
                getMinutesToFullCharge());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatteryState that = (BatteryState) o;
        return percentage == that.percentage
                && isCharging == that.isCharging
                && isFull == that.isFull
                && isPlugged == that.isPlugged
                && isLow == that.isLow
                && msUntilCharged == that.msUntilCharged;
    }

    @Override
    public int hashCode() {
        return Objects.hash(percentage, isCharging, isFull, isPlugged, isLow, msUntilCharged);
    }
}
