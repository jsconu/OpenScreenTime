package org.openscreentime.shared.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Builds a minimal, well-formed DNS query for [name] (A record, recursion desired), matching what a browser's resolver actually sends. */
private fun buildTestQuery(name: String, id: Int = 0x1234): ByteArray {
    val labels = name.split(".")
    val questionBytes = labels.flatMap { label ->
        listOf(label.length.toByte()) + label.toByteArray(Charsets.US_ASCII).toList()
    } + listOf(0.toByte()) // terminating root label
    val header = byteArrayOf(
        (id shr 8).toByte(), id.toByte(), // ID
        0x01, 0x00, // flags: RD = 1
        0x00, 0x01, // QDCOUNT = 1
        0x00, 0x00, // ANCOUNT
        0x00, 0x00, // NSCOUNT
        0x00, 0x00 // ARCOUNT
    )
    val questionTail = byteArrayOf(0x00, 0x01, 0x00, 0x01) // QTYPE = A, QCLASS = IN
    return header + questionBytes.toByteArray() + questionTail
}

class DnsMessageTest {

    @Test
    fun `parses the queried domain name and transaction id`() {
        val query = parseDnsQuery(buildTestQuery("example.com", id = 0xABCD))
        assertEquals(0xABCD, query?.id)
        assertEquals("example.com", query?.questionName)
    }

    @Test
    fun `parses a multi-label subdomain`() {
        val query = parseDnsQuery(buildTestQuery("m.tiktok.com"))
        assertEquals("m.tiktok.com", query?.questionName)
    }

    @Test
    fun `too short to contain a header is unparseable`() {
        assertNull(parseDnsQuery(ByteArray(5)))
    }

    @Test
    fun `a header claiming zero questions is unparseable`() {
        val header = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x00, 0, 0, 0, 0, 0, 0)
        assertNull(parseDnsQuery(header))
    }

    @Test
    fun `a truncated question section is unparseable`() {
        val query = buildTestQuery("example.com")
        assertNull(parseDnsQuery(query.copyOfRange(0, query.size - 6)))
    }

    @Test
    fun `nxdomain response echoes the question and id, and sets rcode 3`() {
        val query = buildTestQuery("blocked.example", id = 0x5566)
        val parsed = parseDnsQuery(query)!!
        val response = buildDnsNxDomainResponse(query, parsed)

        // ID unchanged.
        assertEquals(0x55, response[0].toInt() and 0xFF)
        assertEquals(0x66, response[1].toInt() and 0xFF)
        // QR=1, RD preserved (query set RD=1), AA/TC cleared -> 0x81.
        assertEquals(0x81, response[2].toInt() and 0xFF)
        // RA=1, RCODE=3 -> 0x83.
        assertEquals(0x83, response[3].toInt() and 0xFF)
        // QDCOUNT still 1, AN/NS/AR counts all zero.
        assertEquals(1, readUInt16(response, 4))
        assertEquals(0, readUInt16(response, 6))
        assertEquals(0, readUInt16(response, 8))
        assertEquals(0, readUInt16(response, 10))
        // The question section itself (name/type/class) is byte-for-byte unchanged.
        assertEquals(
            query.copyOfRange(12, parsed.questionSectionEnd).toList(),
            response.copyOfRange(12, parsed.questionSectionEnd).toList()
        )
    }
}
