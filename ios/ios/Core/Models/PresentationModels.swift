import SwiftUI

struct CoursePalette: Equatable, Sendable {
    let startHex: UInt
    let endHex: UInt
    let accentHex: UInt

    var start: Color { Color(hex: startHex) }
    var end: Color { Color(hex: endHex) }
    var accent: Color { Color(hex: accentHex) }
}

func coursePalette(seed: Int64, dark: Bool) -> CoursePalette {
    let light: [CoursePalette] = [
        .init(startHex: 0x073B4C, endHex: 0x0B6E69, accentHex: 0x74D6C5),
        .init(startHex: 0x183A61, endHex: 0x38669B, accentHex: 0xA6C8F2),
        .init(startHex: 0x4B294C, endHex: 0x7B536F, accentHex: 0xE1B8D2),
        .init(startHex: 0x5A4311, endHex: 0x99752C, accentHex: 0xF1D58A),
        .init(startHex: 0x23452F, endHex: 0x477557, accentHex: 0xB4D9B8),
    ]
    let darkPalettes: [CoursePalette] = [
        .init(startHex: 0x0B313B, endHex: 0x095B56, accentHex: 0x67CABC),
        .init(startHex: 0x172D49, endHex: 0x31577E, accentHex: 0x91B7E6),
        .init(startHex: 0x3C263E, endHex: 0x65465E, accentHex: 0xCFA5C1),
        .init(startHex: 0x40320F, endHex: 0x775B22, accentHex: 0xE4C36E),
        .init(startHex: 0x1C3726, endHex: 0x385F46, accentHex: 0x9FC9A5),
    ]
    let values = dark ? darkPalettes : light
    let index = Int(seed.magnitude % UInt64(values.count))
    return values[index]
}

extension Color {
    init(hex: UInt, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}
