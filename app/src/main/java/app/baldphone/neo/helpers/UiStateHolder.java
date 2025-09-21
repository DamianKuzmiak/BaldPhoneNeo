package app.baldphone.neo.helpers;

/**
 * Holds global, temporary UI state for the application.
 *
 * <p>Used to maintain UI state that should not be persisted permanently but needs to be accessible
 * across different components or survive configuration changes. It serves as a lightweight
 * alternative to a ViewModel for simple, global state.
 */
public class UiStateHolder {
    private static UiStateHolder instance;
    private boolean isCallLogVisible = false;

    private UiStateHolder() {}

    public static synchronized UiStateHolder getInstance() {
        if (instance == null) instance = new UiStateHolder();
        return instance;
    }

    public boolean isCallLogVisible() {
        return isCallLogVisible;
    }

    public void setCallLogVisible(boolean visible) {
        this.isCallLogVisible = visible;
    }
}
