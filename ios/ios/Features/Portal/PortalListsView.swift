import SwiftUI

struct CourseListView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var search = ""
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 6) { Text("courses.eyebrow").font(.caption.bold()).foregroundStyle(PortalTheme.teal).textCase(.uppercase); Text("courses.title").font(.largeTitle.bold()); Text("courses.subtitle").font(.subheadline).foregroundStyle(.secondary) }
                HStack { Image(systemName: "magnifyingglass").foregroundStyle(.secondary); TextField("courses.search", text: $search).textInputAutocapitalization(.never); if !search.isEmpty { Button { search = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }.accessibilityLabel(Text("action.clear")) } }
                    .padding(.horizontal, 15).frame(minHeight: 50).background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
                if filtered.isEmpty { PortalEmptyState(icon: "books.vertical", title: "courses.empty.title", message: search.isEmpty ? "courses.empty.body" : "courses.search.empty") }
                else if sizeClass == .regular {
                    let pairs = filtered.chunked(into: 2)
                    ForEach(Array(pairs.enumerated()), id: \.offset) { _, pair in HStack(alignment: .top, spacing: 16) { ForEach(pair) { courseCard($0).frame(maxWidth: .infinity) }; if pair.count == 1 { Color.clear.frame(maxWidth: .infinity) } } }
                } else { ForEach(filtered) { courseCard($0) } }
            }.padding(.horizontal, sizeClass == .regular ? 24 : 16).padding(.bottom, 34)
        }.refreshable { await model.refresh() }
    }
    private var filtered: [MoodleCourse] { let value = search.trimmingCharacters(in: .whitespacesAndNewlines); return value.isEmpty ? model.snapshot.courses : model.snapshot.courses.filter { $0.fullName.localizedCaseInsensitiveContains(value) || $0.shortName.localizedCaseInsensitiveContains(value) } }
    private func courseCard(_ course: MoodleCourse) -> some View {
        Button { model.path.append(AppRoute.course(course.id)) } label: {
            VStack(alignment: .leading, spacing: 0) {
                PortalCourseCover(course: course)
                VStack(alignment: .leading, spacing: 8) {
                    Text(course.fullName).font(.headline).foregroundStyle(.primary).lineLimit(2)
                    HStack { PortalStatusPill(text: course.shortName, systemImage: "number", emphasized: true); Spacer(); if let end = course.endDate { Label { Text(end, format: .dateTime.month().day()) } icon: { Image(systemName: "calendar") }.font(.caption).foregroundStyle(.secondary) } }
                }.padding(16)
            }.portalCard()
        }.buttonStyle(.plain).accessibilityIdentifier("course.\(course.id)")
    }
}

struct CalendarView: View {
    @Environment(AppModel.self) private var model
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 6) { Text("calendar.eyebrow").font(.caption.bold()).foregroundStyle(PortalTheme.teal).textCase(.uppercase); Text("calendar.title").font(.largeTitle.bold()); Text("calendar.subtitle").font(.subheadline).foregroundStyle(.secondary) }
                let grouped = Dictionary(grouping: model.snapshot.events.filter { $0.startDate >= Calendar.current.startOfDay(for: Date()) }) { Calendar.current.startOfDay(for: $0.startDate) }
                if grouped.isEmpty { PortalEmptyState(icon: "calendar", title: "calendar.empty.title", message: "calendar.empty.body") }
                else { ForEach(grouped.keys.sorted(), id: \.self) { day in Section { ForEach(grouped[day] ?? []) { EventRow(event: $0) } } header: { Text(day, format: .dateTime.weekday(.wide).month(.wide).day()).font(.headline).padding(.top, 8) } } }
            }.padding(.horizontal, 16).padding(.bottom, 34)
        }.refreshable { await model.refresh() }
    }
}

struct NotificationListView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var safariURL: URL?
    var body: some View {
        PortalBackground {
            ScrollView {
                LazyVStack(spacing: 0) {
                    if model.snapshot.notifications.isEmpty { PortalEmptyState(icon: "bell", title: "notifications.empty.title", message: "notifications.empty.body").padding() }
                    else { ForEach(model.snapshot.notifications) { notification in
                        Button { Task { await model.markNotification(notification); if let url = notification.contextURL { safariURL = await model.authenticatedURL(url) } } } label: {
                            HStack(alignment: .top, spacing: 14) {
                                Circle().fill(notification.isRead ? Color.secondary.opacity(0.15) : PortalTheme.gold).frame(width: 9, height: 9).padding(.top, 7).accessibilityLabel(notification.isRead ? Text("status.read") : Text("status.unread"))
                                VStack(alignment: .leading, spacing: 5) { Text(notification.subject).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading); Text(notification.createdAt, format: .relative(presentation: .named)).font(.caption).foregroundStyle(.secondary) }
                                Spacer(); Image(systemName: "chevron.right").foregroundStyle(.tertiary)
                            }.padding(.vertical, 16).contentShape(Rectangle())
                        }.buttonStyle(.plain); Divider().padding(.leading, 24)
                    } }
                }.padding(.horizontal, 18)
            }.refreshable { await model.refresh() }
        }.navigationTitle(Text("notifications")).navigationBarTitleDisplayMode(.inline).toolbar { ToolbarItem(placement: .confirmationAction) { Button("action.done") { dismiss() } } }
        .sheet(isPresented: Binding(
            get: { safariURL != nil },
            set: { presented in if !presented { safariURL = nil } }
        )) {
            if let safariURL { SafariView(url: safariURL) }
        }
    }
}

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var showDelete = false
    @State private var showReauthentication = false
    var onAccounts: () -> Void
    var body: some View {
        PortalBackground {
            List {
                if let account = model.activeAccount {
                    Section {
                        HStack(spacing: 14) { InitialAvatar(name: account.fullName ?? account.siteName, size: 48); VStack(alignment: .leading) { Text(account.fullName ?? account.username ?? String(localized: "account")).font(.headline); Text(account.siteName).font(.caption).foregroundStyle(.secondary) } }
                        LabeledContent("settings.connection", value: account.connectionMode == .nativeApi ? String(localized: "mode.api") : String(localized: "mode.html"))
                        if let version = account.moodleVersion { LabeledContent("settings.version", value: version) }
                        Button("accounts.switch", systemImage: "person.2.fill") { dismiss(); onAccounts() }
                    } header: { Text("settings.account") }
                    Section {
                        Toggle(isOn: Binding(
                            get: { model.messagePreviewEnabled },
                            set: { enabled in model.setMessagePreview(enabled) }
                        )) { Label("settings.preview", systemImage: "text.bubble") }
                        Text("settings.preview.body").font(.caption).foregroundStyle(.secondary)
                    } header: { Text("settings.notifications") }
                    Section {
                        Label("settings.security.https", systemImage: "lock.shield.fill")
                        Label("settings.security.password", systemImage: "key.slash.fill")
                        Label("settings.security.direct", systemImage: "arrow.left.arrow.right.circle.fill")
                    } header: { Text("settings.security") }
                    Section {
                        if account.authenticationState == .reauthenticationRequired { Button("login.again", systemImage: "person.badge.key") { showReauthentication = true } }
                        Button("account.remove", systemImage: "trash", role: .destructive) { showDelete = true }
                    }
                }
            }.scrollContentBackground(.hidden)
        }.navigationTitle(Text("settings")).toolbar { ToolbarItem(placement: .confirmationAction) { Button("action.done") { dismiss() } } }
        .confirmationDialog("account.remove.confirm", isPresented: $showDelete, titleVisibility: .visible) { Button("account.remove", role: .destructive) { if let id = model.activeAccount?.id { model.remove(id); dismiss() } } }
        .sheet(isPresented: $showReauthentication) { ReauthenticationView().environment(model) }
    }
}

struct ReauthenticationView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var username = ""
    @State private var password = ""
    var body: some View {
        NavigationStack { Form { Section("login.again") { TextField("login.username", text: $username).textInputAutocapitalization(.never); SecureField("login.password", text: $password) }; Button("login.action") { Task { await model.reauthenticate(username: username, password: password); password = ""; if model.activeAccount?.authenticationState == .authenticated { dismiss() } } }.disabled(username.isEmpty || password.isEmpty || model.isLoading) }.navigationTitle(Text("login.again")).toolbar { ToolbarItem(placement: .cancellationAction) { Button("action.cancel") { dismiss() } } } }
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] { stride(from: 0, to: count, by: size).map { Array(self[$0..<Swift.min($0 + size, count)]) } }
}
