import Foundation

struct SiteInfo: Sendable {
    var siteName: String
    var username: String?
    var userID: Int64?
    var fullName: String?
    var functions: Set<String>
}

final class MoodleMapper: @unchecked Sendable {
    private let html: MoodleHTMLParser
    init(html: MoodleHTMLParser) { self.html = html }

    func siteInfo(_ value: JSONValue, fallback: String) throws -> SiteInfo {
        guard let object = value.object else { throw MoodleError(code: "invalid_response", message: String(localized: "error.response.invalid")) }
        return SiteInfo(
            siteName: object.string("sitename", default: fallback), username: object["username"]?.string,
            userID: object.int64("userid"), fullName: object["fullname"]?.string,
            functions: Set(object.array("functions").compactMap { $0.object?["name"]?.string })
        )
    }

    func courses(_ value: JSONValue) throws -> [MoodleCourse] {
        guard let values = value.array else { throw MoodleError(code: "invalid_response", message: String(localized: "error.response.invalid")) }
        return try values.compactMap { item in
            guard let object = item.object, let id = object.int64("id") else { return nil }
            let fullName = object.string("fullname", default: object.string("displayname", default: String(localized: "course")))
            return MoodleCourse(
                id: id, shortName: object.string("shortname", default: fullName), fullName: fullName,
                summaryHTML: try html.sanitize(object.string("summary")),
                startDate: .moodleTimestamp(object.int64("startdate")), endDate: .moodleTimestamp(object.int64("enddate"))
            )
        }
    }

    func sections(_ value: JSONValue, courseID: Int64) throws -> [MoodleSection] {
        guard let values = value.array else { throw MoodleError(code: "invalid_response", message: String(localized: "error.response.invalid")) }
        return try values.enumerated().compactMap { index, item in
            guard let object = item.object, let id = object.int64("id") else { return nil }
            let modules = try object.array("modules").compactMap { element -> MoodleModule? in
                guard let module = element.object, let moduleID = module.int64("id") else { return nil }
                let files = module.array("contents").compactMap { file -> MoodleFile? in
                    guard let raw = file.object, let rawURL = raw["fileurl"]?.string ?? raw["content"]?.string,
                          let url = URL(string: rawURL) else { return nil }
                    return MoodleFile(name: raw.string("filename", default: url.lastPathComponent), url: url,
                                      mimeType: raw["mimetype"]?.string, sizeBytes: raw.int64("filesize"))
                }
                return MoodleModule(
                    id: moduleID, instanceID: module.int64("instance"),
                    name: module.string("name", default: String(localized: "content")), moduleType: module.string("modname"),
                    descriptionHTML: try html.sanitize(module.string("description", default: module.string("content"))),
                    webURL: module["url"]?.string.flatMap(URL.init(string:)), files: files
                )
            }
            return MoodleSection(id: id, courseID: courseID, name: object.string("name", default: String(format: String(localized: "section.default"), index + 1)),
                                 summaryHTML: try html.sanitize(object.string("summary")), position: object.int("section", default: index), modules: modules)
        }
    }

    func assignments(_ value: JSONValue, courseID: Int64) throws -> [MoodleAssignment] {
        let courses = value.object?.array("courses") ?? []
        return try courses.flatMap { course -> [MoodleAssignment] in
            guard let courseObject = course.object else { return [] }
            return try courseObject.array("assignments").compactMap { element in
                guard let object = element.object, let id = object.int64("id") else { return nil }
                let configs = object.array("configs").compactMap(\.object)
                let allowsText = configs.contains { $0.string("plugin") == "onlinetext" && $0.string("name") == "enabled" && $0.int("value") == 1 }
                let allowsFiles = configs.contains { $0.string("plugin") == "file" && $0.string("name") == "enabled" && $0.int("value") == 1 }
                return MoodleAssignment(
                    id: id, courseID: courseID, courseModuleID: object.int64("cmid") ?? 0,
                    name: object.string("name", default: String(localized: "assignment")), introHTML: try html.sanitize(object.string("intro")),
                    dueDate: .moodleTimestamp(object.int64("duedate")), cutoffDate: .moodleTimestamp(object.int64("cutoffdate")),
                    allowsOnlineText: allowsText, allowsFiles: allowsFiles, requiresSubmitButton: object.bool("requiresubmissionstatement")
                )
            }
        }
    }

    func submissionStatus(_ value: JSONValue) throws -> AssignmentSubmissionStatus {
        guard let object = value.object else { throw MoodleError(code: "invalid_response", message: String(localized: "error.response.invalid")) }
        let submission = object["lastattempt"]?.object?["submission"]?.object ?? object["lastattempt"]?.object ?? [:]
        let feedback = object["feedback"]?.object
        return AssignmentSubmissionStatus(
            status: submission.string("status", default: String(localized: "submission.none")),
            graded: feedback != nil, submittedAt: .moodleTimestamp(submission.int64("timemodified")),
            grade: feedback?["gradefordisplay"]?.string ?? feedback?["grade"]?.string,
            feedbackHTML: try feedback.map { try html.sanitize($0.string("feedbacktext")) }
        )
    }

    func grades(_ value: JSONValue) -> [MoodleGrade] {
        let reports = value.object?.array("usergrades") ?? []
        return reports.flatMap { report -> [MoodleGrade] in
            guard let object = report.object, let courseID = object.int64("courseid") else { return [] }
            return object.array("gradeitems").compactMap { item in
                guard let grade = item.object, let itemID = grade.int64("id") else { return nil }
                return MoodleGrade(courseID: courseID, itemID: itemID, itemName: grade.string("itemname", default: String(localized: "grade")),
                                   gradeFormatted: grade.string("gradeformatted", default: grade.string("graderaw")),
                                   rangeFormatted: grade.string("rangeformatted"), percentageFormatted: grade.string("percentageformatted"))
            }
        }
    }

    func events(_ value: JSONValue) throws -> [MoodleCalendarEvent] {
        let values = value.object?.array("events") ?? value.array ?? []
        return try values.compactMap { item in
            guard let object = item.object, let id = object.int64("id"), let start = Date.moodleTimestamp(object.int64("timestart")) else { return nil }
            return MoodleCalendarEvent(id: id, name: object.string("name", default: String(localized: "event")),
                                       descriptionHTML: try html.sanitize(object.string("description")), startDate: start,
                                       courseID: object.int64("courseid"), actionURL: object["url"]?.string.flatMap(URL.init(string:)))
        }
    }

    func notifications(_ value: JSONValue) throws -> [MoodleNotification] {
        let values = value.object?.array("messages") ?? value.array ?? []
        return try values.compactMap { item in
            guard let object = item.object, let id = object.int64("id") else { return nil }
            return MoodleNotification(id: id, subject: object.string("subject", default: object.string("smallmessage", default: String(localized: "notification"))),
                                      fullMessageHTML: try html.sanitize(object.string("fullmessagehtml", default: object.string("fullmessage"))),
                                      createdAt: .moodleTimestamp(object.int64("timecreated")) ?? .distantPast,
                                      isRead: (object.int64("timeread") ?? 0) > 0 || object.bool("read"),
                                      contextURL: object["contexturl"]?.string.flatMap(URL.init(string:)))
        }
    }

    func conversations(_ value: JSONValue, currentUserID: Int64) throws -> [MoodleConversation] {
        let values = value.object?.array("conversations") ?? value.array ?? []
        return try values.compactMap { item in try conversation(item, currentUserID: currentUserID) }
    }

    func conversation(_ value: JSONValue, currentUserID: Int64) throws -> MoodleConversation? {
        guard let object = value.object, let id = object.int64("id") else { return nil }
        let members = object.array("members").compactMap { item -> MoodleConversationMember? in
            guard let member = item.object, let memberID = member.int64("id") else { return nil }
            return MoodleConversationMember(id: memberID, fullName: member.string("fullname", default: member.string("name", default: String(localized: "moodle.user"))), isCurrentUser: memberID == currentUserID, canMessage: member["canmessage"]?.bool ?? true)
        }
        let type: ConversationType = switch object.int("type") { case 1: .individual; case 2: .group; case 3: .selfConversation; default: .unknown }
        let last = object.array("messages").first?.object
        let safe = try html.sanitize(last?.string("text", default: last?.string("smallmessage") ?? "") ?? object.string("smallmessage"), messageOnly: true)
        let name = object.string("name").nonEmpty ?? members.filter { !$0.isCurrentUser }.map(\.fullName).joined(separator: ", ").nonEmpty ?? (type == .group ? String(localized: "conversation.group") : String(localized: "conversation"))
        return MoodleConversation(id: id, type: type, name: name, members: members, latestMessagePreview: html.plainText(safe),
                                  latestMessageAt: .moodleTimestamp(object.int64("timemodified") ?? last?.int64("timecreated")) ?? .distantPast,
                                  unreadCount: object.int("unreadcount"), isFavourite: object.bool("isfavourite"),
                                  canReply: type == .group || members.contains { !$0.isCurrentUser && $0.canMessage })
    }

    func messages(_ value: JSONValue, conversationID: Int64, currentUserID: Int64) throws -> [MoodleMessage] {
        let values = value.object?.array("messages") ?? value.array ?? []
        return try values.compactMap { item in try message(item, conversationID: conversationID, currentUserID: currentUserID) }
            .sorted { ($0.createdAt, $0.id) < ($1.createdAt, $1.id) }
    }

    func message(_ value: JSONValue, conversationID: Int64, currentUserID: Int64) throws -> MoodleMessage? {
        guard let object = value.object, let id = object.int64("id") ?? object.int64("msgid") else { return nil }
        let senderID = object.int64("useridfrom") ?? object.int64("userid") ?? 0
        let safe = try html.sanitize(object.string("text", default: object.string("fullmessagehtml", default: object.string("fullmessage"))), messageOnly: true)
        return MoodleMessage(id: id, conversationID: object.int64("conversationid") ?? conversationID, senderID: senderID,
                             senderName: object.string("userfullname", default: object.string("sendername", default: String(localized: "moodle.user"))),
                             bodyText: html.plainText(safe), bodyHTML: safe,
                             createdAt: .moodleTimestamp(object.int64("timecreated")) ?? Date(), isMine: senderID == currentUserID,
                             isRead: senderID == currentUserID || (object.int64("timeread") ?? 0) > 0 || object.bool("isread"))
    }

    func users(_ value: JSONValue, currentUserID: Int64) -> [MoodleMessageUser] {
        var result: [MoodleMessageUser] = []
        collectUsers(value, currentUserID: currentUserID, result: &result)
        var seen = Set<Int64>()
        return result.filter { $0.canMessage && seen.insert($0.id).inserted }
    }

    private func collectUsers(_ value: JSONValue, currentUserID: Int64, result: inout [MoodleMessageUser]) {
        if let array = value.array { array.forEach { collectUsers($0, currentUserID: currentUserID, result: &result) }; return }
        guard let object = value.object else { return }
        if let id = object.int64("id") ?? object.int64("userid"), id != currentUserID,
           let name = (object["fullname"]?.string ?? object["name"]?.string)?.nonEmpty {
            result.append(MoodleMessageUser(id: id, fullName: name, canMessage: object["canmessage"]?.bool ?? true))
        } else { object.values.forEach { collectUsers($0, currentUserID: currentUserID, result: &result) } }
    }
}

private extension String { var nonEmpty: String? { isEmpty ? nil : self } }
