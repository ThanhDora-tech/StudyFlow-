package com.example.studyflow.ui.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.studyflow.databinding.ActivitySettingsBinding
import com.example.studyflow.notification.EveningReceiver
import com.example.studyflow.notification.NotificationHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Khởi tạo ViewBinding
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Thiết lập Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // Khởi tạo SharedPreferences
        sharedPreferences = getSharedPreferences("studyflow_prefs", Context.MODE_PRIVATE)

        // Đọc "morning_hour" (default 7) và thiết lập cho TimePicker
        val savedHour = sharedPreferences.getInt("morning_hour", 7)
        val savedMinute = sharedPreferences.getInt("morning_minute", 0)
        binding.timePicker.hour = savedHour // Sử dụng thuộc tính hour thay vì currentHour đã bị deprecated
        binding.timePicker.minute = savedMinute

        // Đọc "evening_notify" (default true) và thiết lập trạng thái cho Switch
        val isEveningNotifyEnabled = sharedPreferences.getBoolean("evening_notify", true)
        binding.switchEveningNotify.isChecked = isEveningNotifyEnabled

        // Xử lý sự kiện lưu giờ nhắc buổi sáng
        binding.btnSaveTime.setOnClickListener {
            val hour = binding.timePicker.hour
            val minute = binding.timePicker.minute

            // Lưu giờ nhắc vào SharedPreferences
            sharedPreferences.edit()
                .putInt("morning_hour", hour)
                .putInt("morning_minute", minute)
                .apply()

            // Gọi NotificationHelper để lên lịch (hàm tự đọc giờ/phút từ SharedPreferences)
            NotificationHelper.scheduleDailyAlarm(this)
            Log.d("StudyFlow_Debug", "SettingsActivity: đã gọi scheduleDailyAlarm sau khi lưu giờ $hour:$minute")

            val minuteStr = minute.toString().padStart(2, '0')
            Toast.makeText(this, "Đã lưu: nhắc lúc $hour:$minuteStr mỗi ngày", Toast.LENGTH_SHORT).show()
        }

        // Xử lý sự kiện thay đổi trạng thái thông báo buổi tối
        binding.switchEveningNotify.setOnCheckedChangeListener { _, isChecked ->
            // Lưu trạng thái vào SharedPreferences
            sharedPreferences.edit().putBoolean("evening_notify", isChecked).apply()

            if (isChecked) {
                // Nếu bật, tiến hành lên lịch thông báo
                NotificationHelper.scheduleEveningAlarm(this)
            } else {
                // Nếu tắt, hủy thông báo buổi tối
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(this, EveningReceiver::class.java)
                
                // REQUEST_EVENING (30_001) theo yêu cầu do bị private trong NotificationHelper
                val pi = PendingIntent.getBroadcast(
                    this,
                    30_001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                alarmManager.cancel(pi)
            }
        }
    }

    // Xử lý sự kiện bấm nút back trên Toolbar
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
