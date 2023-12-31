/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static junit.framework.Assert.assertNotNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.support.test.filters.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.plugins.statusbar.NotificationMenuRowPlugin;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.NotificationGuts;
import com.android.systemui.statusbar.NotificationTestHelper;
import com.android.systemui.statusbar.notification.NotificationInflater;
import com.android.systemui.statusbar.stack.NotificationStackScrollLayout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoRule;
import org.mockito.junit.MockitoJUnit;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class NotificationGutsManagerTest extends SysuiTestCase {
    private static final String TEST_CHANNEL_ID = "NotificationManagerServiceTestChannelId";

    private final String mPackageName = mContext.getPackageName();
    private final int mUid = Binder.getCallingUid();

    private NotificationChannel mTestNotificationChannel = new NotificationChannel(
            TEST_CHANNEL_ID, TEST_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT);
    private TestableLooper mTestableLooper;
    private Handler mHandler;
    private NotificationTestHelper mHelper;
    private NotificationGutsManager mGutsManager;

    @Rule public MockitoRule mockito = MockitoJUnit.rule();
    @Mock private NotificationLockscreenUserManager mLockscreenUserManager;
    @Mock private NotificationPresenter mPresenter;
    @Mock private NotificationEntryManager mEntryManager;
    @Mock private NotificationStackScrollLayout mStackScroller;
    @Mock private NotificationInfo.CheckSaveListener mCheckSaveListener;
    @Mock private NotificationGutsManager.OnSettingsClickListener mOnSettingsClickListener;

    @Before
    public void setUp() {
        mTestableLooper = TestableLooper.get(this);
        mHandler = new Handler(mTestableLooper.getLooper());

        mHelper = new NotificationTestHelper(mContext);

        mGutsManager = new NotificationGutsManager(mContext);
        mGutsManager.setUpWithPresenter(mPresenter, mEntryManager, mStackScroller,
                mCheckSaveListener, mOnSettingsClickListener);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Test methods:

    @Test
    public void testOpenAndCloseGuts() {
        NotificationGuts guts = spy(new NotificationGuts(mContext));
        when(guts.post(any())).thenAnswer(invocation -> {
            mHandler.post(((Runnable) invocation.getArguments()[0]));
            return null;
        });

        // Test doesn't support animation since the guts view is not attached.
        doNothing().when(guts).openControls(anyInt(), anyInt(), anyBoolean(), any(Runnable.class));

        ExpandableNotificationRow realRow = createTestNotificationRow();
        NotificationMenuRowPlugin.MenuItem menuItem = createTestMenuItem(realRow);

        ExpandableNotificationRow row = spy(realRow);
        when(row.getWindowToken()).thenReturn(new Binder());
        when(row.getGuts()).thenReturn(guts);

        mGutsManager.openGuts(row, 0, 0, menuItem);
        assertEquals(View.INVISIBLE, guts.getVisibility());
        mTestableLooper.processAllMessages();
        verify(guts).openControls(anyInt(), anyInt(), anyBoolean(), any(Runnable.class));

        assertEquals(View.VISIBLE, guts.getVisibility());
        mGutsManager.closeAndSaveGuts(false, false, false, 0, 0, false);

        verify(guts).closeControls(anyBoolean(), anyBoolean(), anyInt(), anyInt(), anyBoolean());
        verify(row, times(1)).setGutsView(any());
    }

    @Test
    public void testChangeDensityOrFontScale() {
        NotificationGuts guts = spy(new NotificationGuts(mContext));
        when(guts.post(any())).thenAnswer(invocation -> {
            mHandler.post(((Runnable) invocation.getArguments()[0]));
            return null;
        });

        // Test doesn't support animation since the guts view is not attached.
        doNothing().when(guts).openControls(anyInt(), anyInt(), anyBoolean(), any(Runnable.class));

        ExpandableNotificationRow realRow = createTestNotificationRow();
        NotificationMenuRowPlugin.MenuItem menuItem = createTestMenuItem(realRow);

        ExpandableNotificationRow row = spy(realRow);
        when(row.getWindowToken()).thenReturn(new Binder());
        when(row.getGuts()).thenReturn(guts);
        doNothing().when(row).inflateGuts();

        mGutsManager.openGuts(row, 0, 0, menuItem);
        mTestableLooper.processAllMessages();
        verify(guts).openControls(anyInt(), anyInt(), anyBoolean(), any(Runnable.class));

        row.onDensityOrFontScaleChanged();
        mGutsManager.onDensityOrFontScaleChanged(row);
        mTestableLooper.processAllMessages();

        mGutsManager.closeAndSaveGuts(false, false, false, 0, 0, false);

        verify(guts).closeControls(anyBoolean(), anyBoolean(), anyInt(), anyInt(), anyBoolean());
        verify(row, times(2)).setGutsView(any());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Utility methods:

    private ExpandableNotificationRow createTestNotificationRow() {
        Notification.Builder nb = new Notification.Builder(mContext,
                mTestNotificationChannel.getId())
                                        .setContentTitle("foo")
                                        .setColorized(true)
                                        .setFlag(Notification.FLAG_CAN_COLORIZE, true)
                                        .setSmallIcon(android.R.drawable.sym_def_app_icon);

        try {
            ExpandableNotificationRow row = mHelper.createRow(nb.build());
            row.getEntry().channel = mTestNotificationChannel;
            return row;
        } catch (Exception e) {
            fail();
            return null;
        }
    }

    private NotificationMenuRowPlugin.MenuItem createTestMenuItem(ExpandableNotificationRow row) {
        NotificationMenuRowPlugin menuRow = new NotificationMenuRow(mContext);
        menuRow.createMenu(row, row.getStatusBarNotification());

        NotificationMenuRowPlugin.MenuItem menuItem = menuRow.getLongpressMenuItem(mContext);
        assertNotNull(menuItem);
        return menuItem;
    }
}
