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

package com.android.systemui.statusbar.phone;

import android.graphics.Color;
import android.os.Trace;
import android.util.MathUtils;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.statusbar.ScrimView;
import com.android.systemui.statusbar.stack.StackStateAnimator;

/**
 * Possible states of the ScrimController state machine.
 */
public enum ScrimState {

    /**
     * Initial state.
     */
    UNINITIALIZED(-1),

    /**
     * On the lock screen.
     */
    KEYGUARD(0) {

        @Override
        public void prepare(ScrimState previousState) {
            mBlankScreen = false;
            if (previousState == ScrimState.AOD) {
                mAnimationDuration = StackStateAnimator.ANIMATION_DURATION_WAKEUP;
                if (mDisplayRequiresBlanking) {
                    // DisplayPowerManager will blank the screen, we'll just
                    // set our scrim to black in this frame to avoid flickering and
                    // fade it out afterwards.
                    mBlankScreen = true;
                }
            } else {
                mAnimationDuration = ScrimController.ANIMATION_DURATION;
            }
            mCurrentBehindAlpha = mScrimBehindAlphaKeyguard;
            mCurrentInFrontAlpha = 0;
        }

        @Override
        public float getBehindAlpha(float busynessFactor) {
            return MathUtils.map(0 /* start */, 1 /* stop */,
                   ScrimController.GRADIENT_SCRIM_ALPHA, ScrimController.GRADIENT_SCRIM_ALPHA_BUSY,
                   busynessFactor);
        }
    },

    /**
     * Showing password challenge.
     */
    BOUNCER(1) {
        @Override
        public void prepare(ScrimState previousState) {
            mCurrentBehindAlpha = ScrimController.SCRIM_BEHIND_ALPHA_UNLOCKING;
            mCurrentInFrontAlpha = ScrimController.SCRIM_IN_FRONT_ALPHA_LOCKED;
        }
    },

    /**
     * Changing screen brightness from quick settings.
     */
    BRIGHTNESS_MIRROR(2) {
        @Override
        public void prepare(ScrimState previousState) {
            mCurrentBehindAlpha = 0;
            mCurrentInFrontAlpha = 0;
        }
    },

    /**
     * Always on display or screen off.
     */
    AOD(3) {
        @Override
        public void prepare(ScrimState previousState) {
            final boolean alwaysOnEnabled = mDozeParameters.getAlwaysOn();
            final boolean wasPulsing = previousState == ScrimState.PULSING;
            mBlankScreen = wasPulsing && !mCanControlScreenOff;
            mCurrentBehindAlpha = mWallpaperSupportsAmbientMode
                    && !mKeyguardUpdateMonitor.hasLockscreenWallpaper() ? 0f : 1f;
            mCurrentInFrontAlpha = alwaysOnEnabled ? mAodFrontScrimAlpha : 1f;
            mCurrentInFrontTint = Color.BLACK;
            mCurrentBehindTint = Color.BLACK;
            // DisplayPowerManager will blank the screen for us, we just need
            // to set our state.
            mAnimateChange = mCanControlScreenOff;
        }
    },

    /**
     * When phone wakes up because you received a notification.
     */
    PULSING(4) {
        @Override
        public void prepare(ScrimState previousState) {
            mCurrentInFrontAlpha = 0;
            mCurrentInFrontTint = Color.BLACK;
            mCurrentBehindAlpha = mWallpaperSupportsAmbientMode
                    && !mKeyguardUpdateMonitor.hasLockscreenWallpaper() ? 0f : 1f;
            mCurrentBehindTint = Color.BLACK;
            mBlankScreen = mDisplayRequiresBlanking;
        }
    },

    /**
     * Unlocked on top of an app (launcher or any other activity.)
     */
    UNLOCKED(5) {
        @Override
        public void prepare(ScrimState previousState) {
            mCurrentBehindAlpha = 0;
            mCurrentInFrontAlpha = 0;
            mAnimationDuration = StatusBar.FADE_KEYGUARD_DURATION;

            if (previousState == ScrimState.AOD) {
                // Fade from black to transparent when coming directly from AOD
                updateScrimColor(mScrimInFront, 1, Color.BLACK);
                updateScrimColor(mScrimBehind, 1, Color.BLACK);
                // Scrims should still be black at the end of the transition.
                mCurrentInFrontTint = Color.BLACK;
                mCurrentBehindTint = Color.BLACK;
                mBlankScreen = true;
            } else {
                // Scrims should still be black at the end of the transition.
                mCurrentInFrontTint = Color.TRANSPARENT;
                mCurrentBehindTint = Color.TRANSPARENT;
                mBlankScreen = false;
            }
        }
    };

    boolean mBlankScreen = false;
    long mAnimationDuration = ScrimController.ANIMATION_DURATION;
    int mCurrentInFrontTint = Color.TRANSPARENT;
    int mCurrentBehindTint = Color.TRANSPARENT;
    boolean mAnimateChange = true;
    float mCurrentInFrontAlpha;
    float mCurrentBehindAlpha;
    float mAodFrontScrimAlpha;
    float mScrimBehindAlphaKeyguard;
    ScrimView mScrimInFront;
    ScrimView mScrimBehind;
    DozeParameters mDozeParameters;
    boolean mDisplayRequiresBlanking;
    boolean mCanControlScreenOff;
    boolean mWallpaperSupportsAmbientMode;
    KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    int mIndex;

    ScrimState(int index) {
        mIndex = index;
    }

    public void init(ScrimView scrimInFront, ScrimView scrimBehind, DozeParameters dozeParameters) {
        mScrimInFront = scrimInFront;
        mScrimBehind = scrimBehind;
        mDozeParameters = dozeParameters;
        mDisplayRequiresBlanking = dozeParameters.getDisplayNeedsBlanking();
        mCanControlScreenOff = dozeParameters.getCanControlScreenOffAnimation();
        mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(scrimInFront.getContext());
    }

    public void prepare(ScrimState previousState) {
    }

    public int getIndex() {
        return mIndex;
    }

    public float getFrontAlpha() {
        return mCurrentInFrontAlpha;
    }

    public float getBehindAlpha(float busyness) {
        return mCurrentBehindAlpha;
    }

    public int getFrontTint() {
        return mCurrentInFrontTint;
    }

    public int getBehindTint() {
        return mCurrentBehindTint;
    }

    public long getAnimationDuration() {
        return mAnimationDuration;
    }

    public boolean getBlanksScreen() {
        return mBlankScreen;
    }

    public void updateScrimColor(ScrimView scrim, float alpha, int tint) {
        Trace.traceCounter(Trace.TRACE_TAG_APP,
                scrim == mScrimInFront ? "front_scrim_alpha" : "back_scrim_alpha",
                (int) (alpha * 255));

        Trace.traceCounter(Trace.TRACE_TAG_APP,
                scrim == mScrimInFront ? "front_scrim_tint" : "back_scrim_tint",
                Color.alpha(tint));

        scrim.setTint(tint);
        scrim.setViewAlpha(alpha);
    }

    public boolean getAnimateChange() {
        return mAnimateChange;
    }

    public void setAodFrontScrimAlpha(float aodFrontScrimAlpha) {
        mAodFrontScrimAlpha = aodFrontScrimAlpha;
    }

    public void setScrimBehindAlphaKeyguard(float scrimBehindAlphaKeyguard) {
        mScrimBehindAlphaKeyguard = scrimBehindAlphaKeyguard;
    }

    public void setWallpaperSupportsAmbientMode(boolean wallpaperSupportsAmbientMode) {
        mWallpaperSupportsAmbientMode = wallpaperSupportsAmbientMode;
    }
}