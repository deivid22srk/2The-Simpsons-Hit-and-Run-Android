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

import org.libsdl.app.R;
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
    // Botoes maiores e melhor espacados para ergonomia mobile.
    private static final Btn[] BTNS = {
        new Btn("UP",     0.16f, 0.70f, 0.10f, 0.10f, 0),  // 0
        new Btn("DOWN",   0.16f, 0.88f, 0.10f, 0.10f, 0),  // 1
        new Btn("LEFT",   0.07f, 0.79f, 0.10f, 0.10f, 0),  // 2
        new Btn("RIGHT",  0.25f, 0.79f, 0.10f, 0.10f, 0),  // 3
        new Btn("A",      0.85f, 0.86f, 0.10f, 0.10f, 0),  // 4
        new Btn("B",      0.95f, 0.76f, 0.10f, 0.10f, 0),  // 5
        new Btn("X",      0.75f, 0.76f, 0.10f, 0.10f, 0),  // 6
        new Btn("Y",      0.85f, 0.66f, 0.10f, 0.10f, 0),  // 7
        new Btn("START",  0.55f, 0.04f, 0.12f, 0.06f, 0),  // 8
        new Btn("SELECT", 0.43f, 0.04f, 0.12f, 0.06f, 0),  // 9
        new Btn("L1",     0.14f, 0.04f, 0.14f, 0.08f, 0),  // 10
        new Btn("R1",     0.86f, 0.04f, 0.14f, 0.08f, 0),  // 11
    };

    // ── Definicões de sticks ──────────────────────────────────────────
    // Sticks maiores e mais abaixo para evitar overlap com D-Pad/face buttons.
    private static final Stk[] STKS = {
        new Stk(0.16f, 0.52f, 0.12f),
        new Stk(0.84f, 0.52f, 0.12f),
    };

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

        // Mapeamento: indice do botao -> nome do recurso drawable            int[] resIds = {
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
        };

        for (int i = 0; i < BTNS.length; i++) {
            int resId = resIds[i];
            if (resId == 0) continue;

            Bitmap raw = BitmapFactory.decodeResource(res, resId);
            if (raw == null) continue;

            Btn b = BTNS[i];
            // Mantem aspect ratio do bitmap original para evitar esticamento
            float targetW = b.rect.width();
            float targetH = b.rect.height();
            float scale = Math.min(targetW / raw.getWidth(), targetH / raw.getHeight());
            int bw = Math.max(1, (int)(raw.getWidth() * scale));
            int bh = Math.max(1, (int)(raw.getHeight() * scale));

            if (i == 10 || i == 11) {
                // L1 / R1: flip horizontal para parecer shoulder direita/esquerda
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
            // Base do stick
            canvas.drawCircle(s.cx, s.cy, s.r, mPStkBase);

            // Knob — usa a posicao do stick ativo, ou centro como fallback
            float kx = mStickKnobX[i];
            float ky = mStickKnobY[i];
            canvas.drawCircle(kx, ky, s.r * 0.35f, mPStkKnob);
        }

        // Desenha botoes (bitmaps)
        for (int i = 0; i < BTNS.length; i++) {
            Bitmap bmp = mBtnBmps[i];
            if (bmp == null) continue;

            Btn b = BTNS[i];
            int alpha = (mButtonPointerIds[i] != -1) ? 180 : 255;

            mPBmp.setAlpha(alpha);
            canvas.drawBitmap(bmp, b.rect.left, b.rect.top, mPBmp);
        }
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

    private void handleDown(float x, float y, int pid) {
        // Botoes primeiro (mais precisos)
        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] == -1 && BTNS[i].rect.contains(x, y)) {
                mButtonPointerIds[i] = pid;
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
                return;
            }
        }
    }

    private void handleMove(float x, float y, int pid) {
        // Atualiza sticks ativos para este pointer
        for (int i = 0; i < STKS.length; i++) {
            if (mStickPointerIds[i] == pid) {
                clampKnob(i, x, y);
                return; // um dedo so controla um stick
            }
        }
        // Atualiza botoes: libera se saiu do rect
        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] == pid && !BTNS[i].rect.contains(x, y)) {
                mButtonPointerIds[i] = -1;
            }
        }
    }

    private void handleUp(int pid) {
        for (int i = 0; i < BTNS.length; i++) {
            if (mButtonPointerIds[i] == pid) mButtonPointerIds[i] = -1;
        }
        for (int i = 0; i < STKS.length; i++) {
            if (mStickPointerIds[i] == pid) {
                mStickPointerIds[i] = -1;
                mStickKnobX[i] = STKS[i].cx;
                mStickKnobY[i] = STKS[i].cy;
            }
        }
    }

    private void handleCancel() {
        for (int i = 0; i < BTNS.length; i++) mButtonPointerIds[i] = -1;
        for (int i = 0; i < STKS.length; i++) {
            mStickPointerIds[i] = -1;
            mStickKnobX[i] = STKS[i].cx;
            mStickKnobY[i] = STKS[i].cy;
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
                // Envia release para todos os pontos ativos para evitar botao preso
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
