package com.example.studyflow.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studyflow.data.StudyFlowDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * BroadcastReceiver gửi thông báo tổng kết ngày học mỗi buổi tối (21h).
 *
 * Được kích hoạt bởi AlarmManager thông qua NotificationHelper.scheduleEveningAlarm().
 * Receiver đếm tổng số task có deadline hôm nay (hoặc trước hôm nay)
 * và số task trong đó đã được đánh dấu hoàn thành, sau đó gửi thông báo tổng kết.
 */
class EveningReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val db = StudyFlowDatabase.getInstance(context)
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        // Lấy toàn bộ task (bao gồm cả đã done và chưa done)
        val allTasks = db.getAllTasks()

        // Lọc ra các task thuộc về hôm nay:
        // deadline là hôm nay hoặc đã quá hạn (daysLeft <= 0)
        val todayTasks = allTasks.filter { task ->
            val cleanDeadline = if (task.deadline.length >= 10)
                task.deadline.substring(0, 10) else task.deadline
            try {
                val deadlineDate = LocalDate.parse(cleanDeadline, formatter)
                // Tính số ngày từ deadline đến hôm nay (âm = deadline đã qua)
                val daysDiff = ChronoUnit.DAYS.between(deadlineDate, today)
                daysDiff >= 0L // deadline hôm nay hoặc trước hôm nay
            } catch (e: Exception) {
                false // Bỏ qua task có deadline không hợp lệ
            }
        }

        val totalCount = todayTasks.size

        if (totalCount == 0) {
            // Kể cả không có task vẫn phải re-schedule cho ngày mai
            NotificationHelper.scheduleEveningAlarm(context)
            return
        }

        // Đếm số task đã hoàn thành trong danh sách hôm nay
        val doneCount = todayTasks.count { it.isDone }

        // Gửi thông báo tổng kết buổi tối
        NotificationHelper.sendEveningSummary(
            context = context,
            doneCount = doneCount,
            totalCount = totalCount
        )
        NotificationHelper.scheduleEveningAlarm(context)
    }
}
