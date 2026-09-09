package com.twojstar.llmbench.data.skills

import java.security.MessageDigest

internal fun localSkillSourceDigest(source: String): String =
    sha256Hex(source.encodeToByteArray())

internal fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex = CharArray(digest.size * 2)
    digest.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xFF
        hex[index * 2] = HEX_DIGITS[value ushr 4]
        hex[index * 2 + 1] = HEX_DIGITS[value and 0x0F]
    }
    return hex.concatToString()
}

internal fun isSha256Hex(value: String): Boolean =
    value.length == SHA256_HEX_CHARS && value.all { it in HEX_DIGITS }

private const val SHA256_HEX_CHARS = 64
private const val HEX_DIGITS = "0123456789abcdef"
