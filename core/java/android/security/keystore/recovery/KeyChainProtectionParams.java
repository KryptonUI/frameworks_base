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
 * limitations under the License.
 */

package android.security.keystore.recovery;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * A {@link KeyChainSnapshot} is protected with a key derived from the user's lock screen. This
 * class wraps all the data necessary to derive the same key on a recovering device:
 *
 * <ul>
 *     <li>UI parameters for the user's lock screen - so that if e.g., the user was using a pattern,
 *         the recovering device can display the pattern UI to the user when asking them to enter
 *         the lock screen from their previous device.
 *     <li>The algorithm used to derive a key from the user's lock screen, e.g. SHA-256 with a salt.
 * </ul>
 *
 * <p>As such, this data is sent along with the {@link KeyChainSnapshot} when syncing the current
 * version of the keychain.
 *
 * <p>For now, the recoverable keychain only supports a single layer of protection, which is the
 * user's lock screen. In the future, the keychain will support multiple layers of protection
 * (e.g. an additional keychain password, along with the lock screen).
 *
 * @hide
 */
@SystemApi
public final class KeyChainProtectionParams implements Parcelable {
    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"TYPE_"}, value = {TYPE_LOCKSCREEN, TYPE_CUSTOM_PASSWORD})
    public @interface UserSecretType {
    }

    /**
     * Lockscreen secret is required to recover KeyStore.
     */
    public static final int TYPE_LOCKSCREEN = 100;

    /**
     * Custom passphrase, unrelated to lock screen, is required to recover KeyStore.
     */
    public static final int TYPE_CUSTOM_PASSWORD = 101;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"UI_FORMAT_"}, value = {UI_FORMAT_PIN, UI_FORMAT_PASSWORD, UI_FORMAT_PATTERN})
    public @interface LockScreenUiFormat {
    }

    /**
     * Pin with digits only.
     */
    public static final int UI_FORMAT_PIN = 1;

    /**
     * Password. String with latin-1 characters only.
     */
    public static final int UI_FORMAT_PASSWORD = 2;

    /**
     * Pattern with 3 by 3 grid.
     */
    public static final int UI_FORMAT_PATTERN = 3;

    @UserSecretType
    private Integer mUserSecretType;

    @LockScreenUiFormat
    private Integer mLockScreenUiFormat;

    /**
     * Parameters of the key derivation function, including algorithm, difficulty, salt.
     */
    private KeyDerivationParams mKeyDerivationParams;
    private byte[] mSecret; // Derived from user secret. The field must have limited visibility.

    /**
     * @param secret Constructor creates a reference to the secret. Caller must use
     * @link {#clearSecret} to overwrite its value in memory.
     * @hide
     */
    public KeyChainProtectionParams(@UserSecretType int userSecretType,
            @LockScreenUiFormat int lockScreenUiFormat,
            @NonNull KeyDerivationParams keyDerivationParams,
            @NonNull byte[] secret) {
        mUserSecretType = userSecretType;
        mLockScreenUiFormat = lockScreenUiFormat;
        mKeyDerivationParams = Preconditions.checkNotNull(keyDerivationParams);
        mSecret = Preconditions.checkNotNull(secret);
    }

    private KeyChainProtectionParams() {

    }

    /**
     * @see TYPE_LOCKSCREEN
     * @see TYPE_CUSTOM_PASSWORD
     */
    public @UserSecretType int getUserSecretType() {
        return mUserSecretType;
    }

    /**
     * Specifies UX shown to user during recovery.
     * Default value is {@code UI_FORMAT_LOCKSCREEN}
     *
     * @see UI_FORMAT_PIN
     * @see UI_FORMAT_PASSWORD
     * @see UI_FORMAT_PATTERN
     */
    public @LockScreenUiFormat int getLockScreenUiFormat() {
        return mLockScreenUiFormat;
    }

    /**
     * Specifies function used to derive symmetric key from user input
     * Format is defined in separate util class.
     */
    public @NonNull KeyDerivationParams getKeyDerivationParams() {
        return mKeyDerivationParams;
    }

    /**
     * Secret derived from user input.
     * Default value is empty array
     *
     * @return secret or empty array
     */
    public @NonNull byte[] getSecret() {
        return mSecret;
    }

    /**
     * Builder for creating {@link KeyChainProtectionParams}.
     */
    public static class Builder {
        private KeyChainProtectionParams mInstance = new KeyChainProtectionParams();

        /**
         * Sets user secret type.
         *
         * @see TYPE_LOCKSCREEN
         * @see TYPE_CUSTOM_PASSWORD
         * @param userSecretType The secret type
         * @return This builder.
         */
        public Builder setUserSecretType(@UserSecretType int userSecretType) {
            mInstance.mUserSecretType = userSecretType;
            return this;
        }

        /**
         * Sets UI format.
         *
         * @see UI_FORMAT_PIN
         * @see UI_FORMAT_PASSWORD
         * @see UI_FORMAT_PATTERN
         * @param lockScreenUiFormat The UI format
         * @return This builder.
         */
        public Builder setLockScreenUiFormat(@LockScreenUiFormat int lockScreenUiFormat) {
            mInstance.mLockScreenUiFormat = lockScreenUiFormat;
            return this;
        }

        /**
         * Sets parameters of the key derivation function.
         *
         * @param keyDerivationParams Key derivation Params
         * @return This builder.
         */
        public Builder setKeyDerivationParams(@NonNull KeyDerivationParams
                keyDerivationParams) {
            mInstance.mKeyDerivationParams = keyDerivationParams;
            return this;
        }

        /**
         * Secret derived from user input, or empty array.
         *
         * @param secret The secret.
         * @return This builder.
         */
        public Builder setSecret(@NonNull byte[] secret) {
            mInstance.mSecret = secret;
            return this;
        }


        /**
         * Creates a new {@link KeyChainProtectionParams} instance.
         * The instance will include default values, if {@link setSecret}
         * or {@link setUserSecretType} were not called.
         *
         * @return new instance
         * @throws NullPointerException if some required fields were not set.
         */
        @NonNull public KeyChainProtectionParams build() {
            if (mInstance.mUserSecretType == null) {
                mInstance.mUserSecretType = TYPE_LOCKSCREEN;
            }
            Preconditions.checkNotNull(mInstance.mLockScreenUiFormat);
            Preconditions.checkNotNull(mInstance.mKeyDerivationParams);
            if (mInstance.mSecret == null) {
                mInstance.mSecret = new byte[]{};
            }
            return mInstance;
        }
    }

    /**
     * Fills secret with zeroes.
     */
    public void clearSecret() {
        Arrays.fill(mSecret, (byte) 0);
    }

    public static final Parcelable.Creator<KeyChainProtectionParams> CREATOR =
            new Parcelable.Creator<KeyChainProtectionParams>() {
        public KeyChainProtectionParams createFromParcel(Parcel in) {
            return new KeyChainProtectionParams(in);
        }

        public KeyChainProtectionParams[] newArray(int length) {
            return new KeyChainProtectionParams[length];
        }
    };

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mUserSecretType);
        out.writeInt(mLockScreenUiFormat);
        out.writeTypedObject(mKeyDerivationParams, flags);
        out.writeByteArray(mSecret);
    }

    /**
     * @hide
     */
    protected KeyChainProtectionParams(Parcel in) {
        mUserSecretType = in.readInt();
        mLockScreenUiFormat = in.readInt();
        mKeyDerivationParams = in.readTypedObject(KeyDerivationParams.CREATOR);
        mSecret = in.createByteArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
