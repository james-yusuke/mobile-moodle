import UIKit
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .badge]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        guard let accountID = info["accountID"] as? String else { return }
        let conversationID: Int64?
        if let value = info["conversationID"] as? Int64 { conversationID = value }
        else if let value = info["conversationID"] as? NSNumber { conversationID = value.int64Value }
        else { conversationID = nil }
        guard let conversationID else { return }
        await MainActor.run { NotificationRouter.shared.route?(accountID, conversationID) }
    }
}
