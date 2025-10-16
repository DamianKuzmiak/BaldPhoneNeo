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

package com.bald.uriah.baldphone.databases.calls;

import android.database.Cursor;
import android.provider.CallLog;

import androidx.annotation.NonNull;

public class Call {
    public final int callType;
    public final long dateTime;
    public final String phoneNumber;
    public final int duration;
//    public final boolean neW; Currently it's commented because new calls are not marked in any way

    public Call(String phoneNumber, int duration, long dateTime, int callType) {
        this.callType = callType;
        this.dateTime = dateTime;
        this.phoneNumber = phoneNumber;
        this.duration = duration;
//        this.neW = neW;
    }

    public Call(@NonNull final Cursor cursor) {
        this(
                cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER)),
                cursor.getInt(cursor.getColumnIndex(CallLog.Calls.DURATION)),
                cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE)),
                cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE))
//                ,(cursor.getInt(cursor.getColumnIndex(CallLog.Calls.NEW)) == 1) && (cursor.getInt(cursor.getColumnIndex(CallLog.Calls.IS_READ)) == 0)
        );
    }
}