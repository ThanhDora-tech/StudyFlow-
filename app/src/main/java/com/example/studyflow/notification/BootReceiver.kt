package com.example.studyflow.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studyflow.data.StudyFlowDatabase

/**
 * BroadcastReceiver lắng nghe sự kiện khởi động lại thiết bị (BOOT_COMPLETED).
 *
 * Tất cả alarm của AlarmManager bị mất khi thiết bị tắt nguồn hoặc khởi động lại.
 * Receiver này chịu trách nhiệm khôi phục toàn bộ alarm sau khi hệ thống boot xong:
 *   1. Alarm nhắc nhở buổi sáng hàng ngày (7h)
 *   2. Alarm tổng kết buổi tối hàng ngày (21h)
 *   3. Alarm deadline cho tất cả task chưa hoàn thành
 *
 * Yêu cầu permission RECEIVE_BOOT_COMPLETED trong AndroidManifest.xml.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Chỉ xử lý đúng action BOOT_COMPLETED, bỏ qua các intent khác
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // ── Bước 1: Khôi phục alarm hàng ngày ───────────────────────────────

        // Đặt lại alarm nhắc nhở kế hoạch buổi sáng theo giờ người dùng đã lưu
        NotificationHelper.scheduleDailyAlarm(context)

        // Đặt lại alarm tổng kết buổi tối lúc 21h
        NotificationHelper.scheduleEveningAlarm(context)

        // ── Bước 2: Khôi phục alarm deadline cho từng task chưa xong ─────────

        val db = StudyFlowDatabase.getInstance(context)

        // Lấy tất cả task chưa hoàn thành từ database
        val undoneTasks = db.getUndoneTasks()

        // Đặt lại alarm deadline (trước 1 ngày và trước 3 ngày) cho mỗi task
        for (task in undoneTasks) {
            // Lấy đúng 10 ký tự của deadline (yyyy-MM-dd) để tránh lỗi timestamp thừa
            val cleanDeadline = if (task.deadline.length >= 10)
                task.deadline.substring(0, 10) else task.deadline

            NotificationHelper.scheduleDeadlineAlarm(
                context = context,
                taskId = task.id,
                deadlineDateStr = cleanDeadline
            )
        }
    }
}
