import Foundation

struct TokenResponse: Decodable, Sendable {
    var token: String?
    var privateToken: String?
    var error: String?
    var errorCode: String?

    enum CodingKeys: String, CodingKey {
        case token, error
        case privateToken = "privatetoken"
        case errorCode = "errorcode"
    }
}

struct AJAXCall: Encodable, Sendable {
    var index: Int = 0
    var methodName: String
    var arguments: [String: JSONValue] = [:]

    enum CodingKeys: String, CodingKey { case index, methodName = "methodname", arguments = "args" }
}

final class MoodleTransport: @unchecked Sendable {
    private let sessionFactory: (URL) -> URLSession

    init(protocolClasses: [AnyClass]? = nil) {
        sessionFactory = { baseURL in
            let configuration = URLSessionConfiguration.ephemeral
            configuration.timeoutIntervalForRequest = 30
            configuration.timeoutIntervalForResource = 90
            configuration.waitsForConnectivity = false
            configuration.httpAdditionalHeaders = ["Accept": "application/json", "User-Agent": "MobileMoodle-iOS/1.0"]
            if let protocolClasses { configuration.protocolClasses = protocolClasses }
            return URLSession(configuration: configuration, delegate: SameOriginRedirectDelegate(baseURL: baseURL), delegateQueue: nil)
        }
    }

    func publicConfig(baseURL: URL, language: String) async throws -> MoodlePublicConfig {
        var components = URLComponents(url: MoodleURL.endpoint("lib/ajax/service-nologin.php", at: baseURL), resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "info", value: "tool_mobile_get_public_config"),
            URLQueryItem(name: "lang", value: String(language.prefix(8))),
        ]
        let body = try JSONEncoder().encode([AJAXCall(methodName: "tool_mobile_get_public_config")])
        let value = try await jsonRequest(url: components.url!, baseURL: baseURL, method: "POST", body: body, contentType: "application/json")
        guard let response = value.array?.first?.object else { throw MoodleError(code: "public_config", message: String(localized: "error.public_config.empty")) }
        try throwIfAJAXError(response)
        guard let config = response["data"]?.object else { throw MoodleError(code: "public_config", message: String(localized: "error.public_config.empty")) }
        let canonicalInput = config.string("httpswwwroot", default: config.string("wwwroot", default: baseURL.absoluteString))
        let canonical = try MoodleURL.normalize(canonicalInput)
        guard MoodleURL.isAllowed(baseURL: baseURL, candidate: canonical) else {
            throw MoodleError(code: "redirected_site", message: String(localized: "error.url.redirect"), isRecoverable: false)
        }
        return MoodlePublicConfig(
            canonicalURL: canonical,
            siteName: config.string("sitename", default: canonical.host ?? canonical.absoluteString),
            mobileWebServiceEnabled: config.int("enablemobilewebservice") == 1,
            loginType: config.int("typeoflogin", default: 1),
            launchURL: config.string("launchurl").nonEmpty.flatMap(URL.init(string:)),
            showLoginForm: config.int("showloginform", default: 1) != 0
        )
    }

    func token(baseURL: URL, username: String, password: String) async throws -> TokenResponse {
        let url = MoodleURL.endpoint("login/token.php", at: baseURL)
        let body = formBody(["username": username, "password": password, "service": "moodle_mobile_app"])
        let data = try await dataRequest(url: url, baseURL: baseURL, method: "POST", body: body, contentType: "application/x-www-form-urlencoded")
        return try JSONDecoder().decode(TokenResponse.self, from: data)
    }

    func rest(baseURL: URL, token: String, function: String, parameters: [String: String] = [:]) async throws -> JSONValue {
        let url = MoodleURL.endpoint("webservice/rest/server.php", at: baseURL)
        let fields = ["wstoken": token, "wsfunction": function, "moodlewsrestformat": "json"].merging(parameters) { _, new in new }
        let data = try await dataRequest(url: url, baseURL: baseURL, method: "POST", body: formBody(fields), contentType: "application/x-www-form-urlencoded")
        let result = try JSONDecoder().decode(JSONValue.self, from: data)
        try throwIfMoodleError(result)
        return result
    }

    func upload(baseURL: URL, token: String, fileURL: URL) async throws -> JSONValue {
        let boundary = "MobileMoodle-\(UUID().uuidString)"
        var urlComponents = URLComponents(url: MoodleURL.endpoint("webservice/upload.php", at: baseURL), resolvingAgainstBaseURL: false)!
        urlComponents.queryItems = [URLQueryItem(name: "token", value: token), URLQueryItem(name: "filearea", value: "draft")]
        var body = Data()
        body.append("--\(boundary)\r\nContent-Disposition: form-data; name=\"file_1\"; filename=\"\(fileURL.lastPathComponent.safeHeader)\"\r\nContent-Type: application/octet-stream\r\n\r\n".data(using: .utf8)!)
        body.append(try Data(contentsOf: fileURL)); body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        let data = try await dataRequest(url: urlComponents.url!, baseURL: baseURL, method: "POST", body: body, contentType: "multipart/form-data; boundary=\(boundary)")
        let result = try JSONDecoder().decode(JSONValue.self, from: data); try throwIfMoodleError(result); return result
    }

    func jsonRequest(url: URL, baseURL: URL, method: String, body: Data?, contentType: String?) async throws -> JSONValue {
        let data = try await dataRequest(url: url, baseURL: baseURL, method: method, body: body, contentType: contentType)
        return try JSONDecoder().decode(JSONValue.self, from: data)
    }

    private func dataRequest(url: URL, baseURL: URL, method: String, body: Data?, contentType: String?) async throws -> Data {
        guard MoodleURL.isAllowed(baseURL: baseURL, candidate: url) else { throw MoodleError(code: "cross_origin", message: String(localized: "error.url.cross_origin"), isRecoverable: false) }
        var request = URLRequest(url: url); request.httpMethod = method; request.httpBody = body
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        do {
            let (data, response) = try await sessionFactory(baseURL).data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                throw MoodleError(code: "http_error", message: String(localized: "error.http"))
            }
            return data
        } catch let error as MoodleError { throw error }
        catch { throw MoodleError(code: "network_error", message: String(localized: "error.network")) }
    }

    private func throwIfAJAXError(_ object: [String: JSONValue]) throws {
        guard object.bool("error") else { return }
        let exception = object["exception"]?.object
        throw MoodleError(code: exception?.string("errorcode", default: "ajax_error") ?? "ajax_error", message: exception?.string("message", default: String(localized: "error.moodle")) ?? String(localized: "error.moodle"))
    }

    private func throwIfMoodleError(_ value: JSONValue) throws {
        guard let object = value.object, object["exception"] != nil || object["errorcode"] != nil else { return }
        throw MoodleError(code: object.string("errorcode", default: "webservice_error"), message: object.string("message", default: String(localized: "error.moodle")))
    }

    private func formBody(_ values: [String: String]) -> Data {
        values.sorted { $0.key < $1.key }.map { "\($0.key.formEncoded)=\($0.value.formEncoded)" }.joined(separator: "&").data(using: .utf8)!
    }
}

private extension String {
    var formEncoded: String { addingPercentEncoding(withAllowedCharacters: CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))) ?? self }
    var safeHeader: String { replacingOccurrences(of: "\"", with: "_").replacingOccurrences(of: "\r", with: "_").replacingOccurrences(of: "\n", with: "_") }
    var nonEmpty: String? { isEmpty ? nil : self }
}
