#ifndef TOUCH_GUI_H
#define TOUCH_GUI_H

#include <SDL.h>
#include <pddi/pddi.hpp>
#include <radtime.hpp>

class UserController;

// ============================================================================
// TouchGui — on-screen touch controls for mobile platforms.
//
// Responsibilities:
//   1. Dispatch SDL touch events (FINGERDOWN/MOTION/UP) to sticks & buttons.
//   2. Track per-finger ownership so multi-touch works correctly.
//   3. Auto-release inputs that go stale (missed FINGERUP due to Android
//      gesture interception or SDL fingerId mismatch).
//
// On Android, visual rendering is handled by the Java GamepadOverlayView.
// The C++ Render() path exists only for desktop debug builds.
// ============================================================================
class TouchGui
{
public:
    static TouchGui* GetInstance();
    static void CreateInstance();
    static void DestroyInstance();

    void Init();
    void Update(unsigned int elapsedTime);

    // Rendering — no-op on Android (Java overlay handles visuals).
    void Render();

    // Main touch-event entry point. Called from the SDL event loop.
    void HandleTouchEvent(SDL_Event* event);

    void SetVisible(bool visible);
    bool IsVisible() const { return mVisible; }

    // Immediately reset all sticks and buttons to their neutral state.
    void ReleaseAllInputs();

private:
    TouchGui();
    ~TouchGui();

    // ── Data structures ──────────────────────────────────────────────

    struct TouchButton
    {
        float x, y, w, h;       // normalised [0..1], centre-relative
        int   buttonIndex;      // InputManager enum value
        bool  pressed;
        SDL_FingerID fingerId;   // which finger owns this button (-1 = none)
        const char*  label;      // debug label
        radTime64    lastEventTime; // µs, for staleness detection

        bool Contains(float tx, float ty) const
        {
            return tx >= x - w * 0.5f && tx <= x + w * 0.5f &&
                   ty >= y - h * 0.5f && ty <= y + h * 0.5f;
        }
    };

    struct TouchJoystick
    {
        float centerX, centerY, radius;
        float currX, currY;      // current knob offset (-1..1)
        int   axisX, axisY;      // InputManager axis enum values
        SDL_FingerID fingerId;    // which finger owns this stick (-1 = none)
        bool  active;
        radTime64 lastEventTime;  // µs, for staleness detection
    };

    enum ButtonIndex
    {
        BTN_DPAD_UP, BTN_DPAD_DOWN, BTN_DPAD_LEFT, BTN_DPAD_RIGHT,
        BTN_A, BTN_B, BTN_X, BTN_Y,
        BTN_START, BTN_SELECT,
        BTN_L1, BTN_R1,
        NUM_BUTTONS
    };

    // ── Member data ──────────────────────────────────────────────────

    static TouchGui* spInstance;

    bool          mVisible;
    float         mScreenWidth;
    float         mScreenHeight;

    TouchButton   mButtons[NUM_BUTTONS];
    TouchJoystick mLeftStick;
    TouchJoystick mRightStick;

    // ── Touch-event pipeline ─────────────────────────────────────────
    //
    // HandleTouchEvent dispatches to one of:
    //
    //   HandleFingerDown   — new finger touched the screen.
    //   HandleFingerMotion — existing finger moved.
    //   HandleFingerUp     — finger lifted.
    //
    // In HandleFingerUp, we release inputs by exact fingerId match only.
    // This preserves multi-touch (two fingers holding two buttons).
    //
    // The safety net for missed/dropped FINGERUP events is the staleness
    // timer in Update() → AutoReleaseStaleInputs (300 ms threshold).

    void HandleFingerDown(float x, float y, SDL_FingerID fingerId,
                          radTime64 timestamp, UserController* controller);
    void HandleFingerMotion(float x, float y, SDL_FingerID fingerId,
                            radTime64 timestamp, UserController* controller);
    void HandleFingerUp(SDL_FingerID fingerId, UserController* controller);

    // ── Stick helpers ────────────────────────────────────────────────

    // Returns true if the finger claimed the stick.
    bool ProcessStickDown(TouchJoystick& stick, float x, float y,
                          SDL_FingerID fingerId, radTime64 timestamp,
                          UserController* controller);

    void UpdateStickMotion(TouchJoystick& stick, float x, float y,
                           radTime64 timestamp, UserController* controller);

    void ReleaseStick(TouchJoystick& stick, UserController* controller);

    // ── Button helpers ───────────────────────────────────────────────

    void PressButton(int index, SDL_FingerID fingerId, radTime64 timestamp,
                     UserController* controller);
    void ReleaseButton(int index, UserController* controller);

    // ── Staleness guard ──────────────────────────────────────────────

    // Called every frame by Update().  If a stick or button hasn't seen
    // a touch event for STALE_TIMEOUT_US, it is force-released.  This is
    // the only place that handles "ghost" inputs — the touch dispatchers
    // themselves always match by exact fingerId.
    void AutoReleaseStaleInputs(UserController* controller, radTime64 now);

    static const radTime64 STALE_TIMEOUT_US = 300000; // 300 ms

    // ── Math ─────────────────────────────────────────────────────────

    static float ApplyDeadzone(float value, float deadzone);

    // ── Initialisation helper ────────────────────────────────────────

    static void InitButton(TouchButton& btn, float x, float y, float w, float h,
                           int buttonIndex, const char* label);

    // ── Rendering helpers (desktop debug only) ───────────────────────

    void DrawRect(float x, float y, float w, float h, pddiColour colour);
    void RenderStick(const TouchJoystick& stick, pddiColour base, pddiColour knob);
    void RenderButtons();
};

#endif // TOUCH_GUI_H
