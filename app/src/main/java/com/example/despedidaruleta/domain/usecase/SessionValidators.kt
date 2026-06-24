package com.example.despedidaruleta.domain.usecase

data class SessionFormValidationResult(
    val eventNameError: String? = null,
    val groomNameError: String? = null,
    val codeError: String? = null
) {
    val isValid: Boolean = eventNameError == null && groomNameError == null && codeError == null
}

object SessionValidators {
    fun validateCreateSession(eventName: String, groomName: String): SessionFormValidationResult =
        SessionFormValidationResult(
            eventNameError = validateEventName(eventName),
            groomNameError = validateGroomName(groomName)
        )

    fun validateJoinCode(code: String): SessionFormValidationResult = SessionFormValidationResult(
        codeError = when {
            code.isBlank() -> "Introduce el codigo."
            !Regex("^\\d{6}$").matches(code.trim()) -> "El codigo debe tener exactamente 6 digitos."
            else -> null
        }
    )

    fun normalizeJoinCode(input: String): String = input.filter(Char::isDigit).take(6)

    private fun validateEventName(eventName: String): String? = when {
        eventName.isBlank() -> "Introduce el nombre del evento."
        eventName.trim().length < 3 -> "El evento debe tener al menos 3 caracteres."
        eventName.trim().length > 80 -> "El evento no puede superar 80 caracteres."
        else -> null
    }

    private fun validateGroomName(groomName: String): String? = when {
        groomName.isBlank() -> "Introduce el nombre del novio."
        groomName.trim().length < 2 -> "El nombre debe tener al menos 2 caracteres."
        groomName.trim().length > 60 -> "El nombre no puede superar 60 caracteres."
        else -> null
    }
}
