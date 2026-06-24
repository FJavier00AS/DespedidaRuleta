package com.example.despedidaruleta.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionValidatorsTest {
    @Test
    fun `join code must have exactly six digits`() {
        assertFalse(SessionValidators.validateJoinCode("12345").isValid)
        assertFalse(SessionValidators.validateJoinCode("1234567").isValid)
        assertFalse(SessionValidators.validateJoinCode("12A456").isValid)
        assertTrue(SessionValidators.validateJoinCode("123456").isValid)
    }

    @Test
    fun `join code normalization keeps only six digits`() {
        assertEquals("123456", SessionValidators.normalizeJoinCode("12 34-56-999"))
    }

    @Test
    fun `create session validation accepts minimum valid values`() {
        val result = SessionValidators.validateCreateSession(
            eventName = "Despedida Madrid",
            groomName = "Carlos"
        )

        assertTrue(result.isValid)
        assertNull(result.eventNameError)
        assertNull(result.groomNameError)
    }
}
