package com.example.despedidaruleta.domain.repository

import com.example.despedidaruleta.domain.model.AuthUser
import com.example.despedidaruleta.domain.model.CategoryStats
import com.example.despedidaruleta.domain.model.ContentItem
import com.example.despedidaruleta.domain.model.ImportResult
import com.example.despedidaruleta.domain.model.ImportRow
import com.example.despedidaruleta.domain.model.RealtimeValue
import com.example.despedidaruleta.domain.model.RouletteCategory
import com.example.despedidaruleta.domain.model.RouletteGameState
import com.example.despedidaruleta.domain.model.SpinRecord
import kotlinx.coroutines.flow.Flow

interface RouletteRepository {
    fun observeContent(sessionId: String): Flow<RealtimeValue<List<ContentItem>>>
    fun observeCategoryStats(sessionId: String): Flow<RealtimeValue<List<CategoryStats>>>
    fun observeGameState(sessionId: String): Flow<RealtimeValue<RouletteGameState>>
    fun observeSpinHistory(sessionId: String, limit: Long = 80): Flow<RealtimeValue<List<SpinRecord>>>

    suspend fun importContent(user: AuthUser, sessionId: String, rows: List<ImportRow>): ImportResult
    suspend fun startCategorySpin(user: AuthUser, sessionId: String): RouletteCategory
    suspend fun markCategorySpinCompleted(user: AuthUser, sessionId: String)
    suspend fun startContentSpin(user: AuthUser, sessionId: String, category: RouletteCategory): SpinRecord
    suspend fun markSpinCompleted(user: AuthUser, sessionId: String, spinId: String)
    suspend fun drawLightningQuestions(user: AuthUser, sessionId: String): List<ContentItem>
    suspend fun returnToCategoryWheel(user: AuthUser, sessionId: String)
    suspend fun openCategoryWheel(user: AuthUser, sessionId: String, category: RouletteCategory)
    suspend fun openPunishmentWheel(user: AuthUser, sessionId: String)
    suspend fun restoreSpin(user: AuthUser, sessionId: String, spinId: String)
    suspend fun resetGame(user: AuthUser, sessionId: String)
}
