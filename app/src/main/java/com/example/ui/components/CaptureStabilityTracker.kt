package com.example.ui.components

import androidx.compose.ui.geometry.Offset

/**
 * Frame-count based stability state machine for auto-capture.
 *
 * States:
 *   Searching     - no valid quad (or just started)
 *   Stabilizing   - a valid quad is present and being tracked
 *   ReadyToCapture- the quad has been stable for [requiredStableFrames] frames
 *   Captured      - a capture fired; locked out for [cooldownFrames] frames
 *
 * The thresholds are expressed in FRAMES, not wall-clock, so they adapt to the
 * analysis throttle rate (e.g. 5 fps -> 10 frames ~= 2 s).
 */
class CaptureStabilityTracker(
    private val requiredStableFrames: Int = 10,
    private val cooldownFrames: Int = 10,
    private val maxMovement: Float = 0.04f // normalized; ~4% of frame edge between frames
) {
    enum class State { Searching, Stabilizing, ReadyToCapture, Captured }

    var state: State = State.Searching
        private set

    private var stableCount = 0
    private var cooldownCount = 0
    private var lastQuad: CropPoints? = null

    /**
     * Feed one analyzed frame.
     * @param quad the detected quad for this frame, or null if none.
     * @return true if this frame should trigger an auto-capture.
     */
    fun update(quad: CropPoints?): Boolean {
        // Cooldown dominates everything: after any capture we ignore detection
        // for a while so we don't immediately re-fire on the same document.
        if (state == State.Captured) {
            if (--cooldownCount <= 0) {
                state = State.Searching
                lastQuad = null
                stableCount = 0
            }
            return false
        }

        if (quad == null) {
            state = State.Searching
            stableCount = 0
            lastQuad = null
            return false
        }

        val moved = lastQuad?.let { DocumentDetector.normalizedMovement(it, quad) } ?: Float.MAX_VALUE
        val stillEnough = moved <= maxMovement

        if (stillEnough) {
            stableCount++
        } else {
            // Device shake / hand adjusting -> reset; keep the new quad as reference.
            stableCount = 1
        }
        lastQuad = quad

        state = when {
            stableCount >= requiredStableFrames -> State.ReadyToCapture
            else -> State.Stabilizing
        }

        return state == State.ReadyToCapture
    }

    /** Call after a capture (auto OR manual) to start the cooldown lockout. */
    fun onCaptured() {
        state = State.Captured
        cooldownCount = cooldownFrames
        stableCount = 0
        lastQuad = null
    }

    val stableFrames: Int get() = stableCount
}
