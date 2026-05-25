#include <main/fpscounter.h>

#ifdef RAD_ANDROID
  #include <jni.h>
  #include <android/log.h>
  #define FPS_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "FPSCounter", __VA_ARGS__)
  #include <worldsim/avatarmanager.h>
  #include <worldsim/character/character.h>
  #include <ai/actionbuttonhandler.h>
  #include <presentation/gui/guisystem.h>
  #include <presentation/gui/guimanager.h>
  #include <presentation/gui/guiwindow.h>
#else
  #include <cstdio>
  #define FPS_LOGI(...) do { std::printf("[FPSCounter] "); std::printf(__VA_ARGS__); std::printf("\n"); std::fflush(stdout); } while(0)
#endif

// ── EWMA smoothing factor ─────────────────────────────────────────────
// Lower = smoother but slower to react.  0.05 is a good balance.
const float FPSCounter::SMOOTHING = 0.05f;

// ── Global pointer (set during Game::Initialize, accessible from JNI) ──
FPSCounter* g_FPSCounter = NULL;

// ── Constructor ────────────────────────────────────────────────────────

FPSCounter::FPSCounter()
    : mLastTime(0)
    , mSmoothedFPS(0.0f)
    , mInstantFPS(0.0f)
    , mFrameCount(0)
{
}

// ── Update (called every frame) ────────────────────────────────────────

float FPSCounter::Update()
{
    const radTime64 now = radTimeGetMicroseconds64();

    if (mLastTime == 0) {
        // First frame — no delta yet.
        mLastTime = now;
        ++mFrameCount;
        return 0.0f;
    }

    const radTime64 deltaUs = now - mLastTime;
    mLastTime = now;

    if (deltaUs > 0) {
        mInstantFPS = 1000000.0f / static_cast<float>(deltaUs);
    }

    // EWMA: smoothed = alpha * new + (1 - alpha) * old
    if (mSmoothedFPS == 0.0f) {
        mSmoothedFPS = mInstantFPS;
    } else {
        mSmoothedFPS = SMOOTHING * mInstantFPS + (1.0f - SMOOTHING) * mSmoothedFPS;
    }

    ++mFrameCount;
    return mSmoothedFPS;
}

// ═══════════════════════════════════════════════════════════════════════════
//  JNI bridge — exposes smoothed FPS to Java
// ═══════════════════════════════════════════════════════════════════════════

#ifdef RAD_ANDROID

extern "C" JNIEXPORT jfloat JNICALL
Java_com_c4rlox_simpsons_SimpsonsActivity_nativeGetFPS(JNIEnv* /*env*/, jclass /*clazz*/)
{
    if (g_FPSCounter) {
        return static_cast<jfloat>(g_FPSCounter->GetFPS());
    }
    return 0.0f;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_c4rlox_simpsons_SimpsonsActivity_nativeGetHudContext(JNIEnv* /*env*/, jclass /*clazz*/)
{
    AvatarManager* am = GetAvatarManager();
    if (am) {
        Avatar* avatar = am->GetAvatarForPlayer(0);
        if (avatar) {
            if (avatar->IsInCar()) {
                return 2; // Inside Car
            }
            Character* character = avatar->GetCharacter();
            if (character) {
                ActionButton::ButtonHandler* handler = character->GetActionButtonHandler();
                if (handler) {
                    ActionButton::ButtonHandler::Type type = handler->GetType();
                    if (type == ActionButton::ButtonHandler::GET_IN_CAR || type == ActionButton::ButtonHandler::GET_IN_USER_CAR) {
                        return 1; // Near Car
                    }
                }
            }
        }
    }
    return 0; // On Foot (Normal)
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_c4rlox_simpsons_SimpsonsActivity_nativeIsTitleScreen(JNIEnv* /*env*/, jclass /*clazz*/)
{
    CGuiSystem* gs = GetGuiSystem();
    if (gs) {
        CGuiManager* gm = gs->GetCurrentManager();
        if (gm) {
            if (gm->GetCurrentScreen() == CGuiWindow::GUI_SCREEN_ID_SPLASH) {
                return JNI_TRUE;
            }
        }
    }
    return JNI_FALSE;
}

#endif // RAD_ANDROID
