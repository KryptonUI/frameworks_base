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

package android.security.keystore;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

/**
 * @deprecated Use {@link android.security.keystore.recovery.WrappedApplicationKey}.
 * @hide
 */
public final class WrappedApplicationKey implements Parcelable {
    private String mAlias;
    // The only supported format is AES-256 symmetric key.
    private byte[] mEncryptedKeyMaterial;

    /**
     * Builder for creating {@link WrappedApplicationKey}.
     */
    public static class Builder {
        private WrappedApplicationKey mInstance = new WrappedApplicationKey();

        /**
         * Sets Application-specific alias of the key.
         *
         * @param alias The alias.
         * @return This builder.
         */
        public Builder setAlias(@NonNull String alias) {
            mInstance.mAlias = alias;
            return this;
        }

        /**
         * Sets key material encrypted by recovery key.
         *
         * @param encryptedKeyMaterial The key material
         * @return This builder
         */

        public Builder setEncryptedKeyMaterial(@NonNull byte[] encryptedKeyMaterial) {
            mInstance.mEncryptedKeyMaterial = encryptedKeyMaterial;
            return this;
        }

        /**
         * Creates a new {@link WrappedApplicationKey} instance.
         *
         * @return new instance
         * @throws NullPointerException if some required fields were not set.
         */
        @NonNull public WrappedApplicationKey build() {
            Preconditions.checkNotNull(mInstance.mAlias);
            Preconditions.checkNotNull(mInstance.mEncryptedKeyMaterial);
            return mInstance;
        }
    }

    private WrappedApplicationKey() {

    }

    /**
     * Deprecated - consider using Builder.
     * @hide
     */
    public WrappedApplicationKey(@NonNull String alias, @NonNull byte[] encryptedKeyMaterial) {
        mAlias = Preconditions.checkNotNull(alias);
        mEncryptedKeyMaterial = Preconditions.checkNotNull(encryptedKeyMaterial);
    }

    /**
     * Application-specific alias of the key.
     *
     * @see java.security.KeyStore.aliases
     */
    public @NonNull String getAlias() {
        return mAlias;
    }

    /** Key material encrypted by recovery key. */
    public @NonNull byte[] getEncryptedKeyMaterial() {
        return mEncryptedKeyMaterial;
    }

    public static final Parcelable.Creator<WrappedApplicationKey> CREATOR =
            new Parcelable.Creator<WrappedApplicationKey>() {
                public WrappedApplicationKey createFromParcel(Parcel in) {
                    return new WrappedApplicationKey(in);
                }

                public WrappedApplicationKey[] newArray(int length) {
                    return new WrappedApplicationKey[length];
                }
            };

    /**
     * @hide
     */
    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mAlias);
        out.writeByteArray(mEncryptedKeyMaterial);
    }

    /**
     * @hide
     */
    protected WrappedApplicationKey(Parcel in) {
        mAlias = in.readString();
        mEncryptedKeyMaterial = in.createByteArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
