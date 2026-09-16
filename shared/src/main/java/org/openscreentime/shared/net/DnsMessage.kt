package org.openscreentime.shared.net

private const val DNS_HEADER_SIZE = 12

/** A DNS query's transaction id, the domain name of its first question, and where the question section ends. */
data class DnsQuery(val id: Int, val questionName: String, val questionSectionEnd: Int)

/**
 * Parses just enough of a raw DNS query message (RFC 1035 section 4.1) to read its
 * transaction id and the domain name of its first question - the only two things a sinkhole
 * needs to decide whether to block it and, if so, answer it. Returns null for anything
 * malformed or with zero questions.
 */
fun parseDnsQuery(message: ByteArray): DnsQuery? {
    if (message.size < DNS_HEADER_SIZE) return null
    val id = readUInt16(message, 0)
    val qdCount = readUInt16(message, 4)
    if (qdCount < 1) return null

    val labels = mutableListOf<String>()
    var offset = DNS_HEADER_SIZE
    while (offset < message.size) {
        val length = message[offset].toInt() and 0xFF
        if (length == 0) {
            offset += 1
            break
        }
        // Name compression (a pointer into earlier message bytes) is never valid in the
        // question section of a query a client sends, so this is malformed, not a pointer
        // to follow.
        if (length and 0xC0 != 0) return null
        offset += 1
        if (offset + length > message.size) return null
        labels.add(String(message, offset, length, Charsets.US_ASCII))
        offset += length
    }
    if (labels.isEmpty()) return null

    // QTYPE + QCLASS, 2 bytes each, follow the question's terminating zero-length label.
    val questionSectionEnd = offset + 4
    if (questionSectionEnd > message.size) return null

    return DnsQuery(id = id, questionName = labels.joinToString("."), questionSectionEnd = questionSectionEnd)
}

/**
 * Builds an NXDOMAIN response for [query], read from the original raw [message] it was
 * parsed from. Echoes the question section back verbatim (required by RFC 1035), zeroes
 * ANCOUNT/NSCOUNT/ARCOUNT, and sets RCODE to 3 (NXDOMAIN) - the standard "this domain
 * doesn't exist" answer a real resolver gives, so a blocked site just fails to resolve
 * rather than the connection hanging or the sinkhole being distinguishable from a real
 * "no such domain."
 */
fun buildDnsNxDomainResponse(message: ByteArray, query: DnsQuery): ByteArray {
    val response = message.copyOfRange(0, query.questionSectionEnd)
    val queryFlagsByte = message[2].toInt() and 0xFF
    // QR = 1 (this is a response); preserve Opcode (bits 3-6) and RD (bit 0); clear AA/TC.
    response[2] = (0x80 or (queryFlagsByte and 0x79)).toByte()
    // RA = 1 (recursion available); RCODE = 3 (NXDOMAIN).
    response[3] = 0x83.toByte()
    writeUInt16(response, 6, 0) // ANCOUNT
    writeUInt16(response, 8, 0) // NSCOUNT
    writeUInt16(response, 10, 0) // ARCOUNT
    return response
}

internal fun readUInt16(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

internal fun writeUInt16(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = ((value shr 8) and 0xFF).toByte()
    bytes[offset + 1] = (value and 0xFF).toByte()
}
