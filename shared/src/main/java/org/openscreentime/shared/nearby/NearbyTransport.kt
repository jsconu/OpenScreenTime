package org.openscreentime.shared.nearby

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * How a sealed [NearbyMessage] actually gets from one phone to the other.
 *
 * Wi-Fi is the only implementation today ([LanNearbyTransport]); Bluetooth LE is meant to slot in
 * behind this same pair of methods so that nothing above it - the message vocabulary, the sealed
 * envelope, the screens - has to change when it arrives.
 *
 * Both calls block, so callers run them off the main thread. This module has no coroutines
 * dependency on purpose: a local build should not gain one for the sake of two sockets.
 */
interface NearbyTransport {

    /**
     * Kid's phone: find the parent's phone, ask, and wait for its answer. Null when it could not be
     * reached, which is the common case and not an error - the parent's phone may be off, asleep,
     * or on another network. A caller keeps the request and tries again later.
     */
    fun ask(message: NearbyMessage, timeoutMs: Long = DEFAULT_TIMEOUT_MS): NearbyMessage?

    /**
     * Parent's phone: listen until the returned handle is closed. [onAsk] runs on a background
     * thread, and whatever it returns is sealed and sent back as the answer; returning null answers
     * nothing and drops the connection, which is what an unrecognised or replayed message deserves.
     */
    fun host(onAsk: (NearbyMessage) -> NearbyMessage?): Closeable

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}

/**
 * One request, one answer, then the socket closes: length-prefixed so a short read is a failure
 * rather than a message that silently got shorter.
 *
 * The cap is what stops a stranger on the network from claiming a 2 GB message and making a phone
 * try to hold it - every real message here is a few hundred bytes.
 */
internal object NearbyFraming {

    const val MAX_BYTES = 64 * 1024

    fun write(socket: Socket, bytes: ByteArray) {
        require(bytes.size <= MAX_BYTES) { "A nearby message is at most $MAX_BYTES bytes" }
        DataOutputStream(socket.getOutputStream()).run {
            writeInt(bytes.size)
            write(bytes)
            flush()
        }
    }

    /** Null for anything malformed, oversized, or cut short. */
    fun read(socket: Socket): ByteArray? = runCatching {
        val input = DataInputStream(socket.getInputStream())
        val size = input.readInt()
        if (size !in 1..MAX_BYTES) return null
        ByteArray(size).also(input::readFully)
    }.getOrNull()
}
