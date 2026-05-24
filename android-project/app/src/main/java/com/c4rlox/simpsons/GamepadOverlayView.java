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
 * Tambem inclui icone de configuracoes (engrenagem) que abre um painel
 * de configuracoes com toggle para exibir/ocultar o FPS real do jogo,
 * obtido via ponte JNI (SimpsonsActivity.nativeGetFPS).
 */
public class GamepadOverlayView extends View {

    // ── Log tag ───────────────────────────────────────────────────────
    private static final String TAG = "GamepadOverlay";

    // ── Cores tema Simpsons ───────────────────────────────────────────
    private static final int STK_BASE_ALPHA = 120;
    private static final int STK_KNOB_ALPHA = 140;

    // ── Settings UI constants ─────────────────────────────────────────
    private static final int SETTINGS_PANEL_ALPHA = 200;
    private static final int SETTINGS_TEXT_COLOR  = Color.WHITE;
    private static final int SETTINGS_BG_COLOR    = Color.argb(SETTINGS_PANEL_ALPHA, 20, 20, 50);
    private static final int SETTINGS_TOGGLE_ON   = Color.rgb(0, 200, 100);
    private static final int SETTINGS_TOGGLE_OFF  = Color.rgb(100, 100, 100);
    private static final int SETTINGS_ACCENT      = Color.rgb(255, 234, 2); // Simpsons yellow

    // ── Classes de dados ──────────────────────────────────────────────
    private static class Btn {
        final String label;
        final float nx, ny, nw, nh;
        final RectF rect = new RectF();
        final int resId;

        Btn(String label, float nx, float ny, float nw, float nh, int resId) {
            this.label = label;
            this.nx = nx; this.ny = ny; this.nw = nw; this.nh = nh;
            this.resId = resId;
        }
    }

    private static class Stk {
        final float ncx, ncy, nr;
        float cx, cy, r;

        Stk(float ncx, float ncy, float nr) {
            this.ncx = ncx; this.ncy = ncy; this.nr = nr;
        }
    }

    // ── Definicões de botoes (coord normalizadas) ─────────────────────
    // Layout aprimorado com melhor espaçamento vertical entre sticks e botões,
    // clusters mais compactos e posições mais ergonômicas.
    // Index 12 = Settings (gear)
    private static final int BTN_IDX_SETTINGS = 12;

    private static final Btn[] BTNS = {
        // ── D-Pad (clusters em diamante, lado esquerdo inferior) ──────
        new Btn("UP",     0.160f, 0.655f, 0.09f, 0.09f, 0),  // 0
        new Btn("DOWN",   0.160f, 0.785f, 0.09f, 0.09f, 0),  // 1
        new Btn("LEFT",   0.095f, 0.720f, 0.09f, 0.09f, 0),  // 2
        new Btn("RIGHT",  0.225f, 0.720f, 0.09f, 0.09f, 0),  // 3
        // ── Face Buttons A/B/X/Y (clusters em diamante, lado direito inferior)
        new Btn("A",      0.840f, 0.785f, 0.09f, 0.09f, 0),  // 4
        new Btn("B",      0.905f, 0.720f, 0.09f, 0.09f, 0),  // 5
        new Btn("X",      0.775f, 0.720f, 0.09f, 0.09f, 0),  // 6
        new Btn("Y",      0.840f, 0.655f, 0.09f, 0.09f, 0),  // 7
        // ── Botões superiores ─────────────────────────────────────────
        new Btn("START",  0.550f, 0.045f, 0.10f, 0.05f, 0),  // 8
        new Btn("SELECT", 0.450f, 0.045f, 0.10f, 0.05f, 0),  // 9
        new Btn("L1",     0.120f, 0.045f, 0.13f, 0.07f, 0),  // 10
        new Btn("R1",     0.850f, 0.045f, 0.12f, 0.07f, 0),  // 11
        new Btn("SET",    0.950f, 0.060f, 0.05f, 0.05f, 0),  // 12: Settings gear (canto superior direito)
    };

    // ── Definicões de sticks ──────────────────────────────────────────
    // Posicionados mais abaixo para evitar sobreposição com D-Pad e face buttons
    private static final Stk[] STKS = {
        new Stk(0.160f, 0.560f, 0.09f),  // Left stick
        new Stk(0.840f, 0.560f, 0.09f),  // Right stick
    };

    // ── Settings panel geometry (normalised, recalculated onSizeChanged) ──
    private RectF mSettingsPanelRect = new RectF();
    // FPS toggle switch rect inside settings panel
    private RectF mFpsToggleRect = new RectF();
    // Close button inside settings panel
    private RectF mSettingsCloseRect = new RectF();

    // ── Estado de toque (multi-touch real) ────────────────────────────
    private int[] mButtonPointerIds = new int[BTNS.length];  // -1 = não pressionado
    private int[] mStickPointerIds  = new int[STKS.length];  // -1 = não ativo
    private float[] mStickKnobX     = new float[STKS.length];
    private float[] mStickKnobY     = new float[STKS.length];

    // ── Bitmaps dos botoes ────────────────────────────────────────────
    private Bitmap[] mBtnBmps;

    // ── Paints para sticks ────────────────────────────────────────────
    private Paint mPStkBase;
    private Paint mPStkKnob;
    private Paint mPBmp;  // reused in onDraw to avoid per-frame allocation

    // ── Settings state ────────────────────────────────────────────────
    private boolean mShowSettings = false;
    private boolean mShowFPS      = false;
    private boolean mNativeAvailable = false;  // set true on first successful JNI call

    // ── Settings touch tracking ───────────────────────────────────────
    private int mSettingsPointerId = -1;  // finger holding settings panel
    private boolean mSettingsClosePressed = false;
    private boolean mFpsTogglePressed = false;

    // ── Pre-allocated Paints (to avoid per-frame GC) ──────────────────
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

    // ── Construtor ────────────────────────────────────────────────────
    public GamepadOverlayView(Context ctx) {
        super(ctx);
        mBtnBmps = new Bitmap[BTNS.length];
        for (int i = 0; i < BTNS.length; i++) mButtonPointerIds[i] = -1;
        for (int i = 0; i < STKS.length; i++)  mStickPointerIds[i] = -1;
    }

    // ── onSizeChanged: calcula geometria e carrega bitmaps ────────────
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w == 0 || h == 0) return;

        Log.i(TAG, String.format("onSizeChanged %dx%d (old=%dx%d)", w, h, oldw, oldh));

        // Calcula rects dos botoes usando centro + metade-dimensao (igual ao C++)
        for (Btn b : BTNS) {
            float cx = b.nx * w;
            float cy = b.ny * h;
            float halfW = (b.nw * w) / 2f;
            float halfH = (b.nh * h) / 2f;
            b.rect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
        }

        // Calcula posicoes dos sticks
        float minDim = Math.min(w, h);
        for (Stk s : STKS) {
            s.cx = s.ncx * w;
            s.cy = s.ncy * h;
            s.r = s.nr * minDim;
        }

        // Settings panel: centered rectangle covering ~60% of screen
        float panelW = w * 0.60f;
        float panelH = h * 0.50f;
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

        // FPS toggle: middle of panel
        float toggleW = panelW * 0.5f;
        float toggleH = panelH * 0.12f;
        float toggleL = panelL + (panelW - toggleW) / 2f;
        float toggleT = panelT + panelH * 0.40f;
        mFpsToggleRect.set(toggleL, toggleT, toggleL + toggleW, toggleT + toggleH);

        // Inicializa knobs dos sticks no centro
        for (int i = 0; i < STKS.length; i++) {
            mStickKnobX[i] = STKS[i].cx;
            mStickKnobY[i] = STKS[i].cy;
        }

        // Paints dos sticks
        mPStkBase = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPStkBase.setStyle(Paint.Style.STROKE);
        mPStkBase.setStrokeWidth(3f);
        mPStkBase.setColor(Color.argb(STK_BASE_ALPHA, 255, 255, 255));

        mPStkKnob = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPStkKnob.setStyle(Paint.Style.FILL);
        mPStkKnob.setColor(Color.argb(STK_KNOB_ALPHA, 255, 255, 255));

        mPBmp = new Paint(Paint.FILTER_BITMAP_FLAG);

        // Pre-allocated paints for gear icon
        mGearStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGearDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Pre-allocated paints for settings panel
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

        // Pre-allocated paints for FPS overlay
        mFpsBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFpsTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Carrega e escala bitmaps
        loadBitmaps();
    }

    private void loadBitmaps() {
        // Recycle old bitmaps to avoid memory leak on rotation
        for (int i = 0; i < mBtnBmps.length; i++) {
            if (mBtnBmps[i] != null) {
                mBtnBmps[i].recycle();
                mBtnBmps[i] = null;
            }
        }

        Resources res = getResources();

        // Mapeamento: indice do botao -> nome do recurso drawable
        int[] resIds = {
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

        for (int i = 0; i < BTNS.length; i++) {
            int resId = resIds[i];
            if (resId == 0) continue; // skip settings button (no bitmap)

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

        // Desenha sticks (circulos)
        for (int i = 0; i < STKS.length; i++) {
            Stk s = STKS[i];
            canvas.drawCircle(s.cx, s.cy, s.r, mPStkBase);

            float kx = mStickKnobX[i];
            float ky = mStickKnobY[i];
            canvas.drawCircle(kx, ky, s.r * 0.35f, mPStkKnob);
        }

        // Desenha botoes (bitmaps)
        for (int i = 0; i < BTNS.length; i++) {
            Btn b = BTNS[i];

            if (i == BTN_IDX_SETTINGS) {
                // Draw settings gear icon programmatically
                drawSettingsGear(canvas, b);
                continue;
            }

            Bitmap bmp = mBtnBmps[i];
            if (bmp == null) continue;

            int alpha = (mButtonPointerIds[i] != -1) ? 180 : 255;
            mPBmp.setAlpha(alpha);
            canvas.drawBitmap(bmp, b.rect.left, b.rect.top, mPBmp);
        }

        // ── Settings panel (drawn on top) ─────────────────────────────
        if (mShowSettings) {
            drawSettingsPanel(canvas);
        }

        // ── FPS display (drawn when enabled, top-left corner) ─────────
        if (mShowFPS) {
            drawFPSDisplay(canvas);
            // Keep redrawing continuously while FPS is visible.
            // Without this, onDraw only fires on touch events and the
            // FPS counter appears frozen when the user isn't touching.
            postInvalidateDelayed(33); // ~30 fps refresh for the overlay
        }
    }

    // ── Settings gear icon (drawn programmatically) ───────────────────
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

        // Draw gear teeth (8 teeth)
        int teeth = 8;
        for (int i = 0; i < teeth; i++) {
            double angle = (Math.PI * 2 * i) / teeth;
            float sx = cx + (float)(Math.cos(angle) * r);
            float sy = cy + (float)(Math.sin(angle) * r);
            float ex = cx + (float)(Math.cos(angle) * rInner);
            float ey = cy + (float)(Math.sin(angle) * rInner);
            canvas.drawLine(sx, sy, ex, ey, mGearStrokePaint);
        }

        // Draw outer ring
        mGearStrokePaint.setStyle(Paint.Style.STROKE);
        mGearStrokePaint.setStrokeWidth(r * 0.15f);
        canvas.drawCircle(cx, cy, r, mGearStrokePaint);

        // Draw inner dot
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

        // Title text
        mPanelTitlePaint.setColor(SETTINGS_TEXT_COLOR);
        mPanelTitlePaint.setTextSize(h * 0.08f);
        mPanelTitlePaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("CONFIGURACOES", left + w / 2f, top + h * 0.12f, mPanelTitlePaint);

        // Separator line
        mPanelLinePaint.setColor(Color.argb(100, 255, 255, 255));
        mPanelLinePaint.setStrokeWidth(1f);
        float lineY = top + h * 0.17f;
        canvas.drawLine(left + w * 0.1f, lineY, right - w * 0.1f, lineY, mPanelLinePaint);

        // FPS toggle label
        mPanelLabelPaint.setColor(SETTINGS_TEXT_COLOR);
        mPanelLabelPaint.setTextSize(h * 0.055f);
        mPanelLabelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Exibir FPS", left + w * 0.1f,
                         mFpsToggleRect.top - h * 0.02f, mPanelLabelPaint);

        // FPS toggle switch background
        mToggleBgPaint.setStyle(Paint.Style.FILL);
        mToggleBgPaint.setColor(mShowFPS ? SETTINGS_TOGGLE_ON : SETTINGS_TOGGLE_OFF);
        float toggleRound = mFpsToggleRect.height() / 2f;
        canvas.drawRoundRect(mFpsToggleRect, toggleRound, toggleRound, mToggleBgPaint);

        // Toggle thumb
        float thumbSize = mFpsToggleRect.height() * 0.7f;
        float thumbX = mShowFPS
            ? mFpsToggleRect.right - thumbSize - 5f
            : mFpsToggleRect.left + 5f;
        float thumbY = mFpsToggleRect.centerY();
        mToggleThumbPaint.setStyle(Paint.Style.FILL);
        mToggleThumbPaint.setColor(Color.WHITE);
        canvas.drawCircle(thumbX, thumbY, thumbSize / 2f, mToggleThumbPaint);

        // Toggle text (ON/OFF)
        mToggleTextPaint.setColor(Color.WHITE);
        mToggleTextPaint.setTextSize(thumbSize * 0.6f);
        mToggleTextPaint.setTextAlign(Paint.Align.CENTER);
        String toggleLabel = mShowFPS ? "ON" : "OFF";
        float labelOffsetX = mShowFPS ? -thumbSize / 2f - 5f : thumbSize / 2f + 5f;
        canvas.drawText(toggleLabel,
            mFpsToggleRect.centerX() + labelOffsetX,
            mFpsToggleRect.centerY() + thumbSize * 0.25f,
            mToggleTextPaint);

        // FPS current value text
        if (mShowFPS && mNativeAvailable) {
            float fps = SimpsonsActivity.nativeGetFPS();
            mFpsValuePaint.setColor(SETTINGS_ACCENT);
            mFpsValuePaint.setTextSize(h * 0.05f);
            mFpsValuePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(String.format("FPS atual: %.1f", fps),
                left + w / 2f, mFpsToggleRect.bottom + h * 0.08f, mFpsValuePaint);
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

    // ── FPS display (top-left corner overlay) ─────────────────────────
    private void drawFPSDisplay(Canvas canvas) {
        // Avoid per-frame UnsatisfiedLinkError — check once
        if (!mNativeAvailable) {
            try {
                SimpsonsActivity.nativeGetFPS();
                mNativeAvailable = true;
            } catch (UnsatisfiedLinkError e) {
                // JNI not yet loaded — silently skip
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

        mFpsTextPaint.setColor(Color.argb(255, 0, 255, 100)); // green
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

        // Forward diretamente ao SDL nativo (bypass SurfaceView)
        forwardTouchToSDL(ev);

        invalidate();
        return true;
    }

    // ── Debug: string para action human-readable ──────────────────────
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

    private void handleDown(float x, float y, int pid) {
        // ── Settings panel interactions (when visible) ────────────────
        if (mShowSettings) {
            // Settings panel steals all touches when visible.
            // Check close button first.
            if (mSettingsCloseRect.contains(x, y)) {
                mSettingsPointerId = pid;
                mSettingsClosePressed = true;
                Log.i(TAG, "Settings close button pressed");
                return;
            }
            // Check FPS toggle
            if (mFpsToggleRect.contains(x, y)) {
                mSettingsPointerId = pid;
                mFpsTogglePressed = true;
                Log.i(TAG, "FPS toggle pressed");
                return;
            }
            // Tapping outside panel closes it
            if (!mSettingsPanelRect.contains(x, y)) {
                mShowSettings = false;
                Log.i(TAG, "Settings panel closed (tap outside)");
                return;
            }
            // Tap inside panel (non-interactive area) — consume but do nothing
            mSettingsPointerId = pid;
            return;
        }

        // ── Normal HUD interaction ────────────────────────────────────
        // Settings button (check first, before other buttons — it's a special UI element)
        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] == -1 && BTNS[i].rect.contains(x, y)) {
                mButtonPointerIds[i] = pid;
                String label = BTNS[i].label;
                Log.i(TAG, String.format("BTN %s PRESSED pid=%d pos=(%.3f,%.3f)", label, pid, x / getWidth(), y / getHeight()));

                if (i == BTN_IDX_SETTINGS) {
                    // Toggle settings panel
                    mShowSettings = !mShowSettings;
                    mButtonPointerIds[i] = -1; // instant release — it's a toggle, not a hold
                    Log.i(TAG, "Settings panel " + (mShowSettings ? "opened" : "closed"));

                    // When settings opens, release all active game inputs to prevent
                    // stuck buttons/sticks (SDL won't receive ACTION_UP while panel is open).
                    if (mShowSettings) {
                        releaseAllGameInputs();
                    }
                }
                return;
            }
        }
        // Sticks depois
        for (int i = 0; i < STKS.length; i++) {
            if (mStickPointerIds[i] != -1) continue;
            Stk s = STKS[i];
            float dx = x - s.cx, dy = y - s.cy;
            if (dx * dx + dy * dy <= s.r * s.r) {
                mStickPointerIds[i] = pid;
                clampKnob(i, x, y);
                Log.i(TAG, String.format("STK[%d] ACTIVATED pid=%d pos=(%.3f,%.3f)", i, pid, x / getWidth(), y / getHeight()));
                return;
            }
        }
    }

    private void handleMove(float x, float y, int pid) {
        // ── Settings panel drag handling ──────────────────────────────
        if (mShowSettings && mSettingsPointerId == pid) {
            // Update close button pressed state
            boolean wasClosePressed = mSettingsClosePressed;
            mSettingsClosePressed = mSettingsCloseRect.contains(x, y);
            mFpsTogglePressed = mFpsToggleRect.contains(x, y);
            if (wasClosePressed != mSettingsClosePressed || mFpsTogglePressed) {
                // state changed, invalidate
            }
            return;
        }

        // Atualiza sticks ativos para este pointer
        for (int i = 0; i < STKS.length; i++) {
            if (mStickPointerIds[i] == pid) {
                clampKnob(i, x, y);
                return;
            }
        }
        // Atualiza botoes: libera se saiu do rect
        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] == pid && !BTNS[i].rect.contains(x, y)) {
                Log.i(TAG, String.format("BTN %s RELEASED (slide out) pid=%d", BTNS[i].label, pid));
                mButtonPointerIds[i] = -1;
            }
        }
    }

    private void handleUp(int pid) {
        // ── Settings panel touch up ───────────────────────────────────
        if (mShowSettings && mSettingsPointerId == pid) {
            if (mSettingsClosePressed) {
                mShowSettings = false;
                Log.i(TAG, "Settings panel closed via X button");
            } else if (mFpsTogglePressed) {
                mShowFPS = !mShowFPS;
                Log.i(TAG, "FPS display toggled: " + (mShowFPS ? "ON" : "OFF"));
            }
            mSettingsPointerId = -1;
            mSettingsClosePressed = false;
            mFpsTogglePressed = false;
            return;
        }

        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] == pid) {
                Log.i(TAG, String.format("BTN %s RELEASED pid=%d", BTNS[i].label, pid));
                mButtonPointerIds[i] = -1;
            }
        }
        for (int i = 0; i < STKS.length; i++) {
            if (mStickPointerIds[i] == pid) {
                Log.i(TAG, String.format("STK[%d] RELEASED pid=%d", i, pid));
                mStickPointerIds[i] = -1;
                mStickKnobX[i] = STKS[i].cx;
                mStickKnobY[i] = STKS[i].cy;
            }
        }
    }

    private void handleCancel() {
        releaseAllGameInputs();
        mSettingsPointerId = -1;
        mSettingsClosePressed = false;
        mFpsTogglePressed = false;
    }

    /** Release all button and stick inputs and reset knobs to center. */
    private void releaseAllGameInputs() {
        int btnCount = 0, stkCount = 0;
        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] != -1) btnCount++;
            mButtonPointerIds[i] = -1;
        }
        for (int i = 0; i < STKS.length; i++) {
            if (mStickPointerIds[i] != -1) stkCount++;
            mStickPointerIds[i] = -1;
            mStickKnobX[i] = STKS[i].cx;
            mStickKnobY[i] = STKS[i].cy;
        }
        if (btnCount > 0 || stkCount > 0) {
            Log.i(TAG, String.format("Released %d button(s) + %d stick(s)", btnCount, stkCount));
        }
    }

    // ── Forwarding direto para SDL nativo ────────────────────────────
    private void forwardTouchToSDL(MotionEvent ev) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        int touchDevId = ev.getDeviceId();
        if (touchDevId < 0) touchDevId -= 1;

        final int action = ev.getActionMasked();
        final int pointerCount = ev.getPointerCount();

        // When settings panel is visible, don't forward non-game touches to SDL
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
                Log.d(TAG, String.format("forwardToSDL action=%s idx=%d npointers=%d", actionName(action), idx, pointerCount));
                sendTouch(ev, idx, touchDevId, action, w, h);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_DOWN:
                Log.d(TAG, String.format("forwardToSDL action=%s idx=%d npointers=%d", actionName(action), ev.getActionIndex(), pointerCount));
                sendTouch(ev, ev.getActionIndex(), touchDevId, action, w, h);
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.w(TAG, String.format("forwardToSDL ACTION_CANCEL: sending UP for %d pointer(s)", pointerCount));
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

        if (action != MotionEvent.ACTION_MOVE) {
            Log.d(TAG, String.format("sendTouch action=%s pid=%d devId=%d pos=(%.3f,%.3f) pressure=%.2f",
                     actionName(action), pointerFingerId, touchDevId, x, y, p));
        }

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
