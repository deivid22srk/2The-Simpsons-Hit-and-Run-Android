#include <input/touchGui.h>
#include <input/inputmanager.h>
#include <input/usercontroller.h>
#include <p3d/utility.hpp>
#include <pddi/pddi.hpp>
#include <main/game.h>
#include <math.h>
#include <cmath>
#include <cstring>

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
    // Left side: D-Pad (placed above the stick)
    float dpadSize = 0.05f;
    float dpadX = 0.12f;
    float dpadY = 0.50f;

    mButtons[BTN_DPAD_UP] = {dpadX, dpadY - dpadSize, dpadSize, dpadSize, InputManager::DPadUp, false, -1, "U"};
    mButtons[BTN_DPAD_DOWN] = {dpadX, dpadY + dpadSize, dpadSize, dpadSize, InputManager::DPadDown, false, -1, "D"};
    mButtons[BTN_DPAD_LEFT] = {dpadX - dpadSize, dpadY, dpadSize, dpadSize, InputManager::DPadLeft, false, -1, "L"};
    mButtons[BTN_DPAD_RIGHT] = {dpadX + dpadSize, dpadY, dpadSize, dpadSize, InputManager::DPadRight, false, -1, "R"};

    // Right side: Face buttons (Diamond pattern, bottom right)
    float faceX = 0.88f;
    float faceY = 0.78f;
    float faceSize = 0.06f;
    float dist = 0.08f;

    mButtons[BTN_A] = {faceX, faceY + dist, faceSize, faceSize, InputManager::A, false, -1, "A"};
    mButtons[BTN_B] = {faceX + dist, faceY, faceSize, faceSize, InputManager::B, false, -1, "B"};
    mButtons[BTN_X] = {faceX - dist, faceY, faceSize, faceSize, InputManager::Square, false, -1, "X"};
    mButtons[BTN_Y] = {faceX, faceY - dist, faceSize, faceSize, InputManager::Triangle, false, -1, "Y"};

    // Center/Top: Start, Select
    mButtons[BTN_START] = {0.56f, 0.06f, 0.08f, 0.04f, InputManager::Start, false, -1, "START"};
    mButtons[BTN_SELECT] = {0.44f, 0.06f, 0.08f, 0.04f, InputManager::Select, false, -1, "SELECT"};

    // Shoulder buttons
    mButtons[BTN_L1] = {0.12f, 0.06f, 0.10f, 0.06f, InputManager::L1, false, -1, "L1"};
    mButtons[BTN_R1] = {0.88f, 0.06f, 0.10f, 0.06f, InputManager::R1, false, -1, "R1"};

    // Joysticks
    mLeftStick.centerX = 0.12f;
    mLeftStick.centerY = 0.78f; // Lowered stick
    mLeftStick.radius = 0.12f;
    mLeftStick.currX = 0.0f;
    mLeftStick.currY = 0.0f;
    mLeftStick.axisX = InputManager::LeftStickX;
    mLeftStick.axisY = InputManager::LeftStickY;
    mLeftStick.fingerId = -1;
    mLeftStick.active = false;

    mRightStick.centerX = 0.88f;
    mRightStick.centerY = 0.50f; // Right stick above face buttons
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

            if (dist > stick.radius) {
                dx = (dx / dist) * stick.radius;
                dy = (dy / dist) * stick.radius;
            }

            stick.currX = dx / stick.radius;
            stick.currY = dy / stick.radius;

            // Map to -1.0 - 1.0 (centered at 0.0)
            controller->GetInputButton(stick.axisX)->SetValue(stick.currX);
            controller->GetInputButton(stick.axisY)->SetValue(-stick.currY); // Invert Y
        }
    } else if (stick.fingerId == fingerId) {
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
    if (!mVisible) return;

    pddiRenderContext* pddi = p3d::pddi;
    mScreenWidth = (float)pddi->GetDisplayInfo().xRes;
    mScreenHeight = (float)pddi->GetDisplayInfo().yRes;

    pddi->PushState(PDDI_STATE_ALL);
    pddi->SetProjectionMode(PDDI_PROJECTION_DEVICE);
    pddi->SetZWrite(false);
    pddi->SetZCompare(PDDI_COMPARE_ALWAYS);

    // Simpsons Colors
    pddiColour simpsonYellow = pddiColour(255, 217, 15);
    pddiColour simpsonBlue = pddiColour(31, 74, 184);

    // Draw Joysticks
    pddiColour stickBaseCol = simpsonYellow;
    stickBaseCol.SetAlpha(40);
    pddiColour stickKnobCol = simpsonBlue;
    stickKnobCol.SetAlpha(100);

    float stickBaseRadius = 0.10f * mScreenWidth;
    float stickInnerRadius = 0.08f * mScreenWidth;
    float knobRadius = 0.04f * mScreenWidth;
    float knobOffset = 0.06f;

    // Left Stick
    DrawDonut(mLeftStick.centerX * mScreenWidth, mLeftStick.centerY * mScreenHeight, stickBaseRadius, stickInnerRadius, stickBaseCol);
    DrawCircle((mLeftStick.centerX + mLeftStick.currX * knobOffset) * mScreenWidth,
               (mLeftStick.centerY + mLeftStick.currY * knobOffset) * mScreenHeight, knobRadius, stickKnobCol);

    // Right Stick
    DrawDonut(mRightStick.centerX * mScreenWidth, mRightStick.centerY * mScreenHeight, stickBaseRadius, stickInnerRadius, stickBaseCol);
    DrawCircle((mRightStick.centerX + mRightStick.currX * knobOffset) * mScreenWidth,
               (mRightStick.centerY + mRightStick.currY * knobOffset) * mScreenHeight, knobRadius, stickKnobCol);

    // Draw Buttons
    for (int i = 0; i < NUM_BUTTONS; ++i) {
        pddiColour col = simpsonYellow;
        col.SetAlpha(mButtons[i].pressed ? 180 : 80);

        pddiColour borderCol = simpsonBlue;
        borderCol.SetAlpha(mButtons[i].pressed ? 220 : 120);

        float x = mButtons[i].x * mScreenWidth;
        float y = mButtons[i].y * mScreenHeight;
        float radius = (mButtons[i].w * mScreenWidth + mButtons[i].h * mScreenHeight) / 4.0f;

        if (i == BTN_START || i == BTN_SELECT || i == BTN_L1 || i == BTN_R1) {
            // Use rounded rect style for these
            float w = mButtons[i].w * mScreenWidth;
            float h = mButtons[i].h * mScreenHeight;
            DrawRect(x - w/2 - 2, y - h/2 - 2, w + 4, h + 4, borderCol);
            DrawRect(x - w/2, y - h/2, w, h, col);
        } else {
            // Use circle style for face buttons and dpad
            DrawCircle(x, y, radius + 2, borderCol);
            DrawCircle(x, y, radius, col);
        }

        // Draw Label with shadow (centered)
        int labelLen = strlen(mButtons[i].label);
        int offsetX = labelLen * 4;
        pddi->DrawString(mButtons[i].label, (int)(x - offsetX + 1), (int)(y - 7 + 1), pddiColour(0, 0, 0));
        pddi->DrawString(mButtons[i].label, (int)(x - offsetX), (int)(y - 7), pddiColour(255, 255, 255));
    }

    pddi->PopState(PDDI_STATE_ALL);
}

void TouchGui::DrawRect(float x, float y, float w, float h, pddiColour colour) {
    pddiPrimStream* stream = p3d::pddi->BeginPrims(NULL, PDDI_PRIM_TRIANGLES, PDDI_V_C, 6);

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

void TouchGui::DrawCircle(float x, float y, float radius, pddiColour colour) {
    const int segments = 16;
    pddiPrimStream* stream = p3d::pddi->BeginPrims(NULL, PDDI_PRIM_TRIANGLES, PDDI_V_C, segments * 3);

    for (int i = 0; i < segments; ++i) {
        float theta1 = (2.0f * 3.1415926f * (float)i) / (float)segments;
        float theta2 = (2.0f * 3.1415926f * (float)(i + 1)) / (float)segments;

        stream->Colour(colour);
        stream->Coord(x, y, 0.0f);
        stream->Colour(colour);
        stream->Coord(x + radius * cosf(theta1), y + radius * sinf(theta1), 0.0f);
        stream->Colour(colour);
        stream->Coord(x + radius * cosf(theta2), y + radius * sinf(theta2), 0.0f);
    }

    p3d::pddi->EndPrims(stream);
}

void TouchGui::DrawDonut(float x, float y, float outerRadius, float innerRadius, pddiColour colour) {
    const int segments = 16;
    pddiPrimStream* stream = p3d::pddi->BeginPrims(NULL, PDDI_PRIM_TRIANGLES, PDDI_V_C, segments * 6);

    for (int i = 0; i < segments; ++i) {
        float theta1 = (2.0f * 3.1415926f * (float)i) / (float)segments;
        float theta2 = (2.0f * 3.1415926f * (float)(i + 1)) / (float)segments;

        float c1 = cosf(theta1);
        float s1 = sinf(theta1);
        float c2 = cosf(theta2);
        float s2 = sinf(theta2);

        // First triangle
        stream->Colour(colour);
        stream->Coord(x + innerRadius * c1, y + innerRadius * s1, 0.0f);
        stream->Colour(colour);
        stream->Coord(x + outerRadius * c1, y + outerRadius * s1, 0.0f);
        stream->Colour(colour);
        stream->Coord(x + outerRadius * c2, y + outerRadius * s2, 0.0f);

        // Second triangle
        stream->Colour(colour);
        stream->Coord(x + innerRadius * c1, y + innerRadius * s1, 0.0f);
        stream->Colour(colour);
        stream->Coord(x + outerRadius * c2, y + outerRadius * s2, 0.0f);
        stream->Colour(colour);
        stream->Coord(x + innerRadius * c2, y + innerRadius * s2, 0.0f);
    }

    p3d::pddi->EndPrims(stream);
}
