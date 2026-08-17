import Foundation

enum MoodleURL {
    private static let knownSuffixes = [
        "/login/index.php", "/login/", "/login", "/index.php",
        "/admin/tool/mobile/launch.php", "/webservice/rest/server.php",
    ]

    static func normalize(_ input: String) throws -> URL {
        var value = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { throw MoodleError(code: "invalid_url", message: String(localized: "error.url.required"), isRecoverable: false) }
        if !value.contains("://") { value = "https://\(value)" }
        guard var components = URLComponents(string: value),
              components.scheme?.lowercased() == "https",
              let host = components.host?.lowercased(), !host.isEmpty,
              components.user == nil, components.password == nil
        else { throw MoodleError(code: "invalid_url", message: String(localized: "error.url.https"), isRecoverable: false) }

        components.scheme = "https"
        components.host = host
        if components.port == 443 { components.port = nil }
        components.query = nil
        components.fragment = nil
        var path = components.percentEncodedPath
        while path.contains("//") { path = path.replacingOccurrences(of: "//", with: "/") }
        while path.hasSuffix("/") { path.removeLast() }
        if let suffix = knownSuffixes.first(where: { path.lowercased().hasSuffix($0) }) {
            path.removeLast(suffix.count)
            while path.hasSuffix("/") { path.removeLast() }
        }
        components.percentEncodedPath = path
        guard let result = components.url else { throw MoodleError(code: "invalid_url", message: String(localized: "error.url.invalid"), isRecoverable: false) }
        return result
    }

    static func isAllowed(baseURL: URL, candidate: URL) -> Bool {
        guard baseURL.scheme?.lowercased() == "https", candidate.scheme?.lowercased() == "https",
              baseURL.host?.lowercased() == candidate.host?.lowercased(),
              effectivePort(baseURL) == effectivePort(candidate)
        else { return false }
        let basePath = baseURL.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let candidatePath = candidate.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return basePath.isEmpty || candidatePath == basePath || candidatePath.hasPrefix("\(basePath)/")
    }

    static func endpoint(_ path: String, at baseURL: URL) -> URL {
        var result = baseURL
        for component in path.split(separator: "/") { result.append(path: String(component)) }
        return result
    }

    private static func effectivePort(_ url: URL) -> Int { url.port ?? 443 }
}

final class SameOriginRedirectDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    private let baseURL: URL
    private let cookieJar: AccountCookieJar?
    init(baseURL: URL, cookieJar: AccountCookieJar? = nil) { self.baseURL = baseURL; self.cookieJar = cookieJar }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping @Sendable (URLRequest?) -> Void
    ) {
        cookieJar?.store(response: response)
        guard let url = request.url, MoodleURL.isAllowed(baseURL: baseURL, candidate: url) else {
            completionHandler(nil)
            return
        }
        var redirected = request
        cookieJar?.requestHeaders(for: url).forEach { redirected.setValue($0.value, forHTTPHeaderField: $0.key) }
        completionHandler(redirected)
    }
}
