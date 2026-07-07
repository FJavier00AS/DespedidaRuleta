package com.example.despedidaruleta.feature.events

import com.example.despedidaruleta.domain.model.pickNextEventId
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EventsViewModelTest {
    @Test
    fun pickNextEventIdSkipsThePreviousOneWhenPossible() {
        val ids = listOf("1", "2", "3")

        repeat(20) {
            val selected = pickNextEventId(ids, previousId = "1")

            assertNotNull(selected)
            assertNotEquals("1", selected)
        }
    }

    @Test
    fun pickNextEventIdFallsBackToPreviousWhenItIsTheOnlyOption() {
        val selected = pickNextEventId(listOf("1"), previousId = "1")

        assertNotNull(selected)
    }
}
