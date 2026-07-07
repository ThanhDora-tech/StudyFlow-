package com.example.studyflow

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.studyflow.databinding.ActivityMainBinding
import com.example.studyflow.ui.home.TaskAdapter
import com.example.studyflow.ui.subject.SubjectsActivity
import com.example.studyflow.ui.task.AddTaskActivity
import com.example.studyflow.ui.task.TaskDetailActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.studyflow.data.StudyFlowDatabase
import com.studyflow.data.Subject
import com.studyflow.data.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.example.studyflow.notification.NotificationHelper
import java.time.format.TextStyle
import java.util.Locale
import com.example.studyflow.ui.settings.SettingsActivity

/**
 * Màn hình chính (Trang chủ) của ứng dụng StudyFlow.
 * Hiển thị danh sách task cần làm hôm nay và sắp tới, được sắp xếp theo điểm ưu tiên.
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding liên kết với activity_main.xml
    private lateinit var binding: ActivityMainBinding

    // Instance của database (Singleton)
    private lateinit var db: StudyFlowDatabase

    // Adapter cho 2 danh sách task
    private lateinit var todayAdapter: TaskAdapter
    private lateinit var upcomingAdapter: TaskAdapter

    // Bản đồ subjectId → Subject để tra cứu nhanh môn học
    private var subjectMap: Map<Int, Subject> = emptyMap()

    // ─── Biến trạng thái bộ lọc ─────────────────────────────────────────────

    // ID môn học đang lọc (null = không lọc)
    private var selectedSubjectFilter: Int? = null

    // Mức độ khó đang lọc: 1=Dễ, 2=TB, 3=Khó (null = không lọc)
    private var selectedDifficultyFilter: Int? = null

    // Bật/tắt filter chỉ hiển thị task quá hạn
    private var isOverdueFilterActive: Boolean = false

    // ─── Vòng đời Activity ───────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Khởi tạo ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Lấy instance database
        db = StudyFlowDatabase.getInstance(this)

        // Tạo notification channel TRƯỚC KHI schedule bất kỳ alarm nào
        // Nếu channel chưa tồn tại, mọi thông báo sẽ bị drop silently
        NotificationHelper.createNotificationChannel(this)

        // Thiết lập giao diện (chỉ chạy 1 lần)
        setupGreetingHeader()
        setupRecyclerViews()
        setupFab()
        setupBottomNavigation()

        NotificationHelper.scheduleDailyAlarm(this)
        NotificationHelper.scheduleEveningAlarm(this)

        // Thiết lập các chip lọc task
        setupFilterChips()
    }

    /**
     * Dữ liệu được tải lại ở onResume để đảm bảo luôn mới nhất
     * sau khi quay lại từ AddTaskActivity hoặc TaskDetailActivity.
     */
    override fun onResume() {
        super.onResume()
        // Đảm bảo tab "Hôm nay" luôn được chọn khi quay lại
        binding.bottomNavigation.selectedItemId = R.id.nav_today
        // Tải lại dữ liệu mỗi khi màn hình hiển thị
        loadAndDisplayTasks()
    }

    // ─── Thiết lập giao diện ─────────────────────────────────────────────────

    /**
     * Thiết lập lời chào theo buổi trong ngày và hiển thị ngày tháng hiện tại.
     */
    private fun setupGreetingHeader() {
        // Xác định lời chào theo giờ hiện tại
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            currentHour < 12 -> "Chào buổi sáng! 🌟"
            currentHour < 18 -> "Chào buổi chiều! ☀️"
            else             -> "Chào buổi tối! 🌙"
        }
        binding.tvGreeting.text = greeting

        // Định dạng ngày hôm nay theo tiếng Việt
        val today = LocalDate.now()
        val dayOfWeek = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("vi"))
        val formattedDate = today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("vi")))
        binding.tvTodayDate.text = "${dayOfWeek.replaceFirstChar { it.uppercase() }}, $formattedDate"
    }

    /**
     * Thiết lập 2 RecyclerView và gắn ItemTouchHelper để hỗ trợ swipe phải → hoàn thành.
     */
    private fun setupRecyclerViews() {
        // Khởi tạo adapter cho todayList (placeholder map trống, sẽ cập nhật khi load data)
        todayAdapter = TaskAdapter(
            subjectMap = subjectMap,
            onTaskClick = { task -> openTaskDetail(task.id) },
            onTaskDone  = { task -> markDoneAndRefresh(task) }
        )

        // Khởi tạo adapter cho upcomingList
        upcomingAdapter = TaskAdapter(
            subjectMap = subjectMap,
            onTaskClick = { task -> openTaskDetail(task.id) },
            onTaskDone  = { task -> markDoneAndRefresh(task) }
        )

        // Gắn adapter và LayoutManager vào RecyclerView "Hôm nay"
        binding.rvTodayTasks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = todayAdapter
        }

        // Gắn adapter và LayoutManager vào RecyclerView "Sắp tới"
        binding.rvUpcomingTasks.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = upcomingAdapter
        }
    }

    /**
     * Thiết lập FloatingActionButton để mở màn hình thêm task mới.
     */
    private fun setupFab() {
        binding.fabAddTask.setOnClickListener {
            startActivity(Intent(this, AddTaskActivity::class.java))
        }
    }

    /**
     * Thiết lập BottomNavigationView với 3 tab điều hướng.
     */
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_today -> {
                    // Đang ở trang chủ, cuộn lên đầu
                    binding.nestedScrollView.smoothScrollTo(0, 0)
                    true
                }
                R.id.nav_subjects -> {
                    // Chuyển sang màn hình Môn học
                    startActivity(Intent(this, SubjectsActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    // ─── Bộ lọc Filter Chips ─────────────────────────────────────────────────

    /**
     * Gắn click listener cho 4 chip bộ lọc.
     * Gọi 1 lần trong onCreate().
     */
    private fun setupFilterChips() {
        // Chip "Tất cả": reset toàn bộ filter về mặc định
        binding.chipFilterAll.setOnClickListener {
            selectedSubjectFilter = null
            selectedDifficultyFilter = null
            isOverdueFilterActive = false
            loadAndDisplayTasks()
        }

        // Chip "Môn học": mở BottomSheet chọn môn
        binding.chipFilterSubject.setOnClickListener {
            showSubjectFilterBottomSheet()
        }

        // Chip "Độ khó": mở BottomSheet chọn mức độ
        binding.chipFilterDifficulty.setOnClickListener {
            showDifficultyFilterBottomSheet()
        }

        // Chip "Quá hạn": toggle bật/tắt filter quá hạn
        binding.chipFilterOverdue.setOnClickListener {
            isOverdueFilterActive = !isOverdueFilterActive
            loadAndDisplayTasks()
        }
    }

    /**
     * Hiển thị BottomSheetDialog để người dùng chọn lọc theo môn học.
     * Dòng đầu tiên là "Tất cả môn học" để xóa filter môn.
     */
    private fun showSubjectFilterBottomSheet() {
        val dialog = BottomSheetDialog(this)

        // Container dọc chứa các dòng lựa chọn
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 32)
        }

        // Hàm tiện ích tạo 1 dòng lựa chọn với chấm màu và tên môn
        fun addRow(subjectId: Int?, subjectName: String, colorHex: String?) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(48, 28, 48, 28)
                // Đánh dấu dòng đang được chọn bằng màu nền nhạt
                if (subjectId == selectedSubjectFilter) {
                    setBackgroundColor(0x1A6750A4) // colorPrimary với alpha thấp
                }
                setOnClickListener {
                    selectedSubjectFilter = subjectId
                    dialog.dismiss()
                    loadAndDisplayTasks()
                }
            }

            // Chấm màu tròn nhỏ đại diện cho môn học
            val dot = View(this).apply {
                val size = (12 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (12 * resources.displayMetrics.density).toInt()
                    topMargin = (2 * resources.displayMetrics.density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        if (colorHex != null) {
                            try { Color.parseColor(colorHex) } catch (e: Exception) { Color.GRAY }
                        } else {
                            ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray)
                        }
                    )
                }
            }

            // Nhãn tên môn học
            val label = TextView(this).apply {
                text = subjectName
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            }

            row.addView(dot)
            row.addView(label)
            container.addView(row)
        }

        // Dòng đầu tiên: xóa filter môn (chấm xám, null)
        addRow(subjectId = null, subjectName = "Tất cả môn học", colorHex = null)

        // Thêm từng môn học từ database
        val subjects = db.getAllSubjects()
        for (subject in subjects) {
            addRow(subjectId = subject.id, subjectName = subject.name, colorHex = subject.color)
        }

        dialog.setContentView(container)
        dialog.setOnDismissListener { updateFilterChipsUI() }
        dialog.show()
    }

    /**
     * Hiển thị BottomSheetDialog để người dùng chọn lọc theo độ khó.
     * 4 dòng cố định: Tất cả / Dễ / Trung bình / Khó.
     */
    private fun showDifficultyFilterBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 32)
        }

        // Danh sách cố định: (giá trị difficulty, nhãn hiển thị)
        val options = listOf(
            null  to "Tất cả mức độ",
            1     to "🟢 Dễ",
            2     to "🟡 Trung bình",
            3     to "🔴 Khó"
        )

        for ((value, label) in options) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(48, 28, 48, 28)
                // Đánh dấu dòng đang được chọn bằng màu nền nhạt
                if (value == selectedDifficultyFilter) {
                    setBackgroundColor(0x1A6750A4)
                }
                setOnClickListener {
                    selectedDifficultyFilter = value
                    dialog.dismiss()
                    loadAndDisplayTasks()
                }
            }

            val labelView = TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            }

            row.addView(labelView)
            container.addView(row)
        }

        dialog.setContentView(container)
        dialog.setOnDismissListener { updateFilterChipsUI() }
        dialog.show()
    }

    /**
     * Đồng bộ trạng thái isChecked và text của 4 chip theo biến state hiện tại.
     * Gọi bên trong loadAndDisplayTasks() để luôn nhất quán với dữ liệu.
     */
    private fun updateFilterChipsUI() {
        val hasFilter = selectedSubjectFilter != null || selectedDifficultyFilter != null || isOverdueFilterActive

        // Chip "Tất cả": chỉ checked khi không có filter nào đang bật
        binding.chipFilterAll.isChecked = !hasFilter

        // Chip môn học: đổi text thành tên môn nếu đang lọc
        if (selectedSubjectFilter != null) {
            val subjectName = subjectMap[selectedSubjectFilter]?.name ?: "Môn học"
            binding.chipFilterSubject.text = "$subjectName ▼"
            binding.chipFilterSubject.isChecked = true
        } else {
            binding.chipFilterSubject.text = "Môn học ▼"
            binding.chipFilterSubject.isChecked = false
        }

        // Chip độ khó: đổi text thành nhãn mức độ nếu đang lọc
        if (selectedDifficultyFilter != null) {
            val diffLabel = when (selectedDifficultyFilter) {
                1 -> "Dễ"
                2 -> "Trung bình"
                3 -> "Khó"
                else -> "Độ khó"
            }
            binding.chipFilterDifficulty.text = "$diffLabel ▼"
            binding.chipFilterDifficulty.isChecked = true
        } else {
            binding.chipFilterDifficulty.text = "Độ khó ▼"
            binding.chipFilterDifficulty.isChecked = false
        }

        // Chip quá hạn: phản ánh đúng trạng thái toggle
        binding.chipFilterOverdue.isChecked = isOverdueFilterActive
    }

    // ─── Xử lý dữ liệu ───────────────────────────────────────────────────────

    /**
     * Tải dữ liệu từ database, phân loại task và hiển thị lên 2 RecyclerView.
     * Gọi trong onResume() để luôn có dữ liệu mới nhất.
     */
    private fun loadAndDisplayTasks() {
        // Tải danh sách môn học và tạo Map để tra cứu nhanh
        val subjects = db.getAllSubjects()
        subjectMap = subjects.associateBy { it.id }

        // Cập nhật subjectMap trong cả 2 adapter
        // (tạo lại adapter với map mới để đảm bảo Chip màu đúng)
        todayAdapter = TaskAdapter(
            subjectMap = subjectMap,
            onTaskClick = { task -> openTaskDetail(task.id) },
            onTaskDone  = { task -> markDoneAndRefresh(task) }
        )
        upcomingAdapter = TaskAdapter(
            subjectMap = subjectMap,
            onTaskClick = { task -> openTaskDetail(task.id) },
            onTaskDone  = { task -> markDoneAndRefresh(task) }
        )
        binding.rvTodayTasks.adapter = todayAdapter
        binding.rvUpcomingTasks.adapter = upcomingAdapter

        // Lấy toàn bộ task chưa hoàn thành đã sắp xếp theo điểm ưu tiên
        val allTasks = db.getTasksSortedByPriority()

        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        // Xác định xem có filter nào đang bật không
        val hasActiveFilter = selectedSubjectFilter != null || selectedDifficultyFilter != null || isOverdueFilterActive

        // Áp dụng bộ lọc kết hợp trước khi phân loại vào 2 section
        val filteredTasks = allTasks.filter { task ->
            // Lọc theo môn học nếu đang bật
            val subjectMatch = selectedSubjectFilter == null || task.subjectId == selectedSubjectFilter
            // Lọc theo độ khó nếu đang bật
            val difficultyMatch = selectedDifficultyFilter == null || task.difficulty == selectedDifficultyFilter
            // Lọc theo quá hạn nếu đang bật
            val overdueMatch = if (isOverdueFilterActive) {
                val cleanDeadline = if (task.deadline.length >= 10) task.deadline.substring(0, 10) else task.deadline
                val deadlineDate = try {
                    LocalDate.parse(cleanDeadline, formatter)
                } catch (e: Exception) { today }
                java.time.temporal.ChronoUnit.DAYS.between(today, deadlineDate) < 0
            } else true

            subjectMatch && difficultyMatch && overdueMatch
        }

        // Phân loại task vào 2 section sau khi đã lọc
        val todayList = mutableListOf<Task>()
        val upcomingList = mutableListOf<Task>()

        for (task in filteredTasks) {
            val cleanDeadline = if (task.deadline.length >= 10) task.deadline.substring(0, 10) else task.deadline
            val deadlineDate = try {
                LocalDate.parse(cleanDeadline, formatter)
            } catch (e: Exception) {
                today
            }

            val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, deadlineDate)

            when {
                // Quá hạn, deadline hôm nay hoặc ngày mai
                daysLeft <= 1L -> todayList.add(task)
                // Khi có filter: hiển thị tất cả task sắp tới (không giới hạn 7 ngày)
                // Khi không có filter: chỉ hiển thị trong vòng 7 ngày
                hasActiveFilter && daysLeft >= 2 -> upcomingList.add(task)
                !hasActiveFilter && daysLeft in 2..7 -> upcomingList.add(task)
            }
        }

        // Đồng bộ trạng thái chip theo filter hiện tại
        updateFilterChipsUI()

        // Text empty state thay đổi tùy theo có filter hay không
        val emptyTodayText = if (hasActiveFilter)
            "Không có task phù hợp với bộ lọc "
        else
            "Bạn đã hoàn thành mọi việc hôm nay!"

        val emptyUpcomingText = if (hasActiveFilter)
            "Không có task phù hợp với bộ lọc "
        else
            "Không có task nào trong 7 ngày tới "

        // Hiển thị danh sách "Hôm nay"
        todayAdapter.submitList(todayList)
        binding.tvEmptyToday.text = emptyTodayText
        binding.tvEmptyToday.visibility = if (todayList.isEmpty()) View.VISIBLE else View.GONE
        binding.rvTodayTasks.visibility = if (todayList.isEmpty()) View.GONE else View.VISIBLE

        // Hiển thị danh sách "Sắp tới"
        upcomingAdapter.submitList(upcomingList)
        binding.tvEmptyUpcoming.text = emptyUpcomingText
        binding.tvEmptyUpcoming.visibility = if (upcomingList.isEmpty()) View.VISIBLE else View.GONE
        binding.rvUpcomingTasks.visibility = if (upcomingList.isEmpty()) View.GONE else View.VISIBLE
    }

    // ─── Hành động người dùng ────────────────────────────────────────────────

    /**
     * Đánh dấu task là hoàn thành, làm mới danh sách và hiển thị Snackbar.
     * @param task Task cần đánh dấu hoàn thành.
     */
    private fun markDoneAndRefresh(task: Task) {
        // Cập nhật trạng thái hoàn thành vào database
        db.toggleTaskDone(task.id)

        // Làm mới danh sách hiển thị
        loadAndDisplayTasks()

        // Hiển thị Snackbar thông báo thành công
        Snackbar.make(
            binding.coordinatorLayout,
            "Hoàn thành!  \"${task.title}\"",
            Snackbar.LENGTH_LONG
        ).show()
    }

    /**
     * Mở màn hình chi tiết của một task.
     * @param taskId ID của task cần xem chi tiết.
     */
    private fun openTaskDetail(taskId: Int) {
        val intent = Intent(this, TaskDetailActivity::class.java).apply {
            putExtra("TASK_ID", taskId)
        }
        startActivity(intent)
    }
}