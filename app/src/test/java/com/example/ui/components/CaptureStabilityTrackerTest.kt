package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class CaptureStabilityTrackerTest {

    private fun quad(tl: Float = 0.1f, tr: Float = 0.9f, br: Float = 0.9f, bl: Float = 0.1f) =
        CropPoints(
            topLeft = Offset(tl, tl),
            topRight = Offset(tr, tl),
            bottomRight = Offset(br, bl),
            bottomLeft = Offset(bl, bl)
        )

    @Test
    fun captures_after_required_stable_frames() {
        val tracker = CaptureStabilityTracker(requiredStableFrames = 10)
        repeat(9) { assertFalse(tracker.update(quad())) }
        // 10th consecutive stable frame triggers capture.
        assertTrue(tracker.update(quad()))
        assertEquals(CaptureStabilityTracker.State.ReadyToCapture, tracker.state)
    }

    @Test
    fun resets_on_movement() {
        val tracker = CaptureStabilityTracker(requiredStableFrames = 5, maxMovement = 0.04f)
        tracker.update(quad()) // frame 1
        tracker.update(quad(tl = 0.5f, tr = 0.6f, br = 0.6f, bl = 0.5f)) // big jump
        // After a jump the counter restarts, so it takes full 5 again.
        repeat(4) { assertFalse(tracker.update(quad())) }
        assertTrue(tracker.update(quad()))
    }

    @Test
    fun no_capture_when_no_document() {
        val tracker = CaptureStabilityTracker(requiredStableFrames = 3)
        repeat(10) { assertFalse(tracker.update(null)) }
        assertEquals(CaptureStabilityTracker.State.Searching, tracker.state)
    }

    @Test
    fun cooldown_blocks_immediate_retrigger() {
        val tracker = CaptureStabilityTracker(requiredStableFrames = 2, cooldownFrames = 3)
        assertTrue(tracker.update(quad())) // capture
        tracker.onCaptured()
        // During cooldown, even a stable doc must not trigger.
        repeat(3) { assertFalse(tracker.update(quad())) }
        // After cooldown expires the state returns to Searching.
        assertEquals(CaptureStabilityTracker.State.Searching, tracker.state)
    }
}
