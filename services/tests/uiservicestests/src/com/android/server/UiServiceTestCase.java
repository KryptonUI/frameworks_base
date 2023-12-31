/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.server;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.testing.TestableContext;

import org.junit.Before;
import org.junit.Rule;


public class UiServiceTestCase {
    @Rule
    public final TestableContext mContext =
            new TestableContext(InstrumentationRegistry.getContext(), null);

    protected TestableContext getContext() {
        return mContext;
    }

    @Before
    public void setup() {
        // Share classloader to allow package access.
        System.setProperty("dexmaker.share_classloader", "true");
    }
}
