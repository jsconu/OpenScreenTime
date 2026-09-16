package org.openscreentime.kid.monitor

import android.app.Notification
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.R
import org.openscreentime.shared.model.isDomainBlocked
import org.openscreentime.shared.net.buildDnsNxDomainResponse
import org.openscreentime.shared.net.buildIpv4UdpPacket
import org.openscreentime.shared.net.parseDnsQuery
import org.openscreentime.shared.net.parseIpv4UdpPacket
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Local, on-device DNS sinkhole (see #19) that blocks a parent-set list of domains, in any
 * browser or app, without needing to read any browser's own UI.
 *
 * Captures no general internet traffic: the VPN's only route is to [FAKE_DNS_ADDRESS], so
 * only packets an app sends to the system's configured DNS server (which this service sets
 * to that address) ever reach [handlePacket] - everything else, including the actual
 * connection once a domain resolves, flows over the network completely normally, outside
 * this VPN. An allowed query is relayed to a real upstream resolver over a [protect]'d
 * socket (protect() excludes that socket from this VPN's own routes, so the relay itself
 * doesn't loop back into the tunnel it's serving); a blocked query gets an NXDOMAIN answer
 * built locally, so the browser just fails to resolve the site.
 *
 * Known limitation: a browser using DNS-over-HTTPS for its own name resolution (e.g.
 * Chrome/Firefox's "Secure DNS", on by default in many builds) never queries the OS
 * resolver this service intercepts - that traffic is plain HTTPS to an arbitrary IP, not
 * a DNS packet to [FAKE_DNS_ADDRESS]. Tracked as a known gap on #19, not solved here.
 */
class DnsSinkholeVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running.compareAndSet(false, true)) {
            startForeground(NOTIFICATION_ID, buildNotification())
            thread(name = "dns-sinkhole") { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        running.set(false)
        runCatching { tunInterface?.close() }
        tunInterface = null
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
        }

        runCatching { input.close() }
        runCatching { output.close() }
        runCatching { tun.close() }
    }

    private fun establishTunnel(): ParcelFileDescriptor? = Builder()
        .setSession(getString(R.string.app_name))
        .addAddress(TUN_ADDRESS, 32)
        .addDnsServer(FAKE_DNS_ADDRESS)
        .addRoute(FAKE_DNS_ADDRESS, 32)
        .setBlocking(true)
        .establish()

    private fun handlePacket(packet: ByteArray, output: FileOutputStream) {
        val udp = parseIpv4UdpPacket(packet) ?: return
        if (udp.destinationPort != DNS_PORT) return
        val query = parseDnsQuery(udp.payload) ?: return

        val response = if (isDomainBlocked(query.questionName, LiveChildState.blockedDomains)) {
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

    private fun buildNotification(): Notification = buildOngoingNotification(
        context = this,
        channelId = KidApp.MONITOR_CHANNEL_ID,
        iconRes = R.drawable.ic_monitor,
        title = getString(R.string.website_filter_notification_title),
        text = getString(R.string.website_filter_notification_text)
    )

    companion object {
        private const val NOTIFICATION_ID = 1003
        private const val MAX_PACKET_SIZE = 32_767
        private const val DNS_PORT = 53
        private const val UPSTREAM_TIMEOUT_MS = 5_000
        private const val TUN_ADDRESS = "10.233.0.2"
        private const val FAKE_DNS_ADDRESS = "10.233.0.1"
        private const val UPSTREAM_DNS_ADDRESS = "1.1.1.1"
    }
}
