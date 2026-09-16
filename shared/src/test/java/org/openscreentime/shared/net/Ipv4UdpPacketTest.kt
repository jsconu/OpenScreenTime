package org.openscreentime.shared.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ipv4UdpPacketTest {

    private val sourceAddress = byteArrayOf(10, 0, 0, 2)
    private val destinationAddress = byteArrayOf(1, 1, 1, 1)
    private val payload = byteArrayOf(1, 2, 3, 4, 5)

    @Test
    fun `a built packet round-trips back through parsing with the same fields`() {
        val packet = buildIpv4UdpPacket(sourceAddress, 5353, destinationAddress, 53, payload)
        val parsed = parseIpv4UdpPacket(packet)!!

        assertEquals(sourceAddress.toList(), parsed.sourceAddress.toList())
        assertEquals(5353, parsed.sourcePort)
        assertEquals(destinationAddress.toList(), parsed.destinationAddress.toList())
        assertEquals(53, parsed.destinationPort)
        assertEquals(payload.toList(), parsed.payload.toList())
    }

    @Test
    fun `an empty payload round-trips too`() {
        val packet = buildIpv4UdpPacket(sourceAddress, 1, destinationAddress, 2, ByteArray(0))
        val parsed = parseIpv4UdpPacket(packet)!!
        assertEquals(0, parsed.payload.size)
    }

    @Test
    fun `a non-IPv4 packet is unparseable`() {
        val packet = buildIpv4UdpPacket(sourceAddress, 1, destinationAddress, 2, payload)
        packet[0] = 0x60 // version 6 in the top nibble
        assertNull(parseIpv4UdpPacket(packet))
    }

    @Test
    fun `a non-UDP protocol is unparseable`() {
        val packet = buildIpv4UdpPacket(sourceAddress, 1, destinationAddress, 2, payload)
        packet[9] = 6 // TCP
        assertNull(parseIpv4UdpPacket(packet))
    }

    @Test
    fun `a packet shorter than its own IPv4 header is unparseable`() {
        assertNull(parseIpv4UdpPacket(ByteArray(10)))
    }

    @Test
    fun `a truncated UDP payload is unparseable`() {
        val packet = buildIpv4UdpPacket(sourceAddress, 1, destinationAddress, 2, payload)
        assertNull(parseIpv4UdpPacket(packet.copyOfRange(0, packet.size - 3)))
    }

    @Test
    fun `the IPv4 header checksum is internally consistent`() {
        val packet = buildIpv4UdpPacket(sourceAddress, 1, destinationAddress, 2, payload)
        // Summing all 16-bit words of a correctly-checksummed IPv4 header (checksum field
        // included) must fold down to exactly 0xFFFF, per RFC 791.
        var sum = 0
        var offset = 0
        while (offset < 20) {
            sum += readUInt16(packet, offset)
            offset += 2
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        assertEquals(0xFFFF, sum)
    }
}
