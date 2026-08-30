package com.example.androidapp3.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Turns a raw step count into numbers a person actually cares about.
 *
 * The LocationFinder tutorial did the same kind of job with Geocoder: a sensor
 * hands you a raw value (there a latitude/longitude pair, here an integer), and
 * the app is responsible for translating it into something readable. Keeping
 * that translation in its own file means MainActivity stays about the UI.
 */
object StepMath {

    /** Average stride for an adult walking, in metres. Good enough for an estimate. */
    const val STRIDE_METRES = 0.762

    /** Very rough energy cost of one step for an average adult. */
    private const val KCAL_PER_STEP = 0.04

    /** The daily-ish target the progress bar fills toward. */
    const val STEP_GOAL = 1000

    /** Steps -> kilometres. */
    fun distanceKm(steps: Int): Double = steps * STRIDE_METRES / 1000.0

    /** Steps -> calories, rounded to a whole number because decimals imply false precision. */
    fun calories(steps: Int): Int = Math.round(steps * KCAL_PER_STEP).toInt()

    /** Progress toward [STEP_GOAL], clamped to 0..100 so the bar never overflows. */
    fun goalPercent(steps: Int): Int =
        (steps * 100 / STEP_GOAL).coerceIn(0, 100)

    /**
     * Steps per minute. Guards against dividing by zero in the first moments of a
     * walk, which is exactly the kind of edge case that crashes an app on demo day.
     */
    fun cadence(steps: Int, elapsedMs: Long): Double {
        val minutes = elapsedMs / 60_000.0
        return if (minutes <= 0.0) 0.0 else steps / minutes
    }

    /** 1.234 -> "1.23" - two decimals, using a fixed Locale so it never switches to a comma. */
    fun formatKm(km: Double): String = String.format(Locale.US, "%.2f", km)

    fun formatCadence(value: Double): String = String.format(Locale.US, "%.0f", value)

    /** 125_000 -> "2m 05s" */
    fun formatDuration(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format(Locale.US, "%dm %02ds", minutes, seconds)
    }
}
