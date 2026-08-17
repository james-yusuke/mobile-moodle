import Foundation

enum FileCache {
    static func directory(accountID: String) -> URL {
        let root = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appending(path: "MobileMoodle", directoryHint: .isDirectory)
            .appending(path: accountID, directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        var value = root; try? value.setResourceValues(values)
        return root
    }

    static func destination(accountID: String, file: MoodleFile) -> URL {
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "._-"))
        let name = String(file.name.unicodeScalars.map { allowed.contains($0) ? Character(String($0)) : "_" }.prefix(120))
        return directory(accountID: accountID).appending(path: name.isEmpty ? "download" : name)
    }
}
