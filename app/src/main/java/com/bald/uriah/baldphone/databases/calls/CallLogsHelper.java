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

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract;

import app.baldphone.neo.contacts.Contact;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Simple Helper to get the call log. */
public class CallLogsHelper {

    public static List<Call> getForSpecificContact(
            ContentResolver contentResolver, Contact contact) {
        final Uri contactUri =
                ContactsContract.Contacts.getLookupUri(contact.getId(), contact.getLookupKey());
        try (Cursor cursor =
                contentResolver.query(
                        Calls.CONTENT_URI,
                        new String[] {
                            Calls.NUMBER, Calls.DURATION, Calls.DATE, Calls.TYPE, Calls.CACHED_LOOKUP_URI,
                            //            CallLog.Calls.NEW, Currently it's commented because new calls are not
                            // marked in any way
                            //            CallLog.Calls.IS_READ,
                        },
                        Calls.CACHED_LOOKUP_URI + "=?",
                        new String[] {contactUri.toString()},
                        Calls.DATE + " DESC")) {
            final List<Call> calls = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                calls.add(new Call(cursor));
            }
            return calls;
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isAllReadSafe(ContentResolver contentResolver) {
        try (final Cursor cursor =
                contentResolver.query(
                        Calls.CONTENT_URI,
                        new String[] {Calls.TYPE, Calls.IS_READ, Calls.NEW},
                        String.format(
                                Locale.US,
                                "%s=0 AND %s=1 AND %s=%d",
                                Calls.IS_READ,
                                Calls.NEW,
                                Calls.TYPE,
                                Calls.MISSED_TYPE),
                        null,
                        null)) {
            return cursor.getCount() == 0;
        } catch (SecurityException ignore) {
            return true;
        }
    }
}
