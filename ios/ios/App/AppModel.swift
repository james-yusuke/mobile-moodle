import Foundation
import Observation
import SwiftUI

enum AppRoute: Hashable {
    case course(Int64)
    case module(Int64, MoodleModule)
    case assignment(MoodleAssignment)
    case conversation(Int64)
    case newMessage
}

enum PortalDestination: String, CaseIterable, Identifiable {
    case home, courses, messages, calendar
    var id: Self { self }
    var label: LocalizedStringKey {
        switch self {
        case .home: "tab.home"
        case .courses: "tab.courses"
        case .messages: "tab.messages"
        case .calendar: "tab.calendar"
        }
    }
    var icon: String { switch self { case .home: "house.fill"; case .courses: "books.vertical.fill"; case .messages: "bubble.left.and.bubble.right.fill"; case .calendar: "calendar" } }
}

@MainActor @Observable
final class AppModel {
    var accounts: [SiteAccount] = []
    var activeAccount: SiteAccount?
    var snapshot = PortalSnapshot()
    var pendingConfig: MoodlePublicConfig?
    var isLoading = false
    var isOnline = true
    var errorMessage: String?
    var selectedDestination: PortalDestination = .home
    var path = NavigationPath()
    var searchedUsers: [MoodleMessageUser] = []
    var messageSendState: MessageSendState = .idle
    var courseSections: [Int64: [MoodleSection]] = [:]
    var courseAssignments: [Int64: [MoodleAssignment]] = [:]
    var assignmentStatuses: [Int64: AssignmentSubmissionStatus] = [:]
    var moduleContents: [Int64: MoodleModuleContent] = [:]
    var messagePreviewEnabled = false
    var notificationRoute: (accountID: String, conversationID: Int64)?

    @ObservationIgnored private let environment: AppEnvironment
    @ObservationIgnored private let connectivity = ConnectivityMonitor()
    @ObservationIgnored private let sso = SSOAuthSession()
    @ObservationIgnored private var started = false

    init(
        environment: AppEnvironment,
        initialOnlineState: Bool = true,
        observesConnectivity: Bool = true
    ) {
        self.environment = environment
        isOnline = initialOnlineState
        messagePreviewEnabled = UserDefaults.standard.bool(forKey: "showMessagePreview")
        if observesConnectivity {
            connectivity.onChange = { [weak self] online in Task { @MainActor in self?.isOnline = online } }
        }
    }

    func start() async {
        guard !started else { return }; started = true
        reloadAccounts()
        #if DEBUG
        let isUITesting = ProcessInfo.processInfo.environment["MOBILE_MOODLE_UI_TESTING"] == "1" || UserDefaults.standard.bool(forKey: "MobileMoodleUITesting")
        #else
        let isUITesting = false
        #endif
        if !isUITesting { await environment.notifications.requestAuthorization() }
        environment.backgroundSync.register()
        environment.backgroundSync.schedule()
        if let activeAccount { await refresh(accountID: activeAccount.id, showLoading: snapshot.courses.isEmpty) }
        openPendingNotificationIfPossible()
    }

    func inspectSite(_ url: String) async {
        await perform { pendingConfig = try await environment.auth.inspectSite(url) }
    }

    func login(username: String, password: String) async {
        guard let pendingConfig else { return }
        await perform {
            let account = try await environment.auth.login(config: pendingConfig, username: username, password: password)
            self.pendingConfig = nil; reloadAccounts(); try environment.auth.activate(accountID: account.id); reloadAccounts()
            await refresh(accountID: account.id, showLoading: true)
        }
    }

    func loginWithSSO() async {
        guard let pendingConfig else { return }
        await perform {
            let launch = try environment.auth.beginSSO(config: pendingConfig)
            let callback = try await sso.authenticate(url: launch)
            let account = try await environment.auth.completeSSO(callback: callback)
            self.pendingConfig = nil; reloadAccounts(); try environment.auth.activate(accountID: account.id); reloadAccounts()
            await refresh(accountID: account.id, showLoading: true)
        }
    }

    func handleSSOCallback(_ url: URL) async {
        await perform {
            let account = try await environment.auth.completeSSO(callback: url)
            pendingConfig = nil; reloadAccounts(); try environment.auth.activate(accountID: account.id); reloadAccounts()
            await refresh(accountID: account.id, showLoading: true)
        }
    }

    func reauthenticate(username: String, password: String) async {
        guard let activeAccount else { return }
        await perform {
            _ = try await environment.auth.reauthenticate(accountID: activeAccount.id, username: username, password: password)
            reloadAccounts(); await refresh(accountID: activeAccount.id, showLoading: true)
        }
    }

    func activate(_ accountID: String) async {
        do { try environment.auth.activate(accountID: accountID); path = NavigationPath(); selectedDestination = .home; reloadAccounts(); await refresh(accountID: accountID, showLoading: true) }
        catch { show(error) }
    }

    func remove(_ accountID: String) {
        do { try environment.auth.remove(accountID: accountID); reloadAccounts(); snapshot = activeAccount.flatMap { try? environment.repository.snapshot(accountID: $0.id) } ?? PortalSnapshot() }
        catch { show(error) }
    }

    func refresh(accountID: String? = nil, showLoading: Bool = false) async {
        guard let id = accountID ?? activeAccount?.id else { return }
        if showLoading { isLoading = true }
        do {
            if isOnline { try await environment.repository.sync(accountID: id); try await environment.repository.syncMessages(accountID: id, allowNotifications: true) }
            reloadAccounts(); snapshot = try environment.repository.snapshot(accountID: id)
            await environment.notifications.announceNewMessages(accountID: id, showPreview: messagePreviewEnabled)
        } catch { show(error); snapshot = (try? environment.repository.snapshot(accountID: id)) ?? snapshot }
        isLoading = false
    }

    func refreshCourse(_ courseID: Int64) async {
        guard let account = activeAccount else { return }
        do {
            let sections = try await environment.repository.refreshCourse(accountID: account.id, courseID: courseID)
            courseSections[courseID] = sections
            courseAssignments[courseID] = try? await environment.repository.assignments(accountID: account.id, courseID: courseID)
            snapshot = try environment.repository.snapshot(accountID: account.id)
        } catch { show(error) }
    }

    func loadModule(_ module: MoodleModule) async {
        guard let account = activeAccount else { return }
        do { moduleContents[module.id] = try await environment.repository.moduleContent(accountID: account.id, module: module) }
        catch { show(error) }
    }

    func loadAssignment(_ assignment: MoodleAssignment) async {
        guard let account = activeAccount else { return }
        do { assignmentStatuses[assignment.id] = try await environment.repository.submissionStatus(accountID: account.id, assignmentID: assignment.id) }
        catch { show(error) }
    }

    func submit(_ assignment: MoodleAssignment, text: String, fileURL: URL?) async -> Bool {
        guard let account = activeAccount, isOnline else { return false }
        do {
            try await environment.repository.submitAssignment(accountID: account.id, assignment: assignment, onlineText: text, fileURL: fileURL)
            await loadAssignment(assignment); return true
        } catch { show(error); return false }
    }

    func refreshConversations() async {
        guard let account = activeAccount else { return }
        guard isOnline else {
            snapshot = (try? environment.repository.snapshot(accountID: account.id)) ?? snapshot
            return
        }
        do { _ = try await environment.repository.refreshConversations(accountID: account.id, offset: 0); snapshot = try environment.repository.snapshot(accountID: account.id) }
        catch { show(error) }
    }

    func refreshMessages(conversationID: Int64, offset: Int = 0, markRead: Bool = false) async {
        guard let account = activeAccount else { return }
        guard isOnline else {
            snapshot = (try? environment.repository.snapshot(accountID: account.id)) ?? snapshot
            return
        }
        do {
            _ = try await environment.repository.refreshMessages(accountID: account.id, conversationID: conversationID, offset: offset)
            if markRead { try await environment.repository.markConversationRead(accountID: account.id, conversationID: conversationID) }
            snapshot = try environment.repository.snapshot(accountID: account.id)
        } catch { show(error) }
    }

    func searchUsers(_ query: String) async {
        guard let account = activeAccount, query.count >= 2 else { searchedUsers = []; return }
        do { searchedUsers = try await environment.repository.searchUsers(accountID: account.id, query: query) }
        catch { searchedUsers = []; show(error) }
    }

    func sendMessage(conversationID: Int64, text: String) async -> Bool {
        guard let account = activeAccount, isOnline else { messageSendState = .failed(String(localized: "error.offline")); return false }
        messageSendState = .sending
        do {
            try await environment.repository.sendMessage(accountID: account.id, conversationID: conversationID, text: text)
            snapshot = try environment.repository.snapshot(accountID: account.id); messageSendState = .sent(conversationID: conversationID); return true
        } catch {
            messageSendState = .failed(error.localizedDescription)
            try? environment.repository.saveDraft(accountID: account.id, key: conversationDraftKey(conversationID), body: text)
            show(error)
            return false
        }
    }

    func startConversation(userID: Int64, text: String) async -> Int64? {
        guard let account = activeAccount, isOnline else { return nil }
        messageSendState = .sending
        do {
            let id = try await environment.repository.startConversation(accountID: account.id, userID: userID, text: text)
            snapshot = try environment.repository.snapshot(accountID: account.id); messageSendState = .sent(conversationID: id); return id
        } catch {
            messageSendState = .failed(error.localizedDescription)
            try? environment.repository.saveDraft(accountID: account.id, key: userDraftKey(userID), body: text)
            show(error)
            return nil
        }
    }

    func draft(key: String) -> String { guard let account = activeAccount else { return "" }; return (try? environment.repository.draft(accountID: account.id, key: key)?.body) ?? "" }
    func saveDraft(key: String, body: String) { guard let account = activeAccount else { return }; try? environment.repository.saveDraft(accountID: account.id, key: key, body: body) }

    func markNotification(_ notification: MoodleNotification) async {
        guard let account = activeAccount, isOnline else { return }
        do { try await environment.repository.markNotificationRead(accountID: account.id, notificationID: notification.id); snapshot = try environment.repository.snapshot(accountID: account.id) }
        catch { show(error) }
    }

    func authenticatedURL(_ url: URL) async -> URL { guard let account = activeAccount else { return url }; return await environment.repository.authenticatedWebURL(accountID: account.id, targetURL: url) }
    func cachedFile(_ file: MoodleFile) async -> URL? { guard let account = activeAccount else { return nil }; do { return try await environment.repository.cacheFile(accountID: account.id, file: file) } catch { show(error); return nil } }

    func setMessagePreview(_ enabled: Bool) { messagePreviewEnabled = enabled; UserDefaults.standard.set(enabled, forKey: "showMessagePreview") }
    func receiveNotification(accountID: String, conversationID: Int64) { notificationRoute = (accountID, conversationID); openPendingNotificationIfPossible() }

    private func reloadAccounts() {
        accounts = (try? environment.auth.accounts()) ?? []
        activeAccount = (try? environment.auth.activeAccount()) ?? accounts.first
        if let activeAccount { snapshot = (try? environment.repository.snapshot(accountID: activeAccount.id)) ?? snapshot }
    }

    private func openPendingNotificationIfPossible() {
        guard let route = notificationRoute, accounts.contains(where: { $0.id == route.accountID }) else { return }
        Task { await activate(route.accountID); path.append(AppRoute.conversation(route.conversationID)); notificationRoute = nil }
    }

    private func perform(_ operation: () async throws -> Void) async {
        isLoading = true; errorMessage = nil
        do { try await operation() } catch { show(error) }
        isLoading = false
    }

    private func show(_ error: Error) {
        if let moodleError = error as? MoodleError,
           moodleError.requiresReauthentication,
           let accountID = activeAccount?.id {
            try? environment.auth.requireReauthentication(accountID: accountID)
            path = NavigationPath()
            errorMessage = nil
            reloadAccounts()
            return
        }
        errorMessage = error.localizedDescription
    }
}
