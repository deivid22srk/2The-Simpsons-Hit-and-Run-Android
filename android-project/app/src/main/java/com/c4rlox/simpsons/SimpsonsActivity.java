package com.c4rlox.simpsons;

import android.os.Bundle;

import org.libsdl.app.SDLActivity;

/**
 * Simpsons Hit & Run — Activity principal.
 *
 * O HUD de controle touch agora é renderizado em C++ (TouchGui)
 * diretamente pelo motor Pure3D/pddi. O Java GamepadOverlayView
 * foi desabilitado — o código permanece no projeto mas não é
 * mais instanciado.
 */
public class SimpsonsActivity extends SDLActivity {

    // ── Native bridge: real-time FPS from C++ game loop ──────────────
    // Called by GamepadOverlayView each frame to query smoothed FPS.
    public static native float nativeGetFPS();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Java GamepadOverlayView desabilitado ─────────────────────
        // O HUD touch agora é renderizado em C++ (TouchGui) via pddi.
        // O código Java abaixo foi preservado como referência, mas
        // permanece comentado para evitar instanciacao duplicada.
        //
        // mOverlay = new GamepadOverlayView(this);
        // RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
        //     RelativeLayout.LayoutParams.MATCH_PARENT,
        //     RelativeLayout.LayoutParams.MATCH_PARENT
        // );
        // mLayout.addView(mOverlay, lp);
    }
}
