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
 * limitations under the License
 */

package com.android.systemui.statusbar.car;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.qs.car.CarQSFragment;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserSwitcherController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/**
 * Displays a ViewPager with icons for the users in the system to allow switching between users.
 * One of the uses of this is for the lock screen in auto.
 */
public class UserGridView extends ViewPager implements
        UserInfoController.OnUserInfoChangedListener {
    private StatusBar mStatusBar;
    private UserSwitcherController mUserSwitcherController;
    private Adapter mAdapter;
    private UserSelectionListener mUserSelectionListener;
    private UserInfoController mUserInfoController;
    private Vector mUserContainers;
    private int mContainerWidth;
    private boolean mOverrideAlpha;
    private CarQSFragment.UserSwitchCallback mUserSwitchCallback;

    public UserGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void init(StatusBar statusBar, UserSwitcherController userSwitcherController,
            boolean overrideAlpha) {
        mStatusBar = statusBar;
        mUserSwitcherController = userSwitcherController;
        mAdapter = new Adapter(mUserSwitcherController);
        mUserInfoController = Dependency.get(UserInfoController.class);
        mOverrideAlpha = overrideAlpha;
        // Whenever the container width changes, the containers must be refreshed. Instead of
        // doing an initial refreshContainers() to populate the containers, this listener will
        // refresh them on layout change because that affects how the users are split into
        // containers. Furthermore, at this point, the container width is unknown, so
        // refreshContainers() cannot populate any containers.
        addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    int newWidth = Math.max(left - right, right - left);
                    if (mContainerWidth != newWidth) {
                        mContainerWidth = newWidth;
                        refreshContainers();
                    }
                });
    }

    private void refreshContainers() {
        mUserContainers = new Vector();

        Context context = getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < mAdapter.getCount(); i++) {
            ViewGroup pods = (ViewGroup) inflater.inflate(
                    R.layout.car_fullscreen_user_pod_container, null);

            int iconsPerPage = mAdapter.getIconsPerPage();
            int limit = Math.min(mUserSwitcherController.getUsers().size(), (i + 1) * iconsPerPage);
            for (int j = i * iconsPerPage; j < limit; j++) {
                View v = mAdapter.makeUserPod(inflater, context, j, pods);
                if (mOverrideAlpha) {
                    v.setAlpha(1f);
                }
                pods.addView(v);
                // This is hacky, but the dividers on the pod container LinearLayout don't seem
                // to work for whatever reason.  Instead, set a right margin on the pod if it's not
                // the right-most pod and there is more than one pod in the container.
                if (i < limit - 1 && limit > 1) {
                    ViewGroup.MarginLayoutParams params =
                            (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                    params.setMargins(0, 0, getResources().getDimensionPixelSize(
                            R.dimen.car_fullscreen_user_pod_margin_between), 0);
                    v.setLayoutParams(params);
                }
            }
            mUserContainers.add(pods);
        }

        mAdapter = new Adapter(mUserSwitcherController);
        setAdapter(mAdapter);
    }

    @Override
    public void onUserInfoChanged(String name, Drawable picture, String userAccount) {
        refreshContainers();
    }

    public void setUserSwitchCallback(CarQSFragment.UserSwitchCallback callback) {
        mUserSwitchCallback = callback;
    }

    public void onUserSwitched(int newUserId) {
        // Bring up security view after user switch is completed.
        post(this::showOfflineAuthUi);
    }

    public void setUserSelectionListener(UserSelectionListener userSelectionListener) {
        mUserSelectionListener = userSelectionListener;
    }

    public void setListening(boolean listening) {
        if (listening) {
            mUserInfoController.addCallback(this);
        } else {
            mUserInfoController.removeCallback(this);
        }
    }

    void showOfflineAuthUi() {
        // TODO: Show keyguard UI in-place.
        mStatusBar.executeRunnableDismissingKeyguard(null, null, true, true, true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Wrap content doesn't work in ViewPagers, so simulate the behavior in code.
        int height = 0;
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.measure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                height = Math.max(child.getMeasuredHeight(), height);
            }

            // Respect the AT_MOST request from parent.
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
                height = Math.min(MeasureSpec.getSize(heightMeasureSpec), height);
            }
        }
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /**
     * This is a ViewPager.PagerAdapter which deletegates the work to a
     * UserSwitcherController.BaseUserAdapter. Java doesn't support multiple inheritance so we have
     * to use composition instead to achieve the same goal since both the base classes are abstract
     * classes and not interfaces.
     */
    private final class Adapter extends PagerAdapter {
        private final int mPodWidth;
        private final int mPodMarginBetween;
        private final int mPodImageAvatarWidth;
        private final int mPodImageAvatarHeight;

        private final WrappedBaseUserAdapter mUserAdapter;

        public Adapter(UserSwitcherController controller) {
            super();
            mUserAdapter = new WrappedBaseUserAdapter(controller, this);

            Resources res = getResources();
            mPodWidth = res.getDimensionPixelSize(R.dimen.car_fullscreen_user_pod_width);
            mPodMarginBetween = res.getDimensionPixelSize(
                    R.dimen.car_fullscreen_user_pod_margin_between);
            mPodImageAvatarWidth = res.getDimensionPixelSize(
                    R.dimen.car_fullscreen_user_pod_image_avatar_width);
            mPodImageAvatarHeight = res.getDimensionPixelSize(
                    R.dimen.car_fullscreen_user_pod_image_avatar_height);
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        private int getIconsPerPage() {
            // We need to know how many pods we need in this page. Each pod has its own width and
            // a margin between them. We can then divide the measured width of the parent by the
            // sum of pod width and margin to get the number of pods that will completely fit.
            // There is one less margin than the number of pods (eg. for 5 pods, there are 4
            // margins), so need to add the margin to the measured width to account for that.
            return (mContainerWidth + mPodMarginBetween) /
                    (mPodWidth + mPodMarginBetween);
        }

        @Override
        public void finishUpdate(ViewGroup container) {
            if (mUserSwitchCallback != null) {
                mUserSwitchCallback.resetShowing();
            }
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            if (position < mUserContainers.size()) {
                container.addView((View) mUserContainers.get(position));
                return mUserContainers.get(position);
            } else {
                return null;
            }
        }

        /**
         * Returns the default user icon.  This icon is a circle with a letter in it.  The letter is
         * the first character in the username.
         *
         * @param userName the username of the user for which the icon is to be created
         */
        private Bitmap getDefaultUserIcon(CharSequence userName) {
            CharSequence displayText = userName.subSequence(0, 1);
            Bitmap out = Bitmap.createBitmap(mPodImageAvatarWidth, mPodImageAvatarHeight,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);

            // Draw the circle background.
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RADIAL_GRADIENT);
            shape.setGradientRadius(1.0f);
            shape.setColor(getContext().getColor(R.color.car_user_switcher_no_user_image_bgcolor));
            shape.setBounds(0, 0, mPodImageAvatarWidth, mPodImageAvatarHeight);
            shape.draw(canvas);

            // Draw the letter in the center.
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(getContext().getColor(R.color.car_user_switcher_no_user_image_fgcolor));
            paint.setTextAlign(Align.CENTER);
            paint.setTextSize(getResources().getDimensionPixelSize(
                    R.dimen.car_fullscreen_user_pod_icon_text_size));
            Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
            // The Y coordinate is measured by taking half the height of the pod, but that would
            // draw the character putting the bottom of the font in the middle of the pod.  To
            // correct this, half the difference between the top and bottom distance metrics of the
            // font gives the offset of the font.  Bottom is a positive value, top is negative, so
            // the different is actually a sum.  The "half" operation is then factored out.
            canvas.drawText(displayText.toString(), mPodImageAvatarWidth / 2,
                    (mPodImageAvatarHeight - (metrics.bottom + metrics.top)) / 2, paint);

            return out;
        }

        private View makeUserPod(LayoutInflater inflater, Context context,
                int position, ViewGroup parent) {
            final UserSwitcherController.UserRecord record = mUserAdapter.getItem(position);
            View view = inflater.inflate(R.layout.car_fullscreen_user_pod, parent, false);

            TextView nameView = view.findViewById(R.id.user_name);
            if (record != null) {
                nameView.setText(mUserAdapter.getName(context, record));
                view.setActivated(record.isCurrent);
            } else {
                nameView.setText(context.getString(R.string.unknown_user_label));
            }

            ImageView iconView = (ImageView) view.findViewById(R.id.user_avatar);
            if (record == null || (record.picture == null && !record.isAddUser)) {
                iconView.setImageBitmap(getDefaultUserIcon(nameView.getText()));
            } else if (record.isAddUser) {
                Drawable icon = context.getDrawable(R.drawable.ic_add_circle_qs);
                icon.setTint(context.getColor(R.color.car_user_switcher_no_user_image_bgcolor));
                iconView.setImageDrawable(icon);
            } else {
                iconView.setImageBitmap(record.picture);
            }

            iconView.setOnClickListener(v -> {
                if (record == null) {
                    return;
                }

                if (mUserSelectionListener != null) {
                    mUserSelectionListener.onUserSelected(record);
                }

                if (record.isCurrent) {
                    showOfflineAuthUi();
                } else {
                    mUserSwitcherController.switchTo(record);
                }
            });

            return view;
        }

        @Override
        public int getCount() {
            int iconsPerPage = getIconsPerPage();
            if (iconsPerPage == 0) {
                return 0;
            }
            return (int) Math.ceil((double) mUserAdapter.getCount() / getIconsPerPage());
        }

        public void refresh() {
            mUserAdapter.refresh();
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }
    }

    private final class WrappedBaseUserAdapter extends UserSwitcherController.BaseUserAdapter {
        private final Adapter mContainer;

        public WrappedBaseUserAdapter(UserSwitcherController controller, Adapter container) {
            super(controller);
            mContainer = container;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            throw new UnsupportedOperationException("unused");
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            mContainer.notifyDataSetChanged();
        }
    }

    interface UserSelectionListener {
        void onUserSelected(UserSwitcherController.UserRecord record);
    };
}
