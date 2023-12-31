/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.systemui.statusbar;

import static android.app.NotificationManager.IMPORTANCE_NONE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.INotificationManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settingslib.Utils;
import com.android.systemui.Interpolators;
import com.android.systemui.R;

import java.util.List;
import java.util.Set;

/**
 * The guts of a notification revealed when performing a long press.
 */
public class NotificationInfo extends LinearLayout implements NotificationGuts.GutsContent {
    private static final String TAG = "InfoGuts";

    private INotificationManager mINotificationManager;
    private PackageManager mPm;

    private String mPkg;
    private String mAppName;
    private int mAppUid;
    private int mNumNotificationChannels;
    private NotificationChannel mSingleNotificationChannel;
    private int mStartingUserImportance;
    private int mChosenImportance;
    private boolean mIsSingleDefaultChannel;
    private boolean mNonblockable;
    private StatusBarNotification mSbn;
    private AnimatorSet mExpandAnimation;

    private CheckSaveListener mCheckSaveListener;
    private OnSettingsClickListener mOnSettingsClickListener;
    private OnAppSettingsClickListener mAppSettingsClickListener;
    private NotificationGuts mGutsContainer;
    private boolean mNegativeUserSentiment;

    private OnClickListener mOnKeepShowing = v -> {
        closeControls(v);
    };

    private OnClickListener mOnStopNotifications = v -> {
        swapContent(false);
    };

    private OnClickListener mOnUndo = v -> {
        swapContent(true);
    };

    public NotificationInfo(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // Specify a CheckSaveListener to override when/if the user's changes are committed.
    public interface CheckSaveListener {
        // Invoked when importance has changed and the NotificationInfo wants to try to save it.
        // Listener should run saveImportance unless the change should be canceled.
        void checkSave(Runnable saveImportance, StatusBarNotification sbn);
    }

    public interface OnSettingsClickListener {
        void onClick(View v, NotificationChannel channel, int appUid);
    }

    public interface OnAppSettingsClickListener {
        void onClick(View v, Intent intent);
    }

    public void bindNotification(final PackageManager pm,
            final INotificationManager iNotificationManager,
            final String pkg,
            final NotificationChannel notificationChannel,
            final int numChannels,
            final StatusBarNotification sbn,
            final CheckSaveListener checkSaveListener,
            final OnSettingsClickListener onSettingsClick,
            final OnAppSettingsClickListener onAppSettingsClick,
            final Set<String> nonBlockablePkgs)
            throws RemoteException {
        bindNotification(pm, iNotificationManager, pkg, notificationChannel, numChannels, sbn,
                checkSaveListener, onSettingsClick, onAppSettingsClick, nonBlockablePkgs,
                false /* negative sentiment */);
    }

    public void bindNotification(final PackageManager pm,
            final INotificationManager iNotificationManager,
            final String pkg,
            final NotificationChannel notificationChannel,
            final int numChannels,
            final StatusBarNotification sbn,
            final CheckSaveListener checkSaveListener,
            final OnSettingsClickListener onSettingsClick,
            final OnAppSettingsClickListener onAppSettingsClick,
            final Set<String> nonBlockablePkgs,
            boolean negativeUserSentiment)  throws RemoteException {
        mINotificationManager = iNotificationManager;
        mPkg = pkg;
        mNumNotificationChannels = numChannels;
        mSbn = sbn;
        mPm = pm;
        mAppSettingsClickListener = onAppSettingsClick;
        mAppName = mPkg;
        mCheckSaveListener = checkSaveListener;
        mOnSettingsClickListener = onSettingsClick;
        mSingleNotificationChannel = notificationChannel;
        mStartingUserImportance = mChosenImportance = mSingleNotificationChannel.getImportance();
        mNegativeUserSentiment = negativeUserSentiment;

        int numTotalChannels = mINotificationManager.getNumNotificationChannelsForPackage(
                pkg, mAppUid, false /* includeDeleted */);
        if (mNumNotificationChannels == 0) {
            throw new IllegalArgumentException("bindNotification requires at least one channel");
        } else  {
            // Special behavior for the Default channel if no other channels have been defined.
            mIsSingleDefaultChannel = mNumNotificationChannels == 1
                    && mSingleNotificationChannel.getId()
                    .equals(NotificationChannel.DEFAULT_CHANNEL_ID)
                    && numTotalChannels <= 1;
        }

        try {
            final PackageInfo pkgInfo = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            if (Utils.isSystemPackage(getResources(), pm, pkgInfo)) {
                if (mSingleNotificationChannel != null
                        && !mSingleNotificationChannel.isBlockableSystem()) {
                    mNonblockable = true;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            // unlikely.
        }
        if (nonBlockablePkgs != null) {
            mNonblockable |= nonBlockablePkgs.contains(pkg);
        }
        mNonblockable |= (mNumNotificationChannels > 1);

        bindHeader();
        bindPrompt();
        bindButtons();
    }

    private void bindHeader() throws RemoteException {
        // Package name
        Drawable pkgicon = null;
        ApplicationInfo info;
        try {
            info = mPm.getApplicationInfo(mPkg,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
                            | PackageManager.MATCH_DISABLED_COMPONENTS
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE
                            | PackageManager.MATCH_DIRECT_BOOT_AWARE);
            if (info != null) {
                mAppUid = mSbn.getUid();
                mAppName = String.valueOf(mPm.getApplicationLabel(info));
                pkgicon = mPm.getApplicationIcon(info);
            }
        } catch (PackageManager.NameNotFoundException e) {
            // app is gone, just show package name and generic icon
            pkgicon = mPm.getDefaultActivityIcon();
        }
        ((ImageView) findViewById(R.id.pkgicon)).setImageDrawable(pkgicon);
        ((TextView) findViewById(R.id.pkgname)).setText(mAppName);

        // Set group information if this channel has an associated group.
        CharSequence groupName = null;
        if (mSingleNotificationChannel != null && mSingleNotificationChannel.getGroup() != null) {
            final NotificationChannelGroup notificationChannelGroup =
                    mINotificationManager.getNotificationChannelGroupForPackage(
                            mSingleNotificationChannel.getGroup(), mPkg, mAppUid);
            if (notificationChannelGroup != null) {
                groupName = notificationChannelGroup.getName();
            }
        }
        TextView groupNameView = findViewById(R.id.group_name);
        TextView groupDividerView = findViewById(R.id.pkg_group_divider);
        if (groupName != null) {
            groupNameView.setText(groupName);
            groupNameView.setVisibility(View.VISIBLE);
            groupDividerView.setVisibility(View.VISIBLE);
        } else {
            groupNameView.setVisibility(View.GONE);
            groupDividerView.setVisibility(View.GONE);
        }

        // Settings button.
        final View settingsButton = findViewById(R.id.info);
        if (mAppUid >= 0 && mOnSettingsClickListener != null) {
            settingsButton.setVisibility(View.VISIBLE);
            final int appUidF = mAppUid;
            settingsButton.setOnClickListener(
                    (View view) -> {
                        mOnSettingsClickListener.onClick(view,
                                mNumNotificationChannels > 1 ? null : mSingleNotificationChannel,
                                appUidF);
                    });
        } else {
            settingsButton.setVisibility(View.GONE);
        }
    }

    private void bindPrompt() {
        final TextView blockPrompt = findViewById(R.id.block_prompt);
        bindName();
        if (mNonblockable) {
            blockPrompt.setText(R.string.notification_unblockable_desc);
        } else {
            if (mNegativeUserSentiment) {
                blockPrompt.setText(R.string.inline_blocking_helper);
            }  else if (mIsSingleDefaultChannel || mNumNotificationChannels > 1) {
                blockPrompt.setText(R.string.inline_keep_showing_app);
            } else {
                blockPrompt.setText(R.string.inline_keep_showing);
            }
        }
    }

    private void bindName() {
        final TextView channelName = findViewById(R.id.channel_name);
        if (mIsSingleDefaultChannel || mNumNotificationChannels > 1) {
            channelName.setVisibility(View.GONE);
        } else {
            channelName.setText(mSingleNotificationChannel.getName());
        }
    }

    private boolean hasImportanceChanged() {
        return mSingleNotificationChannel != null && mStartingUserImportance != mChosenImportance;
    }

    private void saveImportance() {
        if (mNonblockable) {
            return;
        }
        MetricsLogger.action(mContext, MetricsEvent.ACTION_SAVE_IMPORTANCE,
                mChosenImportance - mStartingUserImportance);
        mSingleNotificationChannel.setImportance(mChosenImportance);
        mSingleNotificationChannel.lockFields(NotificationChannel.USER_LOCKED_IMPORTANCE);
        try {
            mINotificationManager.updateNotificationChannelForPackage(
                    mPkg, mAppUid, mSingleNotificationChannel);
        } catch (RemoteException e) {
            // :(
        }
    }

    private void bindButtons() {
        View block =  findViewById(R.id.block);
        block.setOnClickListener(mOnStopNotifications);
        TextView keep = findViewById(R.id.keep);
        keep.setOnClickListener(mOnKeepShowing);
        findViewById(R.id.undo).setOnClickListener(mOnUndo);

        if (mNonblockable) {
            keep.setText(R.string.notification_done);
            block.setVisibility(GONE);
        }

        // app settings link
        TextView settingsLinkView = findViewById(R.id.app_settings);
        Intent settingsIntent = getAppSettingsIntent(mPm, mPkg, mSingleNotificationChannel,
                mSbn.getId(), mSbn.getTag());
        if (settingsIntent != null
                && !TextUtils.isEmpty(mSbn.getNotification().getSettingsText())) {
            settingsLinkView.setVisibility(View.VISIBLE);
            settingsLinkView.setText(mContext.getString(R.string.notification_app_settings,
                    mSbn.getNotification().getSettingsText()));
            settingsLinkView.setOnClickListener((View view) -> {
                mAppSettingsClickListener.onClick(view, settingsIntent);
            });
        } else {
            settingsLinkView.setVisibility(View.GONE);
        }
    }

    private void swapContent(boolean showPrompt) {
        if (mExpandAnimation != null) {
            mExpandAnimation.cancel();
        }

        if (showPrompt) {
            mChosenImportance = mStartingUserImportance;
        } else {
            mChosenImportance = IMPORTANCE_NONE;
        }

        View prompt = findViewById(R.id.prompt);
        View confirmation = findViewById(R.id.confirmation);
        ObjectAnimator promptAnim = ObjectAnimator.ofFloat(prompt, View.ALPHA,
                prompt.getAlpha(), showPrompt ? 1f : 0f);
        promptAnim.setInterpolator(showPrompt ? Interpolators.ALPHA_IN : Interpolators.ALPHA_OUT);
        ObjectAnimator confirmAnim = ObjectAnimator.ofFloat(confirmation, View.ALPHA,
                confirmation.getAlpha(), showPrompt ? 0f : 1f);
        confirmAnim.setInterpolator(showPrompt ? Interpolators.ALPHA_OUT : Interpolators.ALPHA_IN);

        prompt.setVisibility(showPrompt ? VISIBLE : GONE);
        confirmation.setVisibility(showPrompt ? GONE : VISIBLE);

        mExpandAnimation = new AnimatorSet();
        mExpandAnimation.playTogether(promptAnim, confirmAnim);
        mExpandAnimation.setDuration(150);
        mExpandAnimation.addListener(new AnimatorListenerAdapter() {
            boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    prompt.setVisibility(showPrompt ? VISIBLE : GONE);
                    confirmation.setVisibility(showPrompt ? GONE : VISIBLE);
                }
            }
        });
        mExpandAnimation.start();
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        if (mGutsContainer != null &&
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (mGutsContainer.isExposed()) {
                event.getText().add(mContext.getString(
                        R.string.notification_channel_controls_opened_accessibility, mAppName));
            } else {
                event.getText().add(mContext.getString(
                        R.string.notification_channel_controls_closed_accessibility, mAppName));
            }
        }
    }

    private Intent getAppSettingsIntent(PackageManager pm, String packageName,
            NotificationChannel channel, int id, String tag) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES)
                .setPackage(packageName);
        final List<ResolveInfo> resolveInfos = pm.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
        );
        if (resolveInfos == null || resolveInfos.size() == 0 || resolveInfos.get(0) == null) {
            return null;
        }
        final ActivityInfo activityInfo = resolveInfos.get(0).activityInfo;
        intent.setClassName(activityInfo.packageName, activityInfo.name);
        if (channel != null) {
            intent.putExtra(Notification.EXTRA_CHANNEL_ID, channel.getId());
        }
        intent.putExtra(Notification.EXTRA_NOTIFICATION_ID, id);
        intent.putExtra(Notification.EXTRA_NOTIFICATION_TAG, tag);
        return intent;
    }

    private void closeControls(View v) {
        int[] parentLoc = new int[2];
        int[] targetLoc = new int[2];
        mGutsContainer.getLocationOnScreen(parentLoc);
        v.getLocationOnScreen(targetLoc);
        final int centerX = v.getWidth() / 2;
        final int centerY = v.getHeight() / 2;
        final int x = targetLoc[0] - parentLoc[0] + centerX;
        final int y = targetLoc[1] - parentLoc[1] + centerY;
        mGutsContainer.closeControls(x, y, true /* save */, false /* force */);
    }

    @Override
    public void setGutsParent(NotificationGuts guts) {
        mGutsContainer = guts;
    }

    @Override
    public boolean willBeRemoved() {
        return hasImportanceChanged();
    }

    @Override
    public View getContentView() {
        return this;
    }

    @Override
    public boolean handleCloseControls(boolean save, boolean force) {
        // Save regardless of the importance so we can lock the importance field if the user wants
        // to keep getting notifications
        if (save) {
            if (mCheckSaveListener != null) {
                mCheckSaveListener.checkSave(this::saveImportance, mSbn);
            } else {
                saveImportance();
            }
        }
        return false;
    }

    @Override
    public int getActualHeight() {
        return getHeight();
    }
}
