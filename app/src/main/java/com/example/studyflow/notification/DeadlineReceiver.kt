package com.example.studyflow.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studyflow.data.StudyFlowDatabase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * BroadcastReceiver xử lý alarm nhắc nhở deadline của task.
 *
 * Được kích hoạt bởi AlarmManager thông qua NotificationHelper.scheduleDeadlineAlarm().
 * Receiver đọc taskId từ Intent, query database để lấy thông tin task,
 * tính số ngày còn lại đến deadline rồi gửi thông báo phù hợp.
 */
class DeadlineReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Lấy taskId từ Intent (mặc định -1 nếu không tìm thấy)
        val taskId = intent.getIntExtra("TASK_ID", -1)
        if (taskId == -1) return // Không có taskId hợp lệ → bỏ qua

        // Lấy số ngày trước deadline mà alarm được đặt (1 hoặc 3)
        val daysLeft = intent.getIntExtra("DAYS_LEFT", 1)

        val alertType = intent.getStringExtra("ALERT_TYPE")

        // Mở kết nối database và tìm task theo ID
        val db = StudyFlowDatabase.getInstance(context)
        val task = db.getAllTasks().find { it.id == taskId } ?: return
        // Task không tồn tại hoặc đã bị xóa → bỏ qua

        // Không gửi thông báo nếu task đã hoàn thành
        if (task.isDone) return

        if (alertType == "5HOUR") {
            try {
                val deadlineDateTime = if (task.deadline.length >= 16) {
                    LocalDateTime.parse(
                        task.deadline.substring(0, 16),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    )
                } else {
                    val cleanDeadline = if (task.deadline.length >= 10) task.deadline.substring(0, 10) else task.deadline
                    LocalDate.parse(cleanDeadline, DateTimeFormatter.ISO_LOCAL_DATE).atTime(23, 59)
                }

                if (LocalDateTime.now().isAfter(deadlineDateTime)) {
                    return
                }

                NotificationHelper.sendFiveHourDeadlineNotification(context, task.title)
            } catch (e: Exception) {
                // Lỗi parse thì bỏ qua
            }
            return
        }

        // Tính lại số ngày thực tế còn lại so với ngày hôm nay
        // (đề phòng alarm kích hoạt muộn do Doze Mode hoặc reboot)
        val today = LocalDate.now()
        val cleanDeadline = if (task.deadline.length >= 10)
            task.deadline.substring(0, 10) else task.deadline
        val actualDaysLeft: Long = try {
            val deadlineDate = LocalDate.parse(cleanDeadline, DateTimeFormatter.ISO_LOCAL_DATE)
            ChronoUnit.DAYS.between(today, deadlineDate)
        } catch (e: Exception) {
            // Không parse được ngày → dùng giá trị từ Intent
            daysLeft.toLong()
        }

        // Nếu deadline đã qua thì không cần gửi thêm thông báo
        if (actualDaysLeft < 0) return

        // Gửi thông báo deadline với số ngày còn lại thực tế
        // (ưu tiên giá trị thực tế, fallback về giá trị từ Intent nếu lỗi parse)
        NotificationHelper.sendDeadlineNotification(
            context = context,
            taskTitle = task.title,
            daysLeft = actualDaysLeft.toInt().coerceAtLeast(1)
        )
    }
}
