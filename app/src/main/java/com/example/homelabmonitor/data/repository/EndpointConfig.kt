package com.example.homelabmonitor.data.repository

import java.net.URI

data class ParsedEndpoint(
    val baseUrl: String,
    val scheme: String,
    val host: String,
    val port: Int,
    val usedDefaultPort: Boolean,
)

object EndpointConfig {
    const val DEFAULT_HTTP_PORT = 8099

    fun parse(input: String): Result<ParsedEndpoint> = runCatching {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "Informe o host do homelab." }
        require(!trimmed.any(Char::isWhitespace)) { "O endereço não pode conter espaços." }

        val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
        val uri = URI(withScheme)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "Use http:// ou https://." }
        require(uri.userInfo == null) { "Usuário e senha não devem ficar no endereço." }
        require(uri.rawQuery == null && uri.rawFragment == null) { "O endereço não deve ter query ou fragmento." }

        val rawPath = uri.rawPath.orEmpty().trimEnd('/')
        require(rawPath.isEmpty() || rawPath == "/v1/metrics") {
            "Use somente o host ou o caminho /v1/metrics."
        }

        val host = (uri.host ?: extractBracketedHost(uri.rawAuthority)).orEmpty()
            .removePrefix("[")
            .removeSuffix("]")
        require(isValidHost(host)) { "Host inválido. Use um nome DNS, IP ou endereço Tailscale." }

        val port = if (uri.port == -1) {
            if (scheme == "http") DEFAULT_HTTP_PORT else 443
        } else {
            uri.port
        }
        require(port in 1..65535) { "Porta inválida." }

        val formattedHost = if (host.contains(':')) "[$host]" else host
        ParsedEndpoint(
            baseUrl = "$scheme://$formattedHost:$port",
            scheme = scheme,
            host = host,
            port = port,
            usedDefaultPort = uri.port == -1,
        )
    }

    fun normalize(input: String): String = parse(input).getOrThrow().baseUrl

    private fun extractBracketedHost(authority: String?): String? {
        val value = authority ?: return null
        if (!value.startsWith("[")) return null
        val closing = value.indexOf(']')
        return if (closing > 1) value.substring(0, closing + 1) else null
    }

    private fun isValidHost(host: String): Boolean {
        if (host.isBlank() || host.length > 253) return false
        if (host.contains(':')) return host.matches(Regex("^[0-9a-fA-F:.]+$"))
        if (host.matches(Regex("^[0-9.]+$"))) {
            val octets = host.split('.')
            return octets.size == 4 && octets.all { it.toIntOrNull()?.let { value -> value in 0..255 } == true }
        }
        return host.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.firstOrNull()?.let { it.isLetterOrDigit() } == true &&
                label.lastOrNull()?.let { it.isLetterOrDigit() } == true &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
    }
}
