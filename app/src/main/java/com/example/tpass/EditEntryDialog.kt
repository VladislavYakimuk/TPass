package com.example.tpass

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import androidx.fragment.app.DialogFragment
import com.example.tpass.databinding.DialogEditEntryBinding

class EditEntryDialog(
    private val entry: KeePassEntry,
    private val onSave: (KeePassEntry) -> Unit,
    private val onDelete: (KeePassEntry) -> Unit
) : DialogFragment() {

    private lateinit var binding: DialogEditEntryBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogEditEntryBinding.inflate(requireActivity().layoutInflater)

        binding.editTextService.setText(entry.title)
        binding.editTextUsername.setText(entry.username)
        binding.editTextPassword.setText(entry.password)
        binding.editTextUrl.setText(entry.url)
        binding.editTextNotes.setText(entry.notes)

        var isPasswordVisible = false

        binding.buttonTogglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.editTextPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.buttonTogglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off)
            } else {
                binding.editTextPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.buttonTogglePasswordVisibility.setImageResource(R.drawable.ic_visibility)
            }
            binding.editTextPassword.setSelection(binding.editTextPassword.text.length)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Редактировать запись")
            .setView(binding.root)
            .setPositiveButton("Сохранить") { _, _ ->
                val updatedEntry = entry.copy(
                    title = binding.editTextService.text.toString(),
                    username = binding.editTextUsername.text.toString(),
                    password = binding.editTextPassword.text.toString(),
                    url = binding.editTextUrl.text.toString(),
                    notes = binding.editTextNotes.text.toString()
                )
                onSave(updatedEntry)
            }
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Удалить") { _, _ ->
                onDelete(entry)
            }
            .create()
    }
}