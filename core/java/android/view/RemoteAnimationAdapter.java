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

package android.view;

import android.app.ActivityOptions;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Object that describes how to run a remote animation.
 * <p>
 * A remote animation lets another app control the entire app transition. It does so by
 * <ul>
 *     <li>using {@link ActivityOptions#makeRemoteAnimation}</li>
 *     <li>using {@link IWindowManager#overridePendingAppTransitionRemote}</li>
 * </ul>
 * to register a {@link RemoteAnimationAdapter} that describes how the animation should be run:
 * Along some meta-data, this object contains a callback that gets invoked from window manager when
 * the transition is ready to be started.
 * <p>
 * Window manager supplies a list of {@link RemoteAnimationTarget}s into the callback. Each target
 * contains information about the activity that is animating as well as
 * {@link RemoteAnimationTarget#leash}. The controlling app can modify the leash like any other
 * {@link SurfaceControl}, including the possibility to synchronize updating the leash's surface
 * properties with a frame to be drawn using
 * {@link SurfaceControl.Transaction#deferTransactionUntil}.
 * <p>
 * When the animation is done, the controlling app can invoke
 * {@link IRemoteAnimationFinishedCallback} that gets supplied into
 * {@link IRemoteAnimationRunner#onStartAnimation}
 *
 * @hide
 */
public class RemoteAnimationAdapter implements Parcelable {

    private final IRemoteAnimationRunner mRunner;
    private final long mDuration;
    private final long mStatusBarTransitionDelay;

    /**
     * @param runner The interface that gets notified when we actually need to start the animation.
     * @param duration The duration of the animation.
     * @param statusBarTransitionDelay The desired delay for all visual animations in the
     *        status bar caused by this app animation in millis.
     */
    public RemoteAnimationAdapter(IRemoteAnimationRunner runner, long duration,
            long statusBarTransitionDelay) {
        mRunner = runner;
        mDuration = duration;
        mStatusBarTransitionDelay = statusBarTransitionDelay;
    }

    public RemoteAnimationAdapter(Parcel in) {
        mRunner = IRemoteAnimationRunner.Stub.asInterface(in.readStrongBinder());
        mDuration = in.readLong();
        mStatusBarTransitionDelay = in.readLong();
    }

    public IRemoteAnimationRunner getRunner() {
        return mRunner;
    }

    public long getDuration() {
        return mDuration;
    }

    public long getStatusBarTransitionDelay() {
        return mStatusBarTransitionDelay;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStrongInterface(mRunner);
        dest.writeLong(mDuration);
        dest.writeLong(mStatusBarTransitionDelay);
    }

    public static final Creator<RemoteAnimationAdapter> CREATOR
            = new Creator<RemoteAnimationAdapter>() {
        public RemoteAnimationAdapter createFromParcel(Parcel in) {
            return new RemoteAnimationAdapter(in);
        }

        public RemoteAnimationAdapter[] newArray(int size) {
            return new RemoteAnimationAdapter[size];
        }
    };
}
