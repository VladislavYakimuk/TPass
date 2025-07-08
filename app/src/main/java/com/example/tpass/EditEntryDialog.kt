package com.example.tpass

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import androidx.fragment.app.DialogFragment
import com.example.tpass.databinding.DialogEditEntryBinding
import android.graphics.Color
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout

class EditEntryDialog(
    private val entry: KeePassEntry,
    private val onSave: (KeePassEntry) -> Unit,
    private val onDelete: (KeePassEntry) -> Unit
) : DialogFragment() {

    private lateinit var binding: DialogEditEntryBinding
    private lateinit var generatePasswordButton: MaterialButton

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

        // Кнопка генерации пароля
        generatePasswordButton = MaterialButton(requireContext()).apply {
            text = "Сгенерировать надёжный пароль"
            visibility = android.view.View.GONE
        }
        (binding.root as LinearLayout).addView(generatePasswordButton, binding.root.indexOfChild(binding.editTextPassword.parent as LinearLayout) + 2)

        generatePasswordButton.setOnClickListener {
            val generated = PasswordGenerator.generatePassword()
            binding.editTextPassword.setText(generated)
        }

        updatePasswordStrengthUI(binding.editTextPassword.text.toString())
        binding.editTextPassword.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updatePasswordStrengthUI(s?.toString() ?: "")
            }
        })

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

    private fun updatePasswordStrengthUI(password: String) {
        val bar = binding.root.findViewById<ProgressBar>(R.id.passwordStrengthBar)
        val label = binding.root.findViewById<TextView>(R.id.passwordStrengthLabel)
        val warning = binding.root.findViewById<TextView>(R.id.passwordStrengthWarning)
        val (score, level) = getPasswordStrength(password)
        bar.progress = score
        when (level) {
            PasswordStrengthLevel.WEAK -> {
                label.text = "Слабый"
                label.setTextColor(Color.parseColor("#FF5252"))
                warning.visibility = android.view.View.VISIBLE
            }
            PasswordStrengthLevel.MEDIUM -> {
                label.text = "Средний"
                label.setTextColor(Color.parseColor("#FFEB3B"))
                warning.visibility = android.view.View.VISIBLE
            }
            PasswordStrengthLevel.STRONG -> {
                label.text = "Сильный"
                label.setTextColor(Color.parseColor("#4CAF50"))
                warning.visibility = android.view.View.GONE
            }
        }
        // Показываем/скрываем кнопку генерации
        updateGenerateButtonVisibility(level)
    }

    private fun updateGenerateButtonVisibility(level: PasswordStrengthLevel) {
        generatePasswordButton.visibility = if (level != PasswordStrengthLevel.STRONG) android.view.View.VISIBLE else android.view.View.GONE
    }

    enum class PasswordStrengthLevel { WEAK, MEDIUM, STRONG }
    private fun getPasswordStrength(password: String): Pair<Int, PasswordStrengthLevel> {
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
            score < 40 -> score.coerceAtLeast(0) to PasswordStrengthLevel.WEAK
            score < 70 -> score to PasswordStrengthLevel.MEDIUM
            else -> score.coerceAtMost(100) to PasswordStrengthLevel.STRONG
        }
    }
}