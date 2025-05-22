package com.example.tpass

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.example.tpass.databinding.DialogHistoryDetailsBinding
import java.text.SimpleDateFormat
import java.util.Locale

class HistoryDetailsDialog(private val historyItem: HistoryItem) : DialogFragment() {
    private lateinit var binding: DialogHistoryDetailsBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogHistoryDetailsBinding.inflate(requireActivity().layoutInflater)

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        binding.serviceNameText.text = historyItem.serviceName
        binding.usernameText.text = "Имя пользователя: ${historyItem.username}"
        binding.actionText.text = "Действие: ${historyItem.action}"
        binding.oldPasswordText.text = historyItem.oldPassword
        binding.newPasswordText.text = historyItem.newPassword
        binding.timestampText.text = "Дата: ${dateFormat.format(historyItem.timestamp)}"

        return androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setPositiveButton("OK", null)
            .create()
    }
} 