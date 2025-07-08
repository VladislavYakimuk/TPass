package com.example.tpass

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tpass.databinding.FragmentFirstBinding
import java.io.File
import android.util.Log
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthToken
import androidx.activity.result.ActivityResultLauncher
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import android.text.TextWatcher
import android.text.Editable
import android.widget.TextView
import android.os.Handler
import java.util.concurrent.TimeUnit
import android.widget.ProgressBar
import android.graphics.Color
import com.google.android.material.button.MaterialButton
import android.widget.ImageButton

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val passwordViewModel: PasswordViewModel by viewModels(ownerProducer = { requireActivity() }) {
        PasswordViewModelFactory(requireActivity().application, KeePassManager(requireContext()))
    }

    private lateinit var passwordAdapter: PasswordAdapter
    private lateinit var biometricManager: androidx.biometric.BiometricManager
    private lateinit var masterPasswordManager: MasterPasswordManager
    private lateinit var yandexAuthManager: YandexAuthManager
    private var launcher: ActivityResultLauncher<YandexAuthLoginOptions>? = null
    private var sdk: YandexAuthSdk? = null
    private lateinit var executor: Executor
    private var masterPasswordDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        masterPasswordManager = MasterPasswordManager(requireContext())
        yandexAuthManager = YandexAuthManager(requireContext())
        biometricManager = androidx.biometric.BiometricManager.from(requireContext())
        executor = ContextCompat.getMainExecutor(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupRecyclerView()
        setupAddButton()

        // Откладываем проверку аутентификации до следующего кадра UI
        view.post {
            if (!masterPasswordManager.hasMasterPassword()) {
                showSetMasterPasswordDialog()
            } else if (!passwordViewModel.isDatabaseOpen()) {
                showAuthenticationDialog()
            }
        }

        initializeYandexAuth()
    }

    private fun setupObservers() {
        passwordViewModel.entries.observe(viewLifecycleOwner) { entries ->
            passwordAdapter.updateList(entries)
        }

        passwordViewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupRecyclerView() {
        passwordAdapter = PasswordAdapter(
            onItemClick = { entry: KeePassEntry ->
                val dialog = EditEntryDialog(entry,
                    onSave = { updatedEntry ->
                        passwordViewModel.updateEntry(updatedEntry)
                    },
                    onDelete = { entryToDelete ->
                        passwordViewModel.deleteEntry(entryToDelete)
                    }
                )
                dialog.show(parentFragmentManager, "EditEntryDialog")
            },
            onCopyClick = { entry: KeePassEntry ->
                copyPasswordToClipboard(entry)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = passwordAdapter
        }
    }

    private fun setupAddButton() {
        binding.fabAdd.setOnClickListener {
            showAddPasswordDialog()
        }
    }

    private fun showAddPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_password, null)
        val serviceEditText = dialogView.findViewById<EditText>(R.id.titleEditText)
        val usernameEditText = dialogView.findViewById<EditText>(R.id.usernameEditText)
        val passwordEditText = dialogView.findViewById<EditText>(R.id.passwordEditText)
        val urlEditText = dialogView.findViewById<EditText>(R.id.urlEditText)
        val notesEditText = dialogView.findViewById<EditText>(R.id.notesEditText)
        val generatePasswordButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.generatePasswordButton)
        val passwordStrengthWarning = dialogView.findViewById<TextView>(R.id.passwordStrengthWarning)
        val passwordStrengthBar = dialogView.findViewById<ProgressBar>(R.id.passwordStrengthBar)
        val passwordStrengthLabel = dialogView.findViewById<TextView>(R.id.passwordStrengthLabel)
        val passwordRecommendationsButton = dialogView.findViewById<ImageButton>(R.id.passwordRecommendationsButton)
        var passwordGenerated = false

        // Добавляем обработчик нажатия для скрытия клавиатуры
        dialogView.setOnClickListener {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(dialogView.windowToken, 0)
        }

        generatePasswordButton.setOnClickListener {
            val generatedPassword = PasswordGenerator.generatePassword()
            passwordEditText.setText(generatedPassword)
            passwordEditText.transformationMethod = null
            passwordStrengthWarning.visibility = View.GONE
            passwordGenerated = true
        }

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
            AlertDialog.Builder(requireContext())
                .setTitle("Рекомендации для надежного пароля")
                .setMessage("• Минимум 14 символов\n• Минимум 1 заглавная буква\n• Минимум 1 строчная буква\n• Минимум 1 специальный символ\n• Не используйте подряд идущие одинаковые цифры")
                .setPositiveButton("OK", null)
                .show()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_password)
            .setView(dialogView)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val service = serviceEditText.text.toString()
                val username = usernameEditText.text.toString()
                val password = passwordEditText.text.toString()
                val url = urlEditText.text.toString()
                val notes = notesEditText.text.toString()

                if (service.isBlank() || username.isBlank() || password.isBlank()) {
                    Toast.makeText(requireContext(), R.string.required_fields, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Проверяем надежность только если пароль не был сгенерирован
                if (!passwordGenerated && !validatePassword(password)) {
                    passwordStrengthWarning.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Пароль слишком простой. Придумайте более надежный пароль.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newEntry = KeePassEntry(
                    id = 0, // Room автоматически сгенерирует ID
                    title = service,
                    username = username,
                    password = password,
                    url = url,
                    notes = notes
                )
                passwordViewModel.addEntry(newEntry)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showSetMasterPasswordDialog() {
        Log.d("SetMasterPassword", "Попытка показать диалог установки мастер-пароля")
        Log.d("SetMasterPassword", "isAdded: $isAdded")
        Log.d("SetMasterPassword", "isActivityFinishing: ${requireActivity().isFinishing}")
        Log.d("SetMasterPassword", "hasMasterPassword: ${masterPasswordManager.hasMasterPassword()}")

        if (!isAdded || requireActivity().isFinishing || isDetached) {
            Log.e("SetMasterPassword", "Фрагмент не добавлен или активность завершается")
            return
        }

        try {
            // Закрываем предыдущий диалог, если он существует
            masterPasswordDialog?.dismiss()
            masterPasswordDialog = null

            val dialogView = layoutInflater.inflate(R.layout.dialog_master_password, null)
            val passwordEditText = dialogView.findViewById<EditText>(R.id.masterPasswordInput)

            // Добавляем слушатель изменений текста
            passwordEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    updatePasswordRequirements(s?.toString() ?: "", dialogView)
                }
            })

            Log.d("SetMasterPassword", "Создание диалога")
            AlertDialog.Builder(requireContext())
                .setTitle("Установка мастер-пароля")
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .create()
                .apply {
                    masterPasswordDialog = this
                    setOnShowListener { dialog ->
                        Log.d("SetMasterPassword", "Диалог показан")
                        val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                        positiveButton.setOnClickListener {
                            val password = passwordEditText.text.toString()
                            if (validatePassword(password)) {
                                try {
                                    Log.d("SetMasterPassword", "Установка мастер-пароля")
                                    masterPasswordManager.setMasterPassword(password)
                                    try {
                                        Log.d("SetMasterPassword", "Попытка открыть базу данных")
                                        passwordViewModel.openDatabase(password)
                                        Log.d("SetMasterPassword", "База данных успешно открыта")
                                    } catch (e: Exception) {
                                        Log.e("SetMasterPassword", "Ошибка при открытии базы данных", e)
                                        Toast.makeText(requireContext(), "Ошибка при открытии базы данных", Toast.LENGTH_SHORT).show()
                                        return@setOnClickListener
                                    }
                                    Toast.makeText(requireContext(), "Мастер-пароль установлен", Toast.LENGTH_SHORT).show()
                                    Log.d("SetMasterPassword", "Попытка закрыть диалог")
                                    try {
                                        if (isAdded && !requireActivity().isFinishing && !isDetached) {
                                            dialog.dismiss()
                                            masterPasswordDialog = null
                                            Log.d("SetMasterPassword", "Диалог успешно закрыт")
                                        } else {
                                            Log.d("SetMasterPassword", "Диалог не может быть закрыт из-за состояния фрагмента")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("SetMasterPassword", "Ошибка при закрытии диалога", e)
                                    }
                                } catch (e: Exception) {
                                    Log.e("SetMasterPassword", "Ошибка при установке мастер-пароля", e)
                                    Toast.makeText(requireContext(), "Ошибка при установке мастер-пароля", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Log.d("SetMasterPassword", "Невалидный пароль")
                                Toast.makeText(requireContext(), R.string.invalid_password, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    show()
                }
        } catch (e: Exception) {
            Log.e("SetMasterPassword", "Ошибка при показе диалога установки мастер-пароля", e)
        }
    }

    private fun showMasterPasswordDialog() {
        if (!isAdded || requireActivity().isFinishing) {
            return
        }

        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_master_password_input, null)
            val passwordEditText = dialogView.findViewById<EditText>(R.id.masterPasswordEditText)
            val attemptsTextView = dialogView.findViewById<TextView>(R.id.attemptsTextView)
            val cooldownTextView = dialogView.findViewById<TextView>(R.id.cooldownTextView)

            // Скрываем TextView с попытками при первом показе
            attemptsTextView.visibility = View.GONE
            cooldownTextView.visibility = View.GONE

            val dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.enter_master_password)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .create()

            dialog.setOnShowListener {
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveButton.setOnClickListener {
                    val password = passwordEditText.text.toString()
                    
                    if (masterPasswordManager.isInCooldown()) {
                        Toast.makeText(requireContext(), R.string.too_many_attempts, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    if (masterPasswordManager.verifyMasterPassword(password)) {
                        passwordViewModel.openDatabase(password)
                        dialog.dismiss()
                    } else {
                        // Очищаем поле ввода
                        passwordEditText.text.clear()
                        
                        val remainingAttempts = masterPasswordManager.getRemainingAttempts()
                        
                        // Показываем TextView с попытками
                        attemptsTextView.visibility = View.VISIBLE
                        attemptsTextView.text = getString(R.string.attempts_remaining, remainingAttempts)
                        
                        if (remainingAttempts == 0) {
                            // Скрываем TextView с попытками и показываем таймер
                            attemptsTextView.visibility = View.GONE
                            cooldownTextView.visibility = View.VISIBLE
                            startCooldownTimer(cooldownTextView, dialog)
                        }
                        
                        Toast.makeText(requireContext(), R.string.invalid_password, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Проверяем, не в режиме ли кулдауна
            if (masterPasswordManager.isInCooldown()) {
                cooldownTextView.visibility = View.VISIBLE
                startCooldownTimer(cooldownTextView, dialog)
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e("MasterPassword", "Ошибка при показе диалога мастер-пароля", e)
        }
    }

    private fun startCooldownTimer(cooldownTextView: TextView, dialog: AlertDialog) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val timeRemaining = masterPasswordManager.getCooldownTimeRemaining()
                if (timeRemaining > 0) {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemaining) % 60
                    cooldownTextView.text = getString(R.string.cooldown_message, String.format("%d:%02d", minutes, seconds))
                    handler.postDelayed(this, 1000)
                } else {
                    cooldownTextView.visibility = View.GONE
                    masterPasswordManager.resetAttempts()
                }
            }
        }
        handler.post(runnable)
    }

    private fun validatePassword(password: String): Boolean {
        val hasMinLength = password.length >= 14
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecialChar = password.any { it in "!@#$%^&*" }

        return hasMinLength && hasUpperCase && hasLowerCase && hasDigit && hasSpecialChar
    }

    private fun updatePasswordRequirements(password: String, dialogView: View) {
        val lengthRequirement = dialogView.findViewById<TextView>(R.id.lengthRequirement)
        val uppercaseRequirement = dialogView.findViewById<TextView>(R.id.uppercaseRequirement)
        val lowercaseRequirement = dialogView.findViewById<TextView>(R.id.lowercaseRequirement)
        val digitRequirement = dialogView.findViewById<TextView>(R.id.digitRequirement)
        val specialCharRequirement = dialogView.findViewById<TextView>(R.id.specialCharRequirement)

        val hasMinLength = password.length >= 14
        val hasUpperCase = password.any { it.isUpperCase() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecialChar = password.any { it in "!@#$%^&*" }

        val greenColor = ContextCompat.getColor(requireContext(), R.color.green)
        val redColor = ContextCompat.getColor(requireContext(), R.color.red)

        lengthRequirement.setTextColor(if (hasMinLength) greenColor else redColor)
        uppercaseRequirement.setTextColor(if (hasUpperCase) greenColor else redColor)
        lowercaseRequirement.setTextColor(if (hasLowerCase) greenColor else redColor)
        digitRequirement.setTextColor(if (hasDigit) greenColor else redColor)
        specialCharRequirement.setTextColor(if (hasSpecialChar) greenColor else redColor)
    }

    private fun copyPasswordToClipboard(entry: KeePassEntry) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(Constants.CLIPBOARD_LABEL, entry.password)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.password_copied, Toast.LENGTH_SHORT).show()
    }

    private fun initializeYandexAuth() {
        try {
            // Проверяем наличие интернет-соединения
            if (!isNetworkAvailable()) {
                Toast.makeText(requireContext(), "Отсутствует подключение к интернету", Toast.LENGTH_SHORT).show()
                return
            }

            sdk = YandexAuthSdk.create(YandexAuthOptions(requireContext()))
            launcher = registerForActivityResult(sdk!!.contract) { result -> handleResult(result) }
            
            // Проверяем, есть ли сохраненный токен
            if (!yandexAuthManager.isAuthenticated()) {
                Log.d("YandexAuth", "Нет сохраненного токена, запускаем авторизацию")
                val loginOptions = YandexAuthLoginOptions()
                launcher?.launch(loginOptions)
            } else {
                Log.d("YandexAuth", "Используем сохраненный токен")
                // Проверяем валидность сохраненного токена
                val savedToken = yandexAuthManager.getAuthToken()
                if (savedToken != null) {
                    validateSavedToken(savedToken)
                }
            }
        } catch (e: Exception) {
            Log.e("YandexAuth", "Ошибка инициализации Yandex Auth SDK", e)
            Toast.makeText(requireContext(), "Ошибка инициализации: ${e.message}", Toast.LENGTH_LONG).show()
            // Очищаем токен в случае ошибки
            yandexAuthManager.clearAuthToken()
        }
    }

    private fun validateSavedToken(token: YandexAuthToken) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (sdk?.getJwt(token) != null) {
                    Log.d("YandexAuth", "Сохраненный токен валиден. JWT получен")
                } else {
                    Log.d("YandexAuth", "Токен невалиден, запускаем новую авторизацию")
                    withContext(Dispatchers.Main) {
                        yandexAuthManager.clearAuthToken()
                        val loginOptions = YandexAuthLoginOptions()
                        launcher?.launch(loginOptions)
                    }
                }
            } catch (e: Exception) {
                Log.e("YandexAuth", "Ошибка валидации токена", e)
                withContext(Dispatchers.Main) {
                    yandexAuthManager.clearAuthToken()
                    Toast.makeText(requireContext(), "Ошибка проверки токена: ${e.message}", Toast.LENGTH_SHORT).show()
                    val loginOptions = YandexAuthLoginOptions()
                    launcher?.launch(loginOptions)
                }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun handleResult(result: YandexAuthResult) {
        when (result) {
            is YandexAuthResult.Success -> {
                showProgress(true)
                onSuccessAuth(result.token)
            }
            is YandexAuthResult.Failure -> {
                showProgress(false)
                onProccessError(result.exception)
            }
            YandexAuthResult.Cancelled -> {
                showProgress(false)
                onCancelled()
            }
        }
    }

    private fun onSuccessAuth(token: YandexAuthToken) {
        Log.d("YandexAuth", "Начало обработки успешной авторизации")
        Log.d("YandexAuth", "isAdded: $isAdded")
        Log.d("YandexAuth", "isActivityFinishing: ${requireActivity().isFinishing}")
        Log.d("YandexAuth", "hasMasterPassword: ${masterPasswordManager.hasMasterPassword()}")

        if (sdk == null) {
            showProgress(false)
            Toast.makeText(requireContext(), "Yandex Auth SDK не инициализирован", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jwt = sdk!!.getJwt(token)
                val payload = jwt.substring(jwt.indexOf('.') + 1, jwt.lastIndexOf('.'))
                val decodedPayload = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
                val jsonPayload = org.json.JSONObject(decodedPayload)
                
                val name = jsonPayload.optString("name", "")
                val email = jsonPayload.optString("email", "")
                
                // Сохраняем токен и информацию о пользователе
                yandexAuthManager.saveAuthToken(token)
                yandexAuthManager.saveUserInfo(name, email)
                
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    Toast.makeText(requireContext(), "Авторизация успешна", Toast.LENGTH_SHORT).show()
                    
                    Log.d("YandexAuth", "Проверка наличия мастер-пароля после авторизации")
                    Log.d("YandexAuth", "isAdded: $isAdded")
                    Log.d("YandexAuth", "isActivityFinishing: ${requireActivity().isFinishing}")
                    Log.d("YandexAuth", "hasMasterPassword: ${masterPasswordManager.hasMasterPassword()}")
                    
                    // Проверяем наличие мастер-пароля после успешной авторизации
                    if (!masterPasswordManager.hasMasterPassword()) {
                        Log.d("YandexAuth", "Мастер-пароль не установлен, показываем диалог")
                        showSetMasterPasswordDialog()
                    } else if (!passwordViewModel.isDatabaseOpen()) {
                        Log.d("YandexAuth", "База данных не открыта, показываем диалог аутентификации")
                        showAuthenticationDialog()
                    }
                }
            } catch (e: Exception) {
                Log.e("YandexAuth", "Ошибка при обработке JWT", e)
                withContext(Dispatchers.Main) {
                    showProgress(false)
                    Toast.makeText(requireContext(), "Ошибка получения JWT: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showProgress(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        binding.fabAdd.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun onProccessError(exception: Exception) {
        Toast.makeText(requireContext(), "Ошибка авторизации: ${exception.message}", Toast.LENGTH_SHORT).show()
    }

    private fun onCancelled() {
        Toast.makeText(requireContext(), "Авторизация отменена", Toast.LENGTH_SHORT).show()
    }

    private fun showAuthenticationDialog() {
        // Проверяем, не выполняются ли уже какие-либо транзакции
        if (isAdded && !requireActivity().isFinishing && !childFragmentManager.isDestroyed) {
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                showBiometricPrompt()
            } else {
                showMasterPasswordDialog()
            }
        }
    }

    private fun showBiometricPrompt() {
        // Проверяем состояние фрагмента перед показом биометрического диалога
        if (!isAdded || requireActivity().isFinishing) {
            return
        }

        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setNegativeButtonText(getString(R.string.use_master_password))
                .build()

            val biometricPrompt = BiometricPrompt(requireActivity(), executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (isAdded && !requireActivity().isFinishing) {
                            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                // Пользователь нажал кнопку "Войти по мастер-паролю"
                                view?.post {
                                    showMasterPasswordDialog()
                                }
                            } else {
                                Toast.makeText(requireContext(), errString.toString(), Toast.LENGTH_SHORT).show()
                                view?.post {
                                    showMasterPasswordDialog()
                                }
                            }
                        }
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        if (isAdded && !requireActivity().isFinishing) {
                            val masterPassword = masterPasswordManager.getMasterPassword()
                            if (masterPassword != null) {
                                passwordViewModel.openDatabase(masterPassword)
                            } else {
                                view?.post {
                                    showMasterPasswordDialog()
                                }
                            }
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        if (isAdded && !requireActivity().isFinishing) {
                            Toast.makeText(requireContext(), "Аутентификация не удалась", Toast.LENGTH_SHORT).show()
                        }
                    }
                })

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e("BiometricPrompt", "Ошибка при показе биометрического диалога", e)
            view?.post {
                showMasterPasswordDialog()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            // Закрываем все диалоги при уничтожении представления
            masterPasswordDialog?.dismiss()
            masterPasswordDialog = null
            childFragmentManager.fragments.forEach { fragment ->
                if (fragment is androidx.fragment.app.DialogFragment) {
                    fragment.dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e("FirstFragment", "Ошибка при закрытии диалогов", e)
        }
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        try {
            masterPasswordDialog?.dismiss()
            masterPasswordDialog = null
        } catch (e: Exception) {
            Log.e("FirstFragment", "Ошибка при закрытии диалога при откреплении", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            // Закрываем биометрический диалог при приостановке фрагмента
            childFragmentManager.fragments.forEach { fragment ->
                if (fragment is androidx.biometric.BiometricPrompt.AuthenticationCallback) {
                    (fragment as? androidx.fragment.app.DialogFragment)?.dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e("FirstFragment", "Ошибка при закрытии биометрического диалога", e)
        }
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