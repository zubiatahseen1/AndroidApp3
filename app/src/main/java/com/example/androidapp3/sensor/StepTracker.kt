package com.example.androidapp3.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * The one place in the app that talks to Android's sensor hardware.
 *
 * This mirrors the shape of the LocationFinder tutorial's location helper:
 *
 *   LocationFinder                     StepQuest
 *   ------------------------------     -------------------------------------
 *   getSystemService(LOCATION_SERVICE) getSystemService(SENSOR_SERVICE)
 *   LocationListener                   SensorEventListener
 *   requestLocationUpdates(...)        registerListener(...)
 *   removeUpdates(...)                 unregisterListener(...)
 *   GPS provider / network fallback    step counter / accelerometer fallback
 *
 * MainActivity never touches SensorManager directly - it starts and stops this
 * class and receives finished numbers through [Listener]. Keeping the hardware
 * behind one small interface is what let me swap in the accelerometer fallback
 * without changing a single line of the Activity.
 */
class StepTracker(
    context: Context,
    private val listener: Listener
) : SensorEventListener {

    /** Which sensor is actually producing the step count right now. */
    enum class Source { HARDWARE_COUNTER, ACCELEROMETER_FALLBACK, NONE }

    /** Callbacks back into the UI. All of these arrive on the main thread. */
    interface Listener {
        /** Fired whenever the running total for the current walk changes. */
        fun onStepsChanged(stepsThisWalk: Int)

        /** Fired on every accelerometer sample, for the live motion readout. */
        fun onMotionChanged(x: Float, y: Float, z: Float, magnitude: Float)
    }

    private companion object {
        const val TAG = "StepTracker"
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // getDefaultSensor returns null when the hardware is absent - ALWAYS null-check it.
    private val stepCounterSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val accelerometerSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Fallback counter, used only when there is no pedometer chip. */
    private val fallbackDetector = AccelerometerStepDetector { onFallbackStep() }

    /**
     * Decided once, at construction, from what the device actually reports.
     * The UI reads this to tell the user where its numbers come from.
     */
    val source: Source = when {
        stepCounterSensor != null -> Source.HARDWARE_COUNTER
        accelerometerSensor != null -> Source.ACCELEROMETER_FALLBACK
        else -> Source.NONE
    }

    /**
     * TYPE_STEP_COUNTER reports steps since the device last rebooted, NOT since
     * the app opened - this tripped me up until I logged the raw value. So we
     * remember the reading at the start of the walk and report the difference.
     * -1 means "we have not seen the first reading yet".
     */
    private var baselineSteps = -1L

    /** Steps counted since the current walk started. */
    var stepsThisWalk = 0
        private set

    /** True between start() and stop(), so the Activity can ignore stale callbacks. */
    var isTracking = false
        private set

    /**
     * Subscribe to the sensors. Called from onResume so the app is only listening
     * while it is on screen - sensors drain battery, and a listener left registered
     * in the background is one of the classic Android bugs.
     */
    fun start() {
        if (isTracking) return

        // The accelerometer is always registered: it drives the live motion readout
        // on screen, and doubles as the step source when there is no pedometer.
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // SENSOR_DELAY_NORMAL is plenty for a step counter and is kinder to the battery
        // than the faster rates - the hardware batches these events anyway.
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        isTracking = true
        Log.d(TAG, "Started tracking with source=$source")
    }

    /**
     * Unsubscribe from everything. Called from onPause. One call releases all
     * sensors this object registered.
     */
    fun stop() {
        if (!isTracking) return
        sensorManager.unregisterListener(this)
        isTracking = false
        Log.d(TAG, "Stopped tracking, stepsThisWalk=$stepsThisWalk")
    }

    /** Start a brand-new walk: forget the baseline and zero the counters. */
    fun resetWalk() {
        baselineSteps = -1L
        stepsThisWalk = 0
        fallbackDetector.reset()
        listener.onStepsChanged(0)
    }

    /** True when this device can count steps at all - used to disable the Start button. */
    fun hasUsableSensor(): Boolean = source != Source.NONE

    // ---------------------------------------------------------------- callbacks

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {

            Sensor.TYPE_STEP_COUNTER -> {
                val totalSinceBoot = event.values[0].toLong()

                // First reading of this walk becomes our zero point.
                if (baselineSteps < 0) {
                    baselineSteps = totalSinceBoot
                }

                // coerceAtLeast(0) protects against a reboot mid-walk resetting the
                // hardware counter below our baseline, which would show a negative count.
                stepsThisWalk = (totalSinceBoot - baselineSteps).toInt().coerceAtLeast(0)
                listener.onStepsChanged(stepsThisWalk)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Only let the accelerometer count steps when nothing better exists,
                // otherwise the two sources would double-count.
                if (source == Source.ACCELEROMETER_FALLBACK) {
                    fallbackDetector.onSample(x, y, z, System.currentTimeMillis())
                }

                listener.onMotionChanged(x, y, z, fallbackDetector.lastMagnitude)
            }
        }
    }

    /**
     * Required by SensorEventListener. Fires when the hardware's confidence in its
     * own readings changes. We only log it - a real fitness app might warn the user
     * that SENSOR_STATUS_UNRELIABLE data is about to make the count drift.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Accuracy of ${sensor?.name} changed to $accuracy")
    }

    /** Called by the fallback detector each time it recognises a footfall. */
    private fun onFallbackStep() {
        stepsThisWalk++
        listener.onStepsChanged(stepsThisWalk)
    }
}
