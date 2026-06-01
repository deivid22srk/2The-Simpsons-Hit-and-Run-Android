#include "lsfg_integration.hpp"

#include <android/hardware_buffer_jni.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include <string>

namespace {

std::string jstringToStd(JNIEnv* env, jstring s) {
    if (s == nullptr) return {};
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

} // namespace

// ── JNI: com.c4rlox.simpsons.LsfgBridge ──────────────────────────────

extern "C" JNIEXPORT jstring JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeVersion(JNIEnv* env, jobject /*thiz*/) {
    return env->NewStringUTF("lsfg-integration 1.0.0");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeInit(
    JNIEnv* env, jobject /*thiz*/,
    jstring cacheDir, jint width, jint height,
    jint multiplier, jfloat flowScale,
    jboolean performance, jboolean hdr, jboolean antiArtifacts) {
    
    const std::string cache = jstringToStd(env, cacheDir);
    if (cache.empty() || width <= 0 || height <= 0) {
        return lsfg::kErrDllUnreadable;
    }
    
    lsfg::LsfgConfig config;
    config.width = static_cast<uint32_t>(width);
    config.height = static_cast<uint32_t>(height);
    config.multiplier = static_cast<int>(multiplier);
    config.flowScale = static_cast<float>(flowScale);
    config.performance = performance == JNI_TRUE;
    config.hdr = hdr == JNI_TRUE;
    config.antiArtifacts = antiArtifacts == JNI_TRUE;
    config.shaderCacheDir = cache;
    
    return lsfg::initLsfg(config);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeSetDllPath(
    JNIEnv* env, jobject /*thiz*/,
    jstring dllPath, jstring cacheDir) {
    return lsfg::setDllPath(
        jstringToStd(env, dllPath),
        jstringToStd(env, cacheDir));
}

extern "C" JNIEXPORT void JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeSetFrameGenEnabled(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean enabled) {
    lsfg::setFrameGenEnabled(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeIsFrameGenEnabled(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    return lsfg::isFrameGenEnabled() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeIsFrameGenActive(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    return lsfg::isFrameGenActive() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeSetOutputSurface(
    JNIEnv* env, jobject /*thiz*/, jobject surface, jint w, jint h) {
    ANativeWindow* win = (surface != nullptr)
        ? ANativeWindow_fromSurface(env, surface)
        : nullptr;
    lsfg::setOutputSurface(win, static_cast<uint32_t>(w), static_cast<uint32_t>(h));
    if (win != nullptr) {
        ANativeWindow_release(win);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativePushFrame(
    JNIEnv* env, jobject /*thiz*/, jobject hardwareBuffer, jlong timestampNs) {
    if (hardwareBuffer == nullptr) return;
    AHardwareBuffer* ahb = AHardwareBuffer_fromHardwareBuffer(env, hardwareBuffer);
    lsfg::pushFrame(ahb, static_cast<int64_t>(timestampNs));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeGetGeneratedFrameCount(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jlong>(lsfg::getGeneratedFrameCount());
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeGetPostedFrameCount(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jlong>(lsfg::getPostedFrameCount());
}

extern "C" JNIEXPORT void JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeSetBypass(
    JNIEnv* /*env*/, jobject /*thiz*/, jboolean bypass) {
    lsfg::setBypass(bypass == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_c4rlox_simpsons_LsfgBridge_nativeShutdown(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    lsfg::shutdownLsfg();
}
