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

    fun updateList(newEntries: List<KeePassEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_password, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.bind(entry)
    }

    override fun getItemCount() = entries.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val usernameTextView: TextView = itemView.findViewById(R.id.usernameTextView)
        private val copyButton: MaterialButton = itemView.findViewById(R.id.copyButton)
        private val passwordStrengthIcon: ImageView = itemView.findViewById(R.id.passwordStrengthIcon)

        fun bind(entry: KeePassEntry) {
            titleTextView.text = entry.title
            usernameTextView.text = entry.username

            // Оценка надежности пароля
            val strength = getPasswordStrengthLevel(entry.password)
            passwordStrengthIcon.visibility = if (strength != PasswordStrengthLevel.STRONG) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onItemClick(entry)
            }

            copyButton.setOnClickListener {
                onCopyClick(entry)
            }
        }
    }

    // Универсальная функция оценки уровня надежности пароля
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