package com.example.tpass

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class PasswordAdapter(
    private val onItemClick: (KeePassEntry) -> Unit,
    private val onCopyClick: (KeePassEntry) -> Unit
) : RecyclerView.Adapter<PasswordAdapter.ViewHolder>() {

    private var entries: List<KeePassEntry> = emptyList()
    private var postponedReminderIds: Set<Int> = emptySet()

    fun updateList(newEntries: List<KeePassEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    fun setPostponedReminders(ids: Set<Int>) {
        postponedReminderIds = ids
        notifyDataSetChanged()
    }

    fun getPositionById(id: Int): Int {
        return entries.indexOfFirst { it.id == id }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_password, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        val showReminder = entry.id in postponedReminderIds
        holder.bind(entry, showReminder)
    }

    override fun getItemCount() = entries.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val usernameTextView: TextView = itemView.findViewById(R.id.usernameTextView)
        private val copyButton: MaterialButton = itemView.findViewById(R.id.copyButton)
        private val reminderTextView: TextView? = itemView.findViewById(R.id.reminderTextView)
        private val passwordStrengthIcon: ImageView? = itemView.findViewById(R.id.passwordStrengthIcon)
        private val categoryTextView: TextView? = itemView.findViewById(R.id.categoryTextView)
        private val tagsTextView: TextView? = itemView.findViewById(R.id.tagsTextView)

        fun bind(entry: KeePassEntry, showReminder: Boolean = false) {
            titleTextView.text = entry.title
            usernameTextView.text = entry.username
            reminderTextView?.visibility = if (showReminder) View.VISIBLE else View.GONE
            // Показываем иконку надежности пароля только для записей, которые не являются PIN-кодами
            passwordStrengthIcon?.visibility = if (entry.category != "PIN-коды" && getPasswordStrengthLevel(entry.password) != PasswordStrengthLevel.STRONG) View.VISIBLE else View.GONE
            // Отображение категории
            categoryTextView?.apply {
                text = entry.category
                visibility = if (entry.category.isNotEmpty()) View.VISIBLE else View.GONE
            }
            // Отображение тегов
            tagsTextView?.apply {
                text = entry.tags.joinToString(", ")
                visibility = if (entry.tags.isNotEmpty()) View.VISIBLE else View.GONE
            }
            itemView.setOnClickListener {
                onItemClick(entry)
            }
            copyButton.setOnClickListener {
                onCopyClick(entry)
            }
        }
    }

    enum class PasswordStrengthLevel { WEAK, MEDIUM, STRONG }
    private fun getPasswordStrengthLevel(password: String): PasswordStrengthLevel {
        var score = 0
        if (password.length >= 8) score += 20
        if (password.length >= 12) score += 20
        if (password.length >= 16) score += 20
        if (password.any { it.isUpperCase() }) score += 15
        if (password.any { it.isLowerCase() }) score += 10
        if (password.any { it.isDigit() }) score += 10
        if (password.any { it in "!@#\$%^&*()_+-=[]{}|;:'\",.<>?/" }) score += 15
        if (Regex("(\\d)\\1").containsMatchIn(password)) score -= 15
        return when {
            score < 40 -> PasswordStrengthLevel.WEAK
            score < 70 -> PasswordStrengthLevel.MEDIUM
            else -> PasswordStrengthLevel.STRONG
        }
    }
}