package com.bald.uriah.baldphone.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import app.baldphone.neo.core.assisttouch.ViewExtensionsKt;

public class BaldImageButton extends AppCompatImageView {

    public BaldImageButton(Context context) {
        super(context);
        init(context);
    }

    public BaldImageButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public BaldImageButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setClickable(true);
        setFocusable(true);
        ViewExtensionsKt.enableAssistTouch(this);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return android.widget.ImageButton.class.getName();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.ImageButton.class.getName());
    }
}
