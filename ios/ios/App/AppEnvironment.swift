import Foundation

@MainActor
final class AppEnvironment {
    let store: MoodleStore
    let keychain: KeychainStore
    let auth: MoodleAuthRepository
    let repository: MoodleRepository
    let notifications: NotificationService
    let backgroundSync: BackgroundSyncService

    init(inMemory: Bool = false, protocolClasses: [AnyClass]? = nil) throws {
        let store = try MoodleStore(inMemory: inMemory)
        let keychain = KeychainStore()
        let parser = MoodleHTMLParser()
        let mapper = MoodleMapper(html: parser)
        let transport = MoodleTransport(protocolClasses: protocolClasses)
        let sessions = HTMLSessionPool(keychain: keychain, protocolClasses: protocolClasses)
        let html = DefaultHTMLMoodleDataSource(sessions: sessions, parser: parser, mapper: mapper)
        let repository = DefaultMoodleRepository(transport: transport, store: store, keychain: keychain, html: html, mapper: mapper)
        self.store = store; self.keychain = keychain
        auth = DefaultMoodleAuthRepository(transport: transport, store: store, keychain: keychain, html: html, mapper: mapper)
        self.repository = repository
        notifications = NotificationService(store: store)
        backgroundSync = BackgroundSyncService(store: store, repository: repository, notifications: notifications)
    }
}
