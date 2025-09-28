package app.baldphone.neo.views.popup;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bald.uriah.baldphone.R;

import java.util.ArrayList;
import java.util.List;

public class ActionPopup {

    private final PopupWindow popupWindow;
    private final ActionPopupAdapter adapter;

    private final List<Action> baseActions = new ArrayList<>();

    private ActionCallback callback;

    public ActionPopup(@NonNull Context context) {

        ViewGroup temporaryParent = new FrameLayout(context);
        final View contentView =
                LayoutInflater.from(context)
                        .inflate(
                                R.layout.view_action_popup,
                                temporaryParent, // for LayoutParams resolution
                                false // false, because we set content on PopupWindow manually
                                );
        RecyclerView recyclerView = contentView.findViewById(R.id.recycler);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new ActionPopupAdapter();
        DividerItemDecoration decor =
                new DividerItemDecoration(context, layoutManager.getOrientation());
        recyclerView.addItemDecoration(decor);
        recyclerView.setAdapter(adapter);

        popupWindow = new PopupWindow(contentView, WRAP_CONTENT, WRAP_CONTENT, true);
        popupWindow.setOutsideTouchable(true);
    }

    /**
     * Show anchored to a view; applies current state to known toggles and appends [divider + Exit].
     */
    public void show(
            @NonNull View anchor, @NonNull MenuState state, @NonNull ActionCallback callback) {
        this.callback = callback;

        List<Object> rows = new ArrayList<>();
        for (Action baseAction : baseActions) {
            if (baseAction instanceof ToggleAction ta) {
                ToggleAction t = copyToggle(ta); // avoid mutating caller's instance
                if (t.id() == ActionId.TOGGLE_FAVORITE) t.setChecked(state.isFavorite);
                if (t.id() == ActionId.TOGGLE_HOME) t.setChecked(state.isPinnedToHome);
                rows.add(t);
            } else if (baseAction instanceof SimpleAction s) {
                rows.add(copySimple(s));
            } else if (baseAction.id() == ActionId.DIVIDER ) {
                rows.add(ActionPopupAdapter.Divider.INSTANCE);
            }
        }
        // Divider + Exit
        rows.add(ActionPopupAdapter.Divider.INSTANCE);
        rows.add(new SimpleAction(ActionId.EXIT,
                android.R.drawable.ic_menu_close_clear_cancel,
                android.R.string.cancel));

        adapter.submitRows(
                rows,
                (row, pos) -> {
                    if (row instanceof SimpleAction simpleAction) {
                        dispatchSimple(simpleAction);
                        dismiss();
                    } else if (row instanceof ToggleAction t) {
                        boolean nowChecked = !t.isChecked();
                        t.setChecked(nowChecked);
                        adapter.notifyItemChanged(pos);
                        if (t.id() == ActionId.TOGGLE_FAVORITE)
                            callback.onFavoriteToggled(nowChecked);
                        if (t.id() == ActionId.TOGGLE_HOME) callback.onHomeToggled(nowChecked);
                    }
                });

        popupWindow.showAsDropDown(anchor);
    }

    /**
     * Set the base actions for this Activity (do NOT include Exit or divider; they're added
     * automatically).
     */
    public void setActions(@NonNull List<Action> actions) {
        baseActions.clear();
        baseActions.addAll(actions);
    }

    /** Enable or disable an item at runtime. */
    public void setItemEnabled(@NonNull ActionId id, boolean enabled) {
        if (adapter != null) adapter.setItemEnabled(id, enabled);
    }

    /** Sets the listener to be called when the popup window is dismissed. */
    public void setOnDismissListener(@Nullable PopupWindow.OnDismissListener l) {
        if (popupWindow != null) popupWindow.setOnDismissListener(l);
    }

    private void dispatchSimple(@NonNull SimpleAction sa) {
        if (sa.id() == ActionId.EXIT) {
            dismiss();
            return;
        }

        if (callback == null) return;
        switch (sa.id()) {
            case EDIT:
                callback.onEdit();
                announce(R.string.edit);
                break;
            case SHARE:
                callback.onShare();
                announce(R.string.share);
                break;
            case DELETE:
                callback.onDelete();
                announce(R.string.delete);
                break;
            default:
                // no-op for unknown simple ids in this example
                break;
        }
    }

    private void announce(int stringRes) {
        View content = popupWindow.getContentView();
        if (content != null) {
            content.announceForAccessibility(content.getContext().getString(stringRes));
        }
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) popupWindow.dismiss();
    }

    // Helpers: copy items so per-show state doesn't leak back
    private static SimpleAction copySimple(SimpleAction s) {
        SimpleAction c = new SimpleAction(s.id(), s.iconRes(), s.labelRes(), s.destructive());
        c.setEnabled(s.enabled());
        return c;
    }

    private static ToggleAction copyToggle(ToggleAction t) {
        ToggleAction c =
                new ToggleAction(
                        t.id(),
                        getField(t, true),
                        getField(t, false),
                        getLabel(t, true),
                        getLabel(t, false),
                        t.isChecked());
        c.setEnabled(t.enabled());
        return c;
    }

    // Extract via current API (helper methods since fields are private)
    private static int getField(ToggleAction t, boolean on) {
        // Hack via current API: we can't access iconOn/off directly; use behavior:
        boolean original = t.isChecked();
        t.setChecked(on);
        int res = t.currentIconRes();
        t.setChecked(original);
        return res;
    }

    private static int getLabel(ToggleAction t, boolean on) {
        boolean original = t.isChecked();
        t.setChecked(on);
        int res = t.currentLabelRes();
        t.setChecked(original);
        return res;
    }
}
