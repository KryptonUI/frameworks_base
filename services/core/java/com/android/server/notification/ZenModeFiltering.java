/**
 * Copyright (c) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.notification;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings.Global;
import android.provider.Settings.Secure;
import android.service.notification.ZenModeConfig;
import android.telecom.TelecomManager;
import android.util.ArrayMap;
import android.util.Slog;

import java.io.PrintWriter;
import java.util.Date;
import java.util.Objects;

public class ZenModeFiltering {
    private static final String TAG = ZenModeHelper.TAG;
    private static final boolean DEBUG = ZenModeHelper.DEBUG;

    static final RepeatCallers REPEAT_CALLERS = new RepeatCallers();

    private final Context mContext;

    private ComponentName mDefaultPhoneApp;

    public ZenModeFiltering(Context context) {
        mContext = context;
    }

    public void dump(PrintWriter pw, String prefix) {
        pw.print(prefix); pw.print("mDefaultPhoneApp="); pw.println(mDefaultPhoneApp);
        pw.print(prefix); pw.print("RepeatCallers.mThresholdMinutes=");
        pw.println(REPEAT_CALLERS.mThresholdMinutes);
        synchronized (REPEAT_CALLERS) {
            if (!REPEAT_CALLERS.mCalls.isEmpty()) {
                pw.print(prefix); pw.println("RepeatCallers.mCalls=");
                for (int i = 0; i < REPEAT_CALLERS.mCalls.size(); i++) {
                    pw.print(prefix); pw.print("  ");
                    pw.print(REPEAT_CALLERS.mCalls.keyAt(i));
                    pw.print(" at ");
                    pw.println(ts(REPEAT_CALLERS.mCalls.valueAt(i)));
                }
            }
        }
    }

    private static String ts(long time) {
        return new Date(time) + " (" + time + ")";
    }

    /**
     * @param extras extras of the notification with EXTRA_PEOPLE populated
     * @param contactsTimeoutMs timeout in milliseconds to wait for contacts response
     * @param timeoutAffinity affinity to return when the timeout specified via
     *                        <code>contactsTimeoutMs</code> is hit
     */
    public static boolean matchesCallFilter(Context context, int zen, ZenModeConfig config,
            UserHandle userHandle, Bundle extras, ValidateNotificationPeople validator,
            int contactsTimeoutMs, float timeoutAffinity) {
        if (zen == Global.ZEN_MODE_NO_INTERRUPTIONS) return false; // nothing gets through
        if (zen == Global.ZEN_MODE_ALARMS) return false; // not an alarm
        if (zen == Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS) {
            if (config.allowRepeatCallers && REPEAT_CALLERS.isRepeat(context, extras)) {
                return true;
            }
            if (!config.allowCalls) return false; // no other calls get through
            if (validator != null) {
                final float contactAffinity = validator.getContactAffinity(userHandle, extras,
                        contactsTimeoutMs, timeoutAffinity);
                return audienceMatches(config.allowCallsFrom, contactAffinity);
            }
        }
        return true;
    }

    private static Bundle extras(NotificationRecord record) {
        return record != null && record.sbn != null && record.sbn.getNotification() != null
                ? record.sbn.getNotification().extras : null;
    }

    protected void recordCall(NotificationRecord record) {
        REPEAT_CALLERS.recordCall(mContext, extras(record));
    }

    public boolean shouldIntercept(int zen, ZenModeConfig config, NotificationRecord record) {
        switch (zen) {
            case Global.ZEN_MODE_NO_INTERRUPTIONS:
                // #notevenalarms
                ZenLog.traceIntercepted(record, "none");
                return true;
            case Global.ZEN_MODE_ALARMS:
                if (isAlarm(record)) {
                    // Alarms only
                    return false;
                }
                ZenLog.traceIntercepted(record, "alarmsOnly");
                return true;
            case Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS:
                // allow user-prioritized packages through in priority mode
                if (record.getPackagePriority() == Notification.PRIORITY_MAX) {
                    ZenLog.traceNotIntercepted(record, "priorityApp");
                    return false;
                }

                if (isAlarm(record)) {
                    if (!config.allowAlarms) {
                        ZenLog.traceIntercepted(record, "!allowAlarms");
                        return true;
                    }
                    return false;
                }
                if (isCall(record)) {
                    if (config.allowRepeatCallers
                            && REPEAT_CALLERS.isRepeat(mContext, extras(record))) {
                        ZenLog.traceNotIntercepted(record, "repeatCaller");
                        return false;
                    }
                    if (!config.allowCalls) {
                        ZenLog.traceIntercepted(record, "!allowCalls");
                        return true;
                    }
                    return shouldInterceptAudience(config.allowCallsFrom, record);
                }
                if (isMessage(record)) {
                    if (!config.allowMessages) {
                        ZenLog.traceIntercepted(record, "!allowMessages");
                        return true;
                    }
                    return shouldInterceptAudience(config.allowMessagesFrom, record);
                }
                if (isEvent(record)) {
                    if (!config.allowEvents) {
                        ZenLog.traceIntercepted(record, "!allowEvents");
                        return true;
                    }
                    return false;
                }
                if (isReminder(record)) {
                    if (!config.allowReminders) {
                        ZenLog.traceIntercepted(record, "!allowReminders");
                        return true;
                    }
                    return false;
                }
                AudioAttributes aa = record.getAudioAttributes();
                if (aa != null && AudioAttributes.SUPPRESSIBLE_USAGES.get(aa.getUsage()) ==
                        AudioAttributes.SUPPRESSIBLE_MEDIA_SYSTEM_OTHER) {
                    if (!config.allowMediaSystemOther) {
                        ZenLog.traceIntercepted(record, "!allowMediaSystemOther");
                        return true;
                    }
                    return false;
                }
                ZenLog.traceIntercepted(record, "!priority");
                return true;
            default:
                return false;
        }
    }

    private static boolean shouldInterceptAudience(int source, NotificationRecord record) {
        if (!audienceMatches(source, record.getContactAffinity())) {
            ZenLog.traceIntercepted(record, "!audienceMatches");
            return true;
        }
        return false;
    }

    private static boolean isAlarm(NotificationRecord record) {
        return record.isCategory(Notification.CATEGORY_ALARM)
                || record.isAudioStream(AudioManager.STREAM_ALARM)
                || record.isAudioAttributesUsage(AudioAttributes.USAGE_ALARM);
    }

    private static boolean isEvent(NotificationRecord record) {
        return record.isCategory(Notification.CATEGORY_EVENT);
    }

    private static boolean isReminder(NotificationRecord record) {
        return record.isCategory(Notification.CATEGORY_REMINDER);
    }

    public boolean isCall(NotificationRecord record) {
        return record != null && (isDefaultPhoneApp(record.sbn.getPackageName())
                || record.isCategory(Notification.CATEGORY_CALL));
    }

    private boolean isDefaultPhoneApp(String pkg) {
        if (mDefaultPhoneApp == null) {
            final TelecomManager telecomm =
                    (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            mDefaultPhoneApp = telecomm != null ? telecomm.getDefaultPhoneApp() : null;
            if (DEBUG) Slog.d(TAG, "Default phone app: " + mDefaultPhoneApp);
        }
        return pkg != null && mDefaultPhoneApp != null
                && pkg.equals(mDefaultPhoneApp.getPackageName());
    }

    @SuppressWarnings("deprecation")
    private boolean isDefaultMessagingApp(NotificationRecord record) {
        final int userId = record.getUserId();
        if (userId == UserHandle.USER_NULL || userId == UserHandle.USER_ALL) return false;
        final String defaultApp = Secure.getStringForUser(mContext.getContentResolver(),
                Secure.SMS_DEFAULT_APPLICATION, userId);
        return Objects.equals(defaultApp, record.sbn.getPackageName());
    }

    private boolean isMessage(NotificationRecord record) {
        return record.isCategory(Notification.CATEGORY_MESSAGE) || isDefaultMessagingApp(record);
    }

    private static boolean audienceMatches(int source, float contactAffinity) {
        switch (source) {
            case ZenModeConfig.SOURCE_ANYONE:
                return true;
            case ZenModeConfig.SOURCE_CONTACT:
                return contactAffinity >= ValidateNotificationPeople.VALID_CONTACT;
            case ZenModeConfig.SOURCE_STAR:
                return contactAffinity >= ValidateNotificationPeople.STARRED_CONTACT;
            default:
                Slog.w(TAG, "Encountered unknown source: " + source);
                return true;
        }
    }

    private static class RepeatCallers {
        // Person : time
        private final ArrayMap<String, Long> mCalls = new ArrayMap<>();
        private int mThresholdMinutes;

        private synchronized void recordCall(Context context, Bundle extras) {
            setThresholdMinutes(context);
            if (mThresholdMinutes <= 0 || extras == null) return;
            final String peopleString = peopleString(extras);
            if (peopleString == null) return;
            final long now = System.currentTimeMillis();
            cleanUp(mCalls, now);
            mCalls.put(peopleString, now);
        }

        private synchronized boolean isRepeat(Context context, Bundle extras) {
            setThresholdMinutes(context);
            if (mThresholdMinutes <= 0 || extras == null) return false;
            final String peopleString = peopleString(extras);
            if (peopleString == null) return false;
            final long now = System.currentTimeMillis();
            cleanUp(mCalls, now);
            return mCalls.containsKey(peopleString);
        }

        private synchronized void cleanUp(ArrayMap<String, Long> calls, long now) {
            final int N = calls.size();
            for (int i = N - 1; i >= 0; i--) {
                final long time = mCalls.valueAt(i);
                if (time > now || (now - time) > mThresholdMinutes * 1000 * 60) {
                    calls.removeAt(i);
                }
            }
        }

        private void setThresholdMinutes(Context context) {
            if (mThresholdMinutes <= 0) {
                mThresholdMinutes = context.getResources().getInteger(com.android.internal.R.integer
                        .config_zen_repeat_callers_threshold);
            }
        }

        private static String peopleString(Bundle extras) {
            final String[] extraPeople = ValidateNotificationPeople.getExtraPeople(extras);
            if (extraPeople == null || extraPeople.length == 0) return null;
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < extraPeople.length; i++) {
                String extraPerson = extraPeople[i];
                if (extraPerson == null) continue;
                extraPerson = extraPerson.trim();
                if (extraPerson.isEmpty()) continue;
                if (sb.length() > 0) {
                    sb.append('|');
                }
                sb.append(extraPerson);
            }
            return sb.length() == 0 ? null : sb.toString();
        }
    }

}
