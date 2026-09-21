package org.openscreentime.shared.nearby

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The two phones talking over the home Wi-Fi, with Android's own service discovery (mDNS) and a
 * short-lived socket.
 *
 * **Both phones listen, and either can start an exchange**, because "sync now" has to mean
 * something on whichever phone a person happens to be holding. Each advertises under its own role
 * so the two never answer themselves: a kid's phone sends what it has been used for and is answered
 * with limits, and a parent's phone sends limits and is answered with usage. Same single round
 * trip, started from either end.
 *
 * **The name it advertises is derived from the link key, not the key itself.** A phone can only be
 * found by the phone it was linked with, and a stranger sniffing mDNS learns that an app is running
 * and nothing else. Everything in the socket is sealed anyway (see [NearbyEnvelope]), so this is
 * about finding the right phone rather than about secrecy.
 *
 * What this cannot do, and what the screens have to say plainly:
 *
 *  - **Both phones must be on the same network.** Mobile data on one of them is enough to break it.
 *  - **Some networks forbid it outright.** Guest Wi-Fi and many hotel and cafe networks isolate
 *    clients from each other, so the phones simply never see one another. There is no way around
 *    that from inside an app.
 *  - **The parent's phone has to be listening.** Android will not keep a socket open for an app
 *    that is asleep, so a request sent while the parent's phone is idle arrives when it wakes.
 *
 * None of that makes it useless - "both at home, same Wi-Fi" is when a kid actually asks for more
 * time. It does mean a request must be kept and retried rather than assumed delivered, and a kid
 * must never be shown a spinner that implies an answer is coming.
 */
class LanNearbyTransport(
    context: Context,
    private val link: NearbyLink,
    /** Which end of the link this phone is. Both listen, so each has to advertise its own name. */
    private val role: Role
) : NearbyTransport {

    /** A phone is one end or the other, and each looks for the opposite one. */
    enum class Role(internal val suffix: String) { PARENT("p"), KID("k") }

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceName = instanceName(link.key, role)
    private val peerServiceName = instanceName(link.key, if (role == Role.PARENT) Role.KID else Role.PARENT)

    override fun host(onAsk: (NearbyMessage) -> NearbyMessage?): Closeable {
        val server = ServerSocket(0)
        val registration = register(server.localPort)
        val thread = Thread({ accept(server, onAsk) }, "nearby-host").apply {
            isDaemon = true
            start()
        }
        return Closeable {
            runCatching { nsd.unregisterService(registration) }
            runCatching { server.close() }
            thread.interrupt()
        }
    }

    private fun accept(server: ServerSocket, onAsk: (NearbyMessage) -> NearbyMessage?) {
        while (!server.isClosed) {
            val socket = runCatching { server.accept() }.getOrNull() ?: return
            socket.use {
                it.soTimeout = SOCKET_TIMEOUT_MS
                val sealed = NearbyFraming.read(it) ?: return@use
                // Anything that does not open with our key is not from the phone we linked with.
                val message = NearbyEnvelope.open(sealed, link.key) ?: return@use
                val answer = onAsk(message) ?: return@use
                runCatching { NearbyFraming.write(it, NearbyEnvelope.seal(answer, link.key)) }
            }
        }
    }

    override fun ask(message: NearbyMessage, timeoutMs: Long): NearbyMessage? {
        val peer = discover(timeoutMs) ?: return null
        return runCatching {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(peer.first, peer.second), SOCKET_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS
                NearbyFraming.write(socket, NearbyEnvelope.seal(message, link.key))
                NearbyFraming.read(socket)?.let { NearbyEnvelope.open(it, link.key) }
            }
        }.getOrNull()
    }

    /** The linked phone's address, or null if it is not on this network right now. */
    private fun discover(timeoutMs: Long): Pair<InetAddress, Int>? {
        val found = ArrayBlockingQueue<Pair<InetAddress, Int>>(1)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName != peerServiceName) return
                // resolveService and NsdServiceInfo.host are deprecated in favour of
                // registerServiceInfoCallback, which is API 34. This app supports API 26, and the
                // deprecated pair still works everywhere; worth revisiting when minSdk moves.
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        resolved.host?.let { found.offer(it to resolved.port) }
                    }

                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                })
            }

            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) { found.offer(UNREACHABLE) }
            override fun onStopDiscoveryFailed(type: String, errorCode: Int) = Unit
        }

        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        return try {
            found.poll(timeoutMs, TimeUnit.MILLISECONDS)?.takeIf { it !== UNREACHABLE }
        } finally {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }

    private fun register(port: Int): NsdManager.RegistrationListener {
        val info = NsdServiceInfo().apply {
            serviceName = this@LanNearbyTransport.serviceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        return listener
    }

    companion object {
        const val SERVICE_TYPE = "_openscreentime._tcp."
        private const val SOCKET_TIMEOUT_MS = 5_000
        private val UNREACHABLE = InetAddress.getLoopbackAddress() to -1

        /**
         * A stable name for a link that gives nothing away: the first bytes of a hash of the key.
         * Both phones derive the same one, so the kid's phone can pick its own parent out of a
         * network with several families on it, and nobody else can tell whose is whose.
         */
        fun instanceName(key: ByteArray, role: Role): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(key)
            return "ost-" + digest.take(6).joinToString("") { "%02x".format(it) } + "-" + role.suffix
        }
    }
}
