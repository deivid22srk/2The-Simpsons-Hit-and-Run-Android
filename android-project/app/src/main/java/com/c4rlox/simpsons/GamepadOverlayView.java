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
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

/**
 * HUD de controle touch que sobrepoe a SDLSurface usando icones Xbox.
 * Botoes usam bitmaps dos assets; sticks sao desenhados como circulos.
 */
public class GamepadOverlayView extends View {

    // ── Cores tema Simpsons ───────────────────────────────────────────
    private static final int STK_BASE_ALPHA = 120;
    private static final int STK_KNOB_ALPHA = 140;

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
    // Os resIds serao preenchidos com placeholder 0 e substituidos
    // no construtor via getResources().
    private static final Btn[] BTNS = {
        new Btn("UP",     0.15f, 0.67f, 0.08f, 0.08f, 0),  // 0
        new Btn("DOWN",   0.15f, 0.83f, 0.08f, 0.08f, 0),  // 1
        new Btn("LEFT",   0.07f, 0.75f, 0.08f, 0.08f, 0),  // 2
        new Btn("RIGHT",  0.23f, 0.75f, 0.08f, 0.08f, 0),  // 3
        new Btn("A",      0.85f, 0.83f, 0.08f, 0.08f, 0),  // 4
        new Btn("B",      0.93f, 0.75f, 0.08f, 0.08f, 0),  // 5
        new Btn("X",      0.77f, 0.75f, 0.08f, 0.08f, 0),  // 6
        new Btn("Y",      0.85f, 0.67f, 0.08f, 0.08f, 0),  // 7
        new Btn("START",  0.55f, 0.05f, 0.10f, 0.05f, 0),  // 8
        new Btn("SELECT", 0.45f, 0.05f, 0.10f, 0.05f, 0),  // 9
        new Btn("L1",     0.15f, 0.05f, 0.12f, 0.07f, 0),  // 10
        new Btn("R1",     0.85f, 0.05f, 0.12f, 0.07f, 0),  // 11
    };

    // ── Definicões de sticks ──────────────────────────────────────────
    private static final Stk[] STKS = {
        new Stk(0.15f, 0.52f, 0.10f),
        new Stk(0.85f, 0.52f, 0.10f),
    };

    // ── Estado de toque ───────────────────────────────────────────────
    private int mActivePointerId = -1;
    private int mActiveButtonIdx = -1;
    private int mActiveStickIdx = -1;
    private float mStickKnobX, mStickKnobY;

    // ── Bitmaps dos botoes ────────────────────────────────────────────
    private Bitmap[] mBtnBmps;

    // ── Paints para sticks ────────────────────────────────────────────
    private Paint mPStkBase;
    private Paint mPStkKnob;
    private Paint mPBmp;  // reused in onDraw to avoid per-frame allocation

    // ── Construtor ────────────────────────────────────────────────────
    public GamepadOverlayView(Context ctx) {
        super(ctx);
        mBtnBmps = new Bitmap[BTNS.length];
    }

    // ── onSizeChanged: calcula geometria e carrega bitmaps ────────────
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (w == 0 || h == 0) return;

        // Calcula rects dos botoes
        for (Btn b : BTNS) {
            b.rect.set(b.nx * w, b.ny * h, (b.nx + b.nw) * w, (b.ny + b.nh) * h);
        }

        // Calcula posicoes dos sticks
        float minDim = Math.min(w, h);
        for (Stk s : STKS) {
            s.cx = s.ncx * w;
            s.cy = s.ncy * h;
            s.r = s.nr * minDim;
        }

        // Inicializa knob do stick no centro do primeiro stick
        if (STKS.length > 0) {
            mStickKnobX = STKS[0].cx;
            mStickKnobY = STKS[0].cy;
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
        String pkg = getContext().getPackageName();

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
            R.drawable.button_lb,          // 10: L1
            R.drawable.button_lb,          // 11: R1 (flipped in onDraw)
        };

        for (int i = 0; i < BTNS.length; i++) {
            int resId = resIds[i];
            if (resId == 0) continue;

            Bitmap raw = BitmapFactory.decodeResource(res, resId);
            if (raw == null) continue;

            Btn b = BTNS[i];
            int bw = Math.max(1, (int) b.rect.width());
            int bh = Math.max(1, (int) b.rect.height());

            if (i == 11) {
                // R1: flip horizontal
                Bitmap scaled = Bitmap.createScaledBitmap(raw, bw, bh, true);
                Matrix matrix = new Matrix();
                matrix.preScale(-1f, 1f);
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
            // Base do stick
            canvas.drawCircle(s.cx, s.cy, s.r, mPStkBase);

            // Knob — usa a posicao do stick ativo, ou centro como fallback
            float kx, ky;
            if (mActiveStickIdx == i) {
                kx = mStickKnobX;
                ky = mStickKnobY;
            } else {
                kx = s.cx;
                ky = s.cy;
            }
            canvas.drawCircle(kx, ky, s.r * 0.35f, mPStkKnob);
        }

        // Desenha botoes (bitmaps)
        for (int i = 0; i < BTNS.length; i++) {
            Bitmap bmp = mBtnBmps[i];
            if (bmp == null) continue;

            Btn b = BTNS[i];
            int alpha = (mActiveButtonIdx == i) ? 160 : 255;

            mPBmp.setAlpha(alpha);
            canvas.drawBitmap(bmp, b.rect.left, b.rect.top, mPBmp);
        }
    }

    // ── Touch event handling ──────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        final int pid = ev.getPointerId(ev.getActionIndex());
        final float x = ev.getX(ev.getActionIndex());
        final float y = ev.getY(ev.getActionIndex());

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleDown(x, y, pid);
                break;
            case MotionEvent.ACTION_MOVE:
                if (pid == mActivePointerId) {
                    handleMove(x, y);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pid == mActivePointerId) {
                    handleUp();
                }
                break;
        }

        // Forward diretamente ao SDL nativo (bypass SurfaceView)
        forwardTouchToSDL(ev);

        invalidate();
        return true;
    }

    private void handleDown(float x, float y, int pid) {
        // Botoes primeiro (mais precisos)
        for (int i = 0; i < BTNS.length; i++) {
            if (BTNS[i].rect.contains(x, y)) {
                mActiveButtonIdx = i;
                mActivePointerId = pid;
                mActiveStickIdx = -1;
                return;
            }
        }
        // Sticks depois
        for (int i = 0; i < STKS.length; i++) {
            Stk s = STKS[i];
            float dx = x - s.cx, dy = y - s.cy;
            if (dx * dx + dy * dy <= s.r * s.r * 1.2f) {
                mActiveStickIdx = i;
                mActivePointerId = pid;
                mActiveButtonIdx = -1;
                clampKnob(i, x, y);
                return;
            }
        }
    }

    private void handleMove(float x, float y) {
        if (mActiveStickIdx >= 0) {
            clampKnob(mActiveStickIdx, x, y);
        } else if (mActiveButtonIdx >= 0) {
            if (!BTNS[mActiveButtonIdx].rect.contains(x, y)) {
                mActiveButtonIdx = -1;
                mActivePointerId = -1;
            }
        }
    }

    private void handleUp() {
        mActiveButtonIdx = -1;
        mActiveStickIdx = -1;
        mActivePointerId = -1;
        // Reseta knob para o centro do stick 0
        if (STKS.length > 0) {
            mStickKnobX = STKS[0].cx;
            mStickKnobY = STKS[0].cy;
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

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < pointerCount; i++) {
                    sendTouch(ev, i, touchDevId, action, w, h);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_DOWN:
                sendTouch(ev, 0, touchDevId, action, w, h);
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_DOWN:
                sendTouch(ev, ev.getActionIndex(), touchDevId, action, w, h);
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
        mStickKnobX = s.cx + dx;
        mStickKnobY = s.cy + dy;
    }
}
