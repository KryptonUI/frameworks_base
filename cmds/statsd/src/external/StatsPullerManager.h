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

#pragma once

#include "StatsPullerManagerImpl.h"

namespace android {
namespace os {
namespace statsd {

class StatsPullerManager {
 public:
    virtual ~StatsPullerManager() {}

    virtual void RegisterReceiver(int tagId,
                                  wp <PullDataReceiver> receiver,
                                  long intervalMs) {
        mPullerManager.RegisterReceiver(tagId, receiver, intervalMs);
    };

    virtual void UnRegisterReceiver(int tagId, wp <PullDataReceiver> receiver) {
        mPullerManager.UnRegisterReceiver(tagId, receiver);
    };

    // Verify if we know how to pull for this matcher
    bool PullerForMatcherExists(int tagId) {
        return mPullerManager.PullerForMatcherExists(tagId);
    }

    void OnAlarmFired() {
        mPullerManager.OnAlarmFired();
    }

    virtual bool
    Pull(const int tagId, vector<std::shared_ptr<LogEvent>>* data) {
        return mPullerManager.Pull(tagId, data);
    }

    void SetTimeBaseSec(const long timeBaseSec) {
        mPullerManager.SetTimeBaseSec(timeBaseSec);
    }

    int ForceClearPullerCache() {
        return mPullerManager.ForceClearPullerCache();
    }

    int ClearPullerCacheIfNecessary(long timestampSec) {
        return mPullerManager.ClearPullerCacheIfNecessary(timestampSec);
    }

 private:
    StatsPullerManagerImpl
        & mPullerManager = StatsPullerManagerImpl::GetInstance();
};

}  // namespace statsd
}  // namespace os
}  // namespace android
