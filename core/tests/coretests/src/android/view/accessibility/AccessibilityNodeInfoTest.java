/*
 * Copyright 2017 The Android Open Source Project
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

package android.view.accessibility;

import static org.junit.Assert.fail;

import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.ArraySet;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;

import com.android.internal.util.CollectionUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AccessibilityNodeInfoTest {

    @Test
    public void testStandardActions_serializationFlagIsValid() {
        AccessibilityAction brokenStandardAction = CollectionUtils.find(
                new ArrayList<>(AccessibilityAction.sStandardActions),
                action -> Integer.bitCount(action.mSerializationFlag) != 1);
        if (brokenStandardAction != null) {
            String message = "Invalid serialization flag(0x"
                    + Integer.toHexString(brokenStandardAction.mSerializationFlag)
                    + ") in " + brokenStandardAction;
            if (brokenStandardAction.mSerializationFlag == 0L) {
                message += "\nThis is likely due to an overflow";
            }
            fail(message);
        }

        brokenStandardAction = CollectionUtils.find(
                new ArrayList<>(AccessibilityAction.sStandardActions),
                action -> Integer.bitCount(action.getId()) == 1
                        && action.getId() <= AccessibilityNodeInfo.LAST_LEGACY_STANDARD_ACTION
                        && action.getId() != action.mSerializationFlag);
        if (brokenStandardAction != null) {
            fail("Serialization flag(0x"
                    + Integer.toHexString(brokenStandardAction.mSerializationFlag)
                    + ") is different from legacy action id(0x"
                    + Integer.toHexString(brokenStandardAction.getId())
                    + ") in " + brokenStandardAction);
        }
    }

    @Test
    public void testStandardActions_idsAreUnique() {
        ArraySet<AccessibilityAction> actions = AccessibilityAction.sStandardActions;
        for (int i = 0; i < actions.size(); i++) {
            for (int j = 0; j < i; j++) {
                int id = actions.valueAt(i).getId();
                if (id == actions.valueAt(j).getId()) {
                    fail("Id 0x" + Integer.toHexString(id)
                            + " is duplicated for standard actions #" + i + " and #" + j);
                }
            }
        }
    }

}
