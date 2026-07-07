package com.example.studyflow.ui.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.studyflow.databinding.ItemTaskBinding
import com.studyflow.data.Subject
import com.studyflow.data.Task
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Adapter cho RecyclerView hiển thị danh sách nhiệm vụ (Task) ở màn hình chính.
 *
 * @param subjectMap Bản đồ ánh xạ subjectId → Subject để tra tên và màu môn học.
 * @param onTaskClick Callback khi người dùng nhấn vào một task.
 * @param onTaskDone  Callback khi người dùng tick checkbox hoàn thành.
 */
class TaskAdapter(
    private val subjectMap: Map<Int, Subject>,
    private val onTaskClick: (Task) -> Unit,
    private val onTaskDone: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    // Danh sách task hiển thị hiện tại
    private val taskList = mutableListOf<Task>()

    /**
     * Cập nhật toàn bộ danh sách task và làm mới RecyclerView.
     * @param newTasks Danh sách task mới.
     */
    fun submitList(newTasks: List<Task>) {
        taskList.clear()
        taskList.addAll(newTasks)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = taskList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        // Inflate layout item_task.xml qua ViewBinding
        val binding = ItemTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(taskList[position])
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    inner class TaskViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Gán dữ liệu từ Task vào các View trong item_task.xml.
         */
        fun bind(task: Task) {
            // Gán tên task
            binding.tvTaskTitle.text = task.title

            // Thiết lập Chip môn học: tên và màu nền
            val subject = subjectMap[task.subjectId]
            if (subject != null) {
                binding.chipSubjectName.text = subject.name
                // Đổi màu nền chip theo màu môn học (có xử lý lỗi parse)
                try {
                    val chipColor = Color.parseColor(subject.color)
                    binding.chipSubjectName.chipBackgroundColor =
                        android.content.res.ColorStateList.valueOf(chipColor)
                } catch (e: IllegalArgumentException) {
                    // Nếu màu không hợp lệ, giữ màu mặc định
                }
            } else {
                binding.chipSubjectName.text = "Không rõ"
            }

            // Tính khoảng cách thời gian đến deadline
            val now = LocalDateTime.now()
            
            // BƯỚC 1: Parse deadline thành LocalDateTime
            val deadlineDateTime = try {
                if (task.deadline.length >= 16) {
                    LocalDateTime.parse(task.deadline.substring(0, 16), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                } else {
                    val cleanDeadline = if (task.deadline.length >= 10) task.deadline.substring(0, 10) else task.deadline
                    LocalDate.parse(cleanDeadline, DateTimeFormatter.ISO_LOCAL_DATE).atTime(23, 59)
                }
            } catch (e: Exception) {
                LocalDateTime.now() // Mặc định về hiện tại nếu parse lỗi
            }

            // BƯỚC 2: Tính số phút
            val duration = Duration.between(now, deadlineDateTime)
            val totalMinutes = duration.toMinutes()

            // BƯỚC 3: Format hiển thị cho tvDeadlineLabel
            if (totalMinutes > 0) {
                // Chưa đến hạn
                val days = totalMinutes / (60 * 24)
                val hours = (totalMinutes % (60 * 24)) / 60
                val minutes = totalMinutes % 60

                binding.tvDeadlineLabel.text = when {
                    days > 0 && hours > 0 -> "còn $days ngày $hours giờ $minutes phút"
                    days > 0 && hours == 0L -> "còn $days ngày $minutes phút"
                    days == 0L && hours > 0 -> "còn $hours giờ $minutes phút"
                    else -> "còn $minutes phút"
                }
            } else {
                // Quá hạn
                val absTotalMinutes = -totalMinutes
                val days = absTotalMinutes / (60 * 24)
                val hours = (absTotalMinutes % (60 * 24)) / 60
                val minutes = absTotalMinutes % 60

                binding.tvDeadlineLabel.text = when {
                    days > 0 && hours > 0 -> "trễ $days ngày $hours giờ $minutes phút"
                    days > 0 && hours == 0L -> "trễ $days ngày $minutes phút"
                    days == 0L && hours > 0 -> "trễ $hours giờ $minutes phút"
                    else -> "trễ $minutes phút"
                }
            }

            // BƯỚC 4: Đổi màu sắc tvDeadlineLabel và viewUrgencyBorder
            val colorHex = when {
                totalMinutes <= 0 -> "#F44336" // màu đỏ (quá hạn)
                totalMinutes <= 1440 -> "#FF9800" // màu cam (còn trong vòng 24 giờ)
                else -> "#388E3C" // màu xanh (còn hơn 24 giờ)
            }
            
            binding.tvDeadlineLabel.setTextColor(Color.parseColor(colorHex))
            binding.viewUrgencyBorder.setBackgroundColor(Color.parseColor(colorHex))

            // Thiết lập icon độ khó
            binding.tvDifficultyIcon.text = when (task.difficulty) {
                1 -> "🟢" // Dễ
                2 -> "🟡" // Trung bình
                3 -> "🔴" // Khó
                else -> "🟡"
            }

            // Đặt trạng thái checkbox (không kích hoạt listener trong lúc bind)
            binding.checkboxDone.setOnCheckedChangeListener(null)
            binding.checkboxDone.isChecked = task.isDone

            // Lắng nghe sự kiện tick checkbox
            binding.checkboxDone.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    onTaskDone(task)
                }
            }

            // Lắng nghe sự kiện click vào toàn bộ item để mở chi tiết
            binding.root.setOnClickListener {
                onTaskClick(task)
            }
        }
    }
}
