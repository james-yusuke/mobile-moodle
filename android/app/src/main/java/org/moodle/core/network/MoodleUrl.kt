package org.moodle.core.network

import java.net.IDN
import java.net.URI

object MoodleUrl {
    private val knownSuffixes = listOf(
        "/login/index.php",
        "/login/",
        "/login",
        "/index.php",
    )

    fun normalize(input: String): String {
        val candidate = input.trim()
        require(candidate.isNotEmpty()) { "Moodle URL is required" }

        val withScheme = if (candidate.contains("://")) candidate else "https://$candidate"
        val uri = runCatching { URI(withScheme) }.getOrElse {
            throw IllegalArgumentException("Invalid Moodle URL", it)
        }
        require(uri.scheme.equals("https", ignoreCase = true)) { "HTTPS is required" }
        require(!uri.host.isNullOrBlank()) { "A host name is required" }
        require(uri.userInfo == null) { "Credentials must not be included in the URL" }

        val asciiHost = IDN.toASCII(uri.host.lowercase())
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        var path = (uri.path ?: "").replace(Regex("/{2,}"), "/").trimEnd('/')
        val matchingSuffix = knownSuffixes.firstOrNull { path.endsWith(it, ignoreCase = true) }
        if (matchingSuffix != null) {
            path = path.dropLast(matchingSuffix.length).trimEnd('/')
        }

        return "https://$asciiHost$port$path"
    }

    fun sameSite(baseUrl: String, candidateUrl: String): Boolean {
        val base = runCatching { URI(normalize(baseUrl)) }.getOrNull() ?: return false
        val candidate = runCatching { URI(candidateUrl) }.getOrNull() ?: return false
        if (!candidate.scheme.equals("https", true) || !candidate.host.equals(base.host, true)) return false
        val basePort = if (base.port == -1) 443 else base.port
        val candidatePort = if (candidate.port == -1) 443 else candidate.port
        if (basePort != candidatePort) return false
        val basePath = base.path.trimEnd('/')
        return basePath.isEmpty() || candidate.path == basePath || candidate.path.startsWith("$basePath/")
    }
}
