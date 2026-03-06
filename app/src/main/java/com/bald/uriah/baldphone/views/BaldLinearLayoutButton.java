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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import app.baldphone.neo.core.assisttouch.ViewExtensionsKt;

/**
 * Simple Button, extends {@link LinearLayout}; adapted to App settings.
 * EXACTLY the same code as {@link BaldButton}, but extends {@link LinearLayout} instead.
 */
public class BaldLinearLayoutButton extends LinearLayout {

    public BaldLinearLayoutButton(Context context) {
        super(context);
        init(context);
    }

    public BaldLinearLayoutButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BaldLinearLayoutButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public BaldLinearLayoutButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        setClickable(true);
        setFocusable(true);
        ViewExtensionsKt.enableAssistTouch(this);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return android.widget.Button.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.Button.class.getName());
    }
}
