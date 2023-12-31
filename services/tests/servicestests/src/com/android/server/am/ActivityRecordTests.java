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

package com.android.server.am;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.pm.ActivityInfo.RESIZE_MODE_RESIZEABLE;
import static android.content.pm.ActivityInfo.RESIZE_MODE_UNRESIZEABLE;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.server.am.ActivityStack.ActivityState.DESTROYED;
import static com.android.server.am.ActivityStack.ActivityState.DESTROYING;
import static com.android.server.am.ActivityStack.ActivityState.INITIALIZING;
import static com.android.server.am.ActivityStack.ActivityState.PAUSING;
import static com.android.server.am.ActivityStack.ActivityState.STOPPED;
import static com.android.server.am.ActivityStack.REMOVE_TASK_MODE_MOVING;
import static com.android.server.policy.WindowManagerPolicy.NAV_BAR_BOTTOM;
import static com.android.server.policy.WindowManagerPolicy.NAV_BAR_LEFT;
import static com.android.server.policy.WindowManagerPolicy.NAV_BAR_RIGHT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import android.app.servertransaction.ClientTransaction;
import android.app.servertransaction.PauseActivityItem;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.MutableBoolean;

import org.junit.runner.RunWith;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

/**
 * Tests for the {@link ActivityRecord} class.
 *
 * Build/Install/Run:
 *  atest FrameworksServicesTests:com.android.server.am.ActivityRecordTests
 */
@MediumTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityRecordTests extends ActivityTestsBase {
    private ActivityManagerService mService;
    private TestActivityStack mStack;
    private TaskRecord mTask;
    private ActivityRecord mActivity;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mService = createActivityManagerService();
        mStack = mService.mStackSupervisor.getDefaultDisplay().createStack(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        mTask = new TaskBuilder(mService.mStackSupervisor).setStack(mStack).build();
        mActivity = new ActivityBuilder(mService).setTask(mTask).build();
    }

    @Test
    public void testStackCleanupOnClearingTask() throws Exception {
        mActivity.setTask(null);
        assertEquals(mStack.onActivityRemovedFromStackInvocationCount(), 1);
    }

    @Test
    public void testStackCleanupOnActivityRemoval() throws Exception {
        mTask.removeActivity(mActivity);
        assertEquals(mStack.onActivityRemovedFromStackInvocationCount(),  1);
    }

    @Test
    public void testStackCleanupOnTaskRemoval() throws Exception {
        mStack.removeTask(mTask, null /*reason*/, REMOVE_TASK_MODE_MOVING);
        // Stack should be gone on task removal.
        assertNull(mService.mStackSupervisor.getStack(mStack.mStackId));
    }

    @Test
    public void testNoCleanupMovingActivityInSameStack() throws Exception {
        final TaskRecord newTask = new TaskBuilder(mService.mStackSupervisor).setStack(mStack)
                .build();
        mActivity.reparent(newTask, 0, null /*reason*/);
        assertEquals(mStack.onActivityRemovedFromStackInvocationCount(), 0);
    }

    @Test
    public void testPausingWhenVisibleFromStopped() throws Exception {
        final MutableBoolean pauseFound = new MutableBoolean(false);
        doAnswer((InvocationOnMock invocationOnMock) -> {
            final ClientTransaction transaction = invocationOnMock.getArgument(0);
            if (transaction.getLifecycleStateRequest() instanceof PauseActivityItem) {
                pauseFound.value = true;
            }
            return null;
        }).when(mActivity.app.thread).scheduleTransaction(any());
        mActivity.setState(STOPPED, "testPausingWhenVisibleFromStopped");

        mActivity.makeVisibleIfNeeded(null /* starting */);

        assertTrue(mActivity.isState(PAUSING));

        assertTrue(pauseFound.value);

        // Make sure that the state does not change for current non-stopping states.
        mActivity.setState(INITIALIZING, "testPausingWhenVisibleFromStopped");

        mActivity.makeVisibleIfNeeded(null /* starting */);

        assertTrue(mActivity.isState(INITIALIZING));
    }

    @Test
    public void testPositionLimitedAspectRatioNavBarBottom() throws Exception {
        verifyPositionWithLimitedAspectRatio(NAV_BAR_BOTTOM, new Rect(0, 0, 1000, 2000), 1.5f,
                new Rect(0, 0, 1000, 1500));
    }

    @Test
    public void testPositionLimitedAspectRatioNavBarLeft() throws Exception {
        verifyPositionWithLimitedAspectRatio(NAV_BAR_LEFT, new Rect(0, 0, 2000, 1000), 1.5f,
                new Rect(500, 0, 2000, 1000));
    }

    @Test
    public void testPositionLimitedAspectRatioNavBarRight() throws Exception {
        verifyPositionWithLimitedAspectRatio(NAV_BAR_RIGHT, new Rect(0, 0, 2000, 1000), 1.5f,
                new Rect(0, 0, 1500, 1000));
    }

    private void verifyPositionWithLimitedAspectRatio(int navBarPosition, Rect taskBounds,
            float aspectRatio, Rect expectedActivityBounds) {
        // Verify with nav bar on the right.
        when(mService.mWindowManager.getNavBarPosition()).thenReturn(navBarPosition);
        mTask.getConfiguration().windowConfiguration.setAppBounds(taskBounds);
        mActivity.info.maxAspectRatio = aspectRatio;
        mActivity.ensureActivityConfiguration(
                0 /* globalChanges */, false /* preserveWindow */);
        assertEquals(expectedActivityBounds, mActivity.getBounds());
    }

    @Test
    public void testCanBeLaunchedOnDisplay() throws Exception {
        testSupportsLaunchingResizeable(false /*taskPresent*/, true /*taskResizeable*/,
                true /*activityResizeable*/, true /*expected*/);

        testSupportsLaunchingResizeable(false /*taskPresent*/, true /*taskResizeable*/,
                false /*activityResizeable*/, false /*expected*/);

        testSupportsLaunchingResizeable(true /*taskPresent*/, false /*taskResizeable*/,
                true /*activityResizeable*/, false /*expected*/);

        testSupportsLaunchingResizeable(true /*taskPresent*/, true /*taskResizeable*/,
                false /*activityResizeable*/, true /*expected*/);
    }

    private void testSupportsLaunchingResizeable(boolean taskPresent, boolean taskResizeable,
            boolean activityResizeable, boolean expected) {
        mService.mSupportsMultiWindow = true;

        final TaskRecord task = taskPresent
                ? new TaskBuilder(mService.mStackSupervisor).setStack(mStack).build() : null;

        if (task != null) {
            task.setResizeMode(taskResizeable ? RESIZE_MODE_RESIZEABLE : RESIZE_MODE_UNRESIZEABLE);
        }

        final ActivityRecord record = new ActivityBuilder(mService).setTask(task).build();
        record.info.resizeMode = activityResizeable
                ? RESIZE_MODE_RESIZEABLE : RESIZE_MODE_UNRESIZEABLE;

        record.canBeLaunchedOnDisplay(DEFAULT_DISPLAY);


        verify(mService.mStackSupervisor, times(1)).canPlaceEntityOnDisplay(anyInt(), eq(expected),
                anyInt(), anyInt(), eq(record.info));
    }

    @Test
    public void testFinishingAfterDestroying() throws Exception {
        assertFalse(mActivity.finishing);
        mActivity.setState(DESTROYING, "testFinishingAfterDestroying");
        assertTrue(mActivity.isState(DESTROYING));
        assertTrue(mActivity.finishing);
    }

    @Test
    public void testFinishingAfterDestroyed() throws Exception {
        assertFalse(mActivity.finishing);
        mActivity.setState(DESTROYED, "testFinishingAfterDestroyed");
        assertTrue(mActivity.isState(DESTROYED));
        assertTrue(mActivity.finishing);
    }
}
