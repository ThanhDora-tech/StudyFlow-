package com.example.studyflow.ui.task

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.studyflow.R
import com.example.studyflow.databinding.ActivityAddTaskBinding
import com.example.studyflow.notification.NotificationHelper
import com.studyflow.data.StudyFlowDatabase
import com.studyflow.data.Task
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.Calendar

class AddTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTaskBinding
    private lateinit var dbHelper: StudyFlowDatabase
    private var subjectsList: List<com.studyflow.data.Subject> = emptyList()
    private var selectedSubjectId: Int = -1
    private var selectedDeadline: String = ""
    private var imagePath: String? = null

    // Task đang được sửa (null nếu đang ở chế độ Thêm mới)
    private var existingTask: Task? = null
    private var currentTaskId: Int = -1

    // Đăng ký ActivityResultLauncher để xin quyền CAMERA
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Đã cấp quyền, mở camera
                openCamera()
            } else {
                Toast.makeText(this, "Cần quyền camera để chụp ảnh", Toast.LENGTH_SHORT).show()
            }
        }

    // Đăng ký ActivityResultLauncher để nhận kết quả chụp ảnh từ Camera
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                // Lưu bitmap ra file tạm và lấy đường dẫn
                val file = saveBitmapToFile(bitmap)
                imagePath = file.absolutePath

                // Hiển thị ảnh lên ImageView bằng Glide
                binding.ivTaskImage.visibility = View.VISIBLE
                Glide.with(this)
                    .load(file)
                    .fitCenter()
                    .into(binding.ivTaskImage)
            }
        }

    // Đăng ký ActivityResultLauncher để nhận kết quả chọn ảnh từ thư viện
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val file = copyUriToFile(uri)
                if (file != null) {
                    imagePath = file.absolutePath
                    binding.ivTaskImage.visibility = View.VISIBLE
                    Glide.with(this)
                        .load(file)
                        .fitCenter()
                        .into(binding.ivTaskImage)
                } else {
                    Toast.makeText(this, "Không thể tải ảnh đã chọn", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = StudyFlowDatabase.getInstance(this)

        setupToolbar()
        setupSubjectSpinner()

        // Kiểm tra xem có đang ở chế độ Sửa task hay không
        currentTaskId = intent.getIntExtra("EDIT_TASK_ID", -1)
        if (currentTaskId != -1) {
            loadTaskForEdit(currentTaskId)
        }

        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupSubjectSpinner() {
        // Tải danh sách môn học từ database
        subjectsList = dbHelper.getAllSubjects()
        
        // Lấy tên các môn học để hiển thị lên AutoCompleteTextView
        val subjectNames = subjectsList.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, subjectNames)
        binding.acSubject.setAdapter(adapter)

        // Lắng nghe sự kiện chọn môn học để lưu ID tương ứng
        binding.acSubject.setOnItemClickListener { _, _, position, _ ->
            selectedSubjectId = subjectsList[position].id
        }
    }

    /**
     * Nạp dữ liệu task cũ vào các trường UI khi ở chế độ Sửa.
     */
    private fun loadTaskForEdit(taskId: Int) {
        existingTask = dbHelper.getAllTasks().find { it.id == taskId } ?: return
        val task = existingTask ?: return

        // Đổi tiêu đề Toolbar sang "Sửa Task"
        supportActionBar?.title = getString(R.string.title_edit_task)

        // Điền tên môn học
        selectedSubjectId = task.subjectId
        val subjectName = subjectsList.find { it.id == task.subjectId }?.name
        if (subjectName != null) {
            binding.acSubject.setText(subjectName, false)
        }

        // Điền tên task
        binding.etTaskName.setText(task.title)

        // Điền deadline và cập nhật hiển thị nút
        selectedDeadline = task.deadline
        binding.btnSelectDeadline.text = formatDeadlineButtonText(task.deadline)

        // Đánh dấu độ khó tương ứng
        binding.rgDifficulty.check(
            when (task.difficulty) {
                1 -> R.id.rbEasy
                3 -> R.id.rbHard
                else -> R.id.rbMedium
            }
        )

        // Điền ghi chú nếu có
        binding.etNote.setText(task.note ?: "")

        // Hiển thị ảnh đính kèm nếu tồn tại
        imagePath = task.imagePath
        if (!imagePath.isNullOrBlank() && File(imagePath!!).exists()) {
            binding.ivTaskImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(File(imagePath!!))
                .into(binding.ivTaskImage)
        }
    }

    /**
     * Format chuỗi deadline thành text hiển thị trên nút chọn deadline.
     * Hỗ trợ 2 định dạng: "YYYY-MM-DD HH:mm" (mới) và "YYYY-MM-DD" (cũ).
     */
    private fun formatDeadlineButtonText(deadlineStr: String): String {
        return try {
            val hasTime = deadlineStr.length >= 16
            if (hasTime) {
                val dt = java.time.LocalDateTime.parse(
                    deadlineStr.substring(0, 16),
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                )
                val dayOfWeek = when (dt.dayOfWeek.value) {
                    1 -> "Thứ Hai"; 2 -> "Thứ Ba"; 3 -> "Thứ Tư"
                    4 -> "Thứ Năm"; 5 -> "Thứ Sáu"; 6 -> "Thứ Bảy"
                    7 -> "Chủ Nhật"; else -> ""
                }
                val d = String.format("%02d", dt.dayOfMonth)
                val m = String.format("%02d", dt.monthValue)
                val hh = String.format("%02d", dt.hour)
                val mm = String.format("%02d", dt.minute)
                "$dayOfWeek, $d/$m/${dt.year} $hh:$mm"
            } else {
                // Task cũ chỉ có ngày: hiển thị giờ mặc định 23:59
                val date = java.time.LocalDate.parse(
                    deadlineStr.substring(0, 10),
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
                )
                val dayOfWeek = when (date.dayOfWeek.value) {
                    1 -> "Thứ Hai"; 2 -> "Thứ Ba"; 3 -> "Thứ Tư"
                    4 -> "Thứ Năm"; 5 -> "Thứ Sáu"; 6 -> "Thứ Bảy"
                    7 -> "Chủ Nhật"; else -> ""
                }
                val d = String.format("%02d", date.dayOfMonth)
                val m = String.format("%02d", date.monthValue)
                "$dayOfWeek, $d/$m/${date.year} 23:59"
            }
        } catch (e: Exception) {
            deadlineStr
        }
    }

    private fun setupListeners() {
        // Nút chọn deadline
        binding.btnSelectDeadline.setOnClickListener {
            showDatePicker()
        }

        // Nút chụp ảnh
        binding.btnTakePicture.setOnClickListener {
            checkCameraPermissionAndOpenCamera()
        }

        // Nút chọn ảnh từ thư viện
        binding.btnPickImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Nút lưu task
        binding.btnSaveTask.setOnClickListener {
            saveTask()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        // Giá trị mặc định: ngày hôm nay, giờ 23:59
        var initialYear = calendar.get(Calendar.YEAR)
        var initialMonth = calendar.get(Calendar.MONTH)   // 0-based (DatePickerDialog yêu cầu)
        var initialDay = calendar.get(Calendar.DAY_OF_MONTH)
        var initialHour = 23
        var initialMinute = 59

        // Nếu đang Sửa task và đã có deadline, parse để mở picker ở đúng ngày/giờ cũ
        if (selectedDeadline.isNotEmpty()) {
            try {
                val dateStr = selectedDeadline.substring(0, 10)
                val date = LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                initialYear = date.year
                initialMonth = date.monthValue - 1  // LocalDate đếm từ 1, DatePickerDialog cần từ 0
                initialDay = date.dayOfMonth

                // Parse thêm giờ:phút nếu deadline có đủ định dạng "YYYY-MM-DD HH:mm"
                if (selectedDeadline.length >= 16) {
                    initialHour = selectedDeadline.substring(11, 13).toInt()
                    initialMinute = selectedDeadline.substring(14, 16).toInt()
                }
            } catch (e: Exception) {
                // Giữ nguyên giá trị mặc định nếu parse lỗi, không để app crash
            }
        }

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                // Mở TimePickerDialog để chọn giờ, phút ngay sau khi chọn ngày
                val timePickerDialog = TimePickerDialog(
                    this,
                    { _, selectedHour, selectedMinute ->
                        val formattedMonth = String.format("%02d", selectedMonth + 1)
                        val formattedDay = String.format("%02d", selectedDay)
                        val formattedHour = String.format("%02d", selectedHour)
                        val formattedMinute = String.format("%02d", selectedMinute)
                        
                        // Ghép ngày và giờ thành chuỗi định dạng "YYYY-MM-DD HH:mm"
                        selectedDeadline = "$selectedYear-$formattedMonth-$formattedDay $formattedHour:$formattedMinute"
                        
                        // Tính thứ trong tuần từ ngày đã chọn bằng LocalDate
                        val date = LocalDate.of(selectedYear, selectedMonth + 1, selectedDay)
                        val dayOfWeek = when (date.dayOfWeek.value) {
                            1 -> "Thứ Hai"
                            2 -> "Thứ Ba"
                            3 -> "Thứ Tư"
                            4 -> "Thứ Năm"
                            5 -> "Thứ Sáu"
                            6 -> "Thứ Bảy"
                            7 -> "Chủ Nhật"
                            else -> ""
                        }
                        
                        // Cập nhật text hiển thị trên nút chọn deadline
                        binding.btnSelectDeadline.text = "$dayOfWeek, $formattedDay/$formattedMonth/$selectedYear $formattedHour:$formattedMinute"
                    },
                    initialHour, initialMinute, true // Sử dụng định dạng 24h (không AM/PM)
                )
                timePickerDialog.show()
            },
            initialYear, initialMonth, initialDay
        )
        datePickerDialog.show()
    }

    private fun checkCameraPermissionAndOpenCamera() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Nếu đã có quyền, mở camera luôn
                openCamera()
            }
            else -> {
                // Yêu cầu quyền camera
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        takePictureLauncher.launch(null)
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val filename = "task_image_${System.currentTimeMillis()}.jpg"
        val file = File(cacheDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return file
    }

    private fun copyUriToFile(uri: Uri): File? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val filename = "task_image_${System.currentTimeMillis()}.jpg"
                val file = File(cacheDir, filename)
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
                file
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveTask() {
        val taskName = binding.etTaskName.text.toString().trim()

        // Validate dữ liệu
        if (selectedSubjectId == -1) {
            Toast.makeText(this, getString(R.string.msg_empty_subject), Toast.LENGTH_SHORT).show()
            return
        }
        if (taskName.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_empty_task_name), Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedDeadline.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_empty_deadline), Toast.LENGTH_SHORT).show()
            return
        }

        // Xác định độ khó dựa trên RadioButton được chọn
        val difficulty = when (binding.rgDifficulty.checkedRadioButtonId) {
            R.id.rbEasy -> 1
            R.id.rbHard -> 3
            else -> 2 // Mặc định là Trung bình
        }

        // Lấy giá trị ghi chú (tùy chọn)
        val note = binding.etNote.text?.toString()?.trim()
        val finalNote = if (note.isNullOrEmpty()) null else note

        if (existingTask != null) {
            // ─ Chế độ Sửa: cập nhật task cũ, giữ nguyên isDone và createdAt ─
            val updatedTask = existingTask!!.copy(
                subjectId = selectedSubjectId,
                title = taskName,
                deadline = selectedDeadline,
                difficulty = difficulty,
                imagePath = imagePath,
                note = finalNote
            )
            dbHelper.updateTask(updatedTask)

            // Cập nhật lại alarm deadline theo deadline mới
            NotificationHelper.scheduleDeadlineAlarm(
                context = this,
                taskId = existingTask!!.id,
                deadlineDateStr = selectedDeadline
            )

            Toast.makeText(this, "Đã cập nhật task", Toast.LENGTH_SHORT).show()
        } else {
            // ─ Chế độ Thêm mới: giữ nguyên logic cũ ─
            val newTask = Task(
                id = 0,
                subjectId = selectedSubjectId,
                title = taskName,
                deadline = selectedDeadline,
                difficulty = difficulty,
                isDone = false,
                imagePath = imagePath,
                note = finalNote,
                createdAt = LocalDate.now().toString()
            )

            // Lưu vào cơ sở dữ liệu và lấy taskId được tạo
            val newTaskId = dbHelper.insertTask(newTask)

            // Đặt lịch alarm deadline ngay sau khi tạo task thành công
            // Alarm sẽ kích hoạt trước 1 ngày và 3 ngày so với deadline
            if (newTaskId > 0) {
                NotificationHelper.scheduleDeadlineAlarm(
                    context = this,
                    taskId = newTaskId.toInt(),
                    deadlineDateStr = selectedDeadline
                )
            }

            Toast.makeText(this, "Đã lưu task", Toast.LENGTH_SHORT).show()
        }

        // Đóng Activity, MainActivity sẽ tự refresh qua onResume()
        finish()
    }
}
