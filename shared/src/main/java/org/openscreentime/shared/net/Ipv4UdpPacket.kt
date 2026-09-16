package org.openscreentime.shared.net

private const val IPV4_HEADER_SIZE = 20
private const val UDP_HEADER_SIZE = 8
private const val PROTOCOL_UDP = 17

/** A parsed IPv4/UDP packet's addressing and payload. Addresses are raw 4-byte IPv4 octets. */
data class Ipv4UdpPacket(
    val sourceAddress: ByteArray,
    val sourcePort: Int,
    val destinationAddress: ByteArray,
    val destinationPort: Int,
    val payload: ByteArray
)

/**
 * Parses a raw IPv4 packet - exactly what Android's `VpnService` TUN file descriptor hands
 * you for every outgoing packet an app on the device sends - and returns its UDP payload, or
 * null if it isn't a well-formed, unfragmented IPv4/UDP packet (anything else, e.g. TCP, IPv6,
 * or a fragment, is left alone by the sinkhole and simply not returned here).
 */
fun parseIpv4UdpPacket(packet: ByteArray): Ipv4UdpPacket? {
    if (packet.size < IPV4_HEADER_SIZE) return null
    val versionAndIhl = packet[0].toInt() and 0xFF
    if (versionAndIhl shr 4 != 4) return null
    val ihl = (versionAndIhl and 0x0F) * 4
    if (ihl < IPV4_HEADER_SIZE || packet.size < ihl + UDP_HEADER_SIZE) return null
    if (packet[9].toInt() and 0xFF != PROTOCOL_UDP) return null

    val sourcePort = readUInt16(packet, ihl)
    val destinationPort = readUInt16(packet, ihl + 2)
    val udpLength = readUInt16(packet, ihl + 4)
    val payloadStart = ihl + UDP_HEADER_SIZE
    val payloadEnd = ihl + udpLength
    if (udpLength < UDP_HEADER_SIZE || payloadEnd > packet.size) return null

    return Ipv4UdpPacket(
        sourceAddress = packet.copyOfRange(12, 16),
        sourcePort = sourcePort,
        destinationAddress = packet.copyOfRange(16, 20),
        destinationPort = destinationPort,
        payload = packet.copyOfRange(payloadStart, payloadEnd)
    )
}

/**
 * Builds a complete, checksummed IPv4/UDP packet - the wire format that must be written back
 * into a `VpnService` TUN file descriptor for the OS to deliver it to the app that's waiting
 * on it. Always a minimal 20-byte IPv4 header with no options and no fragmentation, which is
 * all a DNS response ever needs. The UDP checksum is left as zero, which RFC 768 explicitly
 * allows as "no checksum computed" over IPv4 - skips needing the IPv4 pseudo-header checksum
 * a real UDP checksum requires, and Android's own network stack accepts it.
 */
fun buildIpv4UdpPacket(
    sourceAddress: ByteArray,
    sourcePort: Int,
    destinationAddress: ByteArray,
    destinationPort: Int,
    payload: ByteArray
): ByteArray {
    require(sourceAddress.size == 4) { "sourceAddress must be 4 bytes" }
    require(destinationAddress.size == 4) { "destinationAddress must be 4 bytes" }

    val udpLength = UDP_HEADER_SIZE + payload.size
    val packet = ByteArray(IPV4_HEADER_SIZE + udpLength)

    packet[0] = 0x45 // version 4, IHL 5 (20 bytes, no options)
    packet[1] = 0 // DSCP/ECN
    writeUInt16(packet, 2, packet.size) // total length
    writeUInt16(packet, 4, 0) // identification
    writeUInt16(packet, 6, 0) // flags/fragment offset
    packet[8] = 64 // TTL
    packet[9] = PROTOCOL_UDP.toByte()
    writeUInt16(packet, 10, 0) // header checksum, filled in below
    System.arraycopy(sourceAddress, 0, packet, 12, 4)
    System.arraycopy(destinationAddress, 0, packet, 16, 4)
    writeUInt16(packet, 10, ipv4HeaderChecksum(packet))

    writeUInt16(packet, 20, sourcePort)
    writeUInt16(packet, 22, destinationPort)
    writeUInt16(packet, 24, udpLength)
    writeUInt16(packet, 26, 0) // UDP checksum: unused, see doc comment above
    System.arraycopy(payload, 0, packet, 28, payload.size)

    return packet
}

/** RFC 791 one's-complement checksum over the 20-byte IPv4 header. The checksum field itself must be zero when this is called. */
private fun ipv4HeaderChecksum(packet: ByteArray): Int {
    var sum = 0
    var offset = 0
    while (offset < IPV4_HEADER_SIZE) {
        sum += readUInt16(packet, offset)
        offset += 2
    }
    while (sum shr 16 != 0) {
        sum = (sum and 0xFFFF) + (sum shr 16)
    }
    return sum.inv() and 0xFFFF
}
