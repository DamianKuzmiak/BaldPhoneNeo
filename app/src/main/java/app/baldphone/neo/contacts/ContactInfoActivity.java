package app.baldphone.neo.contacts;

import static app.baldphone.neo.utils.SignalUtils.startSignalConversation;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import app.baldphone.neo.contacts.Contact.TaggedData;
import app.baldphone.neo.helpers.UiStateHolder;
import app.baldphone.neo.utils.PhoneUtils;
import app.baldphone.neo.utils.WhatsappUtils;
import app.baldphone.neo.views.TitleBarView;
import app.baldphone.neo.views.popup.ActionCallback;
import app.baldphone.neo.views.popup.ActionId;
import app.baldphone.neo.views.popup.ActionPopup;
import app.baldphone.neo.views.popup.Divider;
import app.baldphone.neo.views.popup.MenuState;
import app.baldphone.neo.views.popup.SimpleAction;
import app.baldphone.neo.views.popup.ToggleAction;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.activities.BaldActivity;
import com.bald.uriah.baldphone.activities.DialerActivity;
import com.bald.uriah.baldphone.activities.contacts.AddContactActivity;
import com.bald.uriah.baldphone.adapters.CallsRecyclerViewAdapter;
import com.bald.uriah.baldphone.databases.calls.Call;
import com.bald.uriah.baldphone.utils.BDB;
import com.bald.uriah.baldphone.utils.BDialog;
import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.BaldToast;
import com.bald.uriah.baldphone.utils.S;
import com.bald.uriah.baldphone.views.BaldImageButton;
import com.bald.uriah.baldphone.views.BaldPictureTextButton;
import com.bald.uriah.baldphone.views.ScrollingHelper;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Simple Activity for interacting with a Contact */
public class ContactInfoActivity extends BaldActivity {
    public static final String CONTACT_LOOKUP_KEY = "contactLookupKey";
    public static final int REQUEST_CHECK_CHANGE = 97;

    private static final String TAG = ContactInfoActivity.class.getSimpleName();
    private final UiStateHolder uiState = UiStateHolder.getInstance();
    private ContactInfoViewModel viewModel;
    private ImageView photoImageView;
    private TextView contactNameTextView;
    private LinearLayout container;
    private LayoutInflater inflater;
    private ActionPopup popup;
    private ActionCallback actionPopupCallback;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String lookupKey = getIntent().getStringExtra(CONTACT_LOOKUP_KEY);
        if (TextUtils.isEmpty(lookupKey)) {
            Log.e(TAG, "Missing contactLookupKey from intent: " + getIntent());
            finish();
            return;
        }

        setContentView(R.layout.activity_contact_info);
        initViews();
        actionPopupCallback = new SimpleActionPopupCallback();

        viewModel = new ViewModelProvider(this).get(ContactInfoViewModel.class);
        observeViewModel();
        viewModel.loadContact(lookupKey);
    }

    private void initViews() {
        inflater = getLayoutInflater();
        container = findViewById(R.id.ll_contact_info_container);
        photoImageView = findViewById(R.id.avatar);
        contactNameTextView = findViewById(R.id.name);
        TitleBarView titleBarView = findViewById(R.id.titleBar);

        titleBarView.setOnMoreClickListener(this::showPopup);
        titleBarView.showMoreButton();
    }

    @Override
    public void startActivity(Intent intent) {
        try {
            super.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showErrorToast("Activity not found: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void finish() {
        if (viewModel != null
                && Boolean.TRUE.equals(viewModel.contactChangedLiveData().getValue())) {
            setResult(RESULT_OK);
        }
        super.finish();
    }

    private void showPopup(@NonNull View anchor) {
        if (viewModel == null) return;

        boolean isFavorite = Boolean.TRUE.equals(viewModel.isFavoriteLiveData().getValue());
        boolean isPinned = Boolean.TRUE.equals(viewModel.isPinnedLiveData().getValue());

        if (popup == null) popup = ActionPopupBuilder.build(this, isFavorite, isPinned);
        popup.show(anchor, new MenuState(isFavorite, isPinned), actionPopupCallback);
    }

    private void observeViewModel() {
        viewModel.contactInfoLiveData().observe(this, this::renderContactInfo);
        viewModel
                .isFavoriteLiveData()
                .observe(
                        this,
                        newFavoriteStatus -> {
                            boolean isFavorite = Boolean.TRUE.equals(newFavoriteStatus);
                            updateFavoriteStatus(isFavorite);
                            //            BaldToast.simpleBottom(getApplicationContext(), "Favorite
                            // updated");
                        });
        viewModel.callHistoryLiveData().observe(this, this::renderRecentCalls);
        viewModel
                .contactNotFoundEvent()
                .observe(
                        this,
                        unused -> {
                            showErrorToast("No contact found!");
                            finish();
                        });
        viewModel.contactDeletedEvent().observe(this, unused -> finish());
    }

    private void renderContactInfo(Contact contact) {
        contactNameTextView.setText(contact.getName());

        container.removeAllViews();
        if (contact.hasPhone()) {
            new ContactSectionInflater().inflatePhones(contact.getPhones());
        }
        if (contact.hasWhatsapp()) {
            new ContactSectionInflater().inflateWhatsapp(contact.getWhatsappNumbers());
        }
        if (contact.hasSignal()) {
            new ContactSectionInflater().inflateSignal(contact.getSignalNumbers());
        }
        if (contact.hasMail()) {
            new ContactSectionInflater().inflateMails(contact.getEmails());
        }
        if (contact.hasAddress()) {
            new ContactSectionInflater().inflateAddresses(contact.getAddresses());
        }
        if (contact.hasNote()) {
            new ContactSectionInflater().inflateNote(contact.getNote());
        }

        loadPhoto(contact.getPhoto()); // the least important field
    }

    private void updateFavoriteStatus(boolean isFavorite) {
        contactNameTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, isFavorite ? R.drawable.star_gold : 0, 0);
    }

    private void renderRecentCalls(List<Call> calls) {
        View existing = container.findViewWithTag("history_section");
        if (existing != null) container.removeView(existing);
        if (calls != null && !calls.isEmpty()) inflateHistory(calls);
    }

    private void loadPhoto(@Nullable String uri) {
        if (isDestroyed() || isFinishing()) return;

        if (TextUtils.isEmpty(uri)) {
            photoImageView.setVisibility(View.GONE);
            return;
        }

        photoImageView.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(uri)
                .fallback(R.drawable.face_in_recent_calls)
                .error(R.drawable.error_on_background)
                .into(photoImageView);
    }

    private void inflateHistory(List<Call> calls) {
        View view = inflater.inflate(R.layout.contact_history, container, false);
        view.setTag("history_section");

        RecyclerView recyclerView = view.findViewById(R.id.child);
        BaldPictureTextButton show = view.findViewById(R.id.bt_show);

        DividerItemDecoration divider =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        Drawable d = getDrawable(R.drawable.ll_divider);
        if (d != null) divider.setDrawable(d);
        recyclerView.addItemDecoration(divider);
        recyclerView.setAdapter(new CallsRecyclerViewAdapter(new ArrayList<>(calls), this));

        show.setOnClickListener(
                v -> {
                    boolean newVisible = !uiState.isCallLogVisible();
                    uiState.setCallLogVisible(newVisible);
                    updateHistoryVisibility(view, newVisible);
                });

        container.addView(view);
        updateHistoryVisibility(view, uiState.isCallLogVisible());
    }

    private void updateHistoryVisibility(@NonNull View historySection, boolean visible) {
        ScrollingHelper scrollHelper = historySection.findViewById(R.id.scrolling_helper);
        BaldPictureTextButton show = historySection.findViewById(R.id.bt_show);

        if (visible) {
            scrollHelper.setVisibility(View.VISIBLE);
            show.getImageView().setImageResource(R.drawable.drop_up_on_button);
            show.getTextView().setText(R.string.hide);
        } else {
            scrollHelper.setVisibility(View.GONE);
            show.getImageView().setImageResource(R.drawable.drop_down_on_button);
            show.getTextView().setText(R.string.show);
        }
    }

    @Override
    protected int requiredPermissions() {
        return PERMISSION_NONE;
    }

    /* ---------------------------------------------------------------------- */
    /* -------------------------- INNER CLASSES ----------------------------- */
    /* ---------------------------------------------------------------------- */

    private void openMap(String address) {
        if (TextUtils.isEmpty(address)) return;
        Uri uri = Uri.parse("geo:0,0?q=" + Uri.encode(address));
        Intent map = new Intent(Intent.ACTION_VIEW, uri);
        if (map.resolveActivity(getPackageManager()) != null) startActivity(map);
        else showErrorToast("No map app found");
    }

    private void sendEmail(String email) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email, null));
        if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
        else showErrorToast("No email app found");
    }

    private void shareContact() {
        if (viewModel == null) return;

        Contact c = viewModel.contactInfoLiveData().getValue();
        if (c == null) return;

        Uri vcardUri =
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, c.getLookupKey());
        Intent share =
                new Intent(Intent.ACTION_SEND)
                        .setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE)
                        .putExtra(Intent.EXTRA_STREAM, vcardUri)
                        .putExtra(Intent.EXTRA_SUBJECT, c.getName());
        S.share(this, share);
    }

    private void editContactDetails() {
        if (viewModel == null) return;
        Contact c = viewModel.contactInfoLiveData().getValue();
        if (c == null) return;

        Intent edit =
                new Intent(this, AddContactActivity.class)
                        .putExtra(CONTACT_LOOKUP_KEY, c.getLookupKey());
        startActivity(edit);
    }

    private void showDeleteConfirmationDialog() {
        if (viewModel == null) return;
        Contact c = viewModel.contactInfoLiveData().getValue();
        if (c == null) return;

        BDB.from(this)
                .addFlag(BDialog.FLAG_YES | BDialog.FLAG_CANCEL)
                .setSubText(getString(R.string.are_you_sure_you_want_to_delete___, c.getName()))
                .setPositiveButtonListener(
                        params -> {
                            viewModel.deleteContact();
                            return true;
                        })
                .show();
    }

    private void makeCall(@NonNull String number) {
        boolean needsConfirmation =
                BPrefs.get(this)
                        .getBoolean(
                                BPrefs.CALL_CONFIRMATION_KEY,
                                BPrefs.CALL_CONFIRMATION_DEFAULT_VALUE);
        if (!needsConfirmation) {
            dialNumber(number);
            return;
        }

        Contact contact = viewModel != null ? viewModel.contactInfoLiveData().getValue() : null;
        String message = (contact != null)
            ? getString(R.string.dialog_call_message_full, contact.getName(), number)
            : getString(R.string.dialog_call_message_number_only, number);

        BDB.from(this)
                .setTitle(R.string.dialog_call_title)
                .setSubText(message)
                .addFlag(BDialog.FLAG_YES | BDialog.FLAG_NO)
                .setPositiveButtonListener(
                        params -> {
                            dialNumber(number);
                            return true;
                        })
                .show();
    }

    private void dialNumber(@NonNull String number) {
        DialerActivity.call(number, this, false);
    }

    private void showErrorToast(CharSequence message) {
        BaldToast.from(this).setType(BaldToast.TYPE_ERROR).setText(message).show();
    }

    /** Handles popup creation logic */
    private static class ActionPopupBuilder {
        static ActionPopup build(Context ctx, boolean isFavorite, boolean isPinned) {
            ActionPopup popup = new ActionPopup(ctx);
            popup.setActions(
                    Arrays.asList(
                            new ToggleAction(
                                    ActionId.TOGGLE_FAVORITE,
                                    R.drawable.star_on_button,
                                    R.drawable.star_remove_on_button,
                                    R.string.remove_from_favorite,
                                    R.string.add_to_favorite,
                                    isFavorite),
                            new ToggleAction(
                                    ActionId.TOGGLE_HOME,
                                    R.drawable.remove_on_button,
                                    R.drawable.add_on_button,
                                    R.string.remove_from_home,
                                    R.string.add_to_home,
                                    isPinned),
                            new Divider(),
                            new SimpleAction(
                                    ActionId.SHARE, R.drawable.share_on_background, R.string.share),
                            new SimpleAction(
                                    ActionId.EDIT, R.drawable.edit_on_background, R.string.edit),
                            new SimpleAction(
                                    ActionId.DELETE,
                                    R.drawable.delete_on_background,
                                    R.string.delete)));
            return popup;
        }
    }

    /** Popup callback */
    private class SimpleActionPopupCallback implements ActionCallback {
        @Override
        public void onEdit() {
            editContactDetails();
        }

        @Override
        public void onShare() {
            shareContact();
        }

        @Override
        public void onDelete() {
            showDeleteConfirmationDialog();
        }

        @Override
        public void onFavoriteToggled(boolean nowFavorite) {
            if (viewModel == null) return;
            viewModel.toggleFavorite();
        }

        @Override
        public void onHomeToggled(boolean nowPinned) {
            if (viewModel == null) return;
            viewModel.toggleHomeScreenPin();
            BaldToast.simpleBottom(getApplicationContext(), "Home updated: " + nowPinned);
        }
    }

    /** Handles contact section inflation */
    private class ContactSectionInflater {
        private final ContactFieldBuilder builder = new ContactFieldBuilder();

        void inflatePhones(@NonNull List<TaggedData> phones) {
            String region = PhoneUtils.getDeviceRegion(ContactInfoActivity.this);
            boolean isFirst = true;

            for (TaggedData phone : phones) {
                String value = phone.value();
                CharSequence label =
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                getResources(), phone.type(), getString(R.string.custom));
                String formatted = PhoneUtils.formatForDisplay(value, region);
                builder.create(label, formatted)
                        .setBold(isFirst)
                        .primary(
                                R.drawable.message_on_button,
                                R.color.blue,
                                R.string.message,
                                v -> S.sendMessage(value, ContactInfoActivity.this))
                        .secondary(
                                R.drawable.phone_on_button,
                                R.color.green,
                                R.string.call,
                                v -> makeCall(value))
                        .add();
                isFirst = false;
            }
        }

        void inflateWhatsapp(@NonNull List<String> numbers) {
            for (String jid : numbers) {
                String formatted = WhatsappUtils.getPhoneNumberFromJid(jid);
                builder.create(getString(R.string.whatsapp), formatted != null ? formatted : jid)
                        .primary(
                                R.drawable.ic_whatsapp_call_lime,
                                null,
                                R.string.open,
                                v -> {
                                    try {
                                        WhatsappUtils.startWhatsAppVoiceCall(
                                                ContactInfoActivity.this, jid);
                                    } catch (Exception e) {
                                        Toast.makeText(
                                                        ContactInfoActivity.this,
                                                        e.getMessage(),
                                                        Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                })
                        .add();
            }
        }

        void inflateSignal(@NonNull List<String> numbers) {
            for (String number : numbers) {
                builder.create("Signal", number)
                        .primary(
                                R.drawable.ic_signal_azure,
                                null,
                                R.string.open,
                                v -> {
                                    try {
                                        startSignalConversation(ContactInfoActivity.this, number);
                                    } catch (Exception e) {
                                        Toast.makeText(
                                                        ContactInfoActivity.this,
                                                        e.getMessage(),
                                                        Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                })
                        .add();
            }
        }

        void inflateMails(List<TaggedData> mails) {
            for (TaggedData mail : mails) {
                builder.create(getString(R.string.mail), mail.value())
                        .primary(
                                R.drawable.mail_on_button,
                                null,
                                R.string.send,
                                v -> sendEmail(mail.value()))
                        .add();
            }
        }

        void inflateAddresses(List<TaggedData> addresses) {
            for (TaggedData address : addresses) {
                CharSequence label =
                        ContactsContract.CommonDataKinds.StructuredPostal.getTypeLabel(
                                getResources(), address.type(), getString(R.string.custom));
                builder.create(getString(R.string.address) + label, address.value())
                        .primary(
                                R.drawable.location_on_button,
                                null,
                                R.string.location,
                                v -> openMap(address.value()))
                        .add();
            }
        }

        void inflateNote(String note) {
            builder.create(getString(R.string.note), note).add();
        }
    }

    /** Reusable builder for contact fields */
    private class ContactFieldBuilder {
        private CharSequence label;
        private CharSequence value;
        private boolean bold;
        private Integer iconPrim, tintPrim, descPrim;
        private View.OnClickListener onPrim;
        private Integer iconSec, tintSec, descSec;
        private View.OnClickListener onSec;

        ContactFieldBuilder create(CharSequence label, CharSequence value) {
            this.label = label;
            this.value = value;
            this.bold = false;
            this.iconPrim = null;
            this.tintPrim = null;
            this.descPrim = null;
            this.onPrim = null;
            this.iconSec = null;
            this.tintSec = null;
            this.descSec = null;
            this.onSec = null;
            return this;
        }

        ContactFieldBuilder setBold(boolean isBold) {
            this.bold = isBold;
            return this;
        }

        ContactFieldBuilder primary(
                @DrawableRes Integer icon,
                @ColorRes Integer tint,
                @StringRes Integer desc,
                View.OnClickListener click) {
            this.iconPrim = icon;
            this.tintPrim = tint;
            this.descPrim = desc;
            this.onPrim = click;
            return this;
        }

        ContactFieldBuilder secondary(
                @DrawableRes Integer icon,
                @ColorRes Integer tint,
                @StringRes Integer desc,
                View.OnClickListener click) {
            this.iconSec = icon;
            this.tintSec = tint;
            this.descSec = desc;
            this.onSec = click;
            return this;
        }

        void add() {
            View view = inflater.inflate(R.layout.item_contact_field, container, false);

            setupButton(
                    view.findViewById(R.id.btnActionPrimary), iconPrim, tintPrim, descPrim, onPrim);
            setupButton(
                    view.findViewById(R.id.btnActionSecondary), iconSec, tintSec, descSec, onSec);

            ((TextView) view.findViewById(R.id.fieldLabel)).setText(label);
            TextView valueTv = view.findViewById(R.id.fieldValue);
            valueTv.setText(value);
            valueTv.setTypeface(null, bold ? Typeface.BOLD : Typeface.NORMAL);

            container.addView(view);
        }

        private void setupButton(
                BaldImageButton btn,
                Integer icon,
                Integer tint,
                Integer desc,
                View.OnClickListener listener) {
            if (icon == null) return;
            btn.setOnClickListener(listener);
            btn.setImageResource(icon);
            if (tint != null)
                btn.setColorFilter(ContextCompat.getColor(ContactInfoActivity.this, tint));
            if (desc != null) btn.setContentDescription(getString(desc));
            btn.setVisibility(View.VISIBLE);
        }
    }
}
