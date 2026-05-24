#ifndef TOUCH_GUI_H
#define TOUCH_GUI_H

#include <SDL.h>
#include <pddi/pddi.hpp>

class TouchGui {
public:
    static TouchGui* GetInstance();
    static void CreateInstance();
    static void DestroyInstance();

    void Init();
    void Update(unsigned int elapsedTime);
    void Render();
    void HandleTouchEvent(SDL_Event* event);

    void SetVisible(bool visible);
    bool IsVisible() const { return mVisible; }

private:
    TouchGui();
    ~TouchGui();

    static TouchGui* spInstance;
    bool mVisible;
    float mScreenWidth;
    float mScreenHeight;

    struct TouchButton {
        float x, y, w, h; // Normalized coordinates (0.0 to 1.0)
        int buttonIndex;
        bool pressed;
        SDL_FingerID fingerId;
        const char* label;
    };

    enum {
        BTN_DPAD_UP,
        BTN_DPAD_DOWN,
        BTN_DPAD_LEFT,
        BTN_DPAD_RIGHT,
        BTN_A,
        BTN_B,
        BTN_X,
        BTN_Y,
        BTN_START,
        BTN_SELECT,
        BTN_L1,
        BTN_R1,
        NUM_BUTTONS
    };

    TouchButton mButtons[NUM_BUTTONS];

    struct TouchJoystick {
        float centerX, centerY, radius;
        float currX, currY;
        int axisX, axisY;
        SDL_FingerID fingerId;
        bool active;
    };

    TouchJoystick mLeftStick;
    TouchJoystick mRightStick;

    void DrawRect(float x, float y, float w, float h, pddiColour colour);
    void DrawCircle(float x, float y, float radius, pddiColour colour);
    void DrawDonut(float x, float y, float outerRadius, float innerRadius, pddiColour colour);
    void UpdateButton(int index, float x, float y, bool down, SDL_FingerID fingerId);
    void UpdateJoystick(TouchJoystick& stick, float x, float y, bool down, SDL_FingerID fingerId);
};

#endif
