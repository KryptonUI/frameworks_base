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

#define DEBUG false  // STOPSHIP if true
#include "Log.h"

#include "DurationAnomalyTracker.h"
#include "guardrail/StatsdStats.h"

namespace android {
namespace os {
namespace statsd {

DurationAnomalyTracker::DurationAnomalyTracker(const Alert& alert, const ConfigKey& configKey,
                                               const sp<AlarmMonitor>& alarmMonitor)
    : AnomalyTracker(alert, configKey), mAlarmMonitor(alarmMonitor) {
}

DurationAnomalyTracker::~DurationAnomalyTracker() {
    stopAllAlarms();
}

void DurationAnomalyTracker::resetStorage() {
    AnomalyTracker::resetStorage();
    if (!mAlarms.empty()) VLOG("AnomalyTracker.resetStorage() called but mAlarms is NOT empty!");
}

void DurationAnomalyTracker::declareAnomalyIfAlarmExpired(const MetricDimensionKey& dimensionKey,
                                                          const uint64_t& timestampNs) {
    auto itr = mAlarms.find(dimensionKey);
    if (itr == mAlarms.end()) {
        return;
    }

    if (itr->second != nullptr &&
        static_cast<uint32_t>(timestampNs / NS_PER_SEC) >= itr->second->timestampSec) {
        declareAnomaly(timestampNs, dimensionKey);
        stopAlarm(dimensionKey);
    }
}

void DurationAnomalyTracker::startAlarm(const MetricDimensionKey& dimensionKey,
                                        const uint64_t& timestampNs) {
    // Alarms are stored in secs. Must round up, since if it fires early, it is ignored completely.
    uint32_t timestampSec = static_cast<uint32_t>((timestampNs -1)/ NS_PER_SEC) + 1; // round up
    if (isInRefractoryPeriod(timestampNs, dimensionKey)) {
        VLOG("Setting a delayed anomaly alarm lest it fall in the refractory period");
        timestampSec = getRefractoryPeriodEndsSec(dimensionKey) + 1;
    }
    sp<const InternalAlarm> alarm = new InternalAlarm{timestampSec};
    mAlarms.insert({dimensionKey, alarm});
    if (mAlarmMonitor != nullptr) {
        mAlarmMonitor->add(alarm);
    }
}

void DurationAnomalyTracker::stopAlarm(const MetricDimensionKey& dimensionKey) {
    auto itr = mAlarms.find(dimensionKey);
    if (itr != mAlarms.end()) {
        mAlarms.erase(dimensionKey);
        if (mAlarmMonitor != nullptr) {
            mAlarmMonitor->remove(itr->second);
        }
    }
}

void DurationAnomalyTracker::stopAllAlarms() {
    std::set<MetricDimensionKey> keys;
    for (auto itr = mAlarms.begin(); itr != mAlarms.end(); ++itr) {
        keys.insert(itr->first);
    }
    for (auto key : keys) {
        stopAlarm(key);
    }
}

void DurationAnomalyTracker::informAlarmsFired(const uint64_t& timestampNs,
        unordered_set<sp<const InternalAlarm>, SpHash<InternalAlarm>>& firedAlarms) {

    if (firedAlarms.empty() || mAlarms.empty()) return;
    // Find the intersection of firedAlarms and mAlarms.
    // The for loop is inefficient, since it loops over all keys, but that's okay since it is very
    // seldomly called. The alternative would be having InternalAlarms store information about the
    // DurationAnomalyTracker and key, but that's a lot of data overhead to speed up something that
    // is rarely ever called.
    unordered_map<MetricDimensionKey, sp<const InternalAlarm>> matchedAlarms;
    for (const auto& kv : mAlarms) {
        if (firedAlarms.count(kv.second) > 0) {
            matchedAlarms.insert({kv.first, kv.second});
        }
    }

    // Now declare each of these alarms to have fired.
    for (const auto& kv : matchedAlarms) {
        declareAnomaly(timestampNs, kv.first);
        mAlarms.erase(kv.first);
        firedAlarms.erase(kv.second);  // No one else can also own it, so we're done with it.
    }
}

}  // namespace statsd
}  // namespace os
}  // namespace android
