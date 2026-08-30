package com.example.androidapp3.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.androidapp3.R
import com.example.androidapp3.databinding.ItemSessionBinding
import com.example.androidapp3.model.StepSession
import com.example.androidapp3.util.StepMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws one row per saved walk in the history list.
 *
 * A RecyclerView adapter has exactly three jobs, which are the three overrides
 * below: build a row when one is needed, fill a row with data, and say how many
 * rows exist. "Recycler" is literal - onBindViewHolder is called again and again
 * on the same handful of views as you scroll, which is why every field must be
 * set on every bind rather than only when it has a value.
 */
class SessionAdapter(
    private val sessions: List<StepSession>
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    // Created once and reused; building a formatter per row would be wasteful.
    private val timeFormat = SimpleDateFormat("EEE h:mm a", Locale.getDefault())

    /** Holds the inflated views for one row so they are not looked up repeatedly. */
    class SessionViewHolder(val binding: ItemSessionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val binding = ItemSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            /* attachToParent = */ false // RecyclerView attaches the row itself.
        )
        return SessionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        val context = holder.itemView.context

        // getQuantityString picks "1 step" vs "2 steps" for us - hard-coding an "s"
        // would be wrong in English and badly wrong in other languages.
        holder.binding.textRowSteps.text =
            context.resources.getQuantityString(R.plurals.steps, session.steps, session.steps)

        holder.binding.textRowDetail.text = context.getString(
            R.string.history_row_detail,
            StepMath.formatKm(session.distanceKm),
            session.calories,
            StepMath.formatDuration(session.durationMs)
        )

        holder.binding.textRowTime.text = timeFormat.format(Date(session.startedAt))
    }

    override fun getItemCount(): Int = sessions.size
}
