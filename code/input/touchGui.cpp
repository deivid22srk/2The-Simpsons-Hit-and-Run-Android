#include <input/touchGui.h>
#include <input/inputmanager.h>
#include <input/usercontroller.h>
#include <p3d/utility.hpp>
#include <pddi/pddi.hpp>
#include <main/game.h>
#include <math.h>

// ── Cross-platform logging ───────────────────────────────────────────
#ifdef RAD_ANDROID
  #include <android/log.h>
  #define LOG_TAG_HUD "SimpsonsTouchHud"
  #define HUD_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG_HUD, __VA_ARGS__)
  #define HUD_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG_HUD, __VA_ARGS__)
#else
  #include <cstdio>
  #define HUD_LOGI(...) do { std::printf("[TouchHud] "); std::printf(__VA_ARGS__); std::printf("\n"); std::fflush(stdout); } while(0)
  #define HUD_LOGE(...) do { std::printf("[TouchHud:ERR] "); std::printf(__VA_ARGS__); std::printf("\n"); std::fflush(stdout); } while(0)
#endif

// ── Constants ────────────────────────────────────────────────────────
static const float DEADZONE = 0.18f;

// ── Singleton ────────────────────────────────────────────────────────

TouchGui* TouchGui::spInstance = NULL;

TouchGui* TouchGui::GetInstance()       { return spInstance; }
void TouchGui::CreateInstance()         { if (!spInstance) spInstance = new TouchGui(); }
void TouchGui::DestroyInstance()        { delete spInstance; spInstance = NULL; }

// ── Constructor / Destructor ─────────────────────────────────────────

TouchGui::TouchGui()
    : mVisible(true)
    , mScreenWidth(640.0f)
    , mScreenHeight(480.0f)
{
    // Zero-initialise sticks
    mLeftStick  = {};
    mRightStick = {};
    mLeftStick.fingerId  = -1;
    mRightStick.fingerId = -1;

    // Zero-initialise buttons
    for (int i = 0; i < NUM_BUTTONS; ++i) {
        mButtons[i] = {};
        mButtons[i].fingerId = -1;
    }

    HUD_LOGI("TouchGui constructed");
}

TouchGui::~TouchGui() {}

// ── Initialisation helper ───────────────────────────────────────────

void TouchGui::InitButton(TouchButton& btn,
                          float x, float y, float w, float h,
                          int buttonIndex, const char* label)
{
    btn.x             = x;
    btn.y             = y;
    btn.w             = w;
    btn.h             = h;
    btn.buttonIndex   = buttonIndex;
    btn.pressed       = false;
    btn.fingerId      = -1;
    btn.label         = label;
    btn.lastEventTime = 0;
}

void TouchGui::Init()
{
    HUD_LOGI("Init() - initializing HUD layout");

    // ── D-Pad ────────────────────────────────────────────────────────
    const float dpadSize = 0.10f;
    const float dpadX    = 0.16f;
    const float dpadY    = 0.79f;

    InitButton(mButtons[BTN_DPAD_UP],    dpadX, 0.70f, dpadSize, dpadSize, InputManager::DPadUp,    "UP");
    InitButton(mButtons[BTN_DPAD_DOWN],  dpadX, 0.88f, dpadSize, dpadSize, InputManager::DPadDown,  "DN");
    InitButton(mButtons[BTN_DPAD_LEFT],  0.07f, dpadY, dpadSize, dpadSize, InputManager::DPadLeft,  "LF");
    InitButton(mButtons[BTN_DPAD_RIGHT], 0.25f, dpadY, dpadSize, dpadSize, InputManager::DPadRight, "RT");

    // ── Face buttons (A / B / X / Y) ─────────────────────────────────
    const float faceX    = 0.85f;
    const float faceY    = 0.76f;
    const float faceSize = 0.10f;

    InitButton(mButtons[BTN_A], faceX,             faceY + faceSize, faceSize, faceSize, InputManager::A,        "A");
    InitButton(mButtons[BTN_B], faceX + faceSize,  faceY,            faceSize, faceSize, InputManager::B,        "B");
    InitButton(mButtons[BTN_X], faceX - faceSize,  faceY,            faceSize, faceSize, InputManager::Square,   "X");
    InitButton(mButtons[BTN_Y], faceX,             faceY - faceSize, faceSize, faceSize, InputManager::Triangle, "Y");

    // ── Start / Select ───────────────────────────────────────────────
    InitButton(mButtons[BTN_START],  0.55f, 0.04f, 0.12f, 0.06f, InputManager::Start,  "START");
    InitButton(mButtons[BTN_SELECT], 0.43f, 0.04f, 0.12f, 0.06f, InputManager::Select, "SELECT");

    // ── Shoulders ────────────────────────────────────────────────────
    InitButton(mButtons[BTN_L1], 0.14f, 0.04f, 0.14f, 0.08f, InputManager::AnalogL1, "L1");
    InitButton(mButtons[BTN_R1], 0.86f, 0.04f, 0.14f, 0.08f, InputManager::AnalogR1, "R1");

    // ── Left stick ───────────────────────────────────────────────────
    mLeftStick = {};
    mLeftStick.centerX = 0.16f;
    mLeftStick.centerY = 0.52f;
    mLeftStick.radius  = 0.12f;
    mLeftStick.axisX   = InputManager::LeftStickX;
    mLeftStick.axisY   = InputManager::LeftStickY;
    mLeftStick.fingerId = -1;

    // ── Right stick ──────────────────────────────────────────────────
    mRightStick = {};
    mRightStick.centerX = 0.84f;
    mRightStick.centerY = 0.52f;
    mRightStick.radius  = 0.12f;
    mRightStick.axisX   = InputManager::RightStickX;
    mRightStick.axisY   = InputManager::RightStickY;
    mRightStick.fingerId = -1;

    HUD_LOGI("Init() - LStick(%.2f,%.2f r=%.2f) RStick(%.2f,%.2f r=%.2f) %d buttons",
             mLeftStick.centerX, mLeftStick.centerY, mLeftStick.radius,
             mRightStick.centerX, mRightStick.centerY, mRightStick.radius, NUM_BUTTONS);
}

// ── Visibility ───────────────────────────────────────────────────────

void TouchGui::SetVisible(bool visible)
{
    if (mVisible == visible) return;
    HUD_LOGI("SetVisible %s -> %s", mVisible ? "ON" : "OFF", visible ? "ON" : "OFF");
    mVisible = visible;

    if (!visible) {
        ReleaseAllInputs();
    }
}

// ── Deadzone helper ──────────────────────────────────────────────────

float TouchGui::ApplyDeadzone(float value, float deadzone)
{
    if (fabsf(value) < deadzone) return 0.0f;
    return (value - (value > 0.0f ? deadzone : -deadzone)) / (1.0f - deadzone);
}

// ── Stick helpers ────────────────────────────────────────────────────

bool TouchGui::ProcessStickDown(TouchJoystick& stick, float x, float y,
                                SDL_FingerID fingerId, radTime64 timestamp,
                                UserController* controller)
{
    // Already owned by a different finger → reject.
    if (stick.fingerId != -1 && stick.fingerId != fingerId) return false;

    float dx = x - stick.centerX;
    float dy = y - stick.centerY;
    float dist = sqrtf(dx * dx + dy * dy);

    // Only claim if finger starts inside the stick radius.
    if (stick.fingerId == -1 && dist >= stick.radius) return false;

    const bool justActivated = !stick.active;
    stick.fingerId     = fingerId;
    stick.active       = true;
    stick.lastEventTime = timestamp;

    if (justActivated) {
        const char* name = (&stick == &mLeftStick) ? "LStick" : "RStick";
        HUD_LOGI("%s ACTIVATED fingerId=%lld pos=(%.3f,%.3f) dist=%.3f",
                 name, (long long)fingerId, x, y, dist);
    }

    UpdateStickMotion(stick, x, y, timestamp, controller);
    return true;
}

void TouchGui::UpdateStickMotion(TouchJoystick& stick, float x, float y,
                                 radTime64 timestamp, UserController* controller)
{
    float dx = x - stick.centerX;
    float dy = y - stick.centerY;
    float dist = sqrtf(dx * dx + dy * dy);

    if (dist > stick.radius) {
        dx *= stick.radius / dist;
        dy *= stick.radius / dist;
    }

    stick.currX = dx / stick.radius;
    stick.currY = dy / stick.radius;
    stick.lastEventTime = timestamp;

    // Game expects [-1, 1] with Y inverted.
    float valX = ApplyDeadzone(stick.currX, DEADZONE);
    float valY = ApplyDeadzone(-stick.currY, DEADZONE);

    controller->GetInputButton(stick.axisX)->SetValue(valX);
    controller->GetInputButton(stick.axisY)->SetValue(valY);
}

void TouchGui::ReleaseStick(TouchJoystick& stick, UserController* controller)
{
    const char* name = (&stick == &mLeftStick) ? "LStick" : "RStick";
    HUD_LOGI("%s RELEASED fingerId=%lld", name, (long long)stick.fingerId);

    stick.active        = false;
    stick.fingerId      = -1;
    stick.currX         = 0.0f;
    stick.currY         = 0.0f;
    stick.lastEventTime = 0;

    controller->GetInputButton(stick.axisX)->SetValue(0.0f);
    controller->GetInputButton(stick.axisY)->SetValue(0.0f);
}

// ── Button helpers ───────────────────────────────────────────────────

void TouchGui::PressButton(int index, SDL_FingerID fingerId,
                           radTime64 timestamp, UserController* controller)
{
    TouchButton& btn = mButtons[index];
    btn.pressed       = true;
    btn.fingerId      = fingerId;
    btn.lastEventTime = timestamp;

    controller->GetInputButton(btn.buttonIndex)->SetValue(1.0f);
    HUD_LOGI("BTN %s PRESSED fingerId=%lld", btn.label, (long long)fingerId);
}

void TouchGui::ReleaseButton(int index, UserController* controller)
{
    TouchButton& btn = mButtons[index];
    HUD_LOGI("BTN %s RELEASED fingerId=%lld", btn.label, (long long)btn.fingerId);

    btn.pressed       = false;
    btn.fingerId      = -1;
    btn.lastEventTime = 0;

    controller->GetInputButton(btn.buttonIndex)->SetValue(0.0f);
}

// ── Touch-event dispatcher ───────────────────────────────────────────

void TouchGui::HandleTouchEvent(SDL_Event* event)
{
    if (!mVisible) return;

    UserController* controller = GetInputManager()->GetController(0);
    if (!controller) {
        HUD_LOGE("HandleTouchEvent: GetController(0) returned null!");
        return;
    }

    const radTime64 now = radTimeGetMicroseconds64();

    switch (event->type) {
    case SDL_FINGERDOWN: {
        const float x = event->tfinger.x;
        const float y = event->tfinger.y;
        HUD_LOGI("EVT DOWN fingerId=%lld pos=(%.3f,%.3f) | LStk(fid=%lld) RStk(fid=%lld)",
                 (long long)event->tfinger.fingerId, x, y,
                 (long long)mLeftStick.fingerId, (long long)mRightStick.fingerId);
        HandleFingerDown(x, y, event->tfinger.fingerId, now, controller);
        break;
    }
    case SDL_FINGERMOTION: {
        // Don't log MOVE events — too noisy at 60+ Hz.
        HandleFingerMotion(event->tfinger.x, event->tfinger.y,
                           event->tfinger.fingerId, now, controller);
        break;
    }
    case SDL_FINGERUP: {
        HUD_LOGI("EVT UP   fingerId=%lld | LStk(fid=%lld) RStk(fid=%lld)",
                 (long long)event->tfinger.fingerId,
                 (long long)mLeftStick.fingerId, (long long)mRightStick.fingerId);
        HandleFingerUp(event->tfinger.fingerId, controller);
        break;
    }
    default:
        break;
    }
}

// ── Event handlers ───────────────────────────────────────────────────

void TouchGui::HandleFingerDown(float x, float y, SDL_FingerID fingerId,
                                radTime64 timestamp, UserController* controller)
{
    // Sticks take priority.
    if (ProcessStickDown(mLeftStick,  x, y, fingerId, timestamp, controller)) return;
    if (ProcessStickDown(mRightStick, x, y, fingerId, timestamp, controller)) return;

    // Then buttons — any unclaimed button under the finger.
    for (int i = 0; i < NUM_BUTTONS; ++i) {
        if (!mButtons[i].pressed && mButtons[i].Contains(x, y)) {
            PressButton(i, fingerId, timestamp, controller);
        }
    }
}

void TouchGui::HandleFingerMotion(float x, float y, SDL_FingerID fingerId,
                                  radTime64 timestamp, UserController* controller)
{
    // If this finger owns a stick, update it.
    if (mLeftStick.fingerId == fingerId) {
        UpdateStickMotion(mLeftStick, x, y, timestamp, controller);
        return;
    }
    if (mRightStick.fingerId == fingerId) {
        UpdateStickMotion(mRightStick, x, y, timestamp, controller);
        return;
    }

    // Otherwise, check buttons: release if slid out, press if slid in.
    for (int i = 0; i < NUM_BUTTONS; ++i) {
        const bool inside = mButtons[i].Contains(x, y);

        if (mButtons[i].fingerId == fingerId && mButtons[i].pressed && !inside) {
            ReleaseButton(i, controller);
        } else if (inside && mButtons[i].fingerId == -1 && !mButtons[i].pressed) {
            // Finger slid into an unclaimed button.
            PressButton(i, fingerId, timestamp, controller);
        }
    }
}

void TouchGui::HandleFingerUp(SDL_FingerID fingerId, UserController* controller)
{
    // Release by exact fingerId match only.
    // If Android/SDL gives a mismatched fingerId, the staleness timer in
    // AutoReleaseStaleInputs will clean up within STALE_TIMEOUT_US (300 ms).

    if (mLeftStick.fingerId == fingerId) {
        ReleaseStick(mLeftStick, controller);
    } else if (mLeftStick.active) {
        // FingerId mismatch — log for diagnostics but let staleness handle it.
        HUD_LOGI("UP fingerId mismatch LStick: stick.fingerId=%lld != event.fingerId=%lld (will be caught by staleness guard)",
                 (long long)mLeftStick.fingerId, (long long)fingerId);
    }

    if (mRightStick.fingerId == fingerId) {
        ReleaseStick(mRightStick, controller);
    } else if (mRightStick.active) {
        HUD_LOGI("UP fingerId mismatch RStick: stick.fingerId=%lld != event.fingerId=%lld (will be caught by staleness guard)",
                 (long long)mRightStick.fingerId, (long long)fingerId);
    }

    for (int i = 0; i < NUM_BUTTONS; ++i) {
        if (mButtons[i].fingerId == fingerId && mButtons[i].pressed) {
            ReleaseButton(i, controller);
        } else if (mButtons[i].pressed) {
            HUD_LOGI("UP fingerId mismatch BTN[%s]: btn.fingerId=%lld != event.fingerId=%lld (will be caught by staleness guard)",
                     mButtons[i].label, (long long)mButtons[i].fingerId, (long long)fingerId);
        }
    }
}

// ── Global release ───────────────────────────────────────────────────

void TouchGui::ReleaseAllInputs()
{
    HUD_LOGI("ReleaseAllInputs() called");

    UserController* controller = GetInputManager()->GetController(0);
    if (!controller) {
        HUD_LOGE("ReleaseAllInputs: GetController(0) returned null!");
        return;
    }

    for (int i = 0; i < NUM_BUTTONS; ++i) {
        if (mButtons[i].pressed) {
            mButtons[i].pressed       = false;
            mButtons[i].fingerId      = -1;
            mButtons[i].lastEventTime = 0;
            controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(0.0f);
        }
    }

    mLeftStick.active        = false;
    mLeftStick.fingerId      = -1;
    mLeftStick.currX         = 0.0f;
    mLeftStick.currY         = 0.0f;
    mLeftStick.lastEventTime = 0;
    controller->GetInputButton(mLeftStick.axisX)->SetValue(0.0f);
    controller->GetInputButton(mLeftStick.axisY)->SetValue(0.0f);

    mRightStick.active        = false;
    mRightStick.fingerId      = -1;
    mRightStick.currX         = 0.0f;
    mRightStick.currY         = 0.0f;
    mRightStick.lastEventTime = 0;
    controller->GetInputButton(mRightStick.axisX)->SetValue(0.0f);
    controller->GetInputButton(mRightStick.axisY)->SetValue(0.0f);
}

// ── Staleness guard ──────────────────────────────────────────────────

void TouchGui::AutoReleaseStaleInputs(UserController* controller, radTime64 now)
{
    // Check left stick.
    if (mLeftStick.active && (now - mLeftStick.lastEventTime) > STALE_TIMEOUT_US) {
        HUD_LOGI("AutoRelease LStick (stale, idle %lldus)", (long long)(now - mLeftStick.lastEventTime));
        mLeftStick.active        = false;
        mLeftStick.fingerId      = -1;
        mLeftStick.currX         = 0.0f;
        mLeftStick.currY         = 0.0f;
        mLeftStick.lastEventTime = 0;
        controller->GetInputButton(mLeftStick.axisX)->SetValue(0.0f);
        controller->GetInputButton(mLeftStick.axisY)->SetValue(0.0f);
    }

    // Check right stick.
    if (mRightStick.active && (now - mRightStick.lastEventTime) > STALE_TIMEOUT_US) {
        HUD_LOGI("AutoRelease RStick (stale, idle %lldus)", (long long)(now - mRightStick.lastEventTime));
        mRightStick.active        = false;
        mRightStick.fingerId      = -1;
        mRightStick.currX         = 0.0f;
        mRightStick.currY         = 0.0f;
        mRightStick.lastEventTime = 0;
        controller->GetInputButton(mRightStick.axisX)->SetValue(0.0f);
        controller->GetInputButton(mRightStick.axisY)->SetValue(0.0f);
    }

    // Check all buttons.
    for (int i = 0; i < NUM_BUTTONS; ++i) {
        if (mButtons[i].pressed && (now - mButtons[i].lastEventTime) > STALE_TIMEOUT_US) {
            HUD_LOGI("AutoRelease BTN[%s] (stale, idle %lldus)",
                     mButtons[i].label, (long long)(now - mButtons[i].lastEventTime));
            mButtons[i].pressed       = false;
            mButtons[i].fingerId      = -1;
            mButtons[i].lastEventTime = 0;
            controller->GetInputButton(mButtons[i].buttonIndex)->SetValue(0.0f);
        }
    }
}

// ── Per-frame update ─────────────────────────────────────────────────

void TouchGui::Update(unsigned int /*elapsedTime*/)
{
    UserController* controller = GetInputManager()->GetController(0);
    if (controller) {
        AutoReleaseStaleInputs(controller, radTimeGetMicroseconds64());
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Rendering — desktop debug builds only (Android uses Java overlay).
// ═══════════════════════════════════════════════════════════════════════

void TouchGui::Render()
{
#ifdef RAD_ANDROID
    return; // HUD rendered by Java GamepadOverlayView
#endif
    if (!mVisible) return;

    pddiRenderContext* pddi = p3d::pddi;
    mScreenWidth  = static_cast<float>(p3d::display->GetWidth());
    mScreenHeight = static_cast<float>(p3d::display->GetHeight());

    pddi->PushState(PDDI_STATE_ALL);
    pddi->SetProjectionMode(PDDI_PROJECTION_DEVICE);
    pddi->SetZWrite(false);
    pddi->SetZCompare(PDDI_COMPARE_ALWAYS);

    pddiRect fullScreen(0, 0, static_cast<int>(mScreenWidth), static_cast<int>(mScreenHeight));
    pddi->SetScissor(&fullScreen);

    // Sticks
    const pddiColour stickCol(255, 234, 2, 40);   // Simpsons Yellow, transparent
    const pddiColour knobCol (17,  31,  161, 120); // Simpsons Blue
    RenderStick(mLeftStick,  stickCol, knobCol);
    RenderStick(mRightStick, stickCol, knobCol);

    // Buttons
    RenderButtons();

    pddi->PopState(PDDI_STATE_ALL);
}

void TouchGui::RenderStick(const TouchJoystick& stick, pddiColour base, pddiColour knob)
{
    const float bx = stick.centerX * mScreenWidth;
    const float by = stick.centerY * mScreenHeight;
    const float kw = 60.0f, kh = 60.0f;
    const float kw2 = 30.0f, kh2 = 30.0f;

    // Base ring
    DrawRect(bx - kw, by - kh, kw * 2.0f, kh * 2.0f, base);

    // Knob
    const float kx = (stick.centerX + stick.currX * 0.08f) * mScreenWidth;
    const float ky = (stick.centerY + stick.currY * 0.08f) * mScreenHeight;
    DrawRect(kx - kw2, ky - kh2, kw2 * 2.0f, kh2 * 2.0f, knob);
}

void TouchGui::RenderButtons()
{
    pddiRenderContext* pddi = p3d::pddi;

    for (int i = 0; i < NUM_BUTTONS; ++i) {
        const TouchButton& btn = mButtons[i];

        const pddiColour fill  = btn.pressed ? pddiColour(255, 255, 0, 180) : pddiColour(255, 234, 2, 100);
        const pddiColour border(17, 31, 161, 160); // Simpsons Blue

        const float bx = btn.x * mScreenWidth;
        const float by = btn.y * mScreenHeight;
        const float bw = btn.w * mScreenWidth;
        const float bh = btn.h * mScreenHeight;

        // Border
        DrawRect(bx - bw * 0.5f - 3.0f, by - bh * 0.5f - 3.0f, bw + 6.0f, bh + 6.0f, border);
        // Fill
        DrawRect(bx - bw * 0.5f, by - bh * 0.5f, bw, bh, fill);

        // Label
        pddi->DrawString(btn.label, static_cast<int>(bx - 9.0f), static_cast<int>(by - 9.0f), pddiColour(255, 255, 255));
        pddi->DrawString(btn.label, static_cast<int>(bx - 10.0f), static_cast<int>(by - 10.0f), pddiColour(17, 31, 161));
    }
}

void TouchGui::DrawRect(float x, float y, float w, float h, pddiColour colour)
{
    pddiPrimStream* stream = p3d::pddi->BeginPrims(NULL, PDDI_PRIM_TRIANGLES, PDDI_V_C, 6);
    p3d::pddi->SetProjectionMode(PDDI_PROJECTION_DEVICE);

    stream->Colour(colour); stream->Coord(x,     y,     0.0f);
    stream->Colour(colour); stream->Coord(x + w, y,     0.0f);
    stream->Colour(colour); stream->Coord(x,     y + h, 0.0f);

    stream->Colour(colour); stream->Coord(x + w, y,     0.0f);
    stream->Colour(colour); stream->Coord(x + w, y + h, 0.0f);
    stream->Colour(colour); stream->Coord(x,     y + h, 0.0f);

    p3d::pddi->EndPrims(stream);
}
