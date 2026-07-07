package com.studyflow.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Lớp quản lý cơ sở dữ liệu SQLite cho ứng dụng StudyFlow.
 * Sử dụng Singleton pattern (companion object) để đảm bảo chỉ có
 * một instance duy nhất tồn tại trong suốt vòng đời ứng dụng.
 */
class StudyFlowDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    // ─────────────────────────────────────────────────────────────
    // Singleton
    // ─────────────────────────────────────────────────────────────
    companion object {
        /** Tên file cơ sở dữ liệu */
        private const val DB_NAME = "studyflow.db"

        /** Phiên bản hiện tại của cơ sở dữ liệu */
        private const val DB_VERSION = 2

        // ── Tên bảng ──────────────────────────────────────────────
        private const val TABLE_SUBJECTS = "subjects"
        private const val TABLE_TASKS = "tasks"
        private const val TABLE_NOTIFICATIONS = "notifications"

        // ── Cột bảng subjects ─────────────────────────────────────
        private const val COL_SUB_ID = "id"
        private const val COL_SUB_NAME = "name"
        private const val COL_SUB_IMPORTANCE = "importance"
        private const val COL_SUB_COLOR = "color"
        private const val COL_SUB_CREATED_AT = "created_at"

        // ── Cột bảng tasks ────────────────────────────────────────
        private const val COL_TASK_ID = "id"
        private const val COL_TASK_SUBJECT_ID = "subject_id"
        private const val COL_TASK_TITLE = "title"
        private const val COL_TASK_DEADLINE = "deadline"
        private const val COL_TASK_DIFFICULTY = "difficulty"
        private const val COL_TASK_IS_DONE = "is_done"
        private const val COL_TASK_IMAGE_PATH = "image_path"
        private const val COL_TASK_NOTE = "note"
        private const val COL_TASK_CREATED_AT = "created_at"

        // ── Cột bảng notifications ────────────────────────────────
        private const val COL_NOTIF_ID = "id"
        private const val COL_NOTIF_TASK_ID = "task_id"
        private const val COL_NOTIF_NOTIFY_TIME = "notify_time"
        private const val COL_NOTIF_IS_SENT = "is_sent"

        /** Biến giữ instance duy nhất (thread-safe với @Volatile) */
        @Volatile
        private var instance: StudyFlowDatabase? = null

        /**
         * Trả về instance duy nhất của StudyFlowDatabase.
         * Thread-safe nhờ Double-Checked Locking.
         *
         * @param context Context của ứng dụng.
         */
        fun getInstance(context: Context): StudyFlowDatabase {
            return instance ?: synchronized(this) {
                instance ?: StudyFlowDatabase(context).also { instance = it }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // onCreate – Tạo bảng khi cơ sở dữ liệu được khởi tạo lần đầu
    // ─────────────────────────────────────────────────────────────
    override fun onCreate(db: SQLiteDatabase) {
        // Bật tính năng khóa ngoại (Foreign Key) cho SQLite
        db.execSQL("PRAGMA foreign_keys = ON;")

        // Tạo bảng subjects
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SUBJECTS (
                $COL_SUB_ID        INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SUB_NAME      TEXT    NOT NULL,
                $COL_SUB_IMPORTANCE INTEGER DEFAULT 3,
                $COL_SUB_COLOR     TEXT,
                $COL_SUB_CREATED_AT TEXT
            )
            """.trimIndent()
        )

        // Tạo bảng tasks (subject_id là khóa ngoại tham chiếu subjects.id)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TASKS (
                $COL_TASK_ID         INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TASK_SUBJECT_ID INTEGER,
                $COL_TASK_TITLE      TEXT    NOT NULL,
                $COL_TASK_DEADLINE   TEXT    NOT NULL,
                $COL_TASK_DIFFICULTY INTEGER DEFAULT 2,
                $COL_TASK_IS_DONE    INTEGER DEFAULT 0,
                $COL_TASK_IMAGE_PATH TEXT,
                $COL_TASK_NOTE       TEXT,
                $COL_TASK_CREATED_AT TEXT,
                FOREIGN KEY ($COL_TASK_SUBJECT_ID)
                    REFERENCES $TABLE_SUBJECTS($COL_SUB_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // Tạo bảng notifications (task_id là khóa ngoại tham chiếu tasks.id)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_NOTIFICATIONS (
                $COL_NOTIF_ID          INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NOTIF_TASK_ID     INTEGER,
                $COL_NOTIF_NOTIFY_TIME TEXT,
                $COL_NOTIF_IS_SENT     INTEGER DEFAULT 0,
                FOREIGN KEY ($COL_NOTIF_TASK_ID)
                    REFERENCES $TABLE_TASKS($COL_TASK_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    // ─────────────────────────────────────────────────────────────
    // onUpgrade – Nâng cấp schema khi tăng DB_VERSION
    // ─────────────────────────────────────────────────────────────
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_TASKS ADD COLUMN $COL_TASK_NOTE TEXT")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // onOpen – Bật Foreign Key mỗi khi mở kết nối (SQLite yêu cầu)
    // ─────────────────────────────────────────────────────────────
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            db.execSQL("PRAGMA foreign_keys = ON;")
        }
    }

    // ═════════════════════════════════════════════════════════════
    // SUBJECTS – Các hàm thao tác với bảng subjects
    // ═════════════════════════════════════════════════════════════

    /**
     * Thêm một môn học mới vào cơ sở dữ liệu.
     *
     * @param subject Đối tượng Subject cần thêm.
     * @return Row ID của bản ghi vừa thêm, hoặc -1 nếu thất bại.
     */
    fun insertSubject(subject: Subject): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SUB_NAME, subject.name)
            put(COL_SUB_IMPORTANCE, subject.importance)
            put(COL_SUB_COLOR, subject.color)
            put(COL_SUB_CREATED_AT, subject.createdAt)
        }
        return db.insert(TABLE_SUBJECTS, null, values)
    }

    /**
     * Lấy toàn bộ danh sách môn học từ cơ sở dữ liệu.
     *
     * @return Danh sách các đối tượng Subject.
     */
    fun getAllSubjects(): List<Subject> {
        val subjects = mutableListOf<Subject>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SUBJECTS,
            null,       // lấy tất cả cột
            null, null, // không lọc
            null, null, // không nhóm
            "$COL_SUB_NAME ASC" // sắp xếp theo tên
        )
        cursor.use {
            while (it.moveToNext()) {
                subjects.add(
                    Subject(
                        id = it.getInt(it.getColumnIndexOrThrow(COL_SUB_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(COL_SUB_NAME)),
                        importance = it.getInt(it.getColumnIndexOrThrow(COL_SUB_IMPORTANCE)),
                        color = it.getString(it.getColumnIndexOrThrow(COL_SUB_COLOR)) ?: "",
                        createdAt = it.getString(it.getColumnIndexOrThrow(COL_SUB_CREATED_AT)) ?: ""
                    )
                )
            }
        }
        return subjects
    }

    /**
     * Cập nhật thông tin một môn học đã có trong cơ sở dữ liệu.
     *
     * @param subject Đối tượng Subject với dữ liệu đã được sửa đổi.
     * @return Số hàng bị ảnh hưởng (1 nếu thành công, 0 nếu không tìm thấy).
     */
    fun updateSubject(subject: Subject): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SUB_NAME, subject.name)
            put(COL_SUB_IMPORTANCE, subject.importance)
            put(COL_SUB_COLOR, subject.color)
            put(COL_SUB_CREATED_AT, subject.createdAt)
        }
        return db.update(
            TABLE_SUBJECTS,
            values,
            "$COL_SUB_ID = ?",
            arrayOf(subject.id.toString())
        )
    }

    /**
     * Xóa một môn học theo ID.
     * Do ON DELETE CASCADE, tất cả Task thuộc môn học này cũng bị xóa theo.
     *
     * @param id ID của môn học cần xóa.
     * @return Số hàng bị ảnh hưởng (1 nếu thành công, 0 nếu không tìm thấy).
     */
    fun deleteSubject(id: Int): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_SUBJECTS,
            "$COL_SUB_ID = ?",
            arrayOf(id.toString())
        )
    }

    // ═════════════════════════════════════════════════════════════
    // TASKS – Các hàm thao tác với bảng tasks
    // ═════════════════════════════════════════════════════════════

    /**
     * Thêm một nhiệm vụ mới vào cơ sở dữ liệu.
     *
     * @param task Đối tượng Task cần thêm.
     * @return Row ID của bản ghi vừa thêm, hoặc -1 nếu thất bại.
     */
    fun insertTask(task: Task): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TASK_SUBJECT_ID, task.subjectId)
            put(COL_TASK_TITLE, task.title)
            put(COL_TASK_DEADLINE, task.deadline)
            put(COL_TASK_DIFFICULTY, task.difficulty)
            put(COL_TASK_IS_DONE, if (task.isDone) 1 else 0)
            put(COL_TASK_IMAGE_PATH, task.imagePath)
            put(COL_TASK_NOTE, task.note)
            put(COL_TASK_CREATED_AT, task.createdAt)
        }
        return db.insert(TABLE_TASKS, null, values)
    }

    /**
     * Hàm nội bộ dùng để đọc một hàng từ Cursor và tạo đối tượng Task.
     * Đảm bảo mapping nhất quán ở một nơi duy nhất.
     */
    private fun cursorToTask(cursor: android.database.Cursor): Task {
        val noteIndex = cursor.getColumnIndex(COL_TASK_NOTE)
        val note = if (noteIndex != -1) cursor.getString(noteIndex) else null
        return Task(
            id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TASK_ID)),
            subjectId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TASK_SUBJECT_ID)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TASK_TITLE)),
            deadline = cursor.getString(cursor.getColumnIndexOrThrow(COL_TASK_DEADLINE)),
            difficulty = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TASK_DIFFICULTY)),
            isDone = cursor.getInt(cursor.getColumnIndexOrThrow(COL_TASK_IS_DONE)) != 0,
            imagePath = cursor.getString(cursor.getColumnIndexOrThrow(COL_TASK_IMAGE_PATH)),
            note = note,
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_TASK_CREATED_AT)) ?: ""
        )
    }

    /**
     * Lấy toàn bộ danh sách nhiệm vụ từ cơ sở dữ liệu.
     *
     * @return Danh sách tất cả các đối tượng Task.
     */
    fun getAllTasks(): List<Task> {
        val tasks = mutableListOf<Task>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TASKS, null, null, null, null, null,
            "$COL_TASK_DEADLINE ASC" // sắp xếp theo deadline gần nhất
        )
        cursor.use {
            while (it.moveToNext()) tasks.add(cursorToTask(it))
        }
        return tasks
    }

    /**
     * Lấy danh sách nhiệm vụ thuộc về một môn học cụ thể.
     *
     * @param subjectId ID của môn học cần lọc.
     * @return Danh sách các Task thuộc môn học đó.
     */
    fun getTasksBySubjectId(subjectId: Int): List<Task> {
        val tasks = mutableListOf<Task>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TASKS, null,
            "$COL_TASK_SUBJECT_ID = ?",
            arrayOf(subjectId.toString()),
            null, null,
            "$COL_TASK_DEADLINE ASC"
        )
        cursor.use {
            while (it.moveToNext()) tasks.add(cursorToTask(it))
        }
        return tasks
    }

    /**
     * Đếm số task chưa hoàn thành (is_done = 0) của một môn học.
     *
     * @param subjectId ID của môn học cần đếm.
     * @return Số lượng task chưa xong.
     */
    fun getUndoneTaskCountBySubject(subjectId: Int): Int {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TASKS,
            arrayOf("COUNT(*)"),
            "$COL_TASK_SUBJECT_ID = ? AND $COL_TASK_IS_DONE = ?",
            arrayOf(subjectId.toString(), "0"),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    /**
     * Lấy danh sách các nhiệm vụ chưa hoàn thành (is_done = 0).
     *
     * @return Danh sách các Task chưa hoàn thành.
     */
    fun getUndoneTasks(): List<Task> {
        val tasks = mutableListOf<Task>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TASKS, null,
            "$COL_TASK_IS_DONE = ?",
            arrayOf("0"),
            null, null,
            "$COL_TASK_DEADLINE ASC"
        )
        cursor.use {
            while (it.moveToNext()) tasks.add(cursorToTask(it))
        }
        return tasks
    }

    /**
     * Lấy danh sách nhiệm vụ chưa hoàn thành, đã được sắp xếp theo điểm ưu tiên
     * giảm dần. Điểm được tính bằng task.calculatePriorityScore(subjectImportance).
     *
     * Cần JOIN với bảng subjects để lấy importance của từng môn học.
     *
     * @return Danh sách Task được sắp xếp theo điểm ưu tiên từ cao đến thấp.
     */
    fun getTasksSortedByPriority(): List<Task> {
        val db = readableDatabase

        // JOIN tasks với subjects để lấy importance phục vụ tính điểm
        val sql = """
            SELECT t.*, s.$COL_SUB_IMPORTANCE AS subject_importance
            FROM $TABLE_TASKS t
            LEFT JOIN $TABLE_SUBJECTS s ON t.$COL_TASK_SUBJECT_ID = s.$COL_SUB_ID
            WHERE t.$COL_TASK_IS_DONE = 0
        """.trimIndent()

        val cursor = db.rawQuery(sql, null)
        val tasksWithScore = mutableListOf<Pair<Task, Int>>()

        cursor.use {
            while (it.moveToNext()) {
                val task = cursorToTask(it)
                // Lấy importance từ cột bổ sung (mặc định = 3 nếu không tìm thấy môn học)
                val importanceIndex = it.getColumnIndex("subject_importance")
                val importance = if (importanceIndex != -1) it.getInt(importanceIndex) else 3
                val score = task.calculatePriorityScore(importance)
                tasksWithScore.add(Pair(task, score))
            }
        }

        // Sắp xếp theo điểm giảm dần (score cao = ưu tiên cao)
        return tasksWithScore
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /**
     * Lấy danh sách nhiệm vụ chưa hoàn thành có deadline trong vòng [days] ngày tới.
     *
     * @param days Số ngày tới để lọc (ví dụ: days = 3 → lấy task deadline trong 3 ngày).
     * @return Danh sách các Task sắp đến hạn.
     */
    fun getTasksDueWithinDays(days: Int): List<Task> {
        val tasks = mutableListOf<Task>()
        val db = readableDatabase
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        val today = LocalDate.now()
        val upperBound = today.plusDays(days.toLong())

        // Dùng so sánh chuỗi ngày định dạng ISO (yyyy-MM-dd) – hợp lệ vì định dạng này so sánh lexicographic đúng thứ tự thời gian
        val cursor = db.query(
            TABLE_TASKS, null,
            "$COL_TASK_IS_DONE = ? AND $COL_TASK_DEADLINE >= ? AND $COL_TASK_DEADLINE <= ?",
            arrayOf("0", today.format(formatter), upperBound.format(formatter)),
            null, null,
            "$COL_TASK_DEADLINE ASC"
        )
        cursor.use {
            while (it.moveToNext()) tasks.add(cursorToTask(it))
        }
        return tasks
    }

    /**
     * Đánh dấu một nhiệm vụ là đã hoàn thành (is_done = 1).
     *
     * @param id ID của nhiệm vụ cần đánh dấu.
     * @return Số hàng bị ảnh hưởng (1 nếu thành công, 0 nếu không tìm thấy).
     */
    fun markTaskAsDone(id: Int): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TASK_IS_DONE, 1)
        }
        return db.update(
            TABLE_TASKS,
            values,
            "$COL_TASK_ID = ?",
            arrayOf(id.toString())
        )
    }

    /**
     * Đảo ngược trạng thái hoàn thành của một nhiệm vụ.
     * Nếu is_done = 0 → set thành 1 (hoàn thành).
     * Nếu is_done = 1 → set thành 0 (chưa hoàn thành).
     *
     * @param id ID của nhiệm vụ cần đảo trạng thái.
     * @return Giá trị is_done mới (0 hoặc 1) sau khi đảo, hoặc -1 nếu không tìm thấy task.
     */
    fun toggleTaskDone(id: Int): Int {
        val db = writableDatabase

        // Đọc trạng thái is_done hiện tại của task
        val cursor = db.query(
            TABLE_TASKS,
            arrayOf(COL_TASK_IS_DONE),
            "$COL_TASK_ID = ?",
            arrayOf(id.toString()),
            null, null, null
        )

        val currentIsDone = cursor.use {
            if (it.moveToFirst()) it.getInt(it.getColumnIndexOrThrow(COL_TASK_IS_DONE))
            else return -1 // Không tìm thấy task
        }

        // Đảo ngược: 0 → 1, 1 → 0
        val newIsDone = if (currentIsDone == 0) 1 else 0

        val values = ContentValues().apply {
            put(COL_TASK_IS_DONE, newIsDone)
        }
        db.update(
            TABLE_TASKS,
            values,
            "$COL_TASK_ID = ?",
            arrayOf(id.toString())
        )

        return newIsDone
    }

    /**
     * Cập nhật toàn bộ thông tin của một nhiệm vụ đã có.
     *
     * @param task Đối tượng Task với dữ liệu đã được sửa đổi.
     * @return Số hàng bị ảnh hưởng (1 nếu thành công, 0 nếu không tìm thấy).
     */
    fun updateTask(task: Task): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TASK_SUBJECT_ID, task.subjectId)
            put(COL_TASK_TITLE, task.title)
            put(COL_TASK_DEADLINE, task.deadline)
            put(COL_TASK_DIFFICULTY, task.difficulty)
            put(COL_TASK_IS_DONE, if (task.isDone) 1 else 0)
            put(COL_TASK_IMAGE_PATH, task.imagePath)
            put(COL_TASK_NOTE, task.note)
            put(COL_TASK_CREATED_AT, task.createdAt)
        }
        return db.update(
            TABLE_TASKS,
            values,
            "$COL_TASK_ID = ?",
            arrayOf(task.id.toString())
        )
    }

    /**
     * Xóa một nhiệm vụ theo ID.
     * Do ON DELETE CASCADE, tất cả Notification thuộc task này cũng bị xóa theo.
     *
     * @param id ID của nhiệm vụ cần xóa.
     * @return Số hàng bị ảnh hưởng (1 nếu thành công, 0 nếu không tìm thấy).
     */
    fun deleteTask(id: Int): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_TASKS,
            "$COL_TASK_ID = ?",
            arrayOf(id.toString())
        )
    }
}
