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

package com.android.keyguard;

import android.content.Intent;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableLooper.RunWithLooper;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.systemui.SysuiTestCase;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@RunWithLooper
public class KeyguardUpdateMonitorTest extends SysuiTestCase {

    private TestableLooper mTestableLooper;

    @Before
    public void setup() {
        mTestableLooper = TestableLooper.get(this);
    }

    @Test
    public void testIgnoresSimStateCallback_rebroadcast() {
        Intent intent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);

        AtomicBoolean simStateChanged = new AtomicBoolean(false);
        KeyguardUpdateMonitor keyguardUpdateMonitor = new KeyguardUpdateMonitor(getContext()) {
            @Override
            protected void handleSimStateChange(int subId, int slotId,
                    IccCardConstants.State state) {
                simStateChanged.set(true);
                super.handleSimStateChange(subId, slotId, state);
            }
        };

        keyguardUpdateMonitor.mBroadcastReceiver.onReceive(getContext(), intent);
        mTestableLooper.processAllMessages();
        Assert.assertTrue("onSimStateChanged not called", simStateChanged.get());

        intent.putExtra(TelephonyIntents.EXTRA_REBROADCAST_ON_UNLOCK, true);
        simStateChanged.set(false);
        keyguardUpdateMonitor.mBroadcastReceiver.onReceive(getContext(), intent);
        mTestableLooper.processAllMessages();
        Assert.assertFalse("onSimStateChanged should have been skipped", simStateChanged.get());
    }
}
