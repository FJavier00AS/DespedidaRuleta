package com.example.despedidaruleta.domain.model

/** Picks a random event id, avoiding [previousId] whenever another candidate exists. */
fun pickNextEventId(availableIds: List<String>, previousId: String?): String? {
    if (availableIds.isEmpty()) return null
    val candidates = availableIds.filterNot { it == previousId }
    return (candidates.ifEmpty { availableIds }).random()
}
