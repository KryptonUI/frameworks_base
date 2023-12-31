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

package com.android.server.locksettings.recoverablekeystore;

import static android.security.keystore.recovery.RecoveryController.ERROR_BAD_CERTIFICATE_FORMAT;
import static android.security.keystore.recovery.RecoveryController.ERROR_DECRYPTION_FAILED;
import static android.security.keystore.recovery.RecoveryController.ERROR_INSECURE_USER;
import static android.security.keystore.recovery.RecoveryController.ERROR_INVALID_KEY_FORMAT;
import static android.security.keystore.recovery.RecoveryController.ERROR_INVALID_CERTIFICATE;
import static android.security.keystore.recovery.RecoveryController.ERROR_NO_SNAPSHOT_PENDING;
import static android.security.keystore.recovery.RecoveryController.ERROR_SERVICE_INTERNAL_ERROR;
import static android.security.keystore.recovery.RecoveryController.ERROR_SESSION_EXPIRED;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.UserHandle;
import android.security.keystore.recovery.KeyChainProtectionParams;
import android.security.keystore.recovery.KeyChainSnapshot;
import android.security.keystore.recovery.RecoveryCertPath;
import android.security.keystore.recovery.RecoveryController;
import android.security.keystore.recovery.WrappedApplicationKey;
import android.security.KeyStore;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.HexDump;
import com.android.server.locksettings.recoverablekeystore.certificate.CertUtils;
import com.android.server.locksettings.recoverablekeystore.certificate.SigXml;
import com.android.server.locksettings.recoverablekeystore.storage.ApplicationKeyStorage;
import com.android.server.locksettings.recoverablekeystore.certificate.CertParsingException;
import com.android.server.locksettings.recoverablekeystore.certificate.CertValidationException;
import com.android.server.locksettings.recoverablekeystore.certificate.CertXml;
import com.android.server.locksettings.recoverablekeystore.certificate.TrustedRootCert;
import com.android.server.locksettings.recoverablekeystore.storage.RecoverableKeyStoreDb;
import com.android.server.locksettings.recoverablekeystore.storage.RecoverySessionStorage;
import com.android.server.locksettings.recoverablekeystore.storage.RecoverySnapshotStorage;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertPath;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.AEADBadTagException;

/**
 * Class with {@link RecoveryController} API implementation and internal methods to interact
 * with {@code LockSettingsService}.
 *
 * @hide
 */
public class RecoverableKeyStoreManager {
    private static final String TAG = "RecoverableKeyStoreMgr";

    private static RecoverableKeyStoreManager mInstance;

    private final Context mContext;
    private final RecoverableKeyStoreDb mDatabase;
    private final RecoverySessionStorage mRecoverySessionStorage;
    private final ExecutorService mExecutorService;
    private final RecoverySnapshotListenersStorage mListenersStorage;
    private final RecoverableKeyGenerator mRecoverableKeyGenerator;
    private final RecoverySnapshotStorage mSnapshotStorage;
    private final PlatformKeyManager mPlatformKeyManager;
    private final KeyStore mKeyStore;
    private final ApplicationKeyStorage mApplicationKeyStorage;

    /**
     * Returns a new or existing instance.
     *
     * @hide
     */
    public static synchronized RecoverableKeyStoreManager
            getInstance(Context context, KeyStore keystore) {
        if (mInstance == null) {
            RecoverableKeyStoreDb db = RecoverableKeyStoreDb.newInstance(context);
            PlatformKeyManager platformKeyManager;
            ApplicationKeyStorage applicationKeyStorage;
            try {
                platformKeyManager = PlatformKeyManager.getInstance(context, db);
                applicationKeyStorage = ApplicationKeyStorage.getInstance(keystore);
            } catch (NoSuchAlgorithmException e) {
                // Impossible: all algorithms must be supported by AOSP
                throw new RuntimeException(e);
            } catch (KeyStoreException e) {
                throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
            }

            mInstance = new RecoverableKeyStoreManager(
                    context.getApplicationContext(),
                    keystore,
                    db,
                    new RecoverySessionStorage(),
                    Executors.newSingleThreadExecutor(),
                    new RecoverySnapshotStorage(),
                    new RecoverySnapshotListenersStorage(),
                    platformKeyManager,
                    applicationKeyStorage);
        }
        return mInstance;
    }

    @VisibleForTesting
    RecoverableKeyStoreManager(
            Context context,
            KeyStore keystore,
            RecoverableKeyStoreDb recoverableKeyStoreDb,
            RecoverySessionStorage recoverySessionStorage,
            ExecutorService executorService,
            RecoverySnapshotStorage snapshotStorage,
            RecoverySnapshotListenersStorage listenersStorage,
            PlatformKeyManager platformKeyManager,
            ApplicationKeyStorage applicationKeyStorage) {
        mContext = context;
        mKeyStore = keystore;
        mDatabase = recoverableKeyStoreDb;
        mRecoverySessionStorage = recoverySessionStorage;
        mExecutorService = executorService;
        mListenersStorage = listenersStorage;
        mSnapshotStorage = snapshotStorage;
        mPlatformKeyManager = platformKeyManager;
        mApplicationKeyStorage = applicationKeyStorage;

        try {
            mRecoverableKeyGenerator = RecoverableKeyGenerator.newInstance(mDatabase);
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "AES keygen algorithm not available. AOSP must support this.", e);
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * @deprecated Use {@link #initRecoveryServiceWithSigFile(String, byte[], byte[])} instead.
     */
    public void initRecoveryService(
            @NonNull String rootCertificateAlias, @NonNull byte[] recoveryServiceCertFile)
            throws RemoteException {
        checkRecoverKeyStorePermission();
        int userId = UserHandle.getCallingUserId();
        int uid = Binder.getCallingUid();

        CertXml certXml;
        try {
            certXml = CertXml.parse(recoveryServiceCertFile);
        } catch (CertParsingException e) {
            // TODO: Do not use raw key bytes anymore once the other components are updated
            Log.d(TAG, "Failed to parse the input as a cert file: " + HexDump.toHexString(
                    recoveryServiceCertFile));
            PublicKey publicKey = parseEcPublicKey(recoveryServiceCertFile);
            if (mDatabase.setRecoveryServicePublicKey(userId, uid, publicKey) > 0) {
                mDatabase.setShouldCreateSnapshot(userId, uid, true);
            }
            Log.d(TAG, "Successfully set the input as the raw public key");
            return;
        }

        // Check serial number
        long newSerial = certXml.getSerial();
        Long oldSerial = mDatabase.getRecoveryServiceCertSerial(userId, uid);
        if (oldSerial != null && oldSerial >= newSerial) {
            if (oldSerial == newSerial) {
                Log.i(TAG, "The cert file serial number is the same, so skip updating.");
            } else {
                Log.e(TAG, "The cert file serial number is older than the one in database.");
            }
            return;
        }
        Log.i(TAG, "Updating the certificate with the new serial number " + newSerial);

        CertPath certPath;
        try {
            Log.d(TAG, "Getting and validating a random endpoint certificate");
            certPath = certXml.getRandomEndpointCert(TrustedRootCert.TRUSTED_ROOT_CERT);
        } catch (CertValidationException e) {
            Log.e(TAG, "Invalid endpoint cert", e);
            throw new ServiceSpecificException(
                    ERROR_INVALID_CERTIFICATE, "Failed to validate certificate.");
        }
        try {
            Log.d(TAG, "Saving the randomly chosen endpoint certificate to database");
            if (mDatabase.setRecoveryServiceCertPath(userId, uid, certPath) > 0) {
                mDatabase.setRecoveryServiceCertSerial(userId, uid, newSerial);
                mDatabase.setShouldCreateSnapshot(userId, uid, true);
            }
        } catch (CertificateEncodingException e) {
            Log.e(TAG, "Failed to encode CertPath", e);
            throw new ServiceSpecificException(
                    ERROR_BAD_CERTIFICATE_FORMAT, "Failed to encode CertPath.");
        }
    }

    /**
     * Initializes the recovery service with the two files {@code recoveryServiceCertFile} and
     * {@code recoveryServiceSigFile}.
     *
     * @param rootCertificateAlias the alias for the root certificate that is used for validating
     *     the recovery service certificates.
     * @param recoveryServiceCertFile the content of the XML file containing a list of certificates
     *     for the recovery service.
     * @param recoveryServiceSigFile the content of the XML file containing the public-key signature
     *     over the entire content of {@code recoveryServiceCertFile}.
     */
    public void initRecoveryServiceWithSigFile(
            @NonNull String rootCertificateAlias, @NonNull byte[] recoveryServiceCertFile,
            @NonNull byte[] recoveryServiceSigFile)
            throws RemoteException {
        if (recoveryServiceCertFile == null || recoveryServiceSigFile == null) {
            Log.d(TAG, "The given cert or sig file is null");
            throw new ServiceSpecificException(
                    ERROR_BAD_CERTIFICATE_FORMAT, "The given cert or sig file is null.");
        }

        SigXml sigXml;
        try {
            sigXml = SigXml.parse(recoveryServiceSigFile);
        } catch (CertParsingException e) {
            Log.d(TAG, "Failed to parse the sig file: " + HexDump.toHexString(
                    recoveryServiceSigFile));
            throw new ServiceSpecificException(
                    ERROR_BAD_CERTIFICATE_FORMAT, "Failed to parse the sig file.");
        }

        try {
            sigXml.verifyFileSignature(TrustedRootCert.TRUSTED_ROOT_CERT, recoveryServiceCertFile);
        } catch (CertValidationException e) {
            Log.d(TAG, "The signature over the cert file is invalid."
                    + " Cert: " + HexDump.toHexString(recoveryServiceCertFile)
                    + " Sig: " + HexDump.toHexString(recoveryServiceSigFile));
            throw new ServiceSpecificException(
                    ERROR_INVALID_CERTIFICATE, "The signature over the cert file is invalid.");
        }

        initRecoveryService(rootCertificateAlias, recoveryServiceCertFile);
    }

    private PublicKey parseEcPublicKey(@NonNull byte[] bytes) throws ServiceSpecificException {
        try {
            KeyFactory kf = KeyFactory.getInstance("EC");
            X509EncodedKeySpec pkSpec = new X509EncodedKeySpec(bytes);
            return kf.generatePublic(pkSpec);
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "EC algorithm not available. AOSP must support this.", e);
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        } catch (InvalidKeySpecException e) {
            throw new ServiceSpecificException(
                    ERROR_BAD_CERTIFICATE_FORMAT, "Not a valid X509 certificate.");
        }
    }

    /**
     * Gets all data necessary to recover application keys on new device.
     *
     * @return recovery data
     * @hide
     */
    public @NonNull
    KeyChainSnapshot getKeyChainSnapshot()
            throws RemoteException {
        checkRecoverKeyStorePermission();
        int uid = Binder.getCallingUid();
        KeyChainSnapshot snapshot = mSnapshotStorage.get(uid);
        if (snapshot == null) {
            throw new ServiceSpecificException(ERROR_NO_SNAPSHOT_PENDING);
        }
        return snapshot;
    }

    public void setSnapshotCreatedPendingIntent(@Nullable PendingIntent intent)
            throws RemoteException {
        checkRecoverKeyStorePermission();
        int uid = Binder.getCallingUid();
        mListenersStorage.setSnapshotListener(uid, intent);
    }

    /**
     * Gets recovery snapshot versions for all accounts. Note that snapshot may have 0 application
     * keys, but it still needs to be synced, if previous versions were not empty.
     *
     * @return Map from Recovery agent account to snapshot version.
     */
    public @NonNull Map<byte[], Integer> getRecoverySnapshotVersions()
            throws RemoteException {
        checkRecoverKeyStorePermission();
        throw new UnsupportedOperationException();
    }

    public void setServerParams(byte[] serverParams) throws RemoteException {
        checkRecoverKeyStorePermission();
        int userId = UserHandle.getCallingUserId();
        int uid = Binder.getCallingUid();
        long updatedRows = mDatabase.setServerParams(userId, uid, serverParams);
        if (updatedRows > 0) {
            mDatabase.setShouldCreateSnapshot(userId, uid, true);
        }
    }

    /**
     * Sets the recovery status of key with {@code alias} to {@code status}.
     */
    public void setRecoveryStatus(String alias, int status) throws RemoteException {
        checkRecoverKeyStorePermission();
        mDatabase.setRecoveryStatus(Binder.getCallingUid(), alias, status);
    }

    /**
     * Returns recovery statuses for all keys belonging to the calling uid.
     *
     * @return {@link Map} from key alias to recovery status. Recovery status is one of
     *     {@link RecoveryController#RECOVERY_STATUS_SYNCED},
     *     {@link RecoveryController#RECOVERY_STATUS_SYNC_IN_PROGRESS} or
     *     {@link RecoveryController#RECOVERY_STATUS_PERMANENT_FAILURE}.
     */
    public @NonNull Map<String, Integer> getRecoveryStatus() throws RemoteException {
        return mDatabase.getStatusForAllKeys(Binder.getCallingUid());
    }

    /**
     * Sets recovery secrets list used by all recovery agents for given {@code userId}
     *
     * @hide
     */
    public void setRecoverySecretTypes(
            @NonNull @KeyChainProtectionParams.UserSecretType int[] secretTypes)
            throws RemoteException {
        checkRecoverKeyStorePermission();
        int userId = UserHandle.getCallingUserId();
        int uid = Binder.getCallingUid();
        long updatedRows = mDatabase.setRecoverySecretTypes(userId, uid, secretTypes);
        if (updatedRows > 0) {
            mDatabase.setShouldCreateSnapshot(userId, uid, true);
        }
    }

    /**
     * Gets secret types necessary to create Recovery Data.
     *
     * @return secret types
     * @hide
     */
    public @NonNull int[] getRecoverySecretTypes() throws RemoteException {
        checkRecoverKeyStorePermission();
        return mDatabase.getRecoverySecretTypes(UserHandle.getCallingUserId(),
            Binder.getCallingUid());
    }

    /**
     * Gets secret types RecoveryManagers is waiting for to create new Recovery Data.
     *
     * @return secret types
     * @hide
     */
    public @NonNull int[] getPendingRecoverySecretTypes() throws RemoteException {
        checkRecoverKeyStorePermission();
        throw new UnsupportedOperationException();
    }

    public void recoverySecretAvailable(
            @NonNull KeyChainProtectionParams recoverySecret) throws RemoteException {
        int uid = Binder.getCallingUid();
        if (recoverySecret.getLockScreenUiFormat() == KeyChainProtectionParams.TYPE_LOCKSCREEN) {
            throw new SecurityException(
                    "Caller " + uid + " is not allowed to set lock screen secret");
        }
        checkRecoverKeyStorePermission();
        // TODO: add hook from LockSettingsService to set lock screen secret.
        throw new UnsupportedOperationException();
    }

    /**
     * Initializes recovery session given the X509-encoded public key of the recovery service.
     *
     * @param sessionId A unique ID to identify the recovery session.
     * @param verifierPublicKey X509-encoded public key.
     * @param vaultParams Additional params associated with vault.
     * @param vaultChallenge Challenge issued by vault service.
     * @param secrets Lock-screen hashes. For now only a single secret is supported.
     * @return Encrypted bytes of recovery claim. This can then be issued to the vault service.
     * @deprecated Use {@link #startRecoverySessionWithCertPath(String, RecoveryCertPath, byte[],
     *         byte[], List)} instead.
     *
     * @hide
     */
    public @NonNull byte[] startRecoverySession(
            @NonNull String sessionId,
            @NonNull byte[] verifierPublicKey,
            @NonNull byte[] vaultParams,
            @NonNull byte[] vaultChallenge,
            @NonNull List<KeyChainProtectionParams> secrets)
            throws RemoteException {
        checkRecoverKeyStorePermission();
        int uid = Binder.getCallingUid();

        if (secrets.size() != 1) {
            throw new UnsupportedOperationException(
                    "Only a single KeyChainProtectionParams is supported");
        }

        PublicKey publicKey;
        try {
            publicKey = KeySyncUtils.deserializePublicKey(verifierPublicKey);
        } catch (InvalidKeySpecException e) {
            throw new ServiceSpecificException(ERROR_BAD_CERTIFICATE_FORMAT,
                    "Not a valid X509 key");
        }
        // The raw public key bytes contained in vaultParams must match the ones given in
        // verifierPublicKey; otherwise, the user secret may be decrypted by a key that is not owned
        // by the original recovery service.
        if (!publicKeysMatch(publicKey, vaultParams)) {
            throw new ServiceSpecificException(ERROR_INVALID_CERTIFICATE,
                    "The public keys given in verifierPublicKey and vaultParams do not match.");
        }

        byte[] keyClaimant = KeySyncUtils.generateKeyClaimant();
        byte[] kfHash = secrets.get(0).getSecret();
        mRecoverySessionStorage.add(
                uid,
                new RecoverySessionStorage.Entry(sessionId, kfHash, keyClaimant, vaultParams));

        try {
            byte[] thmKfHash = KeySyncUtils.calculateThmKfHash(kfHash);
            return KeySyncUtils.encryptRecoveryClaim(
                    publicKey,
                    vaultParams,
                    vaultChallenge,
                    thmKfHash,
                    keyClaimant);
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "SecureBox algorithm missing. AOSP must support this.", e);
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        } catch (InvalidKeyException e) {
            throw new ServiceSpecificException(ERROR_BAD_CERTIFICATE_FORMAT, e.getMessage());
        }
    }

    /**
     * Initializes recovery session given the certificate path of the recovery service.
     *
     * @param sessionId A unique ID to identify the recovery session.
     * @param verifierCertPath The certificate path of the recovery service.
     * @param vaultParams Additional params associated with vault.
     * @param vaultChallenge Challenge issued by vault service.
     * @param secrets Lock-screen hashes. For now only a single secret is supported.
     * @return Encrypted bytes of recovery claim. This can then be issued to the vault service.
     *
     * @hide
     */
    public @NonNull byte[] startRecoverySessionWithCertPath(
            @NonNull String sessionId,
            @NonNull RecoveryCertPath verifierCertPath,
            @NonNull byte[] vaultParams,
            @NonNull byte[] vaultChallenge,
            @NonNull List<KeyChainProtectionParams> secrets)
            throws RemoteException {
        checkRecoverKeyStorePermission();

        CertPath certPath;
        try {
            certPath = verifierCertPath.getCertPath();
        } catch (CertificateException e) {
            throw new ServiceSpecificException(ERROR_BAD_CERTIFICATE_FORMAT,
                    "Failed decode the certificate path");
        }

        try {
            CertUtils.validateCertPath(TrustedRootCert.TRUSTED_ROOT_CERT, certPath);
        } catch (CertValidationException e) {
            Log.e(TAG, "Failed to validate the given cert path", e);
            // TODO: Change this to ERROR_INVALID_CERTIFICATE once ag/3666620 is submitted
            throw new ServiceSpecificException(ERROR_BAD_CERTIFICATE_FORMAT, e.getMessage());
        }

        byte[] verifierPublicKey = certPath.getCertificates().get(0).getPublicKey().getEncoded();
        if (verifierPublicKey == null) {
            Log.e(TAG, "Failed to encode verifierPublicKey");
            throw new ServiceSpecificException(ERROR_BAD_CERTIFICATE_FORMAT,
                    "Failed to encode verifierPublicKey");
        }

        return startRecoverySession(
                sessionId, verifierPublicKey, vaultParams, vaultChallenge, secrets);
    }

    /**
     * Invoked by a recovery agent after a successful recovery claim is sent to the remote vault
     * service.
     *
     * @param sessionId The session ID used to generate the claim. See
     *     {@link #startRecoverySession(String, byte[], byte[], byte[], List)}.
     * @param encryptedRecoveryKey The encrypted recovery key blob returned by the remote vault
     *     service.
     * @param applicationKeys The encrypted key blobs returned by the remote vault service. These
     *     were wrapped with the recovery key.
     * @return Map from alias to raw key material.
     * @throws RemoteException if an error occurred recovering the keys.
     */
    public Map<String, byte[]> recoverKeys(
            @NonNull String sessionId,
            @NonNull byte[] encryptedRecoveryKey,
            @NonNull List<WrappedApplicationKey> applicationKeys)
            throws RemoteException {
        checkRecoverKeyStorePermission();
        int uid = Binder.getCallingUid();
        RecoverySessionStorage.Entry sessionEntry = mRecoverySessionStorage.get(uid, sessionId);
        if (sessionEntry == null) {
            throw new ServiceSpecificException(ERROR_SESSION_EXPIRED,
                    String.format(Locale.US,
                    "Application uid=%d does not have pending session '%s'", uid, sessionId));
        }

        try {
            byte[] recoveryKey = decryptRecoveryKey(sessionEntry, encryptedRecoveryKey);
            return recoverApplicationKeys(recoveryKey, applicationKeys);
        } finally {
            sessionEntry.destroy();
            mRecoverySessionStorage.remove(uid);
        }
    }

    /**
     * Deprecated
     * Generates a key named {@code alias} in the recoverable store for the calling uid. Then
     * returns the raw key material.
     *
     * <p>TODO: Once AndroidKeyStore has added move api, do not return raw bytes.
     *
     * @deprecated
     * @hide
     */
    public byte[] generateAndStoreKey(@NonNull String alias) throws RemoteException {
        int uid = Binder.getCallingUid();
        int userId = UserHandle.getCallingUserId();

        PlatformEncryptionKey encryptionKey;
        try {
            encryptionKey = mPlatformKeyManager.getEncryptKey(userId);
        } catch (NoSuchAlgorithmException e) {
            // Impossible: all algorithms must be supported by AOSP
            throw new RuntimeException(e);
        } catch (KeyStoreException | UnrecoverableKeyException e) {
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        } catch (InsecureUserException e) {
            throw new ServiceSpecificException(ERROR_INSECURE_USER, e.getMessage());
        }

        try {
            return mRecoverableKeyGenerator.generateAndStoreKey(encryptionKey, userId, uid, alias);
        } catch (KeyStoreException | InvalidKeyException | RecoverableKeyStorageException e) {
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * Destroys the session with the given {@code sessionId}.
     */
    public void closeSession(@NonNull String sessionId) throws RemoteException {
        mRecoverySessionStorage.remove(Binder.getCallingUid(), sessionId);
    }

    public void removeKey(@NonNull String alias) throws RemoteException {
        int uid = Binder.getCallingUid();
        int userId = UserHandle.getCallingUserId();

        boolean wasRemoved = mDatabase.removeKey(uid, alias);
        if (wasRemoved) {
            mDatabase.setShouldCreateSnapshot(userId, uid, true);
            mApplicationKeyStorage.deleteEntry(userId, uid, alias);
        }
    }

    /**
     * Generates a key named {@code alias} in caller's namespace.
     * The key is stored in system service keystore namespace.
     *
     * @return grant alias, which caller can use to access the key.
     */
    public String generateKey(@NonNull String alias) throws RemoteException {
        int uid = Binder.getCallingUid();
        int userId = UserHandle.getCallingUserId();

        PlatformEncryptionKey encryptionKey;
        try {
            encryptionKey = mPlatformKeyManager.getEncryptKey(userId);
        } catch (NoSuchAlgorithmException e) {
            // Impossible: all algorithms must be supported by AOSP
            throw new RuntimeException(e);
        } catch (KeyStoreException | UnrecoverableKeyException e) {
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        } catch (InsecureUserException e) {
            throw new ServiceSpecificException(ERROR_INSECURE_USER, e.getMessage());
        }

        try {
            byte[] secretKey =
                    mRecoverableKeyGenerator.generateAndStoreKey(encryptionKey, userId, uid, alias);
            mApplicationKeyStorage.setSymmetricKeyEntry(userId, uid, alias, secretKey);
            return mApplicationKeyStorage.getGrantAlias(userId, uid, alias);
        } catch (KeyStoreException | InvalidKeyException | RecoverableKeyStorageException e) {
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * Imports a 256-bit AES-GCM key named {@code alias}. The key is stored in system service
     * keystore namespace.
     *
     * @param alias the alias provided by caller as a reference to the key.
     * @param keyBytes the raw bytes of the 256-bit AES key.
     * @return grant alias, which caller can use to access the key.
     * @throws RemoteException if the given key is invalid or some internal errors occur.
     *
     * @hide
     */
    public String importKey(@NonNull String alias, @NonNull byte[] keyBytes)
            throws RemoteException {
        if (keyBytes == null ||
                keyBytes.length != RecoverableKeyGenerator.KEY_SIZE_BITS / Byte.SIZE) {
            Log.e(TAG, "The given key for import doesn't have the required length "
                    + RecoverableKeyGenerator.KEY_SIZE_BITS);
            throw new ServiceSpecificException(ERROR_INVALID_KEY_FORMAT,
                    "The given key does not contain " + RecoverableKeyGenerator.KEY_SIZE_BITS
                            + " bits.");
        }

        int uid = Binder.getCallingUid();
        int userId = UserHandle.getCallingUserId();

        // TODO: Refactor RecoverableKeyGenerator to wrap the PlatformKey logic

        PlatformEncryptionKey encryptionKey;
        try {
            encryptionKey = mPlatformKeyManager.getEncryptKey(userId);
        } catch (NoSuchAlgorithmException e) {
            // Impossible: all algorithms must be supported by AOSP
            throw new RuntimeException(e);
        } catch (KeyStoreException | UnrecoverableKeyException e) {
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        } catch (InsecureUserException e) {
            throw new ServiceSpecificException(ERROR_INSECURE_USER, e.getMessage());
        }

        try {
            // Wrap the key by the platform key and store the wrapped key locally
            mRecoverableKeyGenerator.importKey(encryptionKey, userId, uid, alias, keyBytes);

            // Import the key to Android KeyStore and get grant
            mApplicationKeyStorage.setSymmetricKeyEntry(userId, uid, alias, keyBytes);
            return mApplicationKeyStorage.getGrantAlias(userId, uid, alias);
        } catch (KeyStoreException | InvalidKeyException | RecoverableKeyStorageException e) {
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * Gets a key named {@code alias} in caller's namespace.
     *
     * @return grant alias, which caller can use to access the key.
     */
    public String getKey(@NonNull String alias) throws RemoteException {
        int uid = Binder.getCallingUid();
        int userId = UserHandle.getCallingUserId();
        String grantAlias = mApplicationKeyStorage.getGrantAlias(userId, uid, alias);
        return grantAlias;
    }

    private byte[] decryptRecoveryKey(
            RecoverySessionStorage.Entry sessionEntry, byte[] encryptedClaimResponse)
            throws RemoteException, ServiceSpecificException {
        byte[] locallyEncryptedKey;
        try {
            locallyEncryptedKey = KeySyncUtils.decryptRecoveryClaimResponse(
                    sessionEntry.getKeyClaimant(),
                    sessionEntry.getVaultParams(),
                    encryptedClaimResponse);
        } catch (InvalidKeyException e) {
            Log.e(TAG, "Got InvalidKeyException during decrypting recovery claim response", e);
            throw new ServiceSpecificException(ERROR_DECRYPTION_FAILED,
                    "Failed to decrypt recovery key " + e.getMessage());
        } catch (AEADBadTagException e) {
            Log.e(TAG, "Got AEADBadTagException during decrypting recovery claim response", e);
            throw new ServiceSpecificException(ERROR_DECRYPTION_FAILED,
                    "Failed to decrypt recovery key " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            // Should never happen: all the algorithms used are required by AOSP implementations
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        }

        try {
            return KeySyncUtils.decryptRecoveryKey(sessionEntry.getLskfHash(), locallyEncryptedKey);
        } catch (InvalidKeyException e) {
            Log.e(TAG, "Got InvalidKeyException during decrypting recovery key", e);
            throw new ServiceSpecificException(ERROR_DECRYPTION_FAILED,
                    "Failed to decrypt recovery key " + e.getMessage());
        } catch (AEADBadTagException e) {
            Log.e(TAG, "Got AEADBadTagException during decrypting recovery key", e);
            throw new ServiceSpecificException(ERROR_DECRYPTION_FAILED,
                    "Failed to decrypt recovery key " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            // Should never happen: all the algorithms used are required by AOSP implementations
            throw new ServiceSpecificException(ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
        }
    }

    /**
     * Uses {@code recoveryKey} to decrypt {@code applicationKeys}.
     *
     * @return Map from alias to raw key material.
     * @throws RemoteException if an error occurred decrypting the keys.
     */
    private Map<String, byte[]> recoverApplicationKeys(
            @NonNull byte[] recoveryKey,
            @NonNull List<WrappedApplicationKey> applicationKeys) throws RemoteException {
        HashMap<String, byte[]> keyMaterialByAlias = new HashMap<>();
        for (WrappedApplicationKey applicationKey : applicationKeys) {
            String alias = applicationKey.getAlias();
            byte[] encryptedKeyMaterial = applicationKey.getEncryptedKeyMaterial();

            try {
                byte[] keyMaterial =
                        KeySyncUtils.decryptApplicationKey(recoveryKey, encryptedKeyMaterial);
                keyMaterialByAlias.put(alias, keyMaterial);
            } catch (NoSuchAlgorithmException e) {
                Log.wtf(TAG, "Missing SecureBox algorithm. AOSP required to support this.", e);
                throw new ServiceSpecificException(
                        ERROR_SERVICE_INTERNAL_ERROR, e.getMessage());
            } catch (InvalidKeyException e) {
                Log.e(TAG, "Got InvalidKeyException during decrypting application key with alias: "
                        + alias, e);
                throw new ServiceSpecificException(ERROR_DECRYPTION_FAILED,
                        "Failed to recover key with alias '" + alias + "': " + e.getMessage());
            } catch (AEADBadTagException e) {
                Log.e(TAG, "Got AEADBadTagException during decrypting application key with alias: "
                        + alias, e);
                // Ignore the exception to continue to recover the other application keys.
            }
        }
        if (!applicationKeys.isEmpty() && keyMaterialByAlias.isEmpty()) {
            Log.e(TAG, "Failed to recover any of the application keys.");
            throw new ServiceSpecificException(ERROR_DECRYPTION_FAILED,
                    "Failed to recover any of the application keys.");
        }
        return keyMaterialByAlias;
    }

    /**
     * This function can only be used inside LockSettingsService.
     *
     * @param storedHashType from {@code CredentialHash}
     * @param credential - unencrypted String. Password length should be at most 16 symbols {@code
     *     mPasswordMaxLength}
     * @param userId for user who just unlocked the device.
     * @hide
     */
    public void lockScreenSecretAvailable(
            int storedHashType, @NonNull String credential, int userId) {
        // So as not to block the critical path unlocking the phone, defer to another thread.
        try {
            mExecutorService.execute(KeySyncTask.newInstance(
                    mContext,
                    mDatabase,
                    mSnapshotStorage,
                    mListenersStorage,
                    userId,
                    storedHashType,
                    credential,
                    /*credentialUpdated=*/ false));
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "Should never happen - algorithm unavailable for KeySync", e);
        } catch (KeyStoreException e) {
            Log.e(TAG, "Key store error encountered during recoverable key sync", e);
        } catch (InsecureUserException e) {
            Log.wtf(TAG, "Impossible - insecure user, but user just entered lock screen", e);
        }
    }

    /**
     * This function can only be used inside LockSettingsService.
     *
     * @param storedHashType from {@code CredentialHash}
     * @param credential - unencrypted String
     * @param userId for the user whose lock screen credentials were changed.
     * @hide
     */
    public void lockScreenSecretChanged(
            int storedHashType,
            @Nullable String credential,
            int userId) {
        // So as not to block the critical path unlocking the phone, defer to another thread.
        try {
            mExecutorService.execute(KeySyncTask.newInstance(
                    mContext,
                    mDatabase,
                    mSnapshotStorage,
                    mListenersStorage,
                    userId,
                    storedHashType,
                    credential,
                    /*credentialUpdated=*/ true));
        } catch (NoSuchAlgorithmException e) {
            Log.wtf(TAG, "Should never happen - algorithm unavailable for KeySync", e);
        } catch (KeyStoreException e) {
            Log.e(TAG, "Key store error encountered during recoverable key sync", e);
        } catch (InsecureUserException e) {
            Log.e(TAG, "InsecureUserException during lock screen secret update", e);
        }
    }

    private void checkRecoverKeyStorePermission() {
        mContext.enforceCallingOrSelfPermission(
                Manifest.permission.RECOVER_KEYSTORE,
                "Caller " + Binder.getCallingUid() + " doesn't have RecoverKeyStore permission.");
    }

    private boolean publicKeysMatch(PublicKey publicKey, byte[] vaultParams) {
        byte[] encodedPublicKey = SecureBox.encodePublicKey(publicKey);
        return Arrays.equals(encodedPublicKey, Arrays.copyOf(vaultParams, encodedPublicKey.length));
    }
}
