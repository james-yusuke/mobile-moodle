import Foundation
import UserNotifications

@MainActor
final class NotificationService {
    private let store: MoodleStore
    init(store: MoodleStore) { self.store = store }

    func requestAuthorization() async {
        _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])
    }

    func announceNewMessages(accountID: String, showPreview: Bool) async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional,
              let messages = try? store.unannouncedMessages(accountID: accountID), !messages.isEmpty
        else {
            if let values = try? store.unannouncedMessages(accountID: accountID) { try? store.markMessagesAnnounced(accountID: accountID, ids: Set(values.map(\.id))) }
            return
        }
        for message in messages {
            let content = UNMutableNotificationContent()
            content.title = message.senderName.isEmpty ? String(localized: "messages") : message.senderName
            content.body = showPreview ? String(message.bodyText.prefix(180)) : String(localized: "notification.message.new")
            content.sound = .default
            content.categoryIdentifier = "MOODLE_MESSAGE"
            content.userInfo = ["accountID": accountID, "conversationID": message.conversationID]
            let request = UNNotificationRequest(identifier: "\(accountID):\(message.id)", content: content, trigger: nil)
            try? await UNUserNotificationCenter.current().add(request)
        }
        try? store.markMessagesAnnounced(accountID: accountID, ids: Set(messages.map(\.id)))
    }
}

@MainActor
final class NotificationRouter {
    static let shared = NotificationRouter()
    var route: ((String, Int64) -> Void)?
    private init() {}
}
