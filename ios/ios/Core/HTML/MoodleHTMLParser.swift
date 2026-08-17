import CryptoKit
import Foundation
import SwiftSoup

struct HTMLLoginForm: Sendable {
    var actionURL: URL
    var hiddenFields: [String: String]
}

struct HTMLSiteIdentity: Sendable {
    var siteName: String
    var fullName: String?
    var moodleVersion: String?
    var themeFamily: HTMLThemeFamily
    var sessionKey: String?
    var userID: Int64?
    var features: Set<HTMLFeature>
}

struct ParsedHTMLPage {
    var document: Document
    var url: URL
}

final class MoodleHTMLParser: @unchecked Sendable {
    func document(html: String, url: URL) throws -> Document { try SwiftSoup.parse(html, url.absoluteString) }

    func loginForm(_ document: Document) throws -> HTMLLoginForm? {
        guard let form = try document.select("form#login, form.login-form, form[action*=login]").first(),
              try form.select("input[type=password][name=password]").first() != nil
        else { return nil }
        let rawAction = try form.attr("action")
        let action = rawAction.isEmpty ? URL(string: document.location()) : URL(string: rawAction, relativeTo: URL(string: document.location()))?.absoluteURL
        guard let action else { return nil }
        var hidden: [String: String] = [:]
        for input in try form.select("input[type=hidden][name]") {
            hidden[try input.attr("name")] = try input.attr("value")
        }
        return HTMLLoginForm(actionURL: action, hiddenFields: hidden)
    }

    func isLoginPage(_ document: Document) throws -> Bool {
        if try loginForm(document) != nil { return true }
        return document.body()?.id() == "page-login-index"
    }

    func identity(_ document: Document, fallbackSiteName: String) throws -> HTMLSiteIdentity {
        let classes = try document.body()?.classNames() ?? []
        let hasModernNavigation = try document.select("[data-region=drawer], [data-region=moremenu]").first() != nil
        let modern = classes.contains(where: { $0.localizedCaseInsensitiveContains("boost") }) || hasModernNavigation
        let legacy = try document.select("#page-navbar, .block_navigation").first() != nil
        let family: HTMLThemeFamily = modern ? .modern : legacy ? .legacy : .structuralFallback
        let selectedSiteName = try document.select(".navbar-brand, .site-name, header .logo").first()?.text().trimmed
        let title = try document.title().components(separatedBy: CharacterSet(charactersIn: "|:")).first?.trimmed
        let siteName = selectedSiteName.nonEmpty ?? title.nonEmpty ?? fallbackSiteName
        let fullName = try document.select(".usertext, [data-region=user-menu] .usertext, .usermenu .userbutton").first()?.text().trimmed.nonEmpty
        let scripts = try document.select("script").map { try $0.data() + $0.html() }.joined(separator: "\n")
        let sessionKey = firstCapture(in: scripts, patterns: [
            #"[\"']?sesskey[\"']?\s*[:=]\s*[\"']([^\"']+)"#,
            #"M\.cfg\.sesskey\s*=\s*[\"']([^\"']+)"#,
        ])
        let scriptUserID = firstCapture(in: scripts, patterns: [
            #"[\"']?userid[\"']?\s*[:=]\s*[\"']?(\d+)"#,
            #"M\.cfg\.userid\s*=\s*[\"']?(\d+)"#,
        ]).flatMap(Int64.init)
        let userIDAttribute = try document.select("[data-userid]").first()?.attr("data-userid")
        let attributeUserID = userIDAttribute.flatMap(Int64.init)
        let userID = scriptUserID ?? attributeUserID
        let textAndScripts = (try document.text()) + scripts
        let version = firstCapture(in: textAndScripts, patterns: [#"Moodle(?:\s+|/)([0-9]+(?:\.[0-9]+){1,2})"#])
        return HTMLSiteIdentity(
            siteName: siteName, fullName: fullName, moodleVersion: version, themeFamily: family,
            sessionKey: sessionKey, userID: userID, features: Set(HTMLFeature.allCases)
        )
    }

    func courses(_ document: Document) throws -> [MoodleCourse] {
        var values: [Int64: MoodleCourse] = [:]
        for anchor in try document.select("a[href*=/course/view.php]") {
            guard let url = resolvedURL(try anchor.attr("href"), document: document),
                  let id = queryInt64(url, name: "id"), id > 1 else { continue }
            let candidates = [try anchor.attr("data-course-name"), try anchor.attr("title"), try anchor.text()]
            guard let title = candidates.first(where: { !$0.trimmed.isEmpty })?.trimmed else { continue }
            if values[id] == nil || title.count > values[id]!.fullName.count {
                values[id] = MoodleCourse(id: id, shortName: title, fullName: title, summaryHTML: "", startDate: nil, endDate: nil)
            }
        }
        return values.values.sorted { $0.fullName.localizedCaseInsensitiveCompare($1.fullName) == .orderedAscending }
    }

    func courseSections(_ document: Document, courseID: Int64) throws -> [MoodleSection] {
        let elements = try document.select("[data-sectionid], li.section, .course-section").filter {
            (try? $0.select("a[href*=/mod/][href*=view.php]").first()) != nil || $0.hasAttr("data-sectionid")
        }
        let sections: [MoodleSection] = try elements.enumerated().map { index, section in
            let sectionID = Int64(try section.attr("data-sectionid"))
                ?? firstCapture(in: section.id(), patterns: [#"section-(\d+)"#]).flatMap(Int64.init)
                ?? stableID("\(courseID):section:\(index)")
            let selectedName = try section.select(".sectionname, .section-title, h2, h3, h4").first()?.text().trimmed
            let name = selectedName.nonEmpty ?? String(format: String(localized: "section.default"), index + 1)
            let summary = try section.select(".summary, [data-region=section-content]").first()?.html() ?? ""
            return MoodleSection(id: sectionID, courseID: courseID, name: name, summaryHTML: try sanitize(summary), position: index, modules: try modules(section, document: document))
        }
        if !sections.isEmpty { return unique(sections) }
        let allModules = try modules(document, document: document)
        guard !allModules.isEmpty else { return [] }
        return [MoodleSection(
            id: stableID("\(courseID):section:0"), courseID: courseID,
            name: (try document.select("h1").first()?.text()).nonEmpty ?? String(localized: "course"),
            summaryHTML: "", position: 0, modules: allModules
        )]
    }

    func assignments(_ document: Document, courseID: Int64) throws -> [MoodleAssignment] {
        try modules(document, document: document).filter { $0.moduleType == "assign" }.map {
            MoodleAssignment(id: $0.instanceID ?? $0.id, courseID: courseID, courseModuleID: $0.id, name: $0.name,
                             introHTML: $0.descriptionHTML, dueDate: nil, cutoffDate: nil, allowsOnlineText: false,
                             allowsFiles: false, requiresSubmitButton: false)
        }
    }

    func grades(_ document: Document, courseID: Int64) throws -> [MoodleGrade] {
        try document.select("table tbody tr, .gradeitem").enumerated().compactMap { index, row in
            guard let name = try row.select(".itemname, th, [data-region=grade-item-name]").first()?.text().trimmed.nonEmpty else { return nil }
            let cells = try row.select("td").map { try $0.text().trimmed }
            let grade = try row.select(".grade, .column-grade, [data-region=grade]").first()?.text().trimmed.nonEmpty ?? cells.first ?? ""
            let range = try row.select(".range, .column-range").first()?.text().trimmed ?? ""
            let percentage = try row.select(".percentage, .column-percentage").first()?.text().trimmed ?? ""
            let id = Int64(try row.attr("data-itemid")) ?? stableID("\(courseID):grade:\(index):\(name)")
            return MoodleGrade(courseID: courseID, itemID: id, itemName: name, gradeFormatted: grade, rangeFormatted: range, percentageFormatted: percentage)
        }
    }

    func events(_ document: Document) throws -> [MoodleCalendarEvent] {
        let values: [MoodleCalendarEvent] = try document.select("[data-event-id], [data-eventid], .calendar_event, .event").enumerated().compactMap { index, item in
            guard let name = try item.select(".name, .eventname, h3, h4, a").first()?.text().trimmed.nonEmpty else { return nil }
            let url = try item.select("a[href]").first().flatMap { resolvedURL(try $0.attr("href"), document: document) }
            let directTimestamp = Int64(try item.attr("data-timestamp"))
            let nestedTimestampAttribute = try item.select("time[data-timestamp]").first()?.attr("data-timestamp")
            let nestedTimestamp = nestedTimestampAttribute.flatMap(Int64.init)
            let timestamp = directTimestamp ?? nestedTimestamp ?? 0
            let id = Int64(try item.attr("data-event-id").nonEmpty ?? item.attr("data-eventid")) ?? url.flatMap { queryInt64($0, name: "eventid") } ?? stableID("event:\(index):\(name):\(timestamp)")
            let courseID = url.flatMap { queryInt64($0, name: "course") ?? queryInt64($0, name: "courseid") }
            let description = try item.select(".description, [data-region=event-description]").first()?.html() ?? ""
            return MoodleCalendarEvent(id: id, name: name, descriptionHTML: try sanitize(description), startDate: Date.moodleTimestamp(timestamp) ?? .distantPast, courseID: courseID, actionURL: url)
        }
        return unique(values)
    }

    func notifications(_ document: Document) throws -> [MoodleNotification] {
        let values: [MoodleNotification] = try document.select("[data-notification-id], [data-region=notification-content], .notification").enumerated().compactMap { index, item in
            let subject = try item.select(".subject, [data-region=notification-subject], h3, h4").first()?.text().trimmed.nonEmpty ?? String((try item.text().trimmed).prefix(160))
            guard !subject.isEmpty else { return nil }
            let url = try item.select("a[href]").first().flatMap { resolvedURL(try $0.attr("href"), document: document) }
            let directTimestamp = Int64(try item.attr("data-timestamp"))
            let nestedTimestampAttribute = try item.select("time[data-timestamp]").first()?.attr("data-timestamp")
            let nestedTimestamp = nestedTimestampAttribute.flatMap(Int64.init)
            let created = directTimestamp ?? nestedTimestamp ?? 0
            let id = Int64(try item.attr("data-notification-id")) ?? stableID("notification:\(index):\(subject):\(created)")
            return MoodleNotification(id: id, subject: subject, fullMessageHTML: try sanitize(item.html()), createdAt: Date.moodleTimestamp(created) ?? .distantPast, isRead: item.hasClass("read"), contextURL: url)
        }
        return unique(values)
    }

    func moduleContent(_ document: Document, fallbackTitle: String, originalURL: URL) throws -> MoodleModuleContent {
        let main = try document.select("[role=main], #region-main, #page-content").first() ?? document.body()
        let title = try document.select("[role=main] h1, #region-main h1, .page-header-headings h1, h1").first()?.text().trimmed.nonEmpty ?? fallbackTitle
        let content = try main?.select(".activity-description, .resourcecontent, .foldertree, .generalbox, .box:not(.activity-navigation)").first() ?? main
        let files = try main?.select("a[href*=pluginfile.php], a[href*='forcedownload=1']").compactMap { anchor -> MoodleFile? in
            guard let url = resolvedURL(try anchor.attr("href"), document: document) else { return nil }
            let name = try anchor.text().trimmed.nonEmpty ?? url.lastPathComponent
            return MoodleFile(name: name, url: url, mimeType: nil, sizeBytes: nil)
        } ?? []
        return MoodleModuleContent(title: title, bodyHTML: try sanitize(content?.html() ?? ""), files: unique(files), originalURL: originalURL)
    }

    func sanitize(_ html: String, messageOnly: Bool = false) throws -> String {
        let whitelist = Whitelist.none()
        if messageOnly {
            try whitelist.addTags("p", "br", "strong", "em", "b", "i", "code", "a")
        } else {
            try whitelist.addTags(
                "p", "br", "strong", "em", "b", "i", "code", "pre", "a", "ul", "ol", "li",
                "h1", "h2", "h3", "h4", "h5", "blockquote", "section", "article", "figure",
                "figcaption", "table", "thead", "tbody", "tr", "th", "td"
            )
        }
        try whitelist.addAttributes("a", "href", "title").addProtocols("a", "href", "https")
        let dirty = try SwiftSoup.parseBodyFragment(html)
        let clean = try Cleaner(headWhitelist: nil, bodyWhitelist: whitelist).clean(dirty)
        return try clean.body()?.html() ?? ""
    }

    func plainText(_ safeHTML: String) -> String { (try? SwiftSoup.parse(safeHTML).text()) ?? "" }

    private func modules(_ root: Element, document: Document) throws -> [MoodleModule] {
        var result: [Int64: MoodleModule] = [:]
        for anchor in try root.select("a[href*=/mod/][href*=view.php]") {
            guard let url = resolvedURL(try anchor.attr("href"), document: document),
                  let id = queryInt64(url, name: "id"),
                  let type = firstCapture(in: url.absoluteString, patterns: [#"/mod/([^/]+)/view\.php"#]) else { continue }
            let container = activityContainer(for: anchor)
            let candidates = [try anchor.select(".instancename").first()?.ownText(), try anchor.attr("title"), try anchor.text()]
            guard let name = candidates.compactMap({ $0?.trimmed.nonEmpty }).first else { continue }
            let description = try container?.select(".contentafterlink, .description").first()?.html() ?? ""
            let files = try container?.select("a[href*=pluginfile.php]").compactMap { fileAnchor -> MoodleFile? in
                guard let fileURL = resolvedURL(try fileAnchor.attr("href"), document: document) else { return nil }
                return MoodleFile(name: try fileAnchor.text().trimmed.nonEmpty ?? String(localized: "file"), url: fileURL, mimeType: nil, sizeBytes: nil)
            } ?? []
            result[id] = MoodleModule(id: id, instanceID: nil, name: name, moduleType: type, descriptionHTML: try sanitize(description), webURL: url, files: files)
        }
        return Array(result.values)
    }

    private func firstCapture(in input: String, patterns: [String]) -> String? {
        for pattern in patterns {
            guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
                  let match = regex.firstMatch(in: input, range: NSRange(input.startIndex..., in: input)), match.numberOfRanges > 1,
                  let range = Range(match.range(at: 1), in: input) else { continue }
            return String(input[range])
        }
        return nil
    }

    private func activityContainer(for element: Element) -> Element? {
        var candidate: Element? = element
        while let current = candidate {
            if current.hasClass("activity") || current.hasAttr("data-for") { return current }
            candidate = current.parent()
        }
        return element.parent()
    }

    private func resolvedURL(_ value: String, document: Document) -> URL? {
        URL(string: value, relativeTo: URL(string: document.location()))?.absoluteURL
    }

    private func queryInt64(_ url: URL, name: String) -> Int64? {
        URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == name }?.value.flatMap(Int64.init)
    }

    private func stableID(_ value: String) -> Int64 {
        let digest = SHA256.hash(data: Data(value.utf8))
        let bits = digest.prefix(8).reduce(UInt64(0)) { ($0 << 8) | UInt64($1) }
        return Int64(bits & UInt64(Int64.max)).clampedPositive
    }

    private func unique<T: Identifiable>(_ values: [T]) -> [T] where T.ID: Hashable {
        var seen = Set<T.ID>(); return values.filter { seen.insert($0.id).inserted }
    }
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
    var nonEmpty: String? { isEmpty ? nil : self }
}

private extension Optional where Wrapped == String {
    var nonEmpty: String? { self?.isEmpty == false ? self : nil }
}

private extension Int64 {
    var clampedPositive: Int64 { self > 0 ? self : 1 }
}
