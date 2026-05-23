package com.c4rlox.simpsons;

import android.os.Bundle;
import android.widget.RelativeLayout;

import org.libsdl.app.SDLActivity;

/**
 * Simpsons Hit & Run — Activity principal.
 *
 * Adiciona um GamepadOverlayView sobre a SDLSurface para desenhar
 * o HUD de controle touch (D-Pad, botões A/B/X/Y, sticks, L1/R1).
 * Os toques são encaminhados à SDLSurface para processamento pelo
 * InputManager C++.
 */
public class SimpsonsActivity extends SDLActivity {

    private GamepadOverlayView mOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // O overlay desenha o gamepad virtual e encaminha toques ao jogo
        mOverlay = new GamepadOverlayView(this);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        );

        mLayout.addView(mOverlay, lp);
    }
}
