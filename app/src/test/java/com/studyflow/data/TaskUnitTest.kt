package com.studyflow.data

import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Kiểm thử đơn vị cho lớp dữ liệu Task, đặc biệt là hàm calculatePriorityScore.
 */
class TaskUnitTest {

    @Test
    fun testCalculatePriorityScore() {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        // 1. Quá hạn (deadline < ngày hôm nay): 15 điểm
        val overdueTask = Task(
            id = 1,
            subjectId = 101,
            title = "Bài tập về nhà 1",
            deadline = today.minusDays(2).format(formatter),
            difficulty = 3,
            isDone = false,
            imagePath = null,
            createdAt = today.format(formatter)
        )
        // importance = 4, difficulty = 3
        // score = (4 * 3) + (3 * 2) + 15 = 12 + 6 + 15 = 33
        assertEquals(33, overdueTask.calculatePriorityScore(importance = 4))

        // 2. Hôm nay (deadline == ngày hôm nay): 15 điểm
        val todayTask = Task(
            id = 2,
            subjectId = 101,
            title = "Bài tập về nhà 2",
            deadline = today.format(formatter),
            difficulty = 2,
            isDone = false,
            imagePath = null,
            createdAt = today.format(formatter)
        )
        // importance = 5, difficulty = 2
        // score = (5 * 3) + (2 * 2) + 15 = 15 + 4 + 15 = 34
        assertEquals(34, todayTask.calculatePriorityScore(importance = 5))

        // 3. Còn 1 ngày (deadline == ngày mai): 10 điểm
        val oneDayTask = Task(
            id = 3,
            subjectId = 101,
            title = "Bài tập về nhà 3",
            deadline = today.plusDays(1).format(formatter),
            difficulty = 1,
            isDone = false,
            imagePath = null,
            createdAt = today.format(formatter)
        )
        // importance = 3, difficulty = 1
        // score = (3 * 3) + (1 * 2) + 10 = 9 + 2 + 10 = 21
        assertEquals(21, oneDayTask.calculatePriorityScore(importance = 3))

        // 4. Còn 2 ngày: 8 điểm
        val twoDaysTask = Task(
            id = 4,
            subjectId = 101,
            title = "Bài tập về nhà 4",
            deadline = today.plusDays(2).format(formatter),
            difficulty = 2,
            isDone = false,
            imagePath = null,
            createdAt = today.format(formatter)
        )
        // importance = 2, difficulty = 2
        // score = (2 * 3) + (2 * 2) + 8 = 6 + 4 + 8 = 18
        assertEquals(18, twoDaysTask.calculatePriorityScore(importance = 2))

        // 5. Còn 3 ngày: 6 điểm
        val threeDaysTask = Task(
            id = 5,
            subjectId = 101,
            title = "Bài tập về nhà 5",
            deadline = today.plusDays(3).format(formatter),
            difficulty = 2,
            isDone = false,
            imagePath = null,
            createdAt = today.format(formatter)
        )
        // score = (2 * 3) + (2 * 2) + 6 = 6 + 4 + 6 = 16
        assertEquals(16, threeDaysTask.calculatePriorityScore(importance = 2))

        // 6. Còn 4-5 ngày: 4 điểm (thử 4 ngày và 5 ngày)
        val fourDaysTask = Task(
            id = 6,
            subjectId = 101,
            title = "Bài tập về nhà 6",
            deadline = today.plusDays(4).format(formatter),
            difficulty = 2,
            isDone = false,
            imagePath = null,
            createdAt = today.format(formatter)
        )
        // score = (2 * 3) + (2 * 2) + 4 = 6 + 4 + 4 = 14
        assertEquals(14, fourDaysTask.calculatePriorityScore(importance = 2))

        val fiveDaysTask = Task(
            id = 7,
            subjectId = 101,
            title = "Bài tập về nhà 7",
            deadline = today.plusDays(5).format(formatter),
            difficulty = 2,
            isDone = false,
            imagePath = null,
            createdAt = today.format(formatter)
        )
        assertEquals(14, fiveDaysTask.calculatePriorityScore(importance = 2))

        // 7. Còn 6-7 ngày: 2 điểm (thử 6 ngày và 7 ngày)
        val sixDaysTask = Task(
            id = 8,
            subjectId = 101,
            title = "Bài tập về nhà 8",
            deadline = today.plusDays(6).format(formatter),
            difficulty = 2,
            isDone = false,
            imagePath = null,
            createdAt = today.format(formatter)
        )
        // score = (2 * 3) + (2 * 2) + 2 = 6 + 4 + 2 = 12
        assertEquals(12, sixDaysTask.calculatePriorityScore(importance = 2))

        val sevenDaysTask = Task(
            id = 9,
            subjectId = 101,
            title = "Bài tập về nhà 9",
            deadline = today.plusDays(7).format(formatter),
            difficulty = 2,
            isDone = false,
            imagePath = null,
            createdAt = today.format(formatter)
        )
        assertEquals(12, sevenDaysTask.calculatePriorityScore(importance = 2))

        // 8. Còn > 7 ngày: 1 điểm
        val eightDaysTask = Task(
            id = 10,
            subjectId = 101,
            title = "Bài tập về nhà 10",
            deadline = today.plusDays(10).format(formatter),
            difficulty = 2,
            isDone = false,
            imagePath = null,
            createdAt = today.format(formatter)
        )
        // score = (2 * 3) + (2 * 2) + 1 = 6 + 4 + 1 = 11
        assertEquals(11, eightDaysTask.calculatePriorityScore(importance = 2))
    }
}
