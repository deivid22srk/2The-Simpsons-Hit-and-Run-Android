package com.c4rlox.simpsons;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * HUD de controle touch desenhado em Java que sobrepõe a SDLSurface.
 *
 * Desenha: D-Pad, botões A/B/X/Y, sticks analógicos, L1/R1, START/SELECT.
 * Encaminha toques para a SDLSurface → C++ TouchGui → InputManager.
 */
public class GamepadOverlayView extends View {

    // ── Cores tema Simpsons ───────────────────────────────────────────
    private static final int SIMPSONS_YELLOW = Color.rgb(255, 234, 2);
    private static final int SIMPSONS_BLUE   = Color.rgb(17, 31, 161);
    private static final int WHITE           = Color.WHITE;
    private static final int YELLOW_PRESSED  = Color.rgb(255, 255, 0);

    // ── Estado de toque ───────────────────────────────────────────────
    private int mActivePointerId = -1;
    private int mActiveButtonIdx = -1;
    private int mActiveStickIdx  = -1;
    private float mStickKnobX, mStickKnobY;

    // ── View alvo para forwarding ─────────────────────────────────────
    private View mTargetSurface;

    // ── Dimensões da tela (px) ────────────────────────────────────────
    private int mViewW, mViewH;

    // ── Definição de botão ────────────────────────────────────────────
    private static class Btn {
        final String label;
        final float nx, ny, nw, nh;
        final RectF rect = new RectF();
        Btn(String l, float x, float y, float w, float h) {
            label = l; nx = x; ny = y; nw = w; nh = h;
        }
    }

    private static final Btn[] BTNS = {
        new Btn("▲", 0.15f, 0.67f, 0.08f, 0.08f),   // 0 UP
        new Btn("▼", 0.15f, 0.83f, 0.08f, 0.08f),   // 1 DOWN
        new Btn("◀", 0.07f, 0.75f, 0.08f, 0.08f),   // 2 LEFT
        new Btn("▶", 0.23f, 0.75f, 0.08f, 0.08f),   // 3 RIGHT
        new Btn("A", 0.85f, 0.83f, 0.08f, 0.08f),   // 4 A
        new Btn("B", 0.93f, 0.75f, 0.08f, 0.08f),   // 5 B
        new Btn("X", 0.77f, 0.75f, 0.08f, 0.08f),   // 6 X
        new Btn("Y", 0.85f, 0.67f, 0.08f, 0.08f),   // 7 Y
        new Btn("START",  0.55f, 0.05f, 0.10f, 0.05f), // 8
        new Btn("SELECT", 0.45f, 0.05f, 0.10f, 0.05f), // 9
        new Btn("L1", 0.15f, 0.05f, 0.12f, 0.07f),     // 10
        new Btn("R1", 0.85f, 0.05f, 0.12f, 0.07f),     // 11
    };

    // ── Definição de stick ────────────────────────────────────────────
    private static class Stk {
        final float ncx, ncy, nr;
        float cx, cy, r;
        Stk(float x, float y, float rad) { ncx = x; ncy = y; nr = rad; }
    }

    private static final Stk[] STKS = {
        new Stk(0.15f, 0.52f, 0.10f),
        new Stk(0.85f, 0.52f, 0.10f),
    };

    // ── Paints ────────────────────────────────────────────────────────
    private Paint mPBtn, mPBtnPrs, mPBdr, mPStkBase, mPStkKnob, mPTxt, mPTxtShd;
    private float mCRad; // corner radius

    // ── Construtores ──────────────────────────────────────────────────
    public GamepadOverlayView(Context ctx) {
        super(ctx);
    }

    public GamepadOverlayView(Context ctx, View target) {
        super(ctx);
        mTargetSurface = target;
    }

    public void setTargetSurface(View target) { mTargetSurface = target; }

    // ── onSizeChanged ─────────────────────────────────────────────────
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        mViewW = w; mViewH = h;

        for (Btn b : BTNS) {
            b.rect.set(b.nx * w, b.ny * h, (b.nx + b.nw) * w, (b.ny + b.nh) * h);
        }
        for (Stk s : STKS) {
            s.cx = s.ncx * w;
            s.cy = s.ncy * h;
            s.r  = s.nr * Math.min(w, h);
        }

        mCRad = 0.01f * Math.min(w, h);
        float sw = Math.max(2f, w * 0.002f);
        float ts = Math.max(10f, h * 0.025f);

        mPBtn = mkFill(SIMPSONS_YELLOW, 100);
        mPBtnPrs = mkFill(YELLOW_PRESSED, 180);
        mPBdr = mkStroke(SIMPSONS_BLUE, 160, sw);
        mPStkBase = mkStroke(SIMPSONS_YELLOW, 40, sw * 1.5f);
        mPStkKnob = mkFill(SIMPSONS_BLUE, 120);
        mPTxt = mkText(WHITE, ts);
        mPTxtShd = mkText(SIMPSONS_BLUE, ts);
    }

    private static Paint mkFill(int c, int a) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL); p.setColor(c); p.setAlpha(a);
        return p;
    }
    private static Paint mkStroke(int c, int a, float sw) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE); p.setColor(c); p.setAlpha(a); p.setStrokeWidth(sw);
        return p;
    }
    private static Paint mkText(int c, float sz) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(c); p.setTextSize(sz); p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        return p;
    }

    // ── onDraw ────────────────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (mViewW == 0) return;

        // Sticks
        for (int i = 0; i < STKS.length; i++) {
            Stk s = STKS[i];
            c.drawCircle(s.cx, s.cy, s.r, mPStkBase);
            float kx = (i == mActiveStickIdx) ? mStickKnobX : s.cx;
            float ky = (i == mActiveStickIdx) ? mStickKnobY : s.cy;
            c.drawCircle(kx, ky, s.r * 0.35f, mPStkKnob);
        }

        // Botões
        for (int i = 0; i < BTNS.length; i++) {
            Btn b = BTNS[i];
            boolean prs = (i == mActiveButtonIdx);
            c.drawRoundRect(b.rect, mCRad, mCRad, prs ? mPBtnPrs : mPBtn);
            c.drawRoundRect(b.rect, mCRad, mCRad, mPBdr);

            float tx = b.rect.centerX();
            float ty = b.rect.centerY() - (mPTxt.descent() + mPTxt.ascent()) / 2;
            float so = mViewW * 0.002f;
            c.drawText(b.label, tx + so, ty + so, mPTxtShd);
            c.drawText(b.label, tx, ty, mPTxt);
        }
    }

    // ── onTouchEvent ──────────────────────────────────────────────────
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int act = ev.getActionMasked();
        final int pi  = ev.getActionIndex();
        final int pid = ev.getPointerId(pi);
        final float ex = ev.getX(pi);
        final float ey = ev.getY(pi);

        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleDown(ex, ey, pid);
                break;
            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId >= 0) {
                    int idx = ev.findPointerIndex(mActivePointerId);
                    if (idx >= 0) handleMove(ev.getX(idx), ev.getY(idx));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (pid == mActivePointerId) handleUp();
                break;
            case MotionEvent.ACTION_CANCEL:
                handleUp();
                break;
        }

        // Forward cópia para SDLSurface (C++ TouchGui)
        if (mTargetSurface != null) {
            MotionEvent copy = MotionEvent.obtain(ev);
            mTargetSurface.dispatchTouchEvent(copy);
            copy.recycle();
        }

        invalidate();
        return true;
    }

    // ── Lógica de toque ───────────────────────────────────────────────

    private void handleDown(float x, float y, int pid) {
        // Botoes primeiro — alvos menores e precisos.
        // Sticks sao zonas amplas; se verificados primeiro,
        // engolem os botoes (D-Pad e A/B/X/Y estao dentro
        // da area dos sticks).
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
            }
        }
    }

    private void handleUp() {
        mActiveButtonIdx = -1;
        mActiveStickIdx  = -1;
        mActivePointerId = -1;
    }

    private void clampKnob(int si, float x, float y) {
        Stk s = STKS[si];
        float dx = x - s.cx, dy = y - s.cy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > s.r && d > 0) {
            mStickKnobX = s.cx + (dx / d) * s.r;
            mStickKnobY = s.cy + (dy / d) * s.r;
        } else {
            mStickKnobX = x;
            mStickKnobY = y;
        }
    }
}
