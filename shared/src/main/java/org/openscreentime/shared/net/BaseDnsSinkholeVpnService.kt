package org.openscreentime.shared.net

import android.app.Notification
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import org.openscreentime.shared.model.WEBSITE_COUNT_WINDOW_MS
import org.openscreentime.shared.model.isDomainBlocked
import org.openscreentime.shared.model.websiteToCount
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Local, on-device DNS sinkhole (see #19) shared by the kid app and the parent app (for the parent's own
 * phone): it blocks a parent-set list of domains, in any browser or app, and - only when [trackingWebsites]
 * says so (see #41) - tallies which sites a browser looked up. Each app supplies a thin subclass that says
 * where its settings live and how to show its notification.
 *
 * Captures no general internet traffic: the VPN's only route is to [FAKE_DNS_ADDRESS], so only packets an app
 * sends to the system's configured DNS server (which this service sets to that address) ever reach
 * [handlePacket] - everything else, including the actual connection once a domain resolves, flows over the
 * network completely normally, outside this VPN. An allowed query is relayed to a real upstream resolver over
 * a [protect]'d socket (protect() excludes that socket from this VPN's own routes, so the relay itself doesn't
 * loop back into the tunnel it's serving); a blocked query gets an NXDOMAIN answer built locally, so the
 * browser just fails to resolve the site.
 *
 * Known limitation: a browser using DNS-over-HTTPS ("Secure DNS") or Android's "Private DNS" does its own name
 * lookups and never queries the resolver this service intercepts - so those lookups are neither blocked nor
 * counted. See docs/INSTALL_ANDROID.md.
 */
abstract class BaseDnsSinkholeVpnService : VpnService() {

    protected abstract val notificationId: Int
    protected abstract fun sessionName(): String
    protected abstract fun buildNotification(): Notification

    /** The domains to answer with NXDOMAIN right now. Read on the packet thread. */
    protected abstract fun blockedDomains(): List<String>

    /** True while a parent has website tracking on for this device. Read on the packet thread. */
    protected open fun trackingWebsites(): Boolean = false

    /** The package in front right now, used to count lookups only while a browser is open. */
    protected open fun foregroundPackage(): String? = null

    /** Called every few seconds (and on shutdown) with the sites looked up since the last call. */
    protected open fun saveWebsiteCounts(counts: Map<String, Int>) {}

    private var tunInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)

    // Sites counted since the last save, and when each was last counted (a page fires many lookups).
    private val pendingCounts = HashMap<String, Int>()
    private val lastCountedAt = HashMap<String, Long>()
    private var lastSaveMs = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running.compareAndSet(false, true)) {
            startForeground(notificationId, buildNotification())
            thread(name = "dns-sinkhole") { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        running.set(false)
        runCatching { tunInterface?.close() }
        tunInterface = null
        savePending(force = true)
    }

    /** Called by the system if the user revokes VPN consent from Settings directly, bypassing this app. */
    override fun onRevoke() {
        stopSelf()
    }

    private fun runLoop() {
        val tun = establishTunnel()
        if (tun == null) {
            running.set(false)
            return
        }
        tunInterface = tun
        val input = FileInputStream(tun.fileDescriptor)
        val output = FileOutputStream(tun.fileDescriptor)
        val buffer = ByteArray(MAX_PACKET_SIZE)

        while (running.get()) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                break
            }
            if (length <= 0) continue
            handlePacket(buffer.copyOf(length), output)
            savePending(force = false)
        }

        savePending(force = true)
        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { tun.close() }
    }

    private fun establishTunnel(): ParcelFileDescriptor? = Builder()
        .setSession(sessionName())
        .addAddress(TUN_ADDRESS, 32)
        .addDnsServer(FAKE_DNS_ADDRESS)
        .addRoute(FAKE_DNS_ADDRESS, 32)
        .setBlocking(true)
        .establish()

    private fun handlePacket(packet: ByteArray, output: FileOutputStream) {
        val udp = parseIpv4UdpPacket(packet) ?: return
        if (udp.destinationPort != DNS_PORT) return
        val query = parseDnsQuery(udp.payload) ?: return

        val blocked = isDomainBlocked(query.questionName, blockedDomains())
        if (trackingWebsites()) tally(query.questionName)

        val response = if (blocked) {
            buildDnsNxDomainResponse(udp.payload, query)
        } else {
            forwardToUpstreamResolver(udp.payload) ?: return
        }

        val responsePacket = buildIpv4UdpPacket(
            sourceAddress = udp.destinationAddress,
            sourcePort = DNS_PORT,
            destinationAddress = udp.sourceAddress,
            destinationPort = udp.sourcePort,
            payload = response
        )
        runCatching { output.write(responsePacket) }
    }

    /** Counts a site at most once per [WEBSITE_COUNT_WINDOW_MS], and only for a browser's lookups. */
    @Synchronized
    private fun tally(queryName: String) {
        val site = websiteToCount(foregroundPackage(), queryName) ?: return
        val now = System.currentTimeMillis()
        val last = lastCountedAt[site]
        if (last != null && now - last < WEBSITE_COUNT_WINDOW_MS) return
        lastCountedAt[site] = now
        // Bounded: forget anything not seen for a while so this map can't grow without limit.
        if (lastCountedAt.size > 2_000) lastCountedAt.entries.removeAll { now - it.value > WEBSITE_COUNT_WINDOW_MS }
        pendingCounts[site] = (pendingCounts[site] ?: 0) + 1
    }

    @Synchronized
    private fun savePending(force: Boolean) {
        if (pendingCounts.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSaveMs < SAVE_INTERVAL_MS) return
        lastSaveMs = now
        val snapshot = HashMap(pendingCounts)
        pendingCounts.clear()
        runCatching { saveWebsiteCounts(snapshot) }
    }

    /** Relays [query] to a real DNS resolver, bypassing this VPN's own tunnel via [protect]. */
    private fun forwardToUpstreamResolver(query: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket().apply {
                soTimeout = UPSTREAM_TIMEOUT_MS
                protect(this)
            }
            val upstream = InetSocketAddress(InetAddress.getByName(UPSTREAM_DNS_ADDRESS), DNS_PORT)
            socket.send(DatagramPacket(query, query.size, upstream))
            val responseBuffer = ByteArray(MAX_PACKET_SIZE)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)
            responseBuffer.copyOf(responsePacket.length)
        } catch (e: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private companion object {
        const val MAX_PACKET_SIZE = 32_767
        const val DNS_PORT = 53
        const val UPSTREAM_TIMEOUT_MS = 5_000
        const val SAVE_INTERVAL_MS = 15_000L
        const val TUN_ADDRESS = "10.233.0.2"
        const val FAKE_DNS_ADDRESS = "10.233.0.1"
        const val UPSTREAM_DNS_ADDRESS = "1.1.1.1"
    }
}
