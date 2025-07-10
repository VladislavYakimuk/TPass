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

class AddEntryDialogManager(
    private val context: Context,
    private val onEntryAdded: (KeePassEntry) -> Unit,
    private val isDatabaseOpen: () -> Boolean = { true }
) {

    fun showEntryTypeSelectionDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_entry_type_selection, null)
        
        val accountCard = dialogView.findViewById<View>(R.id.accountCard)
        val wifiCard = dialogView.findViewById<View>(R.id.wifiCard)
        val pinCard = dialogView.findViewById<View>(R.id.pinCard)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        accountCard.setOnClickListener {
            dialog.dismiss()
            showAddAccountDialog()
        }

        wifiCard.setOnClickListener {
            dialog.dismiss()
            showAddWifiDialog()
        }

        pinCard.setOnClickListener {
            dialog.dismiss()
            showAddPinDialog()
        }

        dialog.show()
    }

    private fun showAddAccountDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_account, null)
        
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

                // Показываем предупреждение о слабом пароле, но не блокируем создание
                if (!passwordGenerated && !validatePassword(password)) {
                    passwordStrengthWarning.visibility = View.VISIBLE
                } else {
                    passwordStrengthWarning.visibility = View.GONE
                }

                if (!isDatabaseOpen()) {
                    Toast.makeText(context, context.getString(R.string.database_not_open), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newEntry = KeePassEntry(
                    id = 0,
                    title = title,
                    username = username,
                    password = password,
                    url = url,
                    notes = notes,
                    category = "Аккаунты",
                    tags = tags
                )
                
                onEntryAdded(newEntry)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAddWifiDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_wifi, null)
        
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

                // Показываем предупреждение о слабом пароле, но не блокируем создание
                if (!passwordGenerated && !validatePassword(password)) {
                    passwordStrengthWarning.visibility = View.VISIBLE
                } else {
                    passwordStrengthWarning.visibility = View.GONE
                }

                if (!isDatabaseOpen()) {
                    Toast.makeText(context, context.getString(R.string.database_not_open), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newEntry = KeePassEntry(
                    id = 0,
                    title = networkName,
                    username = "", // Для Wi-Fi username не нужен
                    password = password,
                    url = "",
                    notes = notes,
                    category = "Wi-Fi",
                    tags = tags
                )
                
                onEntryAdded(newEntry)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAddPinDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_pin, null)
        
        val titleEditText = dialogView.findViewById<TextInputEditText>(R.id.titleEditText)
        val pinEditText = dialogView.findViewById<TextInputEditText>(R.id.pinEditText)
        val notesEditText = dialogView.findViewById<TextInputEditText>(R.id.notesEditText)
        val tagsEditText = dialogView.findViewById<TextInputEditText>(R.id.tagsEditText)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.save), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
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

                if (!isDatabaseOpen()) {
                    Toast.makeText(context, context.getString(R.string.database_not_open), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newEntry = KeePassEntry(
                    id = 0,
                    title = title,
                    username = "", // Для PIN-кода username не нужен
                    password = pin,
                    url = "",
                    notes = notes,
                    category = "PIN-коды",
                    tags = tags
                )
                
                onEntryAdded(newEntry)
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
        if (password.length >= 14) score += 20
        if (password.length >= 16) score += 20
        if (password.any { it.isUpperCase() }) score += 15
        if (password.any { it.isLowerCase() }) score += 10
        if (password.any { it.isDigit() }) score += 10
        if (password.any { it in "!@#\$%^&*()_+-=[]{}|;:'\",.<>?/" }) score += 15
        if (Regex("(\\d)\\1").containsMatchIn(password)) score -= 15 // подряд идущие одинаковые цифры
        if (score < 40) return score.coerceAtLeast(0) to PasswordStrengthLevel.WEAK
        if (score < 70) return score to PasswordStrengthLevel.MEDIUM
        return score.coerceAtMost(100) to PasswordStrengthLevel.STRONG
    }
} 