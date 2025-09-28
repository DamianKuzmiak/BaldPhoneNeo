package app.baldphone.neo.views.popup;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public class SimpleAction implements Action {
    private final ActionId id;
    private final @DrawableRes int iconRes;
    private final @StringRes int labelRes;
    private final boolean destructive;
    private boolean enabled = true;

    public SimpleAction(ActionId id, int iconRes, int labelRes) {
        this(id, iconRes, labelRes, false);
    }

    public SimpleAction(ActionId id, int iconRes, int labelRes, boolean destructive) {
        this.id = id;
        this.iconRes = iconRes;
        this.labelRes = labelRes;
        this.destructive = destructive;
    }

    @Override
    public ActionId id() {
        return id;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    public SimpleAction setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public int iconRes() {
        return iconRes;
    }

    public int labelRes() {
        return labelRes;
    }

    public boolean destructive() {
        return destructive;
    }
}
