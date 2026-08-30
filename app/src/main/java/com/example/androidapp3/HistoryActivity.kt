package com.example.androidapp3

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.androidapp3.adapter.SessionAdapter
import com.example.androidapp3.data.SessionRepository
import com.example.androidapp3.databinding.ActivityHistoryBinding

/**
 * Second screen: every walk saved during this app session.
 *
 * It reads straight from [SessionRepository], so it always reflects whatever
 * MainActivity has saved. Nothing here touches a sensor - separating "collect the
 * data" from "show the data" is what makes both screens easy to reason about.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerSessions.layoutManager = LinearLayoutManager(this)
        binding.recyclerSessions.adapter = SessionAdapter(SessionRepository.all)

        showEmptyStateIfNeeded()
    }

    /**
     * An empty list is a state, not a bug - showing a friendly message beats
     * showing a blank white screen and letting the user wonder what broke.
     */
    private fun showEmptyStateIfNeeded() {
        val isEmpty = SessionRepository.count == 0
        binding.textEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerSessions.visibility = if (isEmpty) View.GONE else View.VISIBLE

        binding.textTotals.text = getString(
            R.string.history_total,
            SessionRepository.count,
            SessionRepository.totalSteps
        )
    }
}
