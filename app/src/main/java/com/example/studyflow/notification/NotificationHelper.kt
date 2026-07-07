package com.example.studyflow.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.studyflow.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Helper object tập trung toàn bộ logic liên quan đến thông báo (Notification)
 * và lên lịch báo thức (AlarmManager) cho ứng dụng StudyFlow.
 *
 * Hỗ trợ Android 13+ với quyền POST_NOTIFICATIONS.
 * Tất cả PendingIntent dùng FLAG_IMMUTABLE theo yêu cầu Android 12+.
 */
object NotificationHelper {

    // ─── Hằng số kênh thông báo ──────────────────────────────────────────────

    /** ID kênh thông báo dùng xuyên suốt app */
    const val CHANNEL_ID = "studyflow_channel"

    /** Tên hiển thị của kênh thông báo */
    private const val CHANNEL_NAME = "StudyFlow Nhắc nhở"

    // ─── Hằng số ID yêu cầu AlarmManager ────────────────────────────────────

    /** Request code cơ sở cho alarm deadline trước 1 ngày (cộng thêm taskId) */
    private const val REQUEST_DEADLINE_1DAY = 10_000

    /** Request code cơ sở cho alarm deadline trước 3 ngày (cộng thêm taskId) */
    private const val REQUEST_DEADLINE_3DAY = 20_000

    /** Request code cơ sở cho alarm deadline trước 5 giờ (cộng thêm taskId) */
    private const val REQUEST_DEADLINE_5HOUR = 40_000

    /** Request code cho alarm nhắc nhở buổi sáng hàng ngày */
    private const val REQUEST_DAILY_MORNING = 30_000

    /** Request code cho alarm tổng kết buổi tối hàng ngày */
    private const val REQUEST_EVENING = 30_001

    // ─── Tạo Notification Channel ────────────────────────────────────────────

    /**
     * Tạo Notification Channel "studyflow_channel" với mức độ ưu tiên cao.
     * Phải được gọi trước khi gửi bất kỳ thông báo nào (thường trong Application.onCreate
     * hoặc Activity.onCreate).
     *
     * @param context Context của ứng dụng.
     */
    fun createNotificationChannel(context: Context) {
        // NotificationChannel chỉ cần thiết từ Android 8.0 (API 26) trở lên
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH // Hiển thị heads-up notification
        ).apply {
            description = "Kênh thông báo nhắc nhở deadline và kế hoạch học tập"
        }

        // Đăng ký kênh với hệ thống Android
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    // ─── Gửi thông báo deadline ───────────────────────────────────────────────

    /**
     * Gửi thông báo cảnh báo khi deadline của một task sắp đến.
     * Nội dung thông báo thay đổi tùy theo số ngày còn lại.
     *
     * @param context    Context hiện tại.
     * @param taskTitle  Tiêu đề của task cần nhắc.
     * @param daysLeft   Số ngày còn lại đến deadline (1 hoặc 3).
     */
    fun sendDeadlineNotification(context: Context, taskTitle: String, daysLeft: Int) {
        // Chọn nội dung thông báo phù hợp theo số ngày còn lại
        val body = when (daysLeft) {
            1    -> "🚨 Ngày mai deadline: $taskTitle — Hãy làm ngay!"
            3    -> "📅 Còn 3 ngày: $taskTitle"
            else -> "⏰ Còn $daysLeft ngày: $taskTitle"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ Deadline sắp đến!")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // Hiển thị đầy đủ text khi mở rộng
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Tự xóa sau khi người dùng nhấn vào
            .build()

        // Kiểm tra quyền POST_NOTIFICATIONS trên Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return // Không có quyền → bỏ qua, tránh crash
        }

        // Dùng taskTitle.hashCode làm notification ID để tránh trùng lặp
        NotificationManagerCompat.from(context).notify(taskTitle.hashCode(), notification)
    }

    // ─── Gửi thông báo deadline 5 giờ ────────────────────────────────────────

    /**
     * Gửi thông báo cảnh báo khi deadline của một task còn 5 giờ.
     *
     * @param context    Context hiện tại.
     * @param taskTitle  Tiêu đề của task cần nhắc.
     */
    fun sendFiveHourDeadlineNotification(context: Context, taskTitle: String) {
        val body = "⏳ Còn 5 giờ nữa là đến hạn: $taskTitle"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ Deadline sắp đến!")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // Kiểm tra quyền POST_NOTIFICATIONS trên Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        NotificationManagerCompat.from(context).notify(("${taskTitle}_5h").hashCode(), notification)
    }

    // ─── Gửi tóm tắt buổi sáng ───────────────────────────────────────────────

    /**
     * Gửi thông báo tóm tắt kế hoạch học tập đầu ngày.
     *
     * @param context       Context hiện tại.
     * @param taskCount     Tổng số task cần làm hôm nay (0 = không có task).
     * @param topTaskTitle  Tiêu đề task có độ ưu tiên cao nhất (rỗng khi taskCount == 0).
     */
    fun sendDailySummary(context: Context, taskCount: Int, topTaskTitle: String) {
        // Hiển thị nội dung khác nhau tùy theo có task hay không
        val body = if (taskCount == 0) {
            "Hôm nay bạn không có task nào."
        } else {
            "Bạn có $taskCount việc cần làm. Ưu tiên nhất: $topTaskTitle"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📚 Kế hoạch hôm nay")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Kiểm tra quyền trước khi gửi (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        // ID cố định để thông báo cũ bị ghi đè khi gửi lại
        NotificationManagerCompat.from(context).notify(REQUEST_DAILY_MORNING, notification)
    }

    // ─── Gửi tổng kết buổi tối ───────────────────────────────────────────────

    /**
     * Gửi thông báo tổng kết số lượng task đã hoàn thành trong ngày.
     *
     * @param context    Context hiện tại.
     * @param doneCount  Số task đã hoàn thành.
     * @param totalCount Tổng số task cần làm hôm nay.
     */
    fun sendEveningSummary(context: Context, doneCount: Int, totalCount: Int) {
        val body = "Đã hoàn thành $doneCount/$totalCount việc hôm nay."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🌙 Tổng kết ngày")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        // Kiểm tra quyền trước khi gửi (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        // ID cố định để thông báo cũ bị ghi đè khi gửi lại
        NotificationManagerCompat.from(context).notify(REQUEST_EVENING, notification)
    }

    // ─── Lên lịch alarm deadline ─────────────────────────────────────────────

    /**
     * Đặt 2 alarm bằng AlarmManager để nhắc nhở deadline của một task:
     *   - Trước 1 ngày so với deadline
     *   - Trước 3 ngày so với deadline
     *
     * Nếu thời điểm tính toán đã qua so với hiện tại thì alarm đó sẽ bị bỏ qua.
     * PendingIntent chứa taskId để DeadlineReceiver biết task nào cần nhắc.
     *
     * @param context         Context hiện tại.
     * @param taskId          ID của task trong database.
     * @param deadlineDateStr Chuỗi ngày deadline theo định dạng "YYYY-MM-DD".
     */
    fun scheduleDeadlineAlarm(context: Context, taskId: Int, deadlineDateStr: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Parse chuỗi ngày deadline thành LocalDate
        val deadlineDate = try {
            // Lấy đúng 10 ký tự đầu để tránh lỗi nếu có timestamp thừa
            val cleanStr = if (deadlineDateStr.length >= 10) deadlineDateStr.substring(0, 10) else deadlineDateStr
            LocalDate.parse(cleanStr, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            return // Không parse được ngày → bỏ qua, tránh crash
        }

        val now = System.currentTimeMillis()

        // Danh sách các mốc thời gian cần đặt alarm: (số ngày trước deadline, request code offset)
        val alarmConfigs = listOf(
            Pair(1, REQUEST_DEADLINE_1DAY + taskId),
            Pair(3, REQUEST_DEADLINE_3DAY + taskId)
        )

        for ((daysBeforeDeadline, requestCode) in alarmConfigs) {
            // Tính thời điểm alarm = deadline - N ngày, lúc 8h sáng
            val alarmDate = deadlineDate.minusDays(daysBeforeDeadline.toLong())
            val alarmCalendar = Calendar.getInstance().apply {
                timeInMillis = alarmDate.atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Bỏ qua nếu thời điểm alarm đã qua
            if (alarmCalendar.timeInMillis <= now) continue

            // Tạo Intent chứa thông tin task để DeadlineReceiver xử lý
            val intent = Intent(context, DeadlineReceiver::class.java).apply {
                putExtra("TASK_ID", taskId)
                putExtra("DAYS_LEFT", daysBeforeDeadline)
            }

            // FLAG_IMMUTABLE bắt buộc từ Android 12+
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Dùng setExactAndAllowWhileIdle để đảm bảo alarm kích hoạt đúng giờ
            // kể cả khi thiết bị đang ở chế độ Doze
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmCalendar.timeInMillis,
                pendingIntent
            )
        }

        // Tính toán và đặt alarm trước 5 giờ so với deadline
        try {
            val deadlineDateTime = if (deadlineDateStr.length >= 16) {
                LocalDateTime.parse(
                    deadlineDateStr.substring(0, 16),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                )
            } else {
                deadlineDate.atTime(23, 59)
            }

            val trigger5Hour = deadlineDateTime.minusHours(5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            if (trigger5Hour > now) {
                val intent5h = Intent(context, DeadlineReceiver::class.java).apply {
                    putExtra("TASK_ID", taskId)
                    putExtra("ALERT_TYPE", "5HOUR")
                }

                val pendingIntent5h = PendingIntent.getBroadcast(
                    context,
                    REQUEST_DEADLINE_5HOUR + taskId,
                    intent5h,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    trigger5Hour,
                    pendingIntent5h
                )
            }
        } catch (e: Exception) {
            // Lỗi parse thì bỏ qua block 5 giờ này, không ảnh hưởng các alarm trước đó
        }
    }

    // ─── Lên lịch alarm hàng ngày buổi sáng ─────────────────────────────────

    /**
     * Đặt alarm lặp lại hàng ngày để gửi tóm tắt kế hoạch học tập buổi sáng.
     * Giờ và phút kích hoạt được đọc từ SharedPreferences "studyflow_prefs":
     *   - "morning_hour"   (mặc định 7)
     *   - "morning_minute" (mặc định 0)
     * Không nhận tham số giờ — SharedPreferences là nguồn sự thật duy nhất.
     *
     * @param context Context hiện tại.
     */
    fun scheduleDailyAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Đọc giờ và phút người dùng đã lưu; fallback về 7:00 nếu chưa thiết lập
        val prefs = context.getSharedPreferences("studyflow_prefs", Context.MODE_PRIVATE)
        val hour   = prefs.getInt("morning_hour", 7)
        val minute = prefs.getInt("morning_minute", 0)

        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_DAILY_MORNING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tính thời điểm kích hoạt tiếp theo theo giờ và phút đã lưu
        // Nếu thời điểm đó hôm nay đã qua → dời sang ngày mai
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // setExactAndAllowWhileIdle: đảm bảo kích hoạt đúng giờ kể cả khi Doze
        // Receiver sẽ tự gọi lại hàm này để tạo chu kỳ lặp hàng ngày
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    // ─── Lên lịch alarm hàng ngày buổi tối ──────────────────────────────────

    /**
     * Đặt alarm lặp lại hàng ngày lúc 21h để gửi tổng kết ngày học.
     *
     * @param context Context hiện tại.
     */
    fun scheduleEveningAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, EveningReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_EVENING,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Kích hoạt lúc 21h, nếu đã qua 21h hôm nay thì dời sang ngày mai
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }
}
