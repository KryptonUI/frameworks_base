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

package com.android.server.wm;

import com.android.internal.util.ToBooleanFunction;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;

import static com.android.server.policy.WindowManagerPolicy.FINISH_LAYOUT_REDO_WALLPAPER;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY_WITH_WALLPAPER;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_APP_TRANSITIONS;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_WALLPAPER;
import static com.android.server.wm.WindowManagerDebugConfig.DEBUG_WALLPAPER_LIGHT;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.wm.WindowManagerDebugConfig.TAG_WM;
import static com.android.server.wm.WindowManagerService.H.WALLPAPER_DRAW_PENDING_TIMEOUT;

import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.WindowManager;
import android.view.animation.Animation;

import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Controls wallpaper windows visibility, ordering, and so on.
 * NOTE: All methods in this class must be called with the window manager service lock held.
 */
class WallpaperController {
    private static final String TAG = TAG_WITH_CLASS_NAME ? "WallpaperController" : TAG_WM;
    private WindowManagerService mService;

    private final ArrayList<WallpaperWindowToken> mWallpaperTokens = new ArrayList<>();

    // If non-null, this is the currently visible window that is associated
    // with the wallpaper.
    private WindowState mWallpaperTarget = null;
    // If non-null, we are in the middle of animating from one wallpaper target
    // to another, and this is the previous wallpaper target.
    private WindowState mPrevWallpaperTarget = null;

    private float mLastWallpaperX = -1;
    private float mLastWallpaperY = -1;
    private float mLastWallpaperXStep = -1;
    private float mLastWallpaperYStep = -1;
    private int mLastWallpaperDisplayOffsetX = Integer.MIN_VALUE;
    private int mLastWallpaperDisplayOffsetY = Integer.MIN_VALUE;

    // This is set when we are waiting for a wallpaper to tell us it is done
    // changing its scroll position.
    private WindowState mWaitingOnWallpaper;

    // The last time we had a timeout when waiting for a wallpaper.
    private long mLastWallpaperTimeoutTime;
    // We give a wallpaper up to 150ms to finish scrolling.
    private static final long WALLPAPER_TIMEOUT = 150;
    // Time we wait after a timeout before trying to wait again.
    private static final long WALLPAPER_TIMEOUT_RECOVERY = 10000;

    // Set to the wallpaper window we would like to hide once the transition animations are done.
    // This is useful in cases where we don't want the wallpaper to be hidden when the close app
    // is a wallpaper target and is done animating out, but the opening app isn't a wallpaper
    // target and isn't done animating in.
    WindowState mDeferredHideWallpaper = null;

    // We give a wallpaper up to 500ms to finish drawing before playing app transitions.
    private static final long WALLPAPER_DRAW_PENDING_TIMEOUT_DURATION = 500;
    private static final int WALLPAPER_DRAW_NORMAL = 0;
    private static final int WALLPAPER_DRAW_PENDING = 1;
    private static final int WALLPAPER_DRAW_TIMEOUT = 2;
    private int mWallpaperDrawState = WALLPAPER_DRAW_NORMAL;

    private final FindWallpaperTargetResult mFindResults = new FindWallpaperTargetResult();

    private final ToBooleanFunction<WindowState> mFindWallpaperTargetFunction = w -> {
        final WindowAnimator winAnimator = mService.mAnimator;
        if ((w.mAttrs.type == TYPE_WALLPAPER)) {
            if (mFindResults.topWallpaper == null || mFindResults.resetTopWallpaper) {
                mFindResults.setTopWallpaper(w);
                mFindResults.resetTopWallpaper = false;
            }
            return false;
        }

        mFindResults.resetTopWallpaper = true;
        if (w != winAnimator.mWindowDetachedWallpaper && w.mAppToken != null) {
            // If this window's app token is hidden and not animating, it is of no interest to us.
            if (w.mAppToken.isHidden() && !w.mAppToken.isSelfAnimating()) {
                if (DEBUG_WALLPAPER) Slog.v(TAG,
                        "Skipping hidden and not animating token: " + w);
                return false;
            }
        }
        if (DEBUG_WALLPAPER) Slog.v(TAG, "Win " + w + ": isOnScreen=" + w.isOnScreen()
                + " mDrawState=" + w.mWinAnimator.mDrawState);

        if (w.mWillReplaceWindow && mWallpaperTarget == null
                && !mFindResults.useTopWallpaperAsTarget) {
            // When we are replacing a window and there was wallpaper before replacement, we want to
            // keep the window until the new windows fully appear and can determine the visibility,
            // to avoid flickering.
            mFindResults.setUseTopWallpaperAsTarget(true);
        }

        final boolean keyguardGoingAwayWithWallpaper = (w.mAppToken != null
                && w.mAppToken.isSelfAnimating()
                && AppTransition.isKeyguardGoingAwayTransit(w.mAppToken.getTransit())
                && (w.mAppToken.getTransitFlags()
                        & TRANSIT_FLAG_KEYGUARD_GOING_AWAY_WITH_WALLPAPER) != 0);

        boolean needsShowWhenLockedWallpaper = false;
        if ((w.mAttrs.flags & FLAG_SHOW_WHEN_LOCKED) != 0
                && mService.mPolicy.isKeyguardLocked()
                && mService.mPolicy.isKeyguardOccluded()) {
            // The lowest show when locked window decides whether we need to put the wallpaper
            // behind.
            needsShowWhenLockedWallpaper = !isFullscreen(w.mAttrs)
                    || (w.mAppToken != null && !w.mAppToken.fillsParent());
        }

        if (keyguardGoingAwayWithWallpaper || needsShowWhenLockedWallpaper) {
            // Keep the wallpaper during Keyguard exit but also when it's needed for a
            // non-fullscreen show when locked activity.
            mFindResults.setUseTopWallpaperAsTarget(true);
        }

        final RecentsAnimationController recentsAnimationController =
                mService.getRecentsAnimationController();
        final boolean animationWallpaper = w.mAppToken != null && w.mAppToken.getAnimation() != null
                && w.mAppToken.getAnimation().getShowWallpaper();
        final boolean hasWallpaper = (w.mAttrs.flags & FLAG_SHOW_WALLPAPER) != 0
                || animationWallpaper;
        final boolean isRecentsTransitionTarget = (recentsAnimationController != null
                && recentsAnimationController.isWallpaperVisible(w));
        if (isRecentsTransitionTarget) {
            if (DEBUG_WALLPAPER) Slog.v(TAG, "Found recents animation wallpaper target: " + w);
            mFindResults.setWallpaperTarget(w);
            return true;
        } else if (hasWallpaper && w.isOnScreen()
                && (mWallpaperTarget == w || w.isDrawFinishedLw())) {
            if (DEBUG_WALLPAPER) Slog.v(TAG, "Found wallpaper target: " + w);
            mFindResults.setWallpaperTarget(w);
            if (w == mWallpaperTarget && w.mWinAnimator.isAnimationSet()) {
                // The current wallpaper target is animating, so we'll look behind it for
                // another possible target and figure out what is going on later.
                if (DEBUG_WALLPAPER) Slog.v(TAG,
                        "Win " + w + ": token animating, looking behind.");
            }
            // Found a target! End search.
            return true;
        } else if (w == winAnimator.mWindowDetachedWallpaper) {
            if (DEBUG_WALLPAPER_LIGHT) Slog.v(TAG,
                    "Found animating detached wallpaper target win: " + w);
            mFindResults.setUseTopWallpaperAsTarget(true);
        }
        return false;
    };

    public WallpaperController(WindowManagerService service) {
        mService = service;
    }

    WindowState getWallpaperTarget() {
        return mWallpaperTarget;
    }

    boolean isWallpaperTarget(WindowState win) {
        return win == mWallpaperTarget;
    }

    boolean isBelowWallpaperTarget(WindowState win) {
        return mWallpaperTarget != null && mWallpaperTarget.mLayer >= win.mBaseLayer;
    }

    boolean isWallpaperVisible() {
        return isWallpaperVisible(mWallpaperTarget);
    }

    /**
     * Starts {@param a} on all wallpaper windows.
     */
    void startWallpaperAnimation(Animation a) {
        for (int curTokenNdx = mWallpaperTokens.size() - 1; curTokenNdx >= 0; curTokenNdx--) {
            final WallpaperWindowToken token = mWallpaperTokens.get(curTokenNdx);
            token.startAnimation(a);
        }
    }

    private final boolean isWallpaperVisible(WindowState wallpaperTarget) {
        final RecentsAnimationController recentsAnimationController =
                mService.getRecentsAnimationController();
        boolean isAnimatingWithRecentsComponent = recentsAnimationController != null
                && recentsAnimationController.isWallpaperVisible(wallpaperTarget);
        if (DEBUG_WALLPAPER) Slog.v(TAG, "Wallpaper vis: target " + wallpaperTarget + ", obscured="
                + (wallpaperTarget != null ? Boolean.toString(wallpaperTarget.mObscured) : "??")
                + " animating=" + ((wallpaperTarget != null && wallpaperTarget.mAppToken != null)
                ? wallpaperTarget.mAppToken.isSelfAnimating() : null)
                + " prev=" + mPrevWallpaperTarget
                + " recentsAnimationWallpaperVisible=" + isAnimatingWithRecentsComponent);
        return (wallpaperTarget != null
                && (!wallpaperTarget.mObscured
                        || isAnimatingWithRecentsComponent
                        || (wallpaperTarget.mAppToken != null
                                && wallpaperTarget.mAppToken.isSelfAnimating())))
                || mPrevWallpaperTarget != null;
    }

    boolean isWallpaperTargetAnimating() {
        return mWallpaperTarget != null && mWallpaperTarget.mWinAnimator.isAnimationSet()
                && (mWallpaperTarget.mAppToken == null
                        || !mWallpaperTarget.mAppToken.isWaitingForTransitionStart());
    }

    void updateWallpaperVisibility() {
        final boolean visible = isWallpaperVisible(mWallpaperTarget);

        for (int curTokenNdx = mWallpaperTokens.size() - 1; curTokenNdx >= 0; curTokenNdx--) {
            final WallpaperWindowToken token = mWallpaperTokens.get(curTokenNdx);
            token.updateWallpaperVisibility(visible);
        }
    }

    void hideDeferredWallpapersIfNeeded() {
        if (mDeferredHideWallpaper != null) {
            hideWallpapers(mDeferredHideWallpaper);
            mDeferredHideWallpaper = null;
        }
    }

    void hideWallpapers(final WindowState winGoingAway) {
        if (mWallpaperTarget != null
                && (mWallpaperTarget != winGoingAway || mPrevWallpaperTarget != null)) {
            return;
        }
        if (mService.mAppTransition.isRunning()) {
            // Defer hiding the wallpaper when app transition is running until the animations
            // are done.
            mDeferredHideWallpaper = winGoingAway;
            return;
        }

        final boolean wasDeferred = (mDeferredHideWallpaper == winGoingAway);
        for (int i = mWallpaperTokens.size() - 1; i >= 0; i--) {
            final WallpaperWindowToken token = mWallpaperTokens.get(i);
            token.hideWallpaperToken(wasDeferred, "hideWallpapers");
            if (DEBUG_WALLPAPER_LIGHT && !token.isHidden()) Slog.d(TAG, "Hiding wallpaper " + token
                    + " from " + winGoingAway + " target=" + mWallpaperTarget + " prev="
                    + mPrevWallpaperTarget + "\n" + Debug.getCallers(5, "  "));
        }
    }

    boolean updateWallpaperOffset(WindowState wallpaperWin, int dw, int dh, boolean sync) {
        int xOffset = 0;
        int yOffset = 0;
        boolean rawChanged = false;
        // Set the default wallpaper x-offset to either edge of the screen (depending on RTL), to
        // match the behavior of most Launchers
        float defaultWallpaperX = wallpaperWin.isRtl() ? 1f : 0f;
        float wpx = mLastWallpaperX >= 0 ? mLastWallpaperX : defaultWallpaperX;
        float wpxs = mLastWallpaperXStep >= 0 ? mLastWallpaperXStep : -1.0f;
        int availw = wallpaperWin.mFrame.right - wallpaperWin.mFrame.left - dw;
        int offset = availw > 0 ? -(int)(availw * wpx + .5f) : 0;
        if (mLastWallpaperDisplayOffsetX != Integer.MIN_VALUE) {
            offset += mLastWallpaperDisplayOffsetX;
        }
        xOffset = offset;

        if (wallpaperWin.mWallpaperX != wpx || wallpaperWin.mWallpaperXStep != wpxs) {
            wallpaperWin.mWallpaperX = wpx;
            wallpaperWin.mWallpaperXStep = wpxs;
            rawChanged = true;
        }

        float wpy = mLastWallpaperY >= 0 ? mLastWallpaperY : 0.5f;
        float wpys = mLastWallpaperYStep >= 0 ? mLastWallpaperYStep : -1.0f;
        int availh = wallpaperWin.mFrame.bottom - wallpaperWin.mFrame.top - dh;
        offset = availh > 0 ? -(int)(availh * wpy + .5f) : 0;
        if (mLastWallpaperDisplayOffsetY != Integer.MIN_VALUE) {
            offset += mLastWallpaperDisplayOffsetY;
        }
        yOffset = offset;

        if (wallpaperWin.mWallpaperY != wpy || wallpaperWin.mWallpaperYStep != wpys) {
            wallpaperWin.mWallpaperY = wpy;
            wallpaperWin.mWallpaperYStep = wpys;
            rawChanged = true;
        }

        boolean changed = wallpaperWin.mWinAnimator.setWallpaperOffset(xOffset, yOffset);

        if (rawChanged && (wallpaperWin.mAttrs.privateFlags &
                WindowManager.LayoutParams.PRIVATE_FLAG_WANTS_OFFSET_NOTIFICATIONS) != 0) {
            try {
                if (DEBUG_WALLPAPER) Slog.v(TAG, "Report new wp offset "
                        + wallpaperWin + " x=" + wallpaperWin.mWallpaperX
                        + " y=" + wallpaperWin.mWallpaperY);
                if (sync) {
                    mWaitingOnWallpaper = wallpaperWin;
                }
                wallpaperWin.mClient.dispatchWallpaperOffsets(
                        wallpaperWin.mWallpaperX, wallpaperWin.mWallpaperY,
                        wallpaperWin.mWallpaperXStep, wallpaperWin.mWallpaperYStep, sync);
                if (sync) {
                    if (mWaitingOnWallpaper != null) {
                        long start = SystemClock.uptimeMillis();
                        if ((mLastWallpaperTimeoutTime + WALLPAPER_TIMEOUT_RECOVERY)
                                < start) {
                            try {
                                if (DEBUG_WALLPAPER) Slog.v(TAG,
                                        "Waiting for offset complete...");
                                mService.mWindowMap.wait(WALLPAPER_TIMEOUT);
                            } catch (InterruptedException e) {
                            }
                            if (DEBUG_WALLPAPER) Slog.v(TAG, "Offset complete!");
                            if ((start + WALLPAPER_TIMEOUT) < SystemClock.uptimeMillis()) {
                                Slog.i(TAG, "Timeout waiting for wallpaper to offset: "
                                        + wallpaperWin);
                                mLastWallpaperTimeoutTime = start;
                            }
                        }
                        mWaitingOnWallpaper = null;
                    }
                }
            } catch (RemoteException e) {
            }
        }

        return changed;
    }

    void setWindowWallpaperPosition(
            WindowState window, float x, float y, float xStep, float yStep) {
        if (window.mWallpaperX != x || window.mWallpaperY != y)  {
            window.mWallpaperX = x;
            window.mWallpaperY = y;
            window.mWallpaperXStep = xStep;
            window.mWallpaperYStep = yStep;
            updateWallpaperOffsetLocked(window, true);
        }
    }

    void setWindowWallpaperDisplayOffset(WindowState window, int x, int y) {
        if (window.mWallpaperDisplayOffsetX != x || window.mWallpaperDisplayOffsetY != y)  {
            window.mWallpaperDisplayOffsetX = x;
            window.mWallpaperDisplayOffsetY = y;
            updateWallpaperOffsetLocked(window, true);
        }
    }

    Bundle sendWindowWallpaperCommand(
            WindowState window, String action, int x, int y, int z, Bundle extras, boolean sync) {
        if (window == mWallpaperTarget || window == mPrevWallpaperTarget) {
            boolean doWait = sync;
            for (int curTokenNdx = mWallpaperTokens.size() - 1; curTokenNdx >= 0; curTokenNdx--) {
                final WallpaperWindowToken token = mWallpaperTokens.get(curTokenNdx);
                token.sendWindowWallpaperCommand(action, x, y, z, extras, sync);
            }

            if (doWait) {
                // TODO: Need to wait for result.
            }
        }

        return null;
    }

    private void updateWallpaperOffsetLocked(WindowState changingTarget, boolean sync) {
        final DisplayContent displayContent = changingTarget.getDisplayContent();
        if (displayContent == null) {
            return;
        }
        final DisplayInfo displayInfo = displayContent.getDisplayInfo();
        final int dw = displayInfo.logicalWidth;
        final int dh = displayInfo.logicalHeight;

        WindowState target = mWallpaperTarget;
        if (target != null) {
            if (target.mWallpaperX >= 0) {
                mLastWallpaperX = target.mWallpaperX;
            } else if (changingTarget.mWallpaperX >= 0) {
                mLastWallpaperX = changingTarget.mWallpaperX;
            }
            if (target.mWallpaperY >= 0) {
                mLastWallpaperY = target.mWallpaperY;
            } else if (changingTarget.mWallpaperY >= 0) {
                mLastWallpaperY = changingTarget.mWallpaperY;
            }
            if (target.mWallpaperDisplayOffsetX != Integer.MIN_VALUE) {
                mLastWallpaperDisplayOffsetX = target.mWallpaperDisplayOffsetX;
            } else if (changingTarget.mWallpaperDisplayOffsetX != Integer.MIN_VALUE) {
                mLastWallpaperDisplayOffsetX = changingTarget.mWallpaperDisplayOffsetX;
            }
            if (target.mWallpaperDisplayOffsetY != Integer.MIN_VALUE) {
                mLastWallpaperDisplayOffsetY = target.mWallpaperDisplayOffsetY;
            } else if (changingTarget.mWallpaperDisplayOffsetY != Integer.MIN_VALUE) {
                mLastWallpaperDisplayOffsetY = changingTarget.mWallpaperDisplayOffsetY;
            }
            if (target.mWallpaperXStep >= 0) {
                mLastWallpaperXStep = target.mWallpaperXStep;
            } else if (changingTarget.mWallpaperXStep >= 0) {
                mLastWallpaperXStep = changingTarget.mWallpaperXStep;
            }
            if (target.mWallpaperYStep >= 0) {
                mLastWallpaperYStep = target.mWallpaperYStep;
            } else if (changingTarget.mWallpaperYStep >= 0) {
                mLastWallpaperYStep = changingTarget.mWallpaperYStep;
            }
        }

        for (int curTokenNdx = mWallpaperTokens.size() - 1; curTokenNdx >= 0; curTokenNdx--) {
            mWallpaperTokens.get(curTokenNdx).updateWallpaperOffset(dw, dh, sync);
        }
    }

    void clearLastWallpaperTimeoutTime() {
        mLastWallpaperTimeoutTime = 0;
    }

    void wallpaperCommandComplete(IBinder window) {
        if (mWaitingOnWallpaper != null &&
                mWaitingOnWallpaper.mClient.asBinder() == window) {
            mWaitingOnWallpaper = null;
            mService.mWindowMap.notifyAll();
        }
    }

    void wallpaperOffsetsComplete(IBinder window) {
        if (mWaitingOnWallpaper != null &&
                mWaitingOnWallpaper.mClient.asBinder() == window) {
            mWaitingOnWallpaper = null;
            mService.mWindowMap.notifyAll();
        }
    }

    private void findWallpaperTarget(DisplayContent dc) {
        mFindResults.reset();
        if (dc.isStackVisible(WINDOWING_MODE_FREEFORM)) {
            // In freeform mode we set the wallpaper as its own target, so we don't need an
            // additional window to make it visible.
            mFindResults.setUseTopWallpaperAsTarget(true);
        }

        dc.forAllWindows(mFindWallpaperTargetFunction, true /* traverseTopToBottom */);

        if (mFindResults.wallpaperTarget == null && mFindResults.useTopWallpaperAsTarget) {
            mFindResults.setWallpaperTarget(mFindResults.topWallpaper);
        }
    }

    private boolean isFullscreen(WindowManager.LayoutParams attrs) {
        return attrs.x == 0 && attrs.y == 0
                && attrs.width == MATCH_PARENT && attrs.height == MATCH_PARENT;
    }

    /** Updates the target wallpaper if needed and returns true if an update happened. */
    private void updateWallpaperWindowsTarget(DisplayContent dc,
            FindWallpaperTargetResult result) {

        WindowState wallpaperTarget = result.wallpaperTarget;

        if (mWallpaperTarget == wallpaperTarget
                || (mPrevWallpaperTarget != null && mPrevWallpaperTarget == wallpaperTarget)) {

            if (mPrevWallpaperTarget == null) {
                return;
            }

            // Is it time to stop animating?
            if (!mPrevWallpaperTarget.isAnimatingLw()) {
                if (DEBUG_WALLPAPER_LIGHT) Slog.v(TAG, "No longer animating wallpaper targets!");
                mPrevWallpaperTarget = null;
                mWallpaperTarget = wallpaperTarget;
            }
            return;
        }

        if (DEBUG_WALLPAPER_LIGHT) Slog.v(TAG,
                "New wallpaper target: " + wallpaperTarget + " prevTarget: " + mWallpaperTarget);

        mPrevWallpaperTarget = null;

        final WindowState prevWallpaperTarget = mWallpaperTarget;
        mWallpaperTarget = wallpaperTarget;

        if (wallpaperTarget == null || prevWallpaperTarget == null) {
            return;
        }

        // Now what is happening...  if the current and new targets are animating,
        // then we are in our super special mode!
        boolean oldAnim = prevWallpaperTarget.isAnimatingLw();
        boolean foundAnim = wallpaperTarget.isAnimatingLw();
        if (DEBUG_WALLPAPER_LIGHT) Slog.v(TAG,
                "New animation: " + foundAnim + " old animation: " + oldAnim);

        if (!foundAnim || !oldAnim) {
            return;
        }

        if (dc.getWindow(w -> w == prevWallpaperTarget) == null) {
            return;
        }

        final boolean newTargetHidden = wallpaperTarget.mAppToken != null
                && wallpaperTarget.mAppToken.hiddenRequested;
        final boolean oldTargetHidden = prevWallpaperTarget.mAppToken != null
                && prevWallpaperTarget.mAppToken.hiddenRequested;

        if (DEBUG_WALLPAPER_LIGHT) Slog.v(TAG, "Animating wallpapers:" + " old: "
                + prevWallpaperTarget + " hidden=" + oldTargetHidden + " new: " + wallpaperTarget
                + " hidden=" + newTargetHidden);

        mPrevWallpaperTarget = prevWallpaperTarget;

        if (newTargetHidden && !oldTargetHidden) {
            if (DEBUG_WALLPAPER_LIGHT) Slog.v(TAG, "Old wallpaper still the target.");
            // Use the old target if new target is hidden but old target
            // is not. If they're both hidden, still use the new target.
            mWallpaperTarget = prevWallpaperTarget;
        } else if (newTargetHidden == oldTargetHidden
                && !mService.mOpeningApps.contains(wallpaperTarget.mAppToken)
                && (mService.mOpeningApps.contains(prevWallpaperTarget.mAppToken)
                || mService.mClosingApps.contains(prevWallpaperTarget.mAppToken))) {
            // If they're both hidden (or both not hidden), prefer the one that's currently in
            // opening or closing app list, this allows transition selection logic to better
            // determine the wallpaper status of opening/closing apps.
            mWallpaperTarget = prevWallpaperTarget;
        }

        result.setWallpaperTarget(wallpaperTarget);
    }

    private void updateWallpaperTokens(boolean visible) {
        for (int curTokenNdx = mWallpaperTokens.size() - 1; curTokenNdx >= 0; curTokenNdx--) {
            final WallpaperWindowToken token = mWallpaperTokens.get(curTokenNdx);
            token.updateWallpaperWindows(visible);
            token.getDisplayContent().assignWindowLayers(false);
        }
    }

    void adjustWallpaperWindows(DisplayContent dc) {
        mService.mRoot.mWallpaperMayChange = false;

        // First find top-most window that has asked to be on top of the wallpaper;
        // all wallpapers go behind it.
        findWallpaperTarget(dc);
        updateWallpaperWindowsTarget(dc, mFindResults);

        // The window is visible to the compositor...but is it visible to the user?
        // That is what the wallpaper cares about.
        final boolean visible = mWallpaperTarget != null && isWallpaperVisible(mWallpaperTarget);
        if (DEBUG_WALLPAPER) Slog.v(TAG, "Wallpaper visibility: " + visible);

        if (visible) {
            if (mWallpaperTarget.mWallpaperX >= 0) {
                mLastWallpaperX = mWallpaperTarget.mWallpaperX;
                mLastWallpaperXStep = mWallpaperTarget.mWallpaperXStep;
            }
            if (mWallpaperTarget.mWallpaperY >= 0) {
                mLastWallpaperY = mWallpaperTarget.mWallpaperY;
                mLastWallpaperYStep = mWallpaperTarget.mWallpaperYStep;
            }
            if (mWallpaperTarget.mWallpaperDisplayOffsetX != Integer.MIN_VALUE) {
                mLastWallpaperDisplayOffsetX = mWallpaperTarget.mWallpaperDisplayOffsetX;
            }
            if (mWallpaperTarget.mWallpaperDisplayOffsetY != Integer.MIN_VALUE) {
                mLastWallpaperDisplayOffsetY = mWallpaperTarget.mWallpaperDisplayOffsetY;
            }
        }

        updateWallpaperTokens(visible);

        if (DEBUG_WALLPAPER_LIGHT)  Slog.d(TAG, "New wallpaper: target=" + mWallpaperTarget
                + " prev=" + mPrevWallpaperTarget);
    }

    boolean processWallpaperDrawPendingTimeout() {
        if (mWallpaperDrawState == WALLPAPER_DRAW_PENDING) {
            mWallpaperDrawState = WALLPAPER_DRAW_TIMEOUT;
            if (DEBUG_APP_TRANSITIONS || DEBUG_WALLPAPER) Slog.v(TAG,
                    "*** WALLPAPER DRAW TIMEOUT");

            // If there was a recents animation in progress, cancel that animation
            if (mService.getRecentsAnimationController() != null) {
                mService.getRecentsAnimationController().cancelAnimation();
            }
            return true;
        }
        return false;
    }

    boolean wallpaperTransitionReady() {
        boolean transitionReady = true;
        boolean wallpaperReady = true;
        for (int curTokenIndex = mWallpaperTokens.size() - 1;
                curTokenIndex >= 0 && wallpaperReady; curTokenIndex--) {
            final WallpaperWindowToken token = mWallpaperTokens.get(curTokenIndex);
            if (token.hasVisibleNotDrawnWallpaper()) {
                // We've told this wallpaper to be visible, but it is not drawn yet
                wallpaperReady = false;
                if (mWallpaperDrawState != WALLPAPER_DRAW_TIMEOUT) {
                    // wait for this wallpaper until it is drawn or timeout
                    transitionReady = false;
                }
                if (mWallpaperDrawState == WALLPAPER_DRAW_NORMAL) {
                    mWallpaperDrawState = WALLPAPER_DRAW_PENDING;
                    mService.mH.removeMessages(WALLPAPER_DRAW_PENDING_TIMEOUT);
                    mService.mH.sendEmptyMessageDelayed(WALLPAPER_DRAW_PENDING_TIMEOUT,
                            WALLPAPER_DRAW_PENDING_TIMEOUT_DURATION);
                }
                if (DEBUG_APP_TRANSITIONS || DEBUG_WALLPAPER) Slog.v(TAG,
                        "Wallpaper should be visible but has not been drawn yet. " +
                                "mWallpaperDrawState=" + mWallpaperDrawState);
                break;
            }
        }
        if (wallpaperReady) {
            mWallpaperDrawState = WALLPAPER_DRAW_NORMAL;
            mService.mH.removeMessages(WALLPAPER_DRAW_PENDING_TIMEOUT);
        }

        return transitionReady;
    }

    /**
     * Adjusts the wallpaper windows if the input display has a pending wallpaper layout or one of
     * the opening apps should be a wallpaper target.
     */
    void adjustWallpaperWindowsForAppTransitionIfNeeded(DisplayContent dc,
            ArraySet<AppWindowToken> openingApps) {
        boolean adjust = false;
        if ((dc.pendingLayoutChanges & FINISH_LAYOUT_REDO_WALLPAPER) != 0) {
            adjust = true;
        } else {
            for (int i = openingApps.size() - 1; i >= 0; --i) {
                final AppWindowToken token = openingApps.valueAt(i);
                if (token.windowsCanBeWallpaperTarget()) {
                    adjust = true;
                    break;
                }
            }
        }

        if (adjust) {
            adjustWallpaperWindows(dc);
        }
    }

    void addWallpaperToken(WallpaperWindowToken token) {
        mWallpaperTokens.add(token);
    }

    void removeWallpaperToken(WallpaperWindowToken token) {
        mWallpaperTokens.remove(token);
    }

    void dump(PrintWriter pw, String prefix) {
        pw.print(prefix); pw.print("mWallpaperTarget="); pw.println(mWallpaperTarget);
        if (mPrevWallpaperTarget != null) {
            pw.print(prefix); pw.print("mPrevWallpaperTarget="); pw.println(mPrevWallpaperTarget);
        }
        pw.print(prefix); pw.print("mLastWallpaperX="); pw.print(mLastWallpaperX);
        pw.print(" mLastWallpaperY="); pw.println(mLastWallpaperY);
        if (mLastWallpaperDisplayOffsetX != Integer.MIN_VALUE
                || mLastWallpaperDisplayOffsetY != Integer.MIN_VALUE) {
            pw.print(prefix);
            pw.print("mLastWallpaperDisplayOffsetX="); pw.print(mLastWallpaperDisplayOffsetX);
            pw.print(" mLastWallpaperDisplayOffsetY="); pw.println(mLastWallpaperDisplayOffsetY);
        }
    }

    /** Helper class for storing the results of a wallpaper target find operation. */
    final private static class FindWallpaperTargetResult {
        WindowState topWallpaper = null;
        boolean useTopWallpaperAsTarget = false;
        WindowState wallpaperTarget = null;
        boolean resetTopWallpaper = false;

        void setTopWallpaper(WindowState win) {
            topWallpaper = win;
        }

        void setWallpaperTarget(WindowState win) {
            wallpaperTarget = win;
        }

        void setUseTopWallpaperAsTarget(boolean topWallpaperAsTarget) {
            useTopWallpaperAsTarget = topWallpaperAsTarget;
        }

        void reset() {
            topWallpaper = null;
            wallpaperTarget = null;
            useTopWallpaperAsTarget = false;
            resetTopWallpaper = false;
        }
    }
}
