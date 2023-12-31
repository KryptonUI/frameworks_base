/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.car;

import android.app.UiModeManager;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import com.android.keyguard.AlphaOptimizedImageButton;
import com.android.systemui.R;

/**
 * A custom navigation bar for the automotive use case.
 * <p>
 * The navigation bar in the automotive use case is more like a list of shortcuts, rendered
 * in a linear layout.
 */
class CarNavigationBarView extends LinearLayout {
    private LinearLayout mNavButtons;
    private AlphaOptimizedImageButton mNotificationsButton;
    private CarStatusBar mCarStatusBar;

    public CarNavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFinishInflate() {
        mNavButtons = findViewById(R.id.nav_buttons);

        mNotificationsButton = findViewById(R.id.notifications);
        mNotificationsButton.setOnClickListener(this::onNotificationsClick);
    }

    void setStatusBar(CarStatusBar carStatusBar) {
        mCarStatusBar = carStatusBar;
    }

    protected void onNotificationsClick(View v) {
        mCarStatusBar.togglePanel();
    }
}
