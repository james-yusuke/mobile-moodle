import Foundation

@MainActor
protocol MoodleAuthRepository: AnyObject {
    func accounts() throws -> [SiteAccount]
    func activeAccount() throws -> SiteAccount?
    func inspectSite(_ inputURL: String) async throws -> MoodlePublicConfig
    func login(config: MoodlePublicConfig, username: String, password: String) async throws -> SiteAccount
    func reauthenticate(accountID: String, username: String, password: String) async throws -> SiteAccount
    func beginSSO(config: MoodlePublicConfig) throws -> URL
    func completeSSO(callback: URL) async throws -> SiteAccount
    func activate(accountID: String) throws
    func requireReauthentication(accountID: String) throws
    func remove(accountID: String) throws
}

@MainActor
final class DefaultMoodleAuthRepository: MoodleAuthRepository {
    private let transport: MoodleTransport
    private let store: MoodleStore
    private let keychain: KeychainStore
    private let html: HTMLMoodleDataSource
    private let mapper: MoodleMapper

    init(transport: MoodleTransport, store: MoodleStore, keychain: KeychainStore, html: HTMLMoodleDataSource, mapper: MoodleMapper) {
        self.transport = transport; self.store = store; self.keychain = keychain; self.html = html; self.mapper = mapper
    }

    func accounts() throws -> [SiteAccount] { try store.accounts() }
    func activeAccount() throws -> SiteAccount? { try store.activeAccount() }

    func inspectSite(_ inputURL: String) async throws -> MoodlePublicConfig {
        let baseURL = try MoodleURL.normalize(inputURL)
        return try await transport.publicConfig(baseURL: baseURL, language: Locale.current.language.languageCode?.identifier ?? "en")
    }

    func login(config: MoodlePublicConfig, username: String, password: String) async throws -> SiteAccount {
        let cleanedUsername = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanedUsername.isEmpty, !password.isEmpty else { throw MoodleError(code: "invalid_credentials", message: String(localized: "error.login.required"), isRecoverable: false) }
        if config.connectionMode == .nativeHtml {
            guard config.showLoginForm else { throw MoodleError(code: "sso_html_unsupported", message: String(localized: "error.sso.html"), isRecoverable: false) }
            return try await createHTMLAccount(config: config, username: cleanedUsername, password: password)
        }
        let response = try await transport.token(baseURL: config.canonicalURL, username: cleanedUsername, password: password)
        guard let token = response.token else { throw MoodleError(code: response.errorCode ?? "login_failed", message: response.error ?? String(localized: "error.login.credentials"), isRecoverable: false) }
        return try await createAPIAccount(config: config, token: token, privateToken: response.privateToken, enteredUsername: cleanedUsername)
    }

    func reauthenticate(accountID: String, username: String, password: String) async throws -> SiteAccount {
        guard var existing = try store.account(id: accountID) else { throw MoodleError(code: "account_missing", message: String(localized: "error.account.missing"), isRecoverable: false) }
        if existing.connectionMode == .nativeHtml {
            let config = MoodlePublicConfig(canonicalURL: existing.baseURL, siteName: existing.siteName, mobileWebServiceEnabled: false, loginType: 1, launchURL: nil, showLoginForm: true)
            let result = try await html.login(accountID: existing.id, config: config, username: username.trimmed, password: password)
            existing.username = username.trimmed; existing.userID = result.identity.userID ?? existing.userID
            existing.fullName = result.identity.fullName ?? existing.fullName; existing.siteName = result.identity.siteName
            existing.capabilities = SiteCapabilities(htmlFeatures: result.identity.features)
            existing.authenticationState = .authenticated; existing.moodleVersion = result.identity.moodleVersion
            existing.themeFamily = result.identity.themeFamily
            try store.saveAccount(existing); try store.activateAccount(existing.id); return existing
        }
        let response = try await transport.token(baseURL: existing.baseURL, username: username.trimmed, password: password)
        guard let token = response.token else { throw MoodleError(code: response.errorCode ?? "login_failed", message: response.error ?? String(localized: "error.login.credentials"), isRecoverable: false) }
        let info = try mapper.siteInfo(try await transport.rest(baseURL: existing.baseURL, token: token, function: "core_webservice_get_site_info"), fallback: existing.siteName)
        existing.username = info.username ?? username.trimmed; existing.userID = info.userID; existing.fullName = info.fullName
        existing.siteName = info.siteName; existing.capabilities = SiteCapabilities(functions: info.functions)
        existing.authenticationState = .authenticated
        try keychain.saveCredentials(StoredCredentials(token: token, privateToken: response.privateToken), accountID: existing.id)
        try store.saveAccount(existing); try store.activateAccount(existing.id); return existing
    }

    func beginSSO(config: MoodlePublicConfig) throws -> URL {
        guard config.connectionMode == .nativeApi else { throw MoodleError(code: "sso_html_unsupported", message: String(localized: "error.sso.html"), isRecoverable: false) }
        let pending = PendingSSO(baseURL: config.canonicalURL, passport: SSOProtocol.createPassport(), createdAt: Date())
        try keychain.savePendingSSO(pending)
        return try SSOProtocol.launchURL(config: config, passport: pending.passport)
    }

    func completeSSO(callback: URL) async throws -> SiteAccount {
        guard let pending = try keychain.consumePendingSSO() else { throw MoodleError(code: "missing_sso", message: String(localized: "error.sso.missing"), isRecoverable: false) }
        let credentials = try SSOProtocol.validate(callback: callback, pending: pending)
        let config = try await inspectSite(credentials.baseURL.absoluteString)
        return try await createAPIAccount(config: config, token: credentials.token, privateToken: credentials.privateToken, enteredUsername: nil)
    }

    func activate(accountID: String) throws { try store.activateAccount(accountID) }

    func requireReauthentication(accountID: String) throws {
        guard var account = try store.account(id: accountID) else { return }
        account.authenticationState = .reauthenticationRequired
        try store.saveAccount(account)
        try store.activateAccount(accountID)
    }

    func remove(accountID: String) throws {
        if try store.account(id: accountID)?.connectionMode == .nativeHtml { html.clear(accountID: accountID) }
        try keychain.deleteCredentials(accountID: accountID)
        try store.deleteAccount(accountID)
        try FileManager.default.removeItem(at: FileCache.directory(accountID: accountID))
    }

    private func createAPIAccount(config: MoodlePublicConfig, token: String, privateToken: String?, enteredUsername: String?) async throws -> SiteAccount {
        let info = try mapper.siteInfo(try await transport.rest(baseURL: config.canonicalURL, token: token, function: "core_webservice_get_site_info"), fallback: config.siteName)
        let account = SiteAccount(id: UUID().uuidString, baseURL: config.canonicalURL, siteName: info.siteName,
                                  username: info.username ?? enteredUsername, userID: info.userID, fullName: info.fullName,
                                  connectionMode: .nativeApi, capabilities: SiteCapabilities(functions: info.functions), isActive: true)
        try keychain.saveCredentials(StoredCredentials(token: token, privateToken: privateToken), accountID: account.id)
        try store.saveAccount(account); try store.activateAccount(account.id); return account
    }

    private func createHTMLAccount(config: MoodlePublicConfig, username: String, password: String) async throws -> SiteAccount {
        let id = UUID().uuidString
        do {
            let result = try await html.login(accountID: id, config: config, username: username, password: password)
            var features = result.identity.features
            let candidate = SiteAccount(id: id, baseURL: config.canonicalURL, siteName: result.identity.siteName,
                                        username: username, userID: result.identity.userID, fullName: result.identity.fullName,
                                        connectionMode: .nativeHtml, capabilities: SiteCapabilities(htmlFeatures: features),
                                        moodleVersion: result.identity.moodleVersion, themeFamily: result.identity.themeFamily, isActive: true)
            let messagingAvailable: Bool
            if candidate.userID == nil {
                messagingAvailable = false
            } else {
                messagingAvailable = (try? await html.conversations(account: candidate, offset: 0, limit: 1)) != nil
            }
            if !messagingAvailable {
                features.subtract([.messagesRead, .messagesSearch, .messagesSend, .messagesMarkRead])
            }
            var account = candidate; account.capabilities = SiteCapabilities(htmlFeatures: features)
            try store.saveAccount(account); try store.activateAccount(account.id); return account
        } catch { html.clear(accountID: id); try? keychain.deleteCredentials(accountID: id); throw error }
    }
}

private extension String { var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) } }
