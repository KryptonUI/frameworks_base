/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.display;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.opengl.Matrix;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import com.android.internal.annotations.GuardedBy;

import com.android.internal.app.ColorDisplayController;
import java.util.Arrays;

/**
 * Manager for applying color transformations to the display.
 */
public class DisplayTransformManager {

    private static final String TAG = "DisplayTransformManager";

    private static final String SURFACE_FLINGER = "SurfaceFlinger";

    /**
     * Color transform level used by Night display to tint the display red.
     */
    public static final int LEVEL_COLOR_MATRIX_NIGHT_DISPLAY = 100;
    /**
     * Color transform level used by A11y services to make the display monochromatic.
     */
    public static final int LEVEL_COLOR_MATRIX_GRAYSCALE = 200;
    /**
     * Color transform level used by A11y services to invert the display colors.
     */
    public static final int LEVEL_COLOR_MATRIX_INVERT_COLOR = 300;

    private static final int SURFACE_FLINGER_TRANSACTION_COLOR_MATRIX = 1015;
    private static final int SURFACE_FLINGER_TRANSACTION_DALTONIZER = 1014;

    private static final String PERSISTENT_PROPERTY_SATURATION = "persist.sys.sf.color_saturation";
    private static final String PERSISTENT_PROPERTY_NATIVE_MODE = "persist.sys.sf.native_mode";

    private static final int SURFACE_FLINGER_TRANSACTION_SATURATION = 1022;
    private static final int SURFACE_FLINGER_TRANSACTION_NATIVE_MODE = 1023;

    private static final float COLOR_SATURATION_NATURAL = 1.0f;
    private static final float COLOR_SATURATION_BOOSTED = 1.1f;

    /**
     * Map of level -> color transformation matrix.
     */
    @GuardedBy("mColorMatrix")
    private final SparseArray<float[]> mColorMatrix = new SparseArray<>(3);
    /**
     * Temporary matrix used internally by {@link #computeColorMatrixLocked()}.
     */
    @GuardedBy("mColorMatrix")
    private final float[][] mTempColorMatrix = new float[2][16];

    /**
     * Lock used for synchronize access to {@link #mDaltonizerMode}.
     */
    private final Object mDaltonizerModeLock = new Object();
    @GuardedBy("mDaltonizerModeLock")
    private int mDaltonizerMode = -1;

    /* package */ DisplayTransformManager() {
    }

    /**
     * Returns a copy of the color transform matrix set for a given level.
     */
    public float[] getColorMatrix(int key) {
        synchronized (mColorMatrix) {
            final float[] value = mColorMatrix.get(key);
            return value == null ? null : Arrays.copyOf(value, value.length);
        }
    }

    /**
     * Sets and applies a current color transform matrix for a given level.
     * <p>
     * Note: all color transforms are first composed to a single matrix in ascending order based
     * on level before being applied to the display.
     *
     * @param level the level used to identify and compose the color transform (low -> high)
     * @param value the 4x4 color transform matrix (in column-major order), or {@code null} to
     *              remove the color transform matrix associated with the provided level
     */
    public void setColorMatrix(int level, float[] value) {
        if (value != null && value.length != 16) {
            throw new IllegalArgumentException("Expected length: 16 (4x4 matrix)"
                    + ", actual length: " + value.length);
        }

        synchronized (mColorMatrix) {
            final float[] oldValue = mColorMatrix.get(level);
            if (!Arrays.equals(oldValue, value)) {
                if (value == null) {
                    mColorMatrix.remove(level);
                } else if (oldValue == null) {
                    mColorMatrix.put(level, Arrays.copyOf(value, value.length));
                } else {
                    System.arraycopy(value, 0, oldValue, 0, value.length);
                }

                // Update the current color transform.
                applyColorMatrix(computeColorMatrixLocked());
            }
        }
    }

    /**
     * Returns the composition of all current color matrices, or {@code null} if there are none.
     */
    @GuardedBy("mColorMatrix")
    private float[] computeColorMatrixLocked() {
        final int count = mColorMatrix.size();
        if (count == 0) {
            return null;
        }

        final float[][] result = mTempColorMatrix;
        Matrix.setIdentityM(result[0], 0);
        for (int i = 0; i < count; i++) {
            float[] rhs = mColorMatrix.valueAt(i);
            Matrix.multiplyMM(result[(i + 1) % 2], 0, result[i % 2], 0, rhs, 0);
        }
        return result[count % 2];
    }

    /**
     * Returns the current Daltonization mode.
     */
    public int getDaltonizerMode() {
        synchronized (mDaltonizerModeLock) {
            return mDaltonizerMode;
        }
    }

    /**
     * Sets the current Daltonization mode. This adjusts the color space to correct for or simulate
     * various types of color blindness.
     *
     * @param mode the new Daltonization mode, or -1 to disable
     */
    public void setDaltonizerMode(int mode) {
        synchronized (mDaltonizerModeLock) {
            if (mDaltonizerMode != mode) {
                mDaltonizerMode = mode;
                applyDaltonizerMode(mode);
            }
        }
    }

    /**
     * Propagates the provided color transformation matrix to the SurfaceFlinger.
     */
    private static void applyColorMatrix(float[] m) {
        final IBinder flinger = ServiceManager.getService(SURFACE_FLINGER);
        if (flinger != null) {
            final Parcel data = Parcel.obtain();
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            if (m != null) {
                data.writeInt(1);
                for (int i = 0; i < 16; i++) {
                    data.writeFloat(m[i]);
                }
            } else {
                data.writeInt(0);
            }
            try {
                flinger.transact(SURFACE_FLINGER_TRANSACTION_COLOR_MATRIX, data, null, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "Failed to set color transform", ex);
            } finally {
                data.recycle();
            }
        }
    }

    /**
     * Propagates the provided Daltonization mode to the SurfaceFlinger.
     */
    private static void applyDaltonizerMode(int mode) {
        final IBinder flinger = ServiceManager.getService(SURFACE_FLINGER);
        if (flinger != null) {
            final Parcel data = Parcel.obtain();
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            data.writeInt(mode);
            try {
                flinger.transact(SURFACE_FLINGER_TRANSACTION_DALTONIZER, data, null, 0);
            } catch (RemoteException ex) {
                Slog.e(TAG, "Failed to set Daltonizer mode", ex);
            } finally {
                data.recycle();
            }
        }
    }

    public static boolean isNativeModeEnabled() {
        return SystemProperties.getBoolean(PERSISTENT_PROPERTY_NATIVE_MODE, false);
    }

    public boolean setColorMode(int colorMode, float[] nightDisplayMatrix) {
        if (colorMode == ColorDisplayController.COLOR_MODE_NATURAL) {
            applySaturation(COLOR_SATURATION_NATURAL);
            setNativeMode(false);
        } else if (colorMode == ColorDisplayController.COLOR_MODE_BOOSTED) {
            applySaturation(COLOR_SATURATION_BOOSTED);
            setNativeMode(false);
        } else if (colorMode == ColorDisplayController.COLOR_MODE_SATURATED) {
            applySaturation(COLOR_SATURATION_NATURAL);
            setNativeMode(true);
        }
        setColorMatrix(LEVEL_COLOR_MATRIX_NIGHT_DISPLAY, nightDisplayMatrix);

        updateConfiguration();

        return true;
    }

    /**
     * Propagates the provided saturation to the SurfaceFlinger.
     */
    private void applySaturation(float saturation) {
        SystemProperties.set(PERSISTENT_PROPERTY_SATURATION, Float.toString(saturation));
        final IBinder flinger = ServiceManager.getService(SURFACE_FLINGER);
        if (flinger != null) {
            final Parcel data = Parcel.obtain();
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            data.writeFloat(saturation);
            try {
                flinger.transact(SURFACE_FLINGER_TRANSACTION_SATURATION, data, null, 0);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to set saturation", ex);
            } finally {
                data.recycle();
            }
        }
    }

    /**
     * Toggles native mode on/off in SurfaceFlinger.
     */
    private void setNativeMode(boolean enabled) {
        SystemProperties.set(PERSISTENT_PROPERTY_NATIVE_MODE, enabled ? "1" : "0");
        final IBinder flinger = ServiceManager.getService(SURFACE_FLINGER);
        if (flinger != null) {
            final Parcel data = Parcel.obtain();
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            data.writeInt(enabled ? 1 : 0);
            try {
                flinger.transact(SURFACE_FLINGER_TRANSACTION_NATIVE_MODE, data, null, 0);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to set native mode", ex);
            } finally {
                data.recycle();
            }
        }
    }

    private void updateConfiguration() {
        try {
            ActivityManager.getService().updateConfiguration(null);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not update configuration", e);
        }
    }
}
