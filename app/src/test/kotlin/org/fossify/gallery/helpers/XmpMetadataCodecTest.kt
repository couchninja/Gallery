package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class XmpMetadataCodecTest {

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
        assertEquals(3, readXmpMetadataFromJpegBytes(bytes).rating)
        val packet = extractXmpPacketFromJpeg(bytes)
        assertTrue(packet != null)
        assertTrue(xmpPacketHasCanonicalRating(packet!!))
    }

    @Test
    fun readFStopStyleSample() {
        val bytes = readResource("xmp_rating/fstop_rated_4.jpg")
        assertEquals(4, readXmpMetadataFromJpegBytes(bytes).rating)
    }

    @Test
    fun writeRatingRoundTripOnCanonicalSample() {
        val temp = copyResourceToTemp("xmp_rating/canonical_rated_3.jpg")
        assertTrue(writeXmpRatingToJpegFile(temp.absolutePath, 5))
        assertEquals(5, readXmpMetadataFromJpegFile(temp.absolutePath).rating)
        val packet = extractXmpPacketFromJpeg(temp.readBytes())!!
        assertTrue(xmpPacketHasCanonicalRating(packet))
        assertEquals(5, parseRatingFromXmpPacket(packet))
        temp.delete()
    }

    @Test
    fun writeRatingRoundTripOnFStopSample() {
        val temp = copyResourceToTemp("xmp_rating/fstop_rated_4.jpg")
        assertTrue(writeXmpRatingToJpegFile(temp.absolutePath, 2))
        assertEquals(2, readXmpMetadataFromJpegFile(temp.absolutePath).rating)
        val packet = extractXmpPacketFromJpeg(temp.readBytes())!!
        assertTrue(xmpPacketHasCanonicalRating(packet))
        temp.delete()
    }

    @Test
    fun clearRatingRemovesAttribute() {
        val temp = copyResourceToTemp("xmp_rating/canonical_rated_3.jpg")
        assertTrue(writeXmpRatingToJpegFile(temp.absolutePath, 0))
        assertEquals(0, readXmpMetadataFromJpegFile(temp.absolutePath).rating)
        temp.delete()
    }

    @Test
    fun readTaggedSample() {
        val metadata = readXmpMetadataFromJpegBytes(readResource("xmp_metadata/tagged_rated_4.jpg"))
        assertEquals(listOf("keep 2 months"), metadata.tags)
        assertEquals(4, metadata.rating)
    }

    @Test
    fun readTaggedSampleFromFileUsesHeaderOnly() {
        val temp = copyResourceToTemp("xmp_metadata/tagged_rated_4.jpg")
        val header = readJpegHeaderSegments(temp)!!
        assertTrue(header.size < temp.length() / 10)
        assertEquals(XmpMetadata(4, listOf("keep 2 months")), readXmpMetadataFromJpegFile(temp.absolutePath))
        temp.delete()
    }

    @Test
    fun untaggedSampleHasNoTags() {
        assertEquals(emptyList<String>(), readXmpMetadataFromJpegBytes(readResource("xmp_rating/canonical_rated_3.jpg")).tags)
    }

    @Test
    fun parseTagsFromBagSeqAndAttributeForms() {
        val bag = """<rdf:Description><dc:subject><rdf:Bag><rdf:li>tag1</rdf:li><rdf:li>tag2</rdf:li> <rdf:li>tag3</rdf:li></rdf:Bag></dc:subject></rdf:Description>"""
        assertEquals(listOf("tag1", "tag2", "tag3"), parseTagsFromXmpPacket(bag.toByteArray()))

        val seq = """<dc:subject><rdf:Seq><rdf:li>a &amp; b</rdf:li><rdf:li>caf&#233;</rdf:li><rdf:li/></rdf:Seq></dc:subject>"""
        assertEquals(listOf("a & b", "café"), parseTagsFromXmpPacket(seq.toByteArray()))

        val attr = """<rdf:Description rdf:about="" dc:subject="single_tag"/>"""
        assertEquals(listOf("single_tag"), parseTagsFromXmpPacket(attr.toByteArray()))

        val none = """<rdf:Description xmlns:xmp="http://ns.adobe.com/xap/1.0/" xmp:Rating="3"/>"""
        assertEquals(emptyList<String>(), parseTagsFromXmpPacket(none.toByteArray()))
    }

    @Test
    fun writeRatingKeepsTags() {
        val temp = copyResourceToTemp("xmp_metadata/tagged_rated_4.jpg")
        assertTrue(writeXmpRatingToJpegFile(temp.absolutePath, 2))
        assertEquals(XmpMetadata(2, listOf("keep 2 months")), readXmpMetadataFromJpegFile(temp.absolutePath))
        temp.delete()
    }

    @Test
    fun writeTagsRoundTrip() {
        val temp = copyResourceToTemp("xmp_rating/canonical_rated_3.jpg")
        assertTrue(writeXmpTagsToJpegFile(temp.absolutePath, listOf("keep 2 months", "archive")))
        assertEquals(
            XmpMetadata(3, listOf("keep 2 months", "archive")),
            readXmpMetadataFromJpegFile(temp.absolutePath),
        )
        val packet = extractXmpPacketFromJpeg(temp.readBytes())!!.toString(Charsets.UTF_8)
        assertTrue(packet.contains("""xmlns:dc="http://purl.org/dc/elements/1.1/""""))
        temp.delete()
    }

    @Test
    fun writeTagsPreservesRating() {
        val temp = copyResourceToTemp("xmp_metadata/tagged_rated_4.jpg")
        assertTrue(writeXmpTagsToJpegFile(temp.absolutePath, listOf("new tag")))
        assertEquals(XmpMetadata(4, listOf("new tag")), readXmpMetadataFromJpegFile(temp.absolutePath))
        temp.delete()
    }

    @Test
    fun clearTagsRemovesSubject() {
        val temp = copyResourceToTemp("xmp_metadata/tagged_rated_4.jpg")
        assertTrue(writeXmpTagsToJpegFile(temp.absolutePath, emptyList()))
        assertEquals(XmpMetadata(4, emptyList()), readXmpMetadataFromJpegFile(temp.absolutePath))
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
