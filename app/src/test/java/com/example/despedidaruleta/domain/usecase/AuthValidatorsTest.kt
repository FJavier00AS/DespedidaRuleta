package com.example.despedidaruleta.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorsTest {
    @Test
    fun `register validation rejects invalid fields`() {
        val result = AuthValidators.validateRegister(
            email = "not-an-email",
            password = "123",
            displayName = "A"
        )

        assertFalse(result.isValid)
        assertTrue(result.emailError != null)
        assertTrue(result.passwordError != null)
        assertTrue(result.displayNameError != null)
    }

    @Test
    fun `register validation accepts valid form`() {
        val result = AuthValidators.validateRegister(
            email = "user@example.com",
            password = "123456",
            displayName = "Javi"
        )

        assertTrue(result.isValid)
        assertNull(result.emailError)
        assertNull(result.passwordError)
        assertNull(result.displayNameError)
    }

    @Test
    fun `login validation requires password`() {
        val result = AuthValidators.validateLogin(
            email = "user@example.com",
            password = ""
        )

        assertFalse(result.isValid)
        assertTrue(result.passwordError != null)
    }
}
