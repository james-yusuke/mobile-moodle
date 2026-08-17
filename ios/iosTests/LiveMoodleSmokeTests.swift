import XCTest
@testable import MobileMoodle

@MainActor
final class LiveMoodleSmokeTests: XCTestCase {
    func testReadOnlyLoginCoursesAndConversationListWhenConfigured() async throws {
        guard let credentials = localCredentials()
        else { throw XCTSkip("Set the ignored ios/local-test.env values to run the read-only smoke test.") }

        let app = try AppEnvironment(inMemory: true)
        let config = try await app.auth.inspectSite(credentials.siteURL)
        let account = try await app.auth.login(config: config, username: credentials.username, password: credentials.password)
        defer { try? app.auth.remove(accountID: account.id) }
        try await app.repository.sync(accountID: account.id)
        _ = try await app.repository.refreshConversations(accountID: account.id, offset: 0)
        let snapshot = try app.repository.snapshot(accountID: account.id)
        XCTAssertFalse(snapshot.courses.isEmpty)
    }

    private func localCredentials() -> (siteURL: String, username: String, password: String)? {
        var values = ProcessInfo.processInfo.environment
        let localFile = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appending(path: "local-test.env")
        if let contents = try? String(contentsOf: localFile, encoding: .utf8) {
            for line in contents.split(whereSeparator: \.isNewline) {
                let parts = line.split(separator: "=", maxSplits: 1).map(String.init)
                guard parts.count == 2 else { continue }
                let key = parts[0].replacingOccurrences(of: "export ", with: "").trimmingCharacters(in: .whitespaces)
                let value = parts[1].trimmingCharacters(in: CharacterSet(charactersIn: " \t\"'"))
                values[key] = value
            }
        }
        guard let siteURL = values["MOODLE_TEST_URL"], let username = values["MOODLE_TEST_USERNAME"], let password = values["MOODLE_TEST_PASSWORD"] else { return nil }
        return (siteURL, username, password)
    }
}
