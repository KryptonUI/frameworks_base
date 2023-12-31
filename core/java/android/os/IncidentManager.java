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

package android.os;

import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.annotation.TestApi;
import android.content.Context;
import android.provider.Settings;
import android.util.Slog;

/**
 * Class to take an incident report.
 *
 * @hide
 */
@SystemApi
@TestApi
@SystemService(Context.INCIDENT_SERVICE)
public class IncidentManager {
    private static final String TAG = "IncidentManager";

    private final Context mContext;

    private IIncidentManager mService;

    /**
     * @hide
     */
    public IncidentManager(Context context) {
        mContext = context;
    }

    /**
     * Take an incident report and put it in dropbox.
     */
    @RequiresPermission(allOf = {
            android.Manifest.permission.DUMP,
            android.Manifest.permission.PACKAGE_USAGE_STATS
    })
    public void reportIncident(IncidentReportArgs args) {
        reportIncidentInternal(args);
    }

    /**
     * Convenience method to trigger an incident report and put it in dropbox.
     * <p>
     * The fields that are reported will be looked up in the system setting named by
     * the settingName parameter.  The setting must match one of these patterns:
     *      The string "disabled": The report will not be taken.
     *      The string "all": The report will taken with all sections.
     *      The string "none": The report will taken with no sections, but with the header.
     *      A comma separated list of field numbers: The report will have these fields.
     * <p>
     * The header parameter will be added as a header for the incident report.  Fill in a
     * {@link android.util.proto.ProtoOutputStream ProtoOutputStream}, and then call the
     * {@link android.util.proto.ProtoOutputStream#bytes bytes()} method to retrieve
     * the encoded data for the header.
     */
    @RequiresPermission(allOf = {
            android.Manifest.permission.DUMP,
            android.Manifest.permission.PACKAGE_USAGE_STATS
    })
    public void reportIncident(String settingName, byte[] headerProto) {
        // Sections
        String setting = Settings.Global.getString(mContext.getContentResolver(), settingName);
        IncidentReportArgs args;
        try {
            args = IncidentReportArgs.parseSetting(setting);
        } catch (IllegalArgumentException ex) {
            Slog.w(TAG, "Bad value for incident report setting '" + settingName + "'", ex);
            return;
        }
        if (args == null) {
            Slog.i(TAG, String.format("Incident report requested but disabled with "
                    + "settings [name: %s, value: %s]", settingName, setting));
            return;
        }

        args.addHeader(headerProto);

        Slog.i(TAG, "Taking incident report: " + settingName);
        reportIncidentInternal(args);
    }

    private class IncidentdDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            synchronized (this) {
                mService = null;
            }
        }
    }

    private void reportIncidentInternal(IncidentReportArgs args) {
        try {
            final IIncidentManager service = getIIncidentManagerLocked();
            if (service == null) {
                Slog.e(TAG, "reportIncident can't find incident binder service");
                return;
            }
            service.reportIncident(args);
        } catch (RemoteException ex) {
            Slog.e(TAG, "reportIncident failed", ex);
        }
    }

    private IIncidentManager getIIncidentManagerLocked() throws RemoteException {
        if (mService != null) {
            return mService;
        }

        synchronized (this) {
            if (mService != null) {
                return mService;
            }
            mService = IIncidentManager.Stub.asInterface(
                ServiceManager.getService(Context.INCIDENT_SERVICE));
            if (mService != null) {
                mService.asBinder().linkToDeath(new IncidentdDeathRecipient(), 0);
            }
            return mService;
        }
    }

}

