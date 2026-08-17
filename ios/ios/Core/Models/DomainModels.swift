import Foundation

enum ConnectionMode: String, Codable, Sendable {
    case nativeApi
    case nativeHtml
}

enum AuthenticationState: String, Codable, Sendable {
    case authenticated
    case reauthenticationRequired
}

enum HTMLThemeFamily: String, Codable, Sendable {
    case modern
    case legacy
    case structuralFallback
}

enum HTMLFeature: String, Codable, CaseIterable, Sendable {
    case courses
    case contents
    case assignmentsRead
    case grades
    case calendar
    case notifications
    case files
    case messagesRead
    case messagesSearch
    case messagesSend
    case messagesMarkRead
}

struct MessageCapabilities: Codable, Hashable, Sendable {
    var canList = false
    var canRead = false
    var canSearchUsers = false
    var canSend = false
    var canStartConversation = false
    var canMarkRead = false
}

struct SiteCapabilities: Codable, Hashable, Sendable {
    var functions: Set<String> = []
    var htmlFeatures: Set<HTMLFeature> = []

    func supports(_ function: String) -> Bool { functions.contains(function) }

    var courses: Bool { supports("core_enrol_get_users_courses") || htmlFeatures.contains(.courses) }
    var contents: Bool { supports("core_course_get_contents") || htmlFeatures.contains(.contents) }
    var assignments: Bool { supports("mod_assign_get_assignments") || htmlFeatures.contains(.assignmentsRead) }
    var assignmentSubmission: Bool {
        supports("mod_assign_get_assignments") && supports("mod_assign_save_submission")
    }
    var grades: Bool { supports("gradereport_user_get_grade_items") || htmlFeatures.contains(.grades) }
    var calendar: Bool { supports("core_calendar_get_calendar_upcoming_view") || htmlFeatures.contains(.calendar) }
    var notifications: Bool { supports("core_message_get_messages") || htmlFeatures.contains(.notifications) }
    var autoLogin: Bool { supports("tool_mobile_get_autologin_key") }

    var messages: MessageCapabilities {
        MessageCapabilities(
            canList: supports("core_message_get_conversations") || htmlFeatures.contains(.messagesRead),
            canRead: supports("core_message_get_conversation_messages") || htmlFeatures.contains(.messagesRead),
            canSearchUsers: supports("core_message_message_search_users") || htmlFeatures.contains(.messagesSearch),
            canSend: supports("core_message_send_messages_to_conversation") || htmlFeatures.contains(.messagesSend),
            canStartConversation: supports("core_message_send_instant_messages") || htmlFeatures.contains(.messagesSend),
            canMarkRead: supports("core_message_mark_all_conversation_messages_as_read") || htmlFeatures.contains(.messagesMarkRead)
        )
    }
}

struct SiteAccount: Identifiable, Codable, Hashable, Sendable {
    let id: String
    var baseURL: URL
    var siteName: String
    var username: String?
    var userID: Int64?
    var fullName: String?
    var connectionMode: ConnectionMode
    var capabilities = SiteCapabilities()
    var authenticationState: AuthenticationState = .authenticated
    var moodleVersion: String?
    var themeFamily: HTMLThemeFamily?
    var lastSync: Date?
    var isActive = false
}

struct MoodlePublicConfig: Codable, Hashable, Sendable {
    var canonicalURL: URL
    var siteName: String
    var mobileWebServiceEnabled: Bool
    var loginType: Int
    var launchURL: URL?
    var showLoginForm: Bool

    var connectionMode: ConnectionMode { mobileWebServiceEnabled ? .nativeApi : .nativeHtml }
    var browserSSORequired: Bool { loginType == 2 || loginType == 3 }
}

struct MoodleCourse: Identifiable, Codable, Hashable, Sendable {
    let id: Int64
    var shortName: String
    var fullName: String
    var summaryHTML: String
    var startDate: Date?
    var endDate: Date?
}

struct MoodleSection: Identifiable, Codable, Hashable, Sendable {
    let id: Int64
    var courseID: Int64
    var name: String
    var summaryHTML: String
    var position: Int
    var modules: [MoodleModule]
}

enum NativeModuleType: String, Codable, Sendable {
    case assignment
    case page
    case url
    case resource
    case folder
    case unsupported
}

struct MoodleModule: Identifiable, Codable, Hashable, Sendable {
    let id: Int64
    var instanceID: Int64?
    var name: String
    var moduleType: String
    var descriptionHTML: String
    var webURL: URL?
    var files: [MoodleFile]

    var nativeType: NativeModuleType {
        switch moduleType {
        case "assign": .assignment
        case "page": .page
        case "url": .url
        case "resource": .resource
        case "folder": .folder
        default: .unsupported
        }
    }
}

struct MoodleModuleContent: Codable, Hashable, Sendable {
    var title: String
    var bodyHTML: String
    var files: [MoodleFile]
    var originalURL: URL?
}

struct MoodleFile: Identifiable, Codable, Hashable, Sendable {
    var id: String { url.absoluteString }
    var name: String
    var url: URL
    var mimeType: String?
    var sizeBytes: Int64?
}

struct MoodleAssignment: Identifiable, Codable, Hashable, Sendable {
    let id: Int64
    var courseID: Int64
    var courseModuleID: Int64
    var name: String
    var introHTML: String
    var dueDate: Date?
    var cutoffDate: Date?
    var allowsOnlineText: Bool
    var allowsFiles: Bool
    var requiresSubmitButton: Bool
}

struct AssignmentSubmissionStatus: Codable, Hashable, Sendable {
    var status: String
    var graded: Bool
    var submittedAt: Date?
    var grade: String?
    var feedbackHTML: String?
}

struct MoodleGrade: Identifiable, Codable, Hashable, Sendable {
    var id: String { "\(courseID):\(itemID)" }
    var courseID: Int64
    var itemID: Int64
    var itemName: String
    var gradeFormatted: String
    var rangeFormatted: String
    var percentageFormatted: String
}

struct MoodleCalendarEvent: Identifiable, Codable, Hashable, Sendable {
    let id: Int64
    var name: String
    var descriptionHTML: String
    var startDate: Date
    var courseID: Int64?
    var actionURL: URL?
}

struct MoodleNotification: Identifiable, Codable, Hashable, Sendable {
    let id: Int64
    var subject: String
    var fullMessageHTML: String
    var createdAt: Date
    var isRead: Bool
    var contextURL: URL?
}

enum ConversationType: String, Codable, Sendable {
    case individual
    case group
    case selfConversation
    case unknown
}

struct MoodleConversationMember: Identifiable, Codable, Hashable, Sendable {
    let id: Int64
    var fullName: String
    var isCurrentUser: Bool
    var canMessage: Bool
}

struct MoodleConversation: Identifiable, Codable, Hashable, Sendable {
    let id: Int64
    var type: ConversationType
    var name: String
    var members: [MoodleConversationMember]
    var latestMessagePreview: String
    var latestMessageAt: Date
    var unreadCount: Int
    var isFavourite: Bool
    var canReply: Bool
}

struct MoodleMessage: Identifiable, Codable, Hashable, Sendable {
    let id: Int64
    var conversationID: Int64
    var senderID: Int64
    var senderName: String
    var bodyText: String
    var bodyHTML: String
    var createdAt: Date
    var isMine: Bool
    var isRead: Bool
}

struct MoodleMessageUser: Identifiable, Codable, Hashable, Sendable {
    let id: Int64
    var fullName: String
    var canMessage: Bool
}

struct MessageDraft: Identifiable, Codable, Hashable, Sendable {
    var id: String { "\(accountID):\(key)" }
    var accountID: String
    var key: String
    var body: String
    var updatedAt: Date
}

enum MessageSendState: Equatable, Sendable {
    case idle
    case sending
    case failed(String)
    case sent(conversationID: Int64)
}

struct PortalSnapshot: Sendable {
    var courses: [MoodleCourse] = []
    var sections: [Int64: [MoodleSection]] = [:]
    var grades: [MoodleGrade] = []
    var events: [MoodleCalendarEvent] = []
    var notifications: [MoodleNotification] = []
    var conversations: [MoodleConversation] = []
    var messages: [Int64: [MoodleMessage]] = [:]

    var unreadMessages: Int { conversations.reduce(0) { $0 + $1.unreadCount } }
    var nextEvent: MoodleCalendarEvent? {
        events.filter { $0.startDate >= Date() }.min { $0.startDate < $1.startDate }
    }
}

struct MoodleError: Error, LocalizedError, Equatable, Sendable {
    var code: String
    var message: String
    var isRecoverable = true

    var errorDescription: String? { message }

    static let offline = MoodleError(code: "offline", message: String(localized: "error.offline"))
    static let unsupported = MoodleError(code: "unsupported", message: String(localized: "error.unsupported"), isRecoverable: false)
}

extension Date {
    static func moodleTimestamp(_ value: Int64?) -> Date? {
        guard let value, value > 0 else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(value))
    }
}
