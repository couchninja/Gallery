package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class XmpRatingCodecTest {

    @Test
    fun xmpPacketHasCanonicalRating_requiresCorrectNamespace() {
        val typoBinding = """<rdf:Description xmlns:xmp="http://ns.abobe.com/xap/1.0/" xmp:Rating="3"/>"""
        assertFalse(xmpPacketHasCanonicalRating(typoBinding.toByteArray()))

        val canonicalXmp = """<rdf:Description xmlns:xmp="http://ns.adobe.com/xap/1.0/" xmp:Rating="3"/>"""
        assertTrue(xmpPacketHasCanonicalRating(canonicalXmp.toByteArray()))

        val canonicalXap = """<rdf:Description xmlns:xap="http://ns.adobe.com/xap/1.0/" xap:Rating="5"/>"""
        assertTrue(xmpPacketHasCanonicalRating(canonicalXap.toByteArray()))

        val elementForm = """<rdf:Description xmlns:xmp='http://ns.adobe.com/xap/1.0/'><xmp:Rating>3</xmp:Rating></rdf:Description>"""
        assertFalse(xmpPacketHasCanonicalRating(elementForm.toByteArray()))
    }

    @Test
    fun canonicalizeFixesTypoNamespace() {
        val typo = """xmlns:xmp="http://ns.abobe.com/xap/1.0/" xmp:Rating="3""""
        val fixed = canonicalizeXmpRatingPacket(typo.toByteArray()).toString(Charsets.ISO_8859_1)
        assertTrue(fixed.contains(CANONICAL_XAP_NS))
        assertFalse(fixed.contains("abobe"))
    }

    @Test
    fun readCanonicalRatedSample() {
        val bytes = readResource("xmp_rating/canonical_rated_3.jpg")
        assertEquals(3, readXmpRatingFromJpegBytes(bytes))
        val packet = extractXmpPacketFromJpeg(bytes)
        assertTrue(packet != null)
        assertTrue(xmpPacketHasCanonicalRating(packet!!))
    }

    @Test
    fun readFStopStyleSample() {
        val bytes = readResource("xmp_rating/fstop_rated_4.jpg")
        assertEquals(4, readXmpRatingFromJpegBytes(bytes))
    }

    @Test
    fun writeRatingRoundTripOnCanonicalSample() {
        val temp = copyResourceToTemp("xmp_rating/canonical_rated_3.jpg")
        assertTrue(writeXmpRatingToJpegFile(temp.absolutePath, 5))
        assertEquals(5, readXmpRatingFromJpegFile(temp.absolutePath))
        val packet = extractXmpPacketFromJpeg(temp.readBytes())!!
        assertTrue(xmpPacketHasCanonicalRating(packet))
        assertEquals(5, parseRatingFromXmpPacket(packet))
        temp.delete()
    }

    @Test
    fun writeRatingRoundTripOnFStopSample() {
        val temp = copyResourceToTemp("xmp_rating/fstop_rated_4.jpg")
        assertTrue(writeXmpRatingToJpegFile(temp.absolutePath, 2))
        assertEquals(2, readXmpRatingFromJpegFile(temp.absolutePath))
        val packet = extractXmpPacketFromJpeg(temp.readBytes())!!
        assertTrue(xmpPacketHasCanonicalRating(packet))
        temp.delete()
    }

    @Test
    fun clearRatingRemovesAttribute() {
        val temp = copyResourceToTemp("xmp_rating/canonical_rated_3.jpg")
        assertTrue(writeXmpRatingToJpegFile(temp.absolutePath, 0))
        assertEquals(0, readXmpRatingFromJpegFile(temp.absolutePath))
        temp.delete()
    }

    private fun readResource(name: String): ByteArray {
        val stream = javaClass.classLoader.getResourceAsStream(name)
            ?: error("Missing test resource $name")
        return stream.use { it.readBytes() }
    }

    private fun copyResourceToTemp(name: String): File {
        val stream = javaClass.classLoader.getResourceAsStream(name)
            ?: error("Missing test resource $name")
        val temp = Files.createTempFile("xmp-rating-", ".jpg").toFile()
        stream.use { input ->
            Files.copy(input, temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return temp
    }
}
