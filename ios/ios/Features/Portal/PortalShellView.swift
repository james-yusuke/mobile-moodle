import SwiftUI

struct PortalShellView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var showNotifications = false
    @State private var showSettings = false
    @State private var showAccounts = false
    var onAddSite: () -> Void

    var body: some View {
        @Bindable var model = model
        PortalBackground {
            Group {
                if model.isLoading && model.snapshot.courses.isEmpty { PortalLoadingView() }
                else if sizeClass == .regular {
                    HStack(spacing: 0) {
                        PortalRail(selection: $model.selectedDestination, account: model.activeAccount, onSettings: { showSettings = true })
                        Divider().opacity(0.5)
                        mainContent.padding(.horizontal, 8)
                    }
                } else {
                    mainContent.safeAreaInset(edge: .bottom, spacing: 0) { PortalDock(selection: $model.selectedDestination, unread: model.snapshot.unreadMessages) }
                }
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showNotifications) { NavigationStack { NotificationListView().environment(model) } }
        .sheet(isPresented: $showSettings) { NavigationStack { SettingsView(onAccounts: { showSettings = false; showAccounts = true }).environment(model) } }
        .sheet(isPresented: $showAccounts) { NavigationStack { AccountSwitcherView(onAddSite: { showAccounts = false; onAddSite() }).environment(model) } }
    }

    private var mainContent: some View {
        VStack(spacing: 0) {
            PortalTopBar(showNotifications: { showNotifications = true }, showSettings: { showSettings = true }, showAccounts: { showAccounts = true })
            if !model.isOnline { HStack { OfflinePill(); Spacer() }.padding(.horizontal).padding(.bottom, 6) }
            if let error = model.errorMessage { PortalErrorBanner(message: error) { model.errorMessage = nil }.padding(.horizontal).padding(.bottom, 6) }
            destinationView.transition(.opacity.combined(with: .move(edge: .trailing)))
        }
        .animation(.easeOut(duration: UIAccessibility.isReduceMotionEnabled ? 0 : 0.22), value: model.selectedDestination)
    }

    @ViewBuilder private var destinationView: some View {
        switch model.selectedDestination {
        case .home: HomeView()
        case .courses: CourseListView()
        case .messages: MessageListView()
        case .calendar: CalendarView()
        }
    }
}

private struct PortalTopBar: View {
    @Environment(AppModel.self) private var model
    var showNotifications: () -> Void
    var showSettings: () -> Void
    var showAccounts: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            PortalBrandMark(size: 38)
            VStack(alignment: .leading, spacing: 1) {
                Text(model.activeAccount?.siteName ?? String(localized: "app.name")).font(.subheadline.weight(.bold)).lineLimit(1)
                Text(model.activeAccount?.fullName ?? model.activeAccount?.username ?? "").font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer()
            Button(action: showNotifications) {
                ZStack(alignment: .topTrailing) {
                    Image(systemName: "bell.fill").frame(width: 44, height: 44).background(Color.secondary.opacity(0.08), in: Circle())
                    let unread = model.snapshot.notifications.filter { !$0.isRead }.count
                    if unread > 0 { Text(String(min(unread, 99))).font(.caption2.bold()).foregroundStyle(.white).padding(4).background(Color.red, in: Capsule()).offset(x: 2, y: -2) }
                }
            }.accessibilityLabel(Text("notifications")).accessibilityIdentifier("notifications.button")
            Menu {
                Button("accounts.switch", systemImage: "person.2.fill", action: showAccounts)
                Button("settings", systemImage: "gearshape.fill", action: showSettings)
            } label: { InitialAvatar(name: model.activeAccount?.fullName ?? model.activeAccount?.username ?? "M", size: 40) }
            .accessibilityLabel(Text("account.menu"))
        }.padding(.horizontal, 18).padding(.vertical, 10)
    }
}

private struct PortalDock: View {
    @Binding var selection: PortalDestination
    var unread: Int
    var body: some View {
        HStack(spacing: 4) {
            ForEach(PortalDestination.allCases) { item in
                Button {
                    selection = item
                } label: {
                    VStack(spacing: 4) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: item.icon).font(.system(size: 19, weight: .semibold)).frame(height: 23)
                            if item == .messages && unread > 0 { Circle().fill(Color.red).frame(width: 8, height: 8).offset(x: 5, y: -2) }
                        }
                        Text(item.label).font(.caption2.weight(selection == item ? .bold : .medium))
                    }.foregroundStyle(selection == item ? PortalTheme.teal : Color.secondary).frame(maxWidth: .infinity).frame(minHeight: 52)
                    .background(selection == item ? PortalTheme.teal.opacity(0.1) : .clear, in: RoundedRectangle(cornerRadius: 15))
                }.buttonStyle(.plain).accessibilityAddTraits(selection == item ? .isSelected : []).accessibilityIdentifier("tab.\(item.rawValue)")
            }
        }.padding(6).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.35))).shadow(color: PortalTheme.navy.opacity(0.16), radius: 20, y: 7)
        .padding(.horizontal, 14).padding(.bottom, 5)
    }
}

private struct PortalRail: View {
    @Binding var selection: PortalDestination
    var account: SiteAccount?
    var onSettings: () -> Void
    var body: some View {
        VStack(spacing: 12) {
            PortalBrandMark(size: 48).padding(.bottom, 12)
            ForEach(PortalDestination.allCases) { item in
                Button { selection = item } label: {
                    VStack(spacing: 5) { Image(systemName: item.icon).font(.title3); Text(item.label).font(.caption2.weight(.semibold)) }
                        .foregroundStyle(selection == item ? PortalTheme.teal : Color.secondary).frame(width: 74, height: 62)
                        .background(selection == item ? PortalTheme.teal.opacity(0.11) : .clear, in: RoundedRectangle(cornerRadius: 17))
                }.buttonStyle(.plain).accessibilityAddTraits(selection == item ? .isSelected : []).accessibilityIdentifier("tab.\(item.rawValue)")
            }
            Spacer()
            Button(action: onSettings) { Image(systemName: "gearshape.fill").frame(width: 50, height: 50).background(Color.secondary.opacity(0.08), in: Circle()) }.accessibilityLabel(Text("settings"))
            InitialAvatar(name: account?.fullName ?? account?.username ?? "M", size: 42)
        }.padding(.vertical, 18).padding(.horizontal, 9)
    }
}

struct AccountSwitcherView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    var onAddSite: () -> Void
    var body: some View {
        List {
            Section("accounts.title") {
                ForEach(model.accounts) { account in
                    Button {
                        Task { await model.activate(account.id); dismiss() }
                    } label: {
                        HStack(spacing: 12) {
                            InitialAvatar(name: account.fullName ?? account.siteName, size: 42)
                            VStack(alignment: .leading) { Text(account.fullName ?? account.username ?? String(localized: "account")).foregroundStyle(.primary); Text(account.siteName).font(.caption).foregroundStyle(.secondary) }
                            Spacer(); if account.id == model.activeAccount?.id { Image(systemName: "checkmark.circle.fill").foregroundStyle(PortalTheme.teal) }
                        }
                    }.buttonStyle(.plain)
                }
            }
            Button { dismiss(); onAddSite() } label: { Label("accounts.add", systemImage: "plus.circle.fill") }
        }.navigationTitle(Text("accounts.title")).toolbar { ToolbarItem(placement: .confirmationAction) { Button("action.done") { dismiss() } } }
    }
}
