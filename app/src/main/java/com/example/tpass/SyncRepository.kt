package com.example.tpass

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.yandex.authsdk.YandexAuthToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class SyncRepository(private val context: Context) {
    private val databaseFile: File
    private val keePassManager = KeePassManager(context)
    private val database = AppDatabase.getDatabase(context)
    private val passwordDao = database.passwordDao()
    private val masterPasswordManager = MasterPasswordManager(context)

    init {
        // Используем тот же путь, что и в KeePassManager
        databaseFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Для Android 10+ используем внутреннее хранилище приложения
            File(context.getExternalFilesDir(null), "passwords.kdbx")
        } else {
            // Для более старых версий используем внешнее хранилище
            File(Environment.getExternalStorageDirectory(), "TPass/passwords.kdbx").apply {
                parentFile?.mkdirs()
            }
        }
        Log.d("SyncRepository", "Путь к файлу базы данных: ${databaseFile.absolutePath}")
    }

    private suspend fun ensureDatabaseFileExists(): Boolean {
        if (!databaseFile.exists()) {
            try {
                // Создаем новую базу данных
                keePassManager.createInitialDatabase()
                Log.d("SyncRepository", "Создана новая база данных")
                return true
            } catch (e: Exception) {
                Log.e("SyncRepository", "Ошибка при создании базы данных", e)
                return false
            }
        }
        
        // Проверяем, что файл не пустой
        if (databaseFile.length() == 0L) {
            try {
                // Создаем новую базу данных
                keePassManager.createInitialDatabase()
                Log.d("SyncRepository", "Создана новая база данных")
                return true
            } catch (e: Exception) {
                Log.e("SyncRepository", "Ошибка при создании базы данных", e)
                return false
            }
        }
        
        return true
    }

    suspend fun downloadDatabase(token: YandexAuthToken, masterPassword: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d("SyncRepository", "Начало загрузки базы данных с Яндекс Диска")
            
            // Сначала проверяем существование файла
            val checkUrl = URL("https://cloud-api.yandex.net/v1/disk/resources?path=/passwords.kdbx")
            val checkConnection = checkUrl.openConnection() as HttpURLConnection
            checkConnection.setRequestProperty("Authorization", "OAuth ${token.value}")
            checkConnection.setRequestProperty("Accept", "application/json")
            checkConnection.requestMethod = "GET"

            if (checkConnection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("SyncRepository", "Файл не найден на Яндекс Диске")
                return@withContext false
            }

            Log.d("SyncRepository", "Файл найден на Яндекс Диске, получаем ссылку для скачивания")

            val url = URL("https://cloud-api.yandex.net/v1/disk/resources/download?path=/passwords.kdbx")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "OAuth ${token.value}")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.requestMethod = "GET"

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                val downloadUrl = jsonResponse.getString("href")

                Log.d("SyncRepository", "Получена ссылка для скачивания, начинаем загрузку файла")

                val downloadConnection = URL(downloadUrl).openConnection() as HttpURLConnection
                downloadConnection.setRequestProperty("Authorization", "OAuth ${token.value}")
                downloadConnection.setRequestProperty("Accept", "*/*")
                downloadConnection.setRequestProperty("User-Agent", "Mozilla/5.0")
                downloadConnection.setRequestProperty("Connection", "keep-alive")
                downloadConnection.setRequestProperty("Cache-Control", "no-cache")
                downloadConnection.requestMethod = "GET"
                downloadConnection.connectTimeout = 30000
                downloadConnection.readTimeout = 30000

                if (downloadConnection.responseCode == HttpURLConnection.HTTP_OK) {
                    // Создаем временный файл для загрузки
                    val tempFile = File(context.cacheDir, "temp_passwords.kdbx")
                    try {
                        Log.d("SyncRepository", "Начинаем сохранение файла во временный файл")
                        
                        // Создаем буфер для чтения данных
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        downloadConnection.inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    output.write(buffer, 0, bytesRead)
                                    totalBytesRead += bytesRead
                                }
                                output.flush()
                            }
                        }
                        
                        Log.d("SyncRepository", "Файл успешно сохранен во временный файл, размер: $totalBytesRead байт")

                        // Проверяем, что временный файл не пустой
                        if (totalBytesRead == 0L) {
                            Log.e("SyncRepository", "Загруженный файл пуст")
                            return@withContext false
                        }

                        // Создаем резервную копию текущей базы данных
                        val backupFile = File(context.cacheDir, "backup_passwords.kdbx")
                        if (databaseFile.exists()) {
                            databaseFile.copyTo(backupFile, overwrite = true)
                        }

                        try {
                            // Копируем временный файл в основной
                            tempFile.copyTo(databaseFile, overwrite = true)

                            // Пробуем открыть базу данных
                            keePassManager.openDatabase(masterPassword)
                            val entries = keePassManager.getEntries()
                            keePassManager.closeDatabase()

                            if (entries.isEmpty()) {
                                Log.e("SyncRepository", "Загруженная база данных не содержит записей")
                                // Восстанавливаем резервную копию
                                if (backupFile.exists()) {
                                    backupFile.copyTo(databaseFile, overwrite = true)
                                }
                                return@withContext false
                            }

                            Log.d("SyncRepository", "Найдено ${entries.size} записей в загруженной базе данных")

                            // Обновляем локальную базу данных
                            try {
                                Log.d("SyncRepository", "Открываем основную базу данных")
                                keePassManager.openDatabase(masterPassword)
                                
                                Log.d("SyncRepository", "Получение записей из базы данных")
                                val loadedEntries = keePassManager.getEntries()
                                
                                if (loadedEntries.isNotEmpty()) {
                                    Log.d("SyncRepository", "Найдено ${loadedEntries.size} записей, обновляем локальную базу данных")
                                    passwordDao.deleteAllPasswords()
                                    
                                    loadedEntries.forEach { entry ->
                                        val passwordEntry = PasswordEntry(
                                            serviceName = entry.title,
                                            username = entry.username,
                                            password = entry.password,
                                            url = entry.url,
                                            notes = entry.notes
                                        )
                                        passwordDao.insertPassword(passwordEntry)
                                    }
                                    
                                    Log.d("SyncRepository", "Локальная база данных успешно обновлена")
                                    return@withContext true
                                } else {
                                    Log.e("SyncRepository", "База данных пуста после копирования")
                                    keePassManager.closeDatabase()
                                    // Восстанавливаем резервную копию
                                    if (backupFile.exists()) {
                                        backupFile.copyTo(databaseFile, overwrite = true)
                                    }
                                    return@withContext false
                                }
                            } catch (e: Exception) {
                                Log.e("SyncRepository", "Ошибка при обновлении локальной базы данных: ${e.message}", e)
                                keePassManager.closeDatabase()
                                // Восстанавливаем резервную копию
                                if (backupFile.exists()) {
                                    backupFile.copyTo(databaseFile, overwrite = true)
                                }
                                return@withContext false
                            }
                        } catch (e: Exception) {
                            Log.e("SyncRepository", "Ошибка при проверке загруженной базы данных: ${e.message}", e)
                            // Восстанавливаем резервную копию
                            if (backupFile.exists()) {
                                backupFile.copyTo(databaseFile, overwrite = true)
                            }
                            return@withContext false
                        } finally {
                            // Удаляем резервную копию
                            if (backupFile.exists()) {
                                backupFile.delete()
                            }
                        }
                    } finally {
                        // Удаляем временный файл
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    }
                } else {
                    Log.e("SyncRepository", "Ошибка загрузки файла: ${downloadConnection.responseCode}")
                    Log.e("SyncRepository", "Ответ сервера: ${downloadConnection.errorStream?.bufferedReader()?.readText()}")
                    return@withContext false
                }
            } else {
                Log.e("SyncRepository", "Ошибка получения ссылки на скачивание: ${connection.responseCode}")
                Log.e("SyncRepository", "Ответ сервера: ${connection.errorStream?.bufferedReader()?.readText()}")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "Ошибка при синхронизации", e)
            return@withContext false
        }
    }

    suspend fun uploadDatabase(token: YandexAuthToken, masterPassword: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!ensureDatabaseFileExists()) {
                Log.e("SyncRepository", "Не удалось создать файл базы данных")
                return@withContext false
            }

            // Открываем базу данных перед загрузкой
            try {
                keePassManager.openDatabase(masterPassword)
            } catch (e: Exception) {
                Log.e("SyncRepository", "Ошибка при открытии базы данных: ${e.message}", e)
                return@withContext false
            }

            // Сначала проверяем существование файла
            val checkUrl = URL("https://cloud-api.yandex.net/v1/disk/resources?path=/passwords.kdbx")
            val checkConnection = checkUrl.openConnection() as HttpURLConnection
            checkConnection.setRequestProperty("Authorization", "OAuth ${token.value}")
            checkConnection.setRequestProperty("Accept", "application/json")
            checkConnection.requestMethod = "GET"

            // Получаем URL для загрузки
            val uploadUrl = URL("https://cloud-api.yandex.net/v1/disk/resources/upload?path=/passwords.kdbx&overwrite=true")
            val connection = uploadUrl.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "OAuth ${token.value}")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.requestMethod = "GET"

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                val href = jsonResponse.getString("href")

                // Загружаем файл
                val uploadConnection = URL(href).openConnection() as HttpURLConnection
                uploadConnection.setRequestProperty("Authorization", "OAuth ${token.value}")
                uploadConnection.setRequestProperty("Accept", "application/json")
                uploadConnection.requestMethod = "PUT"
                uploadConnection.doOutput = true

                databaseFile.inputStream().use { input ->
                    uploadConnection.outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                if (uploadConnection.responseCode == HttpURLConnection.HTTP_CREATED || 
                    uploadConnection.responseCode == HttpURLConnection.HTTP_ACCEPTED) {
                    true
                } else {
                    Log.e("SyncRepository", "Ошибка загрузки файла: ${uploadConnection.responseCode}")
                    Log.e("SyncRepository", "Ответ сервера: ${uploadConnection.errorStream?.bufferedReader()?.readText()}")
                    false
                }
            } else {
                Log.e("SyncRepository", "Ошибка получения ссылки для загрузки: ${connection.responseCode}")
                Log.e("SyncRepository", "Ответ сервера: ${connection.errorStream?.bufferedReader()?.readText()}")
                false
            }
        } catch (e: Exception) {
            Log.e("SyncRepository", "Ошибка при загрузке файла", e)
            false
        } finally {
            try {
                keePassManager.closeDatabase()
            } catch (e: Exception) {
                Log.e("SyncRepository", "Ошибка при закрытии базы данных", e)
            }
        }
    }
} 