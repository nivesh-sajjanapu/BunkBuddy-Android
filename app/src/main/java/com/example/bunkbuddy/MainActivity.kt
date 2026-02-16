package com.example.bunkbuddy

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.net.Uri
import android.content.Intent
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : AppCompatActivity() {

    private var streakMap = mutableMapOf<String, Int>()

    private lateinit var subjectRecycler: RecyclerView
    private lateinit var adapter: SubjectAdapter
    private lateinit var overallAttendance: TextView
    private lateinit var addSubjectBtn: FloatingActionButton
    private val prefsName = "attendance_prefs"

    private val subjects = mutableListOf<String>()
    private val attendanceMap = mutableMapOf<String, Pair<Int, Int>>() // Subject -> (Present, Total)

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        subjectRecycler = findViewById(R.id.subjectRecycler)
        overallAttendance = findViewById(R.id.overallAttendance)
        addSubjectBtn = findViewById(R.id.addSubjectBtn)

        subjectRecycler.layoutManager = LinearLayoutManager(this)
        subjectRecycler.setHasFixedSize(true)
        subjectRecycler.itemAnimator?.apply {
            addDuration = 300
            removeDuration = 300
        }

        adapter = SubjectAdapter(
            subjects,
            attendanceMap,
            streakMap,
            { subject -> showAttendanceDialog(subject) },
            { subject -> showEditDeleteDialog(subject) }
        )
        subjectRecycler.adapter = adapter

        loadData()

        addSubjectBtn.setOnClickListener {
            val editText = EditText(this)
            android.app.AlertDialog.Builder(this)
                .setTitle("Enter Subject Name")
                .setView(editText)
                .setPositiveButton("Add") { _, _ ->
                    val name = editText.text.toString()
                    if (name.isNotBlank() && !subjects.contains(name)) {
                        subjects.add(name)
                        attendanceMap[name] = Pair(0, 0)
                        saveData()
                        adapter.notifyItemInserted(subjects.size - 1)
                        updateOverall()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        val uploadBtn = findViewById<FloatingActionButton>(R.id.uploadBtn)

        uploadBtn.setOnClickListener {
            pickImage.launch("image/*")
        }
    }
    private fun processImage(uri: Uri) {

        val image = InputImage.fromFilePath(this, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                extractSubjects(visionText.text)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to read text", Toast.LENGTH_SHORT).show()
            }
    }
    private fun extractSubjects(text: String) {

        val lines = text.lines()
        val detectedSubjects = mutableListOf<String>()

        val ignoreWords = listOf(
            "monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday",
            "break", "lunch", "time", "room", "lab"
        )

        for (line in lines) {

            val cleaned = line.trim()

            if (cleaned.length in 4..30 &&
                ignoreWords.none { cleaned.lowercase().contains(it) } &&
                cleaned.any { it.isLetter() }
            ) {
                detectedSubjects.add(cleaned)
            }
        }

        showImportPreview(detectedSubjects.distinct())
    }
    private fun showImportPreview(detectedSubjects: List<String>) {

        if (detectedSubjects.isEmpty()) {
            Toast.makeText(this, "No subjects detected", Toast.LENGTH_SHORT).show()
            return
        }

        val checkedItems = BooleanArray(detectedSubjects.size) { true }

        android.app.AlertDialog.Builder(this)
            .setTitle("Import Subjects")
            .setMultiChoiceItems(
                detectedSubjects.toTypedArray(),
                checkedItems
            ) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Import") { _, _ ->

                for (i in detectedSubjects.indices) {
                    if (checkedItems[i]) {
                        val subject = detectedSubjects[i]

                        if (!subjects.contains(subject)) {
                            subjects.add(subject)
                            attendanceMap[subject] = Pair(0, 0)
                            streakMap[subject] = 0
                        }
                    }
                }

                adapter.notifyDataSetChanged()
                updateOverall()
                saveData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun showAttendanceDialog(subject: String) {
        val (present, total) = attendanceMap[subject]!!
        val attendance = calculateAttendance(present, total)
        val required = 0.75

        val statusMessage = if (total == 0) {
            "No classes yet."
        } else {
            val bunk = present - required * total
            if (bunk >= 0) {
                "✅ You can bunk ${kotlin.math.floor(bunk).toInt()} classes"
            } else {
                var x = 0
                while ((present + x).toDouble() / (total + x) < required) {
                    x++
                }
                "❌ Attend $x more classes to reach 75%"
            }
        }

        val message = """
            Present: $present
            Total: $total
            Attendance: $attendance%

            $statusMessage
        """.trimIndent()

        android.app.AlertDialog.Builder(this)
            .setTitle(subject)
            .setMessage(message)
            .setPositiveButton("Mark Present") { _, _ ->
                attendanceMap[subject] = Pair(present + 1, total + 1)
                updateAndSave(subject)
                val currentStreak = streakMap[subject] ?: 0
                streakMap[subject] = currentStreak + 1
            }
            .setNegativeButton("Mark Absent") { _, _ ->
                attendanceMap[subject] = Pair(present, total + 1)
                updateAndSave(subject)
                streakMap[subject] = 0
            }
            .setNeutralButton("Simulate Risk") { _, _ ->
                showSimulationDialog(subject)
            }

            .show()
    }
    private fun showEditDeleteDialog(subject: String) {

        val options = arrayOf("Edit Subject", "Delete Subject")

        android.app.AlertDialog.Builder(this)
            .setTitle(subject)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(subject)
                    1 -> deleteSubject(subject)
                }
            }
            .show()
    }
    private fun showSimulationDialog(subject: String) {

        val (present, total) = attendanceMap[subject]!!

        val nextAbsent = if (total == 0) 0
        else ((present.toDouble() / (total + 1)) * 100).toInt()

        val threeAbsent = if (total == 0) 0
        else ((present.toDouble() / (total + 3)) * 100).toInt()

        val nextPresent = ((present + 1).toDouble() / (total + 1) * 100).toInt()

        val message = """
        📉 If you miss next class: $nextAbsent%
        📉 If you miss next 3 classes: $threeAbsent%
        
        📈 If you attend next class: $nextPresent%
    """.trimIndent()

        android.app.AlertDialog.Builder(this)
            .setTitle("Simulation - $subject")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }
    private fun deleteSubject(subject: String) {
        val index = subjects.indexOf(subject)
        if (index != -1) {
            subjects.removeAt(index)
            attendanceMap.remove(subject)
            adapter.notifyItemRemoved(index)
            updateOverall()
            saveData()
        }
    }
    private fun showEditDialog(subject: String) {

        val (present, total) = attendanceMap[subject]!!

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 20, 40, 10)

        val nameInput = EditText(this)
        nameInput.setText(subject)

        val presentInput = EditText(this)
        presentInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        presentInput.setText(present.toString())
        presentInput.hint = "Present"

        val totalInput = EditText(this)
        totalInput.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        totalInput.setText(total.toString())
        totalInput.hint = "Total"

        layout.addView(nameInput)
        layout.addView(presentInput)
        layout.addView(totalInput)

        android.app.AlertDialog.Builder(this)
            .setTitle("Edit Subject")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->

                val newName = nameInput.text.toString()
                val newPresent = presentInput.text.toString().toIntOrNull() ?: 0
                val newTotal = totalInput.text.toString().toIntOrNull() ?: 0
                val index = subjects.indexOf(subject)

                if (index != -1) {
                    subjects[index] = newName
                    attendanceMap.remove(subject)
                    attendanceMap[newName] = Pair(newPresent, newTotal)

                    adapter.notifyItemChanged(index)
                    updateOverall()
                    saveData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveData() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        prefs.edit {
            val dataString = attendanceMap.entries.joinToString(";") {
                "${it.key},${it.value.first},${it.value.second}"
            }
            putString("data", dataString)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadData() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val dataString = prefs.getString("data", null) ?: return

        subjects.clear()
        attendanceMap.clear()

        val subjectsData = dataString.split(";")
        for (entry in subjectsData) {
            val parts = entry.split(",")
            if (parts.size == 3) {
                val subject = parts[0]
                val present = parts[1].toInt()
                val total = parts[2].toInt()
                subjects.add(subject)
                attendanceMap[subject] = Pair(present, total)
            }
        }
        updateOverall()
        adapter.notifyDataSetChanged()
    }

    private fun calculateAttendance(p: Int, t: Int): Int {
        return if (t == 0) 0 else (p * 100) / t
    }

    private fun updateAndSave(subject: String) {
        updateOverall()
        saveData()
        val index = subjects.indexOf(subject)
        if (index != -1) {
            adapter.notifyItemChanged(index)
        }
    }

    private fun updateOverall() {
        var totalPresent = 0
        var totalClasses = 0
        attendanceMap.values.forEach {
            totalPresent += it.first
            totalClasses += it.second
        }
        val overall = calculateAttendance(totalPresent, totalClasses)
        overallAttendance.text = getString(R.string.overall_attendance, overall)

        val color = when {
            overall >= 80 -> R.color.colorSafe
            overall >= 75 -> R.color.colorWarning
            else -> R.color.colorDanger
        }
        overallAttendance.setTextColor(ContextCompat.getColor(this, color))
    }
}
