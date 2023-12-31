/*
 * Copyright (C) 2012 The Android Open Source Project
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

#include "Layer.h"

#include "renderstate/RenderState.h"

#include <SkColorFilter.h>

namespace android {
namespace uirenderer {

Layer::Layer(RenderState& renderState, Api api, SkColorFilter* colorFilter, int alpha,
             SkBlendMode mode)
        : GpuMemoryTracker(GpuObjectType::Layer)
        , mRenderState(renderState)
        , mApi(api)
        , colorFilter(nullptr)
        , alpha(alpha)
        , mode(mode) {
    // TODO: This is a violation of Android's typical ref counting, but it
    // preserves the old inc/dec ref locations. This should be changed...
    incStrong(nullptr);

    renderState.registerLayer(this);
}

Layer::~Layer() {
    SkSafeUnref(colorFilter);

    mRenderState.unregisterLayer(this);
}

void Layer::setColorFilter(SkColorFilter* filter) {
    SkRefCnt_SafeAssign(colorFilter, filter);
}

void Layer::postDecStrong() {
    mRenderState.postDecStrong(this);
}

};  // namespace uirenderer
};  // namespace android
