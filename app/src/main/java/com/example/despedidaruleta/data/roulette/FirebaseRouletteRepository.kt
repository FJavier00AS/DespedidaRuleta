package com.example.despedidaruleta.data.roulette

import com.example.despedidaruleta.core.firebase.await
import com.example.despedidaruleta.domain.model.AuthUser
import com.example.despedidaruleta.domain.model.CategoryStats
import com.example.despedidaruleta.domain.model.ContentItem
import com.example.despedidaruleta.domain.model.GamePhase
import com.example.despedidaruleta.domain.model.ImportPreviewEmptyException
import com.example.despedidaruleta.domain.model.ImportResult
import com.example.despedidaruleta.domain.model.ImportRow
import com.example.despedidaruleta.domain.model.RealtimeValue
import com.example.despedidaruleta.domain.model.RouletteCategory
import com.example.despedidaruleta.domain.model.RouletteContentMissingException
import com.example.despedidaruleta.domain.model.RouletteExhaustedException
import com.example.despedidaruleta.domain.model.RouletteGameState
import com.example.despedidaruleta.domain.model.SpinAlreadyRestoredException
import com.example.despedidaruleta.domain.model.SpinNotFoundException
import com.example.despedidaruleta.domain.model.SpinRecord
import com.example.despedidaruleta.domain.model.SpinStatus
import com.example.despedidaruleta.domain.model.normalizedContentHash
import com.example.despedidaruleta.domain.repository.RouletteRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date
import kotlin.random.Random

class FirebaseRouletteRepository(
    private val firestore: FirebaseFirestore
) : RouletteRepository {
    override fun observeContent(sessionId: String): Flow<RealtimeValue<List<ContentItem>>> = callbackFlow {
        val registration = sessionRef(sessionId)
            .collection(CONTENT)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toContentItem() }
                    .sortedWith(compareBy<ContentItem> { it.category.ordinal }.thenBy { it.number })
                trySend(RealtimeValue(items, snapshot?.metadata?.isFromCache == true))
            }
        awaitClose { registration.remove() }
    }

    override fun observeCategoryStats(sessionId: String): Flow<RealtimeValue<List<CategoryStats>>> = callbackFlow {
        val registration = sessionRef(sessionId)
            .collection(CATEGORY_STATS)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val stats = RouletteCategory.entries.map { category ->
                    snapshot?.documents.orEmpty()
                        .firstOrNull { it.id == category.firestoreValue }
                        ?.toCategoryStats(category)
                        ?: CategoryStats(category, 0, 0, 0, emptyList(), emptyList())
                }
                trySend(RealtimeValue(stats, snapshot?.metadata?.isFromCache == true))
            }
        awaitClose { registration.remove() }
    }

    override fun observeGameState(sessionId: String): Flow<RealtimeValue<RouletteGameState>> = callbackFlow {
        val registration = sessionRef(sessionId)
            .collection(STATE)
            .document(GAME)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(RealtimeValue(snapshot.toGameState(), snapshot?.metadata?.isFromCache == true))
            }
        awaitClose { registration.remove() }
    }

    override fun observeSpinHistory(sessionId: String, limit: Long): Flow<RealtimeValue<List<SpinRecord>>> = callbackFlow {
        val registration = sessionRef(sessionId)
            .collection(SPINS)
            .orderBy(STARTED_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val spins = snapshot?.documents.orEmpty().mapNotNull { it.toSpinRecord() }
                trySend(RealtimeValue(spins, snapshot?.metadata?.isFromCache == true))
            }
        awaitClose { registration.remove() }
    }

    override suspend fun importContent(user: AuthUser, sessionId: String, rows: List<ImportRow>): ImportResult {
        val validRows = rows.filter { it.isValid }
        if (validRows.isEmpty()) throw ImportPreviewEmptyException()

        val sessionRef = sessionRef(sessionId)
        val importRef = sessionRef.collection(IMPORTS).document()
        val preparedRows = validRows.map { row -> row to sessionRef.collection(CONTENT).document() }

        return firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertOwner(sessionSnapshot, user.uid)

            val statsSnapshots = RouletteCategory.entries.associateWith { category ->
                transaction.get(statsRef(sessionId, category))
            }
            val statsByCategory = statsSnapshots.mapValues { (category, snapshot) -> snapshot.toCategoryStats(category) }
            val mutableStats = statsByCategory.mapValues { it.value.toMutableStats() }.toMutableMap()

            var inserted = 0
            var skipped = 0
            preparedRows.forEach { (row, contentRef) ->
                val category = row.category ?: return@forEach
                val stats = mutableStats.getValue(category)
                val hash = row.stableHash
                if (stats.contentHashes.contains(hash)) {
                    skipped++
                    return@forEach
                }
                val now = Date()
                transaction.set(
                    contentRef,
                    mapOf(
                        CATEGORY to category.firestoreValue,
                        NUMBER to row.number,
                        TEXT to row.text.trim(),
                        SEARCH_HASH to row.text.normalizedContentHash(),
                        IMPORT_HASH to hash,
                        ACTIVE to true,
                        USED to false,
                        IMPORT_ID to importRef.id,
                        CREATED_BY to user.uid,
                        CREATED_AT to now,
                        UPDATED_AT to now
                    )
                )
                stats.availableContentIds += contentRef.id
                stats.contentHashes += hash
                stats.totalCount += 1
                stats.availableCount += 1
                inserted++
            }

            mutableStats.forEach { (category, stats) ->
                transaction.set(statsRef(sessionId, category), stats.toFirestore(category), SetOptions.merge())
            }

            val result = ImportResult(
                importId = importRef.id,
                inserted = inserted,
                skipped = skipped,
                totalValidRows = validRows.size
            )
            val now = Date()
            transaction.set(
                importRef,
                mapOf(
                    IMPORT_ID to importRef.id,
                    CREATED_BY to user.uid,
                    CREATED_BY_NAME to user.displayName,
                    TOTAL_VALID_ROWS to validRows.size,
                    INSERTED_COUNT to inserted,
                    SKIPPED_COUNT to skipped,
                    CREATED_AT to now
                )
            )
            transaction.set(auditRef(sessionId), auditMap(user, "IMPORT_CONTENT", "inserted=$inserted skipped=$skipped"))
            result
        }.await()
    }

    override suspend fun startCategorySpin(user: AuthUser, sessionId: String): RouletteCategory {
        val sessionRef = sessionRef(sessionId)
        val stateRef = sessionRef.collection(STATE).document(GAME)
        val now = Date()

        return firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertMember(sessionId, sessionSnapshot, user.uid, transaction)

            val statsByCategory = RouletteCategory.entries.associateWith { category ->
                transaction.get(statsRef(sessionId, category)).toCategoryStats(category)
            }
            if (statsByCategory.values.all { it.totalCount == 0 }) throw RouletteContentMissingException()
            val availableStats = statsByCategory.values.filter { it.availableContentIds.isNotEmpty() && it.availableCount > 0 }
            if (availableStats.isEmpty()) throw RouletteExhaustedException()

            val selectedCategory = chooseWeighted(availableStats).category
            transaction.set(
                stateRef,
                mapOf(
                    PHASE to GamePhase.CATEGORY_SPINNING.firestoreValue,
                    ACTIVE_SPIN_ID to null,
                    SELECTED_CATEGORY to selectedCategory.firestoreValue,
                    SELECTED_CONTENT_ID to null,
                    SELECTED_CONTENT_NUMBER to null,
                    SELECTED_CONTENT_TEXT to null,
                    CATEGORY_ROTATION to targetCategoryRotation(statsByCategory, selectedCategory),
                    CONTENT_ROTATION to 0f,
                    STARTED_AT to now,
                    COMPLETED_AT to null,
                    UPDATED_AT to now
                ),
                SetOptions.merge()
            )
            transaction.set(auditRef(sessionId), auditMap(user, "START_CATEGORY_SPIN", "category=${selectedCategory.firestoreValue}"))
            selectedCategory
        }.await()
    }

    override suspend fun markCategorySpinCompleted(user: AuthUser, sessionId: String) {
        val sessionRef = sessionRef(sessionId)
        val stateRef = sessionRef.collection(STATE).document(GAME)
        val now = Date()
        firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertMember(sessionId, sessionSnapshot, user.uid, transaction)
            val stateSnapshot = transaction.get(stateRef)
            if (RouletteCategory.fromFirestore(stateSnapshot.getString(SELECTED_CATEGORY)) == null) {
                throw RouletteContentMissingException()
            }
            transaction.set(
                stateRef,
                mapOf(
                    PHASE to GamePhase.CATEGORY_SELECTED.firestoreValue,
                    COMPLETED_AT to now,
                    UPDATED_AT to now
                ),
                SetOptions.merge()
            )
            null
        }.await()
    }

    override suspend fun startContentSpin(user: AuthUser, sessionId: String, category: RouletteCategory): SpinRecord {
        val sessionRef = sessionRef(sessionId)
        val spinRef = sessionRef.collection(SPINS).document()
        val nowMillis = System.currentTimeMillis()
        val now = Date(nowMillis)

        return firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertMember(sessionId, sessionSnapshot, user.uid, transaction)

            val selectedStats = transaction.get(statsRef(sessionId, category)).toCategoryStats(category)
            if (selectedStats.totalCount == 0) throw RouletteContentMissingException()
            if (selectedStats.availableContentIds.isEmpty() || selectedStats.availableCount <= 0) throw RouletteExhaustedException()
            val selectedContentId = selectedStats.availableContentIds.random()
            val contentRef = sessionRef.collection(CONTENT).document(selectedContentId)
            val contentSnapshot = transaction.get(contentRef)
            val item = contentSnapshot.toContentItem() ?: throw RouletteContentMissingException()
            if (!item.active || item.used || item.category != category) throw RouletteExhaustedException()

            val nextAvailableIds = selectedStats.availableContentIds.filterNot { it == selectedContentId }
            val updatedStats = selectedStats.copy(
                availableCount = nextAvailableIds.size,
                usedCount = selectedStats.usedCount + 1,
                availableContentIds = nextAvailableIds
            )
            val record = SpinRecord(
                id = spinRef.id,
                category = item.category,
                contentId = item.id,
                contentNumber = item.number,
                contentText = item.text,
                spunByUid = user.uid,
                spunByName = user.displayName,
                status = SpinStatus.SPINNING,
                startedAtMillis = nowMillis,
                completedAtMillis = null,
                restoredAtMillis = null,
                restoredByUid = null
            )

            transaction.update(
                contentRef,
                mapOf(
                    USED to true,
                    USED_AT to now,
                    USED_BY_UID to user.uid,
                    USED_BY_NAME to user.displayName,
                    USED_SPIN_ID to spinRef.id,
                    UPDATED_AT to now
                )
            )
            transaction.set(statsRef(sessionId, item.category), updatedStats.toMutableStats().toFirestore(item.category), SetOptions.merge())
            transaction.set(spinRef, record.toFirestore(now))
            transaction.set(
                sessionRef.collection(STATE).document(GAME),
                mapOf(
                    PHASE to GamePhase.CONTENT_SPINNING.firestoreValue,
                    ACTIVE_SPIN_ID to spinRef.id,
                    SELECTED_CATEGORY to category.firestoreValue,
                    SELECTED_CONTENT_ID to item.id,
                    SELECTED_CONTENT_NUMBER to item.number,
                    SELECTED_CONTENT_TEXT to item.text,
                    CONTENT_ROTATION to targetContentRotation(selectedStats.availableContentIds.size),
                    STARTED_AT to now,
                    COMPLETED_AT to null,
                    UPDATED_AT to now
                ),
                SetOptions.merge()
            )
            transaction.set(auditRef(sessionId), auditMap(user, "START_SPIN", "spin=${spinRef.id}"))
            record
        }.await()
    }

    override suspend fun markSpinCompleted(user: AuthUser, sessionId: String, spinId: String) {
        val sessionRef = sessionRef(sessionId)
        val spinRef = sessionRef.collection(SPINS).document(spinId)
        val stateRef = sessionRef.collection(STATE).document(GAME)
        val now = Date()
        firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertMember(sessionId, sessionSnapshot, user.uid, transaction)
            val spinSnapshot = transaction.get(spinRef)
            if (!spinSnapshot.exists()) throw SpinNotFoundException()
            val stateSnapshot = transaction.get(stateRef)
            if (SpinStatus.fromFirestore(spinSnapshot.getString(STATUS)) != SpinStatus.RESTORED) {
                transaction.update(
                    spinRef,
                    mapOf(
                        STATUS to SpinStatus.COMPLETED.firestoreValue,
                        COMPLETED_AT to now,
                        UPDATED_AT to now
                    )
                )
            }
            if (stateSnapshot.getString(ACTIVE_SPIN_ID) == spinId) {
                transaction.set(
                    stateRef,
                    mapOf(
                        PHASE to GamePhase.COMPLETED.firestoreValue,
                        COMPLETED_AT to now,
                        UPDATED_AT to now
                    ),
                    SetOptions.merge()
                )
            }
            null
        }.await()
    }

    override suspend fun returnToCategoryWheel(user: AuthUser, sessionId: String) {
        val sessionRef = sessionRef(sessionId)
        val stateRef = sessionRef.collection(STATE).document(GAME)
        val now = Date()
        firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertMember(sessionId, sessionSnapshot, user.uid, transaction)
            transaction.set(
                stateRef,
                mapOf(
                    PHASE to GamePhase.IDLE.firestoreValue,
                    ACTIVE_SPIN_ID to null,
                    SELECTED_CATEGORY to null,
                    SELECTED_CONTENT_ID to null,
                    SELECTED_CONTENT_NUMBER to null,
                    SELECTED_CONTENT_TEXT to null,
                    CATEGORY_ROTATION to 0f,
                    CONTENT_ROTATION to 0f,
                    STARTED_AT to null,
                    COMPLETED_AT to null,
                    UPDATED_AT to now
                ),
                SetOptions.merge()
            )
            transaction.set(auditRef(sessionId), auditMap(user, "RETURN_TO_CATEGORY_WHEEL", ""))
            null
        }.await()
    }

    override suspend fun openPunishmentWheel(user: AuthUser, sessionId: String) {
        val sessionRef = sessionRef(sessionId)
        val stateRef = sessionRef.collection(STATE).document(GAME)
        val now = Date()
        firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertMember(sessionId, sessionSnapshot, user.uid, transaction)
            val stats = transaction.get(statsRef(sessionId, RouletteCategory.PUNISHMENT)).toCategoryStats(RouletteCategory.PUNISHMENT)
            if (stats.totalCount == 0) throw RouletteContentMissingException()
            if (stats.availableContentIds.isEmpty() || stats.availableCount <= 0) throw RouletteExhaustedException()

            transaction.set(
                stateRef,
                mapOf(
                    PHASE to GamePhase.CATEGORY_SELECTED.firestoreValue,
                    ACTIVE_SPIN_ID to null,
                    SELECTED_CATEGORY to RouletteCategory.PUNISHMENT.firestoreValue,
                    SELECTED_CONTENT_ID to null,
                    SELECTED_CONTENT_NUMBER to null,
                    SELECTED_CONTENT_TEXT to null,
                    CATEGORY_ROTATION to targetCategoryRotation(
                        mapOf(RouletteCategory.PUNISHMENT to stats),
                        RouletteCategory.PUNISHMENT
                    ),
                    CONTENT_ROTATION to 0f,
                    STARTED_AT to now,
                    COMPLETED_AT to null,
                    UPDATED_AT to now
                ),
                SetOptions.merge()
            )
            transaction.set(auditRef(sessionId), auditMap(user, "OPEN_PUNISHMENT_WHEEL", "question_failed"))
            null
        }.await()
    }

    override suspend fun restoreSpin(user: AuthUser, sessionId: String, spinId: String) {
        val sessionRef = sessionRef(sessionId)
        val spinRef = sessionRef.collection(SPINS).document(spinId)
        val stateRef = sessionRef.collection(STATE).document(GAME)
        val now = Date()
        firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertOwner(sessionSnapshot, user.uid)
            val spinSnapshot = transaction.get(spinRef)
            val spin = spinSnapshot.toSpinRecord() ?: throw SpinNotFoundException()
            if (spin.status == SpinStatus.RESTORED) throw SpinAlreadyRestoredException()

            val contentRef = sessionRef.collection(CONTENT).document(spin.contentId)
            val contentSnapshot = transaction.get(contentRef)
            val content = contentSnapshot.toContentItem() ?: throw SpinNotFoundException()
            val statsRef = statsRef(sessionId, spin.category)
            val stats = transaction.get(statsRef).toCategoryStats(spin.category)
            val stateSnapshot = transaction.get(stateRef)
            val restoredIds = if (content.active && !stats.availableContentIds.contains(content.id)) {
                stats.availableContentIds + content.id
            } else {
                stats.availableContentIds
            }
            val restoredStats = stats.copy(
                availableContentIds = restoredIds,
                availableCount = restoredIds.size,
                usedCount = (stats.usedCount - 1).coerceAtLeast(0)
            )

            transaction.update(
                contentRef,
                mapOf(
                    USED to false,
                    USED_AT to FieldValue.delete(),
                    USED_BY_UID to FieldValue.delete(),
                    USED_BY_NAME to FieldValue.delete(),
                    USED_SPIN_ID to FieldValue.delete(),
                    UPDATED_AT to now
                )
            )
            transaction.set(statsRef, restoredStats.toMutableStats().toFirestore(spin.category), SetOptions.merge())
            transaction.update(
                spinRef,
                mapOf(
                    STATUS to SpinStatus.RESTORED.firestoreValue,
                    RESTORED_AT to now,
                    RESTORED_BY_UID to user.uid,
                    UPDATED_AT to now
                )
            )
            if (stateSnapshot.getString(ACTIVE_SPIN_ID) == spinId) {
                transaction.set(
                    stateRef,
                    mapOf(
                        PHASE to GamePhase.IDLE.firestoreValue,
                        ACTIVE_SPIN_ID to null,
                        SELECTED_CATEGORY to null,
                        SELECTED_CONTENT_ID to null,
                        SELECTED_CONTENT_NUMBER to null,
                        SELECTED_CONTENT_TEXT to null,
                        UPDATED_AT to now
                    ),
                    SetOptions.merge()
                )
            }
            transaction.set(auditRef(sessionId), auditMap(user, "RESTORE_SPIN", "spin=$spinId"))
            null
        }.await()
    }

    override suspend fun resetGame(user: AuthUser, sessionId: String) {
        val sessionRef = sessionRef(sessionId)
        firestore.runTransaction { transaction ->
            val sessionSnapshot = transaction.get(sessionRef)
            assertOwner(sessionSnapshot, user.uid)
            transaction.set(
                sessionRef.collection(STATE).document(GAME),
                mapOf(
                    PHASE to GamePhase.IDLE.firestoreValue,
                    ACTIVE_SPIN_ID to null,
                    SELECTED_CATEGORY to null,
                    SELECTED_CONTENT_ID to null,
                    SELECTED_CONTENT_NUMBER to null,
                    SELECTED_CONTENT_TEXT to null,
                    CATEGORY_ROTATION to 0f,
                    CONTENT_ROTATION to 0f,
                    STARTED_AT to null,
                    COMPLETED_AT to null,
                    UPDATED_AT to Date()
                ),
                SetOptions.merge()
            )
            null
        }.await()
    }

    private fun assertOwner(sessionSnapshot: DocumentSnapshot, uid: String) {
        if (!sessionSnapshot.exists() || sessionSnapshot.getString(OWNER_UID) != uid) {
            throw FirebaseFirestoreException("Only the owner can do this.", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
    }

    private fun assertMember(
        sessionId: String,
        sessionSnapshot: DocumentSnapshot,
        uid: String,
        transaction: com.google.firebase.firestore.Transaction
    ) {
        if (!sessionSnapshot.exists()) throw FirebaseFirestoreException("Session missing.", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val memberSnapshot = transaction.get(sessionRef(sessionId).collection(MEMBERS).document(uid))
        if (!memberSnapshot.exists() || memberSnapshot.getBoolean(ACTIVE) != true) {
            throw FirebaseFirestoreException("Only active members can do this.", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        }
    }

    private fun chooseWeighted(stats: List<CategoryStats>): CategoryStats {
        val total = stats.sumOf { it.availableCount.coerceAtLeast(0) }
        var ticket = Random.nextInt(total.coerceAtLeast(1))
        stats.forEach { categoryStats ->
            ticket -= categoryStats.availableCount.coerceAtLeast(0)
            if (ticket < 0) return categoryStats
        }
        return stats.first()
    }

    private fun targetCategoryRotation(
        statsByCategory: Map<RouletteCategory, CategoryStats>,
        selectedCategory: RouletteCategory
    ): Float {
        val weights = RouletteCategory.entries.map { category ->
            statsByCategory[category]?.availableCount ?: 0
        }
        return targetWheelRotation(weights, RouletteCategory.entries.indexOf(selectedCategory))
    }

    private fun targetContentRotation(segmentCount: Int): Float =
        targetWheelRotation(List(segmentCount.coerceAtLeast(1)) { 1 }, selectedIndex = 0)

    private fun targetWheelRotation(weights: List<Int>, selectedIndex: Int): Float {
        val safeWeights = weights.ifEmpty { listOf(1) }.map { it.coerceAtLeast(0) }
        val effectiveWeights = if (safeWeights.any { it > 0 }) safeWeights else List(safeWeights.size) { 1 }
        val clampedIndex = selectedIndex.coerceIn(0, effectiveWeights.lastIndex)
        val totalWeight = effectiveWeights.sum().coerceAtLeast(1)
        val previousWeight = effectiveWeights.take(clampedIndex).sum()
        val selectedWeight = effectiveWeights[clampedIndex]
        val selectedCenterOffset = (previousWeight + selectedWeight / 2f) * 360f / totalWeight
        return FULL_SPINS * 360f + normalizeDegrees(-selectedCenterOffset)
    }

    private fun normalizeDegrees(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    private fun sessionRef(sessionId: String): DocumentReference = firestore.collection(SESSIONS).document(sessionId)

    private fun statsRef(sessionId: String, category: RouletteCategory): DocumentReference = sessionRef(sessionId)
        .collection(CATEGORY_STATS)
        .document(category.firestoreValue)

    private fun auditRef(sessionId: String): DocumentReference = sessionRef(sessionId).collection(AUDIT_LOGS).document()

    private fun auditMap(user: AuthUser, action: String, detail: String): Map<String, Any?> = mapOf(
        ACTION to action,
        DETAIL to detail,
        CREATED_BY to user.uid,
        CREATED_BY_NAME to user.displayName,
        CREATED_AT to Date()
    )

    private fun DocumentSnapshot?.toGameState(): RouletteGameState {
        if (this == null || !exists()) return RouletteGameState()
        return RouletteGameState(
            phase = GamePhase.fromFirestore(getString(PHASE)),
            activeSpinId = getString(ACTIVE_SPIN_ID),
            selectedCategory = RouletteCategory.fromFirestore(getString(SELECTED_CATEGORY)),
            selectedContentId = getString(SELECTED_CONTENT_ID),
            selectedContentNumber = getLong(SELECTED_CONTENT_NUMBER)?.toInt(),
            selectedContentText = getString(SELECTED_CONTENT_TEXT),
            categoryRotation = getDouble(CATEGORY_ROTATION)?.toFloat() ?: 0f,
            contentRotation = getDouble(CONTENT_ROTATION)?.toFloat() ?: 0f,
            startedAtMillis = getTimestampMillis(STARTED_AT),
            completedAtMillis = getTimestampMillis(COMPLETED_AT),
            updatedAtMillis = getTimestampMillis(UPDATED_AT)
        )
    }

    private fun DocumentSnapshot.toContentItem(): ContentItem? {
        if (!exists()) return null
        val category = RouletteCategory.fromFirestore(getString(CATEGORY)) ?: return null
        return ContentItem(
            id = id,
            category = category,
            number = getLong(NUMBER)?.toInt() ?: 0,
            text = getString(TEXT).orEmpty(),
            active = getBoolean(ACTIVE) != false,
            used = getBoolean(USED) == true,
            importId = getString(IMPORT_ID),
            usedAtMillis = getTimestampMillis(USED_AT),
            usedByUid = getString(USED_BY_UID),
            usedByName = getString(USED_BY_NAME),
            usedSpinId = getString(USED_SPIN_ID),
            createdAtMillis = getTimestampMillis(CREATED_AT),
            updatedAtMillis = getTimestampMillis(UPDATED_AT)
        )
    }

    private fun DocumentSnapshot.toCategoryStats(category: RouletteCategory): CategoryStats {
        if (!exists()) return CategoryStats(category, 0, 0, 0, emptyList(), emptyList())
        return CategoryStats(
            category = category,
            totalCount = getLong(TOTAL_COUNT)?.toInt() ?: 0,
            availableCount = getLong(AVAILABLE_COUNT)?.toInt() ?: availableIds().size,
            usedCount = getLong(USED_COUNT)?.toInt() ?: 0,
            availableContentIds = availableIds(),
            contentHashes = stringList(CONTENT_HASHES)
        )
    }

    private fun DocumentSnapshot.toSpinRecord(): SpinRecord? {
        if (!exists()) return null
        val category = RouletteCategory.fromFirestore(getString(CATEGORY)) ?: return null
        return SpinRecord(
            id = id,
            category = category,
            contentId = getString(CONTENT_ID).orEmpty(),
            contentNumber = getLong(CONTENT_NUMBER)?.toInt() ?: 0,
            contentText = getString(CONTENT_TEXT).orEmpty(),
            spunByUid = getString(SPUN_BY_UID).orEmpty(),
            spunByName = getString(SPUN_BY_NAME).orEmpty(),
            status = SpinStatus.fromFirestore(getString(STATUS)),
            startedAtMillis = getTimestampMillis(STARTED_AT),
            completedAtMillis = getTimestampMillis(COMPLETED_AT),
            restoredAtMillis = getTimestampMillis(RESTORED_AT),
            restoredByUid = getString(RESTORED_BY_UID)
        )
    }

    private fun SpinRecord.toFirestore(now: Date): Map<String, Any?> = mapOf(
        SPIN_ID to id,
        CATEGORY to category.firestoreValue,
        CONTENT_ID to contentId,
        CONTENT_NUMBER to contentNumber,
        CONTENT_TEXT to contentText,
        SPUN_BY_UID to spunByUid,
        SPUN_BY_NAME to spunByName,
        STATUS to status.firestoreValue,
        STARTED_AT to now,
        COMPLETED_AT to null,
        RESTORED_AT to null,
        RESTORED_BY_UID to null,
        CREATED_AT to now,
        UPDATED_AT to now
    )

    private fun CategoryStats.toMutableStats(): MutableCategoryStats = MutableCategoryStats(
        totalCount = totalCount,
        availableCount = availableCount,
        usedCount = usedCount,
        availableContentIds = availableContentIds.toMutableList(),
        contentHashes = contentHashes.toMutableList()
    )

    private fun MutableCategoryStats.toFirestore(category: RouletteCategory): Map<String, Any?> = mapOf(
        CATEGORY to category.firestoreValue,
        TOTAL_COUNT to totalCount,
        AVAILABLE_COUNT to availableCount,
        USED_COUNT to usedCount,
        AVAILABLE_CONTENT_IDS to availableContentIds.distinct(),
        CONTENT_HASHES to contentHashes.distinct(),
        EXHAUSTED to (totalCount > 0 && availableCount == 0),
        UPDATED_AT to Date()
    )

    private fun DocumentSnapshot.availableIds(): List<String> = stringList(AVAILABLE_CONTENT_IDS)

    private fun DocumentSnapshot.stringList(field: String): List<String> = (get(field) as? List<*>)
        ?.mapNotNull { it as? String }
        .orEmpty()

    private fun DocumentSnapshot.getTimestampMillis(field: String): Long? = when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        else -> null
    }

    private data class MutableCategoryStats(
        var totalCount: Int,
        var availableCount: Int,
        var usedCount: Int,
        var availableContentIds: MutableList<String>,
        var contentHashes: MutableList<String>
    )

    private companion object {
        const val SESSIONS = "sessions"
        const val MEMBERS = "members"
        const val CONTENT = "content"
        const val CATEGORY_STATS = "categoryStats"
        const val STATE = "state"
        const val GAME = "game"
        const val SPINS = "spins"
        const val IMPORTS = "imports"
        const val AUDIT_LOGS = "auditLogs"

        const val OWNER_UID = "ownerUid"
        const val ACTIVE = "active"
        const val CATEGORY = "category"
        const val NUMBER = "number"
        const val TEXT = "text"
        const val SEARCH_HASH = "searchHash"
        const val IMPORT_HASH = "importHash"
        const val IMPORT_ID = "importId"
        const val CREATED_BY = "createdBy"
        const val CREATED_BY_NAME = "createdByName"
        const val CREATED_AT = "createdAt"
        const val UPDATED_AT = "updatedAt"
        const val USED = "used"
        const val USED_AT = "usedAt"
        const val USED_BY_UID = "usedByUid"
        const val USED_BY_NAME = "usedByName"
        const val USED_SPIN_ID = "usedSpinId"
        const val TOTAL_COUNT = "totalCount"
        const val AVAILABLE_COUNT = "availableCount"
        const val USED_COUNT = "usedCount"
        const val AVAILABLE_CONTENT_IDS = "availableContentIds"
        const val CONTENT_HASHES = "contentHashes"
        const val EXHAUSTED = "exhausted"
        const val TOTAL_VALID_ROWS = "totalValidRows"
        const val INSERTED_COUNT = "insertedCount"
        const val SKIPPED_COUNT = "skippedCount"
        const val ACTION = "action"
        const val DETAIL = "detail"

        const val PHASE = "phase"
        const val ACTIVE_SPIN_ID = "activeSpinId"
        const val SELECTED_CATEGORY = "selectedCategory"
        const val SELECTED_CONTENT_ID = "selectedContentId"
        const val SELECTED_CONTENT_NUMBER = "selectedContentNumber"
        const val SELECTED_CONTENT_TEXT = "selectedContentText"
        const val CATEGORY_ROTATION = "categoryRotation"
        const val CONTENT_ROTATION = "contentRotation"
        const val STARTED_AT = "startedAt"
        const val COMPLETED_AT = "completedAt"

        const val SPIN_ID = "spinId"
        const val CONTENT_ID = "contentId"
        const val CONTENT_NUMBER = "contentNumber"
        const val CONTENT_TEXT = "contentText"
        const val SPUN_BY_UID = "spunByUid"
        const val SPUN_BY_NAME = "spunByName"
        const val STATUS = "status"
        const val RESTORED_AT = "restoredAt"
        const val RESTORED_BY_UID = "restoredByUid"
        const val FULL_SPINS = 4
    }
}
