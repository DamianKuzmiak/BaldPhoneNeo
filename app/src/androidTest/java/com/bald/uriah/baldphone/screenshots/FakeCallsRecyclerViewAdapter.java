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

package com.bald.uriah.baldphone.screenshots;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.activities.BaldActivity;
import com.bald.uriah.baldphone.utils.S;
import com.bald.uriah.baldphone.views.ModularRecyclerView;

import org.joda.time.DateTime;

import java.util.List;

import app.baldphone.neo.calls.CallLogItemType;

public class FakeCallsRecyclerViewAdapter extends ModularRecyclerView.ModularAdapter<FakeCallsRecyclerViewAdapter.ViewHolder> {
    private static final String TAG = FakeCallsRecyclerViewAdapter.class.getSimpleName();

    public static final int INCOMING_TYPE = 1;
    public static final int OUTGOING_TYPE = 2;
    public static final int MISSED_TYPE = 3;
    public static final int VOICEMAIL_TYPE = 4;

    public static final int REJECTED_TYPE = 5;
    public static final int BLOCKED_TYPE = 6;
    public static final int ANSWERED_EXTERNALLY_TYPE = 7;

    @ColorInt
    private final List<FakeCall> callList;
    private final BaldActivity activity;
    private final LayoutInflater inflater;

    public FakeCallsRecyclerViewAdapter(List<FakeCall> callList, BaldActivity activity) {
        this.callList = callList;
        this.activity = activity;
        this.inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final TypedValue typedValue = new TypedValue();
        final Resources.Theme theme = activity.getTheme();
        theme.resolveAttribute(R.attr.bald_decoration_on_button, typedValue, true);
        theme.resolveAttribute(R.attr.bald_background, typedValue, true);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.contact_call_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        holder.update(position);
    }

    @Override
    public int getItemCount() {
        return callList.size();
    }

    static class FakeCall {
        public final int callType;
        public final String photo, name, phoneNumber;
        public final long dateTime;

        public FakeCall(int callType, String photo, String name, String phoneNumber, long dateTime) {
            this.callType = callType;
            this.photo = photo;
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.dateTime = dateTime;
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView iv_type;
        final TextView tv_type, tv_time, day;
        private boolean expanded;

        public ViewHolder(View itemView) {
            super(itemView);

            final LinearLayout container = (LinearLayout) itemView;
            tv_type = container.findViewById(R.id.tv_call_type);
            iv_type = container.findViewById(R.id.iv_call_type);
            tv_time = container.findViewById(R.id.tv_time);
            day = container.findViewById(R.id.day);
        }

        public void update(int index) {
            final FakeCall fakeCall = callList.get(index);
            setType(fakeCall.callType);
            setDay(
                    (index == 0 ||
                            new DateTime(callList.get(index - 1).dateTime).getDayOfYear() !=
                                    new DateTime(fakeCall.dateTime).getDayOfYear()) ?
                            S.stringTimeFromLong(activity, fakeCall.dateTime, false) :
                            null
            );

            final DateTime dateTime = new DateTime(fakeCall.dateTime);
            tv_time.setText(S.numberToAlarmString(dateTime.getHourOfDay(), dateTime.getMinuteOfHour()));
        }

        public void setDay(final @Nullable String day) {
            if (day == null && expanded) {
                this.day.setVisibility(View.GONE);
                expanded = false;
            } else if (day != null && expanded) {
                this.day.setText(day);
            } else if (day != null) {
                this.day.setVisibility(View.VISIBLE);
                this.day.setText(day);
                expanded = true;
            }
        }

        public void setType(int type) {
            CallLogItemType displayType = CallLogItemType.fromSystemType(type);
            tv_type.setText(displayType.stringRes);
            iv_type.setImageResource(displayType.drawableRes);
            tv_type.setTextColor(ContextCompat.getColor(activity, displayType.colorRes));
            iv_type.setBackgroundResource(displayType.colorRes);
        }
    }
}