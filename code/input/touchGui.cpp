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
    mRightStick.fingerId = -1;
    mRightStick.active = false;

    for(int i = 0; i < NUM_BUTTONS; ++i) {
        mButtons[i].pressed = false;
        mButtons[i].fingerId = -1;
    }
}

TouchGui::~TouchGui() {}

void TouchGui::Init() {
    // Define button positions (normalized 0.0 - 1.0)
    // Left side: D-Pad
    float dpadSize = 0.08f;
    float dpadX = 0.15f;
    float dpadY = 0.75f;

    mButtons[BTN_DPAD_UP] = {dpadX, dpadY - dpadSize, dpadSize, dpadSize, InputManager::DPadUp, false, -1, "UP"};
    mButtons[BTN_DPAD_DOWN] = {dpadX, dpadY + dpadSize, dpadSize, dpadSize, InputManager::DPadDown, false, -1, "DN"};
    mButtons[BTN_DPAD_LEFT] = {dpadX - dpadSize, dpadY, dpadSize, dpadSize, InputManager::DPadLeft, false, -1, "LF"};
    mButtons[BTN_DPAD_RIGHT] = {dpadX + dpadSize, dpadY, dpadSize, dpadSize, InputManager::DPadRight, false, -1, "RT"};

    // Right side: Face buttons
    float faceX = 0.85f;
    float faceY = 0.75f;
    float faceSize = 0.08f;

    mButtons[BTN_A] = {faceX, faceY + faceSize, faceSize, faceSize, InputManager::A, false, -1, "A"};
    mButtons[BTN_B] = {faceX + faceSize, faceY, faceSize, faceSize, InputManager::B, false, -1, "B"};
    mButtons[BTN_X] = {faceX - faceSize, faceY, faceSize, faceSize, InputManager::Square, false, -1, "X"};
    mButtons[BTN_Y] = {faceX, faceY - faceSize, faceSize, faceSize, InputManager::Triangle, false, -1, "Y"};

    // Center/Top: Start, Select
    mButtons[BTN_START] = {0.55f, 0.05f, 0.10f, 0.05f, InputManager::Start, false, -1, "START"};
    mButtons[BTN_SELECT] = {0.45f, 0.05f, 0.10f, 0.05f, InputManager::Select, false, -1, "SELECT"};

    // Shoulder buttons
    mButtons[BTN_L1] = {0.15f, 0.05f, 0.12f, 0.07f, InputManager::AnalogL1, false, -1, "L1"};
    mButtons[BTN_R1] = {0.85f, 0.05f, 0.12f, 0.07f, InputManager::AnalogR1, false, -1, "R1"};

    // Joysticks
    mLeftStick.centerX = 0.15f;
    mLeftStick.centerY = 0.75f;
    mLeftStick.radius = 0.15f;
    mLeftStick.currX = 0.0f;
    mLeftStick.currY = 0.0f;
    mLeftStick.axisX = InputManager::LeftStickX;
    mLeftStick.axisY = InputManager::LeftStickY;
    mLeftStick.fingerId = -1;
    mLeftStick.active = false;

    mRightStick.centerX = 0.85f;
    mRightStick.centerY = 0.75f;
    mRightStick.radius = 0.15f;
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
                    controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(0.0f);
                }
            }
            controller->GetInputButton(mLeftStick.axisX)->SetValue(0.5f);
            controller->GetInputButton(mLeftStick.axisY)->SetValue(0.5f);
            controller->GetInputButton(mRightStick.axisX)->SetValue(0.5f);
            controller->GetInputButton(mRightStick.axisY)->SetValue(0.5f);
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

            if (dist > stick.radius) {
                dx = (dx / dist) * stick.radius;
                dy = (dy / dist) * stick.radius;
            }

            stick.currX = dx / stick.radius;
            stick.currY = dy / stick.radius;

            // Map to 0.0 - 1.0 (centered at 0.5)
            // Note: Some systems use -1 to 1, but SRR2 Button::SetValue expects normalized for sticks too?
            // InputManager handles deadzones and calibration.
            // Let's check sdlcontroller.cpp: newValue += 0.5f; for axis.
            controller->GetInputButton(stick.axisX)->SetValue(stick.currX * 0.5f + 0.5f);
            controller->GetInputButton(stick.axisY)->SetValue(-stick.currY * 0.5f + 0.5f); // Invert Y
        }
    } else if (stick.fingerId == fingerId) {
        stick.active = false;
        stick.fingerId = -1;
        stick.currX = 0.0f;
        stick.currY = 0.0f;
        controller->GetInputButton(stick.axisX)->SetValue(0.5f);
        controller->GetInputButton(stick.axisY)->SetValue(0.5f);
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

    // Check sticks first (they are larger and usually at corners)
    if (x < 0.5f) UpdateJoystick(mLeftStick, x, y, down, fingerId);
    else UpdateJoystick(mRightStick, x, y, down, fingerId);

    // Check buttons
    for (int i = 0; i < NUM_BUTTONS; ++i) {
        bool inBounds = (x >= mButtons[i].x - mButtons[i].w/2 && x <= mButtons[i].x + mButtons[i].w/2 &&
                         y >= mButtons[i].y - mButtons[i].h/2 && y <= mButtons[i].y + mButtons[i].h/2);

        if (event->type == SDL_FINGERDOWN && inBounds) {
            mButtons[i].pressed = true;
            mButtons[i].fingerId = fingerId;
            controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(1.0f);
        } else if (event->type == SDL_FINGERUP && mButtons[i].fingerId == fingerId) {
            mButtons[i].pressed = false;
            mButtons[i].fingerId = -1;
            controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(0.0f);
        } else if (event->type == SDL_FINGERMOTION && mButtons[i].fingerId == fingerId && !inBounds) {
            mButtons[i].pressed = false;
            mButtons[i].fingerId = -1;
            controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(0.0f);
        } else if (event->type == SDL_FINGERMOTION && inBounds && mButtons[i].fingerId == -1 && !mLeftStick.active && !mRightStick.active) {
            // Only capture if not already handled by stick
            mButtons[i].pressed = true;
            mButtons[i].fingerId = fingerId;
            controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(1.0f);
        }
    }
}

void TouchGui::Update(unsigned int elapsedTime) {}

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
