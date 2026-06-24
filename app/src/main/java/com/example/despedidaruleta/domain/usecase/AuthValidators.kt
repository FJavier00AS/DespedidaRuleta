package com.example.despedidaruleta.domain.usecase

data class AuthValidationResult(
    val emailError: String? = null,
    val passwordError: String? = null,
    val displayNameError: String? = null
) {
    val isValid: Boolean = emailError == null && passwordError == null && displayNameError == null
}

object AuthValidators {
    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateLogin(email: String, password: String): AuthValidationResult = AuthValidationResult(
        emailError = validateEmail(email),
        passwordError = if (password.isBlank()) "Introduce la contrasena." else null
    )

    fun validateRegister(
        email: String,
        password: String,
        displayName: String
    ): AuthValidationResult = AuthValidationResult(
        emailError = validateEmail(email),
        passwordError = validatePassword(password),
        displayNameError = validateDisplayName(displayName)
    )

    fun validateReset(email: String): AuthValidationResult = AuthValidationResult(
        emailError = validateEmail(email)
    )

    private fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Introduce el correo."
        !emailRegex.matches(email.trim()) -> "Introduce un correo valido."
        else -> null
    }

    private fun validatePassword(password: String): String? = when {
        password.isBlank() -> "Introduce la contrasena."
        password.length < 6 -> "La contrasena debe tener al menos 6 caracteres."
        else -> null
    }

    private fun validateDisplayName(displayName: String): String? = when {
        displayName.isBlank() -> "Introduce tu nombre visible."
        displayName.trim().length < 2 -> "El nombre debe tener al menos 2 caracteres."
        displayName.trim().length > 40 -> "El nombre no puede superar 40 caracteres."
        else -> null
    }
}
