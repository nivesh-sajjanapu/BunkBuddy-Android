package com.example.bunkbuddy

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val overallAttendanceTV = findViewById<TextView>(R.id.overallAttendanceTV)
        val dashboardRecycler = findViewById<RecyclerView>(R.id.dashboardRecycler)

        val subjects = DataHolder.subjects
        val attendanceMap = DataHolder.attendanceMap

        var totalPresent = 0
        var totalClasses = 0
        attendanceMap.values.forEach {
            totalPresent += it.first
            totalClasses += it.second
        }

        val overallAttendance = if (totalClasses == 0) 0 else (totalPresent * 100) / totalClasses
        overallAttendanceTV.text = getString(R.string.dashboard_overall_attendance, overallAttendance)

        dashboardRecycler.layoutManager = LinearLayoutManager(this)
        dashboardRecycler.adapter = DashboardAdapter(subjects)
    }
}