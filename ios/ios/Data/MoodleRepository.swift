import Foundation

@MainActor
protocol MoodleRepository: AnyObject {
    func snapshot(accountID: String) throws -> PortalSnapshot
    func sync(accountID: String) async throws
    func syncMessages(accountID: String, allowNotifications: Bool) async throws
    func refreshConversations(accountID: String, offset: Int) async throws -> [MoodleConversation]
    func refreshMessages(accountID: String, conversationID: Int64, offset: Int) async throws -> [MoodleMessage]
    func searchUsers(accountID: String, query: String) async throws -> [MoodleMessageUser]
    func sendMessage(accountID: String, conversationID: Int64, text: String) async throws
    func startConversation(accountID: String, userID: Int64, text: String) async throws -> Int64
    func markConversationRead(accountID: String, conversationID: Int64) async throws
    func saveDraft(accountID: String, key: String, body: String) throws
    func draft(accountID: String, key: String) throws -> MessageDraft?
    func refreshCourse(accountID: String, courseID: Int64) async throws -> [MoodleSection]
    func assignments(accountID: String, courseID: Int64) async throws -> [MoodleAssignment]
    func moduleContent(accountID: String, module: MoodleModule) async throws -> MoodleModuleContent
    func submissionStatus(accountID: String, assignmentID: Int64) async throws -> AssignmentSubmissionStatus
    func submitAssignment(accountID: String, assignment: MoodleAssignment, onlineText: String, fileURL: URL?) async throws
    func markNotificationRead(accountID: String, notificationID: Int64) async throws
    func authenticatedWebURL(accountID: String, targetURL: URL) async -> URL
    func cacheFile(accountID: String, file: MoodleFile) async throws -> URL
}

@MainActor
final class DefaultMoodleRepository: MoodleRepository {
    private let transport: MoodleTransport
    private let store: MoodleStore
    private let keychain: KeychainStore
    private let html: HTMLMoodleDataSource
    private let mapper: MoodleMapper

    init(transport: MoodleTransport, store: MoodleStore, keychain: KeychainStore, html: HTMLMoodleDataSource, mapper: MoodleMapper) {
        self.transport = transport; self.store = store; self.keychain = keychain; self.html = html; self.mapper = mapper
    }

    func snapshot(accountID: String) throws -> PortalSnapshot { try store.snapshot(accountID: accountID) }

    func sync(accountID: String) async throws {
        var account = try requiredAccount(accountID)
        do {
            if account.connectionMode == .nativeApi { try await syncAPI(account) }
            else { try await syncHTML(account) }
            account.authenticationState = .authenticated; account.lastSync = Date()
            try store.saveAccount(account)
        } catch let error as MoodleError {
            try markExpiredIfNeeded(account: &account, error: error); throw error
        }
    }

    func syncMessages(accountID: String, allowNotifications: Bool = true) async throws {
        var account = try requiredAccount(accountID)
        guard account.capabilities.messages.canList else { return }
        do {
            let initial = try !store.messageSyncInitialized(accountID: account.id)
            let conversations = try await fetchConversations(account, offset: 0)
            try store.replaceConversations(conversations, accountID: account.id)
            for conversation in conversations.filter({ $0.unreadCount > 0 }).prefix(8) {
                if let messages = try? await fetchMessages(account, conversationID: conversation.id, offset: 0) {
                    try store.upsertMessages(messages, accountID: account.id, conversationID: conversation.id,
                                             suppressNotifications: initial || !allowNotifications)
                }
            }
            try store.setMessageSyncInitialized(accountID: account.id)
        } catch let error as MoodleError {
            try markExpiredIfNeeded(account: &account, error: error); throw error
        }
    }

    func refreshConversations(accountID: String, offset: Int = 0) async throws -> [MoodleConversation] {
        let account = try requiredAccount(accountID)
        guard account.capabilities.messages.canList else { throw MoodleError.unsupported }
        let values = try await fetchConversations(account, offset: max(0, offset))
        if offset == 0 { try store.replaceConversations(values, accountID: account.id) }
        return values
    }

    func refreshMessages(accountID: String, conversationID: Int64, offset: Int = 0) async throws -> [MoodleMessage] {
        let account = try requiredAccount(accountID)
        guard account.capabilities.messages.canRead else { throw MoodleError.unsupported }
        let values = try await fetchMessages(account, conversationID: conversationID, offset: max(0, offset))
        try store.upsertMessages(values, accountID: account.id, conversationID: conversationID, suppressNotifications: true)
        return values
    }

    func searchUsers(accountID: String, query: String) async throws -> [MoodleMessageUser] {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.count >= 2 else { throw MoodleError(code: "query_short", message: String(localized: "error.search.short"), isRecoverable: false) }
        let account = try requiredAccount(accountID)
        guard account.capabilities.messages.canSearchUsers else { throw MoodleError.unsupported }
        if account.connectionMode == .nativeHtml { return try await html.searchUsers(account: account, query: text, limit: 30) }
        let value = try await call(account, "core_message_message_search_users", [
            "userid": requiredUserID(account), "search": text, "limitfrom": "0", "limitnum": "30",
        ])
        return Array(mapper.users(value, currentUserID: account.userID ?? 0).filter(\.canMessage).prefix(30))
    }

    func sendMessage(accountID: String, conversationID: Int64, text: String) async throws {
        let body = try validatedMessage(text); let account = try requiredAccount(accountID)
        guard account.capabilities.messages.canSend else { throw MoodleError.unsupported }
        if account.connectionMode == .nativeHtml { _ = try await html.sendMessage(account: account, conversationID: conversationID, text: body) }
        else {
            _ = try await call(account, "core_message_send_messages_to_conversation", [
                "conversationid": String(conversationID), "messages[0][text]": body, "messages[0][textformat]": "0",
            ])
        }
        try store.saveDraft(accountID: accountID, key: conversationDraftKey(conversationID), body: "")
        _ = try await refreshMessages(accountID: accountID, conversationID: conversationID, offset: 0)
        _ = try? await refreshConversations(accountID: accountID, offset: 0)
    }

    func startConversation(accountID: String, userID: Int64, text: String) async throws -> Int64 {
        let body = try validatedMessage(text); let account = try requiredAccount(accountID)
        guard account.capabilities.messages.canStartConversation else { throw MoodleError.unsupported }
        let id: Int64
        if account.connectionMode == .nativeHtml { id = try await html.sendDirectMessage(account: account, userID: userID, text: body) }
        else {
            let sent = try await call(account, "core_message_send_instant_messages", [
                "messages[0][touserid]": String(userID), "messages[0][text]": body,
                "messages[0][textformat]": "0", "messages[0][clientmsgid]": "ios-\(UUID().uuidString)",
            ])
            if let returned = sent.array?.first?.object?.int64("conversationid") { id = returned }
            else if account.capabilities.supports("core_message_get_conversation_between_users") {
                let between = try await call(account, "core_message_get_conversation_between_users", [
                    "userid": requiredUserID(account), "otheruserid": String(userID), "includecontactrequests": "0",
                    "includeprivacyinfo": "0", "memberlimit": "0", "memberoffset": "0", "messagelimit": "1",
                    "messageoffset": "0", "newestmessagesfirst": "1",
                ])
                guard let returned = between.object?.int64("id") else { throw MoodleError(code: "message_sent_refresh_needed", message: String(localized: "error.message.refresh")) }; id = returned
            } else {
                guard let returned = try await fetchConversations(account, offset: 0).first(where: { $0.members.contains { $0.id == userID } })?.id else {
                    throw MoodleError(code: "message_sent_refresh_needed", message: String(localized: "error.message.refresh"))
                }; id = returned
            }
        }
        try store.saveDraft(accountID: accountID, key: userDraftKey(userID), body: "")
        _ = try await refreshConversations(accountID: accountID, offset: 0)
        _ = try await refreshMessages(accountID: accountID, conversationID: id, offset: 0)
        return id
    }

    func markConversationRead(accountID: String, conversationID: Int64) async throws {
        let account = try requiredAccount(accountID)
        guard account.capabilities.messages.canMarkRead else { return }
        if account.connectionMode == .nativeHtml { try await html.markRead(account: account, conversationID: conversationID) }
        else { _ = try await call(account, "core_message_mark_all_conversation_messages_as_read", ["userid": requiredUserID(account), "conversationid": String(conversationID)]) }
        try store.markConversationRead(accountID: accountID, conversationID: conversationID)
    }

    func saveDraft(accountID: String, key: String, body: String) throws { try store.saveDraft(accountID: accountID, key: key, body: String(body.prefix(4_096))) }
    func draft(accountID: String, key: String) throws -> MessageDraft? { try store.draft(accountID: accountID, key: key) }

    func refreshCourse(accountID: String, courseID: Int64) async throws -> [MoodleSection] {
        let account = try requiredAccount(accountID); guard account.capabilities.contents else { throw MoodleError.unsupported }
        let values = account.connectionMode == .nativeHtml
            ? try await html.sections(account: account, courseID: courseID)
            : try mapper.sections(try await call(account, "core_course_get_contents", ["courseid": String(courseID)]), courseID: courseID)
        try store.replaceSections(values, accountID: accountID, courseID: courseID); return values
    }

    func assignments(accountID: String, courseID: Int64) async throws -> [MoodleAssignment] {
        let account = try requiredAccount(accountID); guard account.capabilities.assignments else { throw MoodleError.unsupported }
        if account.connectionMode == .nativeHtml {
            return try await html.sections(account: account, courseID: courseID).flatMap(\.modules).filter { $0.moduleType == "assign" }.map {
                MoodleAssignment(id: $0.instanceID ?? $0.id, courseID: courseID, courseModuleID: $0.id, name: $0.name,
                                 introHTML: $0.descriptionHTML, dueDate: nil, cutoffDate: nil, allowsOnlineText: false,
                                 allowsFiles: false, requiresSubmitButton: false)
            }
        }
        return try mapper.assignments(try await call(account, "mod_assign_get_assignments", ["courseids[0]": String(courseID)]), courseID: courseID)
    }

    func moduleContent(accountID: String, module: MoodleModule) async throws -> MoodleModuleContent {
        let account = try requiredAccount(accountID)
        if account.connectionMode == .nativeHtml {
            guard let url = module.webURL else { throw MoodleError.unsupported }
            return try await html.moduleContent(account: account, title: module.name, url: url)
        }
        return MoodleModuleContent(title: module.name, bodyHTML: module.descriptionHTML, files: module.files, originalURL: module.webURL)
    }

    func submissionStatus(accountID: String, assignmentID: Int64) async throws -> AssignmentSubmissionStatus {
        let account = try requiredAccount(accountID)
        if account.connectionMode == .nativeHtml { return AssignmentSubmissionStatus(status: "read_only", graded: false, submittedAt: nil, grade: nil, feedbackHTML: nil) }
        return try mapper.submissionStatus(try await call(account, "mod_assign_get_submission_status", ["assignid": String(assignmentID)]))
    }

    func submitAssignment(accountID: String, assignment: MoodleAssignment, onlineText: String, fileURL: URL?) async throws {
        let account = try requiredAccount(accountID)
        guard account.connectionMode == .nativeApi, account.capabilities.assignmentSubmission else { throw MoodleError.unsupported }
        guard fileURL == nil || assignment.allowsFiles, onlineText.isEmpty || assignment.allowsOnlineText else { throw MoodleError.unsupported }
        var draftID: Int64?
        if let fileURL {
            let response = try await transport.upload(baseURL: account.baseURL, token: try token(account.id), fileURL: fileURL)
            draftID = response.array?.first?.object?.int64("itemid")
            if draftID == nil { throw MoodleError(code: "upload_failed", message: String(localized: "error.upload")) }
        }
        var fields = ["assignmentid": String(assignment.id)]
        if assignment.allowsOnlineText {
            fields["plugindata[onlinetext_editor][text]"] = onlineText; fields["plugindata[onlinetext_editor][format]"] = "1"; fields["plugindata[onlinetext_editor][itemid]"] = "0"
        }
        if let draftID { fields["plugindata[files_filemanager]"] = String(draftID) }
        _ = try await call(account, "mod_assign_save_submission", fields)
        if assignment.requiresSubmitButton && account.capabilities.supports("mod_assign_submit_for_grading") {
            _ = try await call(account, "mod_assign_submit_for_grading", ["assignmentid": String(assignment.id), "acceptsubmissionstatement": "1"])
        }
    }

    func markNotificationRead(accountID: String, notificationID: Int64) async throws {
        let account = try requiredAccount(accountID)
        if account.connectionMode == .nativeApi {
            _ = try await call(account, "core_message_mark_notification_read", ["notificationid": String(notificationID), "timeread": String(Int64(Date().timeIntervalSince1970))])
        }
        try store.markNotificationRead(accountID: accountID, notificationID: notificationID)
    }

    func authenticatedWebURL(accountID: String, targetURL: URL) async -> URL {
        let storedCredentials = try? keychain.credentials(accountID: accountID)
        guard let account = try? requiredAccount(accountID), account.connectionMode == .nativeApi,
              MoodleURL.isAllowed(baseURL: account.baseURL, candidate: targetURL), account.capabilities.autoLogin,
              let privateToken = storedCredentials?.privateToken,
              let userID = account.userID,
              let value = try? await call(account, "tool_mobile_get_autologin_key", ["privatetoken": privateToken]),
              let object = value.object, let rawURL = object["autologinurl"]?.string, let autoURL = URL(string: rawURL),
              MoodleURL.isAllowed(baseURL: account.baseURL, candidate: autoURL), let key = object["key"]?.string,
              var components = URLComponents(url: autoURL, resolvingAgainstBaseURL: false)
        else { return targetURL }
        components.queryItems = (components.queryItems ?? []) + [URLQueryItem(name: "userid", value: String(userID)), URLQueryItem(name: "key", value: key), URLQueryItem(name: "urltogo", value: targetURL.absoluteString)]
        return components.url ?? targetURL
    }

    func cacheFile(accountID: String, file: MoodleFile) async throws -> URL {
        let account = try requiredAccount(accountID)
        guard MoodleURL.isAllowed(baseURL: account.baseURL, candidate: file.url) else { throw MoodleError(code: "cross_origin", message: String(localized: "error.url.cross_origin"), isRecoverable: false) }
        let data: Data
        if account.connectionMode == .nativeHtml { data = try await html.download(account: account, url: file.url) }
        else {
            var components = URLComponents(url: file.url, resolvingAgainstBaseURL: false)!; components.queryItems = (components.queryItems ?? []) + [URLQueryItem(name: "token", value: try token(accountID))]
            let session = URLSession(configuration: .ephemeral, delegate: SameOriginRedirectDelegate(baseURL: account.baseURL), delegateQueue: nil)
            let (value, response) = try await session.data(from: components.url!)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw MoodleError(code: "download_failed", message: String(localized: "error.download")) }
            data = value
        }
        let destination = FileCache.destination(accountID: accountID, file: file); try data.write(to: destination, options: .atomic); return destination
    }

    private func syncAPI(_ account: SiteAccount) async throws {
        if account.capabilities.courses {
            let courses = try mapper.courses(try await call(account, "core_enrol_get_users_courses", ["userid": requiredUserID(account)]))
            try store.replaceCourses(courses, accountID: account.id)
            if account.capabilities.grades {
                var grades: [MoodleGrade] = []
                for course in courses { if let values = try? await call(account, "gradereport_user_get_grade_items", ["courseid": String(course.id), "userid": requiredUserID(account)]) { grades += mapper.grades(values) } }
                try store.replaceGrades(grades, accountID: account.id)
            }
        }
        if account.capabilities.calendar, let values = try? await call(account, "core_calendar_get_calendar_upcoming_view") { try store.replaceEvents(try mapper.events(values), accountID: account.id) }
        if account.capabilities.notifications, let values = try? await call(account, "core_message_get_messages", ["useridto": requiredUserID(account), "useridfrom": "0", "type": "notifications", "read": "both", "newestfirst": "1", "limitnum": "50"]) { try store.upsertNotifications(try mapper.notifications(values), accountID: account.id) }
        if account.capabilities.messages.canList { _ = try? await refreshConversations(accountID: account.id, offset: 0) }
    }

    private func syncHTML(_ account: SiteAccount) async throws {
        let courses = try await html.courses(account: account); try store.replaceCourses(courses, accountID: account.id)
        if account.capabilities.grades, let values = try? await html.grades(account: account, courses: courses) { try store.replaceGrades(values, accountID: account.id) }
        if account.capabilities.calendar, let values = try? await html.events(account: account) { try store.replaceEvents(values, accountID: account.id) }
        if account.capabilities.notifications, let values = try? await html.notifications(account: account) { try store.upsertNotifications(values, accountID: account.id) }
        if account.capabilities.messages.canList { _ = try? await refreshConversations(accountID: account.id, offset: 0) }
    }

    private func fetchConversations(_ account: SiteAccount, offset: Int) async throws -> [MoodleConversation] {
        if account.connectionMode == .nativeHtml { return try await html.conversations(account: account, offset: offset, limit: 30) }
        return try mapper.conversations(try await call(account, "core_message_get_conversations", ["userid": requiredUserID(account), "limitfrom": String(offset), "limitnum": "30"]), currentUserID: account.userID ?? 0)
    }

    private func fetchMessages(_ account: SiteAccount, conversationID: Int64, offset: Int) async throws -> [MoodleMessage] {
        if account.connectionMode == .nativeHtml { return try await html.messages(account: account, conversationID: conversationID, offset: offset, limit: 50) }
        return try mapper.messages(try await call(account, "core_message_get_conversation_messages", ["currentuserid": requiredUserID(account), "convid": String(conversationID), "limitfrom": String(offset), "limitnum": "50", "newest": "1", "timefrom": "0"]), conversationID: conversationID, currentUserID: account.userID ?? 0)
    }

    private func call(_ account: SiteAccount, _ function: String, _ parameters: [String: String] = [:]) async throws -> JSONValue {
        try await transport.rest(baseURL: account.baseURL, token: try token(account.id), function: function, parameters: parameters)
    }

    private func token(_ accountID: String) throws -> String {
        guard let value = try keychain.credentials(accountID: accountID)?.token else { throw MoodleError(code: "invalidtoken", message: String(localized: "error.session.expired"), isRecoverable: false) }
        return value
    }

    private func requiredAccount(_ id: String) throws -> SiteAccount {
        guard let value = try store.account(id: id) else { throw MoodleError(code: "account_missing", message: String(localized: "error.account.missing"), isRecoverable: false) }; return value
    }

    private func requiredUserID(_ account: SiteAccount) -> String { String(account.userID ?? 0) }
    private func validatedMessage(_ value: String) throws -> String {
        let text = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, text.count <= 4_096 else { throw MoodleError(code: "invalid_message", message: String(localized: "error.message.invalid"), isRecoverable: false) }; return text
    }
    private func markExpiredIfNeeded(account: inout SiteAccount, error: MoodleError) throws {
        if error.requiresReauthentication { account.authenticationState = .reauthenticationRequired; try store.saveAccount(account) }
    }
}

func conversationDraftKey(_ id: Int64) -> String { "conversation:\(id)" }
func userDraftKey(_ id: Int64) -> String { "user:\(id)" }
