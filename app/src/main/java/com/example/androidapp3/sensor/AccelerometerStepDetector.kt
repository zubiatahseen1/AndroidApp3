package com.example.androidapp3.sensor

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Counts steps from raw accelerometer samples.
 *
 * Why this class exists: TYPE_STEP_COUNTER is a hardware sensor and plenty of
 * devices - including every Android emulator I tested on - simply do not have it.
 * Rather than showing an empty screen, StepQuest falls back to detecting the
 * bounce of a walk in the accelerometer stream. This is the sensor equivalent of
 * LocationFinder falling back from GPS to the network provider.
 *
 * The algorithm is a classic three-stage peak detector:
 *
 *  1. Collapse the x/y/z vector into a single magnitude, so it no longer matters
 *     which way the phone is held.
 *  2. Subtract gravity (~9.81) and smooth the result with a low-pass filter, so
 *     small hand jitter does not register.
 *  3. Count a step each time the smoothed signal crosses [THRESHOLD] on the way
 *     up, but only if [MIN_STEP_INTERVAL_MS] has passed since the last one.
 *     That debounce is what stops a single vigorous shake counting as ten steps.
 *
 * @param onStep called once per detected step, on the sensor callback thread.
 */
class AccelerometerStepDetector(private val onStep: () -> Unit) {

    private companion object {
        /** Earth gravity, the constant baseline in every accelerometer reading. */
        const val GRAVITY = 9.81f

        /** How much movement (m/s^2 above gravity) counts as a footfall. */
        const val THRESHOLD = 1.6f

        /** Nobody walks faster than ~4 steps/second; anything quicker is noise. */
        const val MIN_STEP_INTERVAL_MS = 250L

        /** 0 = ignore new data, 1 = no smoothing. 0.2 keeps the signal responsive but calm. */
        const val SMOOTHING = 0.2f
    }

    private var smoothedMagnitude = 0f
    private var wasAboveThreshold = false
    private var lastStepAt = 0L

    /** Latest smoothed movement value, exposed so the UI can show something live. */
    var lastMagnitude: Float = 0f
        private set

    /**
     * Feed one accelerometer sample in.
     * @param timestampMs the moment the sample arrived, used for the debounce.
     */
    fun onSample(x: Float, y: Float, z: Float, timestampMs: Long) {
        // 1. Direction-independent magnitude of the acceleration vector.
        val magnitude = sqrt(x * x + y * y + z * z)

        // 2. Remove gravity, then low-pass filter what is left.
        val movement = abs(magnitude - GRAVITY)
        smoothedMagnitude += SMOOTHING * (movement - smoothedMagnitude)
        lastMagnitude = smoothedMagnitude

        // 3. Rising-edge detection with a debounce.
        val isAboveThreshold = smoothedMagnitude > THRESHOLD
        val longEnoughSinceLastStep = timestampMs - lastStepAt > MIN_STEP_INTERVAL_MS

        if (isAboveThreshold && !wasAboveThreshold && longEnoughSinceLastStep) {
            lastStepAt = timestampMs
            onStep()
        }
        wasAboveThreshold = isAboveThreshold
    }

    /** Wipe the filter state so a new walk does not inherit the old one's momentum. */
    fun reset() {
        smoothedMagnitude = 0f
        lastMagnitude = 0f
        wasAboveThreshold = false
        lastStepAt = 0L
    }
}
