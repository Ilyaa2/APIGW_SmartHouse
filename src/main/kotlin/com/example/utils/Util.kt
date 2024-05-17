package com.example.utils

import java.security.MessageDigest

fun hashPassword(password: String): String {
    val bytes = password.toByteArray()
    val digest = MessageDigest.getInstance("SHA-256")
    val hashedBytes = digest.digest(bytes)
    return hashedBytes.joinToString("") { "%02x".format(it) }
}

data class Result(val wasError: Boolean, val description: String)

fun parseResponse(input: String): Result {
    return when {
        input.startsWith("error/") -> {
            Result(wasError = true, description = input.removePrefix("error/"))
        }

        input == "ok" -> {
            Result(wasError = false, description = "Success")
        }

        input.startsWith("ok/") -> {
            Result(wasError = false, description = input.removePrefix("ok/"))
        }

        else -> {
            Result(wasError = true, description = "Unknown format")
        }
    }
}

fun isDeviceModeCorrect(input: String): Boolean {
    return when (input) {
        "off" -> true
        "on" -> true
        else -> false
    }
}
