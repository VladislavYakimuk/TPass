package com.example.tpass

import android.widget.Toast

object Constants {
    const val TAG = "TPass"
    const val DATABASE_FILENAME = "keepass.kdbx"
    const val DATABASE_PATH = "keepass.kdbx"
    const val SNACKBAR_DURATION = 2000
    
    // Биометрическая аутентификация
    const val BIOMETRIC_KEY_NAME = "biometric_key"
    const val BIOMETRIC_KEY_SIZE = 256
    const val BIOMETRIC_BLOCK_MODE = "CBC"
    const val BIOMETRIC_ENCRYPTION_PADDING = "PKCS7Padding"
    const val BIOMETRIC_ENCRYPTION_ALGORITHM = "AES"
    
    // Сообщения об ошибках
    const val ERROR_INVALID_PASSWORD = "Неверный пароль"
    const val ERROR_DATABASE_NOT_OPEN = "База данных не открыта"
    const val ERROR_ENTRY_NOT_FOUND = "Запись не найдена"
    const val ERROR_SAVING_DATABASE = "Ошибка сохранения базы данных"
    const val ERROR_OPENING_DATABASE = "Ошибка открытия базы данных"
    const val ERROR_CREATING_DATABASE = "Ошибка создания базы данных"
    const val ERROR_ADDING_ENTRY = "Ошибка добавления записи"
    const val ERROR_DELETING_ENTRY = "Ошибка удаления записи"
    const val ERROR_UPDATING_ENTRY = "Ошибка обновления записи"
    
    // Toast сообщения
    const val TOAST_PASSWORD_COPIED = "Пароль скопирован в буфер обмена"
    const val TOAST_ENTRY_ADDED = "Запись добавлена"
    const val TOAST_ENTRY_DELETED = "Запись удалена"
    const val TOAST_ENTRY_UPDATED = "Запись обновлена"
    const val TOAST_DATABASE_SAVED = "База данных сохранена"
    const val TOAST_DATABASE_OPENED = "База данных открыта"
    const val TOAST_DATABASE_CREATED = "База данных создана"
    
    // Toast длительность
    const val TOAST_DURATION_SHORT = Toast.LENGTH_SHORT
    const val TOAST_DURATION_LONG = Toast.LENGTH_LONG

    const val CLIPBOARD_LABEL = "password"
    const val TOAST_DURATION = Toast.LENGTH_SHORT
} 