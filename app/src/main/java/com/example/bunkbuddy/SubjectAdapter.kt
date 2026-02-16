package com.example.bunkbuddy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.widget.ProgressBar

class SubjectAdapter(
    private val subjects: List<String>,
    private val attendanceMap: Map<String, Pair<Int, Int>>,
    private val streakMap: Map<String, Int>,
    private val onItemClick: (String) -> Unit,
    private val onItemLongClick: (String) -> Unit

) : RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder>() {

    class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val riskText: TextView = itemView.findViewById(R.id.riskText)
        val progress: ProgressBar = itemView.findViewById(R.id.attendanceProgress)
        val subjectName: TextView = itemView.findViewById(R.id.subjectName)
        val subjectAttendance: TextView = itemView.findViewById(R.id.subjectAttendance)
        val card: CardView = itemView as CardView
        val streakText: TextView = itemView.findViewById(R.id.streakText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject, parent, false)
        return SubjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        val subject = subjects[position]
        val streak = streakMap[subject] ?: 0
        val context = holder.itemView.context

        if (streak > 0) {
            holder.streakText.text = context.getString(R.string.streak_text, streak)
        } else {
            holder.streakText.text = ""
        }
        val (present, total) = attendanceMap[subject] ?: Pair(0, 0)

        val percentage = if (total == 0) 0 else (present * 100) / total

        holder.subjectName.text = subject
        holder.subjectAttendance.text = context.getString(R.string.attendance_text, percentage)
        holder.progress.progress = percentage
        val required = 0.75

        if (total == 0) {
            holder.riskText.text = context.getString(R.string.no_classes_yet)
        } else {

            val bunk = present - required * total

            if (percentage < 75) {
                holder.riskText.text = context.getString(R.string.below_safe_zone)
                holder.riskText.setTextColor(ContextCompat.getColor(context, R.color.colorDanger))

            } else if (bunk < 1) {
                holder.riskText.text = context.getString(R.string.warning_attendance_drop)
                holder.riskText.setTextColor(ContextCompat.getColor(context, R.color.colorWarning))

            } else {
                val safeBunks = kotlin.math.floor(bunk).toInt()
                holder.riskText.text = context.getString(R.string.safe_for_more_absences, safeBunks)
                holder.riskText.setTextColor(ContextCompat.getColor(context, R.color.colorSafe))
            }
        }


        // Color indicator
        when {
            percentage >= 80 -> holder.subjectAttendance.setTextColor(ContextCompat.getColor(context, R.color.colorSafe))
            percentage >= 75 -> holder.subjectAttendance.setTextColor(ContextCompat.getColor(context, R.color.colorWarning))
            else -> holder.subjectAttendance.setTextColor(ContextCompat.getColor(context, R.color.colorDanger))
        }

        holder.card.setOnClickListener {
            onItemClick(subject)
        }
        holder.card.setOnLongClickListener {
            onItemLongClick(subject)
            true
        }
    }

    override fun getItemCount(): Int = subjects.size
}