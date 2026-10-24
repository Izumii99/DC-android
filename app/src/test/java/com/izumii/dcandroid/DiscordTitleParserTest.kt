package com.izumii.dcandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordTitleParserTest {

    private fun parseUnreadCount(title: String?): Int {
        return title?.let {
            val match = Regex("""^\((\d+)\)""").find(it)
            match?.groupValues?.get(1)?.toIntOrNull()
        } ?: 0
    }

    private fun isDiscordHost(host: String): Boolean {
        return host.endsWith("discord.com") || host.endsWith("discordapp.com") || host.endsWith("discord.gg")
    }

    @Test
    fun testUnreadCountExtraction() {
        assertEquals(1, parseUnreadCount("(1) #general | Server"))
        assertEquals(99, parseUnreadCount("(99) Discord"))
        assertEquals(0, parseUnreadCount("#general | Server"))
        assertEquals(0, parseUnreadCount(null))
        assertEquals(0, parseUnreadCount(""))
    }

    @Test
    fun testHostRouting() {
        assertTrue(isDiscordHost("discord.com"))
        assertTrue(isDiscordHost("canary.discord.com"))
        assertTrue(isDiscordHost("cdn.discordapp.com"))
        assertTrue(isDiscordHost("discord.gg"))
        assertFalse(isDiscordHost("youtube.com"))
        assertFalse(isDiscordHost("google.com"))
    }
}
