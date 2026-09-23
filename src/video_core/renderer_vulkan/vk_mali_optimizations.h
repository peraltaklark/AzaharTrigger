// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include "common/settings.h"
#include "video_core/renderer_vulkan/vk_common.h"

namespace Vulkan {

/**
 * Mali GPU Optimizations
 *
 * Mali GPUs (ARM's GPU architecture) are tile-based renderers with specific
 * optimization opportunities:
 *
 * 1. Mediump (16-bit float) precision: Mali GPUs have excellent 16-bit float
 *    performance. Using mediump instead of highp can provide 5-10% FPS boost.
 *
 * 2. Optimal tile sizes: Mali works best with render targets that align with
 *    its tile size (typically 16x16 pixels).
 *
 * 3. Avoiding expensive operations: Mali struggles with discard operations
 *    and non-coherent framebuffer fetch.
 */
struct MaliOptimizations {
    bool enabled = false;
    bool use_mediump_precision = false;
    bool optimize_tile_sizes = false;

    static MaliOptimizations Get(bool is_mali_gpu) {
        MaliOptimizations opts;
        opts.enabled = Settings::values.mali_gpu_optimizations.GetValue() && is_mali_gpu;

        if (opts.enabled) {
            // Use 16-bit floats for better performance on Mali
            opts.use_mediump_precision = true;

            // Optimize render target sizes for Mali's tile architecture
            opts.optimize_tile_sizes = true;

            LOG_INFO(Render_Vulkan, "Mali GPU optimizations enabled");
        }

        return opts;
    }
};

} // namespace Vulkan
