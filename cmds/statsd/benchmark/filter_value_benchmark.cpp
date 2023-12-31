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
 * limitations under the License.
 */
#include <vector>
#include "benchmark/benchmark.h"
#include "FieldValue.h"
#include "HashableDimensionKey.h"
#include "logd/LogEvent.h"

namespace android {
namespace os {
namespace statsd {

using std::vector;

static void BM_FilterValue(benchmark::State& state) {
    LogEvent event(1, 100000);
    event.write(3.2f);
    event.write("LOCATION");
    event.write((int64_t)990);
    event.init();

    FieldMatcher field_matcher;
    field_matcher.set_field(1);
    field_matcher.add_child()->set_field(2);
    field_matcher.add_child()->set_field(3);

    std::vector<Matcher> matchers;
    translateFieldMatcher(field_matcher, &matchers);

    while (state.KeepRunning()) {
        vector<HashableDimensionKey> output;
        filterValues(matchers, event.getValues(), &output);
    }
}

BENCHMARK(BM_FilterValue);

}  //  namespace statsd
}  //  namespace os
}  //  namespace android
