package com.twojstar.llmbench.web

import com.twojstar.llmbench.data.model.WebAiService

internal fun javascriptStringLiteral(value: String): String = buildString(value.length + 2) {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> if (char.code < 0x20) {
                append("\\u")
                append(char.code.toString(16).padStart(4, '0'))
            } else {
                append(char)
            }
        }
    }
    append('"')
}

internal fun providerRuntimeGuardScript(
    service: WebAiService,
    offProviderResult: String
): String {
    val allowedHosts = ProviderWebTweakRegistry.ownedHosts(service)
        .joinToString(prefix = "[", postfix = "]") { javascriptStringLiteral(it) }
    return """
        var allowedHosts = $allowedHosts;
        var currentHost = String(location.hostname || '').toLowerCase();
        if (currentHost.slice(-1) === '.') currentHost = currentHost.slice(0, -1);
        var owned = allowedHosts.some(function(host) {
            return currentHost === host || currentHost.slice(-(host.length + 1)) === '.' + host;
        });
        if (String(location.protocol || '').toLowerCase() !== 'https:' || !owned) {
            return $offProviderResult;
        }
    """.trimIndent()
}

internal fun documentRuntimeGuardScript(
    pageUrl: String,
    offProviderResult: String
): String {
    val expectedUrl = javascriptStringLiteral(pageUrl.substringBefore('#'))
    return """
        function llmbenchDocumentUrl(value) {
            return String(value || '').split('#')[0];
        }
        if (llmbenchDocumentUrl(location.href) !== $expectedUrl) {
            return $offProviderResult;
        }
    """.trimIndent()
}
