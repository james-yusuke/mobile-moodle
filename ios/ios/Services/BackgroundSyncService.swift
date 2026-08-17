import BackgroundTasks
import Foundation

@MainActor
final class BackgroundSyncService {
    static let taskIdentifier = "org.moodle.ios.message-refresh"
    private let store: MoodleStore
    private let repository: MoodleRepository
    private let notifications: NotificationService
    private var registered = false

    init(store: MoodleStore, repository: MoodleRepository, notifications: NotificationService) {
        self.store = store; self.repository = repository; self.notifications = notifications
    }

    func register() {
        guard !registered else { return }; registered = true
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.taskIdentifier, using: nil) { [weak self] task in
            guard let refresh = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            Task { @MainActor in self?.handle(refresh) }
        }
    }

    func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: Self.taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    private func handle(_ task: BGAppRefreshTask) {
        schedule()
        let operation = Task { @MainActor [weak self] in
            guard let self else { task.setTaskCompleted(success: false); return }
            var success = true
            let accounts = (try? store.authenticatedAccounts()) ?? []
            for account in accounts {
                do {
                    try await repository.syncMessages(accountID: account.id, allowNotifications: true)
                    await notifications.announceNewMessages(accountID: account.id, showPreview: UserDefaults.standard.bool(forKey: "showMessagePreview"))
                } catch { success = false }
            }
            task.setTaskCompleted(success: success)
        }
        task.expirationHandler = { operation.cancel() }
    }
}
