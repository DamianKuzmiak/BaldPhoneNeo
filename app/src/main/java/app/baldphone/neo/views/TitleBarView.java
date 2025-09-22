package app.baldphone.neo.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.bald.uriah.baldphone.R;

public class TitleBarView extends RelativeLayout {

    private TextView txtTitle;
    private ImageButton btnExit;
    private ImageButton btnMore;

    public TitleBarView(Context context) {
        this(context, null);
    }

    public TitleBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.view_title_bar, this, true);

        RelativeLayout rootLayout = findViewById(R.id.titleBarRoot);
        txtTitle = findViewById(R.id.txtTitle);
        btnExit = findViewById(R.id.btnExit);
        btnMore = findViewById(R.id.btnMore);

        // Default Exit behavior → acts like Back button
        btnExit.setOnClickListener(
                v -> {
                    Context viewContext = getContext();
                    if (viewContext instanceof ComponentActivity activity) {
                        activity.getOnBackPressedDispatcher().onBackPressed();
                    } else {
                        Log.e("TitleBarView", "Context is not an Activity!");
                    }
                });

        if (attrs != null) {
            String title;
            int bgColor;
            try (TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TitleBarView)) {
                title = a.getString(R.styleable.TitleBarView_titleBarTitle);
                bgColor = a.getColor(R.styleable.TitleBarView_titleBarBackgroundColor, -1);
            }
            if (title != null) setTitle(title);
            if (bgColor != -1) rootLayout.setBackgroundColor(bgColor);
        }
    }

    /**
     * Sets the title text displayed in the title bar using a string resource ID.
     *
     * @param titleResId The string resource ID for the title.
     */
    public void setTitle(@StringRes int titleResId) {
        setTitle(getContext().getString(titleResId));
    }

    /**
     * Sets the title text displayed in the title bar.
     *
     * @param title The text to display as the title. Must not be null.
     */
    public void setTitle(@NonNull String title) {
        txtTitle.setText(title);
    }

    /** Makes the 'More settings' button visible, as it is not visible by default. */
    public void showMoreButton() {
        btnMore.setVisibility(VISIBLE);
    }

    /**
     * Registers a callback to be invoked when the 'More' options button is clicked.
     *
     * <p>Note: After setting the listener, you must call {@link #showMoreButton()} to make the
     * button visible, as it is not visible by default.
     *
     * @param listener The callback that will run. Can be null to clear the listener.
     */
    public void setOnMoreClickListener(@Nullable OnClickListener listener) {
        btnMore.setOnClickListener(listener);
    }

    /**
     * Registers a callback to be invoked when the 'Exit' (or Back) button is clicked. The default
     * behavior simulates a back press. If a listener is provided, it will override the default
     * behavior.
     *
     * @param listener The callback that will run. Can be null to restore default behavior.
     */
    public void setOnExitClickListener(@Nullable OnClickListener listener) {
        btnExit.setOnClickListener(listener);
    }
}
