// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>
#include "common/settings.h"
#include "video_core/renderer_vulkan/vk_shader_util.h"

namespace Vulkan {

/**
 * Parallel Shader Compilation
 *
 * 3DS games can trigger hundreds of shader compilations during gameplay,
 * causing stuttering. By compiling shaders on multiple threads, we can
 * reduce this stuttering significantly.
 */
class ParallelShaderCompiler {
public:
    ParallelShaderCompiler() {
        const bool enabled = Settings::values.parallel_shader_compilation.GetValue();
        if (!enabled) {
            return;
        }

        // Use half of available CPU cores for shader compilation
        const u32 thread_count = std::max(1u, std::thread::hardware_concurrency() / 2);

        for (u32 i = 0; i < thread_count; ++i) {
            workers.emplace_back([this] { WorkerThread(); });
        }

        LOG_INFO(Render_Vulkan, "Parallel shader compilation enabled with {} threads", thread_count);
    }

    ~ParallelShaderCompiler() {
        {
            std::lock_guard lock(queue_mutex);
            stop = true;
        }
        condition.notify_all();

        for (auto& worker : workers) {
            if (worker.joinable()) {
                worker.join();
            }
        }
    }

private:
    void WorkerThread() {
        while (true) {
            std::unique_lock lock(queue_mutex);
            condition.wait(lock, [this] { return stop || !task_queue.empty(); });

            if (stop && task_queue.empty()) {
                break;
            }

            if (!task_queue.empty()) {
                auto task = std::move(task_queue.front());
                task_queue.pop();
                lock.unlock();

                // Execute shader compilation task
                task();
            }
        }
    }

    std::vector<std::thread> workers;
    std::queue<std::function<void()>> task_queue;
    std::mutex queue_mutex;
    std::condition_variable condition;
    bool stop = false;
};

/**
 * Async Texture Loading
 *
 * Loading textures synchronously on the render thread causes frame drops.
 * By loading textures asynchronously in background threads, we can maintain
 * smooth frame rates even when new textures are being loaded.
 */
class AsyncTextureLoader {
public:
    AsyncTextureLoader() {
        const bool enabled = Settings::values.async_texture_loading.GetValue();
        if (!enabled) {
            return;
        }

        // Use 2 threads for texture loading
        for (u32 i = 0; i < 2; ++i) {
            workers.emplace_back([this] { WorkerThread(); });
        }

        LOG_INFO(Render_Vulkan, "Async texture loading enabled with 2 threads");
    }

    ~AsyncTextureLoader() {
        {
            std::lock_guard lock(queue_mutex);
            stop = true;
        }
        condition.notify_all();

        for (auto& worker : workers) {
            if (worker.joinable()) {
                worker.join();
            }
        }
    }

private:
    void WorkerThread() {
        while (true) {
            std::unique_lock lock(queue_mutex);
            condition.wait(lock, [this] { return stop || !task_queue.empty(); });

            if (stop && task_queue.empty()) {
                break;
            }

            if (!task_queue.empty()) {
                auto task = std::move(task_queue.front());
                task_queue.pop();
                lock.unlock();

                // Execute texture loading task
                task();
            }
        }
    }

    std::vector<std::thread> workers;
    std::queue<std::function<void()>> task_queue;
    std::mutex queue_mutex;
    std::condition_variable condition;
    bool stop = false;
};

} // namespace Vulkan
