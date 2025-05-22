package com.example.tpass

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        fun bind(entry: KeePassEntry) {
            titleTextView.text = entry.title
            usernameTextView.text = entry.username

            itemView.setOnClickListener {
                onItemClick(entry)
            }

            copyButton.setOnClickListener {
                onCopyClick(entry)
            }
        }
    }
}