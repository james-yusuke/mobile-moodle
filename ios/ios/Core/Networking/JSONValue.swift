import Foundation

enum JSONValue: Codable, Equatable, Sendable {
    case object([String: JSONValue])
    case array([JSONValue])
    case string(String)
    case number(Double)
    case bool(Bool)
    case null

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .null }
        else if let value = try? container.decode(Bool.self) { self = .bool(value) }
        else if let value = try? container.decode(Double.self) { self = .number(value) }
        else if let value = try? container.decode(String.self) { self = .string(value) }
        else if let value = try? container.decode([String: JSONValue].self) { self = .object(value) }
        else { self = .array(try container.decode([JSONValue].self)) }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .object(let value): try container.encode(value)
        case .array(let value): try container.encode(value)
        case .string(let value): try container.encode(value)
        case .number(let value): try container.encode(value)
        case .bool(let value): try container.encode(value)
        case .null: try container.encodeNil()
        }
    }

    var object: [String: JSONValue]? { if case .object(let value) = self { value } else { nil } }
    var array: [JSONValue]? { if case .array(let value) = self { value } else { nil } }
    var string: String? {
        switch self { case .string(let value): value; case .number(let value): value.formatted(.number.grouping(.never)); default: nil }
    }
    var int64: Int64? {
        switch self { case .number(let value): Int64(value); case .string(let value): Int64(value); default: nil }
    }
    var int: Int? { int64.map(Int.init) }
    var bool: Bool? {
        switch self { case .bool(let value): value; case .number(let value): value != 0; case .string(let value): value == "1" || value.lowercased() == "true"; default: nil }
    }

    subscript(_ key: String) -> JSONValue? { object?[key] }
}

extension Dictionary where Key == String, Value == JSONValue {
    func string(_ key: String, default fallback: String = "") -> String { self[key]?.string ?? fallback }
    func int64(_ key: String) -> Int64? { self[key]?.int64 }
    func int(_ key: String, default fallback: Int = 0) -> Int { self[key]?.int ?? fallback }
    func bool(_ key: String, default fallback: Bool = false) -> Bool { self[key]?.bool ?? fallback }
    func array(_ key: String) -> [JSONValue] { self[key]?.array ?? [] }
}
