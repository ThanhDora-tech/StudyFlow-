package com.studyflow.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Lớp dữ liệu đại diện cho một Nhiệm vụ (Task).
 *
 * @property id ID duy nhất của nhiệm vụ.
 * @property subjectId ID của môn học liên kết với nhiệm vụ này.
 * @property title Tiêu đề của nhiệm vụ.
 * @property deadline Hạn chót hoàn thành nhiệm vụ (định dạng String, ví dụ: "yyyy-MM-dd" hoặc "yyyy-MM-dd HH:mm:ss").
 * @property difficulty Độ khó của nhiệm vụ.
 * @property isDone Trạng thái hoàn thành của nhiệm vụ.
 * @property imagePath Đường dẫn ảnh đính kèm (nếu có).
 * @property createdAt Ngày tạo nhiệm vụ (định dạng String).
 */
data class Task(
    val id: Int,
    val subjectId: Int,
    val title: String,
    val deadline: String,
    val difficulty: Int,
    val isDone: Boolean,
    val imagePath: String?,
    val note: String? = null,
    val createdAt: String
) {
    /**
     * Tính toán điểm ưu tiên (Priority Score) dựa trên mức độ quan trọng (importance) của môn học,
     * độ khó (difficulty) của nhiệm vụ và khoảng cách từ hôm nay đến hạn chót (deadline).
     *
     * Công thức:
     * score = (importance * 3) + (difficulty * 2) + deadlineScore
     *
     * Trong đó deadlineScore được tính dựa trên số ngày từ hôm nay đến deadline:
     * - Quá hạn (<= 0 ngày): 15
     * - Còn 1 ngày: 10
     * - Còn 2 ngày: 8
     * - Còn 3 ngày: 6
     * - Còn 4-5 ngày: 4
     * - Còn 6-7 ngày: 2
     * - Còn > 7 ngày: 1
     *
     * @param importance Mức độ quan trọng của môn học.
     * @return Điểm số ưu tiên được tính toán.
     */
    fun calculatePriorityScore(importance: Int): Int {
        val today = LocalDate.now()
        
        // Trích xuất định dạng ngày yyyy-MM-dd từ chuỗi deadline
        val cleanDeadline = if (deadline.length >= 10) deadline.substring(0, 10) else deadline
        
        val deadlineDate = try {
            LocalDate.parse(cleanDeadline, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            // Nếu không phân tích được định dạng ngày, mặc định trả về ngày hôm nay
            today
        }

        // Tính số ngày chênh lệch giữa ngày hôm nay và deadline
        val daysBetween = ChronoUnit.DAYS.between(today, deadlineDate)

        val deadlineScore = when {
            daysBetween <= 0 -> 15 // Đã quá hạn hoặc hạn chót là hôm nay
            daysBetween == 1L -> 10
            daysBetween == 2L -> 8
            daysBetween == 3L -> 6
            daysBetween in 4..5 -> 4
            daysBetween in 6..7 -> 2
            else -> 1 // > 7 ngày
        }

        return (importance * 3) + (difficulty * 2) + deadlineScore
    }
}
