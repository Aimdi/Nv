package com.nai.promptcompanion

import com.nai.promptcompanion.data.backup.BackupFavoriteTag
import com.nai.promptcompanion.data.backup.BackupFile
import com.nai.promptcompanion.data.backup.BackupPrompt
import com.nai.promptcompanion.data.backup.decodeBackup
import com.nai.promptcompanion.data.backup.encodeBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    @Test
    fun `backup round-trips through JSON`() {
        val backup = BackupFile(
            exportedAt = "2026-07-27T00:00:00Z",
            prompts = listOf(
                BackupPrompt("Beach", "1girl, beach, smile", favorite = true, createdAt = 1, updatedAt = 2),
            ),
            favoriteTags = listOf(BackupFavoriteTag("wlop", addedAt = 3)),
        )
        val decoded = decodeBackup(encodeBackup(backup))
        assertEquals(BackupFile.CURRENT_VERSION, decoded.version)
        assertEquals(1, decoded.prompts.size)
        assertEquals("Beach", decoded.prompts[0].title)
        assertTrue(decoded.prompts[0].favorite)
        assertEquals("wlop", decoded.favoriteTags[0].tag)
    }

    @Test
    fun `decoder tolerates unknown future fields`() {
        val futureJson = """
            {
              "version": 99,
              "app": "nai-prompt-companion",
              "exportedAt": "2027-01-01T00:00:00Z",
              "futureField": {"nested": true},
              "prompts": [{"title": "A", "body": "solo", "futureFlag": 1}]
            }
        """.trimIndent()
        val decoded = decodeBackup(futureJson)
        assertEquals(99, decoded.version)
        assertEquals(1, decoded.prompts.size)
        assertEquals("A", decoded.prompts[0].title)
    }
}
