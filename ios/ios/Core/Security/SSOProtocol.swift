import CryptoKit
import Foundation
import Security

struct SSOCredentials: Sendable {
    var baseURL: URL
    var token: String
    var privateToken: String?
}

enum SSOProtocol {
    static let callbackScheme = "mobilemoodle"
    private static let maxAge: TimeInterval = 10 * 60

    static func createPassport() -> UInt64 {
        var value: UInt64 = 0
        let status = SecRandomCopyBytes(kSecRandomDefault, MemoryLayout<UInt64>.size, &value)
        return status == errSecSuccess ? value & UInt64.max >> 1 : UInt64(Date().timeIntervalSince1970 * 1_000)
    }

    static func launchURL(config: MoodlePublicConfig, passport: UInt64) throws -> URL {
        let selected = config.launchURL.flatMap { MoodleURL.isAllowed(baseURL: config.canonicalURL, candidate: $0) ? $0 : nil }
            ?? MoodleURL.endpoint("admin/tool/mobile/launch.php", at: config.canonicalURL)
        guard var components = URLComponents(url: selected, resolvingAgainstBaseURL: false) else {
            throw MoodleError(code: "invalid_sso", message: String(localized: "error.sso.invalid"))
        }
        components.queryItems = (components.queryItems ?? []) + [
            URLQueryItem(name: "service", value: "moodle_mobile_app"),
            URLQueryItem(name: "passport", value: String(passport)),
            URLQueryItem(name: "urlscheme", value: callbackScheme),
        ]
        guard let result = components.url else { throw MoodleError(code: "invalid_sso", message: String(localized: "error.sso.invalid")) }
        return result
    }

    static func validate(callback: URL, pending: PendingSSO, now: Date = Date()) throws -> SSOCredentials {
        guard callback.scheme == callbackScheme,
              callback.absoluteString.lowercased().hasPrefix("\(callbackScheme)://token=")
        else { throw MoodleError(code: "invalid_sso", message: String(localized: "error.sso.callback"), isRecoverable: false) }
        guard now.timeIntervalSince(pending.createdAt) >= 0, now.timeIntervalSince(pending.createdAt) <= maxAge else {
            throw MoodleError(code: "expired_sso", message: String(localized: "error.sso.expired"), isRecoverable: false)
        }
        let prefix = "\(callbackScheme)://token="
        let encodedPayload = callback.absoluteString.dropFirst(prefix.count).split(whereSeparator: { $0 == "#" || $0 == "?" }).first.map(String.init) ?? ""
        let payload = encodedPayload.removingPercentEncoding ?? encodedPayload
        let parts = payload.components(separatedBy: ":::")
        guard parts.count >= 2, !parts[1].isEmpty else {
            throw MoodleError(code: "invalid_sso", message: String(localized: "error.sso.payload"), isRecoverable: false)
        }
        let expected = Insecure.MD5.hash(data: Data((pending.baseURL.absoluteString + String(pending.passport)).utf8))
            .map { String(format: "%02x", $0) }.joined()
        guard constantTimeEqual(parts[0], expected) else {
            throw MoodleError(code: "invalid_sso", message: String(localized: "error.sso.signature"), isRecoverable: false)
        }
        return SSOCredentials(baseURL: try MoodleURL.normalize(pending.baseURL.absoluteString), token: parts[1], privateToken: parts.count > 2 && !parts[2].isEmpty ? parts[2] : nil)
    }

    private static func constantTimeEqual(_ left: String, _ right: String) -> Bool {
        let lhs = Array(left.utf8), rhs = Array(right.utf8)
        guard lhs.count == rhs.count else { return false }
        return zip(lhs, rhs).reduce(UInt8(0)) { $0 | ($1.0 ^ $1.1) } == 0
    }
}
