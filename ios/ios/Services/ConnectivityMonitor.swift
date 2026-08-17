import Foundation
import Network

final class ConnectivityMonitor: @unchecked Sendable {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "org.moodle.ios.connectivity")
    var onChange: (@Sendable (Bool) -> Void)?

    init() {
        monitor.pathUpdateHandler = { [weak self] path in self?.onChange?(path.status == .satisfied) }
        monitor.start(queue: queue)
    }

    deinit { monitor.cancel() }
}
