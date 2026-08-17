import Foundation
import Security

struct StoredCredentials: Codable, Sendable {
    var token: String?
    var privateToken: String?
    var cookies: [StoredCookie] = []
}

struct StoredCookie: Codable, Hashable, Sendable {
    var name: String
    var value: String
    var domain: String
    var path: String
    var expiresAt: Date?
    var isSecure: Bool

    init?(_ cookie: HTTPCookie) {
        guard !cookie.name.isEmpty else { return nil }
        name = cookie.name; value = cookie.value; domain = cookie.domain; path = cookie.path
        expiresAt = cookie.expiresDate; isSecure = cookie.isSecure
    }

    var cookie: HTTPCookie? {
        var properties: [HTTPCookiePropertyKey: Any] = [
            .name: name, .value: value, .domain: domain, .path: path,
            .secure: isSecure ? "TRUE" : "FALSE",
        ]
        if let expiresAt { properties[.expires] = expiresAt }
        return HTTPCookie(properties: properties)
    }
}

struct PendingSSO: Codable, Sendable {
    var baseURL: URL
    var passport: UInt64
    var createdAt: Date
}

final class KeychainStore: @unchecked Sendable {
    private let service = "org.moodle.ios.credentials"
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    func credentials(accountID: String) throws -> StoredCredentials? {
        try value(StoredCredentials.self, account: "account:\(accountID)")
    }

    func saveCredentials(_ value: StoredCredentials, accountID: String) throws {
        try save(value, account: "account:\(accountID)")
    }

    func deleteCredentials(accountID: String) throws { try delete(account: "account:\(accountID)") }

    func savePendingSSO(_ value: PendingSSO) throws { try save(value, account: "pending-sso") }

    func consumePendingSSO() throws -> PendingSSO? {
        let value = try value(PendingSSO.self, account: "pending-sso")
        try delete(account: "pending-sso")
        return value
    }

    private func value<T: Decodable>(_ type: T.Type, account: String) throws -> T? {
        var query = baseQuery(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw keychainError(status) }
        return try decoder.decode(type, from: data)
    }

    private func save<T: Encodable>(_ value: T, account: String) throws {
        let data = try encoder.encode(value)
        let query = baseQuery(account: account)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var inserted = query; attributes.forEach { inserted[$0.key] = $0.value }
            let insertStatus = SecItemAdd(inserted as CFDictionary, nil)
            guard insertStatus == errSecSuccess else { throw keychainError(insertStatus) }
        } else if status != errSecSuccess { throw keychainError(status) }
    }

    private func delete(account: String) throws {
        let status = SecItemDelete(baseQuery(account: account) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw keychainError(status) }
    }

    private func baseQuery(account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: account]
    }

    private func keychainError(_ status: OSStatus) -> MoodleError {
        MoodleError(code: "secure_storage", message: SecCopyErrorMessageString(status, nil) as String? ?? String(localized: "error.secure_storage"))
    }
}
