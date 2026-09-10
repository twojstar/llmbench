package com.twojstar.llmbench.data.streambench

import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response

/** DNS boundary used only by Streambench media requests. */
internal class StreambenchPublicDns(
    private val delegate: Dns = Dns.SYSTEM
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val publicAddresses = delegate.lookup(hostname).filter(::isPublicStreambenchAddress)
        if (publicAddresses.isEmpty()) {
            throw UnknownHostException("Streambench hostname did not resolve to a public address")
        }
        return publicAddresses
    }
}

/**
 * Applies the shared strict URL policy to each independently-created media request.
 *
 * OkHttp's DNS guard remains authoritative for the resolved destination. Protocol-changing redirects
 * are separately disabled on the Streambench client before this interceptor is installed.
 */
internal class StreambenchRemoteRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (StreambenchM3uParser.validateRemotePlaybackUrl(request.url.toString()) == null) {
            throw IOException("Streambench blocked a non-public media request")
        }
        return chain.proceed(request)
    }
}

/** Reject addresses that should never be reached from an untrusted imported playlist. */
internal fun isPublicStreambenchAddress(address: InetAddress): Boolean {
    if (
        address.isAnyLocalAddress ||
        address.isLoopbackAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress
    ) return false

    val bytes = address.address
    return when (bytes.size) {
        IPV4_SIZE -> !isBlockedIpv4(bytes)
        IPV6_SIZE -> !isBlockedIpv6(bytes)
        else -> false
    }
}

private fun isBlockedIpv4(bytes: ByteArray): Boolean {
    val first = bytes[0].unsigned()
    val second = bytes[1].unsigned()
    val third = bytes[2].unsigned()

    return first == 0 ||
        first == 10 ||
        first == 127 ||
        first >= 224 ||
        (first == 100 && second in 64..127) ||
        (first == 169 && second == 254) ||
        (first == 172 && second in 16..31) ||
        (first == 192 && second == 0 && third == 0) ||
        (first == 192 && second == 0 && third == 2) ||
        (first == 192 && second == 168) ||
        (first == 198 && second in 18..19) ||
        (first == 198 && second == 51 && third == 100) ||
        (first == 203 && second == 0 && third == 113)
}

private fun isBlockedIpv6(bytes: ByteArray): Boolean {
    val first = bytes[0].unsigned()
    val second = bytes[1].unsigned()

    if ((first and 0xFE) == 0xFC) return true
    if (first == 0x20 && second == 0x01 && bytes[2].unsigned() == 0x0D && bytes[3].unsigned() == 0xB8) {
        return true
    }

    val mappedIpv4 = ipv4MappedAddress(bytes)
    return mappedIpv4?.let(::isBlockedIpv4) == true
}

private fun ipv4MappedAddress(bytes: ByteArray): ByteArray? {
    val hasMappedPrefix = bytes.take(10).all { it == 0.toByte() } &&
        bytes[10] == 0xFF.toByte() &&
        bytes[11] == 0xFF.toByte()
    return if (hasMappedPrefix) bytes.copyOfRange(12, 16) else null
}

private fun Byte.unsigned(): Int = toInt() and 0xFF

private const val IPV4_SIZE = 4
private const val IPV6_SIZE = 16
