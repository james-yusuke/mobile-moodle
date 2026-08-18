import Foundation

@MainActor
enum UITestSeeder {
    static func seed(store: MoodleStore, authenticationState: AuthenticationState = .authenticated) throws {
        let account = SiteAccount(id: "ui-test-account", baseURL: URL(string: "https://example.invalid/moodle")!, siteName: "Sample University",
                                  username: "student", userID: 42, fullName: "Moodle Student", connectionMode: .nativeApi,
                                  capabilities: SiteCapabilities(functions: ["core_enrol_get_users_courses", "core_course_get_contents", "core_message_get_conversations", "core_message_get_conversation_messages", "core_message_send_messages_to_conversation", "core_message_message_search_users", "core_message_send_instant_messages", "core_calendar_get_calendar_upcoming_view"]),
                                  authenticationState: authenticationState,
                                  lastSync: Date(), isActive: true)
        try store.saveAccount(account); try store.activateAccount(account.id)
        try store.replaceCourses([
            MoodleCourse(id: 101, shortName: "DES101", fullName: "Design Thinking and Innovation", summaryHTML: "<p>Learn to solve meaningful problems.</p>", startDate: Date(), endDate: Calendar.current.date(byAdding: .month, value: 4, to: Date())),
            MoodleCourse(id: 202, shortName: "CS204", fullName: "Mobile Application Engineering", summaryHTML: "", startDate: Date(), endDate: nil),
        ], accountID: account.id)
        try store.replaceEvents([
            MoodleCalendarEvent(id: 1, name: "Project proposal deadline", descriptionHTML: "", startDate: Calendar.current.date(byAdding: .day, value: 2, to: Date())!, courseID: 101, actionURL: nil),
        ], accountID: account.id)
        let conversation = MoodleConversation(id: 77, type: .individual, name: "Course Advisor", members: [.init(id: 9, fullName: "Course Advisor", isCurrentUser: false, canMessage: true)], latestMessagePreview: "Your appointment is confirmed.", latestMessageAt: Date(), unreadCount: 2, isFavourite: false, canReply: true)
        try store.replaceConversations([conversation], accountID: account.id)
        try store.upsertMessages([
            MoodleMessage(id: 1, conversationID: 77, senderID: 9, senderName: "Course Advisor", bodyText: "Your appointment is confirmed.", bodyHTML: "<p>Your appointment is confirmed.</p>", createdAt: Date().addingTimeInterval(-300), isMine: false, isRead: false),
            MoodleMessage(id: 2, conversationID: 77, senderID: 42, senderName: "Moodle Student", bodyText: "Thank you.", bodyHTML: "<p>Thank you.</p>", createdAt: Date(), isMine: true, isRead: true),
        ], accountID: account.id, conversationID: 77, suppressNotifications: true)
    }
}
