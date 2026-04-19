/*
 * Copyright 2019 Uriah Shaul Mandel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bald.uriah.baldphone.activities;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.databases.contacts.MiniContact;
import com.bald.uriah.baldphone.databases.home_screen_pins.HomeScreenPinHelper;

import app.baldphone.neo.activities.ContactsActivity;
import app.baldphone.neo.contacts.data.ContactsDataSource;
import app.baldphone.neo.features.calls.CallUiHelper;
import app.baldphone.neo.utils.PhoneNumberUtils;

import com.bald.uriah.baldphone.utils.BaldToast;
import com.bald.uriah.baldphone.views.BaldLinearLayoutButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.bald.uriah.baldphone.databases.home_screen_pins.HomeScreenPinHelper.SHARED_PREFS_KEY;

public class SOSActivity extends BaldActivity {
    private static final int MAX_PINNED_CONTACTS = 2;
    private BaldLinearLayoutButton[] ec;
    private BaldLinearLayoutButton ecReal;

    private static void setupEC(BaldLinearLayoutButton baldLinearLayoutButton, MiniContact miniContact) {
        if (miniContact.photo != null)
            ((ImageView) baldLinearLayoutButton.getChildAt(0)).setImageURI(Uri.parse(miniContact.photo));
        else
            ((ImageView) baldLinearLayoutButton.getChildAt(0)).setImageResource(R.drawable.face_on_button);

        ((TextView) baldLinearLayoutButton.getChildAt(1)).setText(miniContact.name);
        baldLinearLayoutButton.setOnClickListener(v -> {
            String phoneNumber = new ContactsDataSource(v.getContext()).resolvePhoneNumberBlocking(miniContact.lookupKey);
            if (phoneNumber != null) {
                CallUiHelper.INSTANCE.callDirectly(v.getContext(), phoneNumber);
            } else {
                BaldToast.error(v.getContext(), "Could not find a phone number for this contact");
            }
        });
    }    private final ActivityResultLauncher<Intent> pickContactLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    final String lookupKey = result.getData().getStringExtra(ContactsActivity.EXTRA_CONTACT_LOOKUP_KEY);
                    if (lookupKey != null) {
                        PinHelper.pinContact(this, lookupKey);
                        refreshContacts();
                    }
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!checkPermissions(this, requiredPermissions()))
            return;
        setContentView(R.layout.activity_sos);
        ec = new BaldLinearLayoutButton[]{findViewById(R.id.ec1), findViewById(R.id.ec2)};
        ecReal = findViewById(R.id.ec_real);
    }

    @Override
    protected void onStart() {
        super.onStart();
        refreshContacts();
    }

    private void refreshContacts() {
        final View.OnClickListener addContactListener = v -> {
            Intent intent = new Intent(getApplicationContext(), ContactsActivity.class);
            intent.putExtra(ContactsActivity.EXTRA_PICK_CONTACT, true);
            pickContactLauncher.launch(intent);
        };
        final List<MiniContact> miniContacts = PinHelper.getAllPinnedContacts(this);
        final int size = miniContacts == null ? 0 : miniContacts.size();
        for (int i = 0; i < MAX_PINNED_CONTACTS; i++) {
            if (size > i)
                setupEC(ec[i], miniContacts.get(i));
            else
                ec[i].setOnClickListener(addContactListener);
        }

        ecReal.setOnClickListener((v) -> callEmergencyNumber());
    }

    private void callEmergencyNumber() {
        String emergencyNumber = PhoneNumberUtils.INSTANCE.getPrimaryEmergencyNumber(this);
        CallUiHelper.INSTANCE.callDirectly(this, emergencyNumber);
    }

    @Override
    public void finish() {
        super.finish();
//        overridePendingTransition(R.anim.nothing, R.anim.slide_out_up);
    }

    @Override
    protected int requiredPermissions() {
        return PERMISSION_CALL_PHONE | PERMISSION_READ_CONTACTS;
    }

    public static class PinHelper {
        /**
         * @return true if succeeded
         */
        public static boolean pinContact(Context context, String lookupKey) {
            final SharedPreferences sharedPreferences = context.getSharedPreferences(HomeScreenPinHelper.PinnedContactPreferences.KEY, Context.MODE_PRIVATE);
            final Set<String> befSet = sharedPreferences.getStringSet(HomeScreenPinHelper.PinnedContactPreferences.SOS_KEY, null);
            final Set<String> newSet;
            if (befSet == null)
                newSet = new HashSet<>();
            else {
                if (befSet.size() >= SOSActivity.MAX_PINNED_CONTACTS)
                    return false;
                else
                    newSet = new HashSet<>(befSet);
            }
            newSet.add(lookupKey);
            sharedPreferences.edit().putStringSet(HomeScreenPinHelper.PinnedContactPreferences.SOS_KEY, newSet).apply();
            return true;
        }

        public static boolean isPinned(Context context, String lookupKey) {
            Set<String> set = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE).getStringSet(HomeScreenPinHelper.PinnedContactPreferences.SOS_KEY, null);
            return set != null && set.contains(lookupKey);
        }

        public static void removeContact(Context context, String lookupKey) {
            final SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE);
            final Set<String> befSet = sharedPreferences.getStringSet(HomeScreenPinHelper.PinnedContactPreferences.SOS_KEY, null);
            final Set<String> newSet;
            if (befSet == null)
                newSet = new HashSet<>();
            else
                newSet = new HashSet<>(befSet);
            newSet.remove(lookupKey);
            sharedPreferences.edit().putStringSet(HomeScreenPinHelper.PinnedContactPreferences.SOS_KEY, newSet).apply();
        }

        /**
         * @param context
         * @return sorted list of lookup keys
         */
        private static List<MiniContact> getAllPinnedContacts(Context context) {
            final Set<String> lookupKeys = context.getSharedPreferences(SHARED_PREFS_KEY, Context.MODE_PRIVATE).getStringSet(HomeScreenPinHelper.PinnedContactPreferences.SOS_KEY, null);
            if (lookupKeys == null)
                return null;
            final List<MiniContact> ret = new ArrayList<>(lookupKeys.size());
            final ContentResolver contentResolver = context.getContentResolver();
            for (String lookupKey :
                    lookupKeys) {
                try (Cursor cursor = contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        MiniContact.PROJECTION,
                        ContactsContract.Data.LOOKUP_KEY + " = ?",
                        new String[]{
                                lookupKey
                        }, null)) {
                    if (cursor.moveToFirst()) {
                        ret.add(new MiniContact(
                                lookupKey,
                                cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)),
                                cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)),
                                cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts._ID)),
                                cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.STARRED)) == 1
                        ));
                    } else {
                        removeContact(context, lookupKey);
                    }

                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
            Collections.sort(ret, (o1, o2) -> o1.name.compareTo(o2.name));
            return ret;
        }
    }


}
