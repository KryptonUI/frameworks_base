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

package com.android.server.wm;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.AnimationHandler;
import android.animation.AnimationHandler.AnimationFrameCallbackProvider;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.graphics.Point;
import android.platform.test.annotations.Presubmit;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;

import com.android.server.wm.LocalAnimationAdapter.AnimationSpec;
import com.android.server.wm.SurfaceAnimationRunner.AnimatorFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.CountDownLatch;

/**
 * Test class for {@link SurfaceAnimationRunner}.
 *
 * atest FrameworksServicesTests:com.android.server.wm.SurfaceAnimationRunnerTest
 */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class SurfaceAnimationRunnerTest extends WindowTestsBase {

    @Mock SurfaceControl mMockSurface;
    @Mock Transaction mMockTransaction;
    @Mock AnimationSpec mMockAnimationSpec;
    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();

    private SurfaceAnimationRunner mSurfaceAnimationRunner;
    private CountDownLatch mFinishCallbackLatch;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mFinishCallbackLatch = new CountDownLatch(1);
        mSurfaceAnimationRunner = new SurfaceAnimationRunner(null /* callbackProvider */, null,
                mMockTransaction);
    }

    private void finishedCallback() {
        mFinishCallbackLatch.countDown();
    }

    @Test
    public void testAnimation() throws Exception {
        mSurfaceAnimationRunner
                .startAnimation(createTranslateAnimation(), mMockSurface, mMockTransaction,
                this::finishedCallback);

        // Ensure that the initial transformation has been applied.
        final Matrix m = new Matrix();
        m.setTranslate(-10, 0);
        verify(mMockTransaction, atLeastOnce()).setMatrix(eq(mMockSurface), eq(m), any());
        verify(mMockTransaction, atLeastOnce()).setAlpha(eq(mMockSurface), eq(1.0f));

        mFinishCallbackLatch.await(1, SECONDS);
        assertFinishCallbackCalled();

        m.setTranslate(10, 0);
        verify(mMockTransaction, atLeastOnce()).setMatrix(eq(mMockSurface), eq(m), any());

        // At least 3 times: After initialization, first frame, last frame.
        verify(mMockTransaction, atLeast(3)).setAlpha(eq(mMockSurface), eq(1.0f));
    }

    @Test
    public void testCancel_notStarted() throws Exception {
        mSurfaceAnimationRunner = new SurfaceAnimationRunner(new NoOpFrameCallbackProvider(), null,
                mMockTransaction);
        mSurfaceAnimationRunner
                .startAnimation(createTranslateAnimation(), mMockSurface, mMockTransaction,
                this::finishedCallback);
        mSurfaceAnimationRunner.onAnimationCancelled(mMockSurface);
        waitUntilHandlersIdle();
        assertTrue(mSurfaceAnimationRunner.mPendingAnimations.isEmpty());
        assertFinishCallbackNotCalled();
    }

    @Test
    public void testCancel_running() throws Exception {
        mSurfaceAnimationRunner = new SurfaceAnimationRunner(new NoOpFrameCallbackProvider(), null,
                mMockTransaction);
        mSurfaceAnimationRunner.startAnimation(createTranslateAnimation(), mMockSurface,
                mMockTransaction, this::finishedCallback);
        waitUntilNextFrame();
        assertFalse(mSurfaceAnimationRunner.mRunningAnimations.isEmpty());
        mSurfaceAnimationRunner.onAnimationCancelled(mMockSurface);
        assertTrue(mSurfaceAnimationRunner.mRunningAnimations.isEmpty());
        waitUntilHandlersIdle();
        assertFinishCallbackNotCalled();
    }

    @FlakyTest(bugId = 71719744)
    @Test
    public void testCancel_sneakyCancelBeforeUpdate() throws Exception {
        mSurfaceAnimationRunner = new SurfaceAnimationRunner(null, () -> new ValueAnimator() {
            {
                setFloatValues(0f, 1f);
            }

            @Override
            public void addUpdateListener(AnimatorUpdateListener listener) {
                super.addUpdateListener(animation -> {
                    // Sneaky test cancels animation just before applying frame to simulate
                    // interleaving of multiple threads. Muahahaha
                    if (animation.getCurrentPlayTime() > 0) {
                        mSurfaceAnimationRunner.onAnimationCancelled(mMockSurface);
                    }
                    listener.onAnimationUpdate(animation);
                });
            }
        }, mMockTransaction);
        when(mMockAnimationSpec.getDuration()).thenReturn(200L);
        mSurfaceAnimationRunner.startAnimation(mMockAnimationSpec, mMockSurface, mMockTransaction,
                this::finishedCallback);

        // We need to wait for two frames: The first frame starts the animation, the second frame
        // actually cancels the animation.
        waitUntilNextFrame();
        waitUntilNextFrame();
        assertTrue(mSurfaceAnimationRunner.mRunningAnimations.isEmpty());
        verify(mMockAnimationSpec, atLeastOnce()).apply(any(), any(), eq(0L));
    }

    @Test
    public void testDeferStartingAnimations() throws Exception {
        mSurfaceAnimationRunner.deferStartingAnimations();
        mSurfaceAnimationRunner.startAnimation(createTranslateAnimation(), mMockSurface,
                mMockTransaction, this::finishedCallback);
        waitUntilNextFrame();
        assertTrue(mSurfaceAnimationRunner.mRunningAnimations.isEmpty());
        mSurfaceAnimationRunner.continueStartingAnimations();
        waitUntilNextFrame();
        assertFalse(mSurfaceAnimationRunner.mRunningAnimations.isEmpty());
        mFinishCallbackLatch.await(1, SECONDS);
        assertFinishCallbackCalled();
    }

    private void waitUntilNextFrame() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        mSurfaceAnimationRunner.mChoreographer.postCallback(Choreographer.CALLBACK_COMMIT,
                latch::countDown, null /* token */);
        latch.await();
    }

    private void assertFinishCallbackCalled() {
        assertEquals(0, mFinishCallbackLatch.getCount());
    }

    private void assertFinishCallbackNotCalled() {
        assertEquals(1, mFinishCallbackLatch.getCount());
    }

    private AnimationSpec createTranslateAnimation() {
        final Animation a = new TranslateAnimation(-10, 10, 0, 0);
        a.initialize(0, 0, 0, 0);
        a.setDuration(50);
        return new WindowAnimationSpec(a, new Point(0, 0), false /* canSkipFirstFrame */);
    }

    /**
     * Callback provider that doesn't animate at all.
     */
    private static final class NoOpFrameCallbackProvider implements AnimationFrameCallbackProvider {

        @Override
        public void postFrameCallback(FrameCallback callback) {
        }

        @Override
        public void postCommitCallback(Runnable runnable) {
        }

        @Override
        public long getFrameTime() {
            return 0;
        }

        @Override
        public long getFrameDelay() {
            return 0;
        }

        @Override
        public void setFrameDelay(long delay) {
        }
    }
}
