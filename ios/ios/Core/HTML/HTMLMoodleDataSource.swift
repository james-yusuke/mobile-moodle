import Foundation
import SwiftSoup

struct HTMLLoginResult: Sendable { var identity: HTMLSiteIdentity }
struct HTMLMessageSession: Sendable { var userID: Int64; var sessionKey: String; var cachedAt: Date }

protocol HTMLMoodleDataSource: Sendable {
    func login(accountID: String, config: MoodlePublicConfig, username: String, password: String) async throws -> HTMLLoginResult
    func courses(account: SiteAccount) async throws -> [MoodleCourse]
    func sections(account: SiteAccount, courseID: Int64) async throws -> [MoodleSection]
    func grades(account: SiteAccount, courses: [MoodleCourse]) async throws -> [MoodleGrade]
    func events(account: SiteAccount) async throws -> [MoodleCalendarEvent]
    func notifications(account: SiteAccount) async throws -> [MoodleNotification]
    func conversations(account: SiteAccount, offset: Int, limit: Int) async throws -> [MoodleConversation]
    func messages(account: SiteAccount, conversationID: Int64, offset: Int, limit: Int) async throws -> [MoodleMessage]
    func searchUsers(account: SiteAccount, query: String, limit: Int) async throws -> [MoodleMessageUser]
    func sendMessage(account: SiteAccount, conversationID: Int64, text: String) async throws -> MoodleMessage?
    func sendDirectMessage(account: SiteAccount, userID: Int64, text: String) async throws -> Int64
    func markRead(account: SiteAccount, conversationID: Int64) async throws
    func moduleContent(account: SiteAccount, title: String, url: URL) async throws -> MoodleModuleContent
    func download(account: SiteAccount, url: URL) async throws -> Data
    func clear(accountID: String)
}

final class DefaultHTMLMoodleDataSource: HTMLMoodleDataSource, @unchecked Sendable {
    private let sessions: HTMLSessionPool
    private let parser: MoodleHTMLParser
    private let mapper: MoodleMapper
    private let lock = NSLock()
    private var messageSessions: [String: HTMLMessageSession] = [:]

    init(sessions: HTMLSessionPool, parser: MoodleHTMLParser, mapper: MoodleMapper) {
        self.sessions = sessions; self.parser = parser; self.mapper = mapper
    }

    func login(accountID: String, config: MoodlePublicConfig, username: String, password: String) async throws -> HTMLLoginResult {
        let loginURL = MoodleURL.endpoint("login/index.php", at: config.canonicalURL)
        let loginPage = try await page(accountID: accountID, baseURL: config.canonicalURL, url: loginURL, allowLoginPage: true)
        guard let form = try parser.loginForm(loginPage.document) else {
            throw MoodleError(code: "login_form_missing", message: String(localized: "error.login.form"), isRecoverable: false)
        }
        guard MoodleURL.isAllowed(baseURL: config.canonicalURL, candidate: form.actionURL) else {
            throw MoodleError(code: "cross_origin", message: String(localized: "error.url.cross_origin"), isRecoverable: false)
        }
        var fields = form.hiddenFields.filter { $0.key != "username" && $0.key != "password" }
        fields["username"] = username; fields["password"] = password
        let data = formBody(fields)
        let response = try await execute(accountID: accountID, baseURL: config.canonicalURL, url: form.actionURL, method: "POST", body: data, contentType: "application/x-www-form-urlencoded", accept: "text/html")
        let document = try parser.document(html: String(decoding: response.data, as: UTF8.self), url: response.url)
        if try parser.isLoginPage(document) {
            let acceptedCookies = sessions.hasCookies(accountID: accountID)
            clear(accountID: accountID)
            let message = try document.select(".loginerrors, .alert-danger, [role=alert]").first()?.text() ?? String(localized: "error.login.credentials")
            throw MoodleError(code: acceptedCookies ? "invalid_credentials" : "session_cookie_missing", message: message, isRecoverable: false)
        }
        let identity = try parser.identity(document, fallbackSiteName: config.siteName)
        if let userID = identity.userID, let key = identity.sessionKey?.nonEmpty { cacheSession(accountID: accountID, userID: userID, key: key) }
        try sessions.persist(accountID: accountID)
        return HTMLLoginResult(identity: identity)
    }

    func courses(account: SiteAccount) async throws -> [MoodleCourse] {
        let coursesPage = MoodleURL.endpoint("my/courses.php", at: account.baseURL)
        if let parsed = try? parser.courses(try await page(accountID: account.id, baseURL: account.baseURL, url: coursesPage).document), !parsed.isEmpty { return parsed }
        let dashboard = try await page(accountID: account.id, baseURL: account.baseURL, url: MoodleURL.endpoint("my/", at: account.baseURL))
        let parsed = try parser.courses(dashboard.document)
        if !parsed.isEmpty { return parsed }
        let identity = try parser.identity(dashboard.document, fallbackSiteName: account.siteName)
        guard let key = identity.sessionKey else { return [] }
        let value = try await ajax(account: account, key: key, method: "core_course_get_enrolled_courses_by_timeline_classification", arguments: [
            "offset": .number(0), "limit": .number(0), "classification": .string("all"), "sort": .string("fullname"),
            "customfieldname": .string(""), "customfieldvalue": .string(""),
        ])
        let raw = value.object?["courses"] ?? .array([])
        return try mapper.courses(raw)
    }

    func sections(account: SiteAccount, courseID: Int64) async throws -> [MoodleSection] {
        var components = URLComponents(url: MoodleURL.endpoint("course/view.php", at: account.baseURL), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "id", value: String(courseID))]
        return try parser.courseSections(try await page(accountID: account.id, baseURL: account.baseURL, url: components.url!).document, courseID: courseID)
    }

    func grades(account: SiteAccount, courses: [MoodleCourse]) async throws -> [MoodleGrade] {
        var values: [MoodleGrade] = []
        for course in courses {
            var components = URLComponents(url: MoodleURL.endpoint("grade/report/user/index.php", at: account.baseURL), resolvingAgainstBaseURL: false)!
            components.queryItems = [URLQueryItem(name: "id", value: String(course.id))]
            if let parsed = try? parser.grades(try await page(accountID: account.id, baseURL: account.baseURL, url: components.url!).document, courseID: course.id) { values += parsed }
        }
        return values
    }

    func events(account: SiteAccount) async throws -> [MoodleCalendarEvent] {
        var components = URLComponents(url: MoodleURL.endpoint("calendar/view.php", at: account.baseURL), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "view", value: "upcoming")]
        return try parser.events(try await page(accountID: account.id, baseURL: account.baseURL, url: components.url!).document)
    }

    func notifications(account: SiteAccount) async throws -> [MoodleNotification] {
        let url = MoodleURL.endpoint("message/output/popup/notifications.php", at: account.baseURL)
        return try parser.notifications(try await page(accountID: account.id, baseURL: account.baseURL, url: url).document)
    }

    func conversations(account: SiteAccount, offset: Int = 0, limit: Int = 30) async throws -> [MoodleConversation] {
        let session = try await sessionContext(account)
        let value = try await ajax(account: account, key: session.sessionKey, method: "core_message_get_conversations", arguments: [
            "userid": .number(Double(session.userID)), "limitfrom": .number(Double(max(0, offset))), "limitnum": .number(Double(min(max(limit, 1), 50))),
        ])
        return try mapper.conversations(value, currentUserID: session.userID)
    }

    func messages(account: SiteAccount, conversationID: Int64, offset: Int = 0, limit: Int = 50) async throws -> [MoodleMessage] {
        let session = try await sessionContext(account)
        let value = try await ajax(account: account, key: session.sessionKey, method: "core_message_get_conversation_messages", arguments: [
            "currentuserid": .number(Double(session.userID)), "convid": .number(Double(conversationID)),
            "limitfrom": .number(Double(max(0, offset))), "limitnum": .number(Double(min(max(limit, 1), 50))),
            "newest": .bool(true), "timefrom": .number(0),
        ])
        return try mapper.messages(value, conversationID: conversationID, currentUserID: session.userID)
    }

    func searchUsers(account: SiteAccount, query: String, limit: Int = 30) async throws -> [MoodleMessageUser] {
        let session = try await sessionContext(account)
        let value = try await ajax(account: account, key: session.sessionKey, method: "core_message_message_search_users", arguments: [
            "userid": .number(Double(session.userID)), "search": .string(query.trimmingCharacters(in: .whitespacesAndNewlines)),
            "limitfrom": .number(0), "limitnum": .number(Double(min(max(limit, 1), 50))),
        ])
        return Array(mapper.users(value, currentUserID: session.userID).prefix(limit))
    }

    func sendMessage(account: SiteAccount, conversationID: Int64, text: String) async throws -> MoodleMessage? {
        let session = try await sessionContext(account)
        let value = try await ajax(account: account, key: session.sessionKey, method: "core_message_send_messages_to_conversation", arguments: [
            "conversationid": .number(Double(conversationID)),
            "messages": .array([.object(["text": .string(text), "textformat": .number(0)])]),
        ])
        let messageValue = value.array?.first ?? value.object?["messages"]?.array?.first
        return try messageValue.flatMap { try mapper.message($0, conversationID: conversationID, currentUserID: session.userID) }
    }

    func sendDirectMessage(account: SiteAccount, userID: Int64, text: String) async throws -> Int64 {
        let session = try await sessionContext(account)
        let value = try await ajax(account: account, key: session.sessionKey, method: "core_message_send_instant_messages", arguments: [
            "messages": .array([.object([
                "touserid": .number(Double(userID)), "text": .string(text), "textformat": .number(0),
                "clientmsgid": .string("ios-\(UUID().uuidString)"),
            ])]),
        ])
        if let id = value.array?.first?.object?.int64("conversationid") ?? value.object?.int64("conversationid"), id > 0 { return id }
        if let between = try? await ajax(account: account, key: session.sessionKey, method: "core_message_get_conversation_between_users", arguments: [
            "userid": .number(Double(session.userID)), "otheruserid": .number(Double(userID)), "includecontactrequests": .bool(false),
            "includeprivacyinfo": .bool(false), "memberlimit": .number(0), "memberoffset": .number(0), "messagelimit": .number(1),
            "messageoffset": .number(0), "newestmessagesfirst": .bool(true),
        ]), let id = between.object?.int64("id") { return id }
        if let id = try await conversations(account: account, offset: 0, limit: 30).first(where: { $0.members.contains { $0.id == userID } })?.id { return id }
        throw MoodleError(code: "message_sent_refresh_needed", message: String(localized: "error.message.refresh"))
    }

    func markRead(account: SiteAccount, conversationID: Int64) async throws {
        let session = try await sessionContext(account)
        _ = try await ajax(account: account, key: session.sessionKey, method: "core_message_mark_all_conversation_messages_as_read", arguments: [
            "userid": .number(Double(session.userID)), "conversationid": .number(Double(conversationID)),
        ])
    }

    func moduleContent(account: SiteAccount, title: String, url: URL) async throws -> MoodleModuleContent {
        let value = try await page(accountID: account.id, baseURL: account.baseURL, url: url)
        return try parser.moduleContent(value.document, fallbackTitle: title, originalURL: value.url)
    }

    func download(account: SiteAccount, url: URL) async throws -> Data {
        try await execute(accountID: account.id, baseURL: account.baseURL, url: url, method: "GET", body: nil, contentType: nil, accept: "*/*").data
    }

    func clear(accountID: String) {
        lock.withLock { _ = messageSessions.removeValue(forKey: accountID) }
        sessions.clear(accountID: accountID)
    }

    private func sessionContext(_ account: SiteAccount) async throws -> HTMLMessageSession {
        let cached = lock.withLock { messageSessions[account.id] }
        if let cached, Date().timeIntervalSince(cached.cachedAt) < 5 * 60 { return cached }
        let dashboard = try await page(accountID: account.id, baseURL: account.baseURL, url: MoodleURL.endpoint("my/", at: account.baseURL))
        let identity = try parser.identity(dashboard.document, fallbackSiteName: account.siteName)
        guard let key = identity.sessionKey, let userID = account.userID ?? identity.userID else {
            throw MoodleError(code: "session_context_missing", message: String(localized: "error.session.context"))
        }
        cacheSession(accountID: account.id, userID: userID, key: key)
        return HTMLMessageSession(userID: userID, sessionKey: key, cachedAt: Date())
    }

    private func cacheSession(accountID: String, userID: Int64, key: String) {
        lock.withLock { messageSessions[accountID] = HTMLMessageSession(userID: userID, sessionKey: key, cachedAt: Date()) }
    }

    private func ajax(account: SiteAccount, key: String, method: String, arguments: [String: JSONValue]) async throws -> JSONValue {
        var components = URLComponents(url: MoodleURL.endpoint("lib/ajax/service.php", at: account.baseURL), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "sesskey", value: key), URLQueryItem(name: "info", value: method)]
        let body = try JSONEncoder().encode([AJAXCall(methodName: method, arguments: arguments)])
        let response = try await execute(accountID: account.id, baseURL: account.baseURL, url: components.url!, method: "POST", body: body, contentType: "application/json", accept: "application/json")
        guard let first = try JSONDecoder().decode(JSONValue.self, from: response.data).array?.first?.object else {
            throw MoodleError(code: "invalid_ajax_response", message: String(localized: "error.response.invalid"))
        }
        if first.bool("error") || first["exception"] != nil {
            let exception = first["exception"]?.object
            let code = exception?.string("errorcode", default: first.string("errorcode", default: "ajax_error")) ?? "ajax_error"
            if ["invalidsesskey", "requireloginerror", "sessionipnomatch"].contains(code) {
                lock.withLock { _ = messageSessions.removeValue(forKey: account.id) }
                throw MoodleError(code: "session_expired", message: String(localized: "error.session.expired"), isRecoverable: false)
            }
            throw MoodleError(code: code, message: exception?.string("message", default: first.string("message", default: String(localized: "error.moodle"))) ?? String(localized: "error.moodle"))
        }
        return first["data"] ?? .null
    }

    private func page(accountID: String, baseURL: URL, url: URL, allowLoginPage: Bool = false) async throws -> ParsedHTMLPage {
        let response = try await execute(accountID: accountID, baseURL: baseURL, url: url, method: "GET", body: nil, contentType: nil, accept: "text/html")
        let document = try parser.document(html: String(decoding: response.data, as: UTF8.self), url: response.url)
        let loginPage = try parser.isLoginPage(document)
        if !allowLoginPage && loginPage { throw MoodleError(code: "session_expired", message: String(localized: "error.session.expired"), isRecoverable: false) }
        try sessions.persist(accountID: accountID)
        return ParsedHTMLPage(document: document, url: response.url)
    }

    private func execute(accountID: String, baseURL: URL, url: URL, method: String, body: Data?, contentType: String?, accept: String) async throws -> (data: Data, url: URL) {
        guard MoodleURL.isAllowed(baseURL: baseURL, candidate: url) else { throw MoodleError(code: "cross_origin", message: String(localized: "error.url.cross_origin"), isRecoverable: false) }
        var request = URLRequest(url: url); request.httpMethod = method; request.httpBody = body
        if let contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        request.setValue(accept, forHTTPHeaderField: "Accept")
        do {
            let client = try sessions.client(accountID: accountID, baseURL: baseURL)
            client.cookieJar.requestHeaders(for: url).forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
            let (data, response) = try await client.session.data(for: request)
            guard let http = response as? HTTPURLResponse, let finalURL = http.url,
                  MoodleURL.isAllowed(baseURL: baseURL, candidate: finalURL), (200..<300).contains(http.statusCode)
            else { throw MoodleError(code: "http_error", message: String(localized: "error.http")) }
            client.cookieJar.store(response: http)
            return (data, finalURL)
        } catch let error as MoodleError { throw error }
        catch { throw MoodleError(code: "network_error", message: String(localized: "error.network")) }
    }

    private func formBody(_ values: [String: String]) -> Data {
        values.sorted { $0.key < $1.key }.map { "\($0.key.formEncoded)=\($0.value.formEncoded)" }.joined(separator: "&").data(using: .utf8)!
    }
}

private extension String {
    var formEncoded: String { addingPercentEncoding(withAllowedCharacters: CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "-._~"))) ?? self }
    var nonEmpty: String? { isEmpty ? nil : self }
}
