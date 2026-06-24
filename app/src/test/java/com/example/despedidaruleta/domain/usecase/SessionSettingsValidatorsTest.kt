package com.example.despedidaruleta.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSettingsValidatorsTest {
    @Test
    fun `google drive file links are converted to image friendly url`() {
        val result = SessionSettingsValidators.normalizePublicImageUrl(
            "https://drive.google.com/file/d/1Abc_DefGhijKlmNopQRs/view?usp=sharing"
        )

        assertNull(result.error)
        assertEquals(
            "https://drive.google.com/uc?export=view&id=1Abc_DefGhijKlmNopQRs",
            result.value
        )
    }

    @Test
    fun `google drive open links are converted using id query parameter`() {
        val result = SessionSettingsValidators.normalizePublicImageUrl(
            "https://drive.google.com/open?id=1AbcDefGhijKlmNopQRs"
        )

        assertNull(result.error)
        assertEquals(
            "https://drive.google.com/uc?export=view&id=1AbcDefGhijKlmNopQRs",
            result.value
        )
    }

    @Test
    fun `only https image urls are accepted`() {
        val result = SessionSettingsValidators.normalizePublicImageUrl("http://example.com/image.jpg")

        assertFalse(result.error.isNullOrBlank())
    }

    @Test
    fun `blank photo url is valid and stored as null`() {
        val result = SessionSettingsValidators.normalizePublicImageUrl("   ")

        assertNull(result.error)
        assertNull(result.value)
    }

    @Test
    fun `valid settings parse date range`() {
        val result = SessionSettingsValidators.validate(
            eventName = "Despedida Madrid",
            groomName = "Carlos",
            rawPhotoUrl = "https://example.com/carlos.jpg",
            startsAtText = "20/06/2026 18:00",
            endsAtText = "21/06/2026 04:00",
            timeZone = "Europe/Madrid"
        )

        assertTrue(result.isValid)
        assertEquals("https://example.com/carlos.jpg", result.draft?.groomPhotoUrl)
        assertTrue((result.draft?.startsAtMillis ?: 0L) < (result.draft?.endsAtMillis ?: 0L))
    }

    @Test
    fun `end date must be after start date`() {
        val result = SessionSettingsValidators.validate(
            eventName = "Despedida Madrid",
            groomName = "Carlos",
            rawPhotoUrl = "",
            startsAtText = "21/06/2026 04:00",
            endsAtText = "20/06/2026 18:00",
            timeZone = "Europe/Madrid"
        )

        assertFalse(result.isValid)
        assertFalse(result.endsAtError.isNullOrBlank())
    }
}
