/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "LayerDrawable.h"
#include "GlLayer.h"
#include "VkLayer.h"

#include "GrBackendSurface.h"
#include "SkColorFilter.h"
#include "SkSurface.h"
#include "gl/GrGLTypes.h"

namespace android {
namespace uirenderer {
namespace skiapipeline {

void LayerDrawable::onDraw(SkCanvas* canvas) {
    Layer* layer = mLayerUpdater->backingLayer();
    if (layer) {
        DrawLayer(canvas->getGrContext(), canvas, layer);
    }
}

bool LayerDrawable::DrawLayer(GrContext* context, SkCanvas* canvas, Layer* layer) {
    if (context == nullptr) {
        SkDEBUGF(("Attempting to draw LayerDrawable into an unsupported surface"));
        return false;
    }
    // transform the matrix based on the layer
    SkMatrix layerTransform;
    layer->getTransform().copyTo(layerTransform);
    sk_sp<SkImage> layerImage;
    int layerWidth = layer->getWidth();
    int layerHeight = layer->getHeight();
    if (layer->getApi() == Layer::Api::OpenGL) {
        GlLayer* glLayer = static_cast<GlLayer*>(layer);
        GrGLTextureInfo externalTexture;
        externalTexture.fTarget = glLayer->getRenderTarget();
        externalTexture.fID = glLayer->getTextureId();
        GrBackendTexture backendTexture(layerWidth, layerHeight, kRGBA_8888_GrPixelConfig,
                                        externalTexture);
        layerImage = SkImage::MakeFromTexture(context, backendTexture, kTopLeft_GrSurfaceOrigin,
                                              kPremul_SkAlphaType, nullptr);
    } else {
        SkASSERT(layer->getApi() == Layer::Api::Vulkan);
        VkLayer* vkLayer = static_cast<VkLayer*>(layer);
        canvas->clear(SK_ColorGREEN);
        layerImage = vkLayer->getImage();
    }

    if (layerImage) {
        SkMatrix textureMatrix;
        layer->getTexTransform().copyTo(textureMatrix);
        // TODO: after skia bug https://bugs.chromium.org/p/skia/issues/detail?id=7075 is fixed
        // use bottom left origin and remove flipV and invert transformations.
        SkMatrix flipV;
        flipV.setAll(1, 0, 0, 0, -1, 1, 0, 0, 1);
        textureMatrix.preConcat(flipV);
        textureMatrix.preScale(1.0f / layerWidth, 1.0f / layerHeight);
        textureMatrix.postScale(layerWidth, layerHeight);
        SkMatrix textureMatrixInv;
        if (!textureMatrix.invert(&textureMatrixInv)) {
            textureMatrixInv = textureMatrix;
        }

        SkMatrix matrix = SkMatrix::Concat(layerTransform, textureMatrixInv);

        SkPaint paint;
        paint.setAlpha(layer->getAlpha());
        paint.setBlendMode(layer->getMode());
        paint.setColorFilter(sk_ref_sp(layer->getColorFilter()));

        const bool nonIdentityMatrix = !matrix.isIdentity();
        if (nonIdentityMatrix) {
            canvas->save();
            canvas->concat(matrix);
        }
        canvas->drawImage(layerImage.get(), 0, 0, &paint);
        // restore the original matrix
        if (nonIdentityMatrix) {
            canvas->restore();
        }
    }

    return layerImage;
}

};  // namespace skiapipeline
};  // namespace uirenderer
};  // namespace android
