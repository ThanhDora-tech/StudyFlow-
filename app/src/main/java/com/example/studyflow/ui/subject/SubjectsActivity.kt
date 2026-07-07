@file:Suppress("SpellCheckingInspection")

package com.example.studyflow.ui.subject

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.studyflow.R
import com.example.studyflow.databinding.ActivitySubjectsBinding
import com.example.studyflow.databinding.DialogAddSubjectBinding
import com.studyflow.data.StudyFlowDatabase
import com.studyflow.data.Subject
import java.time.LocalDate

/**
 * Màn hình danh sách môn học.
 */
class SubjectsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubjectsBinding
    private lateinit var adapter: SubjectAdapter
    private lateinit var dbHelper: StudyFlowDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubjectsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Khởi tạo Database
        dbHelper = StudyFlowDatabase.getInstance(this)

        setupRecyclerView()
        setupListeners()
        loadSubjects()
    }

    private fun setupRecyclerView() {
        adapter = SubjectAdapter(
            onItemClick = { subject -> openSubjectHistory(subject) },
            onEditClick = { subject -> showSubjectDialog(subject) },
            onDeleteClick = { subject -> showDeleteConfirmDialog(subject) },
            getUndoneCount = { subjectId -> dbHelper.getUndoneTaskCountBySubject(subjectId) }
        )
        binding.rvSubjects.layoutManager = LinearLayoutManager(this)
        binding.rvSubjects.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddSubject.setOnClickListener {
            showSubjectDialog()
        }
    }

    private fun openSubjectHistory(subject: Subject) {
        val intent = Intent(this, com.example.studyflow.ui.subject.SubjectHistoryActivity::class.java).apply {
            putExtra("SUBJECT_ID", subject.id)
            putExtra("SUBJECT_NAME", subject.name)
        }
        startActivity(intent)
    }

    private fun loadSubjects() {
        val subjects = dbHelper.getAllSubjects()
        adapter.submitList(subjects)
    }

    private fun showSubjectDialog(existingSubject: Subject? = null) {
        val dialogBinding = DialogAddSubjectBinding.inflate(layoutInflater)

        // Danh sách 6 màu preset: đỏ, cam, xanh lá, xanh dương, tím, hồng
        val colors = listOf("#F44336", "#FF9800", "#4CAF50", "#2196F3", "#9C27B0", "#E91E63")
        var selectedColor = existingSubject?.color ?: colors[0]
        
        val colorButtons = mutableListOf<Button>()

        fun refreshColorSelection() {
            colorButtons.forEachIndexed { index, button ->
                val hex = colors[index]
                button.background = GradientDrawable().apply {
                    setColor(Color.parseColor(hex))
                    cornerRadius = 8f * resources.displayMetrics.density
                    if (hex == selectedColor) {
                        setStroke((3 * resources.displayMetrics.density).toInt(), Color.WHITE)
                    }
                }
            }
        }

        // Thêm các nút màu vào layout
        colors.forEach { colorHex ->
            val colorButton = Button(this).apply {
                val size = (40 * resources.displayMetrics.density).toInt()
                val margin = (4 * resources.displayMetrics.density).toInt()
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, margin, margin, margin)
                }
                setOnClickListener {
                    selectedColor = colorHex
                    refreshColorSelection()
                }
            }
            colorButtons.add(colorButton)
            dialogBinding.layoutColors.addView(colorButton)
        }
        
        refreshColorSelection()

        // Cập nhật giá trị độ quan trọng khi kéo SeekBar
        dialogBinding.sbImportance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Cập nhật giá trị độ quan trọng từ string resource
                dialogBinding.tvImportanceValue.text = getString(R.string.importance_level_format, progress + 1)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        if (existingSubject != null) {
            dialogBinding.etSubjectName.setText(existingSubject.name)
            dialogBinding.sbImportance.progress = existingSubject.importance - 1
        }

        val title = if (existingSubject != null) "Sửa môn học" else "Thêm môn học mới"
        val positiveText = if (existingSubject != null) "Lưu" else "Thêm"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(positiveText) { _, _ ->
                val name = dialogBinding.etSubjectName.text.toString().trim()
                if (name.isNotEmpty()) {
                    val importance = dialogBinding.sbImportance.progress + 1
                    
                    if (existingSubject != null) {
                        val updatedSubject = existingSubject.copy(
                            name = name,
                            importance = importance,
                            color = selectedColor
                        )
                        dbHelper.updateSubject(updatedSubject)
                    } else {
                        val newSubject = Subject(
                            id = 0,
                            name = name,
                            importance = importance,
                            color = selectedColor,
                            createdAt = LocalDate.now().toString()
                        )
                        // Thêm vào DB
                        dbHelper.insertSubject(newSubject)
                    }
                    // Load lại danh sách
                    loadSubjects()
                } else {
                    Toast.makeText(this, "Tên môn học không được để trống", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showDeleteConfirmDialog(subject: Subject) {
        AlertDialog.Builder(this)
            .setTitle("Xóa môn học")
            .setMessage("Bạn có chắc chắn muốn xóa môn '${subject.name}'?\nTất cả các nhiệm vụ liên quan cũng sẽ bị xóa.")
            .setPositiveButton("Xóa") { _, _ ->
                dbHelper.deleteSubject(subject.id)
                loadSubjects()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
}
