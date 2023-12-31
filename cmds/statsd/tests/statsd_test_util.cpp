// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "statsd_test_util.h"

namespace android {
namespace os {
namespace statsd {

AtomMatcher CreateSimpleAtomMatcher(const string& name, int atomId) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(atomId);
    return atom_matcher;
}

AtomMatcher CreateScreenBrightnessChangedAtomMatcher() {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId("ScreenBrightnessChanged"));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(android::util::SCREEN_BRIGHTNESS_CHANGED);
    return atom_matcher;
}

AtomMatcher CreateUidProcessStateChangedAtomMatcher() {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId("UidProcessStateChanged"));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(android::util::UID_PROCESS_STATE_CHANGED);
    return atom_matcher;
}

AtomMatcher CreateWakelockStateChangedAtomMatcher(const string& name,
                                                  WakelockStateChanged::State state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(android::util::WAKELOCK_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(4);  // State field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateAcquireWakelockAtomMatcher() {
    return CreateWakelockStateChangedAtomMatcher("AcquireWakelock", WakelockStateChanged::ACQUIRE);
}

AtomMatcher CreateReleaseWakelockAtomMatcher() {
    return CreateWakelockStateChangedAtomMatcher("ReleaseWakelock", WakelockStateChanged::RELEASE);
}

AtomMatcher CreateBatterySaverModeStateChangedAtomMatcher(
    const string& name, BatterySaverModeStateChanged::State state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(android::util::BATTERY_SAVER_MODE_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(1);  // State field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateBatterySaverModeStartAtomMatcher() {
    return CreateBatterySaverModeStateChangedAtomMatcher(
        "BatterySaverModeStart", BatterySaverModeStateChanged::ON);
}


AtomMatcher CreateBatterySaverModeStopAtomMatcher() {
    return CreateBatterySaverModeStateChangedAtomMatcher(
        "BatterySaverModeStop", BatterySaverModeStateChanged::OFF);
}


AtomMatcher CreateScreenStateChangedAtomMatcher(
    const string& name, android::view::DisplayStateEnum state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(android::util::SCREEN_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(1);  // State field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}


AtomMatcher CreateScreenTurnedOnAtomMatcher() {
    return CreateScreenStateChangedAtomMatcher("ScreenTurnedOn",
            android::view::DisplayStateEnum::DISPLAY_STATE_ON);
}

AtomMatcher CreateScreenTurnedOffAtomMatcher() {
    return CreateScreenStateChangedAtomMatcher("ScreenTurnedOff",
            ::android::view::DisplayStateEnum::DISPLAY_STATE_OFF);
}

AtomMatcher CreateSyncStateChangedAtomMatcher(
    const string& name, SyncStateChanged::State state) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(android::util::SYNC_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(3);  // State field.
    field_value_matcher->set_eq_int(state);
    return atom_matcher;
}

AtomMatcher CreateSyncStartAtomMatcher() {
    return CreateSyncStateChangedAtomMatcher("SyncStart", SyncStateChanged::ON);
}

AtomMatcher CreateSyncEndAtomMatcher() {
    return CreateSyncStateChangedAtomMatcher("SyncEnd", SyncStateChanged::OFF);
}

AtomMatcher CreateActivityForegroundStateChangedAtomMatcher(
    const string& name, ActivityForegroundStateChanged::Activity activity) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(android::util::ACTIVITY_FOREGROUND_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(4);  // Activity field.
    field_value_matcher->set_eq_int(activity);
    return atom_matcher;
}

AtomMatcher CreateMoveToBackgroundAtomMatcher() {
    return CreateActivityForegroundStateChangedAtomMatcher(
        "MoveToBackground", ActivityForegroundStateChanged::MOVE_TO_BACKGROUND);
}

AtomMatcher CreateMoveToForegroundAtomMatcher() {
    return CreateActivityForegroundStateChangedAtomMatcher(
        "MoveToForeground", ActivityForegroundStateChanged::MOVE_TO_FOREGROUND);
}

AtomMatcher CreateProcessLifeCycleStateChangedAtomMatcher(
    const string& name, ProcessLifeCycleStateChanged::Event event) {
    AtomMatcher atom_matcher;
    atom_matcher.set_id(StringToId(name));
    auto simple_atom_matcher = atom_matcher.mutable_simple_atom_matcher();
    simple_atom_matcher->set_atom_id(android::util::PROCESS_LIFE_CYCLE_STATE_CHANGED);
    auto field_value_matcher = simple_atom_matcher->add_field_value_matcher();
    field_value_matcher->set_field(3);  // Process state field.
    field_value_matcher->set_eq_int(event);
    return atom_matcher;
}

AtomMatcher CreateProcessCrashAtomMatcher() {
    return CreateProcessLifeCycleStateChangedAtomMatcher(
        "ProcessCrashed", ProcessLifeCycleStateChanged::PROCESS_CRASHED);
}

Predicate CreateBatterySaverModePredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("BatterySaverIsOn"));
    predicate.mutable_simple_predicate()->set_start(StringToId("BatterySaverModeStart"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("BatterySaverModeStop"));
    return predicate;
}

Predicate CreateScreenIsOnPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("ScreenIsOn"));
    predicate.mutable_simple_predicate()->set_start(StringToId("ScreenTurnedOn"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("ScreenTurnedOff"));
    return predicate;
}

Predicate CreateScreenIsOffPredicate() {
    Predicate predicate;
    predicate.set_id(1111123);
    predicate.mutable_simple_predicate()->set_start(StringToId("ScreenTurnedOff"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("ScreenTurnedOn"));
    return predicate;
}

Predicate CreateHoldingWakelockPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("HoldingWakelock"));
    predicate.mutable_simple_predicate()->set_start(StringToId("AcquireWakelock"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("ReleaseWakelock"));
    return predicate;
}

Predicate CreateIsSyncingPredicate() {
    Predicate predicate;
    predicate.set_id(33333333333333);
    predicate.mutable_simple_predicate()->set_start(StringToId("SyncStart"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("SyncEnd"));
    return predicate;
}

Predicate CreateIsInBackgroundPredicate() {
    Predicate predicate;
    predicate.set_id(StringToId("IsInBackground"));
    predicate.mutable_simple_predicate()->set_start(StringToId("MoveToBackground"));
    predicate.mutable_simple_predicate()->set_stop(StringToId("MoveToForeground"));
    return predicate;
}

void addPredicateToPredicateCombination(const Predicate& predicate,
                                        Predicate* combinationPredicate) {
    combinationPredicate->mutable_combination()->add_predicate(predicate.id());
}

FieldMatcher CreateAttributionUidDimensions(const int atomId,
                                            const std::vector<Position>& positions) {
    FieldMatcher dimensions;
    dimensions.set_field(atomId);
    for (const auto position : positions) {
        auto child = dimensions.add_child();
        child->set_field(1);
        child->set_position(position);
        child->add_child()->set_field(1);
    }
    return dimensions;
}

FieldMatcher CreateAttributionUidAndTagDimensions(const int atomId,
                                                 const std::vector<Position>& positions) {
    FieldMatcher dimensions;
    dimensions.set_field(atomId);
    for (const auto position : positions) {
        auto child = dimensions.add_child();
        child->set_field(1);
        child->set_position(position);
        child->add_child()->set_field(1);
        child->add_child()->set_field(2);
    }
    return dimensions;
}

FieldMatcher CreateDimensions(const int atomId, const std::vector<int>& fields) {
    FieldMatcher dimensions;
    dimensions.set_field(atomId);
    for (const int field : fields) {
        dimensions.add_child()->set_field(field);
    }
    return dimensions;
}

std::unique_ptr<LogEvent> CreateScreenStateChangedEvent(
    const android::view::DisplayStateEnum state, uint64_t timestampNs) {
    auto event = std::make_unique<LogEvent>(android::util::SCREEN_STATE_CHANGED, timestampNs);
    EXPECT_TRUE(event->write(state));
    event->init();
    return event;
}

std::unique_ptr<LogEvent> CreateBatterySaverOnEvent(uint64_t timestampNs) {
    auto event = std::make_unique<LogEvent>(
        android::util::BATTERY_SAVER_MODE_STATE_CHANGED, timestampNs);
    EXPECT_TRUE(event->write(BatterySaverModeStateChanged::ON));
    event->init();
    return event;
}

std::unique_ptr<LogEvent> CreateBatterySaverOffEvent(uint64_t timestampNs) {
    auto event = std::make_unique<LogEvent>(
        android::util::BATTERY_SAVER_MODE_STATE_CHANGED, timestampNs);
    EXPECT_TRUE(event->write(BatterySaverModeStateChanged::OFF));
    event->init();
    return event;
}

std::unique_ptr<LogEvent> CreateScreenBrightnessChangedEvent(
    int level, uint64_t timestampNs) {
    auto event = std::make_unique<LogEvent>(android::util::SCREEN_BRIGHTNESS_CHANGED, timestampNs);
    EXPECT_TRUE(event->write(level));
    event->init();
    return event;

}

std::unique_ptr<LogEvent> CreateWakelockStateChangedEvent(
        const std::vector<AttributionNodeInternal>& attributions, const string& wakelockName,
        const WakelockStateChanged::State state, uint64_t timestampNs) {
    auto event = std::make_unique<LogEvent>(android::util::WAKELOCK_STATE_CHANGED, timestampNs);
    event->write(attributions);
    event->write(android::os::WakeLockLevelEnum::PARTIAL_WAKE_LOCK);
    event->write(wakelockName);
    event->write(state);
    event->init();
    return event;
}

std::unique_ptr<LogEvent> CreateAcquireWakelockEvent(
        const std::vector<AttributionNodeInternal>& attributions, const string& wakelockName,
        uint64_t timestampNs) {
    return CreateWakelockStateChangedEvent(
        attributions, wakelockName, WakelockStateChanged::ACQUIRE, timestampNs);
}

std::unique_ptr<LogEvent> CreateReleaseWakelockEvent(
        const std::vector<AttributionNodeInternal>& attributions, const string& wakelockName,
        uint64_t timestampNs) {
    return CreateWakelockStateChangedEvent(
        attributions, wakelockName, WakelockStateChanged::RELEASE, timestampNs);
}

std::unique_ptr<LogEvent> CreateActivityForegroundStateChangedEvent(
    const int uid, const ActivityForegroundStateChanged::Activity activity, uint64_t timestampNs) {
    auto event = std::make_unique<LogEvent>(
        android::util::ACTIVITY_FOREGROUND_STATE_CHANGED, timestampNs);
    event->write(uid);
    event->write("pkg_name");
    event->write("class_name");
    event->write(activity);
    event->init();
    return event;
}

std::unique_ptr<LogEvent> CreateMoveToBackgroundEvent(const int uid, uint64_t timestampNs) {
    return CreateActivityForegroundStateChangedEvent(
        uid, ActivityForegroundStateChanged::MOVE_TO_BACKGROUND, timestampNs);
}

std::unique_ptr<LogEvent> CreateMoveToForegroundEvent(const int uid, uint64_t timestampNs) {
    return CreateActivityForegroundStateChangedEvent(
        uid, ActivityForegroundStateChanged::MOVE_TO_FOREGROUND, timestampNs);
}

std::unique_ptr<LogEvent> CreateSyncStateChangedEvent(
        const std::vector<AttributionNodeInternal>& attributions, const string& name,
        const SyncStateChanged::State state, uint64_t timestampNs) {
    auto event = std::make_unique<LogEvent>(android::util::SYNC_STATE_CHANGED, timestampNs);
    event->write(attributions);
    event->write(name);
    event->write(state);
    event->init();
    return event;
}

std::unique_ptr<LogEvent> CreateSyncStartEvent(
        const std::vector<AttributionNodeInternal>& attributions, const string& name,
        uint64_t timestampNs) {
    return CreateSyncStateChangedEvent(attributions, name, SyncStateChanged::ON, timestampNs);
}

std::unique_ptr<LogEvent> CreateSyncEndEvent(
        const std::vector<AttributionNodeInternal>& attributions, const string& name,
        uint64_t timestampNs) {
    return CreateSyncStateChangedEvent(attributions, name, SyncStateChanged::OFF, timestampNs);
}

std::unique_ptr<LogEvent> CreateProcessLifeCycleStateChangedEvent(
    const int uid, const ProcessLifeCycleStateChanged::Event event, uint64_t timestampNs) {
    auto logEvent = std::make_unique<LogEvent>(
        android::util::PROCESS_LIFE_CYCLE_STATE_CHANGED, timestampNs);
    logEvent->write(uid);
    logEvent->write("");
    logEvent->write(event);
    logEvent->init();
    return logEvent;
}

std::unique_ptr<LogEvent> CreateAppCrashEvent(const int uid, uint64_t timestampNs) {
    return CreateProcessLifeCycleStateChangedEvent(
        uid, ProcessLifeCycleStateChanged::PROCESS_CRASHED, timestampNs);
}

std::unique_ptr<LogEvent> CreateIsolatedUidChangedEvent(
    int isolatedUid, int hostUid, bool is_create, uint64_t timestampNs) {
    auto logEvent = std::make_unique<LogEvent>(
        android::util::ISOLATED_UID_CHANGED, timestampNs);
    logEvent->write(hostUid);
    logEvent->write(isolatedUid);
    logEvent->write(is_create);
    logEvent->init();
    return logEvent;
}

sp<StatsLogProcessor> CreateStatsLogProcessor(const long timeBaseSec, const StatsdConfig& config,
                                              const ConfigKey& key) {
    sp<UidMap> uidMap = new UidMap();
    sp<AlarmMonitor> anomalyAlarmMonitor;
    sp<AlarmMonitor> periodicAlarmMonitor;
    sp<StatsLogProcessor> processor = new StatsLogProcessor(
        uidMap, anomalyAlarmMonitor, periodicAlarmMonitor, timeBaseSec, [](const ConfigKey&){});
    processor->OnConfigUpdated(key, config);
    return processor;
}

AttributionNodeInternal CreateAttribution(const int& uid, const string& tag) {
    AttributionNodeInternal attribution;
    attribution.set_uid(uid);
    attribution.set_tag(tag);
    return attribution;
}

void sortLogEventsByTimestamp(std::vector<std::unique_ptr<LogEvent>> *events) {
  std::sort(events->begin(), events->end(),
            [](const std::unique_ptr<LogEvent>& a, const std::unique_ptr<LogEvent>& b) {
              return a->GetElapsedTimestampNs() < b->GetElapsedTimestampNs();
            });
}

int64_t StringToId(const string& str) {
    return static_cast<int64_t>(std::hash<std::string>()(str));
}

void ValidateAttributionUidDimension(const DimensionsValue& value, int atomId, int uid) {
    EXPECT_EQ(value.field(), atomId);
    EXPECT_EQ(value.value_tuple().dimensions_value_size(), 1);
    // Attribution field.
    EXPECT_EQ(value.value_tuple().dimensions_value(0).field(), 1);
    // Uid only.
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(0).value_int(), uid);
}

void ValidateUidDimension(const DimensionsValue& value, int atomId, int uid) {
    EXPECT_EQ(value.field(), atomId);
    EXPECT_EQ(value.value_tuple().dimensions_value_size(), 1);
    // Attribution field.
    EXPECT_EQ(value.value_tuple().dimensions_value(0).field(), 1);
    // Uid only.
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value_size(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(0).value_int(), uid);
}

void ValidateAttributionUidAndTagDimension(
    const DimensionsValue& value, int atomId, int uid, const std::string& tag) {
    EXPECT_EQ(value.field(), atomId);
    EXPECT_EQ(value.value_tuple().dimensions_value_size(), 1);
    // Attribution field.
    EXPECT_EQ(value.value_tuple().dimensions_value(0).field(), 1);
    // Uid only.
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value_size(), 2);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(0).field(), 1);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(0).value_int(), uid);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(1).field(), 2);
    EXPECT_EQ(value.value_tuple().dimensions_value(0)
        .value_tuple().dimensions_value(1).value_str(), tag);
}

bool EqualsTo(const DimensionsValue& s1, const DimensionsValue& s2) {
    if (s1.field() != s2.field()) {
        return false;
    }
    if (s1.value_case() != s2.value_case()) {
        return false;
    }
    switch (s1.value_case()) {
        case DimensionsValue::ValueCase::kValueStr:
            return (s1.value_str() == s2.value_str());
        case DimensionsValue::ValueCase::kValueInt:
            return s1.value_int() == s2.value_int();
        case DimensionsValue::ValueCase::kValueLong:
            return s1.value_long() == s2.value_long();
        case DimensionsValue::ValueCase::kValueBool:
            return s1.value_bool() == s2.value_bool();
        case DimensionsValue::ValueCase::kValueFloat:
            return s1.value_float() == s2.value_float();
        case DimensionsValue::ValueCase::kValueTuple: {
            if (s1.value_tuple().dimensions_value_size() !=
                s2.value_tuple().dimensions_value_size()) {
                return false;
            }
            bool allMatched = true;
            for (int i = 0; allMatched && i < s1.value_tuple().dimensions_value_size(); ++i) {
                allMatched &= EqualsTo(s1.value_tuple().dimensions_value(i),
                                       s2.value_tuple().dimensions_value(i));
            }
            return allMatched;
        }
        case DimensionsValue::ValueCase::VALUE_NOT_SET:
        default:
            return true;
    }
}

bool LessThan(const DimensionsValue& s1, const DimensionsValue& s2) {
    if (s1.field() != s2.field()) {
        return s1.field() < s2.field();
    }
    if (s1.value_case() != s2.value_case()) {
        return s1.value_case() < s2.value_case();
    }
    switch (s1.value_case()) {
        case DimensionsValue::ValueCase::kValueStr:
            return s1.value_str() < s2.value_str();
        case DimensionsValue::ValueCase::kValueInt:
            return s1.value_int() < s2.value_int();
        case DimensionsValue::ValueCase::kValueLong:
            return s1.value_long() < s2.value_long();
        case DimensionsValue::ValueCase::kValueBool:
            return (int)s1.value_bool() < (int)s2.value_bool();
        case DimensionsValue::ValueCase::kValueFloat:
            return s1.value_float() < s2.value_float();
        case DimensionsValue::ValueCase::kValueTuple: {
            if (s1.value_tuple().dimensions_value_size() !=
                s2.value_tuple().dimensions_value_size()) {
                return s1.value_tuple().dimensions_value_size() <
                       s2.value_tuple().dimensions_value_size();
            }
            for (int i = 0; i < s1.value_tuple().dimensions_value_size(); ++i) {
                if (EqualsTo(s1.value_tuple().dimensions_value(i),
                             s2.value_tuple().dimensions_value(i))) {
                    continue;
                } else {
                    return LessThan(s1.value_tuple().dimensions_value(i),
                                    s2.value_tuple().dimensions_value(i));
                }
            }
            return false;
        }
        case DimensionsValue::ValueCase::VALUE_NOT_SET:
        default:
            return false;
    }
}

bool LessThan(const DimensionsPair& s1, const DimensionsPair& s2) {
    if (LessThan(s1.dimInWhat, s2.dimInWhat)) {
        return true;
    } else if (LessThan(s2.dimInWhat, s1.dimInWhat)) {
        return false;
    }

    return LessThan(s1.dimInCondition, s2.dimInCondition);
}

}  // namespace statsd
}  // namespace os
}  // namespace android