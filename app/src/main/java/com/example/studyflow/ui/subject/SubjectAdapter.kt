@file:Suppress("SpellCheckingInspection")

package com.example.studyflow.ui.subject

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.studyflow.databinding.ItemSubjectBinding
import com.studyflow.data.Subject

/**
 * Adapter cho danh sách môn học (RecyclerView).
 *
 * @param onDeleteClick Callback được gọi khi người dùng nhấn nút xóa môn học.
 */
class SubjectAdapter(
    private val onItemClick: (Subject) -> Unit,
    private val onEditClick: (Subject) -> Unit,
    private val onDeleteClick: (Subject) -> Unit,
    private val getUndoneCount: (subjectId: Int) -> Int
) : RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder>() {

    private var subjects: List<Subject> = emptyList()

    /**
     * Cập nhật danh sách môn học và refresh RecyclerView.
     */
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    fun submitList(newSubjects: List<Subject>) {
        subjects = newSubjects
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val binding = ItemSubjectBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SubjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        holder.bind(subjects[position])
    }

    override fun getItemCount(): Int = subjects.size

    inner class SubjectViewHolder(private val binding: ItemSubjectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(subject: Subject) {
            binding.tvSubjectName.text = subject.name
            binding.tvImportance.text = "⭐".repeat(subject.importance)
            
            try {
                binding.root.setCardBackgroundColor(Color.parseColor(subject.color))
            } catch (_: Exception) {
                // Mặc định màu xám nếu mã màu không hợp lệ
                binding.root.setCardBackgroundColor(Color.GRAY)
            }
            // Hiển thị số task chưa xong
            val undoneCount = getUndoneCount(subject.id)
            binding.tvTaskBadge.text = when (undoneCount) {
                0    -> "🎉 Không có task nào"
                1    -> "📋 1 task chưa xong"
                else -> "📋 $undoneCount task chưa xong"
            }

            binding.root.setOnClickListener {
                onItemClick(subject)
            }
            binding.btnDelete.setOnClickListener {
                onDeleteClick(subject)
            }
            binding.btnEdit.setOnClickListener {
                onEditClick(subject)
            }
        }
    }
}
