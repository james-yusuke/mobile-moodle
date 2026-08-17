import SwiftUI

struct HomeView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.horizontalSizeClass) private var sizeClass
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 22) {
                HomeHero(account: model.activeAccount)
                if let event = model.snapshot.nextEvent { NextActionCard(event: event) }
                else { NoUpcomingCard() }
                metricGrid
                courseSection
                if sizeClass == .regular {
                    HStack(alignment: .top, spacing: 18) { scheduleSection; gradeSection }
                } else { scheduleSection; gradeSection }
            }.padding(.horizontal, sizeClass == .regular ? 24 : 16).padding(.bottom, 36)
        }.refreshable { await model.refresh() }
    }

    private var metricGrid: some View {
        HStack(spacing: 10) {
            MetricCard(icon: "bubble.left.and.bubble.right.fill", value: model.snapshot.unreadMessages, label: "home.unread", color: PortalTheme.teal)
            MetricCard(icon: "books.vertical.fill", value: model.snapshot.courses.count, label: "home.courses", color: Color(hex: 0x38669B))
            MetricCard(icon: "calendar", value: model.snapshot.events.filter { $0.startDate >= Date() }.count, label: "home.events", color: PortalTheme.gold)
        }
    }

    private var courseSection: some View {
        VStack(alignment: .leading, spacing: 13) {
            PortalSectionHeader(title: "courses", action: "action.view_all") { model.selectedDestination = .courses }
            if model.snapshot.courses.isEmpty { PortalEmptyState(icon: "books.vertical", title: "courses.empty.title", message: "courses.empty.body") }
            else {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 14) {
                        ForEach(model.snapshot.courses.prefix(6)) { course in
                            Button { model.path.append(AppRoute.course(course.id)) } label: {
                                VStack(alignment: .leading, spacing: 0) {
                                    PortalCourseCover(course: course, height: 120)
                                    Text(course.fullName).font(.headline).foregroundStyle(.primary).lineLimit(2).padding(14).frame(maxWidth: .infinity, alignment: .leading)
                                }.frame(width: 270).portalCard()
                            }.buttonStyle(.plain)
                        }
                    }.padding(.vertical, 4)
                }
            }
        }
    }

    private var scheduleSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            PortalSectionHeader(title: "calendar.upcoming")
            let events = model.snapshot.events.filter { $0.startDate >= Date() }.prefix(4)
            if events.isEmpty { PortalEmptyState(icon: "calendar", title: "calendar.empty.title", message: "calendar.empty.body") }
            else { ForEach(Array(events)) { EventRow(event: $0) } }
        }.frame(maxWidth: .infinity, alignment: .top)
    }

    private var gradeSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            PortalSectionHeader(title: "grades")
            if model.snapshot.grades.isEmpty { PortalEmptyState(icon: "checkmark.seal", title: "grades.empty.title", message: "grades.empty.body") }
            else { ForEach(model.snapshot.grades.prefix(4)) { GradeRow(grade: $0) } }
        }.frame(maxWidth: .infinity, alignment: .top)
    }
}

private struct HomeHero: View {
    var account: SiteAccount?
    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [PortalTheme.navy, PortalTheme.tealDark, Color(hex: 0x08766B)], startPoint: .topLeading, endPoint: .bottomTrailing)
            Circle().stroke(Color.white.opacity(0.1), lineWidth: 34).frame(width: 220, height: 220).offset(x: 190, y: -70).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 10) {
                Text(greeting).font(.subheadline.weight(.semibold)).foregroundStyle(Color.white.opacity(0.75))
                Text(account?.fullName ?? account?.username ?? String(localized: "home.welcome")).font(.system(.title, design: .rounded, weight: .bold)).foregroundStyle(.white).lineLimit(2)
                HStack(spacing: 8) {
                    Label(account?.siteName ?? String(localized: "app.name"), systemImage: "building.columns.fill").lineLimit(1)
                    if let date = account?.lastSync { Text("·"); Text(date, format: .relative(presentation: .named)) }
                }.font(.caption).foregroundStyle(Color.white.opacity(0.72))
            }.padding(24)
        }.frame(maxWidth: .infinity).frame(height: 176).clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous)).shadow(color: PortalTheme.navy.opacity(0.2), radius: 22, y: 10)
    }
    private var greeting: LocalizedStringKey { let hour = Calendar.current.component(.hour, from: Date()); return hour < 12 ? "home.greeting.morning" : hour < 18 ? "home.greeting.afternoon" : "home.greeting.evening" }
}

private struct NextActionCard: View {
    var event: MoodleCalendarEvent
    var body: some View {
        HStack(spacing: 16) {
            VStack { Text(event.startDate, format: .dateTime.day()).font(.title.bold()); Text(event.startDate, format: .dateTime.month(.abbreviated)).font(.caption.weight(.bold)).textCase(.uppercase) }
                .foregroundStyle(.white).frame(width: 64, height: 76).background(PortalTheme.gold, in: RoundedRectangle(cornerRadius: 17))
            VStack(alignment: .leading, spacing: 5) { Text("home.next").font(.caption.bold()).foregroundStyle(PortalTheme.teal).textCase(.uppercase); Text(event.name).font(.headline).lineLimit(2); Text(event.startDate, format: .dateTime.weekday(.wide).hour().minute()).font(.subheadline).foregroundStyle(.secondary) }
            Spacer(); Image(systemName: "arrow.up.right").foregroundStyle(.tertiary)
        }.padding(18).portalCard()
    }
}

private struct NoUpcomingCard: View {
    var body: some View { HStack(spacing: 14) { Image(systemName: "checkmark.circle.fill").font(.title).foregroundStyle(PortalTheme.teal); VStack(alignment: .leading) { Text("home.clear.title").font(.headline); Text("home.clear.body").font(.subheadline).foregroundStyle(.secondary) }; Spacer() }.padding(18).portalCard() }
}

private struct MetricCard: View {
    var icon: String; var value: Int; var label: LocalizedStringKey; var color: Color
    var body: some View { VStack(alignment: .leading, spacing: 8) { Image(systemName: icon).foregroundStyle(color); Text(String(value)).font(.title2.bold()); Text(label).font(.caption).foregroundStyle(.secondary).lineLimit(1) }.frame(maxWidth: .infinity, alignment: .leading).padding(14).portalCard(radius: 17) }
}

struct EventRow: View {
    var event: MoodleCalendarEvent
    var body: some View { HStack(spacing: 13) { VStack { Text(event.startDate, format: .dateTime.day()).font(.headline); Text(event.startDate, format: .dateTime.month(.abbreviated)).font(.caption2.weight(.bold)).textCase(.uppercase) }.frame(width: 48, height: 52).background(PortalTheme.teal.opacity(0.1), in: RoundedRectangle(cornerRadius: 13)); VStack(alignment: .leading, spacing: 3) { Text(event.name).font(.subheadline.weight(.semibold)).lineLimit(2); Text(event.startDate, format: .dateTime.hour().minute()).font(.caption).foregroundStyle(.secondary) }; Spacer() }.padding(13).portalCard(radius: 15) }
}

struct GradeRow: View {
    var grade: MoodleGrade
    var body: some View { HStack { Image(systemName: "checkmark.seal.fill").foregroundStyle(PortalTheme.gold); VStack(alignment: .leading, spacing: 3) { Text(grade.itemName).font(.subheadline.weight(.semibold)).lineLimit(2); Text(grade.rangeFormatted).font(.caption).foregroundStyle(.secondary) }; Spacer(); Text(grade.gradeFormatted).font(.headline).foregroundStyle(PortalTheme.teal) }.padding(14).portalCard(radius: 15) }
}
