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
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import app.baldphone.neo.R;

public class FirstPageAppIcon extends BaldFrameLayoutButton {
    private final Context context;
    private final LayoutInflater layoutInflater;
    public ImageView imageView;
    public TextView textView;
    public View badge;

    public FirstPageAppIcon(Context context) {
        this(context, null);
    }

    public FirstPageAppIcon(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FirstPageAppIcon(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        this.layoutInflater = LayoutInflater.from(context);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attributeSet) {
        layoutInflater.inflate(R.layout.item_app_icon, this, true);
        imageView = findViewById(R.id.iv_app_icon);
        textView = findViewById(R.id.tv_app_label);
        badge = findViewById(R.id.tv_notification_badge);

        if (attributeSet != null) {
            try (final TypedArray styleAttributesArray =
                    context.obtainStyledAttributes(attributeSet, R.styleable.FirstPageAppIcon)) {
                setImageDrawable(
                        styleAttributesArray.getDrawable(R.styleable.FirstPageAppIcon___src));
                setText(styleAttributesArray.getString(R.styleable.FirstPageAppIcon___text));
            }
        }
    }

    public void setImageDrawable(@Nullable Drawable drawable) {
        imageView.setImageDrawable(drawable);
    }

    public void setImageResource(@DrawableRes int resId) {
        imageView.setImageResource(resId);
    }

    public void setBadgeVisibility(boolean visible) {
        badge.setVisibility(visible ? VISIBLE : GONE);
    }

    public CharSequence getText() {
        return textView.getText();
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public void setText(@StringRes int resId) {
        textView.setText(resId);
    }
}
