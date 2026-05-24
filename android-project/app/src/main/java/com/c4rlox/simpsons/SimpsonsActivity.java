package com.c4rlox.simpsons;

import android.os.Bundle;
import android.view.ViewGroup;
import org.libsdl.app.SDLActivity;

public class SimpsonsActivity extends SDLActivity {

    private TouchOverlay touchOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        addTouchOverlay();
    }

    private void addTouchOverlay() {
        // Get the SDL content layout and add overlay on top
        ViewGroup layout = getContentView();
        if (layout != null) {
            touchOverlay = new TouchOverlay(this);
            touchOverlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));
            touchOverlay.setClickable(false);
            touchOverlay.setFocusable(false);
            touchOverlay.setVisibility(android.view.View.GONE);
            layout.addView(touchOverlay);
        }
    }
}