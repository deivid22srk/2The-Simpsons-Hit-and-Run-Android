//=============================================================================
// JNI Bridge for Java Touch Overlay
// Allows the Java TouchOverlay to query button/joystick states from TouchGui
//=============================================================================

#include <jni.h>
#include <input/touchGui.h>
#include <input/inputmanager.h>
#include <input/usercontroller.h>
#include <input/controller.h>
#include <android/log.h>

#define LOG_TAG "TouchJNIBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_c4rlox_simpsons_TouchOverlay
 * Method:    nativeIsButtonPressed
 * Signature: (I)Z
 */
JNIEXPORT jboolean JNICALL
Java_com_c4rlox_simpsons_TouchOverlay_nativeIsButtonPressed(
    JNIEnv* env, jclass clazz, jint buttonIndex)
{
    TouchGui* gui = TouchGui::GetInstance();
    if (gui == NULL || !gui->IsVisible()) return JNI_FALSE;
    return gui->IsButtonPressed((int)buttonIndex) ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_c4rlox_simpsons_TouchOverlay
 * Method:    nativeGetJoystickX
 * Signature: (I)F
 */
JNIEXPORT jfloat JNICALL
Java_com_c4rlox_simpsons_TouchOverlay_nativeGetJoystickX(
    JNIEnv* env, jclass clazz, jint stickIndex)
{
    TouchGui* gui = TouchGui::GetInstance();
    if (gui == NULL || !gui->IsVisible()) return 0.0f;
    return (jfloat)gui->GetJoystickX((int)stickIndex);
}

/*
 * Class:     com_c4rlox_simpsons_TouchOverlay
 * Method:    nativeGetJoystickY
 * Signature: (I)F
 */
JNIEXPORT jfloat JNICALL
Java_com_c4rlox_simpsons_TouchOverlay_nativeGetJoystickY(
    JNIEnv* env, jclass clazz, jint stickIndex)
{
    TouchGui* gui = TouchGui::GetInstance();
    if (gui == NULL || !gui->IsVisible()) return 0.0f;
    return (jfloat)gui->GetJoystickY((int)stickIndex);
}

/*
 * Class:     com_c4rlox_simpsons_TouchOverlay
 * Method:    nativeIsTouchGuiVisible
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_com_c4rlox_simpsons_TouchOverlay_nativeIsTouchGuiVisible(
    JNIEnv* env, jclass clazz)
{
    TouchGui* gui = TouchGui::GetInstance();
    if (gui == NULL) return JNI_FALSE;
    return gui->IsVisible() ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     com_c4rlox_simpsons_TouchOverlay
 * Method:    nativeIsPhysicalGamepadConnected
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_com_c4rlox_simpsons_TouchOverlay_nativeIsPhysicalGamepadConnected(
    JNIEnv* env, jclass clazz)
{
    InputManager* inputMgr = InputManager::GetInstance();
    if (inputMgr == NULL) return JNI_FALSE;

    for (unsigned int i = 0; i < InputManager::GetMaxControllers(); ++i) {
        UserController* ctrl = inputMgr->GetController(i);
        if (ctrl != NULL && ctrl->IsConnected()) {
            return JNI_TRUE;
        }
    }
    return JNI_FALSE;
}

#ifdef __cplusplus
}
#endif
