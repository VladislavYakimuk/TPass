package com.example.tpass

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.example.tpass.databinding.FragmentSettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var yandexAuthManager: YandexAuthManager
    private lateinit var syncRepository: SyncRepository
    private lateinit var passwordViewModel: PasswordViewModel
    private lateinit var masterPasswordManager: MasterPasswordManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        yandexAuthManager = YandexAuthManager(requireContext())
        syncRepository = SyncRepository(requireContext())
        masterPasswordManager = MasterPasswordManager(requireContext())
        
        // Используем общую область видимости с FirstFragment
        passwordViewModel = ViewModelProvider(requireActivity(), 
            PasswordViewModelFactory(requireActivity().application, KeePassManager(requireContext()))
        ).get(PasswordViewModel::class.java)
        
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUserInfo()
        setupSyncButtons()
        binding.expiryIntervalButton?.setOnClickListener {
            showExpiryIntervalDialog()
        }
    }

    private fun setupSyncButtons() {
        binding.syncButton.setOnClickListener {
            if (!masterPasswordManager.hasMasterPassword()) {
                Toast.makeText(requireContext(), R.string.master_password_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (yandexAuthManager.isAuthenticated()) {
                showMasterPasswordDialog { masterPassword ->
                    CoroutineScope(Dispatchers.Main).launch {
                        val token = yandexAuthManager.getAuthToken()
                        if (token != null) {
                            binding.syncStatusTextView.text = getString(R.string.syncing)
                            val success = withContext(Dispatchers.IO) {
                                syncRepository.uploadDatabase(token, masterPassword)
                            }
                            if (success) {
                                binding.syncStatusTextView.text = getString(R.string.sync_success)
                                Toast.makeText(requireContext(), R.string.sync_success, Toast.LENGTH_SHORT).show()
                            } else {
                                binding.syncStatusTextView.text = getString(R.string.sync_error)
                                Toast.makeText(requireContext(), R.string.sync_error, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            binding.syncStatusTextView.text = getString(R.string.auth_error)
                            Toast.makeText(requireContext(), R.string.auth_error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                binding.syncStatusTextView.text = getString(R.string.not_authorized)
                Toast.makeText(requireContext(), R.string.not_authorized, Toast.LENGTH_SHORT).show()
            }
        }

        binding.downloadButton.setOnClickListener {
            if (!masterPasswordManager.hasMasterPassword()) {
                Toast.makeText(requireContext(), R.string.master_password_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (yandexAuthManager.isAuthenticated()) {
                showMasterPasswordDialog { masterPassword ->
                    CoroutineScope(Dispatchers.Main).launch {
                        val token = yandexAuthManager.getAuthToken()
                        if (token != null) {
                            binding.syncStatusTextView.text = getString(R.string.downloading)
                            val success = withContext(Dispatchers.IO) {
                                syncRepository.downloadDatabase(token, masterPassword)
                            }
                            if (success) {
                                binding.syncStatusTextView.text = getString(R.string.download_success)
                                Toast.makeText(requireContext(), R.string.download_success, Toast.LENGTH_SHORT).show()
                                // Открываем базу данных перед загрузкой записей
                                passwordViewModel.openDatabase(masterPassword)
                            } else {
                                binding.syncStatusTextView.text = getString(R.string.download_error)
                                Toast.makeText(requireContext(), R.string.download_error, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            binding.syncStatusTextView.text = getString(R.string.auth_error)
                            Toast.makeText(requireContext(), R.string.auth_error, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                binding.syncStatusTextView.text = getString(R.string.not_authorized)
                Toast.makeText(requireContext(), R.string.not_authorized, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showMasterPasswordDialog(onPasswordEntered: (String) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_master_password_sync, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.masterPasswordInput)
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.enter_master_password)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val password = passwordInput.text.toString()
                if (masterPasswordManager.verifyMasterPassword(password)) {
                    onPasswordEntered(password)
                } else {
                    Toast.makeText(requireContext(), R.string.master_password_incorrect, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateUserInfo() {
        if (yandexAuthManager.isAuthenticated()) {
            val userName = yandexAuthManager.getUserName()
            val userEmail = yandexAuthManager.getUserEmail()
            
            binding.userNameTextView.text = userName ?: getString(R.string.no_name)
            binding.userEmailTextView.text = userEmail ?: getString(R.string.no_email)
        } else {
            binding.userNameTextView.text = getString(R.string.not_authorized)
            binding.userEmailTextView.text = getString(R.string.login_yandex)
        }
    }

    private fun getPasswordExpiryInterval(): Long {
        val prefs = requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        return prefs.getLong("password_expiry_interval", 7 * 24 * 60 * 60 * 1000L) // по умолчанию 7 дней
    }

    private fun setPasswordExpiryInterval(intervalMillis: Long) {
        val prefs = requireContext().getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putLong("password_expiry_interval", intervalMillis).apply()
    }

    private fun showExpiryIntervalDialog() {
        val intervals = arrayOf("1 минута", "30 дней", "60 дней", "90 дней")
        val values = arrayOf(
            1L * 60 * 1000, // 1 минута
            30L * 24 * 60 * 60 * 1000, // 30 дней
            60L * 24 * 60 * 60 * 1000, // 60 дней
            90L * 24 * 60 * 60 * 1000  // 90 дней
        )
        val current = values.indexOf(getPasswordExpiryInterval())
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Через сколько дней пароль устареет?")
            .setSingleChoiceItems(intervals, current) { dialog, which ->
                setPasswordExpiryInterval(values[which])
                Toast.makeText(requireContext(), "Срок действия пароля: ${intervals[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 