package com.example.studyflow.ui.task

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.studyflow.R
import com.example.studyflow.databinding.ActivityTaskDetailBinding
import com.studyflow.data.StudyFlowDatabase
import com.studyflow.data.Subject
import com.studyflow.data.Task
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TaskDetailActivity : AppCompatActivity() {

    // ViewBinding liên kết layout activity_task_detail.xml
    private lateinit var binding: ActivityTaskDetailBinding

    // Database helper (Singleton)
    private lateinit var dbHelper: StudyFlowDatabase

    // Dữ liệu task và môn học hiện tại
    private var currentTask: Task? = null
    private var currentSubject: Subject? = null

    // taskId nhận từ Intent, lưu lại để onResume có thể refresh dữ liệu
    private var taskId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = StudyFlowDatabase.getInstance(this)

        // Thiết lập Toolbar làm ActionBar
        setupToolbar()

        // Nhận taskId được truyền qua Intent (mặc định -1 nếu không có)
        taskId = intent.getIntExtra("TASK_ID", -1)
        if (taskId == -1) {
            // Không tìm thấy taskId hợp lệ → đóng màn hình
            finish()
            return
        }

        // Thiết lập sự kiện cho nút "Đánh dấu hoàn thành"
        setupMarkDoneButton()
    }

    // ─────────────────────────────────────────────
    // Tự refresh dữ liệu sau khi quay lại từ màn hình Sửa
    // ─────────────────────────────────────────────
    override fun onResume() {
        super.onResume()
        if (taskId != -1) {
            loadAndDisplayTask(taskId)
        }
    }

    // ─────────────────────────────────────────────
    // Thiết lập Toolbar với nút Back
    // ─────────────────────────────────────────────
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        // Bật nút điều hướng quay lại (mũi tên ←)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    // ─────────────────────────────────────────────
    // Load task + subject từ database và render UI
    // ─────────────────────────────────────────────
    private fun loadAndDisplayTask(taskId: Int) {
        // Lấy tất cả tasks rồi lọc theo ID
        val task = dbHelper.getAllTasks().find { it.id == taskId }
        if (task == null) {
            finish()
            return
        }
        currentTask = task

        // Tìm môn học tương ứng với task
        val subject = dbHelper.getAllSubjects().find { it.id == task.subjectId }
        currentSubject = subject

        // ── Tên task làm tiêu đề Toolbar ──
        supportActionBar?.title = task.title
        binding.tvTaskTitle.text = task.title

        // ── Chip môn học ──
        displaySubjectChip(subject)

        // ── TextView deadline ──
        binding.tvDeadline.text = formatDeadlineText(task.deadline)

        // ── Chip độ khó ──
        displayDifficultyChip(task.difficulty)

        // ── ImageView ảnh đính kèm (hiện nếu có imagePath) ──
        displayTaskImage(task.imagePath)

        // ── Ghi chú (hiện nếu có, ẩn nếu null) ──
        displayNote(task.note)

        // ── Trạng thái hoàn thành ──
        updateDoneState(task.isDone)
    }

    // ─────────────────────────────────────────────
    // Hiển thị Chip môn học với màu sắc từ subject.color
    // ─────────────────────────────────────────────
    private fun displaySubjectChip(subject: Subject?) {
        if (subject != null) {
            binding.chipSubject.text = "📖 ${subject.name}"
            // Parse màu HEX từ subject.color, dùng màu mặc định nếu lỗi
            try {
                val bgColor = Color.parseColor(subject.color)
                binding.chipSubject.chipBackgroundColor =
                    android.content.res.ColorStateList.valueOf(bgColor)
            } catch (e: IllegalArgumentException) {
                // Màu không hợp lệ → dùng colorSecondary mặc định
                binding.chipSubject.chipBackgroundColor =
                    android.content.res.ColorStateList.valueOf(
                        getColor(com.google.android.material.R.color.design_default_color_secondary)
                    )
            }
        } else {
            // Không tìm thấy môn học
            binding.chipSubject.text = "📖 Không xác định"
        }
    }

    // ─────────────────────────────────────────────
    // Format deadline: "Thứ X, DD/MM/YYYY — còn N ngày"
    // ─────────────────────────────────────────────
    private fun formatDeadlineText(deadlineStr: String): String {
        return try {
            // Kiểm tra deadline có chứa giờ phút không (dạng "YYYY-MM-DD HH:mm")
            val hasTime = deadlineStr.length >= 16

            if (hasTime) {
                // Parse đầy đủ ngày + giờ
                val deadlineDateTime = java.time.LocalDateTime.parse(
                    deadlineStr.substring(0, 16),
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                )

                // Tính thứ trong tuần
                val dayOfWeek = when (deadlineDateTime.dayOfWeek.value) {
                    1 -> "Thứ Hai"
                    2 -> "Thứ Ba"
                    3 -> "Thứ Tư"
                    4 -> "Thứ Năm"
                    5 -> "Thứ Sáu"
                    6 -> "Thứ Bảy"
                    7 -> "Chủ Nhật"
                    else -> ""
                }

                // Format hiển thị: "Thứ Ba, 30/06/2026 22:00"
                val displayDate = deadlineDateTime.format(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                )
                "$dayOfWeek, $displayDate"

            } else {
                // Task cũ chỉ có ngày → giữ logic cũ, hiện "còn N ngày"
                val cleanDeadline = if (deadlineStr.length >= 10)
                    deadlineStr.substring(0, 10) else deadlineStr
                val deadlineDate = LocalDate.parse(cleanDeadline, DateTimeFormatter.ISO_LOCAL_DATE)
                val today = LocalDate.now()

                val displayDate = deadlineDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

                val dayOfWeek = when (deadlineDate.dayOfWeek.value) {
                    1 -> "Thứ Hai"
                    2 -> "Thứ Ba"
                    3 -> "Thứ Tư"
                    4 -> "Thứ Năm"
                    5 -> "Thứ Sáu"
                    6 -> "Thứ Bảy"
                    7 -> "Chủ Nhật"
                    else -> ""
                }

                val daysLeft = ChronoUnit.DAYS.between(today, deadlineDate)
                val daysText = when {
                    daysLeft < 0   -> "quá hạn ${-daysLeft} ngày"
                    daysLeft == 0L -> "hôm nay!"
                    daysLeft == 1L -> "còn 1 ngày"
                    else           -> "còn $daysLeft ngày"
                }
                "$dayOfWeek, $displayDate — $daysText"
            }
        } catch (e: Exception) {
            // Nếu parse lỗi, trả nguyên chuỗi gốc
            deadlineStr
        }
    }

    // ─────────────────────────────────────────────
    // Hiển thị Chip độ khó theo mức 1/2/3
    // ─────────────────────────────────────────────
    private fun displayDifficultyChip(difficulty: Int) {
        val (label, bgColor) = when (difficulty) {
            1 -> Pair("🟢 Dễ", Color.parseColor("#4CAF50"))
            3 -> Pair("🔴 Khó", Color.parseColor("#F44336"))
            else -> Pair("🟡 Trung bình", Color.parseColor("#FFC107"))
        }
        binding.chipDifficulty.text = label
        binding.chipDifficulty.chipBackgroundColor =
            android.content.res.ColorStateList.valueOf(bgColor)
        // Đặt màu chữ cho dễ đọc trên nền màu
        binding.chipDifficulty.setTextColor(
            if (difficulty == 2) Color.BLACK else Color.WHITE
        )
    }

    // ─────────────────────────────────────────────
    // Hiển thị ảnh đính kèm bằng Glide (ẩn nếu null)
    // ─────────────────────────────────────────────
    private fun displayTaskImage(imagePath: String?) {
        if (!imagePath.isNullOrBlank()) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                // Hiện card ảnh và label
                binding.cardImage.visibility = View.VISIBLE
                binding.tvImageLabel.visibility = View.VISIBLE
                // Dùng Glide load ảnh vào ImageView
                Glide.with(this)
                    .load(imageFile)
                    .fitCenter()
                    .into(binding.ivTaskImage)
            } else {
                // File ảnh không tồn tại → ẩn
                binding.cardImage.visibility = View.GONE
                binding.tvImageLabel.visibility = View.GONE
            }
        } else {
            // Không có đường dẫn ảnh → ẩn
            binding.cardImage.visibility = View.GONE
            binding.tvImageLabel.visibility = View.GONE
        }
    }

    // ─────────────────────────────────────────────
    // Hiển thị ghi chú (ẩn nếu note null hoặc rỗng)
    // ─────────────────────────────────────────────
    private fun displayNote(note: String?) {
        if (!note.isNullOrBlank()) {
            binding.layoutNote.visibility = View.VISIBLE
            binding.tvNote.text = note
        } else {
            binding.layoutNote.visibility = View.GONE
        }
    }

    // ─────────────────────────────────────────────
    // Cập nhật trạng thái UI dựa trên isDone (Toggle):
    // - isDone = false: nút màu primary, text "✅ Đánh dấu hoàn thành"
    // - isDone = true : nút màu secondary, text "↩️ Đánh dấu chưa hoàn thành"
    // ─────────────────────────────────────────────
    private fun updateDoneState(isDone: Boolean) {
        if (isDone) {
            // Task đã hoàn thành: đổi nút sang chế độ "hủy hoàn thành" với màu secondary
            binding.btnMarkDone.text = getString(R.string.btn_mark_undone)
            binding.btnMarkDone.backgroundTintList =
                androidx.core.content.ContextCompat.getColorStateList(this, com.google.android.material.R.color.design_default_color_secondary)
        } else {
            // Task chưa hoàn thành: nút về chế độ "đánh dấu xong" với màu primary
            binding.btnMarkDone.text = getString(R.string.btn_mark_done)
            binding.btnMarkDone.backgroundTintList =
                androidx.core.content.ContextCompat.getColorStateList(this, com.google.android.material.R.color.design_default_color_primary)
        }
    }

    // ─────────────────────────────────────────────
    // Thiết lập sự kiện nút Toggle đánh dấu hoàn thành
    // ─────────────────────────────────────────────
    private fun setupMarkDoneButton() {
        binding.btnMarkDone.setOnClickListener {
            val task = currentTask ?: return@setOnClickListener
            // Gọi DatabaseHelper.toggleTaskDone() để đảo ngược trạng thái trong DB
            val newIsDone = dbHelper.toggleTaskDone(task.id)
            if (newIsDone != -1) {
                // Cập nhật đối tượng task cục bộ và refresh giao diện
                currentTask = task.copy(isDone = newIsDone == 1)
                updateDoneState(isDone = newIsDone == 1)
            }
        }
    }

    // ─────────────────────────────────────────────
    // Inflate menu Share lên Toolbar
    // ─────────────────────────────────────────────
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_task_detail, menu)
        return true
    }

    // ─────────────────────────────────────────────
    // Xử lý sự kiện chọn item menu
    // ─────────────────────────────────────────────
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                openEditTask()
                true
            }
            R.id.action_share -> {
                shareTask()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ─────────────────────────────────────────────
    // Mở màn hình Sửa task, truyền EDIT_TASK_ID cho AddTaskActivity
    // ─────────────────────────────────────────────
    private fun openEditTask() {
        val task = currentTask ?: return
        val intent = Intent(this, AddTaskActivity::class.java).apply {
            putExtra("EDIT_TASK_ID", task.id)
        }
        startActivity(intent)
    }

    // ─────────────────────────────────────────────
    // Chia sẻ thông tin task qua Intent.ACTION_SEND
    // ─────────────────────────────────────────────
    private fun shareTask() {
        val task = currentTask ?: return
        val subjectName = currentSubject?.name ?: "Không xác định"

        // Xác định tên độ khó
        val difficultyText = when (task.difficulty) {
            1 -> "Dễ"
            3 -> "Khó"
            else -> "Trung bình"
        }

        // Xác định trạng thái hoàn thành
        val statusText = if (task.isDone) "Đã xong ✓" else "Chưa xong"

        // Thêm ghi chú vào nội dung chia sẻ nếu có
        val noteText = if (!task.note.isNullOrBlank()) "\nGhi chú: ${task.note}" else ""

        // Format ngày deadline cho dễ đọc
        val deadlineDisplay = try {
            val clean = if (task.deadline.length >= 10) task.deadline.substring(0, 10) else task.deadline
            val date = LocalDate.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE)
            date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        } catch (e: Exception) {
            task.deadline
        }

        // Nội dung chia sẻ rõ ràng, có emoji dễ nhìn
        val shareText = """
            📚 ${task.title}
            Môn: $subjectName
            Deadline: $deadlineDisplay
            Độ khó: $difficultyText
            Trạng thái: $statusText$noteText
        """.trimIndent()

        // Tạo Intent chia sẻ kiểu text/plain
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "📚 Task: ${task.title}")
        }

        // Dùng createChooser để người dùng chọn ứng dụng chia sẻ
        startActivity(Intent.createChooser(shareIntent, "Chia sẻ task qua..."))
    }
}
