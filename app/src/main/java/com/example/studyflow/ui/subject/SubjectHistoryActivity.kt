package com.example.studyflow.ui.subject

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studyflow.databinding.ActivitySubjectHistoryBinding
import com.example.studyflow.ui.home.TaskAdapter
import com.example.studyflow.ui.task.TaskDetailActivity
import com.google.android.material.snackbar.Snackbar
import com.studyflow.data.StudyFlowDatabase
import com.studyflow.data.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SubjectHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubjectHistoryBinding
    private lateinit var dbHelper: StudyFlowDatabase
    private lateinit var taskAdapter: TaskAdapter
    private var subjectId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubjectHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = StudyFlowDatabase.getInstance(this)

        subjectId = intent.getIntExtra("SUBJECT_ID", -1)
        val subjectName = intent.getStringExtra("SUBJECT_NAME") ?: "Môn học"

        if (subjectId == -1) {
            finish()
            return
        }

        setupToolbar(subjectName)
        setupRecyclerView()
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayTasks()
    }

    private fun setupToolbar(title: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            subjectMap = emptyMap(),
            onTaskClick = { task -> openTaskDetail(task.id) },
            onTaskDone = { task -> markDoneAndRefresh(task) }
        )
        binding.rvSubjectTasks.layoutManager = LinearLayoutManager(this)
        binding.rvSubjectTasks.adapter = taskAdapter
    }

    private fun loadAndDisplayTasks() {
        val subjectMap = dbHelper.getAllSubjects().associateBy { it.id }
        
        taskAdapter = TaskAdapter(
            subjectMap = subjectMap,
            onTaskClick = { task -> openTaskDetail(task.id) },
            onTaskDone = { task -> markDoneAndRefresh(task) }
        )
        binding.rvSubjectTasks.adapter = taskAdapter

        val tasks = dbHelper.getTasksBySubjectId(subjectId)
        val sortedTasks = tasks.sortedWith(compareBy<Task> { it.isDone }.thenByDescending { it.id })
        
        taskAdapter.submitList(sortedTasks)

        if (sortedTasks.isEmpty()) {
            binding.tvEmptyHistory.visibility = View.VISIBLE
            binding.rvSubjectTasks.visibility = View.GONE
        } else {
            binding.tvEmptyHistory.visibility = View.GONE
            binding.rvSubjectTasks.visibility = View.VISIBLE
        }
    }


    private fun markDoneAndRefresh(task: Task) {
        dbHelper.toggleTaskDone(task.id)
        loadAndDisplayTasks()
        Snackbar.make(
            binding.coordinatorLayout,
            "Hoàn thành! \uD83C\uDF89 \"${task.title}\"",
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun openTaskDetail(taskId: Int) {
        val intent = Intent(this, TaskDetailActivity::class.java).apply {
            putExtra("TASK_ID", taskId)
        }
        startActivity(intent)
    }
}
