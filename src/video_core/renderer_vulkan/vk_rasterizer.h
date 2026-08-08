// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <array>
#include <vector>

#include "video_core/rasterizer_accelerated.h"
#include "video_core/renderer_vulkan/vk_descriptor_update_queue.h"
#include "video_core/renderer_vulkan/vk_pipeline_cache.h"
#include "video_core/renderer_vulkan/vk_render_manager.h"
#include "video_core/renderer_vulkan/vk_stream_buffer.h"
#include "video_core/renderer_vulkan/vk_texture_runtime.h"

namespace Frontend {
class EmuWindow;
}

namespace VideoCore {
class CustomTexManager;
class RendererBase;
} // namespace VideoCore

namespace Pica {
struct DisplayTransferConfig;
struct MemoryFillConfig;
struct FramebufferConfig;
} // namespace Pica

namespace Vulkan {

struct ScreenInfo;

class Instance;
class Scheduler;
class RenderManager;

class RasterizerVulkan : public VideoCore::RasterizerAccelerated {
public:
    explicit RasterizerVulkan(Memory::MemorySystem& memory, Pica::PicaCore& pica,
                              VideoCore::CustomTexManager& custom_tex_manager,
                              VideoCore::RendererBase& renderer, Frontend::EmuWindow& emu_window,
                              const Instance& instance, Scheduler& scheduler,
                              RenderManager& renderpass_cache, DescriptorUpdateQueue& update_queue,
                              u32 image_count);
    ~RasterizerVulkan() override;

    void TickFrame();
    void LoadDefaultDiskResources(const std::atomic_bool& stop_loading,
                                  const VideoCore::DiskResourceLoadCallback& callback) override;

    void DrawTriangles() override;
    void FlushAll() override;
    void FlushRegion(PAddr addr, u32 size) override;
    void InvalidateRegion(PAddr addr, u32 size) override;
    void FlushAndInvalidateRegion(PAddr addr, u32 size) override;
    void ClearAll(bool flush) override;
    bool AccelerateDisplayTransfer(const Pica::DisplayTransferConfig& config) override;
    bool AccelerateTextureCopy(const Pica::DisplayTransferConfig& config) override;
    bool AccelerateFill(const Pica::MemoryFillConfig& config) override;
    bool AccelerateDisplay(const Pica::FramebufferConfig& config, PAddr framebuffer_addr,
                           u32 pixel_stride, ScreenInfo& screen_info);
    bool AccelerateDrawBatch(bool is_indexed) override;

    void SwitchDiskResources(u64 title_id) override;

private:
    void SyncDrawState();
    void SyncAndUploadLUTs();
    void SyncAndUploadLUTsLF();
    void SyncTextureUnits(const Framebuffer* framebuffer);
    void SyncUtilityTextures(const Framebuffer* framebuffer);
    void BindShadowCube(const Pica::TexturingRegs::FullTextureConfig& texture,
                        vk::DescriptorSet texture_set);
    void BindTextureCube(const Pica::TexturingRegs::FullTextureConfig& texture,
                         vk::DescriptorSet texture_set);
    void UploadUniforms(bool accelerate_draw);
    bool Draw(bool accelerate, bool is_indexed);
    bool AccelerateDrawBatchInternal(bool is_indexed);
    void SetupIndexArray();
    void SetupVertexArray();
    void SetupFixedAttribs();
    bool SetupVertexShader();
    bool SetupGeometryShader();
    void MakeSoftwareVertexLayout();

    // -------------------- Batching infrastructure --------------------
    struct TextureBindingState {
        vk::ImageView view = nullptr;
        vk::Sampler sampler = nullptr;
    };

    struct DrawBatchEntry {
        u32 vertex_count;
        s32 vertex_offset;
        u32 binding_count;
        std::array<vk::Buffer, 16> vertex_buffers{};
        std::array<vk::DeviceSize, 16> vertex_offsets{};
        bool is_indexed = false;
        vk::Buffer index_buffer{};
        vk::DeviceSize index_offset{};
        vk::IndexType index_type{};
    };

    struct DrawBatchState {
        PipelineInfo pipeline;
        u64 texture_hash;
        u64 framebuffer_hash;
        Common::Rectangle<u32> viewport;
        Common::Rectangle<u32> scissor;

        bool operator==(const DrawBatchState& o) const {
            return pipeline == o.pipeline && texture_hash == o.texture_hash &&
                   framebuffer_hash == o.framebuffer_hash && viewport == o.viewport &&
                   scissor == o.scissor;
        }
    };

    void FlushDrawBatch();
    u64 GetFramebufferHash() const;
    u64 GetTextureHash() const;

    std::vector<DrawBatchEntry> draw_batch;
    DrawBatchState current_batch_state;
    bool batch_active = false;
    static constexpr size_t MAX_BATCH_SIZE = 128;

    // Current Vulkan resource bindings (updated when descriptor sets are written)
    std::array<TextureBindingState, 3> current_textures{};
    const Framebuffer* current_framebuffer = nullptr;

    Common::Rectangle<s32> viewport;
    Common::Rectangle<s32> scissor;

    // Deferred index buffer state
    vk::Buffer last_bound_index_buffer;
    vk::DeviceSize last_bound_index_offset;
    vk::IndexType last_bound_index_type;

    // -------------------- Existing members --------------------
    const Instance& instance;
    Scheduler& scheduler;
    RenderManager& renderpass_cache;
    DescriptorUpdateQueue& update_queue;
    PipelineCache pipeline_cache;
    TextureRuntime runtime;
    RasterizerCache res_cache;

    VertexLayout software_layout;
    std::array<u32, 16> binding_offsets{};
    std::array<bool, 16> enable_attributes{};
    std::array<vk::Buffer, 16> vertex_buffers;
    VertexArrayInfo vertex_info;
    PipelineInfo pipeline_info{};

    StreamBuffer stream_buffer;     ///< Vertex+Index buffer
    StreamBuffer uniform_buffer;    ///< Uniform buffer
    StreamBuffer texture_buffer;    ///< Texture buffer
    StreamBuffer texture_lf_buffer; ///< Texture Light-Fog buffer
    vk::UniqueBufferView texture_lf_view;
    vk::UniqueBufferView texture_rg_view;
    vk::UniqueBufferView texture_rgba_view;
    vk::DeviceSize uniform_buffer_alignment;
    u32 uniform_size_aligned_vs_pica;
    u32 uniform_size_aligned_vs;
    u32 uniform_size_aligned_fs;
    bool async_shaders{false};
};

} // namespace Vulkan
