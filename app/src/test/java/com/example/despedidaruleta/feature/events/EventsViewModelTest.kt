package com.example.despedidaruleta.feature.events

import com.example.despedidaruleta.domain.model.ContentItem
import com.example.despedidaruleta.domain.model.RouletteCategory
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EventsViewModelTest {
    @Test
    fun pickNextEventSkipsThePreviousOneWhenPossible() {
        val previous = contentItem("1")
        val next = contentItem("2")
        val another = contentItem("3")

        repeat(20) {
            val selected = EventsViewModel.pickNextEvent(listOf(previous, next, another), previous.id)

            assertNotNull(selected)
            assertNotEquals(previous.id, selected?.id)
        }
    }

    private fun contentItem(id: String): ContentItem = ContentItem(
        id = id,
        category = RouletteCategory.EVENT,
        number = 1,
        text = "Evento $id",
        active = true,
        used = false,
        importId = null,
        usedAtMillis = null,
        usedByUid = null,
        usedByName = null,
        usedSpinId = null,
        createdAtMillis = null,
        updatedAtMillis = null
    )
}
