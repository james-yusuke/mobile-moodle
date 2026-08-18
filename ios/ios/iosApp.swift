import SwiftUI

@main
struct MobileMoodleApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var model: AppModel

    init() {
        do {
            let process = ProcessInfo.processInfo
            #if DEBUG
            let uiTesting = process.arguments.contains("--ui-testing") ||
                process.environment["MOBILE_MOODLE_UI_TESTING"] == "1" ||
                UserDefaults.standard.bool(forKey: "MobileMoodleUITesting")
            #else
            let uiTesting = false
            #endif
            let environment = try AppEnvironment(inMemory: uiTesting)
            if uiTesting {
                let authenticationState: AuthenticationState = process.arguments.contains("--session-expired")
                    ? .reauthenticationRequired
                    : .authenticated
                try UITestSeeder.seed(store: environment.store, authenticationState: authenticationState)
            }
            let appModel = AppModel(environment: environment)
            if uiTesting { appModel.isOnline = false }
            _model = State(initialValue: appModel)
        } catch {
            fatalError("Unable to initialize the local data store: \(error.localizedDescription)")
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView().environment(model)
                .preferredColorScheme(ProcessInfo.processInfo.arguments.contains("--dark-mode") || ProcessInfo.processInfo.environment["MOBILE_MOODLE_DARK_MODE"] == "1" || UserDefaults.standard.bool(forKey: "MobileMoodleDarkMode") ? .dark : nil)
        }
    }
}
