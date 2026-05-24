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
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.R;
import org.libsdl.app.SDLActivity;

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

    // ── Definicões de botoes (coord normalizadas) ─────────────────────
    private static final int BTN_IDX_SETTINGS = 12;

    private static final Btn[] BTNS = {
        // ── D-Pad (clusters em diamante, lado esquerdo inferior) ──────
        new Btn("D-Pad: UP",    0.160f, 0.655f, 0.09f, 0.09f, 0),  // 0
        new Btn("D-Pad: DOWN",  0.160f, 0.785f, 0.09f, 0.09f, 0),  // 1
        new Btn("D-Pad: LEFT",  0.095f, 0.720f, 0.09f, 0.09f, 0),  // 2
        new Btn("D-Pad: RIGHT", 0.225f, 0.720f, 0.09f, 0.09f, 0),  // 3
        // ── Face Buttons A/B/X/Y ──────────────────────────────────────
        new Btn("Face: A",      0.840f, 0.785f, 0.09f, 0.09f, 0),  // 4
        new Btn("Face: B",      0.905f, 0.720f, 0.09f, 0.09f, 0),  // 5
        new Btn("Face: X",      0.775f, 0.720f, 0.09f, 0.09f, 0),  // 6
        new Btn("Face: Y",      0.840f, 0.655f, 0.09f, 0.09f, 0),  // 7
        // ── Botões superiores ─────────────────────────────────────────
        new Btn("START",        0.550f, 0.045f, 0.10f, 0.05f, 0),  // 8
        new Btn("SELECT",       0.450f, 0.045f, 0.10f, 0.05f, 0),  // 9
        new Btn("L1",           0.120f, 0.045f, 0.13f, 0.07f, 0),  // 10
        new Btn("R1",           0.850f, 0.045f, 0.12f, 0.07f, 0),  // 11
        new Btn("CONFIG",       0.950f, 0.060f, 0.05f, 0.05f, 0),  // 12: Settings gear
    };

    // ── Definicões de sticks ──────────────────────────────────────────
    private static final Stk[] STKS = {
        new Stk(0.160f, 0.560f, 0.09f),  // Left stick
        new Stk(0.840f, 0.560f, 0.09f),  // Right stick
    };

    // ── Nomes legiveis dos sticks para o editor ───────────────────────
    private static final String[] STICK_NAMES = {
        "Left Stick", "Right Stick"
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

    // ── Paints ────────────────────────────────────────────────────────
    private Paint mPStkBase;
    private Paint mPStkKnob;
    private Paint mPBmp;

    // ── Settings state ────────────────────────────────────────────────
    private boolean mShowSettings = false;
    private boolean mShowFPS      = false;
    private boolean mNativeAvailable = false;

    // ── Settings touch tracking ───────────────────────────────────────
    private int mSettingsPointerId = -1;
    private boolean mSettingsClosePressed = false;
    private boolean mFpsTogglePressed = false;
    private boolean mEditorBtnPressed = false;

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

        Log.i(TAG, "All controls reset to original values");
    }

    // ── Recalculate a single button's rect ────────────────────────────
    private void recalcBtnRect(int idx, int w, int h) {
        Btn b = BTNS[idx];
        float cx = b.nx * w;
        float cy = b.ny * h;
        float halfW = (b.nw * w) / 2f;
        float halfH = (b.nh * h) / 2f;
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

        // Settings panel: centered, ~60% width, ~55% height
        float panelW = w * 0.60f;
        float panelH = h * 0.55f;
        float panelL = (w - panelW) / 2f;
        float panelT = (h - panelH) / 2f;
        mSettingsPanelRect.set(panelL, panelT, panelL + panelW, panelT + panelH);

        // Close button (X): top-right of panel
        float closeSize = Math.min(w, h) * 0.04f;
        mSettingsCloseRect.set(
            mSettingsPanelRect.right - closeSize - 10f,
            mSettingsPanelRect.top + 10f,
            mSettingsPanelRect.right - 10f,
            mSettingsPanelRect.top + 10f + closeSize);

        // FPS toggle: upper-middle of panel
        float toggleW = panelW * 0.5f;
        float toggleH = panelH * 0.10f;
        float toggleL = panelL + (panelW - toggleW) / 2f;
        float toggleT = panelT + panelH * 0.28f;
        mFpsToggleRect.set(toggleL, toggleT, toggleL + toggleW, toggleT + toggleH);

        // Editor button: below FPS toggle
        float editorBtnW = panelW * 0.6f;
        float editorBtnH = panelH * 0.10f;
        float editorBtnL = panelL + (panelW - editorBtnW) / 2f;
        float editorBtnT = toggleT + toggleH + panelH * 0.06f;
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
        }

        loadBitmaps();
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

            Bitmap bmp = mBtnBmps[i];
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

        // Panel background
        mPanelBgPaint.setStyle(Paint.Style.FILL);
        mPanelBgPaint.setColor(SETTINGS_BG_COLOR);
        canvas.drawRoundRect(mSettingsPanelRect, 16f, 16f, mPanelBgPaint);

        // Panel border
        mPanelBorderPaint.setStyle(Paint.Style.STROKE);
        mPanelBorderPaint.setStrokeWidth(3f);
        mPanelBorderPaint.setColor(SETTINGS_ACCENT);
        canvas.drawRoundRect(mSettingsPanelRect, 16f, 16f, mPanelBorderPaint);

        // Title
        mPanelTitlePaint.setColor(SETTINGS_TEXT_COLOR);
        mPanelTitlePaint.setTextSize(h * 0.07f);
        mPanelTitlePaint.setTextAlign(Paint.Align.CENTER);
        mPanelTitlePaint.setFakeBoldText(true);
        canvas.drawText("CONFIGURACOES", left + w / 2f, top + h * 0.11f, mPanelTitlePaint);

        // Separator
        mPanelLinePaint.setColor(Color.argb(100, 255, 255, 255));
        mPanelLinePaint.setStrokeWidth(1f);
        float lineY = top + h * 0.15f;
        canvas.drawLine(left + w * 0.08f, lineY, right - w * 0.08f, lineY, mPanelLinePaint);

        // ── FPS toggle ──────────────────────────────────────────────────
        mPanelLabelPaint.setColor(SETTINGS_TEXT_COLOR);
        mPanelLabelPaint.setTextSize(h * 0.05f);
        mPanelLabelPaint.setTextAlign(Paint.Align.LEFT);
        mPanelLabelPaint.setFakeBoldText(false);
        canvas.drawText("Exibir FPS", left + w * 0.1f,
                         mFpsToggleRect.top - h * 0.02f, mPanelLabelPaint);

        mToggleBgPaint.setStyle(Paint.Style.FILL);
        mToggleBgPaint.setColor(mShowFPS ? SETTINGS_TOGGLE_ON : SETTINGS_TOGGLE_OFF);
        float toggleRound = mFpsToggleRect.height() / 2f;
        canvas.drawRoundRect(mFpsToggleRect, toggleRound, toggleRound, mToggleBgPaint);

        float thumbSize = mFpsToggleRect.height() * 0.7f;
        float thumbX = mShowFPS
            ? mFpsToggleRect.right - thumbSize - 5f
            : mFpsToggleRect.left + 5f;
        float thumbY = mFpsToggleRect.centerY();
        mToggleThumbPaint.setStyle(Paint.Style.FILL);
        mToggleThumbPaint.setColor(Color.WHITE);
        canvas.drawCircle(thumbX, thumbY, thumbSize / 2f, mToggleThumbPaint);

        mToggleTextPaint.setColor(Color.WHITE);
        mToggleTextPaint.setTextSize(thumbSize * 0.5f);
        mToggleTextPaint.setTextAlign(Paint.Align.CENTER);
        String toggleLabel = mShowFPS ? "ON" : "OFF";
        float labelOffsetX = mShowFPS ? -thumbSize / 2f - 4f : thumbSize / 2f + 4f;
        canvas.drawText(toggleLabel,
            mFpsToggleRect.centerX() + labelOffsetX,
            mFpsToggleRect.centerY() + thumbSize * 0.22f,
            mToggleTextPaint);

        // ── Editor de Controles button ──────────────────────────────────
        boolean editorHover = mEditorBtnPressed;
        mEditorBtnBgPaint.setStyle(Paint.Style.FILL);
        mEditorBtnBgPaint.setColor(editorHover ? SETTINGS_ACCENT : Color.argb(100, 255, 255, 255));
        float btnRound = mEditorBtnRect.height() / 2f;
        canvas.drawRoundRect(mEditorBtnRect, btnRound, btnRound, mEditorBtnBgPaint);

        float btnTextSize = mEditorBtnRect.height() * 0.45f;
        mEditorBtnTextPaint.setColor(editorHover ? Color.BLACK : SETTINGS_TEXT_COLOR);
        mEditorBtnTextPaint.setTextSize(btnTextSize);
        mEditorBtnTextPaint.setTextAlign(Paint.Align.CENTER);
        mEditorBtnTextPaint.setFakeBoldText(true);
        canvas.drawText("EDITOR DE CONTROLES",
            mEditorBtnRect.centerX(),
            mEditorBtnRect.centerY() + btnTextSize * 0.35f,
            mEditorBtnTextPaint);

        // Icon on the left of the button
        mEditorBtnTextPaint.setTextSize(btnTextSize * 0.8f);
        canvas.drawText("\u2699",
            mEditorBtnRect.left + btnTextSize,
            mEditorBtnRect.centerY() + btnTextSize * 0.3f,
            mEditorBtnTextPaint);

        // FPS value text
        if (mShowFPS && mNativeAvailable) {
            float fps = SimpsonsActivity.nativeGetFPS();
            mFpsValuePaint.setColor(SETTINGS_ACCENT);
            mFpsValuePaint.setTextSize(h * 0.04f);
            mFpsValuePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.format("FPS atual: %.1f", fps),
                left + w / 2f, mFpsToggleRect.bottom + h * 0.06f, mFpsValuePaint);
        }

        // Close button (X)
        mCloseBtnPaint.setStyle(Paint.Style.STROKE);
        mCloseBtnPaint.setStrokeWidth(3f);
        mCloseBtnPaint.setColor(mSettingsClosePressed ? SETTINGS_ACCENT : Color.argb(180, 255, 255, 255));
        float cx = mSettingsCloseRect.centerX();
        float cy = mSettingsCloseRect.centerY();
        float half = mSettingsCloseRect.width() / 2f;
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
                }
                return;
            }
        }
        for (int i = 0; i < STKS.length; i++) {
            if (mStickPointerIds[i] != -1) continue;
            Stk s = STKS[i];
            float dx = x - s.cx, dy = y - s.cy;
            if (dx * dx + dy * dy <= s.r * s.r) {
                mStickPointerIds[i] = pid;
                clampKnob(i, x, y);
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
            mEditorBtnPressed = mEditorBtnRect.contains(x, y);
            return;
        }

        // Sticks
        for (int i = 0; i < STKS.length; i++) {
            if (mStickPointerIds[i] == pid) {
                clampKnob(i, x, y);
                return;
            }
        }
        // Buttons: release if slid out
        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] == pid && !BTNS[i].rect.contains(x, y)) {
                mButtonPointerIds[i] = -1;
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
            mEditorBtnPressed = false;
            return;
        }

        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] == pid) {
                mButtonPointerIds[i] = -1;
            }
        }
        for (int i = 0; i < STKS.length; i++) {
            if (mStickPointerIds[i] == pid) {
                mStickPointerIds[i] = -1;
                mStickKnobX[i] = STKS[i].cx;
                mStickKnobY[i] = STKS[i].cy;
            }
        }
    }

    // ── handleCancel ──────────────────────────────────────────────────
    private void handleCancel() {
        releaseAllGameInputs();
        mSettingsPointerId = -1;
        mSettingsClosePressed = false;
        mFpsTogglePressed = false;
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

        final int w = getWidth();
        final int h = getHeight();

        // Handle button actions
        if (mEditorClosePressed) {
            mEditorMode = false;
            mEditorSelectedIdx = -1;
            Log.i(TAG, "Editor: closed");
        } else if (mEditorResetPressed && mEditorSelectedIdx != -1) {
            resetToOrigins();
        } else if (mEditorSizeDownPressed && mEditorSelectedIdx != -1) {
            adjustSize(-EDITOR_SIZE_STEP, w, h);
        } else if (mEditorSizeUpPressed && mEditorSelectedIdx != -1) {
            adjustSize(EDITOR_SIZE_STEP, w, h);
        } else if (mEditorAlphaDownPressed && mEditorSelectedIdx != -1) {
            adjustAlpha(-EDITOR_ALPHA_STEP);
        } else if (mEditorAlphaUpPressed && mEditorSelectedIdx != -1) {
            adjustAlpha(EDITOR_ALPHA_STEP);
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
        for (int i = 0; i < BTNS.length; i++) {
            mButtonPointerIds[i] = -1;
        }
        for (int i = 0; i < STKS.length; i++) {
            mStickPointerIds[i] = -1;
            mStickKnobX[i] = STKS[i].cx;
            mStickKnobY[i] = STKS[i].cy;
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
        float x = ev.getX(i) / (float) w;
        float y = ev.getY(i) / (float) h;
        if (x < 0f) x = 0f; else if (x > 1f) x = 1f;
        if (y < 0f) y = 0f; else if (y > 1f) y = 1f;
        float p = ev.getPressure(i);
        if (p > 1f) p = 1f;

        SDLActivity.onNativeTouch(touchDevId, pointerFingerId, action, x, y, p);
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
    }
}
