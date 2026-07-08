package com.example.despedidaruleta.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RouletteModelsTest {
    @Test
    fun parseRecognizesEventCategory() {
        assertEquals(RouletteCategory.EVENT, RouletteCategory.parse("eventos"))
        assertEquals(RouletteCategory.EVENT, RouletteCategory.parse("EVENT"))
    }
}
