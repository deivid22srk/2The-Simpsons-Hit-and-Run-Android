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
  #include <input/inputmanager.h>
  #include <pddi/pddi.hpp>
  #include <p3d/utility.hpp>
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
    CGuiSystem* gs = GetGuiSystem();
    if (gs) {
        CGuiManager* gm = gs->GetCurrentManager();
        if (gm) {
            if (gm->GetCurrentScreen() == CGuiWindow::GUI_SCREEN_ID_LETTER_BOX) {
                return 4; // Cutscene
            }
        }
    }

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
                    // Check for Talk classes first (they return GAG from GetType())
                    if (dynamic_cast<ActionButton::TalkFood*>(handler) ||
                        dynamic_cast<ActionButton::TalkCollectible*>(handler) ||
                        dynamic_cast<ActionButton::TalkDialog*>(handler) ||
                        dynamic_cast<ActionButton::TalkMission*>(handler)) {
                        return 5; // Near Talkable Character
                    }

                    ActionButton::ButtonHandler::Type type = handler->GetType();
                    switch (type) {
                        case ActionButton::ButtonHandler::GET_IN_CAR:
                        case ActionButton::ButtonHandler::GET_IN_USER_CAR:
                            return 1; // Near Car
                        case ActionButton::ButtonHandler::INTERIOR:
                            return 3; // Near Interior (House door)
                        case ActionButton::ButtonHandler::MISSION_OBJECTIVE:
                            return 6; // Near Mission Objective
                        case ActionButton::ButtonHandler::SUMMON_PHONE:
                            return 7; // Near Phone (summon vehicle)
                        case ActionButton::ButtonHandler::GAG:
                            return 8; // Near Gag/Prank object
                        case ActionButton::ButtonHandler::PURCHASE_CAR:
                            return 9; // Near Car Purchase
                        case ActionButton::ButtonHandler::PURCHASE_SKIN:
                            return 10; // Near Skin Purchase
                        case ActionButton::ButtonHandler::COLLECTOR_CARD:
                            return 11; // Near Collector Card
                        case ActionButton::ButtonHandler::WRENCH_ICON:
                            return 13; // Near Wrench (repair)
                        case ActionButton::ButtonHandler::NITRO_ICON:
                            return 14; // Near Nitro pickup
                        case ActionButton::ButtonHandler::TELEPORT:
                            return 15; // Near Teleport
                        default:
                            return 16; // Other Action (UseVendingMachine, Doorbell, etc.)
                    }
                }
                else {
                    return 0; // On Foot (No action available)
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

extern "C" JNIEXPORT void JNICALL
Java_com_c4rlox_simpsons_SimpsonsActivity_nativeSetRumbleEnabled(JNIEnv* /*env*/, jclass /*clazz*/, jboolean enabled)
{
    InputManager* im = GetInputManager();
    if (im) {
        im->SetRumbleEnabled(enabled == JNI_TRUE);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_c4rlox_simpsons_SimpsonsActivity_nativeIsRumbleEnabled(JNIEnv* /*env*/, jclass /*clazz*/)
{
    InputManager* im = GetInputManager();
    if (im) {
        return im->IsRumbleEnabled() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_c4rlox_simpsons_SimpsonsActivity_nativeSetFPSCap(JNIEnv* /*env*/, jclass /*clazz*/, jint fps)
{
    if (p3d::display) {
        p3d::display->SetTargetFps(fps);
        p3d::display->SetLsfgEnabled(fps > 0);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_c4rlox_simpsons_SimpsonsActivity_nativeSetLsfgEnabled(JNIEnv* /*env*/, jclass /*clazz*/, jboolean enabled)
{
    if (p3d::display) {
        p3d::display->SetLsfgEnabled(enabled == JNI_TRUE);
    }
}

#endif // RAD_ANDROID
