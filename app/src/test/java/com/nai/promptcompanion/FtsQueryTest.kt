package com.nai.promptcompanion

import com.nai.promptcompanion.data.repo.ftsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `blank input yields null (browse mode)`() {
        assertNull(ftsQuery(""))
        assertNull(ftsQuery("   "))
    }

    @Test
    fun `single term becomes quoted prefix`() {
        assertEquals("\"mizuki\"*", ftsQuery("mizuki"))
    }

    @Test
    fun `multiple terms AND together`() {
        assertEquals("\"mizuki\"* \"hito\"*", ftsQuery("mizuki hito"))
    }

    @Test
    fun `dangerous fts characters are neutralized by quoting`() {
        // Parens, colons and quotes would otherwise break the MATCH syntax;
        // inside double quotes they are literal, and FTS tokenizes them away.
        assertEquals("\"hammer_(sunset\"*", ftsQuery("hammer_(sunset"))
        assertEquals("\"artist:\"* \"wlop\"*", ftsQuery("artist: wlop"))
        assertEquals("\"weird\"*", ftsQuery("\"weird\""))
    }
}
