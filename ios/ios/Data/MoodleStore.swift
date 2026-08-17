import Foundation
import SwiftData

@MainActor
final class MoodleStore {
    let container: ModelContainer
    private var context: ModelContext { container.mainContext }
    private let decoder = JSONDecoder()

    init(inMemory: Bool = false) throws {
        let configuration: ModelConfiguration
        if inMemory {
            configuration = ModelConfiguration("MobileMoodleTests", schema: MobileMoodleSchema.schema, isStoredInMemoryOnly: true)
        } else {
            let support = try FileManager.default.url(
                for: .applicationSupportDirectory,
                in: .userDomainMask,
                appropriateFor: nil,
                create: true
            ).appending(path: "MobileMoodle", directoryHint: .isDirectory)
            try FileManager.default.createDirectory(at: support, withIntermediateDirectories: true)
            var values = URLResourceValues(); values.isExcludedFromBackup = true
            var excluded = support; try? excluded.setResourceValues(values)
            configuration = ModelConfiguration(
                "MobileMoodle",
                schema: MobileMoodleSchema.schema,
                url: support.appending(path: "cache.store"),
                allowsSave: true,
                cloudKitDatabase: .none
            )
        }
        container = try ModelContainer(for: MobileMoodleSchema.schema, configurations: [configuration])
        context.autosaveEnabled = false
    }

    func accounts() throws -> [SiteAccount] {
        try context.fetch(FetchDescriptor<SiteAccountRecord>(sortBy: [SortDescriptor(\.siteName)]))
            .compactMap(\.domain)
            .sorted { ($0.isActive ? 0 : 1, $0.siteName) < ($1.isActive ? 0 : 1, $1.siteName) }
    }

    func account(id: String) throws -> SiteAccount? {
        let id = id
        var descriptor = FetchDescriptor<SiteAccountRecord>(predicate: #Predicate { $0.id == id })
        descriptor.fetchLimit = 1
        return try context.fetch(descriptor).first?.domain
    }

    func activeAccount() throws -> SiteAccount? {
        var descriptor = FetchDescriptor<SiteAccountRecord>(predicate: #Predicate { $0.isActive })
        descriptor.fetchLimit = 1
        return try context.fetch(descriptor).first?.domain
    }

    func saveAccount(_ account: SiteAccount) throws {
        let id = account.id
        var descriptor = FetchDescriptor<SiteAccountRecord>(predicate: #Predicate { $0.id == id })
        descriptor.fetchLimit = 1
        if let record = try context.fetch(descriptor).first { record.update(account) }
        else { context.insert(SiteAccountRecord(account)) }
        try context.save()
    }

    func activateAccount(_ accountID: String) throws {
        for record in try context.fetch(FetchDescriptor<SiteAccountRecord>()) {
            record.isActive = record.id == accountID
        }
        try context.save()
    }

    func authenticatedAccounts() throws -> [SiteAccount] {
        try accounts().filter { $0.authenticationState == .authenticated }
    }

    func deleteAccount(_ accountID: String) throws {
        try deleteAll(SiteAccountRecord.self) { $0.id == accountID }
        try deleteAll(CourseRecord.self) { $0.accountID == accountID }
        try deleteAll(SectionRecord.self) { $0.accountID == accountID }
        try deleteAll(GradeRecord.self) { $0.accountID == accountID }
        try deleteAll(CalendarEventRecord.self) { $0.accountID == accountID }
        try deleteAll(NotificationRecord.self) { $0.accountID == accountID }
        try deleteAll(ConversationRecord.self) { $0.accountID == accountID }
        try deleteAll(ConversationMemberRecord.self) { $0.accountID == accountID }
        try deleteAll(MessageRecord.self) { $0.accountID == accountID }
        try deleteAll(MessageDraftRecord.self) { $0.accountID == accountID }
        try deleteAll(MessageSyncStateRecord.self) { $0.accountID == accountID }
        try context.save()
    }

    func snapshot(accountID: String) throws -> PortalSnapshot {
        let accountID = accountID
        let courses = try context.fetch(FetchDescriptor<CourseRecord>(predicate: #Predicate { $0.accountID == accountID }))
            .compactMap { try? decoder.decode(MoodleCourse.self, from: $0.data) }
            .sorted { $0.fullName.localizedCaseInsensitiveCompare($1.fullName) == .orderedAscending }
        let sectionRecords = try context.fetch(FetchDescriptor<SectionRecord>(
            predicate: #Predicate { $0.accountID == accountID },
            sortBy: [SortDescriptor(\.position)]
        ))
        let sections = Dictionary(grouping: sectionRecords.compactMap { try? decoder.decode(MoodleSection.self, from: $0.data) }, by: \.courseID)
        let grades = try context.fetch(FetchDescriptor<GradeRecord>(predicate: #Predicate { $0.accountID == accountID }))
            .compactMap { try? decoder.decode(MoodleGrade.self, from: $0.data) }
        let events = try context.fetch(FetchDescriptor<CalendarEventRecord>(
            predicate: #Predicate { $0.accountID == accountID }, sortBy: [SortDescriptor(\.startDate)]
        )).compactMap { try? decoder.decode(MoodleCalendarEvent.self, from: $0.data) }
        let notifications = try context.fetch(FetchDescriptor<NotificationRecord>(
            predicate: #Predicate { $0.accountID == accountID }, sortBy: [SortDescriptor(\.createdAt, order: .reverse)]
        )).compactMap { try? decoder.decode(MoodleNotification.self, from: $0.data) }
        let conversations = try context.fetch(FetchDescriptor<ConversationRecord>(
            predicate: #Predicate { $0.accountID == accountID }, sortBy: [SortDescriptor(\.latestMessageAt, order: .reverse)]
        )).compactMap { try? decoder.decode(MoodleConversation.self, from: $0.data) }
            .sorted { ($0.unreadCount > 0 ? 0 : 1, -$0.latestMessageAt.timeIntervalSince1970) < ($1.unreadCount > 0 ? 0 : 1, -$1.latestMessageAt.timeIntervalSince1970) }
        let messageRecords = try context.fetch(FetchDescriptor<MessageRecord>(
            predicate: #Predicate { $0.accountID == accountID }, sortBy: [SortDescriptor(\.createdAt)]
        ))
        let messages = Dictionary(grouping: messageRecords.compactMap { try? decoder.decode(MoodleMessage.self, from: $0.data) }, by: \.conversationID)
        return PortalSnapshot(courses: courses, sections: sections, grades: grades, events: events, notifications: notifications, conversations: conversations, messages: messages)
    }

    func replaceCourses(_ values: [MoodleCourse], accountID: String) throws {
        try deleteAll(CourseRecord.self) { $0.accountID == accountID }
        values.forEach { context.insert(CourseRecord(accountID: accountID, value: $0)) }
        try context.save()
    }

    func replaceSections(_ values: [MoodleSection], accountID: String, courseID: Int64) throws {
        try deleteAll(SectionRecord.self) { $0.accountID == accountID && $0.courseID == courseID }
        values.forEach { context.insert(SectionRecord(accountID: accountID, value: $0)) }
        try context.save()
    }

    func replaceGrades(_ values: [MoodleGrade], accountID: String) throws {
        try deleteAll(GradeRecord.self) { $0.accountID == accountID }
        values.forEach { context.insert(GradeRecord(accountID: accountID, value: $0)) }
        try context.save()
    }

    func replaceEvents(_ values: [MoodleCalendarEvent], accountID: String) throws {
        try deleteAll(CalendarEventRecord.self) { $0.accountID == accountID }
        values.forEach { context.insert(CalendarEventRecord(accountID: accountID, value: $0)) }
        try context.save()
    }

    func upsertNotifications(_ values: [MoodleNotification], accountID: String) throws {
        let known = Set(try context.fetch(FetchDescriptor<NotificationRecord>()).filter { $0.accountID == accountID }.map(\.remoteID))
        for value in values {
            let key = "\(accountID):notification:\(value.id)"
            try deleteAll(NotificationRecord.self) { $0.storageKey == key }
            context.insert(NotificationRecord(accountID: accountID, value: value, locallyNotified: known.contains(value.id)))
        }
        try context.save()
    }

    func markNotificationRead(accountID: String, notificationID: Int64) throws {
        for record in try context.fetch(FetchDescriptor<NotificationRecord>()).filter({ $0.accountID == accountID && $0.remoteID == notificationID }) {
            record.isRead = true
            if var value = try? decoder.decode(MoodleNotification.self, from: record.data) {
                value.isRead = true; record.data = (try? JSONEncoder().encode(value)) ?? record.data
            }
        }
        try context.save()
    }

    func replaceConversations(_ values: [MoodleConversation], accountID: String) throws {
        try deleteAll(ConversationRecord.self) { $0.accountID == accountID }
        try deleteAll(ConversationMemberRecord.self) { $0.accountID == accountID }
        for value in values.prefix(30) {
            context.insert(ConversationRecord(accountID: accountID, value: value))
            value.members.forEach { context.insert(ConversationMemberRecord(accountID: accountID, conversationID: value.id, value: $0)) }
        }
        try context.save()
    }

    func upsertMessages(_ values: [MoodleMessage], accountID: String, conversationID: Int64, suppressNotifications: Bool = false) throws {
        let all = try context.fetch(FetchDescriptor<MessageRecord>())
        let existing = Dictionary(uniqueKeysWithValues: all.filter { $0.accountID == accountID }.map { ($0.remoteID, $0.locallyNotified) })
        for value in values {
            let key = "\(accountID):message:\(value.id)"
            try deleteAll(MessageRecord.self) { $0.storageKey == key }
            context.insert(MessageRecord(accountID: accountID, value: value, locallyNotified: suppressNotifications || value.isMine || (existing[value.id] ?? false)))
        }
        let conversationMessages = try context.fetch(FetchDescriptor<MessageRecord>(
            predicate: #Predicate { $0.accountID == accountID && $0.conversationID == conversationID },
            sortBy: [SortDescriptor(\.createdAt, order: .reverse)]
        ))
        for old in conversationMessages.dropFirst(200) { context.delete(old) }
        try context.save()
    }

    func markConversationRead(accountID: String, conversationID: Int64) throws {
        for record in try context.fetch(FetchDescriptor<ConversationRecord>()).filter({ $0.accountID == accountID && $0.remoteID == conversationID }) {
            record.unreadCount = 0
            if var value = try? decoder.decode(MoodleConversation.self, from: record.data) {
                value.unreadCount = 0; record.data = (try? JSONEncoder().encode(value)) ?? record.data
            }
        }
        for record in try context.fetch(FetchDescriptor<MessageRecord>()).filter({ $0.accountID == accountID && $0.conversationID == conversationID && !$0.isMine }) {
            record.isRead = true
            if var value = try? decoder.decode(MoodleMessage.self, from: record.data) {
                value.isRead = true; record.data = (try? JSONEncoder().encode(value)) ?? record.data
            }
        }
        try context.save()
    }

    func saveDraft(accountID: String, key: String, body: String) throws {
        let storageKey = "\(accountID):\(key)"
        try deleteAll(MessageDraftRecord.self) { $0.storageKey == storageKey }
        if !body.isEmpty { context.insert(MessageDraftRecord(.init(accountID: accountID, key: key, body: body, updatedAt: Date()))) }
        try context.save()
    }

    func draft(accountID: String, key: String) throws -> MessageDraft? {
        let storageKey = "\(accountID):\(key)"
        return try context.fetch(FetchDescriptor<MessageDraftRecord>()).first(where: { $0.storageKey == storageKey }).map {
            MessageDraft(accountID: $0.accountID, key: $0.draftKey, body: $0.body, updatedAt: $0.updatedAt)
        }
    }

    func unannouncedMessages(accountID: String) throws -> [MoodleMessage] {
        try context.fetch(FetchDescriptor<MessageRecord>(sortBy: [SortDescriptor(\.createdAt)]))
            .filter { $0.accountID == accountID && !$0.locallyNotified && !$0.isMine }
            .compactMap { try? decoder.decode(MoodleMessage.self, from: $0.data) }
    }

    func markMessagesAnnounced(accountID: String, ids: Set<Int64>) throws {
        try context.fetch(FetchDescriptor<MessageRecord>()).filter { $0.accountID == accountID && ids.contains($0.remoteID) }
            .forEach { $0.locallyNotified = true }
        try context.save()
    }

    func messageSyncInitialized(accountID: String) throws -> Bool {
        try context.fetch(FetchDescriptor<MessageSyncStateRecord>()).first { $0.accountID == accountID }?.initialized ?? false
    }

    func setMessageSyncInitialized(accountID: String) throws {
        if let record = try context.fetch(FetchDescriptor<MessageSyncStateRecord>()).first(where: { $0.accountID == accountID }) {
            record.initialized = true; record.lastSync = Date()
        } else { context.insert(MessageSyncStateRecord(accountID: accountID, initialized: true, lastSync: Date())) }
        try context.save()
    }

    private func deleteAll<T: PersistentModel>(_ type: T.Type, matching include: (T) -> Bool) throws {
        for value in try context.fetch(FetchDescriptor<T>()).filter(include) { context.delete(value) }
    }
}
