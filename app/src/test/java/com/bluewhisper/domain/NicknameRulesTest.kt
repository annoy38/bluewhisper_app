package com.bluewhisper.domain

import com.bluewhisper.domain.model.NicknameRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NicknameRulesTest {

    @Test fun `15 char input passes`()       { assertTrue(NicknameRules.isValid("a".repeat(15))) }
    @Test fun `1 char input passes`()        { assertTrue(NicknameRules.isValid("A")) }
    @Test fun `empty input fails`()          { assertFalse(NicknameRules.isValid("")) }
    @Test fun `whitespace only fails`()      { assertFalse(NicknameRules.isValid("   ")) }

    @Test fun `pipe character rejected as invalid`() {
        assertFalse(NicknameRules.isValid("A|B"))
    }

    @Test fun `sanitize strips pipe`() {
        assertEquals("AB", NicknameRules.sanitize("A|B"))
    }

    @Test fun `sanitize clamps over-length input`() {
        val raw = "x".repeat(40)
        assertEquals(15, NicknameRules.sanitize(raw).length)
    }

    @Test fun `sanitize leaves valid input unchanged`() {
        assertEquals("Annoy", NicknameRules.sanitize("Annoy"))
    }

    @Test fun `whitespace edges still trim valid inputs`() {
        // Whitespace inside is fine; outside is trimmed by the validator.
        assertTrue(NicknameRules.isValid("  Bob  "))
        assertFalse(NicknameRules.isValid("  "))
    }
}
