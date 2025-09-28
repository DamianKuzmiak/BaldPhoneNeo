package app.baldphone.neo.views.popup;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

public class ToggleAction implements Action {
    private final ActionId id;
    private final @DrawableRes int iconOnRes;
    private final @DrawableRes int iconOffRes;
    private final @StringRes int labelOnRes;
    private final @StringRes int labelOffRes;
    private boolean checked;
    private boolean enabled = true;

    public ToggleAction(
            ActionId id,
            int iconOnRes,
            int iconOffRes,
            int labelOnRes,
            int labelOffRes,
            boolean checked) {
        this.id = id;
        this.iconOnRes = iconOnRes;
        this.iconOffRes = iconOffRes;
        this.labelOnRes = labelOnRes;
        this.labelOffRes = labelOffRes;
        this.checked = checked;
    }

    @Override
    public ActionId id() {
        return id;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    public ToggleAction setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public int currentIconRes() {
        return checked ? iconOnRes : iconOffRes;
    }

    public int currentLabelRes() {
        return checked ? labelOnRes : labelOffRes;
    }
}
