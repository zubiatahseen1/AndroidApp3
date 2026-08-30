package com.example.androidapp3.model

/**
 * One completed walk.
 *
 * This is a plain immutable data class - it holds numbers only and knows nothing
 * about Android, which makes it easy to read, log and (later) unit test.
 *
 * @param steps       how many steps were counted between Start and Stop
 * @param distanceKm  steps converted to kilometres using the user's stride length
 * @param calories    rough energy estimate derived from the step count
 * @param durationMs  wall-clock length of the walk, in milliseconds
 * @param startedAt   epoch millis for when Start was pressed (used for the label)
 * @param source      which sensor produced the count, so history stays honest
 */
data class StepSession(
    val steps: Int,
    val distanceKm: Double,
    val calories: Int,
    val durationMs: Long,
    val startedAt: Long,
    val source: String
)
