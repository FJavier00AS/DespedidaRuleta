package com.example.despedidaruleta.domain.usecase

import com.example.despedidaruleta.domain.model.SessionSettingsDraft
import java.net.URI
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SessionSettingsValidationResult(
    val eventNameError: String? = null,
    val groomNameError: String? = null,
    val photoUrlError: String? = null,
    val startsAtError: String? = null,
    val endsAtError: String? = null,
    val timeZoneError: String? = null,
    val draft: SessionSettingsDraft? = null
) {
    val isValid: Boolean = eventNameError == null &&
        groomNameError == null &&
        photoUrlError == null &&
        startsAtError == null &&
        endsAtError == null &&
        timeZoneError == null &&
        draft != null
}

object SessionSettingsValidators {
    private const val DISPLAY_PATTERN = "dd/MM/yyyy HH:mm"

    fun validate(
        eventName: String,
        groomName: String,
        rawPhotoUrl: String,
        startsAtText: String,
        endsAtText: String,
        timeZone: String
    ): SessionSettingsValidationResult {
        val eventNameError = validateEventName(eventName)
        val groomNameError = validateGroomName(groomName)
        val normalizedUrlResult = normalizePublicImageUrl(rawPhotoUrl)
        val timeZoneError = validateTimeZone(timeZone)
        val startsAtResult = parseDate(startsAtText, timeZone)
        val endsAtResult = parseDate(endsAtText, timeZone)
        val rangeError = when {
            startsAtResult.value != null && endsAtResult.value != null && startsAtResult.value >= endsAtResult.value ->
                "La fecha de fin debe ser posterior a la de inicio."
            else -> null
        }

        val effectiveEndsAtError = endsAtResult.error ?: rangeError
        val draft = if (
            eventNameError == null &&
            groomNameError == null &&
            normalizedUrlResult.error == null &&
            startsAtResult.error == null &&
            effectiveEndsAtError == null &&
            timeZoneError == null
        ) {
            SessionSettingsDraft(
                eventName = eventName.trim(),
                groomName = groomName.trim(),
                groomPhotoUrl = normalizedUrlResult.value,
                startsAtMillis = startsAtResult.value,
                endsAtMillis = endsAtResult.value,
                timeZone = timeZone.trim()
            )
        } else {
            null
        }

        return SessionSettingsValidationResult(
            eventNameError = eventNameError,
            groomNameError = groomNameError,
            photoUrlError = normalizedUrlResult.error,
            startsAtError = startsAtResult.error,
            endsAtError = effectiveEndsAtError,
            timeZoneError = timeZoneError,
            draft = draft
        )
    }

    fun formatDate(millis: Long?, timeZone: String): String {
        if (millis == null) return ""
        return formatter(timeZone).format(Date(millis))
    }

    fun normalizePublicImageUrl(rawUrl: String): ValueResult<String?> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ValueResult(null)
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return ValueResult(error = "Introduce una URL HTTPS valida.")
        if (uri.scheme?.lowercase() != "https") {
            return ValueResult(error = "La URL debe empezar por https://")
        }
        val host = uri.host?.lowercase().orEmpty()
        return if (host == "drive.google.com" || host.endsWith(".drive.google.com")) {
            val id = extractGoogleDriveId(trimmed)
                ?: return ValueResult(error = "No se ha podido detectar el ID del archivo de Google Drive.")
            ValueResult("https://drive.google.com/uc?export=view&id=$id")
        } else {
            ValueResult(trimmed)
        }
    }

    private fun extractGoogleDriveId(url: String): String? {
        val filePattern = Regex("/file/d/([^/]+)")
        filePattern.find(url)?.groupValues?.getOrNull(1)?.let { return sanitizeDriveId(it) }

        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val query = uri.rawQuery.orEmpty()
        query.split('&')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
            }
            .firstOrNull { it.first == "id" }
            ?.second
            ?.let { return sanitizeDriveId(it) }

        return null
    }

    private fun sanitizeDriveId(value: String): String? {
        val cleaned = value.trim()
        return cleaned.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,}")) }
    }

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

    private fun validateTimeZone(timeZone: String): String? = when {
        timeZone.isBlank() -> "Introduce una zona horaria."
        TimeZone.getAvailableIDs().none { it == timeZone.trim() } -> "Zona horaria no valida. Ejemplo: Europe/Madrid."
        else -> null
    }

    private fun parseDate(text: String, timeZone: String): ValueResult<Long?> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ValueResult(null)
        val zoneError = validateTimeZone(timeZone)
        if (zoneError != null) return ValueResult(error = "Corrige la zona horaria antes de validar fechas.")
        return try {
            ValueResult(formatter(timeZone).parse(trimmed)?.time)
        } catch (_: ParseException) {
            ValueResult(error = "Usa el formato dd/MM/yyyy HH:mm.")
        }
    }

    private fun formatter(timeZone: String): SimpleDateFormat = SimpleDateFormat(DISPLAY_PATTERN, Locale.getDefault()).apply {
        isLenient = false
        this.timeZone = TimeZone.getTimeZone(timeZone)
    }
}

data class ValueResult<T>(
    val value: T? = null,
    val error: String? = null
)
