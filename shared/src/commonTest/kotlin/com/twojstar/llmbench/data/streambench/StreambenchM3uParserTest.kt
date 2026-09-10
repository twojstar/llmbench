package com.twojstar.llmbench.data.streambench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreambenchM3uParserTest {
    @Test
    fun parsesCanonicalExtinfMetadataWithoutBrowserClassification() {
        val source = """
            ﻿#EXTM3U
            #EXTINF:-1 tvg-id="tv.one" tvg-logo="https://example.com/logo.png" tvg-name="Fallback" group-title="News, World" tvg-country="PL" tvg-language="pl" tvg-quality="1080p",TV One
            #EXTVLCOPT:http-referrer=https://example.com/
            https://example.com/live.m3u8?token=private
            #EXTINF:-1 radio="true" quality="128 kb/s",Radio One
            https://radio.example/live
        """.trimIndent()

        val entries = StreambenchM3uParser.parse(
            source = source,
            allowArtwork = true,
            providerId = "fixture-provider",
            providerLabel = "Fixture Provider"
        )

        assertEquals(2, entries.size)
        assertEquals("tv.one", entries[0].id)
        assertEquals("TV One", entries[0].title)
        assertEquals("News, World", entries[0].group)
        assertEquals("https://example.com/logo.png", entries[0].logo)
        assertEquals("PL", entries[0].country)
        assertEquals("pl", entries[0].language)
        assertEquals("1080p", entries[0].quality)
        assertFalse(entries[0].radio)
        assertEquals("fixture-provider", entries[0].providerId)
        assertEquals("Fixture Provider", entries[0].providerLabel)

        assertEquals("Radio One", entries[1].title)
        assertEquals("128 kb/s", entries[1].quality)
        assertTrue(entries[1].radio)
    }

    @Test
    fun titleTextCannotInjectExtinfAttributes() {
        val source = """
            #EXTINF:-1 tvg-id="real-id" group-title="Real group",Title tvg-id="evil-id" group-title="Evil group"
            https://example.com/live
        """.trimIndent()

        val entry = StreambenchM3uParser.parse(source).single()

        assertEquals("real-id", entry.id)
        assertEquals("Real group", entry.group)
        assertEquals("Title tvg-id=\"evil-id\" group-title=\"Evil group\"", entry.title)
    }

    @Test
    fun artworkIsOptInAndRestrictedToPublicHttpsHostnames() {
        val validLogo = """
            #EXTINF:-1 tvg-logo="https://example.com/logo.png",One
            https://example.com/one
        """.trimIndent()
        val dataLogo = """
            #EXTINF:-1 tvg-logo="data:image/png;base64,abc",Two
            https://example.com/two
        """.trimIndent()
        val insecureLogo = """
            #EXTINF:-1 tvg-logo="http://example.com/logo.png",Three
            https://example.com/three
        """.trimIndent()
        val localLogo = """
            #EXTINF:-1 tvg-logo="https://127.0.0.1/logo.png",Four
            https://example.com/four
        """.trimIndent()
        val localNameLogo = """
            #EXTINF:-1 tvg-logo="https://device.local/logo.png",Five
            https://example.com/five
        """.trimIndent()

        assertEquals("", StreambenchM3uParser.parse(validLogo).single().logo)
        assertEquals(
            "https://example.com/logo.png",
            StreambenchM3uParser.parse(validLogo, allowArtwork = true).single().logo
        )
        assertEquals("", StreambenchM3uParser.parse(dataLogo, allowArtwork = true).single().logo)
        assertEquals("", StreambenchM3uParser.parse(insecureLogo, allowArtwork = true).single().logo)
        assertEquals("", StreambenchM3uParser.parse(localLogo, allowArtwork = true).single().logo)
        assertEquals("", StreambenchM3uParser.parse(localNameLogo, allowArtwork = true).single().logo)
    }

    @Test
    fun invalidSourceLineClearsPendingMetadataBeforeNextUrl() {
        val source = """
            #EXTINF:-1 tvg-id="stale" group-title="Secret",Stale title
            file:///tmp/local
            HTTPS://fresh.example/live
        """.trimIndent()

        val entry = StreambenchM3uParser.parse(source).single()

        assertEquals("", entry.id)
        assertEquals("", entry.group)
        assertEquals("fresh.example", entry.title)
        assertEquals("HTTPS://fresh.example/live", entry.url)
    }

    @Test
    fun nonHttpSourcesUnsafeWhitespaceAndInvalidPortsAreIgnored() {
        val source = """
            #EXTM3U
            relative/stream.m3u8
            ftp://example.com/live
            https://example.com/has space
            https://example.com:abc/live
            https://example.com:70000/live
            https://example.com/live
        """.trimIndent()

        val entries = StreambenchM3uParser.parse(source)

        assertEquals(1, entries.size)
        assertEquals("https://example.com/live", entries.single().url)
    }

    @Test
    fun bracketedIpv6WithPortProducesAHostFallbackTitle() {
        val entry = StreambenchM3uParser.parse("https://[::1]:8080/live").single()

        assertEquals("::1", entry.title)
        assertEquals("https://[::1]:8080/live", entry.url)
    }

    @Test
    fun malformedBracketedIpv6AuthoritiesAreIgnored() {
        val source = """
            https://[not-an-ip]/live
            https://[1:2:3]/live
            https://[::1]/live
        """.trimIndent()

        val entries = StreambenchM3uParser.parse(source)

        assertEquals(1, entries.size)
        assertEquals("https://[::1]/live", entries.single().url)
    }

    @Test
    fun carriageReturnOnlyPlaylistsAreParsed() {
        val source = "#EXTM3U\r#EXTINF:-1,One\rhttps://example.com/one\r#EXTINF:-1,Two\rhttps://example.com/two"

        val entries = StreambenchM3uParser.parse(source)

        assertEquals(listOf("One", "Two"), entries.map { it.title })
    }

    @Test
    fun entryDebugStringRedactsUrlsAndUserMetadata() {
        val secret = "secret-title"
        val entry = StreambenchM3uParser.parse(
            "#EXTINF:-1 tvg-id=\"secret-id\",$secret\nhttps://example.com/live?token=private",
            providerId = "secret-provider",
            providerLabel = "secret-label"
        ).single()

        val debug = entry.toString()
        assertFalse(secret in debug)
        assertFalse("secret-id" in debug)
        assertFalse("token=private" in debug)
        assertFalse("secret-provider" in debug)
        assertTrue("metadata=<redacted>" in debug)
    }

    @Test
    fun entryLimitFailsInsteadOfReturningAQuietlyTruncatedPlaylist() {
        val source = buildString {
            repeat(StreambenchM3uParser.MAX_ENTRIES + 1) { index ->
                append("https://stream")
                append(index)
                append(".example/live\n")
            }
        }

        assertFailsWith<IllegalArgumentException> {
            StreambenchM3uParser.parse(source)
        }
    }
}
