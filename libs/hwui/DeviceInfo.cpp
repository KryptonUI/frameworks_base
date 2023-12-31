/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <DeviceInfo.h>

#include <gui/ISurfaceComposer.h>
#include <gui/SurfaceComposerClient.h>

#include <mutex>
#include <thread>

#include <log/log.h>

#include <GLES2/gl2.h>

namespace android {
namespace uirenderer {

static DeviceInfo* sDeviceInfo = nullptr;
static std::once_flag sInitializedFlag;

const DeviceInfo* DeviceInfo::get() {
    LOG_ALWAYS_FATAL_IF(!sDeviceInfo, "DeviceInfo not yet initialized.");
    return sDeviceInfo;
}

void DeviceInfo::initialize() {
    std::call_once(sInitializedFlag, []() {
        sDeviceInfo = new DeviceInfo();
        sDeviceInfo->load();
    });
}

void DeviceInfo::initialize(int maxTextureSize) {
    std::call_once(sInitializedFlag, [maxTextureSize]() {
        sDeviceInfo = new DeviceInfo();
        sDeviceInfo->loadDisplayInfo();
        sDeviceInfo->mMaxTextureSize = maxTextureSize;
    });
}

void DeviceInfo::load() {
    loadDisplayInfo();
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &mMaxTextureSize);
}

void DeviceInfo::loadDisplayInfo() {
    sp<IBinder> dtoken(SurfaceComposerClient::getBuiltInDisplay(ISurfaceComposer::eDisplayIdMain));
    status_t status = SurfaceComposerClient::getDisplayInfo(dtoken, &mDisplayInfo);
    LOG_ALWAYS_FATAL_IF(status, "Failed to get display info, error %d", status);
}

} /* namespace uirenderer */
} /* namespace android */
