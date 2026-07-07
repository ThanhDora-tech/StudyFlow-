package com.example.studyflow.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studyflow.data.StudyFlowDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * BroadcastReceiver gửi thông báo tóm tắt kế hoạch học tập mỗi buổi sáng (7h).
 *
 * Được kích hoạt bởi AlarmManager thông qua NotificationHelper.scheduleDailyAlarm().
 * Receiver lấy danh sách task chưa hoàn thành có deadline hôm nay hoặc ngày mai,
 * đếm số lượng và lấy task ưu tiên cao nhất để hiển thị trong thông báo.
 */
class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val db = StudyFlowDatabase.getInstance(context)
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        // Lấy toàn bộ task chưa hoàn thành, đã sắp xếp theo điểm ưu tiên giảm dần
        val allPrioritized = db.getTasksSortedByPriority()

        // Lọc ra các task có deadline hôm nay hoặc ngày mai (daysLeft <= 1)
        val todayTasks = allPrioritized.filter { task ->
            val cleanDeadline = if (task.deadline.length >= 10)
                task.deadline.substring(0, 10) else task.deadline
            try {
                val deadlineDate = LocalDate.parse(cleanDeadline, formatter)
                val daysLeft = ChronoUnit.DAYS.between(today, deadlineDate)
                daysLeft <= 1L // Bao gồm cả task quá hạn và deadline ngày mai
            } catch (e: Exception) {
                false // Bỏ qua task có deadline không hợp lệ
            }
        }

        // Luôn gửi thông báo buổi sáng: có task thì nhắc task, không có thì gửi lời khích lệ
        val topTaskTitle = if (todayTasks.isNotEmpty()) todayTasks.first().title else ""
        NotificationHelper.sendDailySummary(
            context = context,
            taskCount = todayTasks.size,
            topTaskTitle = topTaskTitle
        )
        NotificationHelper.scheduleDailyAlarm(context)
    }
}
