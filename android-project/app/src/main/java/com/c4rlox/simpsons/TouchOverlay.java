package com.c4rlox.simpsons;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.View;

/**
 * TouchOverlay - Java HUD for on-screen touch controls.
 * Renders virtual gamepad buttons and joysticks using Android Canvas.
 * Visibility toggles based on physical gamepad connection status.
 *
 * Communicates with the native C++ TouchGui via JNI to get button states
 * and gamepad connection status.
 */
public class TouchOverlay extends View {

    // Button indices matching TouchGui.h
    private static final int BTN_DPAD_UP = 0;
    private static final int BTN_DPAD_DOWN = 1;
    private static final int BTN_DPAD_LEFT = 2;
    private static final int BTN_DPAD_RIGHT = 3;
    private static final int BTN_A = 4;
    private static final int BTN_B = 5;
    private static final int BTN_X = 6;
    private static final int BTN_Y = 7;
    private static final int BTN_START = 8;
    private static final int BTN_SELECT = 9;
    private static final int BTN_L1 = 10;
    private static final int BTN_R1 = 11;

    // Simpsons-themed colors
    private static final int SIMPSON_YELLOW = Color.rgb(255, 217, 15);
    private static final int SIMPSON_BLUE = Color.rgb(31, 74, 184);

    // Paint objects
    private Paint fillPaint;
    private Paint borderPaint;
    private Paint textPaint;
    private Paint textShadowPaint;
    private Paint joystickBasePaint;
    private Paint joystickKnobPaint;

    // Physical gamepad detection
    private boolean physicalGamepadConnected = false;
    private boolean overlayEnabled = true;

    // Handler for periodic state checks
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stateCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkStateAndUpdate();
            handler.postDelayed(this, 250); // Check every 250ms
        }
    };

    // Native JNI methods
    private static native boolean nativeIsButtonPressed(int buttonIndex);
    private static native float nativeGetJoystickX(int stickIndex);
    private static native float nativeGetJoystickY(int stickIndex);
    private static native boolean nativeIsTouchGuiVisible();
    private static native boolean nativeIsPhysicalGamepadConnected();

    public TouchOverlay(Context context) {
        super(context);
        init();
    }

    private void init() {
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3.0f);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        textShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textShadowPaint.setColor(Color.BLACK);
        textShadowPaint.setTextAlign(Paint.Align.CENTER);
        textShadowPaint.setTypeface(Typeface.DEFAULT_BOLD);

        joystickBasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        joystickBasePaint.setStyle(Paint.Style.STROKE);
        joystickBasePaint.setStrokeWidth(4.0f);

        joystickKnobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.postDelayed(stateCheckRunnable, 100);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(stateCheckRunnable);
    }

    private void checkStateAndUpdate() {
        boolean nativeVisible = false;
        try {
            nativeVisible = nativeIsTouchGuiVisible();
        } catch (UnsatisfiedLinkError e) {
            nativeVisible = true; // Default to visible if native not ready
        }

        boolean gamepadConnected = false;
        try {
            gamepadConnected = nativeIsPhysicalGamepadConnected();
        } catch (UnsatisfiedLinkError e) {
            // Fallback: use Android InputDevice API
            gamepadConnected = checkPhysicalGamepadAndroid();
        }

        physicalGamepadConnected = gamepadConnected;

        // Show overlay only when:
        // 1. Native TouchGui says it should be visible (no gamepad from C++ side)
        // 2. AND no physical gamepad detected
        boolean shouldShow = nativeVisible && !gamepadConnected;

        if (shouldShow != overlayEnabled) {
            overlayEnabled = shouldShow;
            if (shouldShow) {
                setVisibility(View.VISIBLE);
            } else {
                setVisibility(View.GONE);
            }
        }

        // Always invalidate if overlay is visible, to update button states
        if (overlayEnabled) {
            postInvalidate();
        }
    }

    private boolean checkPhysicalGamepadAndroid() {
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null) {
                int sources = device.getSources();
                if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                    // Ensure it's not a virtual device
                    if (!device.isVirtual()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!overlayEnabled) return;

        float screenWidth = getWidth();
        float screenHeight = getHeight();

        // Draw semi-transparent background dim
        fillPaint.setColor(Color.argb(25, 0, 0, 0));
        canvas.drawRect(0, 0, screenWidth, screenHeight, fillPaint);

        drawJoysticks(canvas, screenWidth, screenHeight);
        drawButtons(canvas, screenWidth, screenHeight);
    }

    private void drawJoysticks(Canvas canvas, float sw, float sh) {
        float stickSize = 0.14f * sw;
        float knobSize = 0.05f * sw;

        // Left stick (bottom-left)
        float lx = 0.0f, ly = 0.0f;
        try {
            lx = nativeGetJoystickX(0);
            ly = nativeGetJoystickY(0);
        } catch (UnsatisfiedLinkError ignored) {}

        drawSingleJoystick(canvas, 0.12f * sw, 0.78f * sh, stickSize, knobSize, lx, ly);

        // Right stick (right side above face buttons)
        float rx = 0.0f, ry = 0.0f;
        try {
            rx = nativeGetJoystickX(1);
            ry = nativeGetJoystickY(1);
        } catch (UnsatisfiedLinkError ignored) {}

        drawSingleJoystick(canvas, 0.88f * sw, 0.50f * sh, stickSize, knobSize, rx, ry);
    }

    private void drawSingleJoystick(Canvas canvas, float cx, float cy,
                                     float baseRadius, float knobRadius,
                                     float stickX, float stickY) {
        // Draw outer base ring
        joystickBasePaint.setColor(adjustAlpha(SIMPSON_YELLOW, 55));
        canvas.drawCircle(cx, cy, baseRadius, joystickBasePaint);

        // Draw inner fill
        fillPaint.setColor(adjustAlpha(SIMPSON_YELLOW, 25));
        canvas.drawCircle(cx, cy, baseRadius - 4, fillPaint);

        // Draw knob (positioned based on stick values)
        float knobOffset = baseRadius * 0.5f;
        float knobX = cx + stickX * knobOffset;
        float knobY = cy + stickY * knobOffset;

        joystickKnobPaint.setColor(adjustAlpha(SIMPSON_BLUE, 130));
        joystickKnobPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        canvas.drawCircle(knobX, knobY, knobRadius, joystickKnobPaint);

        // Knob highlight
        joystickKnobPaint.setColor(adjustAlpha(SIMPSON_BLUE, 80));
        canvas.drawCircle(knobX - knobRadius * 0.25f, knobY - knobRadius * 0.25f,
                          knobRadius * 0.4f, joystickKnobPaint);
    }

    private void drawButtons(Canvas canvas, float sw, float sh) {
        float dpadSize = 0.05f;
        float dpadX = 0.12f;
        float dpadY = 0.50f;

        // D-Pad
        drawSingleButton(canvas, dpadX * sw, (dpadY - dpadSize) * sh, dpadSize * sw, "U", BTN_DPAD_UP, true);
        drawSingleButton(canvas, dpadX * sw, (dpadY + dpadSize) * sh, dpadSize * sw, "D", BTN_DPAD_DOWN, true);
        drawSingleButton(canvas, (dpadX - dpadSize) * sw, dpadY * sh, dpadSize * sw, "L", BTN_DPAD_LEFT, true);
        drawSingleButton(canvas, (dpadX + dpadSize) * sw, dpadY * sh, dpadSize * sw, "R", BTN_DPAD_RIGHT, true);

        // Face buttons (diamond pattern)
        float faceX = 0.88f;
        float faceY = 0.78f;
        float faceSize = 0.06f;
        float dist = 0.08f;

        drawSingleButton(canvas, faceX * sw, (faceY + dist) * sh, faceSize * sw, "A", BTN_A, true);
        drawSingleButton(canvas, (faceX + dist) * sw, faceY * sh, faceSize * sw, "B", BTN_B, true);
        drawSingleButton(canvas, (faceX - dist) * sw, faceY * sh, faceSize * sw, "X", BTN_X, true);
        drawSingleButton(canvas, faceX * sw, (faceY - dist) * sh, faceSize * sw, "Y", BTN_Y, true);

        // Start/Select
        drawSingleButton(canvas, 0.56f * sw, 0.06f * sh, 0.05f * sw, "START", BTN_START, false);
        drawSingleButton(canvas, 0.44f * sw, 0.06f * sh, 0.05f * sw, "SEL", BTN_SELECT, false);

        // Shoulder buttons
        drawSingleButton(canvas, 0.12f * sw, 0.06f * sh, 0.05f * sw, "L1", BTN_L1, false);
        drawSingleButton(canvas, 0.88f * sw, 0.06f * sh, 0.05f * sw, "R1", BTN_R1, false);
    }

    private void drawSingleButton(Canvas canvas, float x, float y,
                                   float size, String label,
                                   int buttonIndex, boolean isCircular) {
        float halfW = size * 0.5f;
        float halfH = size * 0.5f;

        // Check if pressed via JNI
        boolean pressed = false;
        try {
            pressed = nativeIsButtonPressed(buttonIndex);
        } catch (UnsatisfiedLinkError ignored) {}

        int alpha = pressed ? 200 : 80;
        int borderAlpha = pressed ? 240 : 140;

        if (isCircular) {
            float radius = size * 0.5f;
            fillPaint.setColor(adjustAlpha(SIMPSON_YELLOW, alpha));
            borderPaint.setColor(adjustAlpha(SIMPSON_BLUE, borderAlpha));
            canvas.drawCircle(x, y, radius, fillPaint);
            canvas.drawCircle(x, y, radius, borderPaint);

            // Draw label
            float textSize = radius * 0.7f;
            textPaint.setTextSize(textSize);
            textShadowPaint.setTextSize(textSize);
            canvas.drawText(label, x + 1, y + textSize * 0.35f + 1, textShadowPaint);
            canvas.drawText(label, x, y + textSize * 0.35f, textPaint);
        } else {
            // Rounded rect button
            RectF rect = new RectF(x - halfW, y - halfH, x + halfW, y + halfH);
            fillPaint.setColor(adjustAlpha(SIMPSON_YELLOW, alpha));
            borderPaint.setColor(adjustAlpha(SIMPSON_BLUE, borderAlpha));
            canvas.drawRoundRect(rect, 8.0f, 8.0f, fillPaint);
            canvas.drawRoundRect(rect, 8.0f, 8.0f, borderPaint);

            // Draw label
            float textSize = halfH * 0.5f;
            textPaint.setTextSize(textSize);
            textShadowPaint.setTextSize(textSize);
            canvas.drawText(label, x + 1, y + textSize * 0.3f + 1, textShadowPaint);
            canvas.drawText(label, x, y + textSize * 0.3f, textPaint);
        }
    }

    private int adjustAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        // Don't consume touch events - let them pass through to SDL
        return false;
    }
}
