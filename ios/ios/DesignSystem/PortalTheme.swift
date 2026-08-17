import SwiftUI

enum PortalTheme {
    static let navy = Color(hex: 0x0B2733)
    static let teal = Color(hex: 0x006A60)
    static let tealDark = Color(hex: 0x004E47)
    static let gold = Color(hex: 0xC5963F)
    static let ivory = Color(hex: 0xF6F4ED)
    static let darkBackground = Color(hex: 0x071C23)
    static let darkSurface = Color(hex: 0x102B33)
}

struct PortalBackground<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    @ViewBuilder var content: Content

    var body: some View {
        ZStack {
            (scheme == .dark ? PortalTheme.darkBackground : PortalTheme.ivory).ignoresSafeArea()
            RadialGradient(colors: [PortalTheme.teal.opacity(scheme == .dark ? 0.14 : 0.08), .clear], center: .topTrailing, startRadius: 10, endRadius: 520).ignoresSafeArea()
            content
        }
        .tint(PortalTheme.teal)
    }
}

struct PortalCardModifier: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    var radius: CGFloat = 20
    func body(content: Content) -> some View {
        content
            .background(scheme == .dark ? PortalTheme.darkSurface : Color.white.opacity(0.88), in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).stroke(scheme == .dark ? Color.white.opacity(0.08) : PortalTheme.navy.opacity(0.08)))
            .shadow(color: PortalTheme.navy.opacity(scheme == .dark ? 0.16 : 0.08), radius: 18, y: 7)
    }
}

extension View {
    func portalCard(radius: CGFloat = 20) -> some View { modifier(PortalCardModifier(radius: radius)) }
}

struct PortalBrandMark: View {
    var size: CGFloat = 42
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.3, style: .continuous)
                .fill(LinearGradient(colors: [PortalTheme.teal, PortalTheme.tealDark], startPoint: .topLeading, endPoint: .bottomTrailing))
            Path { path in
                path.move(to: CGPoint(x: size * 0.24, y: size * 0.55))
                path.addLine(to: CGPoint(x: size * 0.5, y: size * 0.35))
                path.addLine(to: CGPoint(x: size * 0.76, y: size * 0.55))
                path.addLine(to: CGPoint(x: size * 0.5, y: size * 0.7))
                path.closeSubpath()
            }.fill(Color.white)
            Circle().fill(PortalTheme.gold).frame(width: size * 0.13)
                .offset(x: size * 0.26, y: -size * 0.26)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct PortalSectionHeader: View {
    var title: LocalizedStringKey
    var action: LocalizedStringKey?
    var onAction: (() -> Void)?
    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title).font(.title3.weight(.bold)).foregroundStyle(.primary)
            Spacer()
            if let action, let onAction { Button(action, action: onAction).font(.subheadline.weight(.semibold)) }
        }
    }
}

struct PortalStatusPill: View {
    var text: String
    var systemImage: String
    var emphasized = false
    var body: some View {
        Label(text, systemImage: systemImage)
            .font(.caption.weight(.semibold))
            .foregroundStyle(emphasized ? PortalTheme.teal : .secondary)
            .padding(.horizontal, 11).padding(.vertical, 7)
            .background((emphasized ? PortalTheme.teal : Color.secondary).opacity(0.1), in: Capsule())
    }
}

struct PortalCourseCover: View {
    @Environment(\.colorScheme) private var scheme
    var course: MoodleCourse
    var height: CGFloat = 132
    var body: some View {
        let palette = coursePalette(seed: course.id, dark: scheme == .dark)
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [palette.start, palette.end], startPoint: .topLeading, endPoint: .bottomTrailing)
            Circle().stroke(palette.accent.opacity(0.38), lineWidth: 24).frame(width: 150, height: 150).offset(x: 150, y: -48).accessibilityHidden(true)
            RoundedRectangle(cornerRadius: 28).stroke(Color.white.opacity(0.13), lineWidth: 1).frame(width: 160, height: 70).rotationEffect(.degrees(-18)).offset(x: 125, y: 20).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 7) {
                Text(course.shortName).font(.caption.weight(.bold)).textCase(.uppercase).foregroundStyle(palette.accent)
                Text(course.fullName).font(.headline.weight(.bold)).foregroundStyle(.white).lineLimit(2)
            }.padding(18)
        }
        .frame(maxWidth: .infinity).frame(height: height).clipped()
        .accessibilityElement(children: .combine)
    }
}

struct PortalEmptyState: View {
    var icon: String
    var title: LocalizedStringKey
    var message: LocalizedStringKey
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 30, weight: .medium)).foregroundStyle(PortalTheme.teal)
                .frame(width: 58, height: 58).background(PortalTheme.teal.opacity(0.1), in: Circle())
            Text(title).font(.headline)
            Text(message).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 380)
        }.frame(maxWidth: .infinity).padding(32).portalCard()
    }
}

struct PortalErrorBanner: View {
    var message: String
    var onDismiss: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
            Text(message).font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onDismiss) { Image(systemName: "xmark").frame(width: 32, height: 32) }.accessibilityLabel(Text("action.dismiss"))
        }.padding(14).background(Color.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 14))
    }
}

struct OfflinePill: View {
    var body: some View { Label("status.offline", systemImage: "wifi.slash").font(.caption.weight(.semibold)).padding(.horizontal, 11).padding(.vertical, 7).background(Color.orange.opacity(0.13), in: Capsule()) }
}

struct PortalLoadingView: View {
    @State private var phase = false
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                RoundedRectangle(cornerRadius: 26).fill(Color.secondary.opacity(phase ? 0.08 : 0.17)).frame(height: 176)
                ForEach(0..<4, id: \.self) { _ in
                    HStack(spacing: 14) {
                        RoundedRectangle(cornerRadius: 14).fill(Color.secondary.opacity(phase ? 0.08 : 0.17)).frame(width: 72, height: 72)
                        VStack(alignment: .leading, spacing: 9) {
                            RoundedRectangle(cornerRadius: 4).fill(Color.secondary.opacity(0.14)).frame(height: 14)
                            RoundedRectangle(cornerRadius: 4).fill(Color.secondary.opacity(0.09)).frame(width: 150, height: 12)
                        }
                    }.padding(16).portalCard()
                }
            }.padding()
        }.task { withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { phase = true } }
        .accessibilityLabel(Text("status.loading"))
    }
}

struct HTMLText: View {
    var html: String
    var textColor: Color = .primary
    var linkColor: Color = PortalTheme.teal

    var body: some View {
        if html.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { EmptyView() }
        else {
            Text(attributed)
                .font(.body)
                .foregroundStyle(textColor)
                .tint(linkColor)
                .lineSpacing(4)
                .textSelection(.enabled)
        }
    }

    private var attributed: AttributedString {
        guard let data = html.data(using: .utf8),
              let value = try? NSAttributedString(data: data, options: [.documentType: NSAttributedString.DocumentType.html, .characterEncoding: String.Encoding.utf8.rawValue], documentAttributes: nil)
        else { return AttributedString(html) }
        // Moodle HTML often carries a black foreground color and a browser font.
        // Strip the UIKit/AppKit color before bridging to SwiftUI; removing the
        // SwiftUI attribute afterwards does not clear the original HTML color.
        let result = NSMutableAttributedString(attributedString: value)
        let fullRange = NSRange(location: 0, length: result.length)
        result.removeAttribute(.foregroundColor, range: fullRange)
        result.removeAttribute(.font, range: fullRange)
        return AttributedString(result)
    }
}
