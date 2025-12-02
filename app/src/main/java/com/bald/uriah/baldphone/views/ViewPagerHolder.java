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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.bald.uriah.baldphone.R;
import com.bald.uriah.baldphone.utils.BPrefs;
import com.bald.uriah.baldphone.utils.D;
import com.google.android.material.tabs.TabLayout;

import java.util.Locale;

public class ViewPagerHolder extends LinearLayout {
    private static final int HEIGHT = 60; // in dp
    private Context context;

    private CharSequence itemType;
    private String of;

    // views
    private View right;
    private View left;
    private ViewPager viewPager;
    private TextView pageIndicatorTextView;
    private View circles;

    private boolean noArrows = false;
    private boolean useCircle = false;
    private boolean showHints = false;

    private int pageIndex;

    public ViewPagerHolder(Context context) {
        this(context, null);
    }

    public ViewPagerHolder(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewPagerHolder(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attributeSet) {
        this.context = context;
        final SharedPreferences sharedPreferences =
                context.getSharedPreferences(D.BALD_PREFS, Context.MODE_PRIVATE);
        noArrows = sharedPreferences.getBoolean(BPrefs.TOUCH_NOT_HARD_KEY, BPrefs.TOUCH_NOT_HARD_DEFAULT_VALUE);

        try (final TypedArray typedArray =
                context.obtainStyledAttributes(attributeSet, R.styleable.ViewPagerHolder)) {
            itemType = typedArray.getString(R.styleable.ViewPagerHolder_item_type);
            if (itemType == null) {
                itemType = context.getString(R.string.page);
            }
            useCircle = typedArray.getBoolean(R.styleable.ViewPagerHolder_use_circles, false);
            showHints = typedArray.getBoolean(R.styleable.ViewPagerHolder_show_hints, false);
        }

        of = String.valueOf(context.getText(R.string.of));
        this.setOrientation(VERTICAL);

        viewPager = noArrows ? new ViewPager(context) : new NonSwipeableViewPager(context);
        viewPager.addOnPageChangeListener(
                new ViewPager.OnPageChangeListener() {
                    @Override
                    public void onPageScrolled(
                            int position, float positionOffset, int positionOffsetPixels) {}

                    @Override
                    public void onPageSelected(int position) {
                        pageChangeHandler(position);
                    }

                    @Override
                    public void onPageScrollStateChanged(int state) {}
                });
        viewPager.setId(R.id.id_dummy);
        addView(viewPager, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setup();
    }

    public void setItemType(CharSequence itemType) {
        this.itemType = itemType;
    }

    private void setup() {
        final LayoutInflater layoutInflater = LayoutInflater.from(context);

        if (showHints) {
            final TextView textView =
                    (TextView) layoutInflater.inflate(R.layout.view_pager_holder_hint, this, false);
            textView.setText(
                    noArrows ? R.string.swipe_left_or_right : R.string.press_next_or_back_buton);
            addView(textView);
        }

        if (!noArrows) {
            final View arrowHolder =
                    layoutInflater.inflate(R.layout.view_pager_holder_arrows, this, false);
            right = arrowHolder.findViewById(R.id.right_arrow);
            left = arrowHolder.findViewById(R.id.left_arrow);

            right.setOnClickListener(
                    v -> {
                        final int currentItem = viewPager.getCurrentItem();
                        final PagerAdapter pagerAdapter = viewPager.getAdapter();
                        if (!(pagerAdapter == null || currentItem + 1 >= pagerAdapter.getCount()))
                            viewPager.setCurrentItem(currentItem + 1);
                    });
            left.setOnClickListener(
                    v -> {
                        final int currentItem = viewPager.getCurrentItem();
                        final PagerAdapter pagerAdapter = viewPager.getAdapter();
                        if (!(pagerAdapter == null || currentItem - 1 < 0))
                            viewPager.setCurrentItem(currentItem - 1);
                    });
            addView(
                    arrowHolder,
                    new LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (int)
                                    TypedValue.applyDimension(
                                            TypedValue.COMPLEX_UNIT_DIP,
                                            HEIGHT,
                                            context.getResources().getDisplayMetrics())));
        }

        if (useCircle) {
            circles = layoutInflater.inflate(R.layout.circles, this, false);
            ((TabLayout) circles.findViewById(R.id.tab)).setupWithViewPager(viewPager);
        } else {
            pageIndicatorTextView = (TextView) layoutInflater.inflate(R.layout.page_of_, this, false);
        }
        addView(useCircle ? circles : pageIndicatorTextView);
    }

    private void pageChangeHandler(int position) {
        final PagerAdapter pagerAdapter = viewPager.getAdapter();
        if (pagerAdapter == null) return;

        if (!noArrows) {
            right.setVisibility(position + 1 < pagerAdapter.getCount() ? VISIBLE : INVISIBLE);
            left.setVisibility((position > 0) ? VISIBLE : INVISIBLE);
        }
        pageIndex = position;
        updatePageIndicatorText();
    }

    private void updatePageIndicatorText() {
        if (!useCircle && pageIndicatorTextView != null && viewPager.getAdapter() != null) {
            pageIndicatorTextView.setText(
                    String.format(
                            Locale.US,
                            "%s %d %s %d",
                            itemType,
                            pageIndex + 1,
                            of,
                            viewPager.getAdapter().getCount()));
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setViewPagerAdapter(PagerAdapter pagerAdapter) {
        viewPager.setAdapter(pagerAdapter);
        updatePageIndicatorText();
        pageChangeHandler(viewPager.getCurrentItem());
    }

    public void setCurrentItem(int item) {
        viewPager.setCurrentItem(item);
    }

    public void onDataChanged() {
        pageChangeHandler(pageIndex);
    }

    public int getPageIndex() {
        return pageIndex;
    }

    /**
     * be careful
     *
     * @return viewPager
     */
    public ViewPager getViewPager() {
        return viewPager;
    }
}
