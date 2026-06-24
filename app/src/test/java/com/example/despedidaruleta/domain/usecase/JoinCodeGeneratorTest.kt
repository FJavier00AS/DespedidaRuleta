package com.example.despedidaruleta.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinCodeGeneratorTest {
    @Test
    fun `generated codes always have six numeric digits`() {
        val generator = JoinCodeGenerator()

        repeat(1_000) {
            val code = generator.generate()
            assertEquals(JoinCodeGenerator.CODE_LENGTH, code.length)
            assertTrue(code.all(Char::isDigit))
        }
    }
}
