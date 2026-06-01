#ifndef FPS_COUNTER_H
#define FPS_COUNTER_H

#include <radtime.hpp>

// ============================================================================
// FPSCounter — lightweight smoothed FPS measurement.
//
// Called once per frame by Game::Run().  Uses an exponentially weighted
// moving average (EWMA) with configurable smoothing factor to produce
// a stable reading that doesn't flicker.
// ============================================================================
class FPSCounter
{
public:
    FPSCounter();

    // Call once per frame.  Returns the updated smoothed FPS.
    float Update();

    // Current smoothed FPS (no side effects).
    float GetFPS() const { return mSmoothedFPS; }

    // Raw instantaneous FPS of the most recent frame.
    float GetInstantFPS() const { return mInstantFPS; }

    // Number of frames since creation.
    unsigned int GetFrameCount() const { return mFrameCount; }

private:
    radTime64 mLastTime;         // µs
    float     mSmoothedFPS;      // EWMA output
    float     mInstantFPS;       // raw 1/dt of last frame
    unsigned int mFrameCount;

    static const float SMOOTHING; // alpha for EWMA (0..1)
};

// Global singleton for JNI access.
extern FPSCounter* g_FPSCounter;

#endif // FPS_COUNTER_H
