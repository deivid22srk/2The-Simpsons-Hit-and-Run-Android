package com.c4rlox.simpsons;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import android.content.SharedPreferences;
import org.libsdl.app.R;
import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLControllerManager;

/**
 * HUD de controle touch que sobrepoe a SDLSurface usando icones Xbox.
 * Botoes usam bitmaps dos assets; sticks sao desenhados como circulos.
 *
 * Inclui icone de configuracoes (engrenagem) que abre um painel com:
 *   - Toggle para exibir/ocultar o FPS real do jogo
 *   - Botao "Editor de Controles" para ajustar posicao, tamanho e opacidade
 *     de cada botao/analogico individualmente em tempo real.
 */
public class GamepadOverlayView extends View {

    // ── Log tag ───────────────────────────────────────────────────────
    private static final String TAG = "GamepadOverlay";

    // ── Cores tema Simpsons ───────────────────────────────────────────
    private static final int STK_BASE_ALPHA_DEF = 120;
    private static final int STK_KNOB_ALPHA_DEF = 140;

    // ── Settings UI constants ─────────────────────────────────────────
    private static final int SETTINGS_PANEL_ALPHA = 200;
    private static final int SETTINGS_TEXT_COLOR  = Color.WHITE;
    private static final int SETTINGS_BG_COLOR    = Color.argb(SETTINGS_PANEL_ALPHA, 20, 20, 50);
    private static final int SETTINGS_TOGGLE_ON   = Color.rgb(0, 200, 100);
    private static final int SETTINGS_TOGGLE_OFF  = Color.rgb(100, 100, 100);
    private static final int SETTINGS_ACCENT      = Color.rgb(255, 234, 2); // Simpsons yellow

    // ── Editor constants ──────────────────────────────────────────────
    private static final float EDITOR_SIZE_STEP   = 0.01f;
    private static final float EDITOR_SIZE_MIN    = 0.03f;
    private static final float EDITOR_SIZE_MAX    = 0.25f;
    private static final int   EDITOR_ALPHA_STEP  = 10;
    private static final int   EDITOR_ALPHA_MIN   = 30;
    private static final int   EDITOR_ALPHA_MAX   = 255;

    // ── Classes de dados ──────────────────────────────────────────────
    private static class Btn {
        String label;
        float nx, ny, nw, nh;
        final RectF rect = new RectF();
        int resId;
        int alpha = 255;

        Btn(String label, float nx, float ny, float nw, float nh, int resId) {
            this.label = label;
            this.nx = nx; this.ny = ny; this.nw = nw; this.nh = nh;
            this.resId = resId;
        }
    }

    private static class Stk {
        float ncx, ncy, nr;
        float cx, cy, r;
        int baseAlpha = STK_BASE_ALPHA_DEF;
        int knobAlpha = STK_KNOB_ALPHA_DEF;

        Stk(float ncx, float ncy, float nr) {
            this.ncx = ncx; this.ncy = ncy; this.nr = nr;
        }
    }

    // ── Native HUD position presets (strategic, bigger buttons) ───────
    private static final float[][] NATIVE_BTN_POS = {
        {0.120f, 0.680f, 0.100f, 0.100f}, // 0: D-Pad UP
        {0.120f, 0.800f, 0.100f, 0.100f}, // 1: D-Pad DOWN
        {0.055f, 0.740f, 0.100f, 0.100f}, // 2: D-Pad LEFT
        {0.185f, 0.740f, 0.100f, 0.100f}, // 3: D-Pad RIGHT
        {0.830f, 0.720f, 0.150f, 0.150f}, // 4: A (big action button)
        {0.830f, 0.545f, 0.130f, 0.130f}, // 5: B
        {0.935f, 0.630f, 0.100f, 0.100f}, // 6: X (Punch/Talk/Camera)
        {0.725f, 0.630f, 0.110f, 0.110f}, // 7: Y (Enter/Exit Car)
        {0.550f, 0.030f, 0.100f, 0.060f}, // 8: START
        {0.420f, 0.030f, 0.100f, 0.060f}, // 9: SELECT
        {0.100f, 0.030f, 0.180f, 0.070f}, // 10: L1
        {0.800f, 0.030f, 0.180f, 0.070f}, // 11: R1
        {0.950f, 0.030f, 0.060f, 0.060f}, // 12: CONFIG (Settings gear)
    };

    // ── Gamepad default positions ─────────────────────────────────────
    private static final float[][] GAMEPAD_BTN_POS = {
        {0.215f, 0.725f, 0.075f, 0.075f}, // 0: D-Pad UP
        {0.215f, 0.835f, 0.075f, 0.075f}, // 1: D-Pad DOWN
        {0.160f, 0.780f, 0.075f, 0.075f}, // 2: D-Pad LEFT
        {0.270f, 0.780f, 0.075f, 0.075f}, // 3: D-Pad RIGHT
        {0.875f, 0.665f, 0.075f, 0.075f}, // 4: A
        {0.930f, 0.610f, 0.075f, 0.075f}, // 5: B
        {0.820f, 0.610f, 0.075f, 0.075f}, // 6: X
        {0.875f, 0.555f, 0.075f, 0.075f}, // 7: Y
        {0.560f, 0.050f, 0.120f, 0.060f}, // 8: START
        {0.440f, 0.050f, 0.120f, 0.060f}, // 9: SELECT
        {0.120f, 0.050f, 0.160f, 0.080f}, // 10: L1
        {0.880f, 0.050f, 0.160f, 0.080f}, // 11: R1
        {0.950f, 0.050f, 0.060f, 0.060f}, // 12: CONFIG
    };

    // ── Definicões de botoes (coord normalizadas) ─────────────────────
    private static final int BTN_IDX_SETTINGS = 12;

    private static final Btn[] BTNS = {
        // ── D-Pad (Xbox style: bottom-left, centered at x=0.215, y=0.780) ──────
        new Btn("D-Pad: UP",    0.215f, 0.725f, 0.075f, 0.075f, 0),  // 0
        new Btn("D-Pad: DOWN",  0.215f, 0.835f, 0.075f, 0.075f, 0),  // 1
        new Btn("D-Pad: LEFT",  0.160f, 0.780f, 0.075f, 0.075f, 0),  // 2
        new Btn("D-Pad: RIGHT", 0.270f, 0.780f, 0.075f, 0.075f, 0),  // 3
        // ── Face Buttons A/B/X/Y (Xbox style: top-right, centered at x=0.875, y=0.610) ──
        new Btn("Face: A",      0.875f, 0.665f, 0.075f, 0.075f, 0),  // 4
        new Btn("Face: B",      0.930f, 0.610f, 0.075f, 0.075f, 0),  // 5
        new Btn("Face: X",      0.820f, 0.610f, 0.075f, 0.075f, 0),  // 6
        new Btn("Face: Y",      0.875f, 0.555f, 0.075f, 0.075f, 0),  // 7
        // ── Top buttons ─────────────────────────────────────────
        new Btn("START",        0.560f, 0.050f, 0.120f, 0.060f, 0),  // 8
        new Btn("SELECT",       0.440f, 0.050f, 0.120f, 0.060f, 0),  // 9
        new Btn("L1",           0.120f, 0.050f, 0.160f, 0.080f, 0),  // 10
        new Btn("R1",           0.880f, 0.050f, 0.160f, 0.080f, 0),  // 11
        new Btn("CONFIG",       0.950f, 0.050f, 0.060f, 0.060f, 0),  // 12: Settings gear
    };

    // ── Definicões de sticks ──────────────────────────────────────────
    private static final Stk[] STKS = {
        new Stk(0.125f, 0.610f, 0.090f),  // Left stick (Xbox style: top-left)
        new Stk(0.785f, 0.780f, 0.090f),  // Right stick (Xbox style: bottom-right)
    };

    // ── Nomes legiveis dos sticks para o editor ───────────────────────
    private static final String[] STICK_NAMES = {
        "Left Stick", "Right Stick"
    };

    // ── Keycodes Android para cada botao (enviados ao SDL nativo) ────
    private static final int[] BTN_KEYCODES = {
        KeyEvent.KEYCODE_DPAD_UP,       // 0: D-Pad UP
        KeyEvent.KEYCODE_DPAD_DOWN,     // 1: D-Pad DOWN
        KeyEvent.KEYCODE_DPAD_LEFT,     // 2: D-Pad LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT,    // 3: D-Pad RIGHT
        KeyEvent.KEYCODE_BUTTON_A,      // 4: A
        KeyEvent.KEYCODE_BUTTON_B,      // 5: B
        KeyEvent.KEYCODE_BUTTON_X,      // 6: X
        KeyEvent.KEYCODE_BUTTON_Y,      // 7: Y
        KeyEvent.KEYCODE_BUTTON_START,  // 8: START
        KeyEvent.KEYCODE_BUTTON_SELECT, // 9: SELECT
        KeyEvent.KEYCODE_BUTTON_L1,     // 10: L1
        KeyEvent.KEYCODE_BUTTON_R1,     // 11: R1
        0,                              // 12: SETTINGS (no key event)
    };

    // ── Resource IDs para bitmaps ─────────────────────────────────────
    private static final int[] BTN_RES_IDS = {
        R.drawable.dpad_up,            // 0: UP
        R.drawable.dpad_down,          // 1: DOWN
        R.drawable.dpad_left,          // 2: LEFT
        R.drawable.dpad_right,         // 3: RIGHT
        R.drawable.button_a,           // 4: A
        R.drawable.button_b,           // 5: B
        R.drawable.button_x,           // 6: X
        R.drawable.button_y,           // 7: Y
        R.drawable.button_start_menu,  // 8: START
        R.drawable.button_select_view, // 9: SELECT
        R.drawable.button_lt,          // 10: L1
        R.drawable.button_lt,          // 11: R1 (flipped in onDraw)
        0,                             // 12: SETTINGS (drawn programmatically)
    };

    // ── Settings panel rects ──────────────────────────────────────────
    private RectF mSettingsPanelRect = new RectF();
    private RectF mFpsToggleRect = new RectF();
    private RectF mCameraSwipeToggleRect = new RectF();
    private RectF mNativeHudToggleRect = new RectF();
    private RectF mSensDownRect = new RectF();
    private RectF mSensUpRect = new RectF();
    private RectF mSettingsCloseRect = new RectF();
    private RectF mEditorBtnRect = new RectF();  // "Editor de Controles" button

    // ── Editor panel rects ────────────────────────────────────────────
    private RectF mEditorPanelRect = new RectF();
    private RectF mEditorSizeDownRect = new RectF();
    private RectF mEditorSizeUpRect = new RectF();
    private RectF mEditorAlphaDownRect = new RectF();
    private RectF mEditorAlphaUpRect = new RectF();
    private RectF mEditorResetRect = new RectF();
    private RectF mEditorCloseRect = new RectF();

    // ── Estado de toque (multi-touch real) ────────────────────────────
    private int[] mButtonPointerIds = new int[BTNS.length];
    private int[] mStickPointerIds  = new int[STKS.length];
    private float[] mStickKnobX     = new float[STKS.length];
    private float[] mStickKnobY     = new float[STKS.length];

    // ── Bitmaps dos botoes ────────────────────────────────────────────
    private Bitmap[] mBtnBmps;
    private Bitmap mBmpPular;
    private Bitmap mBmpCorrer;
    private Bitmap mBmpAcelerar;
    private Bitmap mBmpFrear;
    private Bitmap mBmpEntrarCarro;
    private Bitmap mBmpSoco;
    private Bitmap mBmpMudarCamera;
    private boolean mNativeLayoutApplied = false;

    // ── Paints ────────────────────────────────────────────────────────
    private Paint mPStkBase;
    private Paint mPStkKnob;
    private Paint mPBmp;

    // ── Settings state ────────────────────────────────────────────────
    private boolean mShowSettings = false;
    private boolean mShowFPS      = false;
    private boolean mNativeAvailable = false;
    private boolean mSwipeCameraEnabled = false;
    private float   mSwipeSensitivity = 1.0f;
    private boolean mNativeHudEnabled = false;

    // ── Settings touch tracking ───────────────────────────────────────
    private int mSettingsPointerId = -1;
    private boolean mSettingsClosePressed = false;
    private boolean mFpsTogglePressed = false;
    private boolean mCameraSwipeTogglePressed = false;
    private boolean mNativeHudTogglePressed = false;
    private boolean mSensDownPressed = false;
    private boolean mSensUpPressed = false;
    private boolean mEditorBtnPressed = false;

    // ── Swipe camera touch tracking ───────────────────────────────────
    private int mSwipePointerId = -1;
    private float mSwipeLastX = 0f;
    private float mSwipeLastY = 0f;
    private final android.os.Handler mSwipeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable mSwipeResetRunnable = new Runnable() {
        @Override
        public void run() {
            SDLControllerManager.onNativeJoy(9999, 2, 0.0f);
            SDLControllerManager.onNativeJoy(9999, 3, 0.0f);
        }
    };

    // ── Editor de Controles state ─────────────────────────────────────
    private boolean mEditorMode = false;
    private int mEditorSelectedIdx = -1;          // -1 = nothing selected
    private boolean mEditorSelectedIsStick = false;
    private int mEditorPointerId = -1;
    private boolean mEditorDragging = false;
    private float mEditorDragOffX, mEditorDragOffY;
    private boolean mEditorSizeDownPressed = false;
    private boolean mEditorSizeUpPressed = false;
    private boolean mEditorAlphaDownPressed = false;
    private boolean mEditorAlphaUpPressed = false;
    private boolean mEditorResetPressed = false;
    private boolean mEditorClosePressed = false;

    // ── Saved originals for Reset ─────────────────────────────────────
    private float[][] mBtnOrigins;  // [index][nx, ny, nw, nh, alpha]
    private float[][] mStkOrigins;  // [index][ncx, ncy, nr, baseAlpha, knobAlpha]

    // ── Pre-allocated Paints ──────────────────────────────────────────
    // Gear icon paints
    private Paint mGearStrokePaint;
    private Paint mGearDotPaint;
    // Settings panel paints
    private Paint mPanelBgPaint;
    private Paint mPanelBorderPaint;
    private Paint mPanelTitlePaint;
    private Paint mPanelLinePaint;
    private Paint mPanelLabelPaint;
    private Paint mToggleBgPaint;
    private Paint mToggleThumbPaint;
    private Paint mToggleTextPaint;
    private Paint mFpsValuePaint;
    private Paint mCloseBtnPaint;
    // FPS overlay paints
    private Paint mFpsBgPaint;
    private Paint mFpsTextPaint;
    // Editor paints
    private Paint mEditorBgPaint;
    private Paint mEditorBorderPaint;
    private Paint mEditorLabelPaint;
    private Paint mEditorValuePaint;
    private Paint mEditorBtnBgPaint;
    private Paint mEditorBtnTextPaint;
    private Paint mEditorHighlightPaint;
    private Paint mEditorDragHintPaint;
    // Editor: highlight overlay for selected element
    private Paint mEditorOverlayPaint;

    // ── Construtor ────────────────────────────────────────────────────
    public GamepadOverlayView(Context ctx) {
        super(ctx);
        mBtnBmps = new Bitmap[BTNS.length];
        for (int i = 0; i < BTNS.length; i++) mButtonPointerIds[i] = -1;
        for (int i = 0; i < STKS.length; i++)  mStickPointerIds[i] = -1;

        // Init originals arrays
        mBtnOrigins = new float[BTNS.length][5];
        mStkOrigins = new float[STKS.length][5];
    }

    // ── Save current values as originals (for Reset) ──────────────────
    private void saveOrigins() {
        for (int i = 0; i < BTNS.length; i++) {
            Btn b = BTNS[i];
            mBtnOrigins[i][0] = b.nx;
            mBtnOrigins[i][1] = b.ny;
            mBtnOrigins[i][2] = b.nw;
            mBtnOrigins[i][3] = b.nh;
            mBtnOrigins[i][4] = b.alpha;
        }
        for (int i = 0; i < STKS.length; i++) {
            Stk s = STKS[i];
            mStkOrigins[i][0] = s.ncx;
            mStkOrigins[i][1] = s.ncy;
            mStkOrigins[i][2] = s.nr;
            mStkOrigins[i][3] = s.baseAlpha;
            mStkOrigins[i][4] = s.knobAlpha;
        }
    }

    private void resetToOrigins() {
        final int w = getWidth();
        final int h = getHeight();
        final float minDim = Math.min(w, h);

        for (int i = 0; i < BTNS.length; i++) {
            Btn b = BTNS[i];
            b.nx = mBtnOrigins[i][0];
            b.ny = mBtnOrigins[i][1];
            b.nw = mBtnOrigins[i][2];
            b.nh = mBtnOrigins[i][3];
            b.alpha = (int) mBtnOrigins[i][4];
            recalcBtnRect(i, w, h);
        }
        loadBitmaps();

        for (int i = 0; i < STKS.length; i++) {
            Stk s = STKS[i];
            s.ncx = mStkOrigins[i][0];
            s.ncy = mStkOrigins[i][1];
            s.nr  = mStkOrigins[i][2];
            s.baseAlpha  = (int) mStkOrigins[i][3];
            s.knobAlpha  = (int) mStkOrigins[i][4];
            s.cx = s.ncx * w;
            s.cy = s.ncy * h;
            s.r  = s.nr * minDim;
        }

        // Reset knob positions to center
        for (int i = 0; i < STKS.length; i++) {
            mStickKnobX[i] = STKS[i].cx;
            mStickKnobY[i] = STKS[i].cy;
        }
    }

    // ── Save/Load layout profiles using SharedPreferences ─────────────
    private void saveProfile() {
        Context context = getContext();
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences("GamepadOverlayProfile", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();

        for (int i = 0; i < BTNS.length; i++) {
            Btn b = BTNS[i];
            editor.putFloat("btn_" + i + "_nx", b.nx);
            editor.putFloat("btn_" + i + "_ny", b.ny);
            editor.putFloat("btn_" + i + "_nw", b.nw);
            editor.putFloat("btn_" + i + "_nh", b.nh);
            editor.putInt("btn_" + i + "_alpha", b.alpha);
        }
        for (int i = 0; i < STKS.length; i++) {
            Stk s = STKS[i];
            editor.putFloat("stk_" + i + "_ncx", s.ncx);
            editor.putFloat("stk_" + i + "_ncy", s.ncy);
            editor.putFloat("stk_" + i + "_nr", s.nr);
            editor.putInt("stk_" + i + "_baseAlpha", s.baseAlpha);
            editor.putInt("stk_" + i + "_knobAlpha", s.knobAlpha);
        }
        editor.putBoolean("swipe_camera_enabled", mSwipeCameraEnabled);
        editor.putFloat("swipe_camera_sensitivity", mSwipeSensitivity);
        editor.putBoolean("native_hud_enabled", mNativeHudEnabled);
        editor.apply();
        Log.i(TAG, "Profile saved successfully");
    }

    private void loadProfile() {
        Context context = getContext();
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences("GamepadOverlayProfile", Context.MODE_PRIVATE);
        
        mSwipeCameraEnabled = sp.getBoolean("swipe_camera_enabled", false);
        mSwipeSensitivity = sp.getFloat("swipe_camera_sensitivity", 1.0f);
        mNativeHudEnabled = sp.getBoolean("native_hud_enabled", false);

        // If native HUD was enabled, ensure the native layout is applied
        if (mNativeHudEnabled && getWidth() > 0 && getHeight() > 0) {
            applyLayout(NATIVE_BTN_POS);
            mNativeLayoutApplied = true;
            // Check native availability
            try {
                SimpsonsActivity.nativeGetHudContext();
                mNativeAvailable = true;
            } catch (UnsatisfiedLinkError e) { }
        }

        // If no saved settings exist, do nothing (use default layout)
        if (!sp.contains("btn_0_nx")) {
            return;
        }

        for (int i = 0; i < BTNS.length; i++) {
            Btn b = BTNS[i];
            b.nx = sp.getFloat("btn_" + i + "_nx", b.nx);
            b.ny = sp.getFloat("btn_" + i + "_ny", b.ny);
            b.nw = sp.getFloat("btn_" + i + "_nw", b.nw);
            b.nh = sp.getFloat("btn_" + i + "_nh", b.nh);
            b.alpha = sp.getInt("btn_" + i + "_alpha", b.alpha);
        }
        for (int i = 0; i < STKS.length; i++) {
            Stk s = STKS[i];
            s.ncx = sp.getFloat("stk_" + i + "_ncx", s.ncx);
            s.ncy = sp.getFloat("stk_" + i + "_ncy", s.ncy);
            s.nr = sp.getFloat("stk_" + i + "_nr", s.nr);
            s.baseAlpha = sp.getInt("stk_" + i + "_baseAlpha", s.baseAlpha);
            s.knobAlpha = sp.getInt("stk_" + i + "_knobAlpha", s.knobAlpha);
        }
        
        final int w = getWidth();
        final int h = getHeight();
        if (w > 0 && h > 0) {
            recalcAllRects(w, h);
            for (int i = 0; i < STKS.length; i++) {
                mStickKnobX[i] = STKS[i].cx;
                mStickKnobY[i] = STKS[i].cy;
            }
        }
        Log.i(TAG, "Profile loaded successfully");
    }

    // ── Recalculate a single button's rect ────────────────────────────
    private void recalcBtnRect(int idx, int w, int h) {
        Btn b = BTNS[idx];
        float minDim = Math.min(w, h);
        float cx = b.nx * w;
        float cy = b.ny * h;
        float halfW = (b.nw * minDim) / 2f;
        float halfH = (b.nh * minDim) / 2f;
        b.rect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
    }

    // ── Recalculate all rects ─────────────────────────────────────────
    private void recalcAllRects(int w, int h) {
        for (int i = 0; i < BTNS.length; i++) {
            recalcBtnRect(i, w, h);
        }
        float minDim = Math.min(w, h);
        for (Stk s : STKS) {
            s.cx = s.ncx * w;
            s.cy = s.ncy * h;
            s.r  = s.nr * minDim;
        }
    }

    // ── onSizeChanged ─────────────────────────────────────────────────
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;

        Log.i(TAG, String.format("onSizeChanged %dx%d (old=%dx%d)", w, h, oldw, oldh));

        recalcAllRects(w, h);

        // Settings panel: centered, ~65% width, ~78% height
        float panelW = w * 0.65f;
        float panelH = h * 0.78f;
        float panelL = (w - panelW) / 2f;
        float panelT = (h - panelH) / 2f;
        mSettingsPanelRect.set(panelL, panelT, panelL + panelW, panelT + panelH);

        // Close button (X): top-right of panel
        float closeSize = Math.min(w, h) * 0.05f;
        mSettingsCloseRect.set(
            mSettingsPanelRect.right - closeSize - 15f,
            mSettingsPanelRect.top + 15f,
            mSettingsPanelRect.right - 15f,
            mSettingsPanelRect.top + 15f + closeSize);

        // Cards horizontal bounds
        float cardW = panelW * 0.88f;
        float cardL = panelL + (panelW - cardW) / 2f;
        float cardR = cardL + cardW;

        // Card vertical bounds
        float rowH = panelH * 0.11f;
        float rowSpacing = panelH * 0.02f;

        // Row 1 (FPS Toggle Row):
        float row1T = panelT + panelH * 0.18f;
        float row1B = row1T + rowH;
        mFpsToggleRect.set(cardL, row1T, cardR, row1B);

        // Row 2 (Swipe Camera Toggle Row):
        float row2T = row1B + rowSpacing;
        float row2B = row2T + rowH;
        mCameraSwipeToggleRect.set(cardL, row2T, cardR, row2B);

        // Row 3 (Native HUD Toggle Row):
        float row3T = row2B + rowSpacing;
        float row3B = row3T + rowH;
        mNativeHudToggleRect.set(cardL, row3T, cardR, row3B);

        // Row 4 (Sensitivity Row):
        float row4T = row3B + rowSpacing;
        float row4B = row4T + rowH;
        
        // Position sensitivity buttons inside Row 4 Card
        float btnSize = rowH * 0.7f;
        float btnY = row4T + (rowH - btnSize) / 2f;
        float upRight = cardR - panelW * 0.04f;
        float upLeft = upRight - btnSize;
        mSensUpRect.set(upLeft, btnY, upRight, btnY + btnSize);

        float valW = panelW * 0.12f;
        float downRight = upLeft - valW;
        float downLeft = downRight - btnSize;
        mSensDownRect.set(downLeft, btnY, downRight, btnY + btnSize);

        // Editor button: bottom of panel
        float editorBtnW = panelW * 0.65f;
        float editorBtnH = panelH * 0.09f;
        float editorBtnL = panelL + (panelW - editorBtnW) / 2f;
        float editorBtnT = panelT + panelH * 0.82f;
        mEditorBtnRect.set(editorBtnL, editorBtnT, editorBtnL + editorBtnW, editorBtnT + editorBtnH);

        // ── Editor panel (bottom of screen, when editor mode is active) ──
        float editorPanelH = h * 0.30f;
        float editorPanelW = w * 0.92f;
        float editorPanelL = (w - editorPanelW) / 2f;
        float editorPanelT = h - editorPanelH - 12f;
        mEditorPanelRect.set(editorPanelL, editorPanelT, editorPanelL + editorPanelW, editorPanelT + editorPanelH);

        // Close button for editor panel (top-right)
        float edCloseSize = editorPanelH * 0.20f;
        mEditorCloseRect.set(
            mEditorPanelRect.right - edCloseSize - 8f,
            mEditorPanelRect.top + 8f,
            mEditorPanelRect.right - 8f,
            mEditorPanelRect.top + 8f + edCloseSize);

        // Size stepper: [-] [value] [+]
        float stepH = editorPanelH * 0.16f;
        float stepW = stepH * 1.4f;
        float labelAreaTop = mEditorPanelRect.top + editorPanelH * 0.10f;
        float row1CenterY = labelAreaTop + editorPanelH * 0.18f;
        float row2CenterY = labelAreaTop + editorPanelH * 0.42f;
        float btnRowY = mEditorPanelRect.bottom - editorPanelH * 0.18f;

        // Row 1: Size controls
        float sizeLabelLeft = mEditorPanelRect.left + editorPanelW * 0.10f;
        mEditorSizeDownRect.set(sizeLabelLeft, row1CenterY - stepH / 2f, sizeLabelLeft + stepW, row1CenterY + stepH / 2f);
        float sizeValLeft = mEditorSizeDownRect.right + editorPanelW * 0.04f;
        float sizeValW = editorPanelW * 0.18f;
        // The value is drawn inline, no rect needed
        mEditorSizeUpRect.set(sizeValLeft + sizeValW + editorPanelW * 0.04f, row1CenterY - stepH / 2f,
                              sizeValLeft + sizeValW + editorPanelW * 0.04f + stepW, row1CenterY + stepH / 2f);

        // Row 2: Opacity controls
        float alphaLabelLeft = mEditorPanelRect.left + editorPanelW * 0.10f;
        mEditorAlphaDownRect.set(alphaLabelLeft, row2CenterY - stepH / 2f, alphaLabelLeft + stepW, row2CenterY + stepH / 2f);
        float alphaValLeft = mEditorAlphaDownRect.right + editorPanelW * 0.04f;
        float alphaValW = editorPanelW * 0.18f;
        mEditorAlphaUpRect.set(alphaValLeft + alphaValW + editorPanelW * 0.04f, row2CenterY - stepH / 2f,
                               alphaValLeft + alphaValW + editorPanelW * 0.04f + stepW, row2CenterY + stepH / 2f);

        // Reset button
        float resetW = editorPanelW * 0.22f;
        float resetH = editorPanelH * 0.16f;
        float resetL = mEditorPanelRect.left + editorPanelW * 0.15f;
        mEditorResetRect.set(resetL, btnRowY - resetH / 2f, resetL + resetW, btnRowY + resetH / 2f);

        // Knobs dos sticks
        for (int i = 0; i < STKS.length; i++) {
            mStickKnobX[i] = STKS[i].cx;
            mStickKnobY[i] = STKS[i].cy;
        }

        // Inicializa paints
        initPaints();

        // Save originals for Reset
        if (oldw == 0 || oldh == 0) {
            saveOrigins();
            loadProfile();
        }

        loadBitmaps();
    }

    private boolean isBtnVisible(int idx) {
        if (!mNativeHudEnabled || mEditorMode) {
            return true; // Always show all buttons in Xbox mode or Editor mode
        }
        if (idx == BTN_IDX_SETTINGS) {
            return true; // Config gear is always visible
        }
        if (idx >= 0 && idx <= 3) {
            return true; // D-pad is always visible
        }
        if (idx == 8 || idx == 9 || idx == 10 || idx == 11) {
            return true; // Start, Select, L1, R1 are always visible
        }
        
        // Face buttons (4: A, 5: B, 6: X, 7: Y)
        int context = 0;
        try {
            context = SimpsonsActivity.nativeGetHudContext();
        } catch (UnsatisfiedLinkError e) {
            // Native library not loaded
        }
        if (context == 0 || context == 1) {
            // On Foot or Near Car
            if (idx == 4) return true; // A (Jump/Accelerate)
            if (idx == 5) return true; // B (Run/Brake)
            if (idx == 6) return true; // X (Punch/Talk)
            if (idx == 7) return context == 1; // Y (Enter Car, only when near car)
            return false;
        } else if (context == 2) {
            // Inside Car
            if (idx == 4) return true; // A (Accelerate)
            if (idx == 5) return true; // B (Brake)
            if (idx == 6) return true; // X (Change Camera)
            if (idx == 7) return true; // Y (Exit Car)
            return false;
        }
        return true;
    }

    private Bitmap loadResBitmap(Resources res, int resId, float targetW, float targetH) {
        if (resId == 0) return null;
        Bitmap raw = BitmapFactory.decodeResource(res, resId);
        if (raw == null) return null;
        float scale = Math.min(targetW / raw.getWidth(), targetH / raw.getHeight());
        int bw = Math.max(1, (int)(raw.getWidth() * scale));
        int bh = Math.max(1, (int)(raw.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(raw, bw, bh, true);
        if (scaled != raw) raw.recycle();
        return scaled;
    }

    private void initPaints() {
        mPStkBase = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPStkBase.setStyle(Paint.Style.STROKE);
        mPStkBase.setStrokeWidth(3f);
        mPStkBase.setColor(Color.argb(STK_BASE_ALPHA_DEF, 255, 255, 255));

        mPStkKnob = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPStkKnob.setStyle(Paint.Style.FILL);
        mPStkKnob.setColor(Color.argb(STK_KNOB_ALPHA_DEF, 255, 255, 255));

        mPBmp = new Paint(Paint.FILTER_BITMAP_FLAG);

        mGearStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGearDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mPanelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPanelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPanelTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPanelLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPanelLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mToggleBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mToggleThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mToggleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFpsValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCloseBtnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mFpsBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFpsTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Editor paints
        mEditorBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEditorBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEditorLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEditorValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEditorBtnBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEditorBtnTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEditorHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEditorDragHintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEditorOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    // ── loadBitmaps ───────────────────────────────────────────────────
    private void loadBitmaps() {
        for (int i = 0; i < mBtnBmps.length; i++) {
            if (mBtnBmps[i] != null) {
                mBtnBmps[i].recycle();
                mBtnBmps[i] = null;
            }
        }
        if (mBmpPular != null) { mBmpPular.recycle(); mBmpPular = null; }
        if (mBmpCorrer != null) { mBmpCorrer.recycle(); mBmpCorrer = null; }
        if (mBmpAcelerar != null) { mBmpAcelerar.recycle(); mBmpAcelerar = null; }
        if (mBmpFrear != null) { mBmpFrear.recycle(); mBmpFrear = null; }
        if (mBmpEntrarCarro != null) { mBmpEntrarCarro.recycle(); mBmpEntrarCarro = null; }
        if (mBmpSoco != null) { mBmpSoco.recycle(); mBmpSoco = null; }
        if (mBmpConfiguracoes != null) { mBmpConfiguracoes.recycle(); mBmpConfiguracoes = null; }
        if (mBmpMudarCamera != null) { mBmpMudarCamera.recycle(); mBmpMudarCamera = null; }

        Resources res = getResources();

        for (int i = 0; i < BTNS.length; i++) {
            int resId = BTN_RES_IDS[i];
            if (resId == 0) continue;

            Bitmap raw = BitmapFactory.decodeResource(res, resId);
            if (raw == null) continue;

            Btn b = BTNS[i];
            float targetW = b.rect.width();
            float targetH = b.rect.height();
            float scale = Math.min(targetW / raw.getWidth(), targetH / raw.getHeight());
            int bw = Math.max(1, (int)(raw.getWidth() * scale));
            int bh = Math.max(1, (int)(raw.getHeight() * scale));

            if (i == 10 || i == 11) {
                Bitmap scaled = Bitmap.createScaledBitmap(raw, bw, bh, true);
                Matrix matrix = new Matrix();
                matrix.preScale(i == 11 ? -1f : 1f, 1f);
                mBtnBmps[i] = Bitmap.createBitmap(scaled, 0, 0, bw, bh, matrix, true);
                if (scaled != raw) scaled.recycle();
            } else {
                mBtnBmps[i] = Bitmap.createScaledBitmap(raw, bw, bh, true);
            }
            if (mBtnBmps[i] != raw) raw.recycle();
        }

        float faceW = BTNS[4].rect.width();
        float faceH = BTNS[4].rect.height();
        if (faceW > 0 && faceH > 0) {
            mBmpPular = loadResBitmap(res, R.drawable.pular, faceW, faceH);
            mBmpCorrer = loadResBitmap(res, R.drawable.correr, faceW, faceH);
            mBmpAcelerar = loadResBitmap(res, R.drawable.acelerar, faceW, faceH);
            mBmpFrear = loadResBitmap(res, R.drawable.frear, faceW, faceH);
            mBmpEntrarCarro = loadResBitmap(res, R.drawable.entrar_no_carro, faceW, faceH);
            mBmpSoco = loadResBitmap(res, R.drawable.soco, faceW, faceH);
            mBmpConfiguracoes = loadResBitmap(res, R.drawable.configuracoes, faceW * 1.2f, faceH * 1.2f);
            mBmpMudarCamera = loadResBitmap(res, R.drawable.mudar_camera_no_carro, faceW, faceH);
        }
    }

    // ── onDraw ────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // ── Dim background when in editor mode ────────────────────────
        if (mEditorMode) {
            mEditorOverlayPaint.setStyle(Paint.Style.FILL);
            mEditorOverlayPaint.setColor(Color.argb(60, 0, 0, 0));
            canvas.drawRect(0, 0, getWidth(), getHeight(), mEditorOverlayPaint);
        }

        // ── Desenha sticks ────────────────────────────────────────────
        for (int i = 0; i < STKS.length; i++) {
            if (i == 1 && mSwipeCameraEnabled && !mEditorMode) {
                continue;
            }
            Stk s = STKS[i];

            // In editor mode, apply the custom alpha only to the base circle
            int baseAlpha = mEditorMode ? s.baseAlpha : STK_BASE_ALPHA_DEF;
            int knobAlpha = mEditorMode ? s.knobAlpha : STK_KNOB_ALPHA_DEF;

            mPStkBase.setColor(Color.argb(baseAlpha, 255, 255, 255));
            mPStkKnob.setColor(Color.argb(knobAlpha, 255, 255, 255));

            canvas.drawCircle(s.cx, s.cy, s.r, mPStkBase);
            float kx = mStickKnobX[i];
            float ky = mStickKnobY[i];
            canvas.drawCircle(kx, ky, s.r * 0.35f, mPStkKnob);

            // Restore defaults
            mPStkBase.setColor(Color.argb(STK_BASE_ALPHA_DEF, 255, 255, 255));
            mPStkKnob.setColor(Color.argb(STK_KNOB_ALPHA_DEF, 255, 255, 255));

            // Editor: highlight selected stick
            if (mEditorMode && mEditorSelectedIsStick && mEditorSelectedIdx == i) {
                mEditorHighlightPaint.setStyle(Paint.Style.STROKE);
                mEditorHighlightPaint.setStrokeWidth(4f);
                mEditorHighlightPaint.setColor(SETTINGS_ACCENT);
                canvas.drawCircle(s.cx, s.cy, s.r + 8f, mEditorHighlightPaint);
            }
        }

        // ── Desenha botoes ────────────────────────────────────────────
        for (int i = 0; i < BTNS.length; i++) {
            Btn b = BTNS[i];

            if (i == BTN_IDX_SETTINGS) {
                drawSettingsGear(canvas, b);
                continue;
            }

            if (!isBtnVisible(i)) continue;

            Bitmap bmp = mBtnBmps[i];
            if (mNativeHudEnabled && !mEditorMode) {
                int context = 0;
                try {
                    context = SimpsonsActivity.nativeGetHudContext();
                } catch (UnsatisfiedLinkError e) { }
                if (i == 4) {
                    bmp = (context == 2) ? mBmpAcelerar : mBmpPular;
                } else if (i == 5) {
                    bmp = (context == 2) ? mBmpFrear : mBmpCorrer;
                } else if (i == 6) {
                    bmp = (context == 2) ? mBmpMudarCamera : mBmpSoco;
                } else if (i == 7) {
                    bmp = mBmpEntrarCarro;
                }
            }
            if (bmp == null) continue;

            int pressAlpha = (mButtonPointerIds[i] != -1) ? 180 : 255;
            int finalAlpha = mEditorMode ? (b.alpha * pressAlpha / 255) : pressAlpha;
            mPBmp.setAlpha(finalAlpha);
            canvas.drawBitmap(bmp, b.rect.left, b.rect.top, mPBmp);

            // Editor: highlight selected button
            if (mEditorMode && !mEditorSelectedIsStick && mEditorSelectedIdx == i) {
                mEditorHighlightPaint.setStyle(Paint.Style.STROKE);
                mEditorHighlightPaint.setStrokeWidth(3f);
                mEditorHighlightPaint.setColor(SETTINGS_ACCENT);
                canvas.drawRoundRect(b.rect, 4f, 4f, mEditorHighlightPaint);
            }
        }

        // ── Editor mode: drag hint for selected element ───────────────
        if (mEditorMode && mEditorSelectedIdx != -1) {
            String hint = "Arraste para mover | Painel abaixo para ajustar";
            mEditorDragHintPaint.setColor(Color.argb(180, 255, 255, 255));
            mEditorDragHintPaint.setTextSize(getHeight() * 0.025f);
            mEditorDragHintPaint.setTextAlign(Paint.Align.CENTER);
            mEditorDragHintPaint.setFakeBoldText(false);
            canvas.drawText(hint, getWidth() / 2f, mEditorPanelRect.top - 16f, mEditorDragHintPaint);
        }

        // ── Settings panel ────────────────────────────────────────────
        if (mShowSettings && !mEditorMode) {
            drawSettingsPanel(canvas);
        }

        // ── Editor panel ──────────────────────────────────────────────
        if (mEditorMode) {
            drawEditorPanel(canvas);
        }

        // ── FPS display ───────────────────────────────────────────────
        if (mShowFPS) {
            drawFPSDisplay(canvas);
            postInvalidateDelayed(33);
        }
    }

    // ── Settings gear icon ────────────────────────────────────────────
    private void drawSettingsGear(Canvas canvas, Btn b) {
        // Use configuracoes bitmap when native HUD is enabled
        if (mNativeHudEnabled && mBmpConfiguracoes != null) {
            int pressAlpha = (mButtonPointerIds[BTN_IDX_SETTINGS] != -1) ? 180 : 255;
            mPBmp.setAlpha(pressAlpha);
            canvas.drawBitmap(mBmpConfiguracoes, b.rect.left, b.rect.top, mPBmp);
            return;
        }

        float cx = b.rect.centerX();
        float cy = b.rect.centerY();
        float r = b.rect.width() * 0.38f;
        float rInner = r * 0.55f;

        boolean pressed = mButtonPointerIds[BTN_IDX_SETTINGS] != -1;
        int gearColor = pressed ? SETTINGS_ACCENT : Color.argb(180, 255, 255, 255);

        mGearStrokePaint.setStyle(Paint.Style.STROKE);
        mGearStrokePaint.setStrokeWidth(r * 0.22f);
        mGearStrokePaint.setColor(gearColor);

        int teeth = 8;
        for (int i = 0; i < teeth; i++) {
            double angle = (Math.PI * 2 * i) / teeth;
            float sx = cx + (float)(Math.cos(angle) * r);
            float sy = cy + (float)(Math.sin(angle) * r);
            float ex = cx + (float)(Math.cos(angle) * rInner);
            float ey = cy + (float)(Math.sin(angle) * rInner);
            canvas.drawLine(sx, sy, ex, ey, mGearStrokePaint);
        }

        mGearStrokePaint.setStyle(Paint.Style.STROKE);
        mGearStrokePaint.setStrokeWidth(r * 0.15f);
        canvas.drawCircle(cx, cy, r, mGearStrokePaint);

        mGearDotPaint.setStyle(Paint.Style.FILL);
        mGearDotPaint.setColor(gearColor);
        canvas.drawCircle(cx, cy, r * 0.35f, mGearDotPaint);
    }

    // ── Settings panel ────────────────────────────────────────────────
    private void drawSettingsPanel(Canvas canvas) {
        float left = mSettingsPanelRect.left;
        float top = mSettingsPanelRect.top;
        float right = mSettingsPanelRect.right;
        float w = mSettingsPanelRect.width();
        float h = mSettingsPanelRect.height();

        // 1. Panel background (glassmorphic dark slate fill)
        mPanelBgPaint.setStyle(Paint.Style.FILL);
        mPanelBgPaint.setColor(Color.argb(240, 15, 15, 28));
        canvas.drawRoundRect(mSettingsPanelRect, 32f, 32f, mPanelBgPaint);

        // Panel border (Simpsons Accent Yellow)
        mPanelBorderPaint.setStyle(Paint.Style.STROKE);
        mPanelBorderPaint.setStrokeWidth(4f);
        mPanelBorderPaint.setColor(SETTINGS_ACCENT);
        canvas.drawRoundRect(mSettingsPanelRect, 32f, 32f, mPanelBorderPaint);

        // 2. Title Header
        mPanelTitlePaint.setColor(SETTINGS_TEXT_COLOR);
        mPanelTitlePaint.setTextSize(h * 0.07f);
        mPanelTitlePaint.setTextAlign(Paint.Align.CENTER);
        mPanelTitlePaint.setFakeBoldText(true);
        canvas.drawText("CONFIGURACOES", left + w / 2f, top + h * 0.09f, mPanelTitlePaint);

        // Subtitle
        Paint subTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subTitlePaint.setColor(Color.argb(160, 255, 255, 255));
        subTitlePaint.setTextSize(h * 0.035f);
        subTitlePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Ajustes do Jogo & Controles", left + w / 2f, top + h * 0.135f, subTitlePaint);

        // Separator
        mPanelLinePaint.setColor(Color.argb(60, 255, 255, 255));
        mPanelLinePaint.setStrokeWidth(1.5f);
        float lineY = top + h * 0.155f;
        canvas.drawLine(left + w * 0.06f, lineY, right - w * 0.06f, lineY, mPanelLinePaint);

        // Helper Paint for Card Background
        Paint cardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBgPaint.setStyle(Paint.Style.FILL);
        Paint cardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBorderPaint.setStyle(Paint.Style.STROKE);
        cardBorderPaint.setStrokeWidth(1.5f);

        // 3. Row 1: FPS Toggle Row
        // Card background
        cardBgPaint.setColor(Color.argb(15, 255, 255, 255));
        canvas.drawRoundRect(mFpsToggleRect, 16f, 16f, cardBgPaint);
        cardBorderPaint.setColor(Color.argb(25, 255, 255, 255));
        canvas.drawRoundRect(mFpsToggleRect, 16f, 16f, cardBorderPaint);

        // Label
        mPanelLabelPaint.setColor(SETTINGS_TEXT_COLOR);
        mPanelLabelPaint.setTextSize(h * 0.045f);
        mPanelLabelPaint.setTextAlign(Paint.Align.LEFT);
        mPanelLabelPaint.setFakeBoldText(false);
        canvas.drawText("Exibir FPS", mFpsToggleRect.left + w * 0.05f,
                         mFpsToggleRect.centerY() + h * 0.015f, mPanelLabelPaint);

        // Switch Toggle
        float swW = w * 0.14f;
        float swH = mFpsToggleRect.height() * 0.5f;
        float swR = mFpsToggleRect.right - w * 0.05f;
        float swL = swR - swW;
        float swT = mFpsToggleRect.centerY() - swH / 2f;
        float swB = mFpsToggleRect.centerY() + swH / 2f;
        RectF switchRect = new RectF(swL, swT, swR, swB);

        mToggleBgPaint.setStyle(Paint.Style.FILL);
        mToggleBgPaint.setColor(mShowFPS ? Color.rgb(0, 184, 148) : Color.rgb(45, 52, 54));
        canvas.drawRoundRect(switchRect, swH / 2f, swH / 2f, mToggleBgPaint);

        float thumbRadius = swH * 0.42f;
        float thumbX = mShowFPS ? swR - thumbRadius - 4f : swL + thumbRadius + 4f;
        float thumbY = mFpsToggleRect.centerY();

        // Shadow under switch thumb
        Paint thumbShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbShadow.setStyle(Paint.Style.FILL);
        thumbShadow.setColor(Color.argb(50, 0, 0, 0));
        canvas.drawCircle(thumbX, thumbY + 2f, thumbRadius + 1f, thumbShadow);

        mToggleThumbPaint.setStyle(Paint.Style.FILL);
        mToggleThumbPaint.setColor(Color.WHITE);
        canvas.drawCircle(thumbX, thumbY, thumbRadius, mToggleThumbPaint);

        // ON/OFF label text inside switch
        mToggleTextPaint.setColor(Color.WHITE);
        mToggleTextPaint.setTextSize(swH * 0.4f);
        mToggleTextPaint.setFakeBoldText(true);
        mToggleTextPaint.setTextAlign(Paint.Align.CENTER);
        if (mShowFPS) {
            canvas.drawText("ON", swL + swW * 0.35f, thumbY + swH * 0.15f, mToggleTextPaint);
        } else {
            canvas.drawText("OFF", swL + swW * 0.65f, thumbY + swH * 0.15f, mToggleTextPaint);
        }

        // 4. Row 2: Camera Swipe Toggle Row
        // Card background
        canvas.drawRoundRect(mCameraSwipeToggleRect, 16f, 16f, cardBgPaint);
        canvas.drawRoundRect(mCameraSwipeToggleRect, 16f, 16f, cardBorderPaint);

        // Label
        canvas.drawText("Deslizar Câmera", mCameraSwipeToggleRect.left + w * 0.05f,
                         mCameraSwipeToggleRect.centerY() + h * 0.015f, mPanelLabelPaint);

        // Switch Toggle
        float swT2 = mCameraSwipeToggleRect.centerY() - swH / 2f;
        float swB2 = mCameraSwipeToggleRect.centerY() + swH / 2f;
        RectF switchRect2 = new RectF(swL, swT2, swR, swB2);

        mToggleBgPaint.setColor(mSwipeCameraEnabled ? Color.rgb(0, 184, 148) : Color.rgb(45, 52, 54));
        canvas.drawRoundRect(switchRect2, swH / 2f, swH / 2f, mToggleBgPaint);

        float thumbX2 = mSwipeCameraEnabled ? swR - thumbRadius - 4f : swL + thumbRadius + 4f;
        float thumbY2 = mCameraSwipeToggleRect.centerY();

        canvas.drawCircle(thumbX2, thumbY2 + 2f, thumbRadius + 1f, thumbShadow);
        canvas.drawCircle(thumbX2, thumbY2, thumbRadius, mToggleThumbPaint);

        if (mSwipeCameraEnabled) {
            canvas.drawText("ON", swL + swW * 0.35f, thumbY2 + swH * 0.15f, mToggleTextPaint);
        } else {
            canvas.drawText("OFF", swL + swW * 0.65f, thumbY2 + swH * 0.15f, mToggleTextPaint);
        }

        // 4.5. Row 3: HUD Nativo Toggle Row
        // Card background
        canvas.drawRoundRect(mNativeHudToggleRect, 16f, 16f, cardBgPaint);
        canvas.drawRoundRect(mNativeHudToggleRect, 16f, 16f, cardBorderPaint);

        // Label
        canvas.drawText("HUD Nativo", mNativeHudToggleRect.left + w * 0.05f,
                         mNativeHudToggleRect.centerY() + h * 0.015f, mPanelLabelPaint);

        // Switch Toggle
        float swT3 = mNativeHudToggleRect.centerY() - swH / 2f;
        float swB3 = mNativeHudToggleRect.centerY() + swH / 2f;
        RectF switchRect3 = new RectF(swL, swT3, swR, swB3);

        mToggleBgPaint.setColor(mNativeHudEnabled ? Color.rgb(0, 184, 148) : Color.rgb(45, 52, 54));
        canvas.drawRoundRect(switchRect3, swH / 2f, swH / 2f, mToggleBgPaint);

        float thumbX3 = mNativeHudEnabled ? swR - thumbRadius - 4f : swL + thumbRadius + 4f;
        float thumbY3 = mNativeHudToggleRect.centerY();

        canvas.drawCircle(thumbX3, thumbY3 + 2f, thumbRadius + 1f, thumbShadow);
        canvas.drawCircle(thumbX3, thumbY3, thumbRadius, mToggleThumbPaint);

        if (mNativeHudEnabled) {
            canvas.drawText("ON", swL + swW * 0.35f, thumbY3 + swH * 0.15f, mToggleTextPaint);
        } else {
            canvas.drawText("OFF", swL + swW * 0.65f, thumbY3 + swH * 0.15f, mToggleTextPaint);
        }

        // 5. Row 4: Swipe Camera Sensitivity Control (only visible if enabled)
        if (mSwipeCameraEnabled) {
            float cardH = mCameraSwipeToggleRect.height();
            float sensRowT = mSensDownRect.centerY() - cardH / 2f;
            float sensRowB = mSensDownRect.centerY() + cardH / 2f;
            RectF sensRowRect = new RectF(mCameraSwipeToggleRect.left, sensRowT, mCameraSwipeToggleRect.right, sensRowB);
            
            // Card background
            canvas.drawRoundRect(sensRowRect, 16f, 16f, cardBgPaint);
            canvas.drawRoundRect(sensRowRect, 16f, 16f, cardBorderPaint);

            // Label
            canvas.drawText("Sensibilidade", sensRowRect.left + w * 0.05f,
                             sensRowRect.centerY() + h * 0.015f, mPanelLabelPaint);

            // Stepper buttons
            Paint stepBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            stepBgPaint.setStyle(Paint.Style.FILL);
            
            Paint stepTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            stepTextPaint.setColor(Color.WHITE);
            stepTextPaint.setFakeBoldText(true);
            stepTextPaint.setTextAlign(Paint.Align.CENTER);

            // Decrement [-] button
            stepBgPaint.setColor(mSensDownPressed ? SETTINGS_ACCENT : Color.argb(45, 255, 255, 255));
            canvas.drawRoundRect(mSensDownRect, 12f, 12f, stepBgPaint);
            
            stepTextPaint.setColor(mSensDownPressed ? Color.BLACK : Color.WHITE);
            stepTextPaint.setTextSize(mSensDownRect.height() * 0.6f);
            canvas.drawText("-", mSensDownRect.centerX(), mSensDownRect.centerY() + mSensDownRect.height() * 0.2f, stepTextPaint);

            // Increment [+] button
            stepBgPaint.setColor(mSensUpPressed ? SETTINGS_ACCENT : Color.argb(45, 255, 255, 255));
            canvas.drawRoundRect(mSensUpRect, 12f, 12f, stepBgPaint);
            
            stepTextPaint.setColor(mSensUpPressed ? Color.BLACK : Color.WHITE);
            stepTextPaint.setTextSize(mSensUpRect.height() * 0.6f);
            canvas.drawText("+", mSensUpRect.centerX(), mSensUpRect.centerY() + mSensUpRect.height() * 0.2f, stepTextPaint);

            // Sensitivity Value Text
            Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            valPaint.setColor(SETTINGS_ACCENT);
            valPaint.setTextSize(mSensDownRect.height() * 0.5f);
            valPaint.setFakeBoldText(true);
            valPaint.setTextAlign(Paint.Align.CENTER);
            float valCenterX = (mSensDownRect.right + mSensUpRect.left) / 2f;
            canvas.drawText(String.format("%.1fx", mSwipeSensitivity), valCenterX, sensRowRect.centerY() + h * 0.015f, valPaint);
        }

        // 6. Editor de Controles button
        boolean editorHover = mEditorBtnPressed;
        mEditorBtnBgPaint.setStyle(Paint.Style.FILL);
        mEditorBtnBgPaint.setColor(editorHover ? SETTINGS_ACCENT : Color.argb(45, 255, 255, 255));
        float btnRound = mEditorBtnRect.height() / 2f;
        canvas.drawRoundRect(mEditorBtnRect, btnRound, btnRound, mEditorBtnBgPaint);

        // Button border
        Paint editorBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        editorBorderPaint.setStyle(Paint.Style.STROKE);
        editorBorderPaint.setStrokeWidth(2f);
        editorBorderPaint.setColor(editorHover ? SETTINGS_ACCENT : Color.argb(60, 255, 255, 255));
        canvas.drawRoundRect(mEditorBtnRect, btnRound, btnRound, editorBorderPaint);

        float btnTextSize = mEditorBtnRect.height() * 0.40f;
        mEditorBtnTextPaint.setColor(editorHover ? Color.BLACK : SETTINGS_TEXT_COLOR);
        mEditorBtnTextPaint.setTextSize(btnTextSize);
        mEditorBtnTextPaint.setTextAlign(Paint.Align.CENTER);
        mEditorBtnTextPaint.setFakeBoldText(true);
        canvas.drawText("EDITOR DE CONTROLES",
            mEditorBtnRect.centerX(),
            mEditorBtnRect.centerY() + btnTextSize * 0.35f,
            mEditorBtnTextPaint);

        // Icon on the left of the button
        mEditorBtnTextPaint.setTextSize(btnTextSize * 0.9f);
        canvas.drawText("\u2699",
            mEditorBtnRect.left + btnTextSize * 1.5f,
            mEditorBtnRect.centerY() + btnTextSize * 0.3f,
            mEditorBtnTextPaint);

        // FPS value text
        if (mShowFPS && mNativeAvailable) {
            float fps = SimpsonsActivity.nativeGetFPS();
            mFpsValuePaint.setColor(SETTINGS_ACCENT);
            mFpsValuePaint.setTextSize(h * 0.04f);
            mFpsValuePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.format("FPS atual: %.1f", fps),
                left + w / 2f, mEditorBtnRect.bottom + h * 0.05f, mFpsValuePaint);
        }

        // Close button (X)
        float cx = mSettingsCloseRect.centerX();
        float cy = mSettingsCloseRect.centerY();
        float closeR = mSettingsCloseRect.width() / 2f;
        
        Paint closeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        closeBgPaint.setStyle(Paint.Style.FILL);
        closeBgPaint.setColor(mSettingsClosePressed ? Color.rgb(235, 77, 75) : Color.argb(35, 255, 255, 255));
        canvas.drawCircle(cx, cy, closeR, closeBgPaint);

        mCloseBtnPaint.setStyle(Paint.Style.STROKE);
        mCloseBtnPaint.setStrokeWidth(4.5f);
        mCloseBtnPaint.setColor(mSettingsClosePressed ? Color.BLACK : Color.WHITE);
        float half = closeR * 0.4f;
        canvas.drawLine(cx - half, cy - half, cx + half, cy + half, mCloseBtnPaint);
        canvas.drawLine(cx + half, cy - half, cx - half, cy + half, mCloseBtnPaint);
    }

    // ── Editor Panel ──────────────────────────────────────────────────
    private void drawEditorPanel(Canvas canvas) {
        final float w = mEditorPanelRect.width();
        final float h = mEditorPanelRect.height();

        // Panel background
        mEditorBgPaint.setStyle(Paint.Style.FILL);
        mEditorBgPaint.setColor(Color.argb(220, 20, 20, 50));
        canvas.drawRoundRect(mEditorPanelRect, 14f, 14f, mEditorBgPaint);

        // Border
        mEditorBorderPaint.setStyle(Paint.Style.STROKE);
        mEditorBorderPaint.setStrokeWidth(2f);
        mEditorBorderPaint.setColor(SETTINGS_ACCENT);
        canvas.drawRoundRect(mEditorPanelRect, 14f, 14f, mEditorBorderPaint);

        // Title
        mEditorLabelPaint.setColor(SETTINGS_ACCENT);
        mEditorLabelPaint.setTextSize(h * 0.12f);
        mEditorLabelPaint.setTextAlign(Paint.Align.LEFT);
        mEditorLabelPaint.setFakeBoldText(true);
        canvas.drawText("EDITOR", mEditorPanelRect.left + w * 0.05f,
                        mEditorPanelRect.top + h * 0.14f, mEditorLabelPaint);

        // Selected element name
        String selName = "Nenhum";
        if (mEditorSelectedIdx != -1) {
            if (mEditorSelectedIsStick) {
                selName = STICK_NAMES[mEditorSelectedIdx];
            } else {
                selName = BTNS[mEditorSelectedIdx].label;
            }
        }
        mEditorLabelPaint.setColor(Color.WHITE);
        mEditorLabelPaint.setTextSize(h * 0.10f);
        mEditorLabelPaint.setTextAlign(Paint.Align.RIGHT);
        mEditorLabelPaint.setFakeBoldText(false);
        canvas.drawText(selName, mEditorPanelRect.right - w * 0.05f,
                        mEditorPanelRect.top + h * 0.14f, mEditorLabelPaint);

        // Separator
        mEditorBorderPaint.setStrokeWidth(1f);
        mEditorBorderPaint.setColor(Color.argb(80, 255, 255, 255));
        float sepY = mEditorPanelRect.top + h * 0.17f;
        canvas.drawLine(mEditorPanelRect.left + w * 0.05f, sepY,
                        mEditorPanelRect.right - w * 0.05f, sepY, mEditorBorderPaint);

        // Draw controls only when something is selected
        if (mEditorSelectedIdx == -1) {
            mEditorLabelPaint.setColor(Color.argb(150, 255, 255, 255));
            mEditorLabelPaint.setTextSize(h * 0.09f);
            mEditorLabelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Toque em um controle na tela para editar",
                mEditorPanelRect.centerX(), mEditorPanelRect.top + h * 0.55f, mEditorLabelPaint);
        } else {
            float currentSize, currentAlpha;
            if (mEditorSelectedIsStick) {
                Stk s = STKS[mEditorSelectedIdx];
                currentSize = s.nr;
                currentAlpha = s.baseAlpha;
            } else {
                Btn b = BTNS[mEditorSelectedIdx];
                currentSize = b.nw;
                currentAlpha = b.alpha;
            }

            float labelSize = h * 0.095f;
            float valueSize = h * 0.095f;
            float row1Y = mEditorPanelRect.top + h * 0.34f;
            float row2Y = mEditorPanelRect.top + h * 0.56f;

            // ── Row 1: Tamanho (Size) ────────────────────────────────────
            mEditorLabelPaint.setColor(Color.WHITE);
            mEditorLabelPaint.setTextSize(labelSize);
            mEditorLabelPaint.setTextAlign(Paint.Align.LEFT);
            mEditorLabelPaint.setFakeBoldText(false);
            canvas.drawText("Tamanho", mEditorPanelRect.left + w * 0.08f, row1Y + labelSize * 0.3f, mEditorLabelPaint);

            // [-] button
            drawEditorStepperBtn(canvas, mEditorSizeDownRect, "\u25C0", mEditorSizeDownPressed);
            // Value
            mEditorValuePaint.setColor(SETTINGS_ACCENT);
            mEditorValuePaint.setTextSize(valueSize);
            mEditorValuePaint.setTextAlign(Paint.Align.CENTER);
            mEditorValuePaint.setFakeBoldText(true);
            canvas.drawText(String.format("%.2f", currentSize),
                mEditorSizeDownRect.right + (mEditorSizeUpRect.left - mEditorSizeDownRect.right) / 2f,
                row1Y + valueSize * 0.3f, mEditorValuePaint);
            // [+] button
            drawEditorStepperBtn(canvas, mEditorSizeUpRect, "\u25B6", mEditorSizeUpPressed);

            // ── Row 2: Opacidade (Opacity) ───────────────────────────────
            mEditorLabelPaint.setColor(Color.WHITE);
            mEditorLabelPaint.setTextSize(labelSize);
            mEditorLabelPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Opacidade", mEditorPanelRect.left + w * 0.08f, row2Y + labelSize * 0.3f, mEditorLabelPaint);

            // [-] button
            drawEditorStepperBtn(canvas, mEditorAlphaDownRect, "\u25C0", mEditorAlphaDownPressed);
            // Value
            mEditorValuePaint.setColor(SETTINGS_ACCENT);
            mEditorValuePaint.setTextSize(valueSize);
            mEditorValuePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.valueOf((int)currentAlpha),
                mEditorAlphaDownRect.right + (mEditorAlphaUpRect.left - mEditorAlphaDownRect.right) / 2f,
                row2Y + valueSize * 0.3f, mEditorValuePaint);
            // [+] button
            drawEditorStepperBtn(canvas, mEditorAlphaUpRect, "\u25B6", mEditorAlphaUpPressed);

            // ── Reset button ─────────────────────────────────────────────
            drawEditorActionBtn(canvas, mEditorResetRect, "RESETAR", mEditorResetPressed, false);

            // ── Close button ─────────────────────────────────────────────
            drawEditorActionBtn(canvas, mEditorCloseRect, "X", mEditorClosePressed, true);
        }
    }

    private void drawEditorStepperBtn(Canvas canvas, RectF rect, String label, boolean pressed) {
        mEditorBtnBgPaint.setStyle(Paint.Style.FILL);
        mEditorBtnBgPaint.setColor(pressed ? SETTINGS_ACCENT : Color.argb(80, 255, 255, 255));
        canvas.drawRoundRect(rect, 6f, 6f, mEditorBtnBgPaint);

        mEditorBtnTextPaint.setColor(pressed ? Color.BLACK : Color.WHITE);
        mEditorBtnTextPaint.setTextSize(rect.height() * 0.5f);
        mEditorBtnTextPaint.setTextAlign(Paint.Align.CENTER);
        mEditorBtnTextPaint.setFakeBoldText(true);
        canvas.drawText(label, rect.centerX(), rect.centerY() + rect.height() * 0.18f, mEditorBtnTextPaint);
    }

    private void drawEditorActionBtn(Canvas canvas, RectF rect, String label, boolean pressed, boolean isSmall) {
        int bgColor = pressed ? SETTINGS_ACCENT : Color.argb(100, 255, 255, 255);
        if (label.equals("RESETAR")) bgColor = pressed ? Color.rgb(255, 100, 100) : Color.argb(120, 200, 60, 60);

        mEditorBtnBgPaint.setStyle(Paint.Style.FILL);
        mEditorBtnBgPaint.setColor(bgColor);
        canvas.drawRoundRect(rect, 8f, 8f, mEditorBtnBgPaint);

        float textSize = isSmall ? rect.height() * 0.55f : rect.height() * 0.42f;
        mEditorBtnTextPaint.setColor(pressed ? Color.BLACK : Color.WHITE);
        mEditorBtnTextPaint.setTextSize(textSize);
        mEditorBtnTextPaint.setTextAlign(Paint.Align.CENTER);
        mEditorBtnTextPaint.setFakeBoldText(true);
        canvas.drawText(label, rect.centerX(), rect.centerY() + textSize * 0.35f, mEditorBtnTextPaint);
    }

    // ── FPS display ───────────────────────────────────────────────────
    private void drawFPSDisplay(Canvas canvas) {
        if (!mNativeAvailable) {
            try {
                SimpsonsActivity.nativeGetFPS();
                mNativeAvailable = true;
            } catch (UnsatisfiedLinkError e) {
                return;
            }
        }

        float fps = SimpsonsActivity.nativeGetFPS();

        mFpsBgPaint.setStyle(Paint.Style.FILL);
        mFpsBgPaint.setColor(Color.argb(140, 0, 0, 0));

        float textSize = getHeight() * 0.035f;
        float boxW = textSize * 5f;
        float boxH = textSize * 1.6f;
        float margin = 10f;

        canvas.drawRoundRect(margin, margin, margin + boxW, margin + boxH, 8f, 8f, mFpsBgPaint);

        mFpsTextPaint.setColor(Color.argb(255, 0, 255, 100));
        mFpsTextPaint.setTextSize(textSize);
        mFpsTextPaint.setTextAlign(Paint.Align.LEFT);
        mFpsTextPaint.setFakeBoldText(true);

        canvas.drawText(String.format("FPS: %.1f", fps),
            margin + textSize * 0.3f,
            margin + textSize * 1.1f,
            mFpsTextPaint);
    }

    // ── Touch event handling ──────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int idx = ev.getActionIndex();
                final int pid = ev.getPointerId(idx);
                final float x = ev.getX(idx);
                final float y = ev.getY(idx);
                handleDown(x, y, pid);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                final int pc = ev.getPointerCount();
                for (int i = 0; i < pc; i++) {
                    final int pid = ev.getPointerId(i);
                    final float x = ev.getX(i);
                    final float y = ev.getY(i);
                    handleMove(x, y, pid);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                final int idx = ev.getActionIndex();
                final int pid = ev.getPointerId(idx);
                handleUp(pid);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                handleCancel();
                break;
        }

        if (!mEditorMode) {
            forwardTouchToSDL(ev);
        }

        invalidate();
        return true;
    }

    // ── Debug ─────────────────────────────────────────────────────────
    private static String actionName(int action) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:            return "DOWN";
            case MotionEvent.ACTION_UP:              return "UP";
            case MotionEvent.ACTION_MOVE:            return "MOVE";
            case MotionEvent.ACTION_CANCEL:          return "CANCEL";
            case MotionEvent.ACTION_POINTER_DOWN:    return "PTR_DOWN";
            case MotionEvent.ACTION_POINTER_UP:      return "PTR_UP";
            default:                                 return "?" + action;
        }
    }

    // ── Native HUD / Gamepad layout switches ─────────────────────────
    private void applyLayout(float[][] positions) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;
        
        for (int i = 0; i < BTNS.length && i < positions.length; i++) {
            Btn b = BTNS[i];
            b.nx = positions[i][0];
            b.ny = positions[i][1];
            b.nw = positions[i][2];
            b.nh = positions[i][3];
        }
        recalcAllRects(w, h);
        saveOrigins();
        loadBitmaps();
    }

    // ── handleDown ────────────────────────────────────────────────────
    private void handleDown(float x, float y, int pid) {
        // ── Editor mode takes priority ─────────────────────────────────
        if (mEditorMode) {
            handleEditorDown(x, y, pid);
            return;
        }

        // ── Settings panel ────────────────────────────────────────────
        if (mShowSettings) {
            if (mSettingsCloseRect.contains(x, y)) {
                mSettingsPointerId = pid;
                mSettingsClosePressed = true;
                return;
            }
            if (mFpsToggleRect.contains(x, y)) {
                mSettingsPointerId = pid;
                mFpsTogglePressed = true;
                return;
            }
            if (mCameraSwipeToggleRect.contains(x, y)) {
                mSettingsPointerId = pid;
                mCameraSwipeTogglePressed = true;
                return;
            }
            if (mNativeHudToggleRect.contains(x, y)) {
                mSettingsPointerId = pid;
                mNativeHudTogglePressed = true;
                return;
            }
            if (mSwipeCameraEnabled) {
                if (mSensDownRect.contains(x, y)) {
                    mSettingsPointerId = pid;
                    mSensDownPressed = true;
                    return;
                }
                if (mSensUpRect.contains(x, y)) {
                    mSettingsPointerId = pid;
                    mSensUpPressed = true;
                    return;
                }
            }
            if (mEditorBtnRect.contains(x, y)) {
                mSettingsPointerId = pid;
                mEditorBtnPressed = true;
                return;
            }
            if (!mSettingsPanelRect.contains(x, y)) {
                mShowSettings = false;
                return;
            }
            mSettingsPointerId = pid;
            return;
        }

        // ── Normal HUD ────────────────────────────────────────────────
        for (int i = 0; i < BTNS.length; i++) {
            if (!isBtnVisible(i)) continue;
            if (mButtonPointerIds[i] == -1 && BTNS[i].rect.contains(x, y)) {
                mButtonPointerIds[i] = pid;
                Log.i(TAG, String.format("BTN %s PRESSED pid=%d pos=(%.3f,%.3f)",
                    BTNS[i].label, pid, x / getWidth(), y / getHeight()));

                if (i == BTN_IDX_SETTINGS) {
                    mShowSettings = !mShowSettings;
                    mButtonPointerIds[i] = -1;
                    if (mShowSettings) {
                        releaseAllGameInputs();
                    }
                } else {
                    // Send PAD_DOWN to native game layer via virtual gamepad (Device ID 9999)
                    int kc = BTN_KEYCODES[i];
                    if (kc != 0) {
                        SDLControllerManager.onNativePadDown(9999, kc);
                        Log.v(TAG, String.format("PAD_DOWN 0x%x (%s)", kc, BTNS[i].label));
                    }
                }
                return;
            }
        }
        for (int i = 0; i < STKS.length; i++) {
            if (i == 1 && mSwipeCameraEnabled) continue;
            if (mStickPointerIds[i] != -1) continue;
            Stk s = STKS[i];
            float dx = x - s.cx, dy = y - s.cy;
            if (dx * dx + dy * dy <= s.r * s.r) {
                mStickPointerIds[i] = pid;
                clampKnob(i, x, y);
                return;
            }
        }

        // ── Swipe Camera ──────────────────────────────────────────────
        if (mSwipeCameraEnabled && mSwipePointerId == -1) {
            if (x > getWidth() / 2f) {
                mSwipePointerId = pid;
                mSwipeLastX = x;
                mSwipeLastY = y;
                Log.i(TAG, "Swipe camera started pointer=" + pid);
                return;
            }
        }
    }

    // ── handleMove ────────────────────────────────────────────────────
    private void handleMove(float x, float y, int pid) {
        // ── Editor mode ───────────────────────────────────────────────
        if (mEditorMode) {
            handleEditorMove(x, y, pid);
            return;
        }

        // ── Settings panel drag ───────────────────────────────────────
        if (mShowSettings && mSettingsPointerId == pid) {
            boolean wasClose = mSettingsClosePressed;
            mSettingsClosePressed = mSettingsCloseRect.contains(x, y);
            mFpsTogglePressed = mFpsToggleRect.contains(x, y);
            mCameraSwipeTogglePressed = mCameraSwipeToggleRect.contains(x, y);
            mNativeHudTogglePressed = mNativeHudToggleRect.contains(x, y);
            mEditorBtnPressed = mEditorBtnRect.contains(x, y);
            if (mSwipeCameraEnabled) {
                mSensDownPressed = mSensDownRect.contains(x, y);
                mSensUpPressed = mSensUpRect.contains(x, y);
            }
            return;
        }

        // Swipe Camera movement
        if (mSwipeCameraEnabled && mSwipePointerId == pid) {
            mSwipeHandler.removeCallbacks(mSwipeResetRunnable);
            float dx = x - mSwipeLastX;
            float dy = y - mSwipeLastY;
            mSwipeLastX = x;
            mSwipeLastY = y;

            float sensitivity = (30f / getWidth()) * mSwipeSensitivity;
            float valX = Math.max(-1.0f, Math.min(1.0f, dx * sensitivity));
            float valY = Math.max(-1.0f, Math.min(1.0f, dy * sensitivity));

            SDLControllerManager.onNativeJoy(9999, 2, valX);
            SDLControllerManager.onNativeJoy(9999, 3, valY);

            mSwipeHandler.postDelayed(mSwipeResetRunnable, 40);
            return;
        }

        // Sticks
        for (int i = 0; i < STKS.length; i++) {
            if (i == 1 && mSwipeCameraEnabled) continue;
            if (mStickPointerIds[i] == pid) {
                clampKnob(i, x, y);
                return;
            }
        }

        // Buttons: release if slid out
        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] == pid && !BTNS[i].rect.contains(x, y)) {
                mButtonPointerIds[i] = -1;
                // Send PAD_UP when finger slides off a button via virtual gamepad (Device ID 9999)
                if (i != BTN_IDX_SETTINGS) {
                    int kc = BTN_KEYCODES[i];
                    if (kc != 0) {
                        SDLControllerManager.onNativePadUp(9999, kc);
                        Log.v(TAG, String.format("PAD_UP (slide out) 0x%x (%s)", kc, BTNS[i].label));
                    }
                }
            }
        }
    }

    // ── handleUp ──────────────────────────────────────────────────────
    private void handleUp(int pid) {
        // ── Editor mode ───────────────────────────────────────────────
        if (mEditorMode) {
            handleEditorUp(pid);
            return;
        }

        // ── Settings panel ────────────────────────────────────────────
        if (mShowSettings && mSettingsPointerId == pid) {
            if (mSettingsClosePressed) {
                mShowSettings = false;
            } else if (mFpsTogglePressed) {
                mShowFPS = !mShowFPS;
            } else if (mCameraSwipeTogglePressed) {
                mSwipeCameraEnabled = !mSwipeCameraEnabled;
                saveProfile();
            } else if (mNativeHudTogglePressed) {
                mNativeHudEnabled = !mNativeHudEnabled;
                if (mNativeHudEnabled) {
                    try {
                        SimpsonsActivity.nativeGetHudContext();
                        mNativeAvailable = true;
                    } catch (UnsatisfiedLinkError e) { }
                    if (!mNativeLayoutApplied) {
                        applyLayout(NATIVE_BTN_POS);
                        mNativeLayoutApplied = true;
                    }
                } else {
                    if (mNativeLayoutApplied) {
                        applyLayout(GAMEPAD_BTN_POS);
                        mNativeLayoutApplied = false;
                    }
                }
                saveProfile();
            } else if (mSwipeCameraEnabled && mSensDownPressed) {
                mSwipeSensitivity = Math.max(0.1f, Math.round((mSwipeSensitivity - 0.1f) * 10f) / 10f);
                saveProfile();
            } else if (mSwipeCameraEnabled && mSensUpPressed) {
                mSwipeSensitivity = Math.min(3.0f, Math.round((mSwipeSensitivity + 0.1f) * 10f) / 10f);
                saveProfile();
            } else if (mEditorBtnPressed) {
                // Enter editor mode
                mShowSettings = false;
                mEditorMode = true;
                mEditorSelectedIdx = -1;
                mEditorSelectedIsStick = false;
                Log.i(TAG, "Editor de Controles: mode activated");
            }
            mSettingsPointerId = -1;
            mSettingsClosePressed = false;
            mFpsTogglePressed = false;
            mCameraSwipeTogglePressed = false;
            mNativeHudTogglePressed = false;
            mSensDownPressed = false;
            mSensUpPressed = false;
            mEditorBtnPressed = false;
            return;
        }

        // Handle swipe camera pointer release
        if (mSwipeCameraEnabled && mSwipePointerId == pid) {
            mSwipePointerId = -1;
            mSwipeHandler.removeCallbacks(mSwipeResetRunnable);
            SDLControllerManager.onNativeJoy(9999, 2, 0.0f);
            SDLControllerManager.onNativeJoy(9999, 3, 0.0f);
            return;
        }

        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] == pid) {
                mButtonPointerIds[i] = -1;
                // Send PAD_UP to native game layer via virtual gamepad (Device ID 9999)
                if (i != BTN_IDX_SETTINGS) {
                    int kc = BTN_KEYCODES[i];
                    if (kc != 0) {
                        SDLControllerManager.onNativePadUp(9999, kc);
                        Log.v(TAG, String.format("PAD_UP 0x%x (%s)", kc, BTNS[i].label));
                    }
                }
            }
        }
        for (int i = 0; i < STKS.length; i++) {
            if (mStickPointerIds[i] == pid) {
                mStickPointerIds[i] = -1;
                mStickKnobX[i] = STKS[i].cx;
                mStickKnobY[i] = STKS[i].cy;
                // Reset joystick values to 0.0f
                SDLControllerManager.onNativeJoy(9999, i * 2, 0.0f);
                SDLControllerManager.onNativeJoy(9999, i * 2 + 1, 0.0f);
            }
        }
    }

    // ── handleCancel ──────────────────────────────────────────────────
    private void handleCancel() {
        releaseAllGameInputs();
        mSettingsPointerId = -1;
        mSettingsClosePressed = false;
        mFpsTogglePressed = false;
        mCameraSwipeTogglePressed = false;
        mEditorBtnPressed = false;

        if (mEditorMode) {
            mEditorSelectedIdx = -1;
            mEditorDragging = false;
            mEditorPointerId = -1;
            mEditorSizeDownPressed = false;
            mEditorSizeUpPressed = false;
            mEditorAlphaDownPressed = false;
            mEditorAlphaUpPressed = false;
            mEditorResetPressed = false;
            mEditorClosePressed = false;
        }
    }

    // ── Editor: handleDown ────────────────────────────────────────────
    private void handleEditorDown(float x, float y, int pid) {
        // Check editor panel buttons first
        if (mEditorCloseRect.contains(x, y)) {
            mEditorPointerId = pid;
            mEditorClosePressed = true;
            return;
        }

        if (mEditorSelectedIdx != -1) {
            if (mEditorSizeDownRect.contains(x, y)) {
                mEditorPointerId = pid;
                mEditorSizeDownPressed = true;
                return;
            }
            if (mEditorSizeUpRect.contains(x, y)) {
                mEditorPointerId = pid;
                mEditorSizeUpPressed = true;
                return;
            }
            if (mEditorAlphaDownRect.contains(x, y)) {
                mEditorPointerId = pid;
                mEditorAlphaDownPressed = true;
                return;
            }
            if (mEditorAlphaUpRect.contains(x, y)) {
                mEditorPointerId = pid;
                mEditorAlphaUpPressed = true;
                return;
            }
            if (mEditorResetRect.contains(x, y)) {
                mEditorPointerId = pid;
                mEditorResetPressed = true;
                return;
            }
        }

        // Check if touching an element on screen (to select or drag)
        // Sticks first (they're larger)
        for (int i = 0; i < STKS.length; i++) {
            Stk s = STKS[i];
            float dx = x - s.cx, dy = y - s.cy;
            if (dx * dx + dy * dy <= (s.r + 15f) * (s.r + 15f)) { // +15 for easier touch
                if (mEditorSelectedIsStick && mEditorSelectedIdx == i) {
                    // Already selected — start dragging
                    mEditorPointerId = pid;
                    mEditorDragging = true;
                    mEditorDragOffX = x - s.cx;
                    mEditorDragOffY = y - s.cy;
                } else {
                    // Select this stick
                    mEditorSelectedIdx = i;
                    mEditorSelectedIsStick = true;
                    mEditorPointerId = pid;
                }
                Log.i(TAG, "Editor: selected stick " + STICK_NAMES[i]);
                return;
            }
        }

        // Then buttons
        for (int i = 0; i < BTNS.length; i++) {
            if (BTNS[i].rect.contains(x, y)) {
                if (i == BTN_IDX_SETTINGS) {
                    // Gear icon toggles editor mode off
                    mEditorMode = false;
                    mEditorSelectedIdx = -1;
                    mEditorDragging = false;
                    mEditorPointerId = -1;
                    Log.i(TAG, "Editor: closed via gear icon");
                    return;
                }
                if (!mEditorSelectedIsStick && mEditorSelectedIdx == i) {
                    // Already selected — start dragging
                    mEditorPointerId = pid;
                    mEditorDragging = true;
                    mEditorDragOffX = x - BTNS[i].rect.centerX();
                    mEditorDragOffY = y - BTNS[i].rect.centerY();
                } else {
                    // Select this button
                    mEditorSelectedIdx = i;
                    mEditorSelectedIsStick = false;
                    mEditorPointerId = pid;
                }
                Log.i(TAG, "Editor: selected button " + BTNS[i].label);
                return;
            }
        }

        // Tapping empty area deselects
        mEditorSelectedIdx = -1;
    }

    // ── Editor: handleMove ────────────────────────────────────────────
    private void handleEditorMove(float x, float y, int pid) {
        if (mEditorPointerId != pid) return;

        final int w = getWidth();
        final int h = getHeight();
        final float minDim = Math.min(w, h);

        // Handle dragging selected element
        if (mEditorDragging && mEditorSelectedIdx != -1) {
            float newNX = (x - mEditorDragOffX) / w;
            float newNY = (y - mEditorDragOffY) / h;

            // Clamp to screen bounds (with some padding)
            if (mEditorSelectedIsStick) {
                Stk s = STKS[mEditorSelectedIdx];
                s.ncx = Math.max(0.05f, Math.min(0.95f, newNX));
                s.ncy = Math.max(0.05f, Math.min(0.95f, newNY));
                s.cx = s.ncx * w;
                s.cy = s.ncy * h;
                s.r  = s.nr * minDim;
                // Keep knob centered
                mStickKnobX[mEditorSelectedIdx] = s.cx;
                mStickKnobY[mEditorSelectedIdx] = s.cy;
            } else {
                Btn b = BTNS[mEditorSelectedIdx];
                b.nx = Math.max(0.02f, Math.min(0.98f, newNX));
                b.ny = Math.max(0.02f, Math.min(0.98f, newNY));
                recalcBtnRect(mEditorSelectedIdx, w, h);
            }
            return;
        }

        // Update button press states
        mEditorSizeDownPressed = mEditorSizeDownRect.contains(x, y);
        mEditorSizeUpPressed = mEditorSizeUpRect.contains(x, y);
        mEditorAlphaDownPressed = mEditorAlphaDownRect.contains(x, y);
        mEditorAlphaUpPressed = mEditorAlphaUpRect.contains(x, y);
        mEditorResetPressed = mEditorResetRect.contains(x, y);
        mEditorClosePressed = mEditorCloseRect.contains(x, y);
    }

    // ── Editor: handleUp ──────────────────────────────────────────────
    private void handleEditorUp(int pid) {
        if (mEditorPointerId != pid) return;
        mEditorPointerId = -1;
        mEditorDragging = false;
        saveProfile();

        final int w = getWidth();
        final int h = getHeight();

        // Handle button actions
        if (mEditorClosePressed) {
            mEditorMode = false;
            mEditorSelectedIdx = -1;
            Log.i(TAG, "Editor: closed");
            saveProfile();
        } else if (mEditorResetPressed && mEditorSelectedIdx != -1) {
            resetToOrigins();
            saveProfile();
        } else if (mEditorSizeDownPressed && mEditorSelectedIdx != -1) {
            adjustSize(-EDITOR_SIZE_STEP, w, h);
            saveProfile();
        } else if (mEditorSizeUpPressed && mEditorSelectedIdx != -1) {
            adjustSize(EDITOR_SIZE_STEP, w, h);
            saveProfile();
        } else if (mEditorAlphaDownPressed && mEditorSelectedIdx != -1) {
            adjustAlpha(-EDITOR_ALPHA_STEP);
            saveProfile();
        } else if (mEditorAlphaUpPressed && mEditorSelectedIdx != -1) {
            adjustAlpha(EDITOR_ALPHA_STEP);
            saveProfile();
        }

        mEditorSizeDownPressed = false;
        mEditorSizeUpPressed = false;
        mEditorAlphaDownPressed = false;
        mEditorAlphaUpPressed = false;
        mEditorResetPressed = false;
        mEditorClosePressed = false;
    }

    // ── Editor: adjust size ───────────────────────────────────────────
    private void adjustSize(float delta, int w, int h) {
        final float minDim = Math.min(w, h);

        if (mEditorSelectedIsStick) {
            Stk s = STKS[mEditorSelectedIdx];
            float newNr = Math.max(EDITOR_SIZE_MIN, Math.min(EDITOR_SIZE_MAX, s.nr + delta));
            s.nr = newNr;
            s.r = s.nr * minDim;
        } else {
            Btn b = BTNS[mEditorSelectedIdx];
            float newNw = Math.max(EDITOR_SIZE_MIN, Math.min(EDITOR_SIZE_MAX, b.nw + delta));
            float newNh = Math.max(EDITOR_SIZE_MIN, Math.min(EDITOR_SIZE_MAX, b.nh + delta));
            b.nw = newNw;
            b.nh = newNh;
            recalcBtnRect(mEditorSelectedIdx, w, h);
            loadBitmaps(); // reload with new size
        }
        Log.i(TAG, "Editor: size adjusted by " + delta);
    }

    // ── Editor: adjust alpha ──────────────────────────────────────────
    private void adjustAlpha(int delta) {
        if (mEditorSelectedIsStick) {
            Stk s = STKS[mEditorSelectedIdx];
            int newBase = Math.max(EDITOR_ALPHA_MIN, Math.min(EDITOR_ALPHA_MAX, s.baseAlpha + delta));
            int newKnob = Math.max(EDITOR_ALPHA_MIN, Math.min(EDITOR_ALPHA_MAX, s.knobAlpha + delta));
            s.baseAlpha = newBase;
            s.knobAlpha = newKnob;
        } else {
            Btn b = BTNS[mEditorSelectedIdx];
            b.alpha = Math.max(EDITOR_ALPHA_MIN, Math.min(EDITOR_ALPHA_MAX, b.alpha + delta));
        }
        Log.i(TAG, "Editor: alpha adjusted by " + delta);
    }

    // ── releaseAllGameInputs ──────────────────────────────────────────
    private void releaseAllGameInputs() {
        // Send PAD_UP for all pressed buttons to prevent stuck keys
        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] != -1 && i != BTN_IDX_SETTINGS && BTN_KEYCODES[i] != 0) {
                SDLControllerManager.onNativePadUp(9999, BTN_KEYCODES[i]);
            }
            mButtonPointerIds[i] = -1;
        }
        for (int i = 0; i < STKS.length; i++) {
            mStickPointerIds[i] = -1;
            mStickKnobX[i] = STKS[i].cx;
            mStickKnobY[i] = STKS[i].cy;
            // Reset joystick values to 0.0f
            SDLControllerManager.onNativeJoy(9999, i * 2, 0.0f);
            SDLControllerManager.onNativeJoy(9999, i * 2 + 1, 0.0f);
        }
        if (mSwipeCameraEnabled) {
            mSwipePointerId = -1;
            mSwipeHandler.removeCallbacks(mSwipeResetRunnable);
            SDLControllerManager.onNativeJoy(9999, 2, 0.0f);
            SDLControllerManager.onNativeJoy(9999, 3, 0.0f);
        }
    }

    // ── Forwarding para SDL nativo ────────────────────────────────────
    private void forwardTouchToSDL(MotionEvent ev) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int touchDevId = ev.getDeviceId();
        if (touchDevId < 0) touchDevId -= 1;

        final int action = ev.getActionMasked();
        final int pointerCount = ev.getPointerCount();

        if (mShowSettings) return;

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < pointerCount; i++) {
                    sendTouch(ev, i, touchDevId, action, w, h);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_DOWN: {
                final int idx = ev.getActionIndex();
                sendTouch(ev, idx, touchDevId, action, w, h);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_DOWN:
                sendTouch(ev, ev.getActionIndex(), touchDevId, action, w, h);
                break;
            case MotionEvent.ACTION_CANCEL:
                for (int i = 0; i < pointerCount; i++) {
                    sendTouch(ev, i, touchDevId, MotionEvent.ACTION_UP, w, h);
                }
                break;
            default:
                break;
        }
    }

    private void sendTouch(MotionEvent ev, int i, int touchDevId,
                           int action, int w, int h) {
        int pointerFingerId = ev.getPointerId(i);
        float rawX = ev.getX(i);
        float rawY = ev.getY(i);

        // NÃO encaminhar toques que acertam botoes/sticks do HUD.
        // Esses elementos ja enviam key events (KEY_DOWN/KEY_UP) para o jogo nativo.
        // Se encaminharmos o toque bruto tambem, o jogo nativo pode interpreta-lo
        // como comando em uma regiao fixa (posicao ORIGINAL do botao) e ignorar
        // o key event, fazendo com que o clique na NOVA posicao do botao nao funcione.
        if (hitsHudElement(rawX, rawY)) {
            return;
        }

        float x = rawX / (float) w;
        float y = rawY / (float) h;
        if (x < 0f) x = 0f; else if (x > 1f) x = 1f;
        if (y < 0f) y = 0f; else if (y > 1f) y = 1f;
        float p = ev.getPressure(i);
        if (p > 1f) p = 1f;

        SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
    }

    // ── Verifica se um ponto de toque atinge algum elemento do HUD ────
    private boolean hitsHudElement(float x, float y) {
        // Check all buttons (including settings gear)
        for (int i = 0; i < BTNS.length; i++) {
            if (BTNS[i].rect.contains(x, y)) {
                return true;
            }
        }
        // Check all sticks (inner area)
        for (Stk s : STKS) {
            float dx = x - s.cx, dy = y - s.cy;
            if (dx * dx + dy * dy <= s.r * s.r) {
                return true;
            }
        }
        return false;
    }

    private void clampKnob(int stickIdx, float x, float y) {
        Stk s = STKS[stickIdx];
        float dx = x - s.cx;
        float dy = y - s.cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > s.r) {
            float scale = s.r / dist;
            dx *= scale;
            dy *= scale;
        }
        mStickKnobX[stickIdx] = s.cx + dx;
        mStickKnobY[stickIdx] = s.cy + dy;

        // Send to JNI virtual gamepad
        float valX = dx / s.r;
        float valY = dy / s.r;
        SDLControllerManager.onNativeJoy(9999, stickIdx * 2, valX);
        SDLControllerManager.onNativeJoy(9999, stickIdx * 2 + 1, valY);
    }
}
