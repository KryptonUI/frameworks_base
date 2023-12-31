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

package com.android.systemui.shared.system;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;

import android.app.WindowConfiguration;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.RemoteAnimationTarget;

/**
 * @see RemoteAnimationTarget
 */
public class RemoteAnimationTargetCompat {

    public static final int MODE_OPENING = RemoteAnimationTarget.MODE_OPENING;
    public static final int MODE_CLOSING = RemoteAnimationTarget.MODE_CLOSING;

    public final int taskId;
    public final int mode;
    public final SurfaceControlCompat leash;
    public final boolean isTranslucent;
    public final Rect clipRect;
    public final int prefixOrderIndex;
    public final Point position;
    public final Rect sourceContainerBounds;

    private final RemoteAnimationTarget mTarget;

    public RemoteAnimationTargetCompat(RemoteAnimationTarget app) {
        mTarget = app;
        taskId = app.taskId;
        mode = app.mode;
        leash = new SurfaceControlCompat(app.leash);
        isTranslucent = app.isTranslucent;
        clipRect = app.clipRect;
        position = app.position;
        sourceContainerBounds = app.sourceContainerBounds;
        prefixOrderIndex = app.prefixOrderIndex;
    }

    public static RemoteAnimationTargetCompat[] wrap(RemoteAnimationTarget[] apps) {
        final RemoteAnimationTargetCompat[] appsCompat =
                new RemoteAnimationTargetCompat[apps.length];
        for (int i = 0; i < apps.length; i++) {
            appsCompat[i] = new RemoteAnimationTargetCompat(apps[i]);
        }
        return appsCompat;
    }

    /**
     * TODO: Get as a method for compatibility (will move into ctor once Launcher updates)
     */
    public Rect getContentInsets() {
        return mTarget.contentInsets;
    }

    /**
     * TODO: Get as a method for compatibility (will move into ctor once Launcher updates)
     */
    public boolean isAssistantActivityType() {
        return mTarget.windowConfiguration.getActivityType() == ACTIVITY_TYPE_ASSISTANT;
    }
}