package com.c4rlox.simpsons;

import android.hardware.HardwareBuffer;
import android.view.Surface;

public class LsfgBridge {

    static {
        System.loadLibrary("lsfg-integration");
    }

    public static native String nativeVersion();

    public static native int nativeInit(
        String cacheDir, int width, int height,
        int multiplier, float flowScale,
        boolean performance, boolean hdr, boolean antiArtifacts
    );

    public static native int nativeSetDllPath(String dllPath, String cacheDir);

    public static native void nativeSetFrameGenEnabled(boolean enabled);

    public static native boolean nativeIsFrameGenEnabled();

    public static native boolean nativeIsFrameGenActive();

    public static native void nativeSetOutputSurface(Surface surface, int w, int h);

    public static native void nativePushFrame(HardwareBuffer hardwareBuffer, long timestampNs);

    public static native long nativeGetGeneratedFrameCount();

    public static native long nativeGetPostedFrameCount();

    public static native void nativeSetBypass(boolean bypass);

    public static native void nativeShutdown();
}
