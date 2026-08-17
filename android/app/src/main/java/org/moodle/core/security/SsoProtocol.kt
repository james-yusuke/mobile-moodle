package org.moodle.core.security

import android.net.Uri
import org.moodle.core.network.MoodleUrl
import java.security.MessageDigest
import java.security.SecureRandom

data class PendingSso(
    val baseUrl: String,
    val passport: Long,
    val createdAtEpochSeconds: Long,
)

data class SsoCredentials(
    val baseUrl: String,
    val token: String,
    val privateToken: String?,
)

object SsoProtocol {
    const val CALLBACK_SCHEME = "mobilemoodle"
    private const val MAX_AGE_SECONDS = 10 * 60

    fun createPassport(): Long = SecureRandom().nextLong().ushr(1)

    fun launchUrl(
        baseUrl: String,
        launchUrl: String?,
        passport: Long,
        service: String = "moodle_mobile_app",
    ): String {
        val normalized = MoodleUrl.normalize(baseUrl)
        val target = launchUrl?.takeIf { MoodleUrl.sameSite(normalized, it) }
            ?: "$normalized/admin/tool/mobile/launch.php"
        return Uri.parse(target).buildUpon()
            .appendQueryParameter("service", service)
            .appendQueryParameter("passport", passport.toString())
            .appendQueryParameter("urlscheme", CALLBACK_SCHEME)
            .build()
            .toString()
    }

    fun validateCallback(
        callback: Uri,
        pending: PendingSso,
        nowEpochSeconds: Long,
    ): SsoCredentials {
        require(callback.scheme == CALLBACK_SCHEME && callback.schemeSpecificPart.startsWith("//token=")) {
            "Unexpected SSO callback"
        }
        require(nowEpochSeconds - pending.createdAtEpochSeconds in 0..MAX_AGE_SECONDS) {
            "The SSO request expired"
        }

        val payload = callback.schemeSpecificPart.substringAfter("//token=", "")
            .substringBefore('#')
            .substringBefore('?')
        val parts = payload.split(":::")
        require(parts.size >= 2 && parts[1].isNotBlank()) { "Invalid SSO payload" }
        require(constantTimeEquals(parts[0], md5(pending.baseUrl + pending.passport))) {
            "Invalid SSO signature"
        }

        return SsoCredentials(
            baseUrl = MoodleUrl.normalize(pending.baseUrl),
            token = parts[1],
            privateToken = parts.getOrNull(2)?.takeIf(String::isNotBlank),
        )
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.US_ASCII))
        .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(first: String, second: String): Boolean =
        MessageDigest.isEqual(first.toByteArray(), second.toByteArray())
}
