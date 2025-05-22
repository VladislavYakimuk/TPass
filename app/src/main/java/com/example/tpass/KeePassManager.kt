package com.example.tpass

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class KeePassManager(private val context: Context) {
    private var isOpen = false
    private val database = AppDatabase.getDatabase(context)
    private val passwordDao = database.passwordDao()
    private val databaseFile: File
    private var entries: List<KeePassEntry> = emptyList()

    init {
        // Определяем путь к файлу базы данных
        databaseFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Для Android 10+ используем внутреннее хранилище приложения
            File(context.getExternalFilesDir(null), "passwords.kdbx")
        } else {
            // Для более старых версий используем внешнее хранилище
            File(Environment.getExternalStorageDirectory(), "TPass/passwords.kdbx").apply {
                parentFile?.mkdirs()
            }
        }
        Log.d("KeePassManager", "Путь к файлу базы данных: ${databaseFile.absolutePath}")
    }

    fun isDatabaseOpen(): Boolean = isOpen

    suspend fun openDatabase(password: String) {
        try {
            if (!databaseFile.exists()) {
                Log.d("KeePassManager", "Файл базы данных не существует, создаем новый")
                createInitialDatabase()
            }
            
            // Проверяем права доступа
            if (!databaseFile.canRead() || !databaseFile.canWrite()) {
                Log.e("KeePassManager", "Нет прав доступа к файлу базы данных")
                throw SecurityException("No access to database file")
            }
            
            // Проверяем, что файл не пустой
            if (databaseFile.length() == 0L) {
                Log.d("KeePassManager", "Файл базы данных пуст, создаем новую базу")
                createInitialDatabase()
            }
            
            // Читаем записи из файла базы данных
            val fileEntries = readEntriesFromFile()
            
            // Если в файле есть записи, обновляем локальную базу данных
            if (fileEntries.isNotEmpty()) {
                Log.d("KeePassManager", "Найдено ${fileEntries.size} записей в файле, обновляем локальную базу данных")
                passwordDao.deleteAllPasswords()
                fileEntries.forEach { entry ->
                    val passwordEntry = PasswordEntry(
                        serviceName = entry.title,
                        username = entry.username,
                        password = entry.password,
                        url = entry.url,
                        notes = entry.notes
                    )
                    passwordDao.insertPassword(passwordEntry)
                }
            }
            
            // Читаем все записи из локальной базы данных
            entries = passwordDao.getAllPasswords().map { passwordEntry ->
                KeePassEntry(
                    id = passwordEntry.id,
                    title = passwordEntry.serviceName,
                    username = passwordEntry.username,
                    password = passwordEntry.password,
                    url = passwordEntry.url,
                    notes = passwordEntry.notes
                )
            }
            isOpen = true
            Log.d("KeePassManager", "База данных успешно открыта, найдено ${entries.size} записей")
        } catch (e: Exception) {
            Log.e("KeePassManager", "Ошибка при открытии базы данных: ${e.message}", e)
            isOpen = false
            entries = emptyList()
            throw IllegalStateException("Failed to open database: ${e.message}")
        }
    }

    private fun readEntriesFromFile(): List<KeePassEntry> {
        val entries = mutableListOf<KeePassEntry>()
        var currentEntry: KeePassEntry? = null
        var title = ""
        var username = ""
        var password = ""
        var url = ""
        var notes = ""

        try {
            databaseFile.readLines().forEach { line ->
                when {
                    line.startsWith("Entry:") -> {
                        // Сохраняем предыдущую запись, если она есть
                        currentEntry?.let {
                            entries.add(it)
                        }
                        // Начинаем новую запись
                        currentEntry = null
                        title = ""
                        username = ""
                        password = ""
                        url = ""
                        notes = ""
                    }
                    line.trim().startsWith("Title:") -> {
                        title = line.substringAfter("Title:").trim()
                    }
                    line.trim().startsWith("Username:") -> {
                        username = line.substringAfter("Username:").trim()
                    }
                    line.trim().startsWith("Password:") -> {
                        password = line.substringAfter("Password:").trim()
                    }
                    line.trim().startsWith("URL:") -> {
                        url = line.substringAfter("URL:").trim()
                    }
                    line.trim().startsWith("Notes:") -> {
                        notes = line.substringAfter("Notes:").trim()
                    }
                    line.isBlank() && currentEntry == null && title.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty() -> {
                        // Создаем новую запись, когда встречаем пустую строку после заполнения обязательных полей
                        currentEntry = KeePassEntry(
                            title = title,
                            username = username,
                            password = password,
                            url = url,
                            notes = notes
                        )
                    }
                }
            }

            // Добавляем последнюю запись, если она есть
            currentEntry?.let {
                entries.add(it)
            }

            Log.d("KeePassManager", "Прочитано ${entries.size} записей из файла")
            return entries
        } catch (e: Exception) {
            Log.e("KeePassManager", "Ошибка при чтении записей из файла: ${e.message}", e)
            return emptyList()
        }
    }

    suspend fun createInitialDatabase() {
        try {
            // Создаем директорию, если она не существует
            val parentDir = databaseFile.parentFile
            if (parentDir != null) {
                if (!parentDir.exists()) {
                    Log.d("KeePassManager", "Создаем директорию: ${parentDir.absolutePath}")
                    if (!parentDir.mkdirs()) {
                        Log.e("KeePassManager", "Не удалось создать директорию")
                        throw IOException("Failed to create directory")
                    }
                }
                
                // Проверяем права доступа к директории
                if (!parentDir.canWrite()) {
                    Log.e("KeePassManager", "Нет прав на запись в директорию: ${parentDir.absolutePath}")
                    throw SecurityException("No write access to directory")
                }
            } else {
                Log.e("KeePassManager", "Родительская директория не существует")
                throw IOException("Parent directory does not exist")
            }
            
            // Создаем или пересоздаем файл базы данных
            if (databaseFile.exists()) {
                Log.d("KeePassManager", "Файл уже существует, удаляем его")
                if (!databaseFile.delete()) {
                    Log.e("KeePassManager", "Не удалось удалить существующий файл")
                    throw IOException("Failed to delete existing file")
                }
            }
            
            Log.d("KeePassManager", "Создаем новый файл базы данных")
            if (!databaseFile.createNewFile()) {
                Log.e("KeePassManager", "Не удалось создать файл базы данных")
                throw IOException("Failed to create database file")
            }
            
            // Устанавливаем права доступа
            databaseFile.setReadable(true, false)
            databaseFile.setWritable(true, false)
            
            // Проверяем, что файл создан и доступен
            if (!databaseFile.exists() || !databaseFile.canRead() || !databaseFile.canWrite()) {
                Log.e("KeePassManager", "Файл создан, но недоступен")
                throw SecurityException("File created but not accessible")
            }
            
            // Создаем начальные данные
            val initialData = "KeePass Database File\nVersion: 1.0\n\n"
            FileOutputStream(databaseFile).use { output ->
                output.write(initialData.toByteArray())
                output.flush()
            }
            
            Log.d("KeePassManager", "Создана новая пустая база данных")
        } catch (e: Exception) {
            Log.e("KeePassManager", "Ошибка при создании начальной базы данных: ${e.message}", e)
            throw IllegalStateException("Failed to create initial database: ${e.message}")
        }
    }

    fun getEntries(): List<KeePassEntry> {
        if (!isOpen) {
            Log.e("KeePassManager", "Попытка получить записи из закрытой базы данных")
            throw IllegalStateException("Database is not open")
        }
        return entries
    }

    fun getEntryByTitle(title: String): KeePassEntry? {
        if (!isOpen) {
            Log.e("KeePassManager", "Попытка получить запись из закрытой базы данных")
            throw IllegalStateException("Database is not open")
        }
        return entries.find { it.title == title }
    }

    fun closeDatabase() {
        isOpen = false
        entries = emptyList()
        Log.d("KeePassManager", "База данных закрыта")
    }

    suspend fun addEntry(entry: KeePassEntry): KeePassEntry {
        if (!isOpen) throw IllegalStateException("Database is not open")
        val passwordEntry = PasswordEntry(
            serviceName = entry.title,
            username = entry.username,
            password = entry.password,
            url = entry.url,
            notes = entry.notes
        )
        val id = passwordDao.insertPassword(passwordEntry).toInt()
        val newEntry = entry.copy(id = id)
        entries = entries + newEntry
        
        // Сохраняем изменения в файл
        saveDatabaseToFile()
        
        return newEntry
    }

    suspend fun updateEntry(entry: KeePassEntry) {
        if (!isOpen) throw IllegalStateException("Database is not open")
        val updatedEntry = PasswordEntry(
            id = entry.id,
            serviceName = entry.title,
            username = entry.username,
            password = entry.password,
            url = entry.url,
            notes = entry.notes
        )
        val result = passwordDao.updatePassword(updatedEntry)
        if (result == 0) {
            throw IllegalArgumentException("Entry not found")
        }
        entries = entries.map { if (it.id == entry.id) entry else it }
        
        // Сохраняем изменения в файл
        saveDatabaseToFile()
    }

    suspend fun deleteEntry(entry: KeePassEntry) {
        if (!isOpen) throw IllegalStateException("Database is not open")
        val passwordEntry = PasswordEntry(
            id = entry.id,
            serviceName = entry.title,
            username = entry.username,
            password = entry.password,
            url = entry.url,
            notes = entry.notes
        )
        val result = passwordDao.deletePassword(passwordEntry)
        if (result == 0) {
            throw IllegalArgumentException("Entry not found")
        }
        entries = entries.filter { it.id != entry.id }
        
        // Сохраняем изменения в файл
        saveDatabaseToFile()
    }
    
    private suspend fun saveDatabaseToFile() {
        try {
            if (!isOpen) throw IllegalStateException("Database is not open")
            
            // Создаем резервную копию
            val backupFile = File(context.cacheDir, "backup_passwords.kdbx")
            if (databaseFile.exists()) {
                databaseFile.copyTo(backupFile, overwrite = true)
            }
            
            try {
                // Формируем данные для сохранения
                val data = StringBuilder()
                data.append("KeePass Database File\n")
                data.append("Version: 1.0\n\n")
                data.append("Entries:\n")
                
                entries.forEach { entry ->
                    data.append("Entry:\n")
                    data.append("  Title: ${entry.title}\n")
                    data.append("  Username: ${entry.username}\n")
                    data.append("  Password: ${entry.password}\n")
                    if (entry.url.isNotEmpty()) {
                        data.append("  URL: ${entry.url}\n")
                    }
                    if (entry.notes.isNotEmpty()) {
                        data.append("  Notes: ${entry.notes}\n")
                    }
                    data.append("\n")
                }
                
                // Сохраняем данные в файл
                FileOutputStream(databaseFile).use { output ->
                    output.write(data.toString().toByteArray())
                    output.flush()
                }
                
                Log.d("KeePassManager", "База данных успешно сохранена в файл")
            } catch (e: Exception) {
                Log.e("KeePassManager", "Ошибка при сохранении базы данных в файл: ${e.message}", e)
                // Восстанавливаем резервную копию
                if (backupFile.exists()) {
                    backupFile.copyTo(databaseFile, overwrite = true)
                }
                throw e
            } finally {
                // Удаляем резервную копию
                if (backupFile.exists()) {
                    backupFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("KeePassManager", "Ошибка при сохранении базы данных: ${e.message}", e)
            throw IllegalStateException("Failed to save database: ${e.message}")
        }
    }
}

class DatabaseException(message: String) : Exception(message)