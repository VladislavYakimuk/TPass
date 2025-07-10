package com.example.tpass

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class EditEntryDialogManager(
    private val context: Context,
    private val entry: KeePassEntry,
    private val onSave: (KeePassEntry) -> Unit,
    private val onDelete: (KeePassEntry) -> Unit
) {

    fun showEditDialog() {
        when (entry.category) {
            "PIN-коды" -> showEditPinDialog()
            "Wi-Fi" -> showEditWifiDialog()
            "Аккаунты" -> showEditAccountDialog()
            else -> showEditAccountDialog() // По умолчанию показываем диалог аккаунта
        }
    }

    private fun showEditPinDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_pin, null)
        
        val titleEditText = dialogView.findViewById<TextInputEditText>(R.id.titleEditText)
        val pinEditText = dialogView.findViewById<TextInputEditText>(R.id.pinEditText)
        val notesEditText = dialogView.findViewById<TextInputEditText>(R.id.notesEditText)
        val tagsEditText = dialogView.findViewById<TextInputEditText>(R.id.tagsEditText)

        // Заполняем поля текущими данными
        titleEditText.setText(entry.title)
        pinEditText.setText(entry.password)
        notesEditText.setText(entry.notes)
        tagsEditText.setText(entry.tags.joinToString(", "))

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.save), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .setNeutralButton(context.getString(R.string.delete)) { _, _ ->
                onDelete(entry)
            }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val title = titleEditText.text.toString()
                val pin = pinEditText.text.toString()
                val notes = notesEditText.text.toString()
                val tags = tagsEditText.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (title.isBlank() || pin.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.fill_required_fields), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val updatedEntry = entry.copy(
                    title = title,
                    password = pin,
                    notes = notes,
                    tags = tags,
                    lastUpdated = if (pin != entry.password) System.currentTimeMillis() else entry.lastUpdated
                )
                
                onSave(updatedEntry)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showEditWifiDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_wifi, null)
        
        val networkNameEditText = dialogView.findViewById<TextInputEditText>(R.id.networkNameEditText)
        val passwordEditText = dialogView.findViewById<TextInputEditText>(R.id.passwordEditText)
        val notesEditText = dialogView.findViewById<TextInputEditText>(R.id.notesEditText)
        val tagsEditText = dialogView.findViewById<TextInputEditText>(R.id.tagsEditText)
        val generatePasswordButton = dialogView.findViewById<MaterialButton>(R.id.generatePasswordButton)
        val passwordStrengthWarning = dialogView.findViewById<TextView>(R.id.passwordStrengthWarning)
        val passwordStrengthBar = dialogView.findViewById<ProgressBar>(R.id.passwordStrengthBar)
        val passwordStrengthLabel = dialogView.findViewById<TextView>(R.id.passwordStrengthLabel)
        val passwordRecommendationsButton = dialogView.findViewById<ImageButton>(R.id.passwordRecommendationsButton)
        
        var passwordGenerated = false

        // Заполняем поля текущими данными
        networkNameEditText.setText(entry.title)
        passwordEditText.setText(entry.password)
        // Инициализация полосы и текста надежности пароля
        updatePasswordStrengthUI(entry.password, passwordStrengthBar, passwordStrengthLabel)
        // Показываем предупреждение, если пароль слабый
        val (score, level) = getPasswordStrength(entry.password)
        if (level == PasswordStrengthLevel.WEAK) {
            passwordStrengthWarning.visibility = View.VISIBLE
        } else {
            passwordStrengthWarning.visibility = View.GONE
        }
        notesEditText.setText(entry.notes)
        tagsEditText.setText(entry.tags.joinToString(", "))

        // Настройка генерации пароля
        generatePasswordButton.setOnClickListener {
            val generatedPassword = PasswordGenerator.generatePassword()
            passwordEditText.setText(generatedPassword)
            passwordStrengthWarning.visibility = View.GONE
            passwordGenerated = true
        }

        // Настройка проверки надежности пароля
        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                passwordGenerated = false
                val password = s?.toString() ?: ""
                updatePasswordStrengthUI(password, passwordStrengthBar, passwordStrengthLabel)
            }
        })

        passwordRecommendationsButton.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.password_recommendations))
                .setMessage(context.getString(R.string.password_recommendations_text))
                .setPositiveButton("OK", null)
                .show()
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.save), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .setNeutralButton(context.getString(R.string.delete)) { _, _ ->
                onDelete(entry)
            }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val networkName = networkNameEditText.text.toString()
                val password = passwordEditText.text.toString()
                val notes = notesEditText.text.toString()
                val tags = tagsEditText.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (networkName.isBlank() || password.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.fill_required_fields), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Показываем предупреждение о слабом пароле, но не блокируем сохранение
                if (!passwordGenerated && !validatePassword(password)) {
                    passwordStrengthWarning.visibility = View.VISIBLE
                } else {
                    passwordStrengthWarning.visibility = View.GONE
                }

                val updatedEntry = entry.copy(
                    title = networkName,
                    password = password,
                    notes = notes,
                    tags = tags,
                    lastUpdated = if (password != entry.password) System.currentTimeMillis() else entry.lastUpdated
                )
                
                onSave(updatedEntry)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showEditAccountDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_account, null)
        
        val titleEditText = dialogView.findViewById<TextInputEditText>(R.id.titleEditText)
        val usernameEditText = dialogView.findViewById<TextInputEditText>(R.id.usernameEditText)
        val passwordEditText = dialogView.findViewById<TextInputEditText>(R.id.passwordEditText)
        val urlEditText = dialogView.findViewById<TextInputEditText>(R.id.urlEditText)
        val notesEditText = dialogView.findViewById<TextInputEditText>(R.id.notesEditText)
        val tagsEditText = dialogView.findViewById<TextInputEditText>(R.id.tagsEditText)
        val generatePasswordButton = dialogView.findViewById<MaterialButton>(R.id.generatePasswordButton)
        val passwordStrengthWarning = dialogView.findViewById<TextView>(R.id.passwordStrengthWarning)
        val passwordStrengthBar = dialogView.findViewById<ProgressBar>(R.id.passwordStrengthBar)
        val passwordStrengthLabel = dialogView.findViewById<TextView>(R.id.passwordStrengthLabel)
        val passwordRecommendationsButton = dialogView.findViewById<ImageButton>(R.id.passwordRecommendationsButton)
        
        var passwordGenerated = false

        // Заполняем поля текущими данными
        titleEditText.setText(entry.title)
        usernameEditText.setText(entry.username)
        passwordEditText.setText(entry.password)
        // Инициализация полосы и текста надежности пароля
        updatePasswordStrengthUI(entry.password, passwordStrengthBar, passwordStrengthLabel)
        // Показываем предупреждение, если пароль слабый
        val (score, level) = getPasswordStrength(entry.password)
        if (level == PasswordStrengthLevel.WEAK) {
            passwordStrengthWarning.visibility = View.VISIBLE
        } else {
            passwordStrengthWarning.visibility = View.GONE
        }
        urlEditText.setText(entry.url)
        notesEditText.setText(entry.notes)
        tagsEditText.setText(entry.tags.joinToString(", "))

        // Настройка генерации пароля
        generatePasswordButton.setOnClickListener {
            val generatedPassword = PasswordGenerator.generatePassword()
            passwordEditText.setText(generatedPassword)
            passwordStrengthWarning.visibility = View.GONE
            passwordGenerated = true
        }

        // Настройка проверки надежности пароля
        passwordEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                passwordGenerated = false
                val password = s?.toString() ?: ""
                updatePasswordStrengthUI(password, passwordStrengthBar, passwordStrengthLabel)
            }
        })

        passwordRecommendationsButton.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.password_recommendations))
                .setMessage(context.getString(R.string.password_recommendations_text))
                .setPositiveButton("OK", null)
                .show()
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.save), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .setNeutralButton(context.getString(R.string.delete)) { _, _ ->
                onDelete(entry)
            }
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val title = titleEditText.text.toString()
                val username = usernameEditText.text.toString()
                val password = passwordEditText.text.toString()
                val url = urlEditText.text.toString()
                val notes = notesEditText.text.toString()
                val tags = tagsEditText.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

                if (title.isBlank() || username.isBlank() || password.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.fill_required_fields), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Показываем предупреждение о слабом пароле, но не блокируем сохранение
                if (!passwordGenerated && !validatePassword(password)) {
                    passwordStrengthWarning.visibility = View.VISIBLE
                } else {
                    passwordStrengthWarning.visibility = View.GONE
                }

                val updatedEntry = entry.copy(
                    title = title,
                    username = username,
                    password = password,
                    url = url,
                    notes = notes,
                    tags = tags,
                    lastUpdated = if (password != entry.password) System.currentTimeMillis() else entry.lastUpdated
                )
                
                onSave(updatedEntry)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun validatePassword(password: String): Boolean {
        val hasMinLength = password.length >= 14
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecialChar = password.any { it in "!@#$%^&*" }

        return hasMinLength && hasUpperCase && hasLowerCase && hasDigit && hasSpecialChar
    }

    private fun updatePasswordStrengthUI(password: String, bar: ProgressBar, label: TextView) {
        val (score, level) = getPasswordStrength(password)
        bar.progress = score
        when (level) {
            PasswordStrengthLevel.WEAK -> {
                label.text = "Слабый"
                label.setTextColor(Color.parseColor("#FF5252"))
            }
            PasswordStrengthLevel.MEDIUM -> {
                label.text = "Средний"
                label.setTextColor(Color.parseColor("#FFEB3B"))
            }
            PasswordStrengthLevel.STRONG -> {
                label.text = "Сильный"
                label.setTextColor(Color.parseColor("#4CAF50"))
            }
        }
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