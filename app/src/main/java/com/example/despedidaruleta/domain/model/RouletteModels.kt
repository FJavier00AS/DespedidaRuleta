package com.example.despedidaruleta.domain.model

import kotlin.math.absoluteValue

enum class RouletteCategory(val firestoreValue: String, val label: String) {
    QUESTION("QUESTION", "Preguntas"),
    CHALLENGE("CHALLENGE", "Retos"),
    LIGHTNING("LIGHTNING", "Ronda relampago"),
    PUNISHMENT("PUNISHMENT", "Castigos");

    companion object {
        // La ronda relampago se juega fuera de la ruleta: no participa en los giros de categoria.
        val wheelEntries: List<RouletteCategory> = listOf(QUESTION, CHALLENGE, PUNISHMENT)

        fun fromFirestore(value: String?): RouletteCategory? = entries.firstOrNull { it.firestoreValue == value }

        fun parse(value: String): RouletteCategory? {
            val normalized = value.trim().lowercase()
            return when (normalized) {
                "question", "questions", "pregunta", "preguntas", "q" -> QUESTION
                "challenge", "challenges", "reto", "retos", "r" -> CHALLENGE
                "lightning", "ronda relampago", "ronda relámpago", "relampago", "relámpago", "rr" -> LIGHTNING
                "punishment", "punishments", "castigo", "castigos", "c" -> PUNISHMENT
                else -> null
            }
        }
    }
}

enum class GamePhase(val firestoreValue: String, val label: String) {
    IDLE("IDLE", "Lista"),
    CATEGORY_SPINNING("CATEGORY_SPINNING", "Girando categoria"),
    CATEGORY_SELECTED("CATEGORY_SELECTED", "Categoria elegida"),
    CONTENT_SPINNING("CONTENT_SPINNING", "Girando"),
    COMPLETED("COMPLETED", "Completada"),
    EXHAUSTED("EXHAUSTED", "Sin contenido");

    companion object {
        fun fromFirestore(value: String?): GamePhase = entries.firstOrNull { it.firestoreValue == value } ?: IDLE
    }
}

enum class SpinStatus(val firestoreValue: String, val label: String) {
    SPINNING("SPINNING", "Girando"),
    COMPLETED("COMPLETED", "Completado"),
    RESTORED("RESTORED", "Restaurado");

    companion object {
        fun fromFirestore(value: String?): SpinStatus = entries.firstOrNull { it.firestoreValue == value } ?: COMPLETED
    }
}

data class ContentItem(
    val id: String,
    val category: RouletteCategory,
    val number: Int,
    val text: String,
    val active: Boolean,
    val used: Boolean,
    val importId: String?,
    val usedAtMillis: Long?,
    val usedByUid: String?,
    val usedByName: String?,
    val usedSpinId: String?,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?
)

data class CategoryStats(
    val category: RouletteCategory,
    val totalCount: Int,
    val availableCount: Int,
    val usedCount: Int,
    val availableContentIds: List<String>,
    val contentHashes: List<String>
) {
    val isExhausted: Boolean = totalCount > 0 && availableCount == 0
}

data class RouletteGameState(
    val phase: GamePhase = GamePhase.IDLE,
    val activeSpinId: String? = null,
    val selectedCategory: RouletteCategory? = null,
    val selectedContentId: String? = null,
    val selectedContentNumber: Int? = null,
    val selectedContentText: String? = null,
    val categoryRotation: Float = 0f,
    val contentRotation: Float = 0f,
    val startedAtMillis: Long? = null,
    val completedAtMillis: Long? = null,
    val updatedAtMillis: Long? = null
)

data class SpinRecord(
    val id: String,
    val category: RouletteCategory,
    val contentId: String,
    val contentNumber: Int,
    val contentText: String,
    val spunByUid: String,
    val spunByName: String,
    val status: SpinStatus,
    val startedAtMillis: Long?,
    val completedAtMillis: Long?,
    val restoredAtMillis: Long?,
    val restoredByUid: String?
)

data class ImportRow(
    val sourceRow: Int,
    val category: RouletteCategory?,
    val number: Int?,
    val text: String,
    val error: String? = null
) {
    val isValid: Boolean = category != null && number != null && text.isNotBlank() && error == null
    val stableHash: String = listOf(category?.firestoreValue.orEmpty(), number ?: -1, text.normalizedContentHash()).joinToString("|")
}

data class ImportPreview(
    val fileName: String,
    val rows: List<ImportRow>
) {
    val validRows: List<ImportRow> = rows.filter { it.isValid }
    val invalidRows: List<ImportRow> = rows.filterNot { it.isValid }
}

data class ImportResult(
    val importId: String,
    val inserted: Int,
    val skipped: Int,
    val totalValidRows: Int
)

data class LocalUserSettings(
    val activeSessionId: String? = null,
    val notificationsEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val visualEffectsEnabled: Boolean = true,
    val quietHoursStart: Int = 2,
    val quietHoursEnd: Int = 9
)

fun String.normalizedContentHash(): String = trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")
    .hashCode()
    .absoluteValue
    .toString(36)
