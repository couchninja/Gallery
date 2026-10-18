package org.fossify.gallery.helpers

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/** Adobe XAP namespace for Basic `Rating` (canonical). */
const val CANONICAL_XAP_NS = "http://ns.adobe.com/xap/1.0/"

private val FSTOP_XAP_TYPO_NS = "http://ns.abobe.com/xap/1.0/"

private val ISO_8859_1: Charset = Charsets.ISO_8859_1

private val XMP_APP1_HEADER = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)

private val RATING_ATTR = Regex("""(?i)(xmp|xap):Rating="(-?\d+)"""")
private val RATING_ELEMENT = Regex("""(?i)<(xmp|xap):Rating>\s*(-?\d+)\s*</\1:Rating>""")

/**
 * Whether raw XMP exposes Rating in the Adobe XAP namespace (see media_manage metadata tests).
 */
fun xmpPacketHasCanonicalRating(xmp: ByteArray): Boolean {
    val text = xmp.toString(ISO_8859_1)
    for (prefix in arrayOf("xmp", "xap")) {
        val binding = """xmlns:$prefix="$CANONICAL_XAP_NS""""
        if (!text.contains(binding)) {
            continue
        }
        if (Regex("""$prefix:Rating="-?\d+""").containsMatchIn(text)) {
            return true
        }
        if (Regex("""<$prefix:Rating>\s*-?\d+\s*</$prefix:Rating>""").containsMatchIn(text)) {
            return true
        }
    }
    return false
}

fun canonicalizeXmpRatingPacket(xmp: ByteArray): ByteArray {
    var text = xmp.toString(ISO_8859_1)
    text = text.replace(FSTOP_XAP_TYPO_NS, CANONICAL_XAP_NS)
    for (prefix in arrayOf("xmp", "xap")) {
        text = text.replace("xmlns:$prefix='$CANONICAL_XAP_NS'", """xmlns:$prefix="$CANONICAL_XAP_NS"""")
    }
    return text.toByteArray(ISO_8859_1)
}

fun parseRatingFromXmpPacket(xmp: ByteArray): Int? {
    val text = xmp.toString(ISO_8859_1)
    RATING_ATTR.find(text)?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 5)?.let { return it }
    RATING_ELEMENT.find(text)?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 5)?.let { return it }
    return null
}

fun readXmpRatingFromJpegFile(path: String): Int {
    if (!path.isJpegPath()) {
        return 0
    }
    return try {
        val jpeg = File(path).readBytes()
        readXmpRatingFromJpegBytes(jpeg)
    } catch (_: Exception) {
        0
    }
}

fun readXmpRatingFromJpegBytes(jpeg: ByteArray): Int {
    val packet = extractXmpPacketFromJpeg(jpeg) ?: return scanLooseRatingInJpeg(jpeg)
    return parseRatingFromXmpPacket(packet) ?: 0
}

/** Fallback when XMP is not in a standard APP1 segment (some exports embed x:xmpmeta elsewhere). */
private fun scanLooseRatingInJpeg(jpeg: ByteArray): Int {
    val text = jpeg.toString(ISO_8859_1)
    RATING_ATTR.find(text)?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 5)?.let { return it }
    return 0
}

fun writeXmpRatingToJpegFile(path: String, rating: Int): Boolean {
    if (!path.isJpegPath()) {
        return false
    }
    return try {
        val clamped = rating.coerceIn(0, 5)
        val jpeg = File(path).readBytes()
        val existing = extractXmpPacketFromJpeg(jpeg)
        val newPacket = if (existing != null) {
            setRatingInXmpPacket(existing, clamped)
        } else if (clamped == 0) {
            return true
        } else {
            buildMinimalCanonicalXmpPacket(clamped)
        }
        val updated = replaceOrInsertXmpInJpeg(jpeg, newPacket)
        RandomAccessFile(path, "rw").use { raf ->
            raf.setLength(0)
            raf.write(updated)
        }
        true
    } catch (_: Exception) {
        false
    }
}

fun setRatingInXmpPacket(xmp: ByteArray, rating: Int): ByteArray {
    val clamped = rating.coerceIn(0, 5)
    var packet = canonicalizeXmpRatingPacket(xmp)
    packet = if (clamped == 0) {
        removeRatingFromPacket(packet)
    } else {
        applyRatingToPacket(packet, clamped)
    }
    return packet
}

private fun removeRatingFromPacket(xmp: ByteArray): ByteArray {
    var text = xmp.toString(ISO_8859_1)
    text = Regex("""(?i)\s*(xmp|xap):Rating="-?\d+"""").replace(text, "")
    text = Regex("""(?i)\s*<(xmp|xap):Rating>\s*-?\d+\s*</\1:Rating>""").replace(text, "")
    return text.toByteArray(ISO_8859_1)
}

private fun applyRatingToPacket(xmp: ByteArray, rating: Int): ByteArray {
    var text = canonicalizeXmpRatingPacket(xmp).toString(ISO_8859_1)
    if (Regex("""(?i)(xmp|xap):Rating="""").containsMatchIn(text)) {
        text = Regex("""(?i)(xmp|xap):Rating="-?\d+"""").replace(text) { """${it.groupValues[1]}:Rating="$rating"""" }
    } else if (Regex("""(?i)<(xmp|xap):Rating>""").containsMatchIn(text)) {
        text = Regex("""(?i)<(xmp|xap):Rating>\s*-?\d+\s*</\1:Rating>""").replace(text) {
            "<${it.groupValues[1]}:Rating>$rating</${it.groupValues[1]}:Rating>"
        }
    } else if (text.contains("""xmlns:xmp="$CANONICAL_XAP_NS"""")) {
        text = text.replace(
            Regex("""xmlns:xmp="$CANONICAL_XAP_NS"""")
        ) { """xmlns:xmp="$CANONICAL_XAP_NS" xmp:Rating="$rating"""" }
    } else if (text.contains("<rdf:Description")) {
        text = text.replaceFirst(
            Regex("""<rdf:Description([^>]*)>"""),
            """<rdf:Description$1 xmlns:xmp="$CANONICAL_XAP_NS" xmp:Rating="$rating">"""
        )
    } else {
        return buildMinimalCanonicalXmpPacket(rating)
    }
    return text.toByteArray(ISO_8859_1)
}

fun buildMinimalCanonicalXmpPacket(rating: Int): ByteArray {
    val xml = """<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Fossify Gallery">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about="" xmlns:xmp="$CANONICAL_XAP_NS" xmp:Rating="$rating"/>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
    return xml.toByteArray(Charsets.UTF_8)
}

fun extractXmpPacketFromJpeg(jpeg: ByteArray): ByteArray? {
    var i = 2
    while (i + 4 <= jpeg.size) {
        if (jpeg[i].toInt() and 0xFF != 0xFF) {
            break
        }
        val marker = jpeg[i + 1].toInt() and 0xFF
        if (marker == 0xDA || marker == 0xD9) {
            break
        }
        val segLen = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
        if (segLen < 2 || i + 2 + segLen > jpeg.size) {
            break
        }
        if (marker == 0xE1 && isXmpApp1Segment(jpeg, i)) {
            val dataStart = i + 4 + XMP_APP1_HEADER.size
            val dataEnd = i + 2 + segLen
            if (dataStart <= dataEnd) {
                return jpeg.copyOfRange(dataStart, dataEnd)
            }
        }
        i += 2 + segLen
    }
    return null
}

fun replaceOrInsertXmpInJpeg(jpeg: ByteArray, newXmp: ByteArray): ByteArray {
    if (jpeg.size < 2 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) {
        return jpeg
    }
    val out = ArrayList<ByteArray>()
    out.add(jpeg.copyOfRange(0, 2))
    var i = 2
    var replaced = false
    while (i + 4 <= jpeg.size) {
        if (jpeg[i].toInt() and 0xFF != 0xFF) {
            out.add(jpeg.copyOfRange(i, jpeg.size))
            return out.concat()
        }
        val marker = jpeg[i + 1].toInt() and 0xFF
        if (marker == 0xDA) {
            if (!replaced) {
                out.add(buildApp1XmpSegment(newXmp))
                replaced = true
            }
            out.add(jpeg.copyOfRange(i, jpeg.size))
            return out.concat()
        }
        val segLen = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
        val segEnd = i + 2 + segLen
        if (segEnd > jpeg.size) {
            break
        }
        if (marker == 0xE1 && isXmpApp1Segment(jpeg, i)) {
            if (!replaced) {
                out.add(buildApp1XmpSegment(newXmp))
                replaced = true
            }
        } else {
            out.add(jpeg.copyOfRange(i, segEnd))
        }
        i = segEnd
    }
    if (!replaced) {
        val merged = ArrayList<ByteArray>()
        merged.add(jpeg.copyOfRange(0, 2))
        merged.add(buildApp1XmpSegment(newXmp))
        merged.add(jpeg.copyOfRange(2, jpeg.size))
        return merged.concat()
    }
    return out.concat()
}

private fun isXmpApp1Segment(jpeg: ByteArray, segmentStart: Int): Boolean {
    val headerStart = segmentStart + 4
    if (headerStart + XMP_APP1_HEADER.size > jpeg.size) {
        return false
    }
    for (h in XMP_APP1_HEADER.indices) {
        if (jpeg[headerStart + h] != XMP_APP1_HEADER[h]) {
            return false
        }
    }
    return true
}

private fun buildApp1XmpSegment(xmp: ByteArray): ByteArray {
    val payloadLen = XMP_APP1_HEADER.size + xmp.size
    val segLen = payloadLen + 2
    val segment = ByteArray(4 + payloadLen)
    segment[0] = 0xFF.toByte()
    segment[1] = 0xE1.toByte()
    segment[2] = (segLen shr 8).toByte()
    segment[3] = (segLen and 0xFF).toByte()
    System.arraycopy(XMP_APP1_HEADER, 0, segment, 4, XMP_APP1_HEADER.size)
    System.arraycopy(xmp, 0, segment, 4 + XMP_APP1_HEADER.size, xmp.size)
    return segment
}

private fun ByteArray.containsSlice(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) {
        return false
    }
    for (start in 0..size - needle.size) {
        var match = true
        for (j in needle.indices) {
            if (this[start + j] != needle[j]) {
                match = false
                break
            }
        }
        if (match) {
            return true
        }
    }
    return false
}

private fun List<ByteArray>.concat(): ByteArray {
    val total = sumOf { it.size }
    val out = ByteArray(total)
    var pos = 0
    for (chunk in this) {
        System.arraycopy(chunk, 0, out, pos, chunk.size)
        pos += chunk.size
    }
    return out
}

private fun ArrayList<ByteArray>.concat(): ByteArray = toList().concat()
