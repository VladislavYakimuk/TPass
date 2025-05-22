package com.example.tpass

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.tpass.databinding.ActivityMainBinding
import android.widget.EditText
import android.widget.TextView
import android.app.AlertDialog
import android.view.View
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import android.graphics.PixelFormat
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur
import android.graphics.RenderEffect
import android.graphics.BlurMaskFilter
import android.view.ViewGroup

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appLockManager: AppLockManager
    private lateinit var masterPasswordManager: MasterPasswordManager
    private var blurView: BlurView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Инициализация MainActivity")
        // Добавляем FLAG_SECURE для предотвращения скриншотов
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        appLockManager = AppLockManager(this)
        masterPasswordManager = MasterPasswordManager(this)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        Log.d(TAG, "onCreate: MainActivity успешно инициализирована")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Проверка необходимости блокировки")
        // Восстанавливаем стандартный фон
        window.decorView.setBackgroundColor(android.graphics.Color.WHITE)
        binding.root.visibility = View.VISIBLE
        
        if (appLockManager.shouldLock()) {
            Log.d(TAG, "onResume: Требуется блокировка, показываем диалог ввода пароля")
            showMasterPasswordDialog()
        } else {
            Log.d(TAG, "onResume: Блокировка не требуется, обновляем время активности")
            appLockManager.updateLastActiveTime()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Приложение уходит в фон")
        // Устанавливаем черный фон при уходе в фон
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        binding.root.visibility = View.INVISIBLE
        
        // Обновляем время последней активности
        appLockManager.updateLastActiveTime()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Приложение остановлено")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Приложение уничтожено")
        // Удаляем размытый фон при уничтожении активности
        blurView?.let {
            windowManager.removeView(it)
            blurView = null
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        Log.d(TAG, "onUserInteraction: Обновляем время активности")
        appLockManager.updateLastActiveTime()
    }

    private fun showMasterPasswordDialog() {
        Log.d(TAG, "showMasterPasswordDialog: Показываем диалог ввода мастер-пароля")
        
        // Добавляем размытый фон
        blurView = layoutInflater.inflate(R.layout.overlay_blur_screen, null) as BlurView
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        // Настраиваем BlurView
        val decorView = window.decorView
        val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)
        val windowBackground = decorView.background
        
        blurView?.setupWith(rootView)
            ?.setFrameClearDrawable(windowBackground)
            ?.setBlurRadius(25f)
            ?.setBlurAutoUpdate(true)
        
        windowManager.addView(blurView, params)

        // Показываем диалог с небольшой задержкой
        Handler(Looper.getMainLooper()).postDelayed({
            showPasswordInputDialog()
        }, 100) // Задержка 100мс для плавного появления
    }

    private fun showPasswordInputDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_master_password_input, null)
        val passwordEditText = dialogView.findViewById<EditText>(R.id.masterPasswordEditText)
        val attemptsTextView = dialogView.findViewById<TextView>(R.id.attemptsTextView)
        val cooldownTextView = dialogView.findViewById<TextView>(R.id.cooldownTextView)

        attemptsTextView.visibility = View.GONE
        cooldownTextView.visibility = View.GONE

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.enter_master_password)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setCancelable(false)
            .create()

        dialog.setOnDismissListener {
            // Удаляем размытый фон при закрытии диалога
            blurView?.let {
                windowManager.removeView(it)
                blurView = null
            }
        }

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val password = passwordEditText.text.toString()
                if (masterPasswordManager.verifyMasterPassword(password)) {
                    Log.d(TAG, "showMasterPasswordDialog: Правильный пароль, разблокируем приложение")
                    appLockManager.unlock()
                    dialog.dismiss()
                } else {
                    Log.d(TAG, "showMasterPasswordDialog: Неверный пароль, показываем количество попыток")
                    attemptsTextView.visibility = View.VISIBLE
                    attemptsTextView.text = getString(R.string.attempts_remaining, masterPasswordManager.getRemainingAttempts())
                    
                    if (masterPasswordManager.isInCooldown()) {
                        Log.d(TAG, "showMasterPasswordDialog: Превышен лимит попыток, запускаем таймер ожидания")
                        cooldownTextView.visibility = View.VISIBLE
                        startCooldownTimer(cooldownTextView, dialog)
                    }
                }
            }
        }

        dialog.show()
    }

    private fun startCooldownTimer(cooldownTextView: TextView, dialog: AlertDialog) {
        Log.d(TAG, "startCooldownTimer: Запуск таймера ожидания")
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val timeRemaining = masterPasswordManager.getCooldownTimeRemaining()
                if (timeRemaining > 0) {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemaining) % 60
                    cooldownTextView.text = getString(R.string.cooldown_message, String.format("%d:%02d", minutes, seconds))
                    handler.postDelayed(this, 1000)
                } else {
                    Log.d(TAG, "startCooldownTimer: Таймер ожидания завершен")
                    cooldownTextView.visibility = View.GONE
                    masterPasswordManager.resetAttempts()
                }
            }
        }
        handler.post(runnable)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                navController.navigate(R.id.action_FirstFragment_to_SettingsFragment)
                true
            }
            R.id.action_history -> {
                navController.navigate(R.id.action_FirstFragment_to_HistoryFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
