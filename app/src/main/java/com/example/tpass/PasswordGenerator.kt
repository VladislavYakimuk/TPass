package com.example.tpass

import kotlin.random.Random

class PasswordGenerator {
    companion object {
        private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val DIGITS = "0123456789"
        private const val SPECIAL = "!?\"';:@#$%^&*()_+-=[]{}|<>,./"
        private const val PASSWORD_LENGTH = 14

        fun generatePassword(): String {
            val password = StringBuilder()
            val random = Random.Default

            // Добавляем как минимум по одному символу каждого типа
            password.append(LOWERCASE[random.nextInt(LOWERCASE.length)])
            password.append(UPPERCASE[random.nextInt(UPPERCASE.length)])
            password.append(DIGITS[random.nextInt(DIGITS.length)])
            password.append(SPECIAL[random.nextInt(SPECIAL.length)])

            // Добавляем оставшиеся символы
            val allChars = LOWERCASE + UPPERCASE + DIGITS + SPECIAL
            for (i in password.length until PASSWORD_LENGTH) {
                password.append(allChars[random.nextInt(allChars.length)])
            }

            // Перемешиваем символы в пароле
            return password.toString().toCharArray()
                .apply { shuffle(random) }
                .joinToString("")
        }
    }
} 