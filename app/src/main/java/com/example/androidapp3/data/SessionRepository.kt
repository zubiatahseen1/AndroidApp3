package com.example.androidapp3.data

import com.example.androidapp3.model.StepSession

/**
 * In-memory store for finished walks.
 *
 * Declared as an `object`, so Kotlin creates exactly one instance for the whole
 * app - every Activity that imports it sees the same list. That is the simplest
 * way to share state between MainActivity and HistoryActivity without a database.
 *
 * Trade-off worth knowing: because this lives only in memory, history is wiped
 * when the process is killed. That is deliberate for this assignment (no
 * persistence layer yet); Room or DataStore would be the next step.
 */
object SessionRepository {

    // Private so nothing outside can add/remove items without going through save().
    private val sessions = mutableListOf<StepSession>()

    /** Newest walk first, which is the order the history list wants to show. */
    val all: List<StepSession>
        get() = sessions.reversed()

    val count: Int
        get() = sessions.size

    /** Every step ever counted in this app session. */
    val totalSteps: Int
        get() = sessions.sumOf { it.steps }

    fun save(session: StepSession) {
        sessions.add(session)
    }

    fun clear() {
        sessions.clear()
    }
}
