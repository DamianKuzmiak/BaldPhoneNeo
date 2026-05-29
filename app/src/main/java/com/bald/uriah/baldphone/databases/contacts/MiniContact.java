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

package com.bald.uriah.baldphone.databases.contacts;

import androidx.annotation.Nullable;

import com.bald.uriah.baldphone.utils.Constants;

/**
 * Mini contact, contains lookupkey,photo,name and id.
 */
public class MiniContact implements Constants.BaseContactsConstants {

    public final String lookupKey, photo;
    @Nullable
    public final String name;
    public final int id;
    public final boolean favorite;

    public MiniContact(String lookupKey, @Nullable String name, String photo, int id, boolean favorite) {
        this.lookupKey = lookupKey;
        this.name = name;
        this.photo = photo;
        this.id = id;
        this.favorite = favorite;
    }
}