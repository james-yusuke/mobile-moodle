import SwiftUI
import UniformTypeIdentifiers

struct CourseDetailView: View {
    @Environment(AppModel.self) private var model
    var course: MoodleCourse
    var body: some View {
        PortalBackground {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 18) {
                    PortalCourseCover(course: course, height: 186).clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                    if !course.summaryHTML.isEmpty { HTMLText(html: course.summaryHTML).padding(18).portalCard() }
                    let sections = model.courseSections[course.id] ?? model.snapshot.sections[course.id] ?? []
                    if sections.isEmpty && model.isLoading { PortalLoadingView().frame(height: 320) }
                    else if sections.isEmpty { PortalEmptyState(icon: "square.stack.3d.up", title: "course.empty.title", message: "course.empty.body") }
                    else { ForEach(sections) { section in SectionView(section: section, assignments: model.courseAssignments[course.id] ?? []) } }
                }.padding(16).padding(.bottom, 30)
            }.refreshable { await model.refreshCourse(course.id) }
        }.navigationTitle(course.shortName).navigationBarTitleDisplayMode(.inline).task { await model.refreshCourse(course.id) }
    }
}

private struct SectionView: View {
    @Environment(AppModel.self) private var model
    var section: MoodleSection
    var assignments: [MoodleAssignment]
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 6) {
                Text(section.name).font(.title3.bold())
                if !section.summaryHTML.isEmpty { HTMLText(html: section.summaryHTML).font(.subheadline) }
            }.padding(18)
            Divider()
            if section.modules.isEmpty { Text("course.section.empty").font(.subheadline).foregroundStyle(.secondary).padding(18) }
            else { ForEach(section.modules) { module in
                Button { open(module) } label: {
                    HStack(spacing: 14) {
                        Image(systemName: moduleIcon(module.nativeType)).font(.title3).foregroundStyle(PortalTheme.teal)
                            .frame(width: 44, height: 44).background(PortalTheme.teal.opacity(0.1), in: RoundedRectangle(cornerRadius: 13))
                        VStack(alignment: .leading, spacing: 4) { Text(module.name).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading); Text(moduleLabel(module.nativeType)).font(.caption).foregroundStyle(.secondary) }
                        Spacer(); Image(systemName: "chevron.right").foregroundStyle(.tertiary)
                    }.padding(15).contentShape(Rectangle())
                }.buttonStyle(.plain)
                if module.id != section.modules.last?.id { Divider().padding(.leading, 72) }
            } }
        }.portalCard()
    }
    private func open(_ module: MoodleModule) {
        if module.nativeType == .assignment, let assignment = assignments.first(where: { $0.courseModuleID == module.id || $0.id == module.instanceID }) { model.path.append(AppRoute.assignment(assignment)) }
        else { model.path.append(AppRoute.module(section.courseID, module)) }
    }
    private func moduleIcon(_ value: NativeModuleType) -> String { switch value { case .assignment: "doc.text.fill"; case .page: "text.page.fill"; case .url: "link"; case .resource: "doc.fill"; case .folder: "folder.fill"; case .unsupported: "safari.fill" } }
    private func moduleLabel(_ value: NativeModuleType) -> LocalizedStringKey {
        switch value {
        case .assignment: "module.assignment"
        case .page: "module.page"
        case .url: "module.url"
        case .resource: "module.resource"
        case .folder: "module.folder"
        case .unsupported: "module.unsupported"
        }
    }
}

struct ModuleContentView: View {
    @Environment(AppModel.self) private var model
    var module: MoodleModule
    @State private var safariURL: URL?
    @State private var previewURL: URL?
    var body: some View {
        PortalBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 9) { PortalStatusPill(text: module.moduleType.uppercased(), systemImage: "doc.fill", emphasized: true); Text(module.name).font(.largeTitle.bold()) }.frame(maxWidth: .infinity, alignment: .leading)
                    if let content = model.moduleContents[module.id] {
                        if !content.bodyHTML.isEmpty { HTMLText(html: content.bodyHTML).padding(18).portalCard() }
                        if !content.files.isEmpty { VStack(alignment: .leading, spacing: 12) { PortalSectionHeader(title: "files"); ForEach(content.files) { file in FileRow(file: file) { Task { previewURL = await model.cachedFile(file) } } } } }
                        if let original = content.originalURL { Button { Task { safariURL = await model.authenticatedURL(original) } } label: { Label("action.open_moodle", systemImage: "safari.fill").frame(maxWidth: .infinity).frame(minHeight: 50) }.buttonStyle(.bordered) }
                    } else { PortalLoadingView().frame(height: 300) }
                }.padding(16).padding(.bottom, 28)
            }
        }.navigationTitle(Text("content")).navigationBarTitleDisplayMode(.inline).task { await model.loadModule(module) }
        .sheet(isPresented: Binding(
            get: { safariURL != nil },
            set: { presented in if !presented { safariURL = nil } }
        )) {
            if let safariURL { SafariView(url: safariURL) }
        }
        .sheet(isPresented: Binding(
            get: { previewURL != nil },
            set: { presented in if !presented { previewURL = nil } }
        )) {
            if let previewURL { QuickLookView(url: previewURL) }
        }
    }
}

private struct FileRow: View {
    var file: MoodleFile; var open: () -> Void
    var body: some View { Button(action: open) { HStack(spacing: 13) { Image(systemName: "doc.fill").foregroundStyle(PortalTheme.teal).frame(width: 42, height: 42).background(PortalTheme.teal.opacity(0.1), in: RoundedRectangle(cornerRadius: 12)); VStack(alignment: .leading, spacing: 3) { Text(file.name).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).lineLimit(2); if let size = file.sizeBytes { Text(ByteCountFormatter.string(fromByteCount: size, countStyle: .file)).font(.caption).foregroundStyle(.secondary) } }; Spacer(); Image(systemName: "arrow.down.circle").foregroundStyle(.secondary) }.padding(14).contentShape(Rectangle()) }.buttonStyle(.plain).portalCard(radius: 15) }
}

struct AssignmentView: View {
    @Environment(AppModel.self) private var model
    var assignment: MoodleAssignment
    @State private var onlineText = ""
    @State private var fileURL: URL?
    @State private var showPicker = false
    @State private var confirmSubmit = false
    @State private var submitted = false
    var body: some View {
        PortalBackground {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 10) { PortalStatusPill(text: String(localized: "assignment"), systemImage: "doc.text.fill", emphasized: true); Text(assignment.name).font(.largeTitle.bold()); if let due = assignment.dueDate { Label { Text(due, format: .dateTime.weekday(.wide).month().day().hour().minute()) } icon: { Image(systemName: "clock.fill") }.font(.subheadline).foregroundStyle(due < Date() ? Color.red : PortalTheme.gold) } }.frame(maxWidth: .infinity, alignment: .leading)
                    if !assignment.introHTML.isEmpty { HTMLText(html: assignment.introHTML).padding(18).portalCard() }
                    if let status = model.assignmentStatuses[assignment.id] { statusCard(status) }
                    if model.activeAccount?.connectionMode == .nativeHtml {
                        PortalEmptyState(icon: "eye.fill", title: "assignment.readonly.title", message: "assignment.readonly.body")
                    } else if assignment.allowsOnlineText || assignment.allowsFiles {
                        VStack(alignment: .leading, spacing: 14) {
                            PortalSectionHeader(title: "assignment.submission")
                            if assignment.allowsOnlineText { TextEditor(text: $onlineText).frame(minHeight: 150).padding(10).scrollContentBackground(.hidden).background(Color.secondary.opacity(0.07), in: RoundedRectangle(cornerRadius: 14)).accessibilityLabel(Text("assignment.online_text")) }
                            if assignment.allowsFiles { Button { showPicker = true } label: { HStack { Image(systemName: "paperclip"); Text(fileURL?.lastPathComponent ?? String(localized: "assignment.choose_file")); Spacer(); Image(systemName: "chevron.right") }.frame(minHeight: 48) }.buttonStyle(.bordered) }
                            Button { confirmSubmit = true } label: { Label("assignment.submit", systemImage: "paperplane.fill").font(.headline).frame(maxWidth: .infinity).frame(minHeight: 52) }.buttonStyle(.borderedProminent).tint(PortalTheme.teal).disabled(!model.isOnline || (onlineText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && fileURL == nil))
                        }.padding(18).portalCard()
                    }
                    if submitted { Label("assignment.submitted", systemImage: "checkmark.circle.fill").foregroundStyle(PortalTheme.teal).font(.headline).padding(16).frame(maxWidth: .infinity).background(PortalTheme.teal.opacity(0.1), in: RoundedRectangle(cornerRadius: 16)) }
                }.padding(16).padding(.bottom, 30)
            }
        }.navigationTitle(Text("assignment")).navigationBarTitleDisplayMode(.inline).task { await model.loadAssignment(assignment) }
        .fileImporter(isPresented: $showPicker, allowedContentTypes: [.item], allowsMultipleSelection: false) { result in fileURL = try? result.get().first }
        .confirmationDialog("assignment.submit.confirm", isPresented: $confirmSubmit, titleVisibility: .visible) { Button("assignment.submit") { Task { submitted = await model.submit(assignment, text: onlineText, fileURL: fileURL) } }; Button("action.cancel", role: .cancel) {} }
    }
    private func statusCard(_ status: AssignmentSubmissionStatus) -> some View {
        VStack(alignment: .leading, spacing: 12) { PortalSectionHeader(title: "assignment.status"); LabeledContent("assignment.state", value: status.status); if let date = status.submittedAt { LabeledContent("assignment.sent_at") { Text(date, format: .dateTime.year().month().day().hour().minute()) } }; if let grade = status.grade { LabeledContent("grade", value: grade) }; if let feedback = status.feedbackHTML { HTMLText(html: feedback) } }.padding(18).portalCard()
    }
}
