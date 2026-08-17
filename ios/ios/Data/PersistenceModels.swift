import Foundation
import SwiftData

@Model
final class SiteAccountRecord {
    @Attribute(.unique) var id: String
    var baseURL: String
    var siteName: String
    var username: String?
    var userID: Int64?
    var fullName: String?
    var connectionMode: String
    var capabilitiesData: Data
    var authenticationState: String
    var moodleVersion: String?
    var themeFamily: String?
    var lastSync: Date?
    var isActive: Bool

    init(_ account: SiteAccount) {
        id = account.id
        baseURL = account.baseURL.absoluteString
        siteName = account.siteName
        username = account.username
        userID = account.userID
        fullName = account.fullName
        connectionMode = account.connectionMode.rawValue
        capabilitiesData = (try? JSONEncoder().encode(account.capabilities)) ?? Data()
        authenticationState = account.authenticationState.rawValue
        moodleVersion = account.moodleVersion
        themeFamily = account.themeFamily?.rawValue
        lastSync = account.lastSync
        isActive = account.isActive
    }

    func update(_ account: SiteAccount) {
        baseURL = account.baseURL.absoluteString
        siteName = account.siteName
        username = account.username
        userID = account.userID
        fullName = account.fullName
        connectionMode = account.connectionMode.rawValue
        capabilitiesData = (try? JSONEncoder().encode(account.capabilities)) ?? Data()
        authenticationState = account.authenticationState.rawValue
        moodleVersion = account.moodleVersion
        themeFamily = account.themeFamily?.rawValue
        lastSync = account.lastSync
        isActive = account.isActive
    }

    var domain: SiteAccount? {
        guard let url = URL(string: baseURL),
              let mode = ConnectionMode(rawValue: connectionMode),
              let auth = AuthenticationState(rawValue: authenticationState)
        else { return nil }
        return SiteAccount(
            id: id,
            baseURL: url,
            siteName: siteName,
            username: username,
            userID: userID,
            fullName: fullName,
            connectionMode: mode,
            capabilities: (try? JSONDecoder().decode(SiteCapabilities.self, from: capabilitiesData)) ?? SiteCapabilities(),
            authenticationState: auth,
            moodleVersion: moodleVersion,
            themeFamily: themeFamily.flatMap(HTMLThemeFamily.init(rawValue:)),
            lastSync: lastSync,
            isActive: isActive
        )
    }
}

@Model final class CourseRecord {
    @Attribute(.unique) var storageKey: String
    var accountID: String
    var remoteID: Int64
    var data: Data
    init(accountID: String, value: MoodleCourse) {
        storageKey = "\(accountID):course:\(value.id)"; self.accountID = accountID; remoteID = value.id
        data = (try? JSONEncoder().encode(value)) ?? Data()
    }
}

@Model final class SectionRecord {
    @Attribute(.unique) var storageKey: String
    var accountID: String
    var courseID: Int64
    var remoteID: Int64
    var position: Int
    var data: Data
    init(accountID: String, value: MoodleSection) {
        storageKey = "\(accountID):section:\(value.id)"; self.accountID = accountID; courseID = value.courseID
        remoteID = value.id; position = value.position; data = (try? JSONEncoder().encode(value)) ?? Data()
    }
}

@Model final class GradeRecord {
    @Attribute(.unique) var storageKey: String
    var accountID: String
    var courseID: Int64
    var itemID: Int64
    var data: Data
    init(accountID: String, value: MoodleGrade) {
        storageKey = "\(accountID):grade:\(value.courseID):\(value.itemID)"; self.accountID = accountID
        courseID = value.courseID; itemID = value.itemID; data = (try? JSONEncoder().encode(value)) ?? Data()
    }
}

@Model final class CalendarEventRecord {
    @Attribute(.unique) var storageKey: String
    var accountID: String
    var remoteID: Int64
    var startDate: Date
    var data: Data
    init(accountID: String, value: MoodleCalendarEvent) {
        storageKey = "\(accountID):event:\(value.id)"; self.accountID = accountID; remoteID = value.id
        startDate = value.startDate; data = (try? JSONEncoder().encode(value)) ?? Data()
    }
}

@Model final class NotificationRecord {
    @Attribute(.unique) var storageKey: String
    var accountID: String
    var remoteID: Int64
    var createdAt: Date
    var isRead: Bool
    var locallyNotified: Bool
    var data: Data
    init(accountID: String, value: MoodleNotification, locallyNotified: Bool = false) {
        storageKey = "\(accountID):notification:\(value.id)"; self.accountID = accountID; remoteID = value.id
        createdAt = value.createdAt; isRead = value.isRead; self.locallyNotified = locallyNotified
        data = (try? JSONEncoder().encode(value)) ?? Data()
    }
}

@Model final class ConversationRecord {
    @Attribute(.unique) var storageKey: String
    var accountID: String
    var remoteID: Int64
    var latestMessageAt: Date
    var unreadCount: Int
    var data: Data
    init(accountID: String, value: MoodleConversation) {
        storageKey = "\(accountID):conversation:\(value.id)"; self.accountID = accountID; remoteID = value.id
        latestMessageAt = value.latestMessageAt; unreadCount = value.unreadCount
        data = (try? JSONEncoder().encode(value)) ?? Data()
    }
}

@Model final class ConversationMemberRecord {
    @Attribute(.unique) var storageKey: String
    var accountID: String
    var conversationID: Int64
    var remoteID: Int64
    var data: Data
    init(accountID: String, conversationID: Int64, value: MoodleConversationMember) {
        storageKey = "\(accountID):member:\(conversationID):\(value.id)"; self.accountID = accountID
        self.conversationID = conversationID; remoteID = value.id; data = (try? JSONEncoder().encode(value)) ?? Data()
    }
}

@Model final class MessageRecord {
    @Attribute(.unique) var storageKey: String
    var accountID: String
    var remoteID: Int64
    var conversationID: Int64
    var createdAt: Date
    var isMine: Bool
    var isRead: Bool
    var locallyNotified: Bool
    var data: Data
    init(accountID: String, value: MoodleMessage, locallyNotified: Bool = false) {
        storageKey = "\(accountID):message:\(value.id)"; self.accountID = accountID; remoteID = value.id
        conversationID = value.conversationID; createdAt = value.createdAt; isMine = value.isMine
        isRead = value.isRead; self.locallyNotified = locallyNotified
        data = (try? JSONEncoder().encode(value)) ?? Data()
    }
}

@Model final class MessageDraftRecord {
    @Attribute(.unique) var storageKey: String
    var accountID: String
    var draftKey: String
    var body: String
    var updatedAt: Date
    init(_ value: MessageDraft) {
        storageKey = value.id; accountID = value.accountID; draftKey = value.key; body = value.body; updatedAt = value.updatedAt
    }
}

@Model final class MessageSyncStateRecord {
    @Attribute(.unique) var accountID: String
    var initialized: Bool
    var lastSync: Date
    init(accountID: String, initialized: Bool, lastSync: Date) {
        self.accountID = accountID; self.initialized = initialized; self.lastSync = lastSync
    }
}

enum MobileMoodleSchema {
    static let schema = Schema([
        SiteAccountRecord.self, CourseRecord.self, SectionRecord.self, GradeRecord.self,
        CalendarEventRecord.self, NotificationRecord.self, ConversationRecord.self,
        ConversationMemberRecord.self, MessageRecord.self, MessageDraftRecord.self,
        MessageSyncStateRecord.self,
    ])
}
