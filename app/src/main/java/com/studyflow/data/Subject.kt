package com.studyflow.data

/**
 * Lớp dữ liệu đại diện cho một Môn học (Subject).
 *
 * @property id ID duy nhất của môn học.
 * @property name Tên môn học.
 * @property importance Mức độ quan trọng (độ ưu tiên của môn học).
 * @property color Mã màu đại diện cho môn học (dạng HEX hoặc tên màu).
 * @property createdAt Ngày tạo môn học (định dạng String).
 */
data class Subject(
    val id: Int,
    val name: String,
    val importance: Int,
    val color: String,
    val createdAt: String
)
