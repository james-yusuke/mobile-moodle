import XCTest

@MainActor
final class MobileMoodleUITests: XCTestCase {
    private func launch(dark: Bool = false, additionalArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "-MobileMoodleUITesting", "YES", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"] + additionalArguments
        app.launchEnvironment["MOBILE_MOODLE_UI_TESTING"] = "1"
        if dark {
            app.launchArguments.append("--dark-mode")
            app.launchArguments.append(contentsOf: ["-MobileMoodleDarkMode", "YES"])
            app.launchEnvironment["MOBILE_MOODLE_DARK_MODE"] = "1"
        }
        app.launch()
        return app
    }

    func testPrimaryNavigationAndSeededContent() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["Sample University"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["tab.home"].exists)
        app.buttons["tab.courses"].tap()
        XCTAssertTrue(app.staticTexts["Your courses"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.buttons["course.101"].exists)
        app.buttons["tab.messages"].tap()
        XCTAssertTrue(app.buttons["conversation.77"].waitForExistence(timeout: 2))
        app.buttons["conversation.77"].tap()
        XCTAssertTrue(app.staticTexts["Course Advisor"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.descendants(matching: .any)["message.2"].waitForExistence(timeout: 2))
        attachScreenshot(app, name: "message-conversation")
    }

    func testNotificationsAndDarkAppearanceLaunch() {
        let app = launch(dark: true)
        XCTAssertTrue(app.buttons["notifications.button"].waitForExistence(timeout: 5))
        app.buttons["notifications.button"].tap()
        XCTAssertTrue(app.navigationBars["Notifications"].waitForExistence(timeout: 2))
        attachScreenshot(app, name: "dark-notifications")
    }

    func testExpiredSessionOpensReauthenticationScreen() {
        let app = launch(additionalArguments: ["--session-expired"])
        XCTAssertTrue(app.descendants(matching: .any)["reauthentication.screen"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Sign in again"].exists)
        XCTAssertFalse(app.buttons["tab.home"].exists)
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
