#include "lsfg_integration.hpp"

#include "framegen/public/lsfg_3_1.hpp"
#include "framegen/public/lsfg_3_1p.hpp"

#include <android/hardware_buffer_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <vulkan/vulkan.h>

#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include <map>
#include <thread>

#include <android/log.h>
#define LOG_TAG "LSFG-Integration"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace lsfg {

// ── Forward declarations of Vulkan helper functions ──────────────────
namespace vk {

VkInstance createInstance();
VkPhysicalDevice selectPhysicalDevice(VkInstance instance);
VkDevice createDevice(VkInstance instance, VkPhysicalDevice physDev,
                      uint32_t* outQueueFamily, VkQueue* outQueue);
bool findComputeQueue(VkPhysicalDevice physDev, uint32_t* outFamily);
uint64_t getDeviceUUID(VkPhysicalDevice physDev);
bool checkExtensionSupport(VkPhysicalDevice physDev, const char* extName);

} // namespace vk

// ── Internal state ───────────────────────────────────────────────────
static struct {
    std::mutex mutex;
    
    // Vulkan objects
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    uint32_t computeQueueFamily = 0;
    VkQueue computeQueue = VK_NULL_HANDLE;
    uint64_t deviceUUID = 0;
    
    // Framegen state
    bool framegenInitialized = false;
    bool framegenEnabled = false;
    bool bypass = false;
    int activeContextId = -1;
    
    // Shader cache
    std::string cacheDir;
    
    // Output surface
    ANativeWindow* outputWindow = nullptr;
    uint32_t outputWidth = 0;
    uint32_t outputHeight = 0;
    
    // Frame generation pipeline AHBs
    AHardwareBuffer* inputAhb0 = nullptr;
    AHardwareBuffer* inputAhb1 = nullptr;
    std::vector<AHardwareBuffer*> outputAhbs;
    
    // Statistics
    std::atomic<uint64_t> generatedFrameCount{0};
    std::atomic<uint64_t> postedFrameCount{0};
    
    // Ping-pong frame tracking
    int currentInputSlot = 0;
} g;

// ── Shader loader callback ───────────────────────────────────────────
static std::vector<uint8_t> loadShaderFromCache(const std::string& name) {
    if (g.cacheDir.empty()) return {};
    
    std::string path = g.cacheDir + "/" + name + ".spv";
    FILE* f = fopen(path.c_str(), "rb");
    if (!f) {
        // Try FP16 subdirectory
        path = g.cacheDir + "/fp16/" + name + ".spv";
        f = fopen(path.c_str(), "rb");
        if (!f) return {};
    }
    
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    
    std::vector<uint8_t> data(size);
    fread(data.data(), 1, size, f);
    fclose(f);
    return data;
}

// ── AHardwareBuffer helpers ──────────────────────────────────────────
static bool createAhbInputs(uint32_t width, uint32_t height) {
    AHardwareBuffer_Desc desc = {};
    desc.width = width;
    desc.height = height;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_OUTPUT |
                 AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN |
                 AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN;
    desc.stride = 0;
    
    if (AHardwareBuffer_allocate(&desc, &g.inputAhb0) != 0) return false;
    if (AHardwareBuffer_allocate(&desc, &g.inputAhb1) != 0) {
        AHardwareBuffer_release(g.inputAhb0);
        g.inputAhb0 = nullptr;
        return false;
    }
    return true;
}

static bool createAhbOutputs(uint32_t width, uint32_t height, int count) {
    AHardwareBuffer_Desc desc = {};
    desc.width = width;
    desc.height = height;
    desc.layers = 1;
    desc.format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
    desc.usage = AHARDWAREBUFFER_USAGE_GPU_SAMPLED_OUTPUT |
                 AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN;
    desc.stride = 0;
    
    for (int i = 0; i < count; i++) {
        AHardwareBuffer* buf = nullptr;
        if (AHardwareBuffer_allocate(&desc, &buf) != 0) {
            for (auto* out : g.outputAhbs) AHardwareBuffer_release(out);
            g.outputAhbs.clear();
            return false;
        }
        g.outputAhbs.push_back(buf);
    }
    return true;
}

static void destroyAhbResources() {
    if (g.inputAhb0) { AHardwareBuffer_release(g.inputAhb0); g.inputAhb0 = nullptr; }
    if (g.inputAhb1) { AHardwareBuffer_release(g.inputAhb1); g.inputAhb1 = nullptr; }
    for (auto* out : g.outputAhbs) AHardwareBuffer_release(out);
    g.outputAhbs.clear();
}

// ── Public API ───────────────────────────────────────────────────────

int initLsfg(const LsfgConfig& config) {
    std::lock_guard<std::mutex> lock(g.mutex);
    
    if (g.framegenInitialized) {
        LOGI("LSFG already initialized, skipping");
        return kOk;
    }
    
    g.cacheDir = config.shaderCacheDir;
    
    // 1. Create Vulkan instance
    g.instance = vk::createInstance();
    if (g.instance == VK_NULL_HANDLE) {
        LOGE("Failed to create Vulkan instance");
        return kErrNoVulkan;
    }
    
    // 2. Select physical device
    g.physicalDevice = vk::selectPhysicalDevice(g.instance);
    if (g.physicalDevice == VK_NULL_HANDLE) {
        LOGE("No suitable Vulkan physical device found");
        vkDestroyInstance(g.instance, nullptr);
        g.instance = VK_NULL_HANDLE;
        return kErrNoVulkan;
    }
    
    // 3. Create logical device with compute queue
    g.device = vk::createDevice(g.instance, g.physicalDevice,
                                 &g.computeQueueFamily, &g.computeQueue);
    if (g.device == VK_NULL_HANDLE) {
        LOGE("Failed to create Vulkan device");
        vkDestroyInstance(g.instance, nullptr);
        g.instance = VK_NULL_HANDLE;
        return kErrDeviceInit;
    }
    
    // 4. Get device UUID
    g.deviceUUID = vk::getDeviceUUID(g.physicalDevice);
    
    // 5. Allocate AHB resources
    if (!createAhbInputs(config.width, config.height)) {
        LOGE("Failed to allocate AHB input buffers");
        vkDestroyDevice(g.device, nullptr);
        vkDestroyInstance(g.instance, nullptr);
        g.device = VK_NULL_HANDLE;
        g.instance = VK_NULL_HANDLE;
        return kErrContextCreate;
    }
    
    if (!createAhbOutputs(config.width, config.height, config.multiplier - 1)) {
        LOGE("Failed to allocate AHB output buffers");
        destroyAhbResources();
        vkDestroyDevice(g.device, nullptr);
        vkDestroyInstance(g.instance, nullptr);
        g.device = VK_NULL_HANDLE;
        g.instance = VK_NULL_HANDLE;
        return kErrContextCreate;
    }
    
    // 6. Initialize framegen library
    try {
        if (config.performance) {
            LSFG_3_1P::initialize(
                g.deviceUUID, config.hdr, config.flowScale,
                config.multiplier, loadShaderFromCache);
        } else {
            LSFG_3_1::initialize(
                g.deviceUUID, config.hdr, config.flowScale,
                config.multiplier, loadShaderFromCache);
        }
    } catch (const std::exception& e) {
        LOGE("Framegen initialize failed: %s", e.what());
        destroyAhbResources();
        vkDestroyDevice(g.device, nullptr);
        vkDestroyInstance(g.instance, nullptr);
        g.device = VK_NULL_HANDLE;
        g.instance = VK_NULL_HANDLE;
        return kErrShaderLoad;
    }
    
    // 7. Create framegen context from AHB
    try {
        VkExtent2D extent = { config.width, config.height };
        // Use AHB-based context creation (Android path)
        if (config.performance) {
            g.activeContextId = LSFG_3_1P::createContextFromAHB(
                g.inputAhb0, g.inputAhb1, g.outputAhbs,
                extent, VK_FORMAT_R8G8B8A8_UNORM);
        } else {
            g.activeContextId = LSFG_3_1::createContextFromAHB(
                g.inputAhb0, g.inputAhb1, g.outputAhbs,
                extent, VK_FORMAT_R8G8B8A8_UNORM);
        }
    } catch (const std::exception& e) {
        LOGE("Framegen createContext failed: %s", e.what());
        try {
            if (config.performance) LSFG_3_1P::finalize();
            else LSFG_3_1::finalize();
        } catch (...) {}
        destroyAhbResources();
        vkDestroyDevice(g.device, nullptr);
        vkDestroyInstance(g.instance, nullptr);
        g.device = VK_NULL_HANDLE;
        g.instance = VK_NULL_HANDLE;
        return kErrContextCreate;
    }
    
    g.framegenInitialized = true;
    g.framegenEnabled = true;
    LOGI("LSFG initialized successfully (multiplier=%d, perf=%d)",
         config.multiplier, config.performance);
    
    return kOk;
}

int setDllPath(const std::string& dllPath, const std::string& cacheDir) {
    // Check if DLL exists
    FILE* f = fopen(dllPath.c_str(), "rb");
    if (!f) return kErrDllUnreadable;
    fclose(f);
    
    // Store the cache directory for shader loading
    g.cacheDir = cacheDir;
    LOGI("DLL path set: %s", dllPath.c_str());
    LOGI("Shader cache dir: %s", cacheDir.c_str());
    
    return kOk;
}

void setFrameGenEnabled(bool enabled) {
    g.framegenEnabled = enabled;
    LOGI("Frame generation %s", enabled ? "enabled" : "disabled");
}

bool isFrameGenEnabled() {
    return g.framegenEnabled;
}

bool isFrameGenActive() {
    return g.framegenInitialized && g.framegenEnabled && !g.bypass;
}

void pushFrame(AHardwareBuffer* ahb, int64_t timestampNs) {
    if (!g.framegenInitialized || !g.framegenEnabled || g.bypass) {
        return;
    }
    
    std::lock_guard<std::mutex> lock(g.mutex);
    
    // Copy the captured frame into the current input AHB slot
    AHardwareBuffer* src = ahb;
    AHardwareBuffer* dst = (g.currentInputSlot == 0) ? g.inputAhb0 : g.inputAhb1;
    
    // AHardwareBuffer copy via CPU (simplified - production would use
    // Vulkan compute or vendor blit engine for performance)
    void* dstPtr = nullptr;
    void* srcPtr = nullptr;
    
    if (AHardwareBuffer_lock(dst, AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN, -1, nullptr, &dstPtr) == 0 &&
        AHardwareBuffer_lock(src, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, nullptr, &srcPtr) == 0) {
        
        AHardwareBuffer_Desc dstDesc, srcDesc;
        AHardwareBuffer_describe(dst, &dstDesc);
        AHardwareBuffer_describe(src, &srcDesc);
        
        size_t rowSize = dstDesc.width * 4; // RGBA8
        size_t copyHeight = (dstDesc.height < srcDesc.height) ? dstDesc.height : srcDesc.height;
        
        for (size_t y = 0; y < copyHeight; y++) {
            memcpy(
                (uint8_t*)dstPtr + y * dstDesc.stride * 4,
                (uint8_t*)srcPtr + y * srcDesc.stride * 4,
                rowSize
            );
        }
        
        AHardwareBuffer_unlock(dst, nullptr);
        AHardwareBuffer_unlock(src, nullptr);
    }
    
    // Swap input slot
    g.currentInputSlot = 1 - g.currentInputSlot;
    
    // Run framegen
    try {
        // Wait for any previous work
        if (g.activeContextId >= 0) {
            LSFG_3_1::presentContext(g.activeContextId, -1, {});
        }
        g.generatedFrameCount += 1;
    } catch (const std::exception& e) {
        LOGE("Framegen presentContext failed: %s", e.what());
    }
}

void setOutputSurface(ANativeWindow* window, uint32_t w, uint32_t h) {
    std::lock_guard<std::mutex> lock(g.mutex);
    if (g.outputWindow) {
        ANativeWindow_release(g.outputWindow);
    }
    g.outputWindow = window;
    g.outputWidth = w;
    g.outputHeight = h;
    if (window) {
        ANativeWindow_acquire(window);
    }
}

uint64_t getGeneratedFrameCount() {
    return g.generatedFrameCount.load();
}

uint64_t getPostedFrameCount() {
    return g.postedFrameCount.load();
}

void setBypass(bool bypass) {
    g.bypass = bypass;
    LOGI("LSFG bypass %s", bypass ? "enabled" : "disabled");
}

void shutdownLsfg() {
    std::lock_guard<std::mutex> lock(g.mutex);
    
    if (g.activeContextId >= 0) {
        try {
            LSFG_3_1::deleteContext(g.activeContextId);
        } catch (...) {}
        g.activeContextId = -1;
    }
    
    try {
        LSFG_3_1::finalize();
    } catch (...) {}
    
    destroyAhbResources();
    
    if (g.device != VK_NULL_HANDLE) {
        vkDestroyDevice(g.device, nullptr);
        g.device = VK_NULL_HANDLE;
    }
    if (g.instance != VK_NULL_HANDLE) {
        vkDestroyInstance(g.instance, nullptr);
        g.instance = VK_NULL_HANDLE;
    }
    if (g.outputWindow) {
        ANativeWindow_release(g.outputWindow);
        g.outputWindow = nullptr;
    }
    
    g.framegenInitialized = false;
    g.framegenEnabled = false;
    g.generatedFrameCount = 0;
    g.postedFrameCount = 0;
    
    LOGI("LSFG shutdown complete");
}

// ── Vulkan helper implementations ────────────────────────────────────
namespace vk {

VkInstance createInstance() {
    VkApplicationInfo appInfo = {};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Simpsons H&R LSFG";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "LSFG Integration";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_3;
    
    const char* extensions[] = {
        VK_KHR_EXTERNAL_MEMORY_CAPABILITIES_EXTENSION_NAME,
        VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME,
    };
    
    VkInstanceCreateInfo createInfo = {};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = 2;
    createInfo.ppEnabledExtensionNames = extensions;
    
    VkInstance instance;
    VkResult res = vkCreateInstance(&createInfo, nullptr, &instance);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateInstance failed: %d", res);
        return VK_NULL_HANDLE;
    }
    return instance;
}

bool findComputeQueue(VkPhysicalDevice physDev, uint32_t* outFamily) {
    uint32_t count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(physDev, &count, nullptr);
    std::vector<VkQueueFamilyProperties> families(count);
    vkGetPhysicalDeviceQueueFamilyProperties(physDev, &count, families.data());
    
    for (uint32_t i = 0; i < count; i++) {
        if (families[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
            *outFamily = i;
            return true;
        }
    }
    return false;
}

VkPhysicalDevice selectPhysicalDevice(VkInstance instance) {
    uint32_t count = 0;
    vkEnumeratePhysicalDevices(instance, &count, nullptr);
    if (count == 0) return VK_NULL_HANDLE;
    
    std::vector<VkPhysicalDevice> devices(count);
    vkEnumeratePhysicalDevices(instance, &count, devices.data());
    
    // Return first device with compute queue
    for (auto dev : devices) {
        uint32_t family;
        if (findComputeQueue(dev, &family)) {
            VkPhysicalDeviceProperties props;
            vkGetPhysicalDeviceProperties(dev, &props);
            LOGI("Selected GPU: %s", props.deviceName);
            return dev;
        }
    }
    return devices[0];
}

uint64_t getDeviceUUID(VkPhysicalDevice physDev) {
    VkPhysicalDeviceProperties2 props2 = {};
    props2.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2;
    
    VkPhysicalDeviceIDProperties idProps = {};
    idProps.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ID_PROPERTIES;
    props2.pNext = &idProps;
    
    vkGetPhysicalDeviceProperties2(physDev, &props2);
    
    // Use first 8 bytes of device UUID as uint64_t
    uint64_t uuid = 0;
    memcpy(&uuid, idProps.deviceUUID, sizeof(uuid));
    return uuid;
}

bool checkExtensionSupport(VkPhysicalDevice physDev, const char* extName) {
    uint32_t count = 0;
    vkEnumerateDeviceExtensionProperties(physDev, nullptr, &count, nullptr);
    std::vector<VkExtensionProperties> extensions(count);
    vkEnumerateDeviceExtensionProperties(physDev, nullptr, &count, extensions.data());
    
    for (const auto& ext : extensions) {
        if (strcmp(ext.extensionName, extName) == 0) return true;
    }
    return false;
}

VkDevice createDevice(VkInstance instance, VkPhysicalDevice physDev,
                       uint32_t* outQueueFamily, VkQueue* outQueue) {
    if (!findComputeQueue(physDev, outQueueFamily)) {
        LOGE("No compute queue family found");
        return VK_NULL_HANDLE;
    }
    
    float queuePriority = 1.0f;
    VkDeviceQueueCreateInfo queueCreateInfo = {};
    queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueCreateInfo.queueFamilyIndex = *outQueueFamily;
    queueCreateInfo.queueCount = 1;
    queueCreateInfo.pQueuePriorities = &queuePriority;
    
    // Required device extensions
    std::vector<const char*> deviceExtensions;
    
    // Check for AHB external memory support (Android)
    if (checkExtensionSupport(physDev, VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME)) {
        deviceExtensions.push_back(VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME);
    }
    // Fallback to FD-based external memory
    if (checkExtensionSupport(physDev, VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME)) {
        deviceExtensions.push_back(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME);
    }
    // Required for framegen
    if (checkExtensionSupport(physDev, VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME)) {
        deviceExtensions.push_back(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME);
    }
    if (checkExtensionSupport(physDev, VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME)) {
        deviceExtensions.push_back(VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME);
    }
    
    // Vulkan 1.3 features
    VkPhysicalDeviceVulkan13Features features13 = {};
    features13.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES;
    features13.synchronization2 = VK_TRUE;
    features13.dynamicRendering = VK_TRUE;
    
    VkDeviceCreateInfo createInfo = {};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.pNext = &features13;
    createInfo.queueCreateInfoCount = 1;
    createInfo.pQueueCreateInfos = &queueCreateInfo;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(deviceExtensions.size());
    createInfo.ppEnabledExtensionNames = deviceExtensions.data();
    createInfo.enabledLayerCount = 0;
    
    VkDevice device;
    VkResult res = vkCreateDevice(physDev, &createInfo, nullptr, &device);
    if (res != VK_SUCCESS) {
        LOGE("vkCreateDevice failed: %d", res);
        return VK_NULL_HANDLE;
    }
    
    vkGetDeviceQueue(device, *outQueueFamily, 0, outQueue);
    return device;
}

} // namespace vk
} // namespace lsfg
