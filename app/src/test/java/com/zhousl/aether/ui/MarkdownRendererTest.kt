package com.zhousl.aether.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
    @Test
    fun parseMarkdownImageSupportsOptionalTitleAndAngleWrappedUrls() {
        val image = parseMarkdownImage(
            "![Example](<https://example.com/assets/diagram(v2).svg> \"A title\")"
        )

        requireNotNull(image)
        assertEquals("Example", image.altText)
        assertEquals("https://example.com/assets/diagram(v2).svg", image.url)
    }

    @Test
    fun normalizeMarkdownImageUrlDecodesCommonHtmlEscapes() {
        assertEquals(
            "https://example.com/image.png?foo=1&bar=2",
            normalizeMarkdownImageUrl("https://example.com/image.png?foo=1&amp;bar=2"),
        )
    }

    @Test
    fun extractMarkdownLinkDestinationStopsBeforeOptionalTitle() {
        assertEquals(
            "https://example.com/a(b)c.png",
            extractMarkdownLinkDestination("https://example.com/a(b)c.png 'preview'"),
        )
    }

    @Test
    fun inferMarkdownImageMimeTypeDetectsSvgFromBytes() {
        val mimeType = inferMarkdownImageMimeType(
            reportedMimeType = null,
            rawUrl = "https://example.com/logo",
            bytes = "<svg viewBox=\"0 0 10 10\"></svg>".toByteArray(),
        )

        assertEquals("image/svg+xml", mimeType)
    }

    @Test
    fun parseMarkdownImageRejectsBlankDestinations() {
        assertNull(parseMarkdownImage("![Example]()"))
    }

    @Test
    fun parseMarkdownImageReadsMediaLayoutAttributes() {
        val image = parseMarkdownImage(
            "![Example](https://example.com/image.png){width=75% height=280 scroll=true show-all=false fit=cover}"
        )

        requireNotNull(image)
        assertEquals(MarkdownMediaWidth.Fraction(0.75f), image.layout.width)
        assertEquals(280, image.layout.heightDp)
        assertTrue(image.layout.scroll)
        assertFalse(image.layout.showAll)
        assertEquals(MarkdownMediaFit.Cover, image.layout.fit)
    }

    @Test
    fun parseMarkdownCodeFenceHeaderReadsMermaidAttributes() {
        val header = parseMarkdownCodeFenceHeader("```mermaid {height=420 show-all=true width=640}")
        val layout = parseMarkdownMediaLayout(
            attributes = header.attributes,
            defaults = MarkdownMediaLayout(maxHeightDp = 640),
        )

        assertEquals("mermaid", header.language)
        assertEquals(MarkdownMediaWidth.DpValue(640), layout.width)
        assertEquals(420, layout.heightDp)
        assertTrue(layout.showAll)
        assertFalse(layout.scroll)
        assertNull(layout.maxHeightDp)
    }
}
