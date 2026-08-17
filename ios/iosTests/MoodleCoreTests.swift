import CryptoKit
import XCTest
@testable import MobileMoodle

@MainActor
final class MoodleCoreTests: XCTestCase {
    func testURLNormalizationAcceptsLoginAndSubdirectoryURLs() throws {
        XCTAssertEqual(
            try MoodleURL.normalize("  EXAMPLE.invalid/campus/login/index.php?return=1 ").absoluteString,
            "https://example.invalid/campus"
        )
        XCTAssertThrowsError(try MoodleURL.normalize("http://example.invalid/moodle"))
        XCTAssertThrowsError(try MoodleURL.normalize("https://user:password@example.invalid/moodle"))
    }

    func testSameOriginRequiresHTTPSPortAndBasePath() throws {
        let base = try XCTUnwrap(URL(string: "https://example.invalid:8443/moodle"))
        XCTAssertTrue(MoodleURL.isAllowed(baseURL: base, candidate: try XCTUnwrap(URL(string: "https://example.invalid:8443/moodle/course/view.php?id=1"))))
        XCTAssertFalse(MoodleURL.isAllowed(baseURL: base, candidate: try XCTUnwrap(URL(string: "https://example.invalid/moodle"))))
        XCTAssertFalse(MoodleURL.isAllowed(baseURL: base, candidate: try XCTUnwrap(URL(string: "https://example.invalid/other"))))
        XCTAssertFalse(MoodleURL.isAllowed(baseURL: base, candidate: try XCTUnwrap(URL(string: "http://example.invalid:8443/moodle"))))
    }

    func testCapabilitiesAreDeterminedPerFunction() {
        let capabilities = SiteCapabilities(functions: [
            "core_enrol_get_users_courses",
            "core_message_get_conversations",
            "core_message_get_conversation_messages",
            "core_message_send_messages_to_conversation",
        ])
        XCTAssertTrue(capabilities.courses)
        XCTAssertTrue(capabilities.messages.canList)
        XCTAssertTrue(capabilities.messages.canRead)
        XCTAssertTrue(capabilities.messages.canSend)
        XCTAssertFalse(capabilities.messages.canSearchUsers)
        XCTAssertFalse(capabilities.assignmentSubmission)
    }

    func testAccountCookieJarIsIsolatedAndPathScoped() throws {
        let loginURL = try XCTUnwrap(URL(string: "https://example.invalid/moodle/login/index.php"))
        let jar = AccountCookieJar()
        let otherAccountJar = AccountCookieJar()
        jar.store(headers: ["Set-Cookie": "MoodleSession=test-value; Path=/moodle; Secure; HttpOnly"], for: loginURL)

        XCTAssertEqual(jar.requestHeaders(for: try XCTUnwrap(URL(string: "https://example.invalid/moodle/my/")))["Cookie"], "MoodleSession=test-value")
        XCTAssertTrue(jar.requestHeaders(for: try XCTUnwrap(URL(string: "https://example.invalid/other"))).isEmpty)
        XCTAssertTrue(jar.requestHeaders(for: try XCTUnwrap(URL(string: "http://example.invalid/moodle/my/"))).isEmpty)
        XCTAssertTrue(otherAccountJar.requestHeaders(for: try XCTUnwrap(URL(string: "https://example.invalid/moodle/my/"))).isEmpty)
    }

    func testHTMLIdentityAndSanitizer() throws {
        let parser = MoodleHTMLParser()
        let html = """
        <html><head><title>Example Campus | Dashboard</title></head>
        <body class="theme-boost"><span class="usertext">Student Name</span>
        <script>M.cfg = {userid: 42, sesskey: 'session-key'};</script></body></html>
        """
        let document = try parser.document(html: html, url: try XCTUnwrap(URL(string: "https://example.invalid/moodle/my/")))
        let identity = try parser.identity(document, fallbackSiteName: "Fallback")
        XCTAssertEqual(identity.userID, 42)
        XCTAssertEqual(identity.sessionKey, "session-key")
        XCTAssertEqual(identity.themeFamily, .modern)

        let sanitized = try parser.sanitize("<p onclick='bad()'>Safe <a href='https://example.invalid/help'>link</a><script>bad()</script><img src='x'></p>", messageOnly: true)
        XCTAssertTrue(sanitized.contains("Safe"))
        XCTAssertTrue(sanitized.contains("https://example.invalid/help"))
        XCTAssertFalse(sanitized.contains("onclick"))
        XCTAssertFalse(sanitized.contains("script"))
        XCTAssertFalse(sanitized.contains("img"))
    }

    func testSSORejectsExpiredOrInvalidSignatures() throws {
        let baseURL = try XCTUnwrap(URL(string: "https://example.invalid/moodle"))
        let pending = PendingSSO(baseURL: baseURL, passport: 123, createdAt: Date(timeIntervalSince1970: 100))
        let invalid = try XCTUnwrap(URL(string: "mobilemoodle://token=invalid%3A%3A%3Atoken"))
        XCTAssertThrowsError(try SSOProtocol.validate(callback: invalid, pending: pending, now: Date(timeIntervalSince1970: 101)))
        XCTAssertThrowsError(try SSOProtocol.validate(callback: invalid, pending: pending, now: Date(timeIntervalSince1970: 1_000)))
    }

    func testPresentationAggregationAndStablePalette() {
        let now = Date()
        let later = MoodleCalendarEvent(id: 2, name: "Later", descriptionHTML: "", startDate: now.addingTimeInterval(500), courseID: nil, actionURL: nil)
        let sooner = MoodleCalendarEvent(id: 1, name: "Sooner", descriptionHTML: "", startDate: now.addingTimeInterval(100), courseID: nil, actionURL: nil)
        let snapshot = PortalSnapshot(events: [later, sooner], conversations: [
            MoodleConversation(id: 1, type: .individual, name: "A", members: [], latestMessagePreview: "", latestMessageAt: now, unreadCount: 3, isFavourite: false, canReply: true),
            MoodleConversation(id: 2, type: .individual, name: "B", members: [], latestMessagePreview: "", latestMessageAt: now, unreadCount: 2, isFavourite: false, canReply: true),
        ])
        XCTAssertEqual(snapshot.nextEvent?.id, sooner.id)
        XCTAssertEqual(snapshot.unreadMessages, 5)
        XCTAssertEqual(coursePalette(seed: 1234, dark: false), coursePalette(seed: 1234, dark: false))
        XCTAssertNotEqual(coursePalette(seed: 1234, dark: false), coursePalette(seed: 1234, dark: true))
    }

    func testSwiftDataSeparatesAccountsCapsMessagesAndKeepsDrafts() throws {
        let store = try MoodleStore(inMemory: true)
        let first = SiteAccount(id: "first", baseURL: URL(string: "https://first.invalid")!, siteName: "First", connectionMode: .nativeApi)
        let second = SiteAccount(id: "second", baseURL: URL(string: "https://second.invalid")!, siteName: "Second", connectionMode: .nativeHtml)
        try store.saveAccount(first)
        try store.saveAccount(second)
        try store.replaceCourses([MoodleCourse(id: 7, shortName: "A", fullName: "First Course", summaryHTML: "", startDate: nil, endDate: nil)], accountID: first.id)
        try store.replaceCourses([MoodleCourse(id: 7, shortName: "B", fullName: "Second Course", summaryHTML: "", startDate: nil, endDate: nil)], accountID: second.id)
        XCTAssertEqual(try store.snapshot(accountID: first.id).courses.first?.fullName, "First Course")
        XCTAssertEqual(try store.snapshot(accountID: second.id).courses.first?.fullName, "Second Course")

        let messages = (1...205).map { id in
            MoodleMessage(id: Int64(id), conversationID: 99, senderID: 1, senderName: "Sender", bodyText: "Message \(id)", bodyHTML: "", createdAt: Date(timeIntervalSince1970: Double(id)), isMine: false, isRead: false)
        }
        try store.upsertMessages(messages, accountID: first.id, conversationID: 99)
        XCTAssertEqual(try store.snapshot(accountID: first.id).messages[99]?.count, 200)
        XCTAssertEqual(try store.unannouncedMessages(accountID: first.id).count, 200)
        try store.markMessagesAnnounced(accountID: first.id, ids: Set(messages.map(\.id)))
        XCTAssertTrue(try store.unannouncedMessages(accountID: first.id).isEmpty)
        try store.saveDraft(accountID: first.id, key: "conversation:99", body: "Unsent")
        XCTAssertEqual(try store.draft(accountID: first.id, key: "conversation:99")?.body, "Unsent")
        try store.deleteAccount(first.id)
        XCTAssertNil(try store.account(id: first.id))
        XCTAssertNotNil(try store.account(id: second.id))
    }

    func testPublicConfigAndMoodleErrorMappingThroughURLProtocol() async throws {
        MockURLProtocol.handler = { request in
            let data = Data("""
            [{"error":false,"data":{"httpswwwroot":"https://example.invalid/moodle","sitename":"Example","enablemobilewebservice":1,"typeoflogin":1,"showloginform":1}}]
            """.utf8)
            return (HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!, data)
        }
        let transport = MoodleTransport(protocolClasses: [MockURLProtocol.self])
        let config = try await transport.publicConfig(baseURL: URL(string: "https://example.invalid/moodle")!, language: "en")
        XCTAssertEqual(config.connectionMode, .nativeApi)
        XCTAssertEqual(config.siteName, "Example")

        MockURLProtocol.handler = { request in
            let data = Data("{\"exception\":\"moodle_exception\",\"errorcode\":\"invalidtoken\",\"message\":\"Expired\"}".utf8)
            return (HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!, data)
        }
        do {
            _ = try await transport.rest(baseURL: URL(string: "https://example.invalid/moodle")!, token: "test-token", function: "core_webservice_get_site_info")
            XCTFail("Expected a typed Moodle error")
        } catch let error as MoodleError {
            XCTAssertEqual(error.code, "invalidtoken")
        }
    }
}

private final class MockURLProtocol: URLProtocol, @unchecked Sendable {
    nonisolated(unsafe) static var handler: (@Sendable (URLRequest) throws -> (HTTPURLResponse, Data))?
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        do {
            guard let handler = Self.handler else { throw URLError(.badServerResponse) }
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch { client?.urlProtocol(self, didFailWithError: error) }
    }
    override func stopLoading() {}
}
