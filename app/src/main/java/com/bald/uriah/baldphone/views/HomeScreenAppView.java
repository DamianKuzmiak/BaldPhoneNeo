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

package com.bald.uriah.baldphone.views;

import android.content.ComponentName;
import android.content.Intent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.StringRes;

import app.baldphone.neo.features.contacts.ui.ContactDetailsActivity;
import app.baldphone.neo.launcher.apps.AppIconBinder;
import app.baldphone.neo.launcher.apps.data.db.AppEntry;
import app.baldphone.neo.utils.IntentUtilsKt;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.databases.contacts.MiniContact;
import com.bald.uriah.baldphone.utils.S;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

public class HomeScreenAppView {
    public final ImageView iv_icon;
    private final BaldLinearLayoutButton child;
    private final TextView tv_name;

    public HomeScreenAppView(BaldLinearLayoutButton child) {
        this.child = child;
        tv_name = child.findViewById(R.id.et_name);
        iv_icon = child.findViewById(R.id.iv_icon);
    }

    public void setText(@StringRes int resId) {
        tv_name.setText(resId);
    }

    public void setText(CharSequence charSequence) {
        tv_name.setText(charSequence);
    }

    public void setIntent(final ComponentName componentName, final long userId) {
        child.setOnClickListener(v -> S.startComponentName(v.getContext(), componentName, userId));
    }

    public void setIntent(final String contactLookupKey) {
        child.setOnClickListener(v -> {
            Intent contactDetailsIntent =
                new Intent(v.getContext(), ContactDetailsActivity.class)
                    .putExtra(ContactDetailsActivity.CONTACT_LOOKUP_KEY, contactLookupKey);
            IntentUtilsKt.startActivityWithNewTaskClear(v.getContext(), contactDetailsIntent);
        });
    }

    public void setVisibility(int visibility) {
        child.setVisibility(visibility);
    }

    public void bind(final Object pinnable) {
        if (pinnable instanceof AppEntry appEntry) {
            setText(appEntry.getLabel());
            AppIconBinder.loadPic(appEntry, iv_icon);
            final ComponentName compName = ComponentName.unflattenFromString(appEntry.getComponentName());
            if (compName != null) {
                setIntent(compName, appEntry.getUserId());
            }
        } else if (pinnable instanceof MiniContact contact) {
            if (S.isValidContextForGlide(iv_icon.getContext())) {
                Glide
                        .with(iv_icon)
                        .load(contact.photo)
                        .apply(new RequestOptions()
                                .error(R.drawable.face_on_button))
                        .into(iv_icon);
            }
            setText(contact.name);
            setIntent(contact.lookupKey);
        }
    }
}
