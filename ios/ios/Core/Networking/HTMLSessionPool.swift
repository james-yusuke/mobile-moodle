import Foundation

final class AccountCookieJar: @unchecked Sendable {
    private let lock = NSLock()
    private var values: [HTTPCookie]

    init(cookies: [HTTPCookie] = []) { values = cookies }

    func requestHeaders(for url: URL) -> [String: String] {
        let cookies = lock.withLock {
            let now = Date()
            values = values.filter { $0.expiresDate.map { $0 > now } ?? true }
            return values.filter { Self.matches($0, url: url) }
        }
        return cookies.isEmpty ? [:] : HTTPCookie.requestHeaderFields(with: cookies)
    }

    func store(response: HTTPURLResponse) {
        guard let url = response.url else { return }
        let headers = response.allHeaderFields.reduce(into: [String: String]()) { result, field in
            guard let name = field.key as? String else { return }
            result[name] = String(describing: field.value)
        }
        store(headers: headers, for: url)
    }

    func store(headers: [String: String], for url: URL) {
        let incoming = HTTPCookie.cookies(withResponseHeaderFields: headers, for: url)
        guard !incoming.isEmpty else { return }
        lock.withLock {
            for cookie in incoming {
                values.removeAll { $0.name == cookie.name && $0.domain == cookie.domain && $0.path == cookie.path }
                if cookie.expiresDate.map({ $0 > Date() }) ?? true { values.append(cookie) }
            }
        }
    }

    func storedCookies() -> [StoredCookie] { lock.withLock { values.compactMap(StoredCookie.init) } }
    func isEmpty() -> Bool { lock.withLock { values.isEmpty } }
    func clear() { lock.withLock { values.removeAll() } }

    private static func matches(_ cookie: HTTPCookie, url: URL) -> Bool {
        guard let host = url.host?.lowercased(), !cookie.isSecure || url.scheme?.lowercased() == "https" else { return false }
        let domain = cookie.domain.lowercased().trimmingCharacters(in: CharacterSet(charactersIn: "."))
        let domainMatches = host == domain || host.hasSuffix(".\(domain)")
        let path = cookie.path.isEmpty ? "/" : cookie.path
        return domainMatches && url.path.hasPrefix(path)
    }
}

final class HTMLSessionPool: @unchecked Sendable {
    struct Client {
        var session: URLSession
        var cookieJar: AccountCookieJar
        var baseURL: URL
    }

    private let keychain: KeychainStore
    private let lock = NSLock()
    private var clients: [String: Client] = [:]
    private let protocolClasses: [AnyClass]?

    init(keychain: KeychainStore, protocolClasses: [AnyClass]? = nil) {
        self.keychain = keychain; self.protocolClasses = protocolClasses
    }

    func client(accountID: String, baseURL: URL) throws -> Client {
        try lock.withLock {
            if let client = clients[accountID] { return client }
            let stored = try keychain.credentials(accountID: accountID)?.cookies.compactMap(\.cookie) ?? []
            let jar = AccountCookieJar(cookies: stored)
            let configuration = URLSessionConfiguration.ephemeral
            configuration.httpCookieStorage = nil; configuration.httpShouldSetCookies = false
            configuration.timeoutIntervalForRequest = 30; configuration.timeoutIntervalForResource = 90
            configuration.httpAdditionalHeaders = ["User-Agent": "MobileMoodle-iOS/1.0", "Accept-Language": Locale.current.language.languageCode?.identifier ?? "en"]
            if let protocolClasses { configuration.protocolClasses = protocolClasses }
            let client = Client(
                session: URLSession(configuration: configuration, delegate: SameOriginRedirectDelegate(baseURL: baseURL, cookieJar: jar), delegateQueue: nil),
                cookieJar: jar,
                baseURL: baseURL
            )
            clients[accountID] = client
            return client
        }
    }

    func persist(accountID: String) throws {
        let client = lock.withLock { clients[accountID] }
        guard let client else { return }
        var credentials = try keychain.credentials(accountID: accountID) ?? StoredCredentials()
        credentials.cookies = client.cookieJar.storedCookies()
        try keychain.saveCredentials(credentials, accountID: accountID)
    }

    func hasCookies(accountID: String) -> Bool { lock.withLock { clients[accountID]?.cookieJar.isEmpty() == false } }

    func clear(accountID: String) {
        let client = lock.withLock { clients.removeValue(forKey: accountID) }
        client?.cookieJar.clear()
        client?.session.invalidateAndCancel()
    }
}
