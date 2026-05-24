#include <input/touchGui.h>
#include <input/inputmanager.h>
#include <input/usercontroller.h>
#include <p3d/utility.hpp>
#include <pddi/pddi.hpp>
#include <main/game.h>
#include <math.h>

TouchGui* TouchGui::spInstance = NULL;

TouchGui* TouchGui::GetInstance() {
    return spInstance;
}

void TouchGui::CreateInstance() {
    if (spInstance == NULL) {
        spInstance = new TouchGui();
    }
}

void TouchGui::DestroyInstance() {
    delete spInstance;
    spInstance = NULL;
}

TouchGui::TouchGui() :
    mVisible(true),
    mScreenWidth(640.0f),
    mScreenHeight(480.0f)
{
    mLeftStick.fingerId = -1;
    mLeftStick.active = false;
    mLeftStick.lastEventTime = 0;
    mRightStick.fingerId = -1;
    mRightStick.active = false;
    mRightStick.lastEventTime = 0;

    for(int i = 0; i < NUM_BUTTONS; ++i) {
        mButtons[i].pressed = false;
        mButtons[i].fingerId = -1;
        mButtons[i].lastEventTime = 0;
    }
}

TouchGui::~TouchGui() {}

void TouchGui::Init() {
    // Define button positions (normalized 0.0 - 1.0)
    // Alinhado com GamepadOverlayView.java (botões maiores e mais espaçados)
    // Alinhado com GamepadOverlayView.java
    float dpadSize = 0.10f;
    float dpadX = 0.16f;
    float dpadY = 0.79f;

    mButtons[BTN_DPAD_UP]    = {dpadX, 0.70f, dpadSize, dpadSize, InputManager::DPadUp,    false, -1, "UP", 0};
    mButtons[BTN_DPAD_DOWN]  = {dpadX, 0.88f, dpadSize, dpadSize, InputManager::DPadDown,  false, -1, "DN", 0};
    mButtons[BTN_DPAD_LEFT]  = {0.07f, dpadY, dpadSize, dpadSize, InputManager::DPadLeft, false, -1, "LF", 0};
    mButtons[BTN_DPAD_RIGHT] = {0.25f, dpadY, dpadSize, dpadSize, InputManager::DPadRight,false, -1, "RT", 0};

    // Right side: Face buttons
    float faceX = 0.85f;
    float faceY = 0.76f;
    float faceSize = 0.10f;

    mButtons[BTN_A] = {faceX,     faceY + faceSize, faceSize, faceSize, InputManager::A, false, -1, "A", 0};
    mButtons[BTN_B] = {faceX + faceSize, faceY,    faceSize, faceSize, InputManager::B, false, -1, "B", 0};
    mButtons[BTN_X] = {faceX - faceSize, faceY,    faceSize, faceSize, InputManager::Square, false, -1, "X", 0};
    mButtons[BTN_Y] = {faceX,     faceY - faceSize, faceSize, faceSize, InputManager::Triangle, false, -1, "Y", 0};

    // Center/Top: Start, Select
    mButtons[BTN_START]  = {0.55f, 0.04f, 0.12f, 0.06f, InputManager::Start,  false, -1, "START", 0};
    mButtons[BTN_SELECT] = {0.43f, 0.04f, 0.12f, 0.06f, InputManager::Select, false, -1, "SELECT", 0};

    // Shoulder buttons
    mButtons[BTN_L1] = {0.14f, 0.04f, 0.14f, 0.08f, InputManager::AnalogL1, false, -1, "L1", 0};
    mButtons[BTN_R1] = {0.86f, 0.04f, 0.14f, 0.08f, InputManager::AnalogR1, false, -1, "R1", 0};

    // Joysticks (mais abaixo para evitar overlap com D-Pad / face buttons)
    mLeftStick.centerX = 0.16f;
    mLeftStick.centerY = 0.52f;
    mLeftStick.radius = 0.12f;
    mLeftStick.currX = 0.0f;
    mLeftStick.currY = 0.0f;
    mLeftStick.axisX = InputManager::LeftStickX;
    mLeftStick.axisY = InputManager::LeftStickY;
    mLeftStick.fingerId = -1;
    mLeftStick.active = false;

    mRightStick.centerX = 0.84f;
    mRightStick.centerY = 0.52f;
    mRightStick.radius = 0.12f;
    mRightStick.currX = 0.0f;
    mRightStick.currY = 0.0f;
    mRightStick.axisX = InputManager::RightStickX;
    mRightStick.axisY = InputManager::RightStickY;
    mRightStick.fingerId = -1;
    mRightStick.active = false;
}

void TouchGui::SetVisible(bool visible) {
    if (mVisible == visible) return;
    mVisible = visible;
    if (!visible) {
        // Reset all inputs when hiding
        UserController* controller = GetInputManager()->GetController(0);
        if (controller) {
            for(int i = 0; i < NUM_BUTTONS; ++i) {
                if (mButtons[i].pressed) {
                    mButtons[i].pressed = false;
                    mButtons[i].fingerId = -1;
                    mButtons[i].lastEventTime = 0;
                    controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(0.0f);
                }
            }
            controller->GetInputButton(mLeftStick.axisX)->SetValue(0.0f);
            controller->GetInputButton(mLeftStick.axisY)->SetValue(0.0f);
            controller->GetInputButton(mRightStick.axisX)->SetValue(0.0f);
            controller->GetInputButton(mRightStick.axisY)->SetValue(0.0f);
        }
        mLeftStick.active = false;
        mLeftStick.fingerId = -1;
        mRightStick.active = false;
        mRightStick.fingerId = -1;
    }
}

void TouchGui::UpdateJoystick(TouchJoystick& stick, float x, float y, bool down, SDL_FingerID fingerId) {
    UserController* controller = GetInputManager()->GetController(0);
    if (!controller) return;

    if (down) {
        float dx = x - stick.centerX;
        float dy = y - stick.centerY;
        float dist = sqrtf(dx*dx + dy*dy);

        if (stick.fingerId == fingerId || (stick.fingerId == -1 && dist < stick.radius)) {
            stick.fingerId = fingerId;
            stick.active = true;
            stick.lastEventTime = radTimeGetMicroseconds64();

            if (dist > stick.radius) {
                dx = (dx / dist) * stick.radius;
                dy = (dy / dist) * stick.radius;
            }

            stick.currX = dx / stick.radius;
            stick.currY = dy / stick.radius;

            // The game expects analog stick values in [-1, 1] with 0.0 as center.
            // Apply a generous deadzone so tiny movements near center register as 0.
            const float deadzone = 0.18f;
            float valX = stick.currX;
            float valY = -stick.currY; // Invert Y for game coordinates

            if (fabsf(valX) < deadzone) valX = 0.0f;
            else valX = (valX - (valX > 0.0f ? deadzone : -deadzone)) / (1.0f - deadzone);

            if (fabsf(valY) < deadzone) valY = 0.0f;
            else valY = (valY - (valY > 0.0f ? deadzone : -deadzone)) / (1.0f - deadzone);

            controller->GetInputButton(stick.axisX)->SetValue(valX);
            controller->GetInputButton(stick.axisY)->SetValue(valY);
        }
    } else if (stick.fingerId == fingerId || stick.active) {
        stick.active = false;
        stick.fingerId = -1;
        stick.currX = 0.0f;
        stick.currY = 0.0f;
        controller->GetInputButton(stick.axisX)->SetValue(0.0f);
        controller->GetInputButton(stick.axisY)->SetValue(0.0f);
    }
}

void TouchGui::HandleTouchEvent(SDL_Event* event) {
    if (!mVisible) return;

    float x = 0, y = 0;
    bool down = false;
    SDL_FingerID fingerId = -1;

    if (event->type == SDL_FINGERDOWN || event->type == SDL_FINGERMOTION) {
        x = event->tfinger.x;
        y = event->tfinger.y;
        down = true;
        fingerId = event->tfinger.fingerId;
    } else if (event->type == SDL_FINGERUP) {
        x = event->tfinger.x;
        y = event->tfinger.y;
        down = false;
        fingerId = event->tfinger.fingerId;
    } else {
        return;
    }

    UserController* controller = GetInputManager()->GetController(0);
    if (!controller) return;

    // ========================================================================
    // FINGERUP must be handled FIRST, before any stick/button logic, to ensure
    // that both sticks AND buttons held by the lifting finger are properly
    // released. 
    //
    // Additionally, we force-release ANY active stick as a fallback, even if
    // the FINGERUP fingerId doesn't match the stick's captured fingerId. This
    // handles two key scenarios reported on actual Android devices:
    //   1. SDL/fingerId mismatch: on some devices the FINGERUP fingerId may
    //      differ from the FINGERDOWN fingerId for the same physical touch.
    //   2. System gesture interception: Android may send ACTION_CANCEL instead
    //      of ACTION_UP (e.g. notification shade, back gesture), causing SDL
    //      to NOT generate FINGERUP at all. In that case the staleness guard
    //      (AutoReleaseIfStale / 500ms threshold) cleans up on the next touch.
    // ========================================================================
    if (event->type == SDL_FINGERUP) {
        // Release STICKS: match by fingerId OR force-release any active stick.
        // This is the key fix for fingerId mismatches.
        if (mLeftStick.fingerId == fingerId || mLeftStick.active) {
            UpdateJoystick(mLeftStick, x, y, false, mLeftStick.fingerId != -1 ? mLeftStick.fingerId : fingerId);
        }
        if (mRightStick.fingerId == fingerId || mRightStick.active) {
            UpdateJoystick(mRightStick, x, y, false, mRightStick.fingerId != -1 ? mRightStick.fingerId : fingerId);
        }

        // Release any button currently held by this finger.
        // Also force-release ALL pressed buttons to guard against Android
        // fingerId mismatch (FINGERDOWN and FINGERUP of the same physical
        // touch can be assigned different fingerIds by SDL).
        for (int i = 0; i < NUM_BUTTONS; ++i) {
            if (mButtons[i].pressed) {
                mButtons[i].pressed = false;
                mButtons[i].fingerId = -1;
                mButtons[i].lastEventTime = 0;
                controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(0.0f);
            }
        }
        return;
    }

    // ========================================================================
    // Before processing this touch event, release any stick whose owner
    // finger went stale (lost FINGERUP due to system gesture interception).
    // This prevents "ghost capture" where a new finger cannot claim a stick
    // because a stale fingerId is still sitting on it.
    // ========================================================================
    {
        const radInt64 now = radTimeGetMicroseconds64();
        const radInt64 staleThresholdUs = 500000; // 500ms

        if (mLeftStick.active && (now - mLeftStick.lastEventTime) > staleThresholdUs) {
            mLeftStick.active = false;
            mLeftStick.fingerId = -1;
            mLeftStick.currX = 0.0f;
            mLeftStick.currY = 0.0f;
            controller->GetInputButton(mLeftStick.axisX)->SetValue(0.0f);
            controller->GetInputButton(mLeftStick.axisY)->SetValue(0.0f);
        }
        if (mRightStick.active && (now - mRightStick.lastEventTime) > staleThresholdUs) {
            mRightStick.active = false;
            mRightStick.fingerId = -1;
            mRightStick.currX = 0.0f;
            mRightStick.currY = 0.0f;
            controller->GetInputButton(mRightStick.axisX)->SetValue(0.0f);
            controller->GetInputButton(mRightStick.axisY)->SetValue(0.0f);
        }
    }

    // From this point on, event is guaranteed to be FINGERDOWN or FINGERMOTION.
    // Note: it's possible the stick.fingerId != -1 but the stick is not active
    // (transitional state). We still allow the matching finger to update it.

    // Check sticks first.
    // A finger already captured by a stick only updates that stick.
    // A new finger can only claim a stick if it starts inside the stick radius.
    bool stickClaimed = false;

    float ldx = x - mLeftStick.centerX;
    float ldy = y - mLeftStick.centerY;
    float ldist = sqrtf(ldx*ldx + ldy*ldy);

    float rdx = x - mRightStick.centerX;
    float rdy = y - mRightStick.centerY;
    float rdist = sqrtf(rdx*rdx + rdy*rdy);

    if (mLeftStick.fingerId == fingerId) {
        UpdateJoystick(mLeftStick, x, y, true, fingerId);
        stickClaimed = true;
    } else if (mLeftStick.fingerId == -1 && ldist < mLeftStick.radius) {
        UpdateJoystick(mLeftStick, x, y, true, fingerId);
        stickClaimed = true;
    }

    if (mRightStick.fingerId == fingerId) {
        UpdateJoystick(mRightStick, x, y, true, fingerId);
        stickClaimed = true;
    } else if (!stickClaimed && mRightStick.fingerId == -1 && rdist < mRightStick.radius) {
        UpdateJoystick(mRightStick, x, y, true, fingerId);
        stickClaimed = true;
    }

    if (stickClaimed) {
        return;
    }

    // Check buttons (only FINGERDOWN and FINGERMOTION here; FINGERUP is handled above)
    for (int i = 0; i < NUM_BUTTONS; ++i) {
        bool inBounds = (x >= mButtons[i].x - mButtons[i].w/2 && x <= mButtons[i].x + mButtons[i].w/2 &&
                         y >= mButtons[i].y - mButtons[i].h/2 && y <= mButtons[i].y + mButtons[i].h/2);

        if (event->type == SDL_FINGERDOWN && inBounds) {
            if (!mButtons[i].pressed) {
                mButtons[i].pressed = true;
                mButtons[i].fingerId = fingerId;
                mButtons[i].lastEventTime = radTimeGetMicroseconds64();
                controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(1.0f);
            }
        } else if (event->type == SDL_FINGERMOTION && mButtons[i].fingerId == fingerId && !inBounds) {
            if (mButtons[i].pressed) {
                mButtons[i].pressed = false;
                mButtons[i].fingerId = -1;
                mButtons[i].lastEventTime = 0;
                controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(0.0f);
            }
        } else if (event->type == SDL_FINGERMOTION && inBounds && mButtons[i].fingerId == -1 && !mButtons[i].pressed) {
            // Finger moved into a button while not controlling a stick
            mButtons[i].pressed = true;
            mButtons[i].fingerId = fingerId;
            mButtons[i].lastEventTime = radTimeGetMicroseconds64();
            controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(1.0f);
        }
    }
}

void TouchGui::ReleaseAllInputs() {
    UserController* controller = GetInputManager()->GetController(0);
    if (!controller) return;

    for (int i = 0; i < NUM_BUTTONS; ++i) {
        if (mButtons[i].pressed) {
            mButtons[i].pressed = false;
            mButtons[i].fingerId = -1;
            mButtons[i].lastEventTime = 0;
            controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(0.0f);
        }
    }

    mLeftStick.active = false;
    mLeftStick.fingerId = -1;
    mLeftStick.currX = 0.0f;
    mLeftStick.currY = 0.0f;
    controller->GetInputButton(mLeftStick.axisX)->SetValue(0.0f);
    controller->GetInputButton(mLeftStick.axisY)->SetValue(0.0f);

    mRightStick.active = false;
    mRightStick.fingerId = -1;
    mRightStick.currX = 0.0f;
    mRightStick.currY = 0.0f;
    controller->GetInputButton(mRightStick.axisX)->SetValue(0.0f);
    controller->GetInputButton(mRightStick.axisY)->SetValue(0.0f);
}

void TouchGui::AutoReleaseIfStale(UserController* controller) {
    if (!controller) return;
    const radInt64 now = radTimeGetMicroseconds64();
    const radInt64 staleThresholdUs = 500000; // 500 ms (reduced from 3s for faster ghost-input cleanup)

    // Auto-release sticks if no touch event arrived for a while (ghost-finger guard)
    if (mLeftStick.active && (now - mLeftStick.lastEventTime) > staleThresholdUs) {
        mLeftStick.active = false;
        mLeftStick.fingerId = -1;
        mLeftStick.currX = 0.0f;
        mLeftStick.currY = 0.0f;
        controller->GetInputButton(mLeftStick.axisX)->SetValue(0.0f);
        controller->GetInputButton(mLeftStick.axisY)->SetValue(0.0f);
    }
    if (mRightStick.active && (now - mRightStick.lastEventTime) > staleThresholdUs) {
        mRightStick.active = false;
        mRightStick.fingerId = -1;
        mRightStick.currX = 0.0f;
        mRightStick.currY = 0.0f;
        controller->GetInputButton(mRightStick.axisX)->SetValue(0.0f);
        controller->GetInputButton(mRightStick.axisY)->SetValue(0.0f);
    }

    // Auto-release stale buttons (safety net for missed FINGERUP events)
    for (int i = 0; i < NUM_BUTTONS; ++i) {
        if (mButtons[i].pressed && (now - mButtons[i].lastEventTime) > staleThresholdUs) {
            mButtons[i].pressed = false;
            mButtons[i].fingerId = -1;
            mButtons[i].lastEventTime = 0;
            controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(0.0f);
        }
    }


}

void TouchGui::Update(unsigned int elapsedTime) {
    UserController* controller = GetInputManager()->GetController(0);
    AutoReleaseIfStale(controller);
}

void TouchGui::Render() {
#ifdef RAD_ANDROID
    return; // HUD rendered by Java overlay (GamepadOverlayView)
#endif
    if (!mVisible) return;

    pddiRenderContext* pddi = p3d::pddi;
    mScreenWidth = (float)p3d::display->GetWidth();
    mScreenHeight = (float)p3d::display->GetHeight();

    pddi->PushState(PDDI_STATE_ALL);
    pddi->SetProjectionMode(PDDI_PROJECTION_DEVICE);
    pddi->SetZWrite(false);
    pddi->SetZCompare(PDDI_COMPARE_ALWAYS);
    pddiRect fullScreen(0, 0, (int)mScreenWidth, (int)mScreenHeight);
    pddi->SetScissor(&fullScreen);  // Full-screen scissor in case Scrooby UI set a clipping rect

    // Draw Joysticks
    pddiColour stickCol = pddiColour(255, 234, 2, 40); // Simpsons Yellow transparent
    pddiColour knobCol = pddiColour(17, 31, 161, 120); // Simpsons Blue

    // Left Stick Base
    DrawRect(mLeftStick.centerX * mScreenWidth - 60, mLeftStick.centerY * mScreenHeight - 60, 120, 120, stickCol);
    // Left Stick Knob
    DrawRect((mLeftStick.centerX + mLeftStick.currX * 0.08f) * mScreenWidth - 30,
             (mLeftStick.centerY + mLeftStick.currY * 0.08f) * mScreenHeight - 30, 60, 60, knobCol);

    // Right Stick Base
    DrawRect(mRightStick.centerX * mScreenWidth - 60, mRightStick.centerY * mScreenHeight - 60, 120, 120, stickCol);
    // Right Stick Knob
    DrawRect((mRightStick.centerX + mRightStick.currX * 0.08f) * mScreenWidth - 30,
             (mRightStick.centerY + mRightStick.currY * 0.08f) * mScreenHeight - 30, 60, 60, knobCol);

    // Draw Buttons
    for (int i = 0; i < NUM_BUTTONS; ++i) {
        // Simpsons Yellow: 255, 234, 2
        pddiColour col = mButtons[i].pressed ? pddiColour(255, 255, 0, 180) : pddiColour(255, 234, 2, 100);
        pddiColour borderCol = pddiColour(17, 31, 161, 160); // Simpsons Blue

        float x = mButtons[i].x * mScreenWidth;
        float y = mButtons[i].y * mScreenHeight;
        float w = mButtons[i].w * mScreenWidth;
        float h = mButtons[i].h * mScreenHeight;

        // Draw Border (Simpsons Blue)
        DrawRect(x - w/2 - 3, y - h/2 - 3, w + 6, h + 6, borderCol);
        // Draw Main Rect (Simpsons Yellow)
        DrawRect(x - w/2, y - h/2, w, h, col);

        // Draw Label with shadow
        // Note: pddiRenderContext::DrawString is sometimes available in some PDDI versions/ports.
        // If it causes compilation errors, we would need to use tFont or Scrooby.
        pddi->DrawString(mButtons[i].label, (int)(x - 9), (int)(y - 9), pddiColour(255, 255, 255));
        pddi->DrawString(mButtons[i].label, (int)(x - 10), (int)(y - 10), pddiColour(17, 31, 161));
    }

    pddi->PopState(PDDI_STATE_ALL);
}

void TouchGui::DrawRect(float x, float y, float w, float h, pddiColour colour) {
    pddiPrimStream* stream = p3d::pddi->BeginPrims(NULL, PDDI_PRIM_TRIANGLES, PDDI_V_C, 6);
    // Re-apply device projection: BeginPrims activates defaultShader via SetMaterial(),
    // which does not inherit the projection matrix from SetupHardwareProjection.
    p3d::pddi->SetProjectionMode(PDDI_PROJECTION_DEVICE);

    stream->Colour(colour);
    stream->Coord(x, y, 0.0f);
    stream->Colour(colour);
    stream->Coord(x + w, y, 0.0f);
    stream->Colour(colour);
    stream->Coord(x, y + h, 0.0f);

    stream->Colour(colour);
    stream->Coord(x + w, y, 0.0f);
    stream->Colour(colour);
    stream->Coord(x + w, y + h, 0.0f);
    stream->Colour(colour);
    stream->Coord(x, y + h, 0.0f);

    p3d::pddi->EndPrims(stream);
}
