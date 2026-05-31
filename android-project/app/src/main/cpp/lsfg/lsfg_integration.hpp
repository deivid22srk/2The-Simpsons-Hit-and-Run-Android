#pragma once

#include <vulkan/vulkan_core.h>
#include <android/hardware_buffer.h>
#include <jni.h>

#include <cstdint>
#include <string>
#include <functional>

namespace lsfg {

// Error codes
constexpr int kOk = 0;
constexpr int kErrNoVulkan = -1;
constexpr int kErrExtensionsMissing = -2;
constexpr int kErrDeviceInit = -3;
constexpr int kErrShaderLoad = -4;
constexpr int kErrContextCreate = -5;
constexpr int kErrNotInitialized = -6;
constexpr int kErrDllUnreadable = -10;

// Config for LSFG initialization
struct LsfgConfig {
    uint32_t width = 0;
    uint32_t height = 0;
    int multiplier = 2;
    float flowScale = 0.5f;
    bool performance = false;
    bool hdr = false;
    bool antiArtifacts = true;
    bool framegenFp16 = false;
    std::string shaderCacheDir;
};

// Initialize LSFG system. Creates Vulkan instance + device, loads shaders.
// Returns 0 on success, negative error code on failure.
int initLsfg(const LsfgConfig& config);

// Set the DLL path for shader extraction. Shaders are extracted from
// the Windows Lossless.dll and translated to SPIR-V.
int setDllPath(const std::string& dllPath, const std::string& cacheDir);

// Enable/disable frame generation.
void setFrameGenEnabled(bool enabled);
bool isFrameGenEnabled();

// Check if frame generation is currently active and running.
bool isFrameGenActive();

// Push a captured frame (AHardwareBuffer) to the LSFG pipeline.
void pushFrame(AHardwareBuffer* ahb, int64_t timestampNs);

// Set the output surface for displaying generated frames.
void setOutputSurface(ANativeWindow* window, uint32_t w, uint32_t h);

// Get statistics
uint64_t getGeneratedFrameCount();
uint64_t getPostedFrameCount();

// Toggle bypass (compare original vs generated)
void setBypass(bool bypass);

// Shutdown LSFG system
void shutdownLsfg();

} // namespace lsfg
